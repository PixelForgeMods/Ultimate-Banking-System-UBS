package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Collection;
import java.util.UUID;

final class PcDesktopPreferences {
    private PcDesktopPreferences() {}

    static Path path(Minecraft minecraft, GlobalPos pc) {
        var integrated = minecraft.getSingleplayerServer();
        var remote = minecraft.getCurrentServer();
        if (integrated == null && remote == null) {
            return null;
        }
        String world = integrated != null ? "world:" + integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : "server:" + remote.ip;
        UUID desktop = UUID.nameUUIDFromBytes((world + "|" + pc.dimension().location() + "|" + pc.pos().asLong())
                .getBytes(StandardCharsets.UTF_8));
        return FMLPaths.CONFIGDIR.get().resolve("ultimatebankingsystem/pc-desktops")
                .resolve(minecraft.getUser().getProfileId().toString()).resolve(desktop + ".properties");
    }

    static void load(Path path, PcDesktopWindows<PcDesktopScreen.App> windows, Collection<PcDesktopScreen.App> apps) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            settings.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            UltimateBankingSystem.LOGGER.warn("Could not read PC desktop preferences", e);
            return;
        }
        windows.notifications.load(settings);
        windows.clock.load(settings);
        int size = integer(settings, "icon.size", 22);
        windows.desktopIconSize = size == 16 || size == 28 ? size : 22;
        windows.showDesktopIcons = Boolean.parseBoolean(settings.getProperty("icon.visible", "true"));
        windows.darkFileIcons = Boolean.parseBoolean(settings.getProperty("fileIcons.dark", "false"));
        windows.filePanes.navigationWidth = integer(settings, "files.navigationWidth", 0);
        windows.filePanes.inspectorWidth = integer(settings, "files.inspectorWidth", 0);
        windows.photoWallpaper = imagePreference(settings, "photo.wallpaper");
        windows.photoLockScreen = imagePreference(settings, "photo.lockScreen");
        windows.descendingDesktopOrder = Boolean.parseBoolean(settings.getProperty("icon.descending", "false"));
        windows.quickFoldersInitialized = settings.containsKey("folders.quick");
        windows.samplePicturesInitialized = Boolean.parseBoolean(settings.getProperty("pictures.samplesAdded", "false"));
        loadFolderPins(settings.getProperty("folders.quick", ""), windows.quickFolders);
        loadFolderPins(settings.getProperty("folders.start", ""), windows.startFolders);
        for (var app : apps) {
            String key = app.id().toString();
            String name = settings.getProperty(key + ".name");
            if (name != null && !name.isBlank() && name.length() <= 64) {
                windows.desktopNames.put(app, name);
            }
            int column = integer(settings, key + ".column", -1);
            int row = integer(settings, key + ".row", -1);
            if (column >= 0 && row >= 0) {
                windows.desktopPositions.put(app, new PcDesktopWindows.Cell(column, row));
            }
        }
        for (String key : settings.stringPropertyNames()) {
            if (key.startsWith("files.view.")) {
                try {
                    var id = UUID.fromString(key.substring("files.view.".length()));
                    int step = integer(settings, key, Integer.MIN_VALUE);
                    if (PcFilesView.validStep(step)) windows.fileViews.put(id, step);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed folder IDs in local preferences.
                }
            }
            if (key.startsWith("item.") && key.endsWith(".column")) {
                try {
                    String prefix = key.substring(0, key.length() - 7);
                    var id = UUID.fromString(prefix.substring(5));
                    int column = integer(settings, key, -1);
                    int row = integer(settings, prefix + ".row", -1);
                    if (column >= 0 && row >= 0) {
                        windows.desktopItemPositions.put(id, new PcDesktopWindows.Cell(column, row));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed entry IDs in local preferences.
                }
            }
            if (key.startsWith("defaultApp.")) {
                String extension = key.substring("defaultApp.".length());
                var app = ResourceLocation.tryParse(settings.getProperty(key));
                if (app != null && (extension.isEmpty() || extension.matches("[a-z0-9][a-z0-9_+\\-]{0,31}"))) {
                    windows.defaultApps.put(extension, app.toString());
                }
            }
            if (key.startsWith("pin.")) {
                var id = ResourceLocation.tryParse(key.substring(4));
                if (id != null) {
                    windows.savedPins.put(id.toString(), Boolean.parseBoolean(settings.getProperty(key)));
                }
            }
        }
    }

    static void save(Path path, PcDesktopWindows<PcDesktopScreen.App> windows) {
        if (path == null) {
            return;
        }
        var settings = new Properties();
        settings.setProperty("icon.size", Integer.toString(windows.desktopIconSize));
        settings.setProperty("icon.visible", Boolean.toString(windows.showDesktopIcons));
        settings.setProperty("fileIcons.dark", Boolean.toString(windows.darkFileIcons));
        if (windows.photoWallpaper != null) settings.setProperty("photo.wallpaper", windows.photoWallpaper.toString());
        if (windows.photoLockScreen != null) settings.setProperty("photo.lockScreen", windows.photoLockScreen.toString());
        settings.setProperty("icon.descending", Boolean.toString(windows.descendingDesktopOrder));
        if (windows.quickFoldersInitialized) {
            settings.setProperty("folders.quick", folderPins(windows.quickFolders));
        }
        settings.setProperty("folders.start", folderPins(windows.startFolders));
        settings.setProperty("pictures.samplesAdded", Boolean.toString(windows.samplePicturesInitialized));
        windows.desktopPositions.forEach((app, cell) -> {
            settings.setProperty(app.id() + ".column", Integer.toString(cell.column()));
            settings.setProperty(app.id() + ".row", Integer.toString(cell.row()));
        });
        windows.desktopNames.forEach((app, name) -> settings.setProperty(app.id() + ".name", name));
        windows.desktopItemPositions.forEach((id, cell) -> {
            settings.setProperty("item." + id + ".column", Integer.toString(cell.column()));
            settings.setProperty("item." + id + ".row", Integer.toString(cell.row()));
        });
        windows.savedPins.forEach((id, pin) -> settings.setProperty("pin." + id, Boolean.toString(pin)));
        windows.fileViews.forEach((id, step) -> settings.setProperty("files.view." + id, Integer.toString(step)));
        settings.setProperty("files.navigationWidth", Integer.toString(windows.filePanes.navigationWidth));
        settings.setProperty("files.inspectorWidth", Integer.toString(windows.filePanes.inspectorWidth));
        windows.defaultApps.forEach((extension, app) -> settings.setProperty("defaultApp." + extension, app));
        windows.notifications.store(settings);
        windows.clock.store(settings);
        try (var text = new StringWriter()) {
            settings.store(text, null);
            PcAppInstallations.save(path, text.toString());
        } catch (IOException e) {
            UltimateBankingSystem.LOGGER.warn("Could not encode PC desktop preferences", e);
        }
    }

    private static void loadFolderPins(String value, Collection<UUID> pins) {
        for (String id : value.split(",")) {
            if (id.isEmpty()) continue;
            try {
                pins.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed folder IDs.
            }
        }
    }

    private static String folderPins(Collection<UUID> pins) {
        return String.join(",", pins.stream().map(UUID::toString).toList());
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static UUID imagePreference(Properties properties, String key) {
        try {
            String id = properties.getProperty(key);
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
