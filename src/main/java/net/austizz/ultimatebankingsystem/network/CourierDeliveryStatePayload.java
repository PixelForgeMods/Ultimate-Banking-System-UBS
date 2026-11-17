package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Independent client HUD state for the accepted Courier routes a player is carrying. */
public record CourierDeliveryStatePayload(
        boolean active,
        List<CourierOrderPayload> deliveries
) implements CustomPacketPayload {
    public static final int MAX_DELIVERIES = 3;
    public static final Type<CourierDeliveryStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "courier_delivery_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CourierDeliveryStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    CourierDeliveryStatePayload::active,
                    CourierOrderPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DELIVERIES)),
                    CourierDeliveryStatePayload::deliveries,
                    CourierDeliveryStatePayload::new
            );

    public CourierDeliveryStatePayload {
        deliveries = deliveries == null ? List.of() : deliveries.stream()
                .filter(delivery -> delivery != null && "ACCEPTED".equals(delivery.status()))
                .limit(MAX_DELIVERIES)
                .toList();
        active = active && !deliveries.isEmpty();
    }

    public static CourierDeliveryStatePayload inactive() {
        return new CourierDeliveryStatePayload(false, List.of());
    }

    @Override
    public Type<CourierDeliveryStatePayload> type() {
        return TYPE;
    }
}
