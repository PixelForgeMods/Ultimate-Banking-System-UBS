package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobAchievementProgressTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void achievementMetricsAreRecordedSeparatelyFromSaleAndDailyLimits() throws Exception {
        Class<?> progressType = load("net.austizz.ultimatebankingsystem.job.JobSavedData$JobProgress");
        Object progress = progressType.getConstructor().newInstance();

        progressType.getMethod("addSale", long.class, long.class, long.class, int.class)
                .invoke(progress, 12_500L, 2_000L, 100L, 2);
        progressType.getMethod("recordSale", String.class, String.class, long.class)
                .invoke(progress, "oak", "raw", 2L);
        progressType.getMethod("recordSale", String.class, String.class, long.class)
                .invoke(progress, "oak", "stairs", 3L);
        progressType.getMethod("recordRawLogBreak", String.class).invoke(progress, "oak");
        progressType.getMethod("recordRawLogBreak", String.class).invoke(progress, "oak");
        progressType.getMethod("recordDailyQuestCompletion").invoke(progress);

        assertEquals(1L, progressType.getMethod("lifetimeSaleCount").invoke(progress));
        assertEquals(5L, progressType.getMethod("lifetimeItemCount").invoke(progress));
        assertEquals(3L, progressType.getMethod("lifetimeFinishedProductUnits").invoke(progress));
        assertEquals(2L, progressType.getMethod("lifetimeRawLogsBroken").invoke(progress));
        assertEquals(2L, progressType.getMethod("logsBrokenForFamily", String.class).invoke(progress, "oak"));
        assertEquals(1L, progressType.getMethod("completedDailyQuestCount").invoke(progress));
        assertEquals(12_500L, progressType.getMethod("largestSaleCents").invoke(progress));
    }

    @Test
    void achievementClaimMarkersStoreOnlyTheRolledRewardAndClaimState() throws Exception {
        Class<?> progressType = load("net.austizz.ultimatebankingsystem.job.JobSavedData$JobProgress");
        Object progress = progressType.getConstructor().newInstance();
        Object state = progressType.getMethod("achievementState", String.class)
                .invoke(progress, "lumberjack.test");
        Class<?> stateType = load("net.austizz.ultimatebankingsystem.job.JobSavedData$AchievementState");

        stateType.getMethod("setRewardItem", String.class, int.class)
                .invoke(state, "example:token", 2);
        stateType.getMethod("markClaimed").invoke(state);

        assertEquals("example:token", stateType.getMethod("rewardItemId").invoke(state));
        assertEquals(2, stateType.getMethod("rewardItemCount").invoke(state));
        assertEquals(true, stateType.getMethod("claimed").invoke(state));
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
