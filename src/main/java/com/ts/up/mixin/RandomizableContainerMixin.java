package com.ts.up.mixin;

import com.ts.up.client.ILootState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomizableContainer.class)
public interface RandomizableContainerMixin {

    @Inject(method = "tryLoadLootTable", at = @At("RETURN"))
    private void up$onTryLoadLootTable(CompoundTag tag, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && this instanceof ILootState state) {
            state.up$setHasUnopenedLoot(true);
        }
    }

    @Inject(method = "setLootTable(Lnet/minecraft/resources/ResourceKey;J)V", at = @At("TAIL"))
    private void up$onSetLootTable(@Nullable ResourceKey<LootTable> lootTable, long seed, CallbackInfo ci) {
        if (this instanceof ILootState state && this instanceof BlockEntity be) {
            boolean hasLoot = lootTable != null;
            state.up$setHasUnopenedLoot(hasLoot);
            Level level = be.getLevel();
            if (level != null && !level.isClientSide) {
                BlockState blockState = be.getBlockState();
                level.sendBlockUpdated(be.getBlockPos(), blockState, blockState, 3);
            }
        }
    }

    @Inject(
        method = "unpackLootTable",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/RandomizableContainer;setLootTable(Lnet/minecraft/resources/ResourceKey;)V",
            shift = At.Shift.AFTER
        )
    )
    private void up$onUnpackLootTable(@Nullable Player player, CallbackInfo ci) {
        if (this instanceof ILootState state && this instanceof BlockEntity be) {
            state.up$setHasUnopenedLoot(false);
            Level level = be.getLevel();
            if (level != null && !level.isClientSide) {
                BlockState blockState = be.getBlockState();
                level.sendBlockUpdated(be.getBlockPos(), blockState, blockState, 3);
            }
        }
    }
}