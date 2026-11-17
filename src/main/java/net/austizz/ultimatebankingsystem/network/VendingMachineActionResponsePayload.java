package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VendingMachineActionResponsePayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<VendingMachineActionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "vending_machine_action_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingMachineActionResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.success());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                    },
                    buf -> new VendingMachineActionResponsePayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<VendingMachineActionResponsePayload> type() {
        return TYPE;
    }
}
