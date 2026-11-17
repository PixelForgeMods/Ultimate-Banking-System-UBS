package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GenericJobCatalogTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void resourceClassifiersKeepEquipmentOutOfMinerStockAndPreserveJobCategories() throws Exception {
        ensureMinecraftBootstrap();
        Class<?> itemStackType = load("net.minecraft.world.item.ItemStack");
        Class<?> itemLikeType = load("net.minecraft.world.level.ItemLike");
        Class<?> catalog = load("net.austizz.ultimatebankingsystem.job.JobCatalogService");

        Object ironIngot = itemStack("IRON_INGOT", itemStackType, itemLikeType);
        Object diamondSword = itemStack("DIAMOND_SWORD", itemStackType, itemLikeType);
        Object cod = itemStack("COD", itemStackType, itemLikeType);
        Object milkBucket = itemStack("MILK_BUCKET", itemStackType, itemLikeType);
        Object barrel = itemStack("BARREL", itemStackType, itemLikeType);
        Object barrier = itemStack("BARRIER", itemStackType, itemLikeType);

        assertEquals("refined bars", invoke(catalog, "minerCategory", itemStackType,
                ironIngot, "iron_ingot"));
        assertNull(invoke(catalog, "minerCategory", itemStackType, diamondSword, "diamond_sword"));
        assertNull(invoke(catalog, "minerCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "minerCategory", itemStackType, barrier, "barrier"));
        assertNull(invoke(catalog, "fishermanCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "farmerCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "hunterCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "blacksmithCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "chefCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "herbalistCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "salvagerCategory", itemStackType, barrel, "barrel"));
        assertNull(invoke(catalog, "tailorCategory", itemStackType, barrel, "barrel"));
        assertEquals("fresh catch", invoke(catalog, "fishermanCategory", itemStackType,
                cod, "smoked_cod"));
        assertEquals("livestock", invoke(catalog, "farmerCategory", itemStackType,
                milkBucket, "milk_bucket"));
    }

    private static Object invoke(Class<?> owner, String name, Class<?> itemStackType,
                                 Object stack, String path) throws Exception {
        Method method = owner.getDeclaredMethod(name, itemStackType, String.class);
        method.setAccessible(true);
        return method.invoke(null, stack, path);
    }

    private static Object itemStack(String field, Class<?> itemStackType, Class<?> itemLikeType) throws Exception {
        Object item = load("net.minecraft.world.item.Items").getField(field).get(null);
        return itemStackType.getConstructor(itemLikeType).newInstance(item);
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

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
