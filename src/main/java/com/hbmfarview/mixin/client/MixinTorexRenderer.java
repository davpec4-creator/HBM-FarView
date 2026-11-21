package com.hbmfarview.mixin.client;

import com.hbmfarview.client.NoFogRenderTypes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.hbm.client.render.TorexRenderer")
public abstract class MixinTorexRenderer {
    @ModifyExpressionValue(
            method = "submitCloudlets",
            at = @At(value = "FIELD",
                    target = "Lcom/hbm/client/render/TorexRenderer;CLOUDLET_TYPE:Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType hbmfarview$noFog(RenderType original) {
        return NoFogRenderTypes.cloudlet();
    }
}