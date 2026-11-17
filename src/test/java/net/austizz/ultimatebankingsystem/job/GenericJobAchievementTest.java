package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericJobAchievementTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void minerAchievementsUseMinerActivityAndAreNotLumberjackAchievements() throws Exception {
        Class<?> progressType = Class.forName(
                "net.austizz.ultimatebankingsystem.job.JobSavedData$JobProgress", true, LOADER);
        Object progress = progressType.getConstructor().newInstance();
        progressType.getMethod("recordActivity", String.class, String.class, long.class)
                .invoke(progress, "blocks_mined", "iron", 1L);

        Class<?> service = Class.forName(
                "net.austizz.ultimatebankingsystem.job.JobAchievementService", true, LOADER);
        Method snapshot = service.getMethod("snapshot", String.class, progressType);
        @SuppressWarnings("unchecked")
        List<?> achievements = (List<?>) snapshot.invoke(null, "miner", progress);

        assertTrue(achievements.stream().anyMatch(achievement -> value(achievement, "id").equals("miner.first_shift")
                && (Boolean) value(achievement, "completed")), achievements::toString);
        assertTrue(achievements.stream().noneMatch(achievement -> ((String) value(achievement, "id"))
                .startsWith("lumberjack.")));
    }

    private static Object value(Object record, String accessor) {
        try {
            return record.getClass().getMethod(accessor).invoke(record);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing achievement field: " + accessor, exception);
        }
    }
}
