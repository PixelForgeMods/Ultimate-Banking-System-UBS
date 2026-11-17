package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record AtmPinResultPayload(BlockPos atm, UUID requestId, boolean accepted, String message, UUID sessionToken) implements CustomPacketPayload {
    public AtmPinResultPayload(BlockPos atm, UUID requestId, boolean accepted, String message) {
        this(atm, requestId, accepted, message, new UUID(0, 0));
    }
    public static final Type<AtmPinResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_pin_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmPinResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBlockPos(value.atm); buf.writeUUID(value.requestId); buf.writeBoolean(value.accepted); buf.writeUtf(value.message, 256); buf.writeUUID(value.sessionToken); },
            buf -> new AtmPinResultPayload(buf.readBlockPos(), buf.readUUID(), buf.readBoolean(), buf.readUtf(256), buf.readUUID()));
    @Override public Type<AtmPinResultPayload> type() { return TYPE; }
}
