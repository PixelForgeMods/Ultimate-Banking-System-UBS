package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record AtmActionResultPayload(UUID requestId, boolean success, String balance, String message) implements CustomPacketPayload {
    public static final Type<AtmActionResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmActionResultPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.requestId); b.writeBoolean(v.success); b.writeUtf(v.balance, 80); b.writeUtf(v.message, 2048); },
            b -> new AtmActionResultPayload(b.readUUID(), b.readBoolean(), b.readUtf(80), b.readUtf(2048)));
    @Override public Type<AtmActionResultPayload> type() { return TYPE; }
}
