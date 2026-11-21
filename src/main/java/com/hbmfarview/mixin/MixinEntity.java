package com.hbmfarview.mixin;

import com.hbmfarview.FarView;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @ModifyReturnValue(method = "isAlwaysTicking", at = @At("RETURN"))
    private boolean hbmfarview$clientTicks(boolean original) {
        if (original) {
            return true;
        }
        Entity self = (Entity) (Object) this;
        return self.level().isClientSide() && FarView.isShownFar(self);
    }
}
