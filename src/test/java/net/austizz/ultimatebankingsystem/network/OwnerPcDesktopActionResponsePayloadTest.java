package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnerPcDesktopActionResponsePayloadTest {
    @Test
    void shelfReportLargerThanTheOldStringLimitRoundTrips() throws Exception {
        ClassLoader loader = NeoForgeTestClassLoader.get();
        Class<?> payloadType = Class.forName(
                "net.austizz.ultimatebankingsystem.network.OwnerPcDesktopActionResponsePayload", true, loader);
        String report = "@shelf_card=".repeat(3_967);
        Object payload = payloadType.getConstructor(String.class, boolean.class, String.class)
                .newInstance("SHOP_SCAN", true, report);
        Class<?> byteBufType = Class.forName("io.netty.buffer.ByteBuf", true, loader);
        Object source = Class.forName("io.netty.buffer.Unpooled", true, loader)
                .getMethod("buffer").invoke(null);
        Class<?> registryType = Class.forName("net.minecraft.core.RegistryAccess", true, loader);
        Object registries = registryType.getField("EMPTY").get(null);
        Object buffer = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf", true, loader)
                .getConstructor(byteBufType, registryType).newInstance(source, registries);
        try {
            Object codec = payloadType.getField("STREAM_CODEC").get(null);
            Class<?> codecType = Class.forName("net.minecraft.network.codec.StreamCodec", true, loader);
            codecType.getMethod("encode", Object.class, Object.class).invoke(codec, buffer, payload);
            assertEquals(payload, codecType.getMethod("decode", Object.class).invoke(codec, buffer));
        } finally {
            buffer.getClass().getMethod("release").invoke(buffer);
        }
    }
}
