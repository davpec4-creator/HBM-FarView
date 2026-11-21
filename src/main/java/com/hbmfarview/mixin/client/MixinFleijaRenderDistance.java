package com.hbmfarview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = {
        "com.hbm.entity.effect.EntityCloudFleija",
        "com.hbm.entity.effect.EntityCloudFleijaRainbow"
})
public abstract class MixinFleijaRenderDistance {
    @ModifyReturnValue(method = "shouldRenderAtSqrDistance", at = @At("RETURN"), require = 0)
    private boolean hbmfarview$alwaysRender(boolean original) {
        return true;
    }
}
