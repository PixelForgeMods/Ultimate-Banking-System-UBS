package net.austizz.ultimatebankingsystem.job;

/** Pure Minecraft-day conversion used by the job market rotation and its tests. */
public final class JobMarketClock {
    private JobMarketClock() {
    }

    public static long marketDayFromDayTime(long dayTime) {
        return Math.floorDiv(dayTime, 24_000L);
    }
}
