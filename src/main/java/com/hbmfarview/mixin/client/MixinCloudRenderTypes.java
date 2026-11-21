package com.hbmfarview.mixin.client;

import com.hbmfarview.client.NoFogRenderTypes;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.hbm.client.render.entity.CloudRenderTypes")
public abstract class MixinCloudRenderTypes {
    @Shadow
    @Final
    @Mutable
    public static RenderType OPAQUE;

    @Shadow
    @Final
    @Mutable
    public static RenderType OPAQUE_UNCULLED;

    @Shadow
    @Final
    @Mutable
    public static RenderType TRANSLUCENT;

    @Shadow
    @Final
    @Mutable
    public static RenderType ADDITIVE;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0)
    private static void hbmfarview$noFog(CallbackInfo ci) {
        OPAQUE = NoFogRenderTypes.wrap(OPAQUE);
        OPAQUE_UNCULLED = NoFogRenderTypes.wrap(OPAQUE_UNCULLED);
        TRANSLUCENT = NoFogRenderTypes.wrap(TRANSLUCENT);
        ADDITIVE = NoFogRenderTypes.wrap(ADDITIVE);
    }
}
