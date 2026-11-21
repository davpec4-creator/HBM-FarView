package com.hbmfarview.mixin.client;

import com.hbmfarview.FarView;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @ModifyReturnValue(method = "getDepthFar", at = @At("RETURN"), require = 0)
    private float hbmfarview$depthFar(float original) {
        if (!FarView.recentlySeen()) {
            return original;
        }
        return Math.max(original, FarView.farPlane());
    }
}
