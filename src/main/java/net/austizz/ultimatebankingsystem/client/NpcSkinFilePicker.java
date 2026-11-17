package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.NpcSkinActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Cross-platform desktop picker that transfers a standard Minecraft skin PNG to the server. */
public final class NpcSkinFilePicker {
    private static final int MAX_BYTES = 32 * 1024;

    private NpcSkinFilePicker() {
    }

    public static void choose(Consumer<String> result) {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture.supplyAsync(NpcSkinFilePicker::readSelectedFile).whenComplete((selected, failure) ->
                minecraft.execute(() -> {
                    if (failure != null) {
                        String message = rootMessage(failure);
                        if (minecraft.player != null) minecraft.player.displayClientMessage(
                                Component.literal("Could not import NPC skin: " + message)
                                        .withStyle(ChatFormatting.RED), false);
                        result.accept("Could not import skin: " + message);
                        return;
                    }
                    if (selected == null) {
                        result.accept("Skin import cancelled.");
                        return;
                    }
                    PacketDistributor.sendToServer(new NpcSkinActionPayload("UPLOAD", "", selected.fileName(),
                            selected.png()));
                    result.accept("Uploading " + selected.fileName() + " to the server skin library...");
                }));
    }

    private static SelectedFile readSelectedFile() {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png")).flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog("Import UBS NPC skin", "", filters,
                    "Minecraft skin PNG (*.png)", false);
        }
        if (selected == null || selected.isBlank()) return null;
        Path path = Path.of(selected).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 0 || Files.size(path) > MAX_BYTES) {
                throw new IOException("Skin PNGs must be between 1 byte and 32 KiB.");
            }
            return new SelectedFile(path.getFileName().toString(), Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) cursor = cursor.getCause();
        return cursor == null || cursor.getMessage() == null ? "Unknown error" : cursor.getMessage();
    }

    private record SelectedFile(String fileName, byte[] png) {
    }
}
