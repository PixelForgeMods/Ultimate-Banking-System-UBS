package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public record AtmCardResultPayload(BlockPos atm, UUID requestId, boolean accepted,
                                   InteractionHand hand, ItemStack card, String message, boolean needsPin) implements CustomPacketPayload {
    public AtmCardResultPayload(BlockPos atm, UUID requestId, boolean accepted, InteractionHand hand, ItemStack card, String message) {
        this(atm, requestId, accepted, hand, card, message, false);
    }
    public static final Type<AtmCardResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_card_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtmCardResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBlockPos(value.atm); buf.writeUUID(value.requestId); buf.writeBoolean(value.accepted);
                buf.writeBoolean(value.hand == InteractionHand.MAIN_HAND);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, value.card);
                buf.writeUtf(value.message, 256); buf.writeBoolean(value.needsPin);
            },
            buf -> new AtmCardResultPayload(buf.readBlockPos(), buf.readUUID(), buf.readBoolean(),
                    buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(256), buf.readBoolean()));
    @Override public Type<AtmCardResultPayload> type() { return TYPE; }
}
