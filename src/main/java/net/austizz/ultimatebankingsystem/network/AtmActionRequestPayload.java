package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record AtmActionRequestPayload(UUID token, UUID requestId, int sequence, String action, String amount, int billOption, UUID target, int durationMinutes, String pin) implements CustomPacketPayload {
    public AtmActionRequestPayload(UUID token, UUID requestId, int sequence, String action, String amount, int billOption, UUID target) {
        this(token, requestId, sequence, action, amount, billOption, target, 20, "");
    }
    public AtmActionRequestPayload(UUID token, UUID requestId, int sequence, String action, String amount, int billOption) {
        this(token, requestId, sequence, action, amount, billOption, new UUID(0,0));
    }
    public AtmActionRequestPayload(UUID token, UUID requestId, int sequence, String action, String amount) {
        this(token, requestId, sequence, action, amount, -1);
    }
    public static final Type<AtmActionRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_action_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmActionRequestPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.token); b.writeUUID(v.requestId); b.writeInt(v.sequence); b.writeUtf(v.action, 16); b.writeUtf(v.amount, 10); b.writeVarInt(v.billOption); b.writeUUID(v.target); b.writeVarInt(v.durationMinutes); b.writeUtf(v.pin, 4); },
            b -> new AtmActionRequestPayload(b.readUUID(), b.readUUID(), b.readInt(), b.readUtf(16), b.readUtf(10), b.readVarInt(), b.readUUID(), b.readVarInt(), b.readUtf(4)));
    @Override public String toString() { return "AtmActionRequestPayload[redacted]"; }
    @Override public Type<AtmActionRequestPayload> type() { return TYPE; }
}
