package com.hbmfarview.mixin.client;

import com.hbmfarview.FarViewClientConfig;
import com.hbmfarview.FarViewConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NTM draws the missile smoke only within 350 blocks of the player. This raises that limit to the far tracking
 * range. Beyond 350 blocks the smoke is spawned less often and the puffs are larger, so the trail stays visible
 * without creating a particle every tick.
 */
@Mixin(targets = "com.hbm.client.particle.HbmParticleHandlers")
public abstract class MixinHbmParticleHandlers {
    private static final double NEAR = 350.0;
    private static int counter;

    @ModifyConstant(method = "spawnContrail", constant = @Constant(doubleValue = 122500.0), require = 0)
    private static double hbmfarview$limit(double original) {
        int range = FarViewConfig.range();
        return FarViewClientConfig.missileSmoke() && range > NEAR ? (double) range * range : original;
    }

    @Inject(method = "spawnContrail", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hbmfarview$thin(Level level, double x, double y, double z, CompoundTag tag, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !FarViewClientConfig.missileSmoke()) {
            return;
        }
        double ratio = Math.sqrt(player.distanceToSqr(x, y, z)) / NEAR;
        if (ratio <= 1.0) {
            return;
        }
        if (++counter % (int) Math.ceil(ratio) != 0) {
            ci.cancel();
            return;
        }
        float scale = tag.contains("scale") ? tag.getFloat("scale") : 1.0F;
        tag.putFloat("scale", scale * (float) Math.min(ratio, 6.0));
    }
}
