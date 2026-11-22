package com.ts.up.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LootClientSyncHandler {

    public static void refreshBlockRender(Level level, BlockPos pos) {
        if (level == null || !level.isClientSide) return;

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null) return;
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be != null) {
                be.requestModelDataUpdate();
            }
            BlockState state = mc.level.getBlockState(pos);
            mc.level.sendBlockUpdated(pos, state, state, 3);
            mc.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        });
    }
}