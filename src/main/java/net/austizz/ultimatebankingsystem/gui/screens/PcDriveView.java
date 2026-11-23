package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcDrive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

final class PcDriveView implements PcDrive {
    private final PcFileSystem drive;

    PcDriveView(PcFileSystem drive) { this.drive = drive; }

    private Item item(PcFileSystem.Entry entry) {
        return new Item(entry.id(), entry.parent(), entry.name(), entry.folder(), entry.binary(), entry.size());
    }

    @Override public UUID root() { return PcFileSystem.ROOT; }
    @Override public Item get(UUID id) throws IOException { return item(drive.get(id)); }
    @Override public List<Item> list(UUID folder) throws IOException {
        return drive.list(folder, "").stream().map(this::item).toList();
    }
    @Override public Item resolve(UUID folder, String virtualPath) throws IOException { return item(drive.resolve(folder, virtualPath)); }
    @Override public String path(UUID id) throws IOException { return drive.path(id); }
    @Override public byte[] read(UUID id) throws IOException {
        var entry = file(id);
        return entry.binary() ? entry.payload() : entry.text().getBytes(StandardCharsets.UTF_8);
    }
    @Override public String readText(UUID id) throws IOException {
        var entry = file(id);
        if (entry.binary()) throw new IOException("This file contains binary data.");
        return entry.text();
    }
    @Override public UUID createFolder(UUID parent, String name) throws IOException { return drive.create(parent, name, true, ""); }
    @Override public UUID create(UUID parent, String name, byte[] bytes) throws IOException { return drive.createBinary(parent, name, bytes); }
    @Override public UUID createText(UUID parent, String name, String text) throws IOException { return drive.create(parent, name, false, text); }
    @Override public void write(UUID id, byte[] bytes) throws IOException { drive.writeBinary(id, bytes); }
    @Override public void writeText(UUID id, String text) throws IOException { drive.write(id, text); }
    @Override public void rename(UUID id, String name) throws IOException { drive.rename(id, name); }
    @Override public void move(UUID id, UUID folder) throws IOException { drive.transfer(id, folder, true); }
    @Override public void recycle(UUID id) throws IOException { drive.recycle(id); }

    private PcFileSystem.Entry file(UUID id) throws IOException {
        var entry = drive.get(id);
        if (entry.folder() || entry.shortcut()) throw new IOException("Choose a file.");
        return entry;
    }
}
