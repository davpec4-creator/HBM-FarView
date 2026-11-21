package com.hbmfarview.client;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;

/**
 * The only class that touches the Distant Horizons API. It is loaded only when Distant Horizons is installed.
 */
final class DhOcclusion {
    private static IDhApiTerrainDataCache cache;

    private DhOcclusion() {
    }

    /**
     * Returns true when Distant Horizons terrain lies between the start and the target point.
     */
    static boolean isBlocked(String dimension, double fromX, double fromY, double fromZ,
                             double toX, double toY, double toZ) {
        IDhApiTerrainDataRepo repo = DhApi.Delayed.terrainRepo;
        if (repo == null || DhApi.Delayed.worldProxy == null || !DhApi.Delayed.worldProxy.worldLoaded()) {
            return false;
        }
        IDhApiLevelWrapper level = findLevel(dimension);
        if (level == null) {
            return false;
        }
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0) {
            return false;
        }
        if (cache == null) {
            cache = repo.createSoftCache();
        }
        DhApiResult<DhApiRaycastResult> result = repo.raycast(level, fromX, fromY, fromZ,
                (float) (dx / distance), (float) (dy / distance), (float) (dz / distance),
                (int) Math.ceil(distance), cache);
        if (!result.success || result.payload == null) {
            return false;
        }
        DhApiVec3i hit = result.payload.pos;
        double hx = hit.x + 0.5 - fromX;
        double hy = hit.y + 0.5 - fromY;
        double hz = hit.z + 0.5 - fromZ;
        double hitDistance = Math.sqrt(hx * hx + hy * hy + hz * hz);
        return hitDistance < distance - margin(distance);
    }

    static void clearCache() {
        if (cache != null) {
            cache.clear();
        }
    }

    private static double margin(double distance) {
        return Math.max(16.0, distance * 0.02);
    }

    private static IDhApiLevelWrapper findLevel(String dimension) {
        try {
            for (IDhApiLevelWrapper level : DhApi.Delayed.worldProxy.getAllLoadedLevelsWithDimensionNameLike(dimension)) {
                return level;
            }
        } catch (IllegalStateException ignored) {
        }
        try {
            return DhApi.Delayed.worldProxy.getSinglePlayerLevel();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
