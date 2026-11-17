package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Administrator request to refresh, assign, upload, or look up a server NPC skin. */
public record NpcSkinActionPayload(String action, String targetId, String value, byte[] png)
        implements CustomPacketPayload {
    public static final Type<NpcSkinActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "npc_skin_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSkinActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, trim(payload.action()));
                ByteBufCodecs.STRING_UTF8.encode(buf, trim(payload.targetId()));
                ByteBufCodecs.STRING_UTF8.encode(buf, trim(payload.value()));
                ByteBufCodecs.byteArray(NpcSkinEditorPayload.MAX_SKIN_BYTES)
                        .encode(buf, payload.png() == null ? new byte[0] : payload.png());
            },
            buf -> new NpcSkinActionPayload(ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.byteArray(NpcSkinEditorPayload.MAX_SKIN_BYTES).decode(buf)));

    @Override
    public Type<NpcSkinActionPayload> type() {
        return TYPE;
    }

    private static String trim(String value) {
        if (value == null) return "";
        return value.length() <= NpcSkinEditorPayload.MAX_TEXT
                ? value : value.substring(0, NpcSkinEditorPayload.MAX_TEXT);
    }
}
