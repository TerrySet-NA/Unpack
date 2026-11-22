package com.ts.up.mixin;

import com.ts.up.client.TextureCaptureContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public class RenderTypeMixin {

    @Inject(method = "entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", at = @At("HEAD"))
    private static void up$onEntityCutout(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        TextureCaptureContext.recordTexture(location);
    }

    @Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;", at = @At("HEAD"))
    private static void up$onEntityCutoutNoCull(ResourceLocation location, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        TextureCaptureContext.recordTexture(location);
    }
}