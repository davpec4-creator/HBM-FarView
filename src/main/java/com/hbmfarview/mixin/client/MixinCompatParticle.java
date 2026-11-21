package com.hbmfarview.mixin.client;

import com.hbmfarview.FarViewClientConfig;
import com.hbmfarview.client.NoFogParticles;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.golem.core.render.CompatParticle")
public abstract class MixinCompatParticle {
    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"), require = 0)
    private ParticleRenderType hbmfarview$missileSmokeNoFog(ParticleRenderType original) {
        if (FarViewClientConfig.missileSmoke()
                && original == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                && this.getClass().getName().equals("com.hbm.client.particle.ParticleRocketFlame")) {
            return NoFogParticles.CONTRAIL;
        }
        return original;
    }
}
