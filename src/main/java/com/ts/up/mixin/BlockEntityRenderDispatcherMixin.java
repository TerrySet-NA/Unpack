package com.ts.up.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ts.up.client.TextureCaptureContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void up$beforeRenderBlockEntity(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        TextureCaptureContext.begin(blockEntity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends BlockEntity> void up$afterRenderBlockEntity(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        TextureCaptureContext.end();
    }
}