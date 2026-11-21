package com.hbmfarview.mixin.client;

import com.hbmfarview.FarView;
import com.hbmfarview.client.DhCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean hbmfarview$alwaysInView(boolean original, Entity entity, Frustum frustum,
                                            double camX, double camY, double camZ) {
        if (!FarView.isShownFar(entity)) {
            return original;
        }
        return !DhCompat.isHidden(entity, camX, camY, camZ);
    }
}
