package net.austizz.ultimatebankingsystem.job;

/** Real-world clock used by job daily quests. Minecraft time is intentionally not used here. */
public final class DailyQuestClock {
    public static final long MILLIS_PER_DAY = 86_400_000L;

    private DailyQuestClock() {
    }

    public static long rotationDay(long epochMillis) {
        return Math.floorDiv(epochMillis, MILLIS_PER_DAY);
    }

    public static long nextResetEpochMillis(long rotationDay) {
        if (rotationDay >= Long.MAX_VALUE / MILLIS_PER_DAY) {
            return Long.MAX_VALUE;
        }
        return (rotationDay + 1L) * MILLIS_PER_DAY;
    }

    public static long secondsUntilReset(long epochMillis) {
        long reset = nextResetEpochMillis(rotationDay(epochMillis));
        if (reset == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (reset - epochMillis + 999L) / 1000L);
    }
}
