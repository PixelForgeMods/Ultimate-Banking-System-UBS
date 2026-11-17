package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobQuestServiceTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void everyRegisteredJobGetsThreeStableDailyCards() throws Exception {
        Class<?> registry = load("net.austizz.ultimatebankingsystem.job.JobRegistry");
        Class<?> questService = load("net.austizz.ultimatebankingsystem.job.JobQuestService");
        Class<?> definitionType = load("net.austizz.ultimatebankingsystem.job.JobDefinition");
        @SuppressWarnings("unchecked")
        List<Object> jobs = (List<Object>) registry.getMethod("all").invoke(null);
        for (Object job : jobs) {
            String jobId = (String) definitionType.getMethod("id").invoke(job);
            @SuppressWarnings("unchecked")
            List<Object> first = (List<Object>) questService.getMethod("forDay", String.class, long.class)
                    .invoke(null, jobId, 17L);
            @SuppressWarnings("unchecked")
            List<Object> second = (List<Object>) questService.getMethod("forDay", String.class, long.class)
                    .invoke(null, jobId, 17L);

            assertEquals(3, first.size(), jobId);
            assertEquals(first.stream().map(quest -> value(quest, "id")).toList(),
                    second.stream().map(quest -> value(quest, "id")).toList(), jobId);
            assertEquals(3, new HashSet<>(first.stream().map(quest -> value(quest, "id")).toList()).size(), jobId);
            assertTrue(first.stream().allMatch(quest -> (Long) value(quest, "target") > 0), jobId);
        }
    }

    @Test
    void nonLumberjackQuestProgressOnlyAdvancesItsDeclaredMetric() {
        JobQuestService.Definition activity = JobQuestService.forDay(JobService.MINER_ID, 0L).stream()
                .filter(quest -> quest.metric() == JobQuestService.Metric.ACTIVITY)
                .findFirst().orElseThrow();

        assertEquals(8L, JobQuestService.progressForActivity(activity, "blocks_mined", 8L));
        assertEquals(0L, JobQuestService.progressForActivity(activity, "fish_caught", 8L));
        assertEquals(0L, JobQuestService.progressForSale(activity, "ores", 8, 500L));
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }

    private static Object value(Object record, String accessor) {
        try {
            return record.getClass().getMethod(accessor).invoke(record);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing quest field: " + accessor, exception);
        }
    }
}
