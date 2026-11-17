package net.austizz.ultimatebankingsystem.storage;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UbsItemInventoryTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void serializedStorageIsBidirectionallyCompatibleWithThePreviousNeoForgeHandler() throws Exception {
        ensureMinecraftBootstrap();
        Class<?> holderProvider = load("net.minecraft.core.HolderLookup$Provider");
        Class<?> itemStack = load("net.minecraft.world.item.ItemStack");
        Object registries = load("net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        Object diamond = load("net.minecraft.world.item.Items").getField("DIAMOND").get(null);
        Object original = itemStack.getConstructor(load("net.minecraft.world.level.ItemLike"), int.class)
                .newInstance(diamond, 17);

        Class<?> nativeHandlerType = load("net.neoforged.neoforge.items.ItemStackHandler");
        Object previousHandler = nativeHandlerType.getConstructor(int.class).newInstance(3);
        nativeHandlerType.getMethod("setStackInSlot", int.class, itemStack)
                .invoke(previousHandler, 1, original);
        Object previousTag = nativeHandlerType.getMethod("serializeNBT", holderProvider)
                .invoke(previousHandler, registries);

        Class<?> inventoryType = load("net.austizz.ultimatebankingsystem.storage.UbsItemInventory");
        Object shared = inventoryType.getConstructor(int.class).newInstance(3);
        inventoryType.getMethod("setStackInSlot", int.class, itemStack).invoke(shared, 1, original);
        Object sharedTag = inventoryType.getMethod("serializeNbt", holderProvider).invoke(shared, registries);

        assertEquals(previousTag, sharedTag,
                "Shared storage must keep the exact established ItemStackHandler NBT representation.");

        Object readPrevious = inventoryType.getConstructor(int.class).newInstance(3);
        inventoryType.getMethod("deserializeNbt", load("net.minecraft.nbt.CompoundTag"), holderProvider)
                .invoke(readPrevious, previousTag, registries);
        Object reserializedPrevious = inventoryType.getMethod("serializeNbt", holderProvider)
                .invoke(readPrevious, registries);
        assertEquals(previousTag, reserializedPrevious,
                "Shared storage must load every previously saved handler payload unchanged.");

        Object readShared = nativeHandlerType.getConstructor(int.class).newInstance(3);
        nativeHandlerType.getMethod("deserializeNBT", holderProvider, load("net.minecraft.nbt.CompoundTag"))
                .invoke(readShared, registries, sharedTag);
        Object reserializedShared = nativeHandlerType.getMethod("serializeNBT", holderProvider)
                .invoke(readShared, registries);
        assertEquals(sharedTag, reserializedShared,
                "Existing NeoForge readers must load shared storage payloads unchanged.");
    }

    @Test
    void vanillaMenuContainerViewMutatesTheSameSharedInventory() throws Exception {
        ensureMinecraftBootstrap();
        Class<?> itemStack = load("net.minecraft.world.item.ItemStack");
        Object diamond = load("net.minecraft.world.item.Items").getField("DIAMOND").get(null);
        Object initial = itemStack.getConstructor(load("net.minecraft.world.level.ItemLike"), int.class)
                .newInstance(diamond, 12);

        Class<?> inventoryType = load("net.austizz.ultimatebankingsystem.storage.UbsItemInventory");
        Object shared = inventoryType.getConstructor(int.class).newInstance(2);
        Class<?> containerType = load("net.austizz.ultimatebankingsystem.storage.UbsItemInventoryContainer");
        Object container = containerType.getConstructor(inventoryType).newInstance(shared);
        containerType.getMethod("setItem", int.class, itemStack).invoke(container, 0, initial);

        Object stored = inventoryType.getMethod("getStackInSlot", int.class).invoke(shared, 0);
        assertEquals(12, itemStack.getMethod("getCount").invoke(stored));

        Object extracted = containerType.getMethod("removeItem", int.class, int.class).invoke(container, 0, 5);
        assertEquals(5, itemStack.getMethod("getCount").invoke(extracted));
        stored = inventoryType.getMethod("getStackInSlot", int.class).invoke(shared, 0);
        assertEquals(7, itemStack.getMethod("getCount").invoke(stored));

        Class<?> slotType = load("net.austizz.ultimatebankingsystem.storage.UbsItemInventorySlot");
        Object slot = slotType.getConstructor(inventoryType, int.class, int.class, int.class)
                .newInstance(shared, 0, 8, 18);
        assertEquals(7, itemStack.getMethod("getCount").invoke(slotType.getMethod("getItem").invoke(slot)));
        assertEquals(64, slotType.getMethod("getMaxStackSize").invoke(slot));
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

    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className, true, LOADER);
    }
}
