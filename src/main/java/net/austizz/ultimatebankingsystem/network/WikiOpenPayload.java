package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WikiOpenPayload() implements CustomPacketPayload {
    public static final Type<WikiOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "wiki_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WikiOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {}, buffer -> new WikiOpenPayload());

    @Override
    public Type<WikiOpenPayload> type() {
        return TYPE;
    }
}
