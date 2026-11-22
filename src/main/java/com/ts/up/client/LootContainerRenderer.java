package com.ts.up.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ts.up.UpMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.List;

@EventBusSubscriber(modid = UpMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class LootContainerRenderer {

    private static final ResourceLocation OVERLAY_CHEST_NORMAL = ResourceLocation.fromNamespaceAndPath(UpMod.MOD_ID, "textures/overlay/normal.png");
    private static final ResourceLocation OVERLAY_CHEST_LEFT = ResourceLocation.fromNamespaceAndPath(UpMod.MOD_ID, "textures/overlay/normal_left.png");
    private static final ResourceLocation OVERLAY_CHEST_RIGHT = ResourceLocation.fromNamespaceAndPath(UpMod.MOD_ID, "textures/overlay/normal_right.png");
    private static final ResourceLocation OVERLAY_SHULKER = ResourceLocation.fromNamespaceAndPath(UpMod.MOD_ID, "textures/overlay/shulker.png");
    private static final ResourceLocation OVERLAY_BLOCK = ResourceLocation.fromNamespaceAndPath(UpMod.MOD_ID, "textures/block/loot_overlay.png");

    private static ModelPart singleLid, singleBottom, singleLock;
    private static ModelPart leftLid, leftBottom, leftLock;
    private static ModelPart rightLid, rightBottom, rightLock;

    private static void ensureChestModels() {
        if (singleLid != null) return;
        var entityModels = Minecraft.getInstance().getEntityModels();

        ModelPart singleRoot = entityModels.bakeLayer(ModelLayers.CHEST);
        singleLid = singleRoot.getChild("lid");
        singleBottom = singleRoot.getChild("bottom");
        singleLock = singleRoot.getChild("lock");

        ModelPart leftRoot = entityModels.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT);
        leftLid = leftRoot.getChild("lid");
        leftBottom = leftRoot.getChild("bottom");
        leftLock = leftRoot.getChild("lock");

        ModelPart rightRoot = entityModels.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT);
        rightLid = rightRoot.getChild("lid");
        rightBottom = rightRoot.getChild("bottom");
        rightLock = rightRoot.getChild("lock");
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        int renderDistChunks = Math.min(mc.options.getEffectiveRenderDistance(), 8);
        ChunkPos centerChunk = new ChunkPos(camera.getBlockPosition());

        for (int dx = -renderDistChunks; dx <= renderDistChunks; dx++) {
            for (int dz = -renderDistChunks; dz <= renderDistChunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(centerChunk.x + dx, centerChunk.z + dz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ILootState lootState) || !lootState.up$hasUnopenedLoot()) continue;
                    if (!(be instanceof RandomizableContainer)) continue;

                    BlockPos pos = be.getBlockPos();
                    if (pos.distToCenterSqr(camPos.x, camPos.y, camPos.z) > 64 * 64) continue;

                    poseStack.pushPose();
                    poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);

                    renderMergedContainer(level, pos, be, poseStack, bufferSource);

                    poseStack.popPose();
                }
            }
        }

        bufferSource.endBatch();
    }

    private static void renderMergedContainer(ClientLevel level, BlockPos pos, BlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource) {
        BlockState state = be.getBlockState();
        Block block = state.getBlock();

        if (be instanceof ChestBlockEntity && state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)) {
            int light = LevelRenderer.getLightColor(level, pos);
            renderMergedChest(be, state, block, poseStack, bufferSource, light);
            return;
        }

        if (be instanceof ShulkerBoxBlockEntity && block instanceof ShulkerBoxBlock shulkerBlock) {
            int light = LevelRenderer.getLightColor(level, pos);
            renderMergedShulker(state, shulkerBlock, poseStack, bufferSource, light);
            return;
        }

        renderMergedGenericBlock(level, pos, state, poseStack, bufferSource);
    }

    private static void renderMergedChest(BlockEntity be, BlockState state, Block block, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        ensureChestModels();

        ChestType type = state.getValue(ChestBlock.TYPE);
        Direction facing = state.getValue(ChestBlock.FACING);

        // 直接取得該箱子真實使用的底圖路徑（不猜測、不硬編碼）
        ResourceLocation baseChestTex = TextureCaptureContext.getRealChestTexture(be, block, type);

        ResourceLocation overlayTex = switch (type) {
            case LEFT -> OVERLAY_CHEST_LEFT;
            case RIGHT -> OVERLAY_CHEST_RIGHT;
            default -> OVERLAY_CHEST_NORMAL;
        };

        ResourceLocation mergedTex = RuntimeTextureCache.getOrMerge(baseChestTex, overlayTex);

        ModelPart lid, bottom, lock;
        switch (type) {
            case LEFT -> { lid = leftLid; bottom = leftBottom; lock = leftLock; }
            case RIGHT -> { lid = rightLid; bottom = rightBottom; lock = rightLock; }
            default -> { lid = singleLid; bottom = singleBottom; lock = singleLock; }
        }

        float openProgress = 0f;
        if (be instanceof LidBlockEntity lidBe) {
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            openProgress = lidBe.getOpenNess(partialTick);
            openProgress = 1.0F - openProgress;
            openProgress = 1.0F - openProgress * openProgress * openProgress;
        }
        float lidAngle = -(openProgress * ((float) Math.PI / 2F));
        lid.xRot = lidAngle;
        lock.xRot = lidAngle;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.4375F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (type == ChestType.SINGLE) {
            poseStack.scale(1.002F, 1.002F, 1.002F);
        } else {
            poseStack.scale(1.0005F, 1.002F, 1.002F);
        }
        poseStack.translate(-0.5F, -0.4375F, -0.5F);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(mergedTex));
        bottom.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        lid.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        lock.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    private static void renderMergedShulker(BlockState state, ShulkerBoxBlock shulkerBlock, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        Direction facing = state.getValue(ShulkerBoxBlock.FACING);
        DyeColor color = shulkerBlock.getColor();

        String texName = (color == null) ? "shulker" : "shulker_" + color.getName();
        ResourceLocation baseShulkerTex = ResourceLocation.withDefaultNamespace("textures/entity/shulker/" + texName + ".png");
        ResourceLocation mergedTex = RuntimeTextureCache.getOrMerge(baseShulkerTex, OVERLAY_SHULKER);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(facing.getRotation());
        poseStack.translate(-0.5, -0.5, -0.5);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(mergedTex));
        Matrix4f mat = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        float e = 0.002f;
        float min = -e, max = 1f + e;

        addQuad(vc, mat, pose, min, max, max, max, max, max, max, max, min, min, max, min, 16f/64f, 0f, 32f/64f, 16f/64f, 0, 1, 0, light);
        addQuad(vc, mat, pose, min, min, min, max, min, min, max, min, max, min, min, max, 32f/64f, 28f/64f, 48f/64f, 44f/64f, 0, -1, 0, light);
        addQuad(vc, mat, pose, min, min, max, max, min, max, max, max, max, min, max, max, 16f/64f, 16f/64f, 32f/64f, 28f/64f, 0, 0, 1, light);
        addQuad(vc, mat, pose, max, min, min, min, min, min, min, max, min, max, max, min, 48f/64f, 16f/64f, 64f/64f, 28f/64f, 0, 0, -1, light);
        addQuad(vc, mat, pose, min, min, min, min, min, max, min, max, max, min, max, min, 0f, 16f/64f, 16f/64f, 28f/64f, -1, 0, 0, light);
        addQuad(vc, mat, pose, max, min, max, max, min, min, max, max, min, max, max, max, 32f/64f, 16f/64f, 48f/64f, 28f/64f, 1, 0, 0, light);

        poseStack.popPose();
    }

    private static void renderMergedGenericBlock(ClientLevel level, BlockPos pos, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        RandomSource rand = RandomSource.create(42L);

        VoxelShape shape = state.getShape(level, pos);
        boolean isFullBlock = Block.isShapeFullBlock(shape);
        AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        box = box.inflate(0.002);

        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        Matrix4f mat = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        int selfLight = LevelRenderer.getLightColor(level, pos);

        for (Direction dir : Direction.values()) {
            int faceLight = isFullBlock ? LevelRenderer.getLightColor(level, pos.relative(dir)) : selfLight;

            List<BakedQuad> quads = model.getQuads(state, dir, rand, ModelData.EMPTY, null);
            TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
            ResourceLocation spriteName = sprite.contents().name();

            ResourceLocation basePng = ResourceLocation.fromNamespaceAndPath(
                    spriteName.getNamespace(),
                    "textures/" + spriteName.getPath() + ".png"
            );
            ResourceLocation mergedTex = RuntimeTextureCache.getOrMerge(basePng, OVERLAY_BLOCK);
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(mergedTex));

            switch (dir) {
                case DOWN -> addQuad(vc, mat, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0f, 0f, 1f, 1f, 0, -1, 0, faceLight);
                case UP -> addQuad(vc, mat, pose, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, 0f, 0f, 1f, 1f, 0, 1, 0, faceLight);
                case NORTH -> addQuad(vc, mat, pose, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0f, 0f, 1f, 1f, 0, 0, -1, faceLight);
                case SOUTH -> addQuad(vc, mat, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0f, 0f, 1f, 1f, 0, 0, 1, faceLight);
                case WEST -> addQuad(vc, mat, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, 0f, 0f, 1f, 1f, -1, 0, 0, faceLight);
                case EAST -> addQuad(vc, mat, pose, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 0f, 0f, 1f, 1f, 1, 0, 0, faceLight);
            }
        }
    }

    private static void addQuad(VertexConsumer vc, Matrix4f mat, PoseStack.Pose pose,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float u0, float v0, float u1, float v1,
                                float nx, float ny, float nz, int light) {
        vc.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x4, y4, z4).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}