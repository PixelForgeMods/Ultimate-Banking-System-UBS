package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AtmViewResultPayload(BlockPos atm, boolean accepted) implements CustomPacketPayload {
    public static final Type<AtmViewResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_view_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmViewResultPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeBlockPos(v.atm); b.writeBoolean(v.accepted); }, b -> new AtmViewResultPayload(b.readBlockPos(), b.readBoolean()));
    @Override public Type<AtmViewResultPayload> type() { return TYPE; }
}
