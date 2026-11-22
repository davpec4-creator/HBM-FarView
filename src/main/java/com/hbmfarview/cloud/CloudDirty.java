package com.hbmfarview.cloud;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiles that must be rebuilt because the clouds in them changed (missile holes, the funnel, weather).
 * Changes are collected and handed to the renderers every few ticks, so a flying missile or a growing
 * storm does not rebuild the same tiles every tick. Each renderer takes its own set.
 */
final class CloudDirty {
    private static final int MAX_WAITING = 8192;

    private static final Set<Long> PENDING_DH = new HashSet<>();
    private static final Set<Long> PENDING_MC = new HashSet<>();
    private static final Set<Long> DIRTY_DH = new HashSet<>();
    private static final Set<Long> DIRTY_MC = new HashSet<>();
    private static boolean pendingAll;
    private static boolean allDh;
    private static boolean allMc;

    private CloudDirty() {
    }

    /** One cell changed. A cell on the edge of a tile also changes the faces of the neighbouring tile. */
    static void markCell(int cellX, int cellZ) {
        markArea(cellX, cellZ, cellX, cellZ);
    }

    /** All cells from min to max (inclusive) changed. */
    static void markArea(int minX, int minZ, int maxX, int maxZ) {
        mark(PENDING_MC, minX - 1, minZ - 1, maxX + 1, maxZ + 1, CloudRenderer.TILE_CELLS);
        mark(PENDING_DH, minX - 1, minZ - 1, maxX + 1, maxZ + 1, CloudLod.DH_TILE_CELLS);
    }

    /** The whole sky changed, for example when it starts to rain. */
    static void markAll() {
        pendingAll = true;
    }

    /** Hands the collected changes to the renderers. */
    static void flush() {
        if (pendingAll) {
            allDh = true;
            allMc = true;
            pendingAll = false;
            PENDING_DH.clear();
            PENDING_MC.clear();
        }
        DIRTY_DH.addAll(PENDING_DH);
        DIRTY_MC.addAll(PENDING_MC);
        PENDING_DH.clear();
        PENDING_MC.clear();
        // Without Distant Horizons nobody takes the DH set.
        if (DIRTY_DH.size() > MAX_WAITING) {
            DIRTY_DH.clear();
            allDh = true;
        }
        if (DIRTY_MC.size() > MAX_WAITING) {
            DIRTY_MC.clear();
            allMc = true;
        }
    }

    static void clear() {
        PENDING_DH.clear();
        PENDING_MC.clear();
        DIRTY_DH.clear();
        DIRTY_MC.clear();
        pendingAll = false;
        allDh = false;
        allMc = false;
    }

    static boolean takeAllDh() {
        boolean all = allDh;
        allDh = false;
        return all;
    }

    static boolean takeAllMc() {
        boolean all = allMc;
        allMc = false;
        return all;
    }

    static Set<Long> takeDh() {
        Set<Long> copy = new HashSet<>(DIRTY_DH);
        DIRTY_DH.clear();
        return copy;
    }

    static Set<Long> takeMc() {
        Set<Long> copy = new HashSet<>(DIRTY_MC);
        DIRTY_MC.clear();
        return copy;
    }

    private static void mark(Set<Long> set, int minX, int minZ, int maxX, int maxZ, int tileCells) {
        for (int x = Math.floorDiv(minX, tileCells); x <= Math.floorDiv(maxX, tileCells); x++) {
            for (int z = Math.floorDiv(minZ, tileCells); z <= Math.floorDiv(maxZ, tileCells); z++) {
                set.add(CloudLod.pack(x, z));
            }
        }
    }
}
