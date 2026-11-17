package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record AtmPinRequestPayload(BlockPos atm, UUID requestId, UUID cardId, String pin, boolean create, String repeatedPin) implements CustomPacketPayload {
    public AtmPinRequestPayload(BlockPos atm, UUID requestId, UUID cardId, String pin) {
        this(atm, requestId, cardId, pin, false, "");
    }
    public static final Type<AtmPinRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_pin_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmPinRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBlockPos(value.atm); buf.writeUUID(value.requestId); buf.writeUUID(value.cardId); buf.writeUtf(value.pin, 4); buf.writeBoolean(value.create); buf.writeUtf(value.repeatedPin, 4); },
            buf -> new AtmPinRequestPayload(buf.readBlockPos(), buf.readUUID(), buf.readUUID(), buf.readUtf(4), buf.readBoolean(), buf.readUtf(4)));
    @Override public Type<AtmPinRequestPayload> type() { return TYPE; }
    @Override public String toString() { return "AtmPinRequestPayload[redacted]"; }
}
