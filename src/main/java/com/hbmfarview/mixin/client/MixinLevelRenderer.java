package com.hbmfarview.mixin.client;

import com.hbmfarview.FarView;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @ModifyReturnValue(method = "isSectionCompiled", at = @At("RETURN"), require = 0)
    private boolean hbmfarview$farSections(boolean original, BlockPos pos) {
        Entity entity = FarView.takeChecked();
        return entity != null || original;
    }
}
