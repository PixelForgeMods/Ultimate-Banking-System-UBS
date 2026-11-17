package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobProgressIsolationTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void jobProgressIsStoredIndependentlyPerJobId() throws Exception {
        Class<?> playerRecordType = load("net.austizz.ultimatebankingsystem.job.JobSavedData$PlayerRecord");
        Object record = playerRecordType.getConstructor().newInstance();
        Method progressFor = playerRecordType.getMethod("progress", String.class);

        Object lumberjack = progressFor.invoke(record, "lumberjack");
        lumberjack.getClass().getMethod("addSale", long.class, long.class, long.class, int.class)
                .invoke(lumberjack, 1_500L, 2_000L, 320L, 4);
        Object miner = progressFor.invoke(record, "miner");

        assertEquals(4, lumberjack.getClass().getMethod("level").invoke(lumberjack));
        assertEquals(320L, lumberjack.getClass().getMethod("xp").invoke(lumberjack));
        assertEquals(1, miner.getClass().getMethod("level").invoke(miner));
        assertEquals(0L, miner.getClass().getMethod("xp").invoke(miner));
        assertEquals(lumberjack, progressFor.invoke(record, "lumberjack"));
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
