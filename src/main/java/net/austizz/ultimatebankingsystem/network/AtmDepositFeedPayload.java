package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;

public record AtmDepositFeedPayload(UUID token, UUID requestId, int sequence, List<Integer> bills) implements CustomPacketPayload {
    public AtmDepositFeedPayload { bills = List.copyOf(bills); if (bills.size() != 7) throw new IllegalArgumentException("Seven bill denominations required"); }
    public static final Type<AtmDepositFeedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_deposit_feed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmDepositFeedPayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> { b.writeUUID(v.token); b.writeUUID(v.requestId); b.writeInt(v.sequence); for (int n : v.bills) b.writeVarInt(n); },
            b -> new AtmDepositFeedPayload(b.readUUID(), b.readUUID(), b.readInt(), java.util.stream.IntStream.range(0,7).map(i -> b.readVarInt()).boxed().toList()));
    @Override public Type<AtmDepositFeedPayload> type() { return TYPE; }
}
