package net.austizz.ultimatebankingsystem.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobServiceMarketDayTest {
    @Test
    void marketDayUsesMutableWorldDaytimeBoundaries() {
        assertEquals(0L, JobMarketClock.marketDayFromDayTime(0L));
        assertEquals(0L, JobMarketClock.marketDayFromDayTime(23_999L));
        assertEquals(1L, JobMarketClock.marketDayFromDayTime(24_000L));
        assertEquals(4L, JobMarketClock.marketDayFromDayTime(119_999L));
    }
}
