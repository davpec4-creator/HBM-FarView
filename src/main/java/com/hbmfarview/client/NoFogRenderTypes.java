package com.hbmfarview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public abstract class NoFogRenderTypes extends RenderType {
    private static final ResourceLocation CLOUDLET_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hbm", "textures/particle/particle_base.png");
    private static final float FOG_OFF = 1.0E8F;
    private static float savedStart;
    private static float savedEnd;

    private static final Map<RenderType, RenderType> WRAPPED = new ConcurrentHashMap<>();

    private static final TexturingStateShard NO_FOG = new TexturingStateShard(
            "hbm_farview_no_fog",
            NoFogRenderTypes::fogOff,
            NoFogRenderTypes::fogRestore);

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(texture ->
            create("hbm_farview_translucent",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    true,
                    true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setTexturingState(NO_FOG)
                            .createCompositeState(true)));

    private NoFogRenderTypes() {
        super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {
        }, () -> {
        });
    }

    private static void fogOff() {
        savedStart = RenderSystem.getShaderFogStart();
        savedEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(FOG_OFF);
        RenderSystem.setShaderFogEnd(FOG_OFF);
    }

    private static void fogRestore() {
        RenderSystem.setShaderFogStart(savedStart);
        RenderSystem.setShaderFogEnd(savedEnd);
    }

    public static RenderType cloudlet() {
        return TRANSLUCENT.apply(CLOUDLET_TEXTURE);
    }

    public static RenderType wrap(RenderType original) {
        return WRAPPED.computeIfAbsent(original, NoFogRenderTypes::createWrapper);
    }

    private static RenderType createWrapper(RenderType original) {
        return new RenderType(
                "hbm_farview_" + original,
                original.format(),
                original.mode(),
                original.bufferSize(),
                original.affectsCrumbling(),
                original.sortOnUpload(),
                () -> {
                    original.setupRenderState();
                    fogOff();
                },
                () -> {
                    fogRestore();
                    original.clearRenderState();
                }) {
        };
    }
}
