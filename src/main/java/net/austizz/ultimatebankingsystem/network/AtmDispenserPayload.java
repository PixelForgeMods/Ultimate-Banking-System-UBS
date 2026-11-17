package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AtmDispenserPayload(BlockPos atm, String action) implements CustomPacketPayload {
    public static final Type<AtmDispenserPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_dispenser"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmDispenserPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeBlockPos(v.atm); b.writeUtf(v.action, 16); }, b -> new AtmDispenserPayload(b.readBlockPos(), b.readUtf(16)));
    @Override public Type<AtmDispenserPayload> type() { return TYPE; }
}
