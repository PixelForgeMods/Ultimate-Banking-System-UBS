package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShelfFillOpenPayload(boolean fresh, boolean selecting, boolean complete,
                                   int shelves, int freeSlots, String notice) implements CustomPacketPayload {
    public static final Type<ShelfFillOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shelf_fill_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfFillOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.fresh());
                buf.writeBoolean(value.selecting());
                buf.writeBoolean(value.complete());
                buf.writeVarInt(value.shelves());
                buf.writeVarInt(value.freeSlots());
                buf.writeUtf(value.notice(), 512);
            },
            buf -> new ShelfFillOpenPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(512)));

    @Override public Type<ShelfFillOpenPayload> type() { return TYPE; }
}
