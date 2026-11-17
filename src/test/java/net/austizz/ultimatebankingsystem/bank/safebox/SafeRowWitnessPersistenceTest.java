package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SafeRowWitnessPersistenceTest {
    @Test
    void completeRowPositionSurvivesMetadataCopyAndCanBeInvalidated() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> service = Class.forName(
                "net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService", true, loader);
        Class<?> tagType = Class.forName("net.minecraft.nbt.CompoundTag", true, loader);
        Class<?> posType = Class.forName("net.minecraft.core.BlockPos", true, loader);
        Object metadata = tagType.getConstructor().newInstance();
        Object pos = posType.getConstructor(int.class, int.class, int.class).newInstance(-12, -60, 6);
        Method remember = service.getDeclaredMethod(
                "rememberReadyRow", tagType, String.class, String.class, posType);
        remember.setAccessible(true);
        remember.invoke(null, metadata, "safe-area-1", "minecraft:overworld", pos);

        Object reloaded = tagType.getMethod("copy").invoke(metadata);
        Object witnesses = tagType.getMethod("getCompound", String.class)
                .invoke(reloaded, "safeDepositReadyRows");
        Object saved = tagType.getMethod("getCompound", String.class)
                .invoke(witnesses, "safe-area-1");
        assertEquals("minecraft:overworld", tagType.getMethod("getString", String.class)
                .invoke(saved, "dimension"));
        assertEquals(-12, tagType.getMethod("getInt", String.class).invoke(saved, "x"));
        assertEquals(-60, tagType.getMethod("getInt", String.class).invoke(saved, "y"));
        assertEquals(6, tagType.getMethod("getInt", String.class).invoke(saved, "z"));

        Method forget = service.getDeclaredMethod("forgetReadyRow", tagType, String.class);
        forget.setAccessible(true);
        forget.invoke(null, reloaded, "safe-area-1");
        Object afterRemoval = tagType.getMethod("getCompound", String.class)
                .invoke(reloaded, "safeDepositReadyRows");
        assertFalse((Boolean) tagType.getMethod("contains", String.class)
                .invoke(afterRemoval, "safe-area-1"));
    }
}
