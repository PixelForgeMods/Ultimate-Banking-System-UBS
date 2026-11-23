package net.austizz.ultimatebankingsystem.gui.screens;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class PcZip {
    static final int LIMIT = 12 * 1024 * 1024;
    static final int MAX_ENTRIES = 1024;
    private static final int MAX_DEPTH = 24;

    record Item(String path, boolean directory, byte[] bytes) {}

    private PcZip() {}

    static List<Item> read(byte[] bytes) throws IOException {
        int expected = entryCount(bytes);
        var items = new ArrayList<Item>();
        int expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (items.size() == MAX_ENTRIES) throw new IOException("The archive contains too many items.");
                int remaining = LIMIT - expanded;
                byte[] data = zip.readNBytes(remaining + 1);
                if (data.length > remaining) throw new IOException("This archive expands beyond the PC's limit.");
                zip.closeEntry();
                if (entry.isDirectory() && data.length > 0) throw new IOException("An archive folder contains data.");
                expanded += data.length;
                items.add(new Item(path(entry.getName(), entry.isDirectory()), entry.isDirectory(), data));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("The archive contains invalid text or paths.", e);
        }
        if (items.size() != expected) throw new IOException("The ZIP directory does not match its entries.");
        validate(items);
        return List.copyOf(items);
    }

    static byte[] encode(List<Item> items) throws IOException {
        validate(items);
        var data = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(data)) {
            for (var item : items) {
                zip.putNextEntry(new ZipEntry(item.path + (item.directory ? "/" : "")));
                zip.write(item.bytes);
                zip.closeEntry();
            }
        }
        if (data.size() > PcFileSystem.STORAGE_LIMIT) throw new IOException("The ZIP file is too large.");
        return data.toByteArray();
    }

    static void validate(List<Item> items) throws IOException {
        if (items.size() > MAX_ENTRIES) throw new IOException("The archive contains too many items.");
        Map<String, Boolean> entries = new HashMap<>();
        long expanded = 0;
        for (var item : items) {
            String canonical = path(item.path, item.directory);
            if (!canonical.equals(item.path)) throw new IOException("An archive entry is not a canonical relative path.");
            String name = canonical.toLowerCase(Locale.ROOT);
            if (entries.putIfAbsent(name, item.directory) != null) throw new IOException("Duplicate archive entry: " + item.path);
            expanded += item.bytes.length;
            if (expanded > LIMIT) throw new IOException("This archive expands beyond the PC's limit.");
        }
        for (String name : entries.keySet()) {
            for (int i = name.indexOf('/'); i >= 0; i = name.indexOf('/', i + 1)) {
                if (Boolean.FALSE.equals(entries.get(name.substring(0, i)))) {
                    throw new IOException("An archive file conflicts with a folder path.");
                }
            }
        }
    }

    private static String path(String name, boolean directory) throws IOException {
        String canonical = name.replace('\\', '/');
        if (directory && canonical.endsWith("/")) canonical = canonical.substring(0, canonical.length() - 1);
        if (canonical.isEmpty() || canonical.length() > 1536) throw new IOException("Invalid archive entry name.");
        var parts = canonical.split("/", -1);
        if (parts.length > MAX_DEPTH) throw new IOException("Archive folders are too deeply nested.");
        for (String part : parts) {
            if (part.isBlank() || !part.equals(PcFileSystem.checkedName(part))) {
                throw new IOException("The archive contains an unsafe path.");
            }
        }
        return canonical;
    }

    private static int entryCount(byte[] bytes) throws IOException {
        for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65_557); i--) {
            if ((bytes[i] & 255) != 0x50 || bytes[i + 1] != 0x4B || bytes[i + 2] != 5 || bytes[i + 3] != 6
                    || i + 22 + shortAt(bytes, i + 20) != bytes.length) continue;
            int count = shortAt(bytes, i + 10);
            if (shortAt(bytes, i + 4) != 0 || shortAt(bytes, i + 6) != 0 || shortAt(bytes, i + 8) != count
                    || count > MAX_ENTRIES) throw new IOException("Split or oversized ZIP archives are unsupported.");
            int size = intAt(bytes, i + 12), offset = intAt(bytes, i + 16);
            if (size < 0 || offset < 0 || (long) size + offset != i) throw new IOException("Invalid ZIP directory location.");
            if (offset == 0 && count != 0 || offset > 0 && (offset < 30 || intAt(bytes, 0) != 0x04034B50)) {
                throw new IOException("Invalid ZIP local header.");
            }
            int next = offset;
            for (int entry = 0; entry < count; entry++) {
                if (next > i - 46 || intAt(bytes, next) != 0x02014B50) throw new IOException("Invalid ZIP directory entry.");
                next += 46 + shortAt(bytes, next + 28) + shortAt(bytes, next + 30) + shortAt(bytes, next + 32);
            }
            if (next != i) throw new IOException("Invalid ZIP directory length.");
            return count;
        }
        throw new IOException("This file has no valid ZIP directory.");
    }

    private static int intAt(byte[] bytes, int index) {
        return shortAt(bytes, index) | (shortAt(bytes, index + 2) << 16);
    }

    private static int shortAt(byte[] bytes, int index) { return (bytes[index] & 255) | ((bytes[index + 1] & 255) << 8); }
}
