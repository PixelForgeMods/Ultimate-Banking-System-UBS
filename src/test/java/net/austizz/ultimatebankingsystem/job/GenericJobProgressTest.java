package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericJobProgressTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void activityAndFamiliesAreTrackedWithoutChangingPayoutTotals() throws Exception {
        Class<?> progressType = Class.forName(
                "net.austizz.ultimatebankingsystem.job.JobSavedData$JobProgress", true, LOADER);
        Object progress = progressType.getConstructor().newInstance();

        progressType.getMethod("recordActivity", String.class, String.class, long.class)
                .invoke(progress, "blocks_mined", "iron", 12L);
        progressType.getMethod("recordActivity", String.class, String.class, long.class)
                .invoke(progress, "blocks_mined", "diamond", 3L);

        assertEquals(15L, progressType.getMethod("activityMetric", String.class)
                .invoke(progress, "blocks_mined"));
        assertEquals(2L, progressType.getMethod("distinctFamilies").invoke(progress));
        assertEquals(0L, progressType.getMethod("lifetimeMoneyCents").invoke(progress));
        assertEquals(0L, progressType.getMethod("lifetimeSaleCount").invoke(progress));
    }
}
