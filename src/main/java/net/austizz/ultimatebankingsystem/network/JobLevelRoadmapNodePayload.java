package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Server-authoritative single-level node used by the shop-style job roadmap. */
public record JobLevelRoadmapNodePayload(
        int level,
        long requiredXp,
        int payoutBonusPercent,
        int saleXpBonusPercent,
        long dailyPayoutCapCents,
        long dailyUnitsCapMilli,
        String state,
        List<String> unlocks
) {
    private static final int MAX_UNLOCKS = 8;

    public static final StreamCodec<RegistryFriendlyByteBuf, JobLevelRoadmapNodePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, clampLevel(payload.level()));
                        buf.writeLong(Math.max(0L, payload.requiredXp()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, payload.payoutBonusPercent()));
                        ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, payload.saleXpBonusPercent()));
                        buf.writeLong(Math.max(0L, payload.dailyPayoutCapCents()));
                        buf.writeLong(Math.max(0L, payload.dailyUnitsCapMilli()));
                        ByteBufCodecs.STRING_UTF8.encode(buf, safe(payload.state()));
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_UNLOCKS)).encode(buf, safeUnlocks(payload.unlocks()));
                    },
                    buf -> new JobLevelRoadmapNodePayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            buf.readLong(),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            buf.readLong(),
                            buf.readLong(),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_UNLOCKS)).decode(buf)
                    )
            );

    public JobLevelRoadmapNodePayload {
        level = clampLevel(level);
        requiredXp = Math.max(0L, requiredXp);
        payoutBonusPercent = Math.max(0, payoutBonusPercent);
        saleXpBonusPercent = Math.max(0, saleXpBonusPercent);
        dailyPayoutCapCents = Math.max(0L, dailyPayoutCapCents);
        dailyUnitsCapMilli = Math.max(0L, dailyUnitsCapMilli);
        state = state == null || state.isBlank() ? "LOCKED" : state.trim().toUpperCase(java.util.Locale.ROOT);
        unlocks = safeUnlocks(unlocks);
    }

    private static int clampLevel(int value) {
        return Math.max(1, Math.min(100, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static List<String> safeUnlocks(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .limit(MAX_UNLOCKS)
                .toList();
    }
}
