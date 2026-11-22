package com.hbmfarview.cloud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detail level of whole clouds, used while Distant Horizons draws the far clouds. A cloud is a group of
 * cells connected side by side. The whole cloud gets one level from its nearest point to the player,
 * so a cloud is never drawn half in one style and half in another:
 * CLOSE - by CloudRenderer, full detail;
 * MID   - by Distant Horizons, one box per cell and layer;
 * FAR   - by Distant Horizons, merged boxes.
 * A close cloud must also end before the far plane of the normal renderer, otherwise it would be cut.
 * Very large cloud areas (more than GIANT_CELLS, for example the closed sky in rain) are split per cell by the
 * distance from the player instead, so the close part is a circle around the player. That circle follows the
 * player in steps of SNAP blocks.
 * When a cloud changes level, the tiles it touches are marked for a rebuild.
 */
final class CloudLod {
    static final int CLOSE = 0;
    static final int MID = 1;
    static final int FAR = 2;
    /** Size of the tiles of DhClouds, in cells. */
    static final int DH_TILE_CELLS = 32;

    private static final int GIANT_CELLS = 4096;
    private static final double MID_DISTANCE = 1100.0;
    private static final double HYSTERESIS = 48.0;
    private static final double LOOKUP_DISTANCE = MID_DISTANCE + 700.0;
    private static final double FORGET_DISTANCE = 2600.0;
    private static final double SNAP = 32.0;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final Map<Long, Cloud> CELLS = new HashMap<>();
    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final Set<Long> DIRTY_DH = new HashSet<>();
    private static final Set<Long> DIRTY_MC = new HashSet<>();

    private static double camX;
    private static double camZ;
    private static double snapX = Double.NaN;
    private static double snapZ = Double.NaN;
    private static double closeDistance;
    private static double closeMax;
    private static int frames;

    private CloudLod() {
    }

    private static final class Cloud {
        final long[] cells;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final boolean giant;
        int level;

        Cloud(long[] cells, int minX, int minZ, int maxX, int maxZ, boolean giant) {
            this.cells = cells;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.giant = giant;
        }
    }

    /**
     * Called once per frame. camX/camZ are the camera in cloud space (wind removed), the center and ring
     * describe the tiles of DhClouds, depthFar is where the normal renderer stops drawing.
     */
    static void update(double cameraX, double cameraZ, int tileCenterX, int tileCenterZ, int ring, float depthFar) {
        camX = cameraX;
        camZ = cameraZ;
        closeMax = depthFar * 0.9 - 16.0;
        double close = Math.min(384.0, closeMax * 0.6);
        double sx = Math.floor(cameraX / SNAP) * SNAP + SNAP / 2.0;
        double sz = Math.floor(cameraZ / SNAP) * SNAP + SNAP / 2.0;
        if (sx != snapX || sz != snapZ || close != closeDistance) {
            double oldX = snapX;
            double oldZ = snapZ;
            double oldClose = closeDistance;
            snapX = sx;
            snapZ = sz;
            closeDistance = close;
            for (Cloud cloud : CLOUDS) {
                if (cloud.giant) {
                    markGiant(cloud, oldX, oldZ, oldClose);
                }
            }
        }
        for (Cloud cloud : CLOUDS) {
            if (!cloud.giant) {
                int level = levelOf(cloud, cloud.level);
                if (level != cloud.level) {
                    cloud.level = level;
                    markDirty(cloud);
                }
            }
        }
        if (++frames % 100 == 0) {
            forgetFar();
        }
    }

    /** Level of the cloud in this cell. The cell must have a cloud. */
    static int level(int cellX, int cellZ) {
        Cloud cloud = CELLS.get(pack(cellX, cellZ));
        if (cloud == null) {
            if (cellDistance(cellX, cellZ) > LOOKUP_DISTANCE) {
                return FAR;
            }
            cloud = discover(cellX, cellZ);
        }
        if (cloud.giant) {
            return giantLevel(cellX, cellZ);
        }
        return cloud.level;
    }

    /** True when a tile of DhClouds (or nearer) may hold a close or mid cloud, so cells must be looked up. */
    static boolean nearTile(int minCellX, int minCellZ, int maxCellX, int maxCellZ) {
        return rectDistance(minCellX, minCellZ, maxCellX, maxCellZ) <= LOOKUP_DISTANCE;
    }

    static Set<Long> takeDirtyDh() {
        Set<Long> copy = new HashSet<>(DIRTY_DH);
        DIRTY_DH.clear();
        return copy;
    }

    static Set<Long> takeDirtyMc() {
        Set<Long> copy = new HashSet<>(DIRTY_MC);
        DIRTY_MC.clear();
        return copy;
    }

    /**
     * The clouds in this area of cells changed shape (weather). Clouds that touch it are forgotten and found
     * again when they are needed, and their tiles are rebuilt.
     */
    static void invalidate(int minX, int minZ, int maxX, int maxZ) {
        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud cloud = it.next();
            if (cloud.maxX < minX - 1 || cloud.minX > maxX + 1 || cloud.maxZ < minZ - 1 || cloud.minZ > maxZ + 1) {
                continue;
            }
            for (long key : cloud.cells) {
                CELLS.remove(key);
            }
            markDirty(cloud);
            it.remove();
        }
    }

    static void clear() {
        CELLS.clear();
        CLOUDS.clear();
        DIRTY_DH.clear();
        DIRTY_MC.clear();
    }

    private static Cloud discover(int startX, int startZ) {
        List<Long> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        int[] column = new int[3];
        boolean giant = false;
        int minX = startX;
        int minZ = startZ;
        int maxX = startX;
        int maxZ = startZ;
        queue.add(new long[]{startX, startZ});
        seen.add(pack(startX, startZ));
        while (!queue.isEmpty()) {
            long[] cell = queue.poll();
            int x = (int) cell[0];
            int z = (int) cell[1];
            found.add(pack(x, z));
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
            if (found.size() >= GIANT_CELLS) {
                giant = true;
                break;
            }
            for (int[] side : SIDES) {
                int nx = x + side[0];
                int nz = z + side[1];
                long key = pack(nx, nz);
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                Cloud known = CELLS.get(key);
                if (known != null) {
                    // Only a cut-off giant area can be met here; normal clouds are always found whole.
                    giant = true;
                    continue;
                }
                if (CloudShape.column(nx, nz, column)) {
                    queue.add(new long[]{nx, nz});
                }
            }
        }

        long[] cells = new long[found.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = found.get(i);
        }
        Cloud cloud = new Cloud(cells, minX, minZ, maxX, maxZ, giant);
        cloud.level = giant ? FAR : levelOf(cloud, -1);
        for (long key : cells) {
            CELLS.put(key, cloud);
        }
        CLOUDS.add(cloud);
        return cloud;
    }

    private static int levelOf(Cloud cloud, int current) {
        double near = rectDistance(cloud.minX, cloud.minZ, cloud.maxX, cloud.maxZ);
        double far = farthest(cloud);
        double stay = current == CLOSE ? HYSTERESIS : 0.0;
        if (near < closeDistance + stay && far < closeMax - (current == CLOSE ? 0.0 : HYSTERESIS)) {
            return CLOSE;
        }
        stay = current == MID ? HYSTERESIS : 0.0;
        return near < MID_DISTANCE + stay ? MID : FAR;
    }

    /** Level of a cell of a giant area: by its distance from the player, so the close part is a circle. */
    private static int giantLevel(int cellX, int cellZ) {
        if (Double.isNaN(snapX)) {
            return FAR;
        }
        double dx = (cellX + 0.5) * CloudRenderer.CELL - snapX;
        double dz = (cellZ + 0.5) * CloudRenderer.CELL - snapZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < closeDistance) {
            return CLOSE;
        }
        return distance < MID_DISTANCE ? MID : FAR;
    }

    private static void markDirty(Cloud cloud) {
        mark(DIRTY_DH, cloud, DH_TILE_CELLS);
        mark(DIRTY_MC, cloud, CloudRenderer.TILE_CELLS);
    }

    /** When the circle moves, a giant area only changes in the tiles its edges cross, before or after. */
    private static void markGiant(Cloud cloud, double oldX, double oldZ, double oldClose) {
        markEdges(DIRTY_DH, cloud, DH_TILE_CELLS, oldX, oldZ, oldClose);
        markEdges(DIRTY_MC, cloud, CloudRenderer.TILE_CELLS, oldX, oldZ, oldClose);
    }

    private static void markEdges(Set<Long> set, Cloud cloud, int tileCells, double oldX, double oldZ, double oldClose) {
        for (int x = Math.floorDiv(cloud.minX, tileCells); x <= Math.floorDiv(cloud.maxX, tileCells); x++) {
            for (int z = Math.floorDiv(cloud.minZ, tileCells); z <= Math.floorDiv(cloud.maxZ, tileCells); z++) {
                if (Double.isNaN(oldX) || crosses(x, z, tileCells, snapX, snapZ, closeDistance)
                        || !Double.isNaN(oldX) && crosses(x, z, tileCells, oldX, oldZ, oldClose)) {
                    set.add(pack(x, z));
                }
            }
        }
    }

    /** True when the close or the mid circle around (cx, cz) passes through the tile. */
    private static boolean crosses(int tileX, int tileZ, int tileCells, double cx, double cz, double close) {
        double size = tileCells * (double) CloudRenderer.CELL;
        double x0 = tileX * size;
        double z0 = tileZ * size;
        double nx = Math.max(0.0, Math.max(x0 - cx, cx - x0 - size));
        double nz = Math.max(0.0, Math.max(z0 - cz, cz - z0 - size));
        double near = Math.sqrt(nx * nx + nz * nz);
        double fx = Math.max(Math.abs(x0 - cx), Math.abs(x0 + size - cx));
        double fz = Math.max(Math.abs(z0 - cz), Math.abs(z0 + size - cz));
        double far = Math.sqrt(fx * fx + fz * fz);
        return near <= close && far >= close || near <= MID_DISTANCE && far >= MID_DISTANCE;
    }

    private static void mark(Set<Long> set, Cloud cloud, int tileCells) {
        for (int x = Math.floorDiv(cloud.minX, tileCells); x <= Math.floorDiv(cloud.maxX, tileCells); x++) {
            for (int z = Math.floorDiv(cloud.minZ, tileCells); z <= Math.floorDiv(cloud.maxZ, tileCells); z++) {
                set.add(pack(x, z));
            }
        }
    }

    private static void forgetFar() {
        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud cloud = it.next();
            if (rectDistance(cloud.minX, cloud.minZ, cloud.maxX, cloud.maxZ) > FORGET_DISTANCE) {
                for (long key : cloud.cells) {
                    CELLS.remove(key);
                }
                it.remove();
            }
        }
    }

    private static double cellDistance(int cellX, int cellZ) {
        return rectDistance(cellX, cellZ, cellX, cellZ);
    }

    /** Horizontal distance from the camera to the nearest point of a rectangle of cells. */
    private static double rectDistance(int minX, int minZ, int maxX, int maxZ) {
        double x0 = minX * (double) CloudRenderer.CELL;
        double z0 = minZ * (double) CloudRenderer.CELL;
        double x1 = (maxX + 1) * (double) CloudRenderer.CELL;
        double z1 = (maxZ + 1) * (double) CloudRenderer.CELL;
        double dx = Math.max(0.0, Math.max(x0 - camX, camX - x1));
        double dz = Math.max(0.0, Math.max(z0 - camZ, camZ - z1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double farthest(Cloud cloud) {
        double x0 = cloud.minX * (double) CloudRenderer.CELL;
        double z0 = cloud.minZ * (double) CloudRenderer.CELL;
        double x1 = (cloud.maxX + 1) * (double) CloudRenderer.CELL;
        double z1 = (cloud.maxZ + 1) * (double) CloudRenderer.CELL;
        double dx = Math.max(Math.abs(x0 - camX), Math.abs(x1 - camX));
        double dz = Math.max(Math.abs(z0 - camZ), Math.abs(z1 - camZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
