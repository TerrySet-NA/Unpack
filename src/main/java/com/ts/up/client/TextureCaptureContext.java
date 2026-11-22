package com.ts.up.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ts.up.UpMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextureCaptureContext {

    private static final ThreadLocal<BlockEntity> CURRENT_BE = new ThreadLocal<>();
    private static final Map<Block, Map<ChestType, ResourceLocation>> CAPTURED_CHEST_TEXTURES = new ConcurrentHashMap<>();

    public static void begin(BlockEntity be) {
        CURRENT_BE.set(be);
    }

    public static void end() {
        CURRENT_BE.remove();
    }

    public static void recordTexture(ResourceLocation texturePath) {
        if (texturePath == null || texturePath.getNamespace().equals(UpMod.MOD_ID)) return;
        if (texturePath.getPath().startsWith("textures/atlas/")) return;

        BlockEntity be = CURRENT_BE.get();
        if (be instanceof ChestBlockEntity) {
            BlockState state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE)) {
                Block block = state.getBlock();
                ChestType type = state.getValue(ChestBlock.TYPE);
                CAPTURED_CHEST_TEXTURES
                        .computeIfAbsent(block, b -> new ConcurrentHashMap<>())
                        .putIfAbsent(type, texturePath);
            }
        }
    }

    public static ResourceLocation getRealChestTexture(BlockEntity be, Block block, ChestType type) {
        Map<ChestType, ResourceLocation> byType = CAPTURED_CHEST_TEXTURES.computeIfAbsent(block, b -> new ConcurrentHashMap<>());
        if (byType.containsKey(type)) {
            return byType.get(type);
        }

        // 1. 主動觸發一次該箱子渲染器（Quark 渲染大箱時通常一次提交兩張 Material，會順便捕獲到其中一邊）
        BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        if (renderer != null) {
            try {
                begin(be);
                renderer.render(be, 0f, new PoseStack(),
                        renderType -> new NoOpVertexConsumer(), 0, OverlayTexture.NO_OVERLAY);
            } catch (Throwable ignored) {
            } finally {
                end();
            }

            if (byType.containsKey(type)) {
                return byType.get(type);
            }
        }

        // 2. 因為大箱的 LEFT 格在渲染器中會直接 return 跳過繪製，若同方塊已捕獲到 RIGHT 或 SINGLE 的真實路徑，直接替換後綴
        String targetSuffix = switch (type) {
            case LEFT -> "_left.png";
            case RIGHT -> "_right.png";
            default -> ".png";
        };

        for (ResourceLocation knownPath : byType.values()) {
            String p = knownPath.getPath();
            String baseWithoutSuffix = p.replace("_left.png", ".png").replace("_right.png", ".png");
            String derivedPath = baseWithoutSuffix.substring(0, baseWithoutSuffix.length() - 4) + targetSuffix;
            ResourceLocation derived = ResourceLocation.fromNamespaceAndPath(knownPath.getNamespace(), derivedPath);

            if (Minecraft.getInstance().getResourceManager().getResource(derived).isPresent()) {
                byType.putIfAbsent(type, derived);
                return derived;
            }
        }

        // 3. 若連 SINGLE/RIGHT 都尚未渲染過，暫時用單箱狀態觸發一次渲染器以取得該木種的真實基礎路徑
        if (type != ChestType.SINGLE && renderer != null) {
            BlockState singleState = be.getBlockState().setValue(ChestBlock.TYPE, ChestType.SINGLE);
            BlockEntity tempSingleBe = block instanceof ChestBlock cb ? cb.newBlockEntity(be.getBlockPos(), singleState) : null;
            if (tempSingleBe != null) {
                try {
                    begin(tempSingleBe);
                    renderer.render(tempSingleBe, 0f, new PoseStack(),
                            renderType -> new NoOpVertexConsumer(), 0, OverlayTexture.NO_OVERLAY);
                } catch (Throwable ignored) {
                } finally {
                    end();
                }

                ResourceLocation singlePath = byType.get(ChestType.SINGLE);
                if (singlePath != null) {
                    String p = singlePath.getPath();
                    String derivedPath = p.substring(0, p.length() - 4) + targetSuffix;
                    ResourceLocation derived = ResourceLocation.fromNamespaceAndPath(singlePath.getNamespace(), derivedPath);
                    if (Minecraft.getInstance().getResourceManager().getResource(derived).isPresent()) {
                        byType.putIfAbsent(type, derived);
                        return derived;
                    }
                }
            }
        }

        // 4. 原版箱子回退
        Material vanillaMat = Sheets.chooseMaterial(be, type, false);
        ResourceLocation texId = vanillaMat.texture();
        ResourceLocation resolved = ResourceLocation.fromNamespaceAndPath(
                texId.getNamespace(),
                "textures/" + texId.getPath() + ".png"
        );
        byType.putIfAbsent(type, resolved);
        return resolved;
    }

    private static class NoOpVertexConsumer implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}