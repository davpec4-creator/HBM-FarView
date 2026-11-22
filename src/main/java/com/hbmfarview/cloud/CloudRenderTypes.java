package com.hbmfarview.cloud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types of the custom clouds. They extend RenderType only to reach its protected
 * building blocks. Like vanilla clouds, they draw twice: depth only, then colour. A third type draws the
 * cells that are fading in or out.
 */
public abstract class CloudRenderTypes extends RenderType {
    private static final RenderType DEPTH_ONLY = create(
            "hbm_farview_clouds_depth",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            786432,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(DEPTH_WRITE)
                    .setOutputState(CLOUDS_TARGET)
                    .createCompositeState(false));

    private static final RenderType COLOR = create(
            "hbm_farview_clouds",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            786432,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setOutputState(CLOUDS_TARGET)
                    .createCompositeState(false));

    /** Cells that fade in or out: color only, so they do not hide the clouds behind them. */
    private static final RenderType FADE = create(
            "hbm_farview_clouds_fade",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            786432,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(CLOUDS_TARGET)
                    .createCompositeState(false));

    private CloudRenderTypes() {
        super("", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 0, false, false, () -> {
        }, () -> {
        });
    }

    public static RenderType depthOnly() {
        return DEPTH_ONLY;
    }

    public static RenderType color() {
        return COLOR;
    }

    public static RenderType fade() {
        return FADE;
    }
}
