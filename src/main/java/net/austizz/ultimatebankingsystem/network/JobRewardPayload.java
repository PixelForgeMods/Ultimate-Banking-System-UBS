package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Server-authoritative job milestone card. */
public record JobRewardPayload(
        int level,
        String title,
        String description,
        String effect,
        boolean unlocked
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, JobRewardPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(5, Math.min(100, payload.level())));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.title()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.description()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.effect()));
                        ByteBufCodecs.BOOL.encode(buf, payload.unlocked());
                    },
                    buf -> new JobRewardPayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
