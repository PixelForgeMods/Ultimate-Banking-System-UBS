package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record AtmCardRequestPayload(BlockPos atm, UUID requestId) implements CustomPacketPayload {
    public static final Type<AtmCardRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_card_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmCardRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBlockPos(value.atm); buf.writeUUID(value.requestId); },
            buf -> new AtmCardRequestPayload(buf.readBlockPos(), buf.readUUID()));
    @Override public Type<AtmCardRequestPayload> type() { return TYPE; }
}
