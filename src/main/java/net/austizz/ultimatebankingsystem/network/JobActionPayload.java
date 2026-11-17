package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request for a job application, refresh, preview, or confirmed sale. */
public record JobActionPayload(
        String action,
        String itemId,
        int amount,
        long expectedQuoteCents,
        boolean confirmed
) implements CustomPacketPayload {
    public static final Type<JobActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "job_action"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, JobActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.action() == null ? "" : payload.action());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.itemId() == null ? "" : payload.itemId());
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, payload.amount()));
                        LONG_CODEC.encode(buf, Math.max(0L, payload.expectedQuoteCents()));
                        ByteBufCodecs.BOOL.encode(buf, payload.confirmed());
                    },
                    buf -> new JobActionPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    @Override
    public Type<JobActionPayload> type() {
        return TYPE;
    }
}
