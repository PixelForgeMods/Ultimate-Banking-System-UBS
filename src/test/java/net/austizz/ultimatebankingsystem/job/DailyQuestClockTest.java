package net.austizz.ultimatebankingsystem.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyQuestClockTest {
    @Test
    void rotationUsesARealWorldTwentyFourHourBoundary() {
        long day = DailyQuestClock.MILLIS_PER_DAY * 12L;
        assertEquals(12L, DailyQuestClock.rotationDay(day));
        assertEquals(day + DailyQuestClock.MILLIS_PER_DAY,
                DailyQuestClock.nextResetEpochMillis(12L));
        assertEquals(60L, DailyQuestClock.secondsUntilReset(day + DailyQuestClock.MILLIS_PER_DAY - 60_000L));
    }
}
