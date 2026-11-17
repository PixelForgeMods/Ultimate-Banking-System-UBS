package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LumberjackCatalogServiceTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void onlyRealWoodFamiliesCanContributeProcessedProducts() throws Exception {
        ensureMinecraftBootstrap();

        assertEquals("STAIRS", classify("OAK_STAIRS", "oak_stairs"));
        assertEquals("SLAB", classify("CRIMSON_SLAB", "crimson_slab"));
        assertNull(classify("BLACKSTONE_STAIRS", "blackstone_stairs"));
        assertNull(classify("COBBLESTONE_SLAB", "cobblestone_slab"));
        assertNull(classify("BARREL", "oak_stairs"));
        assertNull(classify("BARRIER", "oak_planks"));
    }

    private static String classify(String itemField, String itemPath) throws Exception {
        Object item = load("net.minecraft.world.item.Items").getField(itemField).get(null);
        Class<?> itemStackType = load("net.minecraft.world.item.ItemStack");
        Object stack = itemStackType.getConstructor(load("net.minecraft.world.level.ItemLike")).newInstance(item);
        Class<?> catalogService = load("net.austizz.ultimatebankingsystem.job.LumberjackCatalogService");
        Method classify = catalogService.getDeclaredMethod("classify", itemStackType, String.class);
        classify.setAccessible(true);
        Object category = classify.invoke(null, stack, itemPath);
        return category == null ? null : ((Enum<?>) category).name();
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

    private static Class<?> load(String name) throws Exception {
        return Class.forName(name, true, LOADER);
    }
}
