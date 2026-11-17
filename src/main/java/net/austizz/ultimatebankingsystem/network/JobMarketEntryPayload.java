package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Compact market-card data for one registered job and the viewer's own progress in it. */
public record JobMarketEntryPayload(
        String jobId,
        String displayName,
        String description,
        String iconItemId,
        int level,
        long xp,
        long xpToNext,
        boolean active
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, JobMarketEntryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.jobId()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.displayName()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.description()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.iconItemId()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(1, payload.level()));
                        LONG_CODEC.encode(buf, Math.max(0L, payload.xp()));
                        LONG_CODEC.encode(buf, Math.max(0L, payload.xpToNext()));
                        ByteBufCodecs.BOOL.encode(buf, payload.active());
                    },
                    buf -> new JobMarketEntryPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
