package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class PcAppInstallations {
    private PcAppInstallations() {}

    static Set<ResourceLocation> load(UUID player) {
        var ids = new HashSet<ResourceLocation>();
        Path file = file(player);
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    var id = ResourceLocation.tryParse(line);
                    if (id != null && !line.isBlank()) {
                        ids.add(id);
                    }
                }
            } catch (IOException e) {
                UltimateBankingSystem.LOGGER.warn("Could not read installed PC apps", e);
            }
        }
        return ids;
    }

    static boolean save(UUID player, Set<ResourceLocation> ids) {
        return save(file(player), String.join("\n", ids.stream().map(ResourceLocation::toString).sorted().toList()));
    }

    static boolean save(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "pc-apps-", ".tmp");
            try {
                Files.writeString(temp, text);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (IOException e) {
            UltimateBankingSystem.LOGGER.warn("Could not save PC preferences", e);
            return false;
        }
    }

    private static Path file(UUID player) {
        return FMLPaths.CONFIGDIR.get().resolve("ultimatebankingsystem/pc-apps").resolve(player + ".txt");
    }
}
