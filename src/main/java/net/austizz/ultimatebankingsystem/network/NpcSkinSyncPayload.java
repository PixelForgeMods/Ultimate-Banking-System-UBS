package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Renderer update for a single server NPC role. An empty image restores the role default. */
public record NpcSkinSyncPayload(String targetId, String skinId, byte[] png, boolean slimModel)
        implements CustomPacketPayload {
    public static final Type<NpcSkinSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "npc_skin_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSkinSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.targetId() == null ? "" : payload.targetId());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.skinId() == null ? "" : payload.skinId());
                ByteBufCodecs.byteArray(NpcSkinEditorPayload.MAX_SKIN_BYTES)
                        .encode(buf, payload.png() == null ? new byte[0] : payload.png());
                ByteBufCodecs.BOOL.encode(buf, payload.slimModel());
            },
            buf -> new NpcSkinSyncPayload(ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.byteArray(NpcSkinEditorPayload.MAX_SKIN_BYTES).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<NpcSkinSyncPayload> type() {
        return TYPE;
    }
}
