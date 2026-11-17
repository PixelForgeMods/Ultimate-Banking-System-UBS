package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Server-authoritative daily job quest card. */
public record JobDailyQuestPayload(
        String id,
        String title,
        String description,
        long progress,
        long target,
        boolean completed,
        boolean monetary
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, JobDailyQuestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.id()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.title()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.description()));
                        LONG_CODEC.encode(buf, Math.max(0L, payload.progress()));
                        LONG_CODEC.encode(buf, Math.max(1L, payload.target()));
                        ByteBufCodecs.BOOL.encode(buf, payload.completed());
                        ByteBufCodecs.BOOL.encode(buf, payload.monetary());
                    },
                    buf -> new JobDailyQuestPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
