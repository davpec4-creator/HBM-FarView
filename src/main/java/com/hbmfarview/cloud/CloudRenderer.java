package com.hbmfarview.cloud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the custom clouds. The sky is split into square tiles in "cloud space". Each tile is built once into
 * a vertex buffer. Wind only moves the cloud space, so tiles never need a rebuild because of it; tiles are
 * created and dropped as the player moves (the drawn area is a circle) and rebuilt when their clouds change
 * (missiles, weather, pressure waves).
 *
 * When the clouds of a tile change, the cells that appear fade in and the cells that go away fade out, instead
 * of popping. For that a rebuilt tile gets two extra buffers next to the main one: the new cells and the old
 * cells. When the fade is over the tile is built once more as a single buffer.
 *
 * Weather groups darken the clouds in the vertex colors; a lightning flash brightens whole tiles through the
 * shader color, so it needs no rebuild.
 */
public final class CloudRenderer {
    static final int CELL = 8;
    static final int LAYER_HEIGHT = 4;
    static final int TILE_CELLS = 16;
    private static final double TILE_BLOCKS = CELL * TILE_CELLS;
    private static final int TILE_RADIUS = 6;
    private static final double DRAW_RADIUS = TILE_RADIUS * TILE_BLOCKS;
    private static final long BUILD_NANOS_PER_FRAME = 2_000_000L;
    private static final long FADE_NANOS = 600_000_000L;
    static final double WIND_BLOCKS_PER_TICK = 0.03;
    private static final float ALPHA = 0.9F;

    private static final float[] TOP = {1.0F, 1.0F, 1.0F};
    private static final float[] BOTTOM = {0.60F, 0.66F, 0.76F};
    private static final float[] SIDE_LOW = {0.68F, 0.73F, 0.82F};
    private static final float[] SIDE_HIGH = {0.95F, 0.96F, 0.98F};
    private static final float SIDE_X = 0.96F;
    private static final float SIDE_Z = 0.88F;

    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static final Set<Long> STALE = new HashSet<>();
    private static final Set<Long> ANIMATE = new HashSet<>();
    private static ClientLevel tilesLevel;

    private static boolean closeOnly;

    private CloudRenderer() {
    }

    /** One tile. target holds the cells the tile shows once any fade is over (null before the first build). */
    private static final class Tile {
        VertexBuffer main;
        VertexBuffer appear;
        VertexBuffer vanish;
        int[][] target;
        int[][] base;
        long fadeStart;

        boolean fading() {
            return appear != null || vanish != null;
        }

        void close() {
            if (main != null) {
                main.close();
                main = null;
            }
            closeFade();
        }

        void closeFade() {
            if (appear != null) {
                appear.close();
                appear = null;
            }
            if (vanish != null) {
                vanish.close();
                vanish = null;
            }
        }
    }

    /** Frees all tile buffers. Called when the clouds are switched off. */
    public static void clear() {
        for (Tile tile : TILES.values()) {
            tile.close();
        }
        TILES.clear();
        STALE.clear();
        ANIMATE.clear();
        tilesLevel = null;
    }

    /**
     * byDh: Distant Horizons draws the mid and far clouds, so only the clouds CloudLod marks as close
     * are drawn here.
     */
    public static void render(PoseStack stack, Matrix4f modelView, Matrix4f projection,
                              float partialTick, double camX, double camY, double camZ, boolean byDh) {
        if (byDh != closeOnly) {
            clear();
            closeOnly = byDh;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float height = level.effects().getCloudHeight();
        if (Float.isNaN(height)) {
            return;
        }
        if (level != tilesLevel) {
            clear();
            tilesLevel = level;
        }

        double wind = (level.getGameTime() + partialTick) * WIND_BLOCKS_PER_TICK;
        updateTiles(camX - wind, camZ);

        Vec3 color = level.getCloudColor(partialTick);
        double baseY = height + 0.33 - camY;
        float alpha = byDh ? 1.0F : ALPHA;
        long now = System.nanoTime();

        // 0: depth of the settled clouds, 1: their color, 2: the cells that fade in or out.
        for (int pass = 0; pass < 3; pass++) {
            RenderType type = pass == 0 ? CloudRenderTypes.depthOnly() : pass == 1 ? CloudRenderTypes.color() : CloudRenderTypes.fade();
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            for (Map.Entry<Long, Tile> entry : TILES.entrySet()) {
                Tile tile = entry.getValue();
                if (pass < 2 ? tile.main == null : !tile.fading()) {
                    continue;
                }
                long key = entry.getKey();
                double tileX = unpackX(key) * TILE_BLOCKS;
                double tileZ = unpackZ(key) * TILE_BLOCKS;
                float flash = CloudWeather.flash(tileX, tileZ, tileX + TILE_BLOCKS, tileZ + TILE_BLOCKS);
                float r = (float) color.x * flash;
                float g = (float) color.y * flash;
                float b = (float) color.z * flash;
                stack.pushPose();
                stack.mulPose(modelView);
                stack.translate(tileX + wind - camX, baseY, tileZ - camZ);
                if (pass < 2) {
                    RenderSystem.setShaderColor(r, g, b, alpha);
                    draw(tile.main, stack, projection, shader);
                } else {
                    float t = Mth.clamp((now - tile.fadeStart) / (float) FADE_NANOS, 0.0F, 1.0F);
                    RenderSystem.setShaderColor(r, g, b, alpha * t);
                    draw(tile.appear, stack, projection, shader);
                    RenderSystem.setShaderColor(r, g, b, alpha * (1.0F - t));
                    draw(tile.vanish, stack, projection, shader);
                    if (t >= 1.0F) {
                        STALE.add(key);
                    }
                }
                stack.popPose();
            }
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            type.clearRenderState();
        }
    }

    private static void draw(VertexBuffer buffer, PoseStack stack, Matrix4f projection, ShaderInstance shader) {
        if (buffer != null) {
            buffer.bind();
            buffer.drawWithShader(stack.last().pose(), projection, shader);
        }
    }

    /** Keeps the tiles within a circle around the camera (cloud space) and rebuilds the stale ones, nearest first. */
    private static void updateTiles(double camX, double camZ) {
        int centerX = Mth.floor(camX / TILE_BLOCKS);
        int centerZ = Mth.floor(camZ / TILE_BLOCKS);
        Iterator<Map.Entry<Long, Tile>> it = TILES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Tile> entry = it.next();
            long key = entry.getKey();
            if (tileDistance(unpackX(key), unpackZ(key), camX, camZ) > DRAW_RADIUS + TILE_BLOCKS) {
                entry.getValue().close();
                STALE.remove(key);
                ANIMATE.remove(key);
                it.remove();
            }
        }

        if (closeOnly) {
            STALE.addAll(CloudLod.takeDirtyMc());
        }
        if (CloudDirty.takeAllMc()) {
            STALE.addAll(TILES.keySet());
            ANIMATE.addAll(TILES.keySet());
        }
        Set<Long> changed = CloudDirty.takeMc();
        STALE.addAll(changed);
        ANIMATE.addAll(changed);

        List<double[]> missing = new ArrayList<>();
        for (int x = centerX - TILE_RADIUS - 1; x <= centerX + TILE_RADIUS + 1; x++) {
            for (int z = centerZ - TILE_RADIUS - 1; z <= centerZ + TILE_RADIUS + 1; z++) {
                double distance = tileDistance(x, z, camX, camZ);
                if (distance > DRAW_RADIUS) {
                    continue;
                }
                long key = pack(x, z);
                if (!TILES.containsKey(key) || STALE.contains(key)) {
                    missing.add(new double[]{distance, x, z});
                }
            }
        }
        missing.sort((a, b) -> Double.compare(a[0], b[0]));
        long start = System.nanoTime();
        for (double[] m : missing) {
            if (System.nanoTime() - start > BUILD_NANOS_PER_FRAME) {
                break;
            }
            int x = (int) m[1];
            int z = (int) m[2];
            long key = pack(x, z);
            Tile tile = TILES.computeIfAbsent(key, k -> new Tile());
            buildTile(x, z, tile, ANIMATE.remove(key));
            STALE.remove(key);
        }
    }

    /** Distance from the camera to the nearest point of a tile. */
    private static double tileDistance(int tileX, int tileZ, double camX, double camZ) {
        double x0 = tileX * TILE_BLOCKS;
        double z0 = tileZ * TILE_BLOCKS;
        double dx = Math.max(0.0, Math.max(x0 - camX, camX - x0 - TILE_BLOCKS));
        double dz = Math.max(0.0, Math.max(z0 - camZ, camZ - z0 - TILE_BLOCKS));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Builds one tile. Each cell is a bit mask of its layers (bit = layer + CloudHoles.OFFSET). A face is drawn
     * only where the cell next to it has no cloud at that layer. With animate, the cells that are new since the
     * last build go into the fade-in buffer and the ones that are gone into the fade-out buffer.
     */
    private static void buildTile(int tileX, int tileZ, Tile tile, boolean animate) {
        int baseX = tileX * TILE_CELLS;
        int baseZ = tileZ * TILE_CELLS;
        int size = TILE_CELLS + 2;
        int[][] full = new int[size][size];
        int[][] base = new int[size][size];
        int[][] tint = new int[size][size];
        int[][] drawn = new int[size][size];
        int[] column = new int[3];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                full[i][j] = cellMask(baseX + i - 1, baseZ + j - 1, column);
                base[i][j] = column[2];
            }
        }
        // The cells this renderer draws; with Distant Horizons only the close clouds.
        for (int i = 1; i <= TILE_CELLS; i++) {
            for (int j = 1; j <= TILE_CELLS; j++) {
                int m = full[i][j];
                if (m != 0 && closeOnly && CloudLod.level(baseX + i - 1, baseZ + j - 1) != CloudLod.CLOSE) {
                    m = 0;
                }
                drawn[i][j] = m;
                tint[i][j] = CloudWeather.tint(baseX + i - 1, baseZ + j - 1);
            }
        }

        int[][] before = tile.target;
        int[][] beforeBase = tile.base;
        tile.closeFade();
        tile.target = drawn;
        tile.base = base;
        if (animate && before != null) {
            int[][] appear = new int[size][size];
            int[][] vanish = new int[size][size];
            int[][] stay = new int[size][size];
            int[][] mainSides = new int[size][size];
            int[][] vanishSides = new int[size][size];
            boolean any = false;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    mainSides[i][j] = full[i][j];
                    vanishSides[i][j] = full[i][j];
                }
            }
            for (int i = 1; i <= TILE_CELLS; i++) {
                for (int j = 1; j <= TILE_CELLS; j++) {
                    int old = before[i][j];
                    appear[i][j] = drawn[i][j] & ~old;
                    vanish[i][j] = old & ~drawn[i][j];
                    stay[i][j] = drawn[i][j] & old;
                    // The settled clouds keep their faces towards cells that are still fading in.
                    mainSides[i][j] = full[i][j] & ~appear[i][j];
                    vanishSides[i][j] = full[i][j] | vanish[i][j];
                    any |= appear[i][j] != 0 || vanish[i][j] != 0;
                }
            }
            if (any) {
                replaceMain(tile, mesh(stay, mainSides, base, tint, baseX, baseZ));
                tile.appear = upload(mesh(appear, full, base, tint, baseX, baseZ));
                tile.vanish = upload(mesh(vanish, vanishSides, beforeBase, tint, baseX, baseZ));
                tile.fadeStart = System.nanoTime();
                return;
            }
        }
        replaceMain(tile, mesh(drawn, full, base, tint, baseX, baseZ));
    }

    private static void replaceMain(Tile tile, MeshData mesh) {
        if (tile.main != null) {
            tile.main.close();
        }
        tile.main = upload(mesh);
    }

    private static VertexBuffer upload(MeshData mesh) {
        if (mesh == null) {
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }

    /**
     * The faces of the given cells. sides decides where a face is drawn: only where the neighbouring cell (or
     * the layer above or below in sides) has no cloud at that layer.
     */
    private static MeshData mesh(int[][] cells, int[][] sides, int[][] base, int[][] tint, int baseX, int baseZ) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] top = new float[3];
        float[] bottom = new float[3];
        for (int i = 1; i <= TILE_CELLS; i++) {
            for (int j = 1; j <= TILE_CELLS; j++) {
                int m = cells[i][j];
                if (m == 0) {
                    continue;
                }
                float dark = CloudWeather.tintFactor(tint[i][j]);
                scale(TOP, dark, top);
                scale(BOTTOM, dark, bottom);
                float x0 = (i - 1) * CELL;
                float z0 = (j - 1) * CELL;
                float x1 = x0 + CELL;
                float z1 = z0 + CELL;
                int cellBase = base == null ? 0 : base[i][j];
                for (int bit = 0; bit < 32; bit++) {
                    if ((m & (1 << bit)) == 0) {
                        continue;
                    }
                    int layer = bit - CloudHoles.OFFSET;
                    float y0 = layer * LAYER_HEIGHT;
                    float y1 = y0 + LAYER_HEIGHT;
                    float shade = CloudShape.shade(baseX + i - 1, baseZ + j - 1, layer);
                    float[] side = sideColor(layer - cellBase);
                    scale(side, dark, side);
                    if (!has(m, bit + 1) && !has(sides[i][j], bit + 1)) {
                        horizontal(builder, x0, x1, z0, z1, y1, top, shade);
                    }
                    if (!has(m, bit - 1) && !has(sides[i][j], bit - 1)) {
                        horizontal(builder, x0, x1, z0, z1, y0, bottom, shade);
                    }
                    if (!has(sides[i - 1][j], bit)) {
                        sideX(builder, x0, y0, y1, z0, z1, side, shade);
                    }
                    if (!has(sides[i + 1][j], bit)) {
                        sideX(builder, x1, y0, y1, z0, z1, side, shade);
                    }
                    if (!has(sides[i][j - 1], bit)) {
                        sideZ(builder, x0, x1, y0, y1, z0, side, shade);
                    }
                    if (!has(sides[i][j + 1], bit)) {
                        sideZ(builder, x0, x1, y0, y1, z1, side, shade);
                    }
                }
            }
        }
        return builder.build();
    }

    private static void horizontal(BufferBuilder b, float x0, float x1, float z0, float z1, float y,
                                   float[] c, float shade) {
        quad(b, x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, c, shade);
    }

    private static void scale(float[] c, float factor, float[] out) {
        out[0] = c[0] * factor;
        out[1] = c[1] * factor;
        out[2] = c[2] * factor;
    }

    /**
     * Layers of one cell as a bit mask (bit = layer + CloudHoles.OFFSET), holes removed and moved up by the
     * missile funnel. column receives the cell's column, also moved up; column[2] is the cloud base.
     */
    static int cellMask(int cellX, int cellZ, int[] column) {
        if (!CloudShape.column(cellX, cellZ, column)) {
            column[2] = 0;
            return 0;
        }
        int from = Math.max(0, column[0] + CloudHoles.OFFSET);
        int to = Math.min(32, column[1] + CloudHoles.OFFSET);
        int mask = 0;
        for (int bit = from; bit < to; bit++) {
            mask |= 1 << bit;
        }
        mask &= ~CloudHoles.mask(cellX, cellZ);
        int lift = CloudHoles.lift(cellX, cellZ);
        if (lift > 0) {
            column[0] += lift;
            column[1] += lift;
            column[2] += lift;
            mask <<= lift;
        }
        return mask;
    }

    private static boolean has(int mask, int bit) {
        return bit >= 0 && bit < 32 && (mask & (1 << bit)) != 0;
    }

    /** Sides are grey-blue at the bottom of a cloud and almost white near its top. */
    static float[] sideColor(int layerAboveBase) {
        float t = Mth.clamp((layerAboveBase + 2) / (float) (CloudShape.MAX_LAYERS + 1), 0.0F, 1.0F);
        return new float[]{
                Mth.lerp(t, SIDE_LOW[0], SIDE_HIGH[0]),
                Mth.lerp(t, SIDE_LOW[1], SIDE_HIGH[1]),
                Mth.lerp(t, SIDE_LOW[2], SIDE_HIGH[2])};
    }

    private static void sideX(BufferBuilder b, float x, float y0, float y1, float z0, float z1,
                              float[] c, float shade) {
        quad(b, x, y0, z1, x, y1, z1, x, y1, z0, x, y0, z0, c, shade * SIDE_X);
    }

    private static void sideZ(BufferBuilder b, float x0, float x1, float y0, float y1, float z,
                              float[] c, float shade) {
        quad(b, x0, y0, z, x0, y1, z, x1, y1, z, x1, y0, z, c, shade * SIDE_Z);
    }

    private static void quad(BufferBuilder b,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float[] c, float shade) {
        float r = c[0] * shade;
        float g = c[1] * shade;
        float bl = c[2] * shade;
        b.addVertex(ax, ay, az).setColor(r, g, bl, 1.0F);
        b.addVertex(bx, by, bz).setColor(r, g, bl, 1.0F);
        b.addVertex(cx, cy, cz).setColor(r, g, bl, 1.0F);
        b.addVertex(dx, dy, dz).setColor(r, g, bl, 1.0F);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
