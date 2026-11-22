package com.hbmfarview.mixin.client;

import com.hbmfarview.cloud.CloudCompat;
import com.hbmfarview.cloud.CloudRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the vanilla (and Sodium) cloud drawing with the custom clouds. The call is cancelled
 * at the place where renderLevel makes it, so it does not matter which mod changes renderClouds.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererClouds {
    /**
     * Distant Horizons switches the vanilla clouds to Off, and renderLevel then never calls
     * renderClouds. With the custom clouds on, that Off is treated as On.
     */
    @ModifyExpressionValue(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;getCloudsType()Lnet/minecraft/client/CloudStatus;"),
            require = 0)
    private CloudStatus hbmfarview$cloudsNotOff(CloudStatus original) {
        return CloudCompat.overrideOff(original);
    }

    @WrapWithCondition(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V"),
            require = 0)
    private boolean hbmfarview$customClouds(LevelRenderer self, PoseStack stack, Matrix4f modelView,
                                            Matrix4f projection, float partialTick,
                                            double camX, double camY, double camZ) {
        if (!CloudCompat.enabled()) {
            return true;
        }
        CloudRenderer.render(stack, modelView, projection, partialTick, camX, camY, camZ, CloudCompat.drawnByDh());
        return false;
    }
}
