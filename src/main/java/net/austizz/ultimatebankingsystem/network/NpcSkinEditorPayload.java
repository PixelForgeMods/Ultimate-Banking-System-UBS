package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Bounded administrator snapshot of the server NPC skin library and role assignments. */
public record NpcSkinEditorPayload(List<SkinAsset> skins, List<TargetSelection> targets, String notice)
        implements CustomPacketPayload {
    public static final int MAX_SKINS = 24;
    public static final int MAX_SKIN_BYTES = 32 * 1024;
    public static final int MAX_TEXT = 192;
    public static final Type<NpcSkinEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "npc_skin_editor"));

    public NpcSkinEditorPayload {
        skins = skins == null ? List.of() : skins.stream().limit(MAX_SKINS).toList();
        targets = targets == null ? List.of() : targets.stream().limit(16).toList();
        notice = limited(notice);
    }

    public record SkinAsset(String id, String displayName, byte[] png) {
        public SkinAsset {
            id = limited(id);
            displayName = limited(displayName);
            png = png == null ? new byte[0] : png.clone();
        }
    }

    public record TargetSelection(String id, String displayName, String selectedSkinId, String defaultTextureId,
                                  boolean slimModel) {
        public TargetSelection {
            id = limited(id);
            displayName = limited(displayName);
            selectedSkinId = limited(selectedSkinId);
            defaultTextureId = limited(defaultTextureId);
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, SkinAsset> SKIN_CODEC = StreamCodec.of(
            (buf, asset) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(asset.id()));
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(asset.displayName()));
                ByteBufCodecs.byteArray(MAX_SKIN_BYTES).encode(buf, asset.png());
            },
            buf -> new SkinAsset(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.byteArray(MAX_SKIN_BYTES).decode(buf)));

    private static final StreamCodec<RegistryFriendlyByteBuf, TargetSelection> TARGET_CODEC = StreamCodec.of(
            (buf, target) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(target.id()));
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(target.displayName()));
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(target.selectedSkinId()));
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(target.defaultTextureId()));
                ByteBufCodecs.BOOL.encode(buf, target.slimModel());
            },
            buf -> new TargetSelection(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSkinEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                SKIN_CODEC.apply(ByteBufCodecs.list(MAX_SKINS)).encode(buf, payload.skins());
                TARGET_CODEC.apply(ByteBufCodecs.list(16)).encode(buf, payload.targets());
                ByteBufCodecs.STRING_UTF8.encode(buf, limited(payload.notice()));
            },
            buf -> new NpcSkinEditorPayload(
                    SKIN_CODEC.apply(ByteBufCodecs.list(MAX_SKINS)).decode(buf),
                    TARGET_CODEC.apply(ByteBufCodecs.list(16)).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public Type<NpcSkinEditorPayload> type() {
        return TYPE;
    }

    private static String limited(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
