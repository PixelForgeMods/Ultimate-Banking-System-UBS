package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobOpenPayloadTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void catalogRoundTripsWhenModdedWoodExceedsLegacyListLimit() throws Exception {
        Class<?> entryType = load("net.austizz.ultimatebankingsystem.network.JobCatalogEntryPayload");
        Constructor<?> entryConstructor = entryType.getConstructor(
                String.class, String.class, String.class, String.class,
                long.class, int.class, int.class);
        List<Object> entries = new ArrayList<>();
        for (int index = 0; index < 451; index++) {
            entries.add(entryConstructor.newInstance(
                    "example:wood_product_" + index,
                    "Wood Product " + index,
                    "example_wood",
                    "planks",
                    500L,
                    0,
                    1000));
        }

        Class<?> payloadType = load("net.austizz.ultimatebankingsystem.network.JobOpenPayload");
        Class<?> marketEntryType = load("net.austizz.ultimatebankingsystem.network.JobMarketEntryPayload");
        Object marketEntry = marketEntryType.getConstructor(
                        String.class, String.class, String.class, String.class,
                        int.class, long.class, long.class, boolean.class)
                .newInstance("lumberjack", "Lumberjack", "Sell wood.", "minecraft:iron_axe",
                        4, 250L, 750L, true);
        Constructor<?> payloadConstructor = payloadType.getConstructor(
                String.class, String.class, String.class, String.class, String.class,
                int.class, long.class, long.class, long.class, long.class, long.class, long.class,
                String.class, String.class, int.class, int.class,
                boolean.class, boolean.class, boolean.class, String.class, int.class, long.class, List.class,
                List.class);
        Object payload = payloadConstructor.newInstance(
                "BUYER", "lumberjack", "Lumberjack", "Wood", "", 1,
                0L, 1000L, 0L, 1_000_000L, 0L, 2_000_000L,
                "", "", 0, 0, true, false, false, "", 0, 0L, entries, List.of(marketEntry));

        Object decoded = roundTrip(payloadType, payload);
        assertEquals(payload, decoded);
    }

    @Test
    void shopStyleLevelRoadmapRoundTripsAllOneHundredNodes() throws Exception {
        Class<?> nodeType = load("net.austizz.ultimatebankingsystem.network.JobLevelRoadmapNodePayload");
        Constructor<?> nodeConstructor = nodeType.getConstructor(
                int.class, long.class, int.class, int.class, long.class, long.class,
                String.class, List.class);
        List<Object> roadmap = new ArrayList<>();
        for (int level = 1; level <= 100; level++) {
            roadmap.add(nodeConstructor.newInstance(
                    level, level == 1 ? 0L : level * 10_000L,
                    Math.min(20, level / 5), Math.min(10, level / 10),
                    1_000_000L + level * 25_000L, 2_000_000L + level * 100_000L,
                    level == 1 ? "CURRENT" : "LOCKED", level % 5 == 0
                            ? List.of("Milestone reward") : List.of()));
        }

        Class<?> achievementType = load("net.austizz.ultimatebankingsystem.network.JobAchievementPayload");
        Object achievement = achievementType.getConstructor(
                        String.class, String.class, String.class, String.class, String.class,
                        long.class, long.class, boolean.class, boolean.class, String.class, String.class)
                .newInstance("lumberjack.first_cut", "First Cut", "Break one log.", "HARVEST", "logs",
                        1L, 1L, true, false, "$25.00 digital money", "minecraft:oak_log");

        Class<?> payloadType = load("net.austizz.ultimatebankingsystem.network.JobOpenPayload");
        Constructor<?> payloadConstructor = payloadType.getConstructor(
                String.class, String.class, String.class, String.class, String.class,
                int.class, long.class, long.class, long.class, long.class, long.class, long.class,
                String.class, String.class, int.class, int.class,
                boolean.class, boolean.class, boolean.class, String.class, int.class, long.class,
                List.class, List.class, List.class, List.class, List.class, List.class, long.class);
        Object payload = payloadConstructor.newInstance(
                "BUYER", "lumberjack", "Lumberjack", "Wood", "", 1,
                0L, 1000L, 0L, 1_000_000L, 0L, 2_000_000L,
                "", "", 0, 0, true, false, false, "", 0, 0L,
                List.of(), List.of(), roadmap, List.of(achievement), List.of(), List.of(), 0L);

        Object decoded = roundTrip(payloadType, payload);
        assertEquals(payload, decoded);
    }

    private static Object roundTrip(Class<?> payloadType, Object payload) throws Exception {
        Object codec = payloadType.getField("STREAM_CODEC").get(null);
        Object buffer = buffer();
        try {
            load("net.minecraft.network.codec.StreamCodec")
                    .getMethod("encode", Object.class, Object.class)
                    .invoke(codec, buffer, payload);
            return load("net.minecraft.network.codec.StreamCodec")
                    .getMethod("decode", Object.class)
                    .invoke(codec, buffer);
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }

    private static Object buffer() throws Exception {
        Class<?> byteBuf = load("io.netty.buffer.ByteBuf");
        Object source = load("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        Object registries = load("net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        return load("net.minecraft.network.RegistryFriendlyByteBuf")
                .getConstructor(byteBuf, load("net.minecraft.core.RegistryAccess"))
                .newInstance(source, registries);
    }

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
