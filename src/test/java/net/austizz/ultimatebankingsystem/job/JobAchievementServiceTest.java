package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobAchievementServiceTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void fixedLumberjackAchievementCatalogContainsHarvestTradeContractAndCareerGoals() throws Exception {
        ensureMinecraftBootstrap();
        Object progress = progress();
        List<?> achievements = achievements(progress);

        assertTrue(achievements.size() >= 20);
        assertTrue(achievements.stream().anyMatch(achievement -> value(achievement, "category").equals("HARVEST")));
        assertTrue(achievements.stream().anyMatch(achievement -> value(achievement, "category").equals("TRADE")));
        assertTrue(achievements.stream().anyMatch(achievement -> value(achievement, "category").equals("CONTRACTS")));
        assertTrue(achievements.stream().anyMatch(achievement -> value(achievement, "category").equals("CAREER")));
    }

    @Test
    void achievementProgressBecomesClaimableOnlyAfterItsObjectiveIsMet() throws Exception {
        ensureMinecraftBootstrap();
        Object progress = progress();
        progress.getClass().getMethod("recordRawLogBreak", String.class).invoke(progress, "oak");

        Object firstCut = achievements(progress).stream()
                .filter(achievement -> value(achievement, "id").equals("lumberjack.first_cut"))
                .findFirst()
                .orElseThrow();

        assertTrue((Boolean) value(firstCut, "completed"));
        assertFalse((Boolean) value(firstCut, "claimed"));
        assertTrue(((String) value(firstCut, "rewardSummary")).contains("digital money"));
    }

    private static Object progress() throws Exception {
        return load("net.austizz.ultimatebankingsystem.job.JobSavedData$JobProgress")
                .getConstructor().newInstance();
    }

    private static List<?> achievements(Object progress) throws Exception {
        Class<?> service = load("net.austizz.ultimatebankingsystem.job.JobAchievementService");
        Method snapshot = service.getMethod("snapshot", load("net.minecraft.server.MinecraftServer"),
                load("net.austizz.ultimatebankingsystem.job.JobSavedData$JobProgress"));
        return (List<?>) snapshot.invoke(null, null, progress);
    }

    private static Object value(Object record, String accessor) {
        try {
            return record.getClass().getMethod(accessor).invoke(record);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing achievement field: " + accessor, exception);
        }
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }

    private static void ensureMinecraftBootstrap() throws Exception {
        Class<?> loadingModList = load("net.neoforged.fml.loading.LoadingModList");
        if (loadingModList.getMethod("get").invoke(null) == null) {
            loadingModList.getMethod("of", List.class, List.class, List.class, List.class, Map.class)
                    .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        load("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        load("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
    }
}
