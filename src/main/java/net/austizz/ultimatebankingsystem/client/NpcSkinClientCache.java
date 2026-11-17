package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.NpcSkinEditorPayload;
import net.austizz.ultimatebankingsystem.network.NpcSkinSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Local dynamic texture cache for server-synchronized NPC skin PNG files. */
public final class NpcSkinClientCache {
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();
    private static final Map<String, ResourceLocation> ASSIGNMENTS = new HashMap<>();
    private static final Map<String, Boolean> SLIM_MODELS = new HashMap<>();

    private NpcSkinClientCache() {
    }

    public static void applyEditor(NpcSkinEditorPayload payload) {
        if (payload == null) return;
        for (NpcSkinEditorPayload.SkinAsset skin : payload.skins()) {
            register(skin.id(), skin.png());
        }
        for (NpcSkinEditorPayload.TargetSelection target : payload.targets()) {
            SLIM_MODELS.put(target.id(), target.slimModel());
            ResourceLocation texture = target.selectedSkinId().isBlank() ? null : SKINS.get(target.selectedSkinId());
            if (texture == null) ASSIGNMENTS.remove(target.id());
            else ASSIGNMENTS.put(target.id(), texture);
        }
    }

    public static void applySync(NpcSkinSyncPayload payload) {
        if (payload == null || payload.targetId().isBlank()) return;
        SLIM_MODELS.put(payload.targetId(), payload.slimModel());
        ResourceLocation texture = register(payload.skinId(), payload.png());
        if (texture == null) ASSIGNMENTS.remove(payload.targetId());
        else ASSIGNMENTS.put(payload.targetId(), texture);
    }

    public static ResourceLocation textureFor(String targetId, ResourceLocation fallback) {
        return ASSIGNMENTS.getOrDefault(targetId, fallback);
    }

    public static boolean usesSlimModel(String targetId, boolean fallback) {
        return SLIM_MODELS.getOrDefault(targetId, fallback);
    }

    public static ResourceLocation preview(String skinId, ResourceLocation fallback) {
        return SKINS.getOrDefault(skinId, fallback);
    }

    private static ResourceLocation register(String skinId, byte[] png) {
        if (skinId == null || skinId.isBlank() || png == null || png.length == 0) return null;
        ResourceLocation existing = SKINS.get(skinId);
        if (existing != null) return existing;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(stream);
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                image.close();
                return null;
            }
            String key = "ubs_npc_skin_" + Integer.toUnsignedString(skinId.hashCode(), 36);
            ResourceLocation texture = Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(image));
            SKINS.put(skinId, texture);
            return texture;
        } catch (IOException exception) {
            UltimateBankingSystem.LOGGER.warn("[UBS] Could not register NPC skin {}: {}", skinId,
                    exception.getMessage());
            return null;
        }
    }
}
