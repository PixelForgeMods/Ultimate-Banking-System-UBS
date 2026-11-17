package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record AtmDepositStockPayload(BlockPos atm, List<Integer> bills) implements CustomPacketPayload {
    public AtmDepositStockPayload { bills = List.copyOf(bills); if (bills.size() != 7) throw new IllegalArgumentException("Seven bill denominations required"); }
    public static final Type<AtmDepositStockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_deposit_stock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmDepositStockPayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> { b.writeBlockPos(v.atm); for (int n : v.bills) b.writeVarInt(n); },
            b -> new AtmDepositStockPayload(b.readBlockPos(), java.util.stream.IntStream.range(0,7).map(i -> b.readVarInt()).boxed().toList()));
    @Override public Type<AtmDepositStockPayload> type() { return TYPE; }
}
