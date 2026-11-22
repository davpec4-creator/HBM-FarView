package com.hbmfarview.mixin.client;

import com.hbmfarview.cloud.WeatherEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the vanilla rain (drops, splashes and sound) appear under a rain group of the custom clouds, even when
 * it does not rain in the world. Only the drawing reads this value; the world itself stays dry.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererRain {
    @ModifyExpressionValue(method = {"renderSnowAndRain", "tickRain"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"),
            require = 0)
    private float hbmfarview$localRain(float original) {
        return WeatherEffects.rainLevel(original);
    }
}
