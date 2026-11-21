package com.hbmfarview;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = HbmFarView.MOD_ID)
public final class FarViewLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FarViewLog() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        log("joined", event.getEntity());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        log("left", event.getEntity());
    }

    private static void log(String action, Entity entity) {
        if (!FarView.isFarVisible(entity)) {
            return;
        }
        Player player = entity.level().getNearestPlayer(entity, -1.0);
        String distance = player == null ? "?" : String.valueOf(Math.round(player.distanceTo(entity)));
        LOGGER.info("[HBM Far View] {} {} on {} (age {}, distance {})",
                entity.getType().builtInRegistryHolder().key().location(), action,
                entity.level().isClientSide() ? "client" : "server", entity.tickCount, distance);
    }
}
