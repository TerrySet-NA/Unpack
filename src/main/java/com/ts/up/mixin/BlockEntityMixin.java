package com.ts.up.mixin;

import com.ts.up.client.ILootState;
import com.ts.up.client.LootClientSyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements ILootState {

    @Shadow public abstract Level getLevel();
    @Shadow public abstract BlockPos getBlockPos();
    @Shadow public abstract BlockState getBlockState();
    @Shadow public abstract boolean isRemoved();

    @Unique
    private boolean up$hasUnopenedLoot = false;

    @Override
    public boolean up$hasUnopenedLoot() {
        return this.up$hasUnopenedLoot;
    }

    @Override
    public void up$setHasUnopenedLoot(boolean hasLoot) {
        this.up$hasUnopenedLoot = hasLoot;
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void up$onGetUpdateTag(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (this instanceof RandomizableContainer) {
            cir.getReturnValue().putBoolean("UpHasLoot", this.up$hasUnopenedLoot);
        }
    }

    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void up$onGetUpdatePacket(CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        if (this instanceof RandomizableContainer) {
            BlockEntity self = (BlockEntity) (Object) this;
            cir.setReturnValue(ClientboundBlockEntityDataPacket.create(self, (be, reg) -> {
                CompoundTag tag = be.getUpdateTag(reg);
                tag.putBoolean("UpHasLoot", this.up$hasUnopenedLoot);
                return tag;
            }));
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void up$onLoadAdditional(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (this.getLevel() != null && this.getLevel().isClientSide && tag.contains("UpHasLoot")) {
            boolean newState = tag.getBoolean("UpHasLoot");
            if (this.up$hasUnopenedLoot != newState) {
                this.up$hasUnopenedLoot = newState;
                LootClientSyncHandler.refreshBlockRender(this.getLevel(), this.getBlockPos());
            }
        }
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void up$onSetLevel(Level level, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && this instanceof RandomizableContainer && this.up$hasUnopenedLoot) {
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
                if (!this.isRemoved() && this.up$hasUnopenedLoot) {
                    BlockState state = this.getBlockState();
                    serverLevel.sendBlockUpdated(this.getBlockPos(), state, state, 3);
                }
            }));
        }
    }
}