package com.hbmfarview.cloud;

import com.hbmfarview.FarView;
import com.hbmfarview.HbmFarView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * One client tick for everything that changes the custom clouds: missiles cutting through them, nuclear
 * explosions pushing the clouds away and starting the fallout, the weather, and the effects under the clouds. Changed tiles are handed to
 * the renderers every few ticks.
 */
@EventBusSubscriber(modid = HbmFarView.MOD_ID, value = Dist.CLIENT)
public final class CloudTicker {
    private static final int FLUSH_TICKS = 4;

    private static ClientLevel lastLevel;
    private static long ticks;

    private CloudTicker() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            CloudHoles.reset();
            CloudBlasts.reset();
            CloudWeather.reset();
            CloudDirty.clear();
            lastLevel = level;
        }
        if (level == null || !CloudCompat.enabled()) {
            WeatherEffects.reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        float height = level.effects().getCloudHeight();
        if (Float.isNaN(height)) {
            return;
        }
        ticks++;
        long gameTime = level.getGameTime();
        double wind = gameTime * CloudRenderer.WIND_BLOCKS_PER_TICK;
        double cloudY = height + 0.33;
        for (Entity entity : level.entitiesForRendering()) {
            if (FarView.isMissile(entity)) {
                CloudHoles.cut(entity, wind, cloudY);
            } else if (FarView.isNuke(entity)) {
                CloudWeather.onNuke(entity, gameTime);
                CloudBlasts.onNuke(entity, gameTime, wind);
            }
        }
        CloudHoles.tick();
        CloudBlasts.tick(level, gameTime);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        CloudWeather.tick(level, gameTime, cam.x - wind, cam.y, cam.z, cloudY);
        WeatherEffects.tick(mc, level);
        if (ticks % FLUSH_TICKS == 0) {
            CloudDirty.flush();
        }
    }
}
