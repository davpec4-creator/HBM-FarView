package com.hbmfarview.client;

import com.hbmfarview.HbmFarView;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Tells the player to turn off Distant Horizons "Vanilla Fade Mode", which hides the mushroom cloud.
 * The setting is checked every two seconds, so the hint also appears when it is changed during play.
 * The setting is only read, never changed.
 */
@EventBusSubscriber(modid = HbmFarView.MOD_ID, value = Dist.CLIENT)
public final class DhFadeHint {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int JOIN_DELAY_TICKS = 100;
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static int countdown = -1;
    private static boolean shown;
    private static String lastName = "";

    private DhFadeHint() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        DhCompat.reset();
        shown = false;
        lastName = "";
        countdown = DhCompat.isLoaded() ? JOIN_DELAY_TICKS : -1;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        countdown = -1;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (countdown < 0 || --countdown > 0) {
            return;
        }
        countdown = CHECK_INTERVAL_TICKS;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean fade = DhCompat.fadeEnabled();
        String name = DhCompat.fadeName();
        if (!name.equals(lastName)) {
            lastName = name;
            LOGGER.info("[HBM Far View] Distant Horizons Vanilla Fade Mode: {}", name);
        }
        if (!fade) {
            shown = false;
        } else if (!shown) {
            shown = true;
            mc.player.displayClientMessage(Component.literal(
                    "[HBM Far View] Distant Horizons hides the mushroom cloud while Vanilla Fade Mode is on. "
                            + "Set Distant Horizons > Advanced Options > Graphics > Quality > Vanilla Fade Mode to None."),
                    false);
        }
    }
}
