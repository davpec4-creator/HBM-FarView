package com.hbmfarview.cloud;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the mid and far clouds through Distant Horizons, the same way its own clouds are drawn: as groups
 * of boxes. Distant Horizons draws them far beyond the vanilla render distance, hides them behind its far
 * terrain and puts its fog over them. Close clouds are left to CloudRenderer; which cloud is which is
 * decided per whole cloud by CloudLod.
 *
 * The sky around the player is split into square tiles, one box group each. A tile holds the mid clouds
 * as one box per cell and layer, and the far clouds as merged boxes (equal columns side by side joined).
 * A tile is built once; every frame only its origin moves with the wind. It is rebuilt when a cloud in it
 * changes its level or its shape (missiles, weather); the old group stays visible until the new one is
 * ready. Weather groups darken the box colors; a lightning flash recolors the tiles near it for a moment.
 * Only touch this class when Distant Horizons is installed.
 */
final class DhClouds {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GROUP_NAME = "hbm_farview:clouds";
    private static final int TILE_CELLS = CloudLod.DH_TILE_CELLS;
    private static final int TILE_BLOCKS = CloudRenderer.CELL * TILE_CELLS;
    private static final int MAX_RANGE_BLOCKS = 3072;
    private static final long BUILD_NANOS_PER_FRAME = 2_000_000L;
    private static final int RECOLORS_PER_FRAME = 6;
    private static final long ACTIVE_NANOS = 500_000_000L;
    private static final float COLOR_STEP = 2.0F / 255.0F;
    /** How long a replaced tile stays visible, so the new one is uploaded before the old one goes. */
    private static final long RETIRE_NANOS = 250_000_000L;

    private static final float[] UPPER = {1.0F, 1.0F, 1.0F};
    private static final float[] BELLY = {0.80F, 0.84F, 0.92F};

    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static final Set<Long> STALE = new java.util.HashSet<>();
    private static final List<Object[]> RETIRED = new ArrayList<>();
    private static IDhApiCustomRenderRegister register;
    private static boolean bound;
    private static boolean failed;
    private static boolean dhCloudsOverridden;
    private static long lastFrame;
    private static double wind;
    private static double cloudY;
    private static Vec3 cloudColor = new Vec3(1.0, 1.0, 1.0);

    private DhClouds() {
    }

    /** One tile; group is null when the tile has nothing for Distant Horizons to draw. */
    private static final class Tile {
        final int x;
        final int z;
        final IDhApiRenderableBoxGroup group;
        final DhApiVec3d origin = new DhApiVec3d();
        final List<float[]> baseColors;
        Vec3 appliedColor;
        float appliedFlash = 1.0F;

        Tile(int x, int z, IDhApiRenderableBoxGroup group, List<float[]> baseColors) {
            this.x = x;
            this.z = z;
            this.group = group;
            this.baseColors = baseColors;
        }
    }

    static void bind() {
        if (bound || failed) {
            return;
        }
        bound = true;
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (failed) {
                    return;
                }
                try {
                    frame(event.value);
                } catch (Throwable t) {
                    failed = true;
                    LOGGER.warn("[HBM Far View] Clouds through Distant Horizons failed, using the normal cloud renderer", t);
                    try {
                        clear();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    /** True while Distant Horizons draws the mid and far clouds. */
    static boolean active() {
        return !failed && register != null && System.nanoTime() - lastFrame < ACTIVE_NANOS;
    }

    private static void frame(DhApiRenderParam param) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        IDhApiLevelWrapper wrapper = param.clientLevelWrapper;
        if (level == null || wrapper == null || !CloudCompat.enabled() || !genericRenderingOn()) {
            clear();
            return;
        }
        float height = level.effects().getCloudHeight();
        if (Float.isNaN(height)) {
            clear();
            return;
        }
        IDhApiCustomRenderRegister current = wrapper.getRenderRegister();
        if (current == null) {
            clear();
            return;
        }
        if (current != register) {
            clear();
            register = current;
        }
        overrideDhClouds(true);
        lastFrame = System.nanoTime();

        wind = (level.getGameTime() + param.partialTicks) * CloudRenderer.WIND_BLOCKS_PER_TICK;
        cloudY = height + 0.33;
        cloudColor = level.getCloudColor(param.partialTicks);

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // The far plane of the normal renderer without the changes Far View makes for blast effects.
        float depthFar = mc.options.getEffectiveRenderDistance() * 16 * 4.0F;
        int range = Math.min(MAX_RANGE_BLOCKS, Math.max(TILE_BLOCKS * 2, lodRangeBlocks()));
        int radius = Mth.ceil(range / (double) TILE_BLOCKS);
        int centerX = Mth.floor((cam.x - wind) / TILE_BLOCKS);
        int centerZ = Mth.floor(cam.z / TILE_BLOCKS);
        CloudLod.update(cam.x - wind, cam.z, centerX, centerZ, closeRing(depthFar), depthFar);
        STALE.addAll(CloudLod.takeDirtyDh());
        if (CloudDirty.takeAllDh()) {
            STALE.addAll(TILES.keySet());
        }
        STALE.addAll(CloudDirty.takeDh());
        updateTiles(centerX, centerZ, radius);
        recolor();
        retire(false);
    }

    /**
     * How many rings of tiles around the player belong to the normal renderer when a cloud area is too
     * large to be handled as one cloud. Its far plane cuts off at 4x the render distance.
     */
    private static int closeRing(float depthFar) {
        return Mth.clamp(Mth.floor(depthFar / (TILE_BLOCKS * 1.42F)) - 1, 0, 1);
    }

    private static void updateTiles(int centerX, int centerZ, int radius) {
        Iterator<Map.Entry<Long, Tile>> it = TILES.entrySet().iterator();
        while (it.hasNext()) {
            Tile tile = it.next().getValue();
            if (Math.abs(tile.x - centerX) > radius + 1 || Math.abs(tile.z - centerZ) > radius + 1) {
                remove(tile);
                it.remove();
                STALE.remove(CloudLod.pack(tile.x, tile.z));
            }
        }

        List<int[]> missing = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                long key = CloudLod.pack(x, z);
                if (!TILES.containsKey(key) || STALE.contains(key)) {
                    missing.add(new int[]{(x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ), x, z});
                }
            }
        }
        missing.sort((a, b) -> Integer.compare(a[0], b[0]));
        long start = System.nanoTime();
        for (int[] m : missing) {
            build(m[1], m[2]);
            if (System.nanoTime() - start > BUILD_NANOS_PER_FRAME) {
                break;
            }
        }
    }

    /**
     * Builds one tile. Close clouds are skipped. Mid clouds, and far cells with missile holes, get one box
     * per cell and layer, colored like the close clouds. Far clouds are split per column into the hanging part below the cloud base (grey-blue)
     * and the part above it (white); equal parts in a row are joined, and equal rows into rectangles.
     */
    private static void build(int tileX, int tileZ) {
        int baseX = tileX * TILE_CELLS;
        int baseZ = tileZ * TILE_CELLS;
        boolean near = CloudLod.nearTile(baseX, baseZ, baseX + TILE_CELLS - 1, baseZ + TILE_CELLS - 1);
        int[] column = new int[3];
        int[][] lower = new int[TILE_CELLS][];
        int[][] upper = new int[TILE_CELLS][];
        List<DhApiRenderableBox> boxes = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();

        Map<RunKey, int[]> openBelly = new HashMap<>();
        Map<RunKey, int[]> openUpper = new HashMap<>();
        for (int j = 0; j <= TILE_CELLS; j++) {
            Map<RunKey, int[]> rowBelly = new HashMap<>();
            Map<RunKey, int[]> rowUpper = new HashMap<>();
            if (j < TILE_CELLS) {
                for (int i = 0; i < TILE_CELLS; i++) {
                    lower[i] = null;
                    upper[i] = null;
                    if (!CloudShape.column(baseX + i, baseZ + j, column)) {
                        continue;
                    }
                    int cellX = baseX + i;
                    int cellZ = baseZ + j;
                    int level = near ? CloudLod.level(cellX, cellZ) : CloudLod.FAR;
                    if (level == CloudLod.CLOSE) {
                        continue;
                    }
                    int tint = CloudWeather.tint(cellX, cellZ);
                    boolean changed = CloudHoles.mask(cellX, cellZ) != 0 || CloudHoles.lift(cellX, cellZ) != 0;
                    if (level == CloudLod.MID || changed) {
                        addDetailed(cellX, cellZ, i, j, tint, boxes, colors);
                    } else {
                        if (column[0] < column[2]) {
                            lower[i] = new int[]{column[0], column[2], tint};
                        }
                        upper[i] = new int[]{Math.max(column[0], column[2]), column[1], tint};
                    }
                }
                runs(lower, j, rowBelly);
                runs(upper, j, rowUpper);
            }
            merge(openBelly, rowBelly, boxes, colors, BELLY);
            merge(openUpper, rowUpper, boxes, colors, UPPER);
        }

        long key = CloudLod.pack(tileX, tileZ);
        Tile old = TILES.get(key);
        Tile tile = boxes.isEmpty() ? new Tile(tileX, tileZ, null, colors) : addGroup(tileX, tileZ, boxes, colors);
        TILES.put(key, tile);
        STALE.remove(key);
        if (old != null) {
            // Distant Horizons uploads the new group on a later frame; removing the old one now would leave a
            // gap for a frame or two, which looks like flickering.
            RETIRED.add(new Object[]{old, System.nanoTime()});
        }
    }

    /** Removes the replaced tiles whose wait is over (all of them with now). */
    private static void retire(boolean now) {
        long time = System.nanoTime();
        Iterator<Object[]> it = RETIRED.iterator();
        while (it.hasNext()) {
            Object[] entry = it.next();
            if (now || time - (Long) entry[1] > RETIRE_NANOS) {
                remove((Tile) entry[0]);
                it.remove();
            }
        }
    }

    /** One box per layer of the cell, without the layers cut out by missiles and moved up by the funnel. */
    private static void addDetailed(int cellX, int cellZ, int i, int j, int tint,
                                    List<DhApiRenderableBox> boxes, List<float[]> colors) {
        int[] column = new int[3];
        int mask = CloudRenderer.cellMask(cellX, cellZ, column);
        int top = 31 - Integer.numberOfLeadingZeros(mask) - CloudHoles.OFFSET;
        float dark = CloudWeather.tintFactor(tint);
        for (int bit = 0; bit < 32; bit++) {
            if ((mask & (1 << bit)) == 0) {
                continue;
            }
            int layer = bit - CloudHoles.OFFSET;
            float shade = CloudShape.shade(cellX, cellZ, layer) * dark;
            float[] c = layer == top ? UPPER : CloudRenderer.sideColor(layer - column[2]);
            boxes.add(box(i, j, i, j, layer, layer + 1));
            colors.add(new float[]{c[0] * shade, c[1] * shade, c[2] * shade});
        }
    }

    private static Tile addGroup(int tileX, int tileZ, List<DhApiRenderableBox> boxes, List<float[]> colors) {
        Tile[] holder = new Tile[1];
        IDhApiRenderableBoxGroup group = DhApi.Delayed.customRenderObjectFactory
                .createRelativePositionedGroup(GROUP_NAME, new DhApiVec3d(), boxes);
        group.setBlockLight(15);
        group.setSkyLight(15);
        group.setSsaoEnabled(false);
        DhApiRenderableBoxGroupShading shading = DhApiRenderableBoxGroupShading.getUnshaded();
        shading.top = 1.0F;
        shading.north = 0.88F;
        shading.south = 0.88F;
        shading.east = 0.96F;
        shading.west = 0.96F;
        shading.bottom = 0.75F;
        group.setShading(shading);
        group.setPreRenderFunc(param -> {
            Tile t = holder[0];
            t.origin.x = (double) t.x * TILE_BLOCKS + wind;
            t.origin.y = cloudY;
            t.origin.z = (double) t.z * TILE_BLOCKS;
            t.group.setOriginBlockPos(t.origin);
        });
        Tile tile = new Tile(tileX, tileZ, group, colors);
        holder[0] = tile;
        applyColor(tile, flashOf(tile));
        group.triggerBoxChange();
        register.add(group);
        return tile;
    }

    /** A run of equal parts in a row: from start to end (cells), from bottom to top (layers), darkness. */
    private record RunKey(int start, int end, int bottom, int top, int tint) {
    }

    /** Finds runs of equal parts in one row; a part is {bottom, top, tint}. */
    private static void runs(int[][] parts, int row, Map<RunKey, int[]> out) {
        int i = 0;
        while (i < TILE_CELLS) {
            if (parts[i] == null) {
                i++;
                continue;
            }
            int start = i;
            int[] p = parts[i];
            while (i + 1 < TILE_CELLS && parts[i + 1] != null
                    && parts[i + 1][0] == p[0] && parts[i + 1][1] == p[1] && parts[i + 1][2] == p[2]) {
                i++;
            }
            out.put(new RunKey(start, i, p[0], p[1], p[2]), new int[]{start, i, p[0], p[1], row, row, p[2]});
            i++;
        }
    }

    /** Extends rectangles that continue in this row, turns the others into boxes. */
    private static void merge(Map<RunKey, int[]> open, Map<RunKey, int[]> row,
                              List<DhApiRenderableBox> boxes, List<float[]> colors, float[] color) {
        Iterator<Map.Entry<RunKey, int[]>> it = open.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<RunKey, int[]> entry = it.next();
            int[] next = row.remove(entry.getKey());
            if (next != null) {
                entry.getValue()[5] = next[5];
            } else {
                int[] r = entry.getValue();
                boxes.add(box(r[0], r[4], r[1], r[5], r[2], r[3]));
                float dark = CloudWeather.tintFactor(r[6]);
                colors.add(new float[]{color[0] * dark, color[1] * dark, color[2] * dark});
                it.remove();
            }
        }
        open.putAll(row);
    }

    /** Box over cells x0..x1 and z0..z1 (inclusive), from layer bottom up to layer top (exclusive). */
    private static DhApiRenderableBox box(int x0, int z0, int x1, int z1, int bottom, int top) {
        return new DhApiRenderableBox(
                new DhApiVec3d(x0 * CloudRenderer.CELL, bottom * CloudRenderer.LAYER_HEIGHT, z0 * CloudRenderer.CELL),
                new DhApiVec3d((x1 + 1) * CloudRenderer.CELL, top * CloudRenderer.LAYER_HEIGHT, (z1 + 1) * CloudRenderer.CELL),
                Color.WHITE, EDhApiBlockMaterial.UNKNOWN);
    }

    /**
     * Day, night and rain change the cloud color; a few tiles are recolored per frame. Tiles near a lightning
     * flash are recolored at once, so the flash is not late.
     */
    private static void recolor() {
        int done = 0;
        for (Tile tile : TILES.values()) {
            if (tile.group == null) {
                continue;
            }
            float flash = flashOf(tile);
            if (Math.abs(flash - tile.appliedFlash) > 0.05F) {
                applyColor(tile, flash);
                tile.group.triggerBoxChange();
                continue;
            }
            if (done >= RECOLORS_PER_FRAME) {
                continue;
            }
            Vec3 c = tile.appliedColor;
            if (Math.abs(c.x - cloudColor.x) > COLOR_STEP || Math.abs(c.y - cloudColor.y) > COLOR_STEP
                    || Math.abs(c.z - cloudColor.z) > COLOR_STEP) {
                applyColor(tile, flash);
                tile.group.triggerBoxChange();
                done++;
            }
        }
    }

    private static float flashOf(Tile tile) {
        double x = (double) tile.x * TILE_BLOCKS;
        double z = (double) tile.z * TILE_BLOCKS;
        return CloudWeather.flash(x, z, x + TILE_BLOCKS, z + TILE_BLOCKS);
    }

    private static void applyColor(Tile tile, float flash) {
        tile.appliedColor = cloudColor;
        tile.appliedFlash = flash;
        float r = (float) cloudColor.x * flash;
        float g = (float) cloudColor.y * flash;
        float b = (float) cloudColor.z * flash;
        for (int i = 0; i < tile.group.size(); i++) {
            float[] base = tile.baseColors.get(i);
            tile.group.get(i).color = new Color(
                    Mth.clamp(base[0] * r, 0.0F, 1.0F),
                    Mth.clamp(base[1] * g, 0.0F, 1.0F),
                    Mth.clamp(base[2] * b, 0.0F, 1.0F));
        }
    }

    /** Removes all cloud groups and gives Distant Horizons its own cloud setting back. */
    static void clear() {
        retire(true);
        for (Tile tile : TILES.values()) {
            remove(tile);
        }
        TILES.clear();
        STALE.clear();
        CloudLod.clear();
        register = null;
        try {
            overrideDhClouds(false);
        } catch (RuntimeException ignored) {
        }
    }

    private static void remove(Tile tile) {
        if (register == null || tile.group == null) {
            return;
        }
        try {
            register.remove(tile.group.getId());
        } catch (RuntimeException ignored) {
        }
    }

    /** While the custom clouds are on, the clouds of Distant Horizons are turned off (not saved to its config). */
    private static void overrideDhClouds(boolean on) {
        if (on == dhCloudsOverridden) {
            return;
        }
        IDhApiConfigValue<Boolean> value = DhApi.Delayed.configs.graphics().genericRendering().cloudRenderingEnabled();
        if (on) {
            value.setValue(false, "HBM Far View");
        } else {
            value.clearValue();
        }
        dhCloudsOverridden = on;
    }

    private static boolean genericRenderingOn() {
        var graphics = DhApi.Delayed.configs.graphics();
        return Boolean.TRUE.equals(graphics.renderingEnabled().getValue())
                && Boolean.TRUE.equals(graphics.genericRendering().renderingEnabled().getValue());
    }

    private static int lodRangeBlocks() {
        Integer chunks = DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue();
        return chunks == null ? MAX_RANGE_BLOCKS : chunks * 16;
    }

}
