package com.ts.up.mixin;

import com.ts.up.client.TextureCaptureContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Material.class)
public class MaterialMixin {

    @Shadow @Final private ResourceLocation texture;

    @Inject(
        method = "buffer(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At("HEAD")
    )
    private void up$onMaterialBuffer(MultiBufferSource bufferSource, Function<ResourceLocation, RenderType> renderTypeGetter, CallbackInfoReturnable<?> cir) {
        if (this.texture != null) {
            ResourceLocation fullPng = ResourceLocation.fromNamespaceAndPath(
                    this.texture.getNamespace(),
                    "textures/" + this.texture.getPath() + ".png"
            );
            TextureCaptureContext.recordTexture(fullPng);
        }
    }
}