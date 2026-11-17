package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShelfFillActionPayloadTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void bulkPartialFillAndIndividualPricesRoundTrip() throws Exception {
        Class<?> priceType = load("net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload$PriceOverride");
        Object price = priceType.getConstructor(String.class, long.class)
                .newInstance("create:brass_ingot", 1250L);
        Class<?> payloadType = load("net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload");
        Object payload = payloadType.getConstructor(String.class, int.class, int.class, int.class,
                        boolean.class, boolean.class, String.class, long.class, List.class, List.class, List.class)
                .newInstance("APPLY", 0, 0, 0, true, true, "create", 500L,
                        List.of("create:excluded_item"), List.of(price), List.of());

        Object buffer = buffer();
        try {
            Object codec = payloadType.getField("STREAM_CODEC").get(null);
            Class<?> codecType = load("net.minecraft.network.codec.StreamCodec");
            codecType.getMethod("encode", Object.class, Object.class).invoke(codec, buffer, payload);
            Object decoded = codecType.getMethod("decode", Object.class).invoke(codec, buffer);
            assertEquals(payload, decoded);
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }

    @Test
    void itemListIsBoundedBeforeEncoding() throws Exception {
        Class<?> payloadType = load("net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload");
        Constructor<?> constructor = payloadType.getConstructor(String.class, int.class, int.class, int.class,
                boolean.class, boolean.class, String.class, long.class, List.class, List.class, List.class);
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 2049; i++) tooMany.add("minecraft:item_" + i);

        assertEquals(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                () -> constructor.newInstance("APPLY", 0, 0, 0, false, false, "", 100L,
                        tooMany, List.of(), List.of())).getCause().getClass());
    }

    @Test
    void inventoryChoicesAreBounded() throws Exception {
        Class<?> choiceType = load("net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload$InventoryChoice");
        Class<?> stackType = Class.forName("net.minecraft.world.item.ItemStack", false, LOADER);
        Object choice = choiceType.getConstructor(int.class, stackType).newInstance(4, null);
        Class<?> payloadType = load("net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload");
        Constructor<?> constructor = payloadType.getConstructor(String.class, int.class, int.class, int.class,
                boolean.class, boolean.class, String.class, long.class, List.class, List.class, List.class);
        assertEquals("@inventory:4", payloadType.getMethod("inventoryKey", int.class).invoke(null, 4));
        assertEquals(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                () -> constructor.newInstance("APPLY", 0, 0, 0, false, false, "", 500L,
                        List.of("@inventory:4"), List.of(), java.util.Collections.nCopies(42, choice)))
                .getCause().getClass());
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
