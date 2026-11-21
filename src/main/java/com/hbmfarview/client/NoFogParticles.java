package com.hbmfarview.client;

import com.hbmfarview.HbmFarView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Render type for the NTM missile contrail. It draws like the vanilla translucent particles, but with the fog
 * switched off, so the trail stays visible beyond the render distance. The fog is restored after all particles.
 */
@EventBusSubscriber(modid = HbmFarView.MOD_ID, value = Dist.CLIENT)
public final class NoFogParticles {
    private static final float FOG_OFF = 1.0E8F;

    public static final ParticleRenderType CONTRAIL = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            BufferBuilder builder = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(tesselator, textureManager);
            fogOff();
            return builder;
        }

        @Override
        public String toString() {
            return "hbm_farview_contrail";
        }
    };

    private static boolean off;
    private static float savedStart;
    private static float savedEnd;

    private NoFogParticles() {
    }

    private static void fogOff() {
        if (!off) {
            savedStart = RenderSystem.getShaderFogStart();
            savedEnd = RenderSystem.getShaderFogEnd();
            off = true;
        }
        RenderSystem.setShaderFogStart(FOG_OFF);
        RenderSystem.setShaderFogEnd(FOG_OFF);
    }

    @SubscribeEvent
    public static void afterParticles(RenderLevelStageEvent event) {
        if (off && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            RenderSystem.setShaderFogStart(savedStart);
            RenderSystem.setShaderFogEnd(savedEnd);
            off = false;
        }
    }
}
