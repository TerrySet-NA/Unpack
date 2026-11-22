package com.ts.up.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.ts.up.UpMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid = UpMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RuntimeTextureCache {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ResourceLocation> MERGED_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Minecraft mc = Minecraft.getInstance();
            for (ResourceLocation loc : MERGED_CACHE.values()) {
                mc.getTextureManager().release(loc);
            }
            MERGED_CACHE.clear();
        });
    }

    public static ResourceLocation getOrMerge(ResourceLocation basePng, ResourceLocation overlayPng) {
        String cacheKey = basePng.toString() + "+" + overlayPng.toString();
        return MERGED_CACHE.computeIfAbsent(cacheKey, key -> {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> baseRes = manager.getResource(basePng);
            Optional<Resource> overlayRes = manager.getResource(overlayPng);

            if (baseRes.isEmpty() || overlayRes.isEmpty()) {
                return basePng;
            }

            try (InputStream baseIn = baseRes.get().open();
                 InputStream overlayIn = overlayRes.get().open();
                 NativeImage baseImg = NativeImage.read(baseIn);
                 NativeImage overlayImg = NativeImage.read(overlayIn)) {

                int bw = baseImg.getWidth();
                int bh = baseImg.getHeight();
                int ow = overlayImg.getWidth();
                int oh = overlayImg.getHeight();

                NativeImage mergedImg = new NativeImage(bw, bh, true);

                for (int y = 0; y < bh; y++) {
                    int oy = (y * oh) / bh;
                    for (int x = 0; x < bw; x++) {
                        int ox = (x * ow) / bw;
                        int dst = baseImg.getPixelRGBA(x, y);
                        int src = overlayImg.getPixelRGBA(ox, oy);

                        int srcA = (src >> 24) & 0xFF;
                        if (srcA == 0) {
                            mergedImg.setPixelRGBA(x, y, dst);
                        } else if (srcA == 255) {
                            mergedImg.setPixelRGBA(x, y, src);
                        } else {
                            int dstA = (dst >> 24) & 0xFF;
                            int dstB = (dst >> 16) & 0xFF;
                            int dstG = (dst >> 8) & 0xFF;
                            int dstR = dst & 0xFF;

                            int srcB = (src >> 16) & 0xFF;
                            int srcG = (src >> 8) & 0xFF;
                            int srcR = src & 0xFF;

                            float alpha = srcA / 255.0f;
                            float invAlpha = 1.0f - alpha;

                            int outR = Math.min(255, Math.round(srcR * alpha + dstR * invAlpha));
                            int outG = Math.min(255, Math.round(srcG * alpha + dstG * invAlpha));
                            int outB = Math.min(255, Math.round(srcB * alpha + dstB * invAlpha));
                            int outA = Math.max(dstA, srcA);

                            mergedImg.setPixelRGBA(x, y, (outA << 24) | (outB << 16) | (outG << 8) | outR);
                        }
                    }
                }

                DynamicTexture dynamicTexture = new DynamicTexture(mergedImg);
                ResourceLocation dynamicId = ResourceLocation.fromNamespaceAndPath(
                        UpMod.MOD_ID,
                        "dynamic_loot_" + COUNTER.incrementAndGet()
                );
                Minecraft.getInstance().getTextureManager().register(dynamicId, dynamicTexture);
                return dynamicId;
            } catch (Exception e) {
                LOGGER.error("[UP Mod] Failed to merge texture: {}", basePng, e);
                return basePng;
            }
        });
    }
}