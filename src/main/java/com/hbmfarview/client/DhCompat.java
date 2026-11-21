package com.hbmfarview.client;

import com.hbmfarview.FarView;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Distant Horizons support. Every call into Distant Horizons classes goes through here, so nothing of it is loaded
 * when Distant Horizons is not installed, and any error turns the support off instead of crashing the game.
 *
 * Without a shader pack the depth of the Distant Horizons terrain is written before entities are drawn
 * (see DhDepthPass), so hills hide the cloud pixel by pixel. With a shader pack that is not possible, and the cloud
 * is only hidden as a whole when terrain covers it completely (see DhOcclusion).
 */
public final class DhCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DH_ID = "distanthorizons";
    private static final String FADE_CLASS =
            "com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Quality";
    private static final long CHECK_NANOS = 1_000_000_000L;
    private static final long OCCLUSION_NANOS = 250_000_000L;
    private static final double MIN_DISTANCE = 128.0;
    private static final double[] SAMPLE_HEIGHTS = {8.0, 64.0, 160.0, 320.0};

    private static final Map<Integer, Entry> CACHE = new HashMap<>();
    private static Boolean loaded;
    private static boolean depthFailed;
    private static boolean occlusionFailed;

    private static long lastCheck;
    private static boolean fadeOn;
    private static String fadeName = "?";
    private static boolean shaderPack;
    private static long lastDepthPass;

    private DhCompat() {
    }

    private record Entry(long time, boolean hidden) {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(DH_ID);
        }
        return loaded;
    }

    /**
     * Called after Minecraft has drawn its terrain and before it draws entities.
     */
    public static void beforeEntities(Matrix4f projection) {
        if (depthFailed || !isLoaded()) {
            return;
        }
        try {
            DhDepthPass.bindEvents();
            if (!FarView.recentlySeen()) {
                return;
            }
            refresh();
            if (fadeOn || shaderPack) {
                return;
            }
            if (DhDepthPass.run(projection)) {
                lastDepthPass = System.nanoTime();
            }
        } catch (Throwable t) {
            depthFailed = true;
            LOGGER.warn("[HBM Far View] Distant Horizons depth pass failed, disabling it", t);
        }
    }

    public static boolean fadeEnabled() {
        refresh();
        return fadeOn;
    }

    public static String fadeName() {
        return fadeName;
    }

    /**
     * True when terrain shown by Distant Horizons hides the whole effect. Only used while the depth pass is not running.
     */
    public static boolean isHidden(Entity entity, double camX, double camY, double camZ) {
        if (occlusionFailed || !isLoaded()) {
            return false;
        }
        long now = System.nanoTime();
        if (now - lastDepthPass < CHECK_NANOS) {
            return false;
        }
        Entry entry = CACHE.get(entity.getId());
        if (entry != null && now - entry.time() < OCCLUSION_NANOS) {
            return entry.hidden();
        }
        if (CACHE.size() > 64) {
            CACHE.clear();
        }
        boolean hidden = coveredByTerrain(entity, camX, camY, camZ);
        CACHE.put(entity.getId(), new Entry(now, hidden));
        return hidden;
    }

    public static void reset() {
        CACHE.clear();
        lastCheck = 0L;
        if (isLoaded() && !occlusionFailed) {
            try {
                DhOcclusion.clearCache();
            } catch (Throwable ignored) {
            }
        }
    }

    static void warn(String message) {
        LOGGER.warn("[HBM Far View] {}", message);
    }

    private static boolean coveredByTerrain(Entity entity, double camX, double camY, double camZ) {
        double dx = entity.getX() - camX;
        double dz = entity.getZ() - camZ;
        if (Math.sqrt(dx * dx + dz * dz) < MIN_DISTANCE) {
            return false;
        }
        String dimension = entity.level().dimension().location().getPath();
        try {
            for (double height : SAMPLE_HEIGHTS) {
                if (!DhOcclusion.isBlocked(dimension, camX, camY, camZ,
                        entity.getX(), entity.getY() + height, entity.getZ())) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            occlusionFailed = true;
            LOGGER.warn("[HBM Far View] Distant Horizons terrain check failed, disabling it", t);
            return false;
        }
    }

    private static void refresh() {
        long now = System.nanoTime();
        if (now - lastCheck < CHECK_NANOS) {
            return;
        }
        lastCheck = now;
        fadeOn = readFade();
        shaderPack = readShaderPack();
    }

    /**
     * The fade setting is not in the public Distant Horizons API, so it is read from its config class.
     */
    private static boolean readFade() {
        try {
            Field field = Class.forName(FADE_CLASS).getField("vanillaFadeMode");
            Object entry = field.get(null);
            Object value = entry.getClass().getMethod("get").invoke(entry);
            if (value instanceof Enum<?> mode) {
                fadeName = mode.name();
                return !fadeName.equals("NONE");
            }
            fadeName = "?";
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fadeName = "unreadable";
            return false;
        }
    }

    private static boolean readShaderPack() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method inUse = api.getMethod("isShaderPackInUse");
            return Boolean.TRUE.equals(inUse.invoke(instance));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
