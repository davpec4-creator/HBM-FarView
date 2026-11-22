package com.hbmfarview.cloud;

import com.hbmfarview.FarViewClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CloudStatus;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Decides whether and how the custom clouds are drawn: the setting must be on and no other
 * cloud mod may be installed. With Distant Horizons they are drawn through it (see DhClouds),
 * otherwise by CloudRenderer.
 */
public final class CloudCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Mod ids of other cloud mods (unverified: "simpleclouds").
    private static final String[] OTHER_CLOUD_MODS = {"simpleclouds"};
    private static Boolean otherMod;
    private static Boolean dhLoaded;
    private static boolean dhFailed;

    private CloudCompat() {
    }

    public static boolean enabled() {
        if (!FarViewClientConfig.customClouds()) {
            CloudRenderer.clear();
            return false;
        }
        if (otherMod == null) {
            otherMod = false;
            for (String id : OTHER_CLOUD_MODS) {
                if (ModList.get().isLoaded(id)) {
                    otherMod = true;
                }
            }
        }
        return !otherMod;
    }

    /** True while Distant Horizons draws the far clouds; the normal renderer then only draws the close ones. */
    public static boolean drawnByDh() {
        if (dhFailed || !dhLoaded()) {
            return false;
        }
        try {
            DhClouds.bind();
            return DhClouds.active();
        } catch (Throwable t) {
            dhFailed = true;
            LOGGER.warn("[HBM Far View] Clouds through Distant Horizons are not available", t);
            return false;
        }
    }

    /**
     * Distant Horizons switches the vanilla clouds to Off. With the custom clouds on, that Off is treated
     * as On, so the close clouds are still drawn by the normal renderer.
     */
    public static CloudStatus overrideOff(CloudStatus original) {
        drawnByDh();
        if (original == CloudStatus.OFF && dhLoaded() && enabled()) {
            return CloudStatus.FAST;
        }
        return original;
    }

    static boolean dhLoaded() {
        if (dhLoaded == null) {
            dhLoaded = ModList.get().isLoaded("distanthorizons");
        }
        return dhLoaded;
    }
}
