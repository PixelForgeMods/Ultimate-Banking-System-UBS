package net.austizz.ultimatebankingsystem.api.pc;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** A PC drive view. Item IDs survive moves and renames. Use on the client thread. */
public interface PcDrive {
    record Item(UUID id, UUID parent, String name, boolean folder, boolean binary, int bytes) {}

    UUID root();
    Item get(UUID id) throws IOException;
    List<Item> list(UUID folder) throws IOException;
    Item resolve(UUID folder, String virtualPath) throws IOException;
    String path(UUID id) throws IOException;
    byte[] read(UUID id) throws IOException;
    String readText(UUID id) throws IOException;
    UUID createFolder(UUID parent, String name) throws IOException;
    UUID create(UUID parent, String name, byte[] bytes) throws IOException;
    UUID createText(UUID parent, String name, String text) throws IOException;
    void write(UUID id, byte[] bytes) throws IOException;
    void writeText(UUID id, String text) throws IOException;
    void rename(UUID id, String name) throws IOException;
    void move(UUID id, UUID folder) throws IOException;
    void recycle(UUID id) throws IOException;
}
