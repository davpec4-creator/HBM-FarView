package com.hbmfarview.cloud;

import com.hbmfarview.FarViewClientConfig;
import com.hbmfarview.HbmFarView;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What the player sees under a weather group. Only visual, nothing in the world changes.
 * Under a rain group the vanilla rain is drawn (see MixinLevelRendererRain). Under a fallout group thick dark
 * dust falls around the player and a dark grey fog closes in to a few blocks (falloutFogDistance), also over
 * the sky and the Distant Horizons terrain.
 */
@EventBusSubscriber(modid = HbmFarView.MOD_ID, value = Dist.CLIENT)
public final class WeatherEffects {
    private static final float DUST_PER_TICK = 40.0F;
    private static final int DUST_RANGE = 10;
    private static final float FOG_DARKEN = 0.9F;
    private static boolean dhFogFailed;
    private static final float[] FALLOUT_FOG = {0.16F, 0.15F, 0.14F};

    private static ParticleOptions greyDust;
    private static ParticleOptions blackDust;

    private WeatherEffects() {
    }

    /** Rain level for the vanilla rain drawing: the higher of the world rain and the rain group above the player. */
    public static float rainLevel(float original) {
        if (!CloudCompat.enabled()) {
            return original;
        }
        return Math.max(original, CloudWeather.localRain());
    }

    static void tick(Minecraft mc, ClientLevel level) {
        float fallout = CloudWeather.localFallout();
        dhFog(fallout);
        ParticleStatus particles = mc.options.particles().get();
        if (fallout <= 0.0F || particles == ParticleStatus.MINIMAL) {
            return;
        }
        if (greyDust == null) {
            greyDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState());
            blackDust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState());
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource random = level.random;
        float wanted = DUST_PER_TICK * fallout * (particles == ParticleStatus.DECREASED ? 0.5F : 1.0F);
        int count = (int) wanted + (random.nextFloat() < wanted - (int) wanted ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double x = cam.x + (random.nextDouble() * 2.0 - 1.0) * DUST_RANGE;
            double z = cam.z + (random.nextDouble() * 2.0 - 1.0) * DUST_RANGE;
            double y = cam.y + 1.0 + random.nextDouble() * 8.0;
            // Only under the open sky, not inside buildings.
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) {
                continue;
            }
            float pick = random.nextFloat();
            ParticleOptions dust = pick < 0.45F ? greyDust : pick < 0.85F ? blackDust : ParticleTypes.ASH;
            level.addParticle(dust, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** Pulls the fog in to falloutFogDistance blocks, smoothly as the player walks under the cloud. */
    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog event) {
        float fallout = CloudWeather.localFallout();
        if (fallout <= 0.0F || event.getType() != FogType.NONE || !CloudCompat.enabled()) {
            return;
        }
        float t = fallout * fallout * (3.0F - 2.0F * fallout);
        float far = FarViewClientConfig.falloutFogDistance();
        float end = Math.max(far, (float) Math.exp(Mth.lerp(t, Math.log(Math.max(far, event.getFarPlaneDistance())), Math.log(far))));
        event.setFarPlaneDistance(end);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), end * 0.1F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    /** Gives Distant Horizons its own fog back, for example when the player leaves the world. */
    static void reset() {
        dhFog(0.0F);
    }

    private static void dhFog(float fallout) {
        if (dhFogFailed || !CloudCompat.dhLoaded()) {
            return;
        }
        try {
            DhFalloutFog.update(fallout);
        } catch (Throwable t) {
            dhFogFailed = true;
        }
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        float fallout = CloudWeather.localFallout();
        if (fallout <= 0.0F || !CloudCompat.enabled()) {
            return;
        }
        float t = fallout * FOG_DARKEN;
        event.setRed(Mth.lerp(t, event.getRed(), FALLOUT_FOG[0]));
        event.setGreen(Mth.lerp(t, event.getGreen(), FALLOUT_FOG[1]));
        event.setBlue(Mth.lerp(t, event.getBlue(), FALLOUT_FOG[2]));
    }
}
