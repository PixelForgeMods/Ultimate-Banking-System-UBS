package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

final class PcClipboardState implements PcClipboard {
    private Item item;

    @Override public Item get() { return item; }
    @Override public void set(Item item) { this.item = item; }

    static void paste(PcClipboard clipboard, PcFileSystem drive, UUID folder) throws IOException {
        switch (clipboard.get()) {
            case Files files -> {
                drive.transferAll(files.ids(), folder, files.cut());
                if (files.cut()) clipboard.clear();
            }
            case Image image -> pasteImage(drive, folder, image);
            case Text text -> drive.create(folder, availableName(drive, folder, "Clipboard.txt"), false, text.text());
            case null -> throw new IOException("Copy an item first.");
        }
    }

    static UUID pasteImage(PcFileSystem drive, UUID folder, Image image) throws IOException {
        return drive.createBinary(folder, availableName(drive, folder, image.name()), image.bytes());
    }

    private static String availableName(PcFileSystem drive, UUID folder, String proposed) throws IOException {
        String name = PcFileSystem.checkedName(proposed);
        var taken = drive.list(folder, "").stream().map(entry -> entry.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String candidate = name;
        for (int i = 2; taken.contains(candidate.toLowerCase(Locale.ROOT)); i++) {
            String suffix = " (" + i + ")" + extension;
            if (suffix.length() >= 120) throw new IOException("Shorten the file name first.");
            candidate = stem.substring(0, Math.min(stem.length(), 120 - suffix.length())) + suffix;
        }
        return candidate;
    }
}
