package com.hbmfarview.mixin;

import com.hbmfarview.FarView;
import com.hbmfarview.FarViewConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MixinTrackedEntity {
    @Shadow
    @Final
    private Entity entity;

    @ModifyExpressionValue(
            method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private int hbmfarview$range(int original, ServerPlayer player) {
        if (!FarView.isFarVisible(this.entity)) {
            return original;
        }
        return Math.max(original, FarViewConfig.range());
    }

    @ModifyExpressionValue(
            method = "updatePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean hbmfarview$tracked(boolean original, ServerPlayer player) {
        return original || (FarViewConfig.range() > 0 && FarView.isFarVisible(this.entity));
    }
}
