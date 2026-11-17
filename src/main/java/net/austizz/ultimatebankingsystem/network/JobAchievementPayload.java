package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Server-authoritative job achievement card and reward state. */
public record JobAchievementPayload(
        String id,
        String title,
        String description,
        String category,
        String unit,
        long progress,
        long target,
        boolean completed,
        boolean claimed,
        String rewardSummary,
        String iconItemId
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public JobAchievementPayload {
        id = safe(id);
        title = safe(title);
        description = safe(description);
        category = safe(category);
        unit = safe(unit);
        progress = Math.max(0L, progress);
        target = Math.max(1L, target);
        rewardSummary = safe(rewardSummary);
        iconItemId = safe(iconItemId);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, JobAchievementPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.id());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.title());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.description());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.category());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.unit());
                        LONG_CODEC.encode(buf, payload.progress());
                        LONG_CODEC.encode(buf, payload.target());
                        ByteBufCodecs.BOOL.encode(buf, payload.completed());
                        ByteBufCodecs.BOOL.encode(buf, payload.claimed());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.rewardSummary());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.iconItemId());
                    },
                    buf -> new JobAchievementPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
