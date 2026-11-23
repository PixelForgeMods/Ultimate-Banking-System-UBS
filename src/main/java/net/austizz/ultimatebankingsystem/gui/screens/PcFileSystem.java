package net.austizz.ultimatebankingsystem.gui.screens;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** A private virtual drive. File names and paths never resolve to host files. */
final class PcFileSystem {
    static final UUID ROOT = new UUID(0, 0);
    static final UUID DOCUMENTS = new UUID(0, 1);
    static final int TEXT_LIMIT = 16_384;
    private static final int ENTRY_LIMIT = 4096;
    static final long STORAGE_LIMIT = 16L * 1024 * 1024;
    private static final int LEGACY_FORMAT = 0x55424601;
    private static final int SHORTCUT_FORMAT = 0x55424602;
    private static final int FORMAT = 0x55424603;
    private static final Pattern RESERVED = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(?:\\..*)?");
    private final Path path;
    private Map<UUID, Entry> entries;
    private long revision;
    private UUID desktop;
    private boolean desktopInitialized;

    record Entry(UUID id, UUID parent, String name, boolean folder, String text, long modified, boolean trashed,
                 String appTarget, byte[] payload) {
        Entry(UUID id, UUID parent, String name, boolean folder, String text, long modified, boolean trashed) {
            this(id, parent, name, folder, text, modified, trashed, "", null);
        }

        boolean shortcut() {
            return !appTarget.isEmpty();
        }

        boolean binary() {
            return payload != null;
        }

        boolean empty() {
            return binary() ? payload.length == 0 : text.isEmpty();
        }

        int size() {
            return binary() ? payload.length : text.getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public byte[] payload() {
            return payload == null ? null : payload.clone();
        }

        Entry change(UUID newParent, String newName, String newText, boolean trash) {
            return new Entry(id, newParent, newName, folder, newText, System.currentTimeMillis(), trash, appTarget, payload);
        }
    }

    PcFileSystem(Path path) {
        this.path = path;
    }

    long revision() {
        return revision;
    }

    UUID desktopFolder() throws IOException {
        data();
        return desktop;
    }

    void initializeDesktop(Map<String, String> shortcuts) throws IOException {
        data();
        if (!desktopInitialized) {
            var next = copy();
            for (var shortcut : shortcuts.entrySet()) {
                putShortcut(next, shortcut.getKey(), shortcut.getValue());
            }
            desktopInitialized = true;
            try {
                commit(next);
            } catch (IOException e) {
                desktopInitialized = false;
                throw e;
            }
        }
    }

    void createDesktopShortcut(String target, String name) throws IOException {
        var next = copy();
        putShortcut(next, target, name);
        commit(next);
    }

    private void putShortcut(Map<UUID, Entry> entries, String target, String label) throws IOException {
        if (target.isBlank() || target.length() > 255) {
            throw new IOException("Invalid shortcut target.");
        }
        String name;
        try {
            name = checkedName(label);
        } catch (IOException e) {
            name = "App shortcut";
        }
        String proposed = name;
        for (int suffix = 2; exists(entries, desktop, proposed, null); suffix++) {
            String number = " (" + suffix + ")";
            proposed = name.substring(0, Math.min(name.length(), 120 - number.length())) + number;
        }
        var id = UUID.randomUUID();
        entries.put(id, new Entry(id, desktop, proposed, false, "", System.currentTimeMillis(), false, target, null));
    }

    long usedBytes() throws IOException {
        data();
        return path == null || Files.notExists(path) ? 0 : Files.size(path);
    }

    Entry get(UUID id) throws IOException {
        return live(data(), id);
    }

    List<Entry> pathEntries(UUID id) throws IOException {
        var snapshot = data();
        var path = new ArrayDeque<Entry>();
        for (Entry entry = live(snapshot, id); entry != null; entry = snapshot.get(entry.parent)) {
            path.addFirst(entry);
        }
        return List.copyOf(path);
    }

    String path(UUID id) throws IOException {
        return "C:\\" + String.join("\\", pathEntries(id).stream().skip(1).map(Entry::name).toList());
    }

    Entry resolve(UUID base, String address) throws IOException {
        String path = address.strip().replace('/', '\\');
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (path.isEmpty() || path.startsWith("\\\\")) {
            throw new IOException("Enter a path on the PC Drive.");
        }
        UUID current = base;
        if (path.length() >= 2 && path.charAt(1) == ':') {
            if (!path.regionMatches(true, 0, "C:", 0, 2) || path.length() > 2 && path.charAt(2) != '\\') {
                throw new IOException("Only C:\\ is available on this PC.");
            }
            current = ROOT;
            path = path.substring(2);
        } else if (path.startsWith("\\")) {
            current = ROOT;
        }
        var snapshot = data();
        Entry entry = live(snapshot, current);
        for (String segment : path.split("\\\\", -1)) {
            if (!entry.folder) throw new IOException("The path continues past a file.");
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (entry.parent != null) entry = live(snapshot, entry.parent);
                continue;
            }
            UUID parent = entry.id;
            entry = snapshot.values().stream().filter(child -> parent.equals(child.parent) && !child.trashed
                            && child.name.equalsIgnoreCase(segment)).findFirst()
                    .orElseThrow(() -> new IOException("Path not found: " + segment));
        }
        return entry;
    }

    List<Entry> list(UUID folder, String filter) throws IOException {
        Map<UUID, Entry> data = data();
        folder(data, folder);
        String needle = filter.strip().toLowerCase(Locale.ROOT);
        return data.values().stream().filter(entry -> !entry.id.equals(folder)
                        && (needle.isEmpty() ? folder.equals(entry.parent) && !entry.trashed
                        : entry.name.toLowerCase(Locale.ROOT).contains(needle)
                                && within(data, entry, folder, true)))
                .sorted(Comparator.comparing(Entry::folder).reversed()
                        .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    List<Entry> searchableEntries() throws IOException {
        var data = data();
        return data.values().stream().filter(entry -> !entry.id.equals(ROOT) && !entry.shortcut()
                && within(data, entry, ROOT, true)).toList();
    }

    List<Entry> trash() throws IOException {
        return data().values().stream().filter(Entry::trashed)
                .sorted(Comparator.comparingLong(Entry::modified).reversed()).toList();
    }

    UUID create(UUID parent, String name, boolean folder, String text) throws IOException {
        var next = copy();
        folder(next, parent);
        name = checkedName(name);
        available(next, parent, name, null);
        var entry = new Entry(UUID.randomUUID(), parent, name, folder, folder ? "" : checkedText(text),
                System.currentTimeMillis(), false);
        next.put(entry.id, entry);
        commit(next);
        return entry.id;
    }

    UUID createDefault(UUID parent, boolean folder) throws IOException {
        Map<UUID, Entry> current = data();
        String base = folder ? "New Folder" : "New Text Document";
        String extension = folder ? "" : ".txt";
        String name = base + extension;
        for (int suffix = 2; exists(current, parent, name, null); suffix++) {
            name = base + " (" + suffix + ")" + extension;
        }
        return create(parent, name, folder, "");
    }

    UUID importFile(UUID parent, Path source) throws IOException {
        var next = copy();
        folder(next, parent);
        if (source.getFileName() == null || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Drop an individual file.");
        }
        String name = checkedName(source.getFileName().toString());
        available(next, parent, name, null);
        if (Files.size(source) > STORAGE_LIMIT) {
            throw new IOException("The file exceeds the PC drive's 16 MiB capacity.");
        }
        byte[] payload;
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            payload = input.readNBytes((int) STORAGE_LIMIT + 1);
        }
        if (payload.length > STORAGE_LIMIT) {
            throw new IOException("The file exceeds the PC drive's 16 MiB capacity.");
        }
        String text = importText(name, payload);
        var id = UUID.randomUUID();
        next.put(id, new Entry(id, parent, name, false, text == null ? "" : text,
                System.currentTimeMillis(), false, "", text == null ? payload : null));
        commit(next);
        return id;
    }

    private static String importText(String name, byte[] payload) {
        if (!PcFileType.canImportAsText(name) || payload.length > TEXT_LIMIT * 4) return null;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(payload)).toString();
            return text.length() <= TEXT_LIMIT && text.indexOf(0) < 0 ? text : null;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    UUID createBinary(UUID parent, String name, byte[] payload) throws IOException {
        var next = copy();
        folder(next, parent);
        name = checkedName(name);
        available(next, parent, name, null);
        if (payload.length > STORAGE_LIMIT) throw new IOException("The image exceeds the PC drive's capacity.");
        UUID id = UUID.randomUUID();
        next.put(id, new Entry(id, parent, name, false, "", System.currentTimeMillis(), false, "", payload.clone()));
        commit(next);
        return id;
    }

    void addPictureSamples(Map<String, byte[]> samples) throws IOException {
        var pictures = list(ROOT, "").stream()
                .filter(entry -> entry.folder && entry.name.equalsIgnoreCase("Pictures")).findFirst().orElse(null);
        if (pictures == null) return;

        var next = copy();
        for (var sample : samples.entrySet()) {
            String name = checkedName(sample.getKey());
            if (exists(next, pictures.id, name, null)) continue;
            var id = UUID.randomUUID();
            next.put(id, new Entry(id, pictures.id, name, false, "", System.currentTimeMillis(), false, "",
                    sample.getValue().clone()));
        }
        if (next.size() != entries.size()) commit(next);
    }

    /** Commits the entire extraction at once, after name and collision checks. */
    void extract(UUID destination, List<PcZip.Item> items) throws IOException {
        PcZip.validate(items);
        var next = copy();
        folder(next, destination);
        var children = new java.util.HashMap<UUID, Map<String, Entry>>();
        for (var entry : next.values()) {
            if (!entry.trashed && entry.parent != null) {
                children.computeIfAbsent(entry.parent, ignored -> new java.util.HashMap<>())
                        .put(entry.name.toLowerCase(Locale.ROOT), entry);
            }
        }
        long now = System.currentTimeMillis();
        for (var item : items) {
            var parts = item.path().split("/");
            UUID parent = destination;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean directory = i < parts.length - 1 || item.directory();
                var siblings = children.computeIfAbsent(parent, ignored -> new java.util.HashMap<>());
                String key = part.toLowerCase(Locale.ROOT);
                var existing = siblings.get(key);
                if (existing != null) {
                    if (!directory || !existing.folder) throw new IOException("A destination already exists: " + part);
                    parent = existing.id;
                    continue;
                }
                var id = UUID.randomUUID();
                byte[] bytes = directory ? null : item.bytes();
                String text = bytes == null ? "" : importText(part, bytes);
                var entry = new Entry(id, parent, part, directory, text == null ? "" : text, now, false, "",
                        text == null ? bytes.clone() : null);
                siblings.put(key, entry);
                next.put(id, entry);
                parent = id;
                if (next.size() > ENTRY_LIMIT) throw new IOException("The drive has reached its item limit.");
            }
        }
        if (!next.equals(entries)) commit(next);
    }

    void write(UUID id, String text) throws IOException {
        var next = copy();
        var entry = live(next, id);
        if (entry.folder || entry.shortcut() || entry.binary()) {
            throw new IOException("Only text documents can be edited.");
        }
        next.put(id, entry.change(entry.parent, entry.name, checkedText(text), false));
        commit(next);
    }

    void writeBinary(UUID id, byte[] payload) throws IOException {
        var next = copy();
        var entry = live(next, id);
        if (entry.folder || entry.shortcut()) throw new IOException("Choose a file.");
        if (payload.length > STORAGE_LIMIT) throw new IOException("The file exceeds the PC drive's capacity.");
        next.put(id, new Entry(id, entry.parent, entry.name, false, "", System.currentTimeMillis(), false, "", payload.clone()));
        commit(next);
    }

    void rename(UUID id, String name) throws IOException {
        var next = copy();
        var entry = mutable(next, id);
        name = checkedName(name);
        available(next, entry.parent, name, id);
        next.put(id, entry.change(entry.parent, name, entry.text, false));
        commit(next);
    }

    void transfer(UUID id, UUID destination, boolean move) throws IOException {
        transferAll(List.of(id), destination, move);
    }

    void transferAll(List<UUID> items, UUID destination, boolean move) throws IOException {
        var next = copy();
        for (var id : items) transfer(next, id, destination, move);
        if (!next.equals(entries)) commit(next);
    }

    private void transfer(Map<UUID, Entry> next, UUID id, UUID destination, boolean move) throws IOException {
        var entry = move ? mutable(next, id) : live(next, id);
        folder(next, destination);
        if (within(next, next.get(destination), id, false)) {
            throw new IOException("Choose a location outside the source folder.");
        }
        if (move && destination.equals(entry.parent)) {
            return;
        }
        String name = entry.name;
        if (!move && destination.equals(entry.parent)) {
            int dot = entry.folder ? -1 : name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            for (int i = 1; ; i++) {
                String suffix = i == 1 ? " - Copy" : " - Copy (" + i + ")";
                if (stem.length() + extension.length() + suffix.length() > 120) {
                    throw new IOException("Shorten the source name before duplicating it.");
                }
                name = stem + suffix + extension;
                if (!exists(next, destination, name, null)) {
                    break;
                }
            }
        }
        available(next, destination, name, id);
        if (move) {
            next.put(id, entry.change(destination, name, entry.text, false));
        } else {
            var queue = new ArrayDeque<Entry>();
            var ids = new LinkedHashMap<UUID, UUID>();
            queue.add(entry);
            while (!queue.isEmpty()) {
                var item = queue.remove();
                var newId = UUID.randomUUID();
                ids.put(item.id, newId);
                boolean first = item.id.equals(id);
                next.put(newId, new Entry(newId, first ? destination : ids.get(item.parent),
                        first ? name : item.name, item.folder, item.text, System.currentTimeMillis(), false,
                        item.appTarget, item.payload));
                if (next.size() > ENTRY_LIMIT) {
                    throw new IOException("The drive has reached its item limit.");
                }
                data().values().stream().filter(child -> item.id.equals(child.parent) && !child.trashed)
                        .forEach(queue::add);
            }
        }
    }

    void recycle(UUID id) throws IOException {
        recycleAll(List.of(id));
    }

    void recycleAll(Collection<UUID> ids) throws IOException {
        var next = copy();
        for (UUID id : ids) {
            var entry = mutable(next, id);
            next.put(id, entry.change(entry.parent, entry.name, entry.text, true));
        }
        commit(next);
    }

    void restore(UUID id) throws IOException {
        var next = copy();
        var entry = next.get(id);
        if (entry == null || !entry.trashed) {
            throw new IOException("This item is not in the Recycle Bin.");
        }
        UUID parent = within(next, next.get(entry.parent), ROOT, true) ? entry.parent : ROOT;
        available(next, parent, entry.name, id);
        next.put(id, entry.change(parent, entry.name, entry.text, false));
        commit(next);
    }

    void emptyTrash() throws IOException {
        var before = data();
        var next = copy();
        next.values().removeIf(entry -> !within(before, entry, ROOT, true));
        commit(next);
    }

    private Map<UUID, Entry> copy() throws IOException {
        return new LinkedHashMap<>(data());
    }

    private static boolean within(Map<UUID, Entry> data, Entry entry, UUID ancestor, boolean liveOnly) {
        for (int hops = 0; entry != null && hops <= data.size(); hops++, entry = data.get(entry.parent)) {
            if (liveOnly && entry.trashed) {
                return false;
            }
            if (entry.id.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static Entry live(Map<UUID, Entry> data, UUID id) throws IOException {
        var entry = data.get(id);
        if (!within(data, entry, ROOT, true)) {
            throw new IOException("This item is unavailable.");
        }
        return entry;
    }

    private Entry mutable(Map<UUID, Entry> data, UUID id) throws IOException {
        var entry = live(data, id);
        if (ROOT.equals(id) || DOCUMENTS.equals(id) || desktop.equals(id)) {
            throw new IOException("This system folder is protected.");
        }
        return entry;
    }

    private static void folder(Map<UUID, Entry> data, UUID id) throws IOException {
        if (!live(data, id).folder) {
            throw new IOException("Choose a folder.");
        }
    }

    static String checkedName(String name) throws IOException {
        if (name.isBlank() || name.length() > 120 || name.endsWith(".") || name.endsWith(" ")
                || RESERVED.matcher(name).matches() || name.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0)) {
            throw new IOException("Enter a valid file or folder name.");
        }
        return name;
    }

    private static String checkedText(String text) throws IOException {
        if (text.length() > TEXT_LIMIT) {
            throw new IOException("The document exceeds the text limit.");
        }
        return text;
    }

    private static boolean exists(Map<UUID, Entry> data, UUID parent, String name, UUID except) {
        return data.values().stream().anyMatch(entry -> parent.equals(entry.parent) && !entry.trashed
                && !entry.id.equals(except) && entry.name.equalsIgnoreCase(name));
    }

    private static void available(Map<UUID, Entry> data, UUID parent, String name, UUID except) throws IOException {
        if (exists(data, parent, name, except)) {
            throw new IOException("An item with that name already exists.");
        }
    }

    private Map<UUID, Entry> data() throws IOException {
        if (entries != null) {
            return entries;
        }
        var loaded = new LinkedHashMap<UUID, Entry>();
        loaded.put(ROOT, new Entry(ROOT, null, "PC Drive", true, "", 0, false));
        if (path == null || Files.notExists(path)) {
            loaded.put(DOCUMENTS, new Entry(DOCUMENTS, ROOT, "Documents", true, "", 0, false));
            for (String name : List.of("Desktop", "Downloads", "Pictures")) {
                var id = UUID.randomUUID();
                loaded.put(id, new Entry(id, ROOT, name, true, "", 0, false));
            }
        } else {
            if (Files.size(path) > STORAGE_LIMIT) {
                throw new IOException("The PC drive exceeds its storage limit.");
            }
            try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                int format = in.readInt();
                int count = in.readInt();
                if ((format != FORMAT && format != SHORTCUT_FORMAT && format != LEGACY_FORMAT)
                        || count < 1 || count >= ENTRY_LIMIT) {
                    throw new IOException("The PC drive format is invalid.");
                }
                if (format != LEGACY_FORMAT) {
                    desktop = new UUID(in.readLong(), in.readLong());
                    desktopInitialized = in.readBoolean();
                }
                for (int i = 0; i < count; i++) {
                    var id = new UUID(in.readLong(), in.readLong());
                    var parent = new UUID(in.readLong(), in.readLong());
                    String name = checkedName(in.readUTF());
                    boolean folder = in.readBoolean();
                    String text = checkedText(in.readUTF());
                    long modified = in.readLong();
                    boolean trashed = in.readBoolean();
                    String target = format == LEGACY_FORMAT ? "" : in.readUTF();
                    byte[] payload = format == FORMAT ? readPayload(in) : null;
                    if (payload != null && (folder || !target.isEmpty() || !text.isEmpty())) {
                        throw new IOException("The PC drive has an invalid file entry.");
                    }
                    var entry = new Entry(id, parent, name, folder, text, modified, trashed, target, payload);
                    if (loaded.putIfAbsent(id, entry) != null) {
                        throw new IOException("The PC drive contains duplicate entries.");
                    }
                }
                if (in.read() != -1) {
                    throw new IOException("The PC drive has unexpected trailing data.");
                }
            }
            folder(loaded, DOCUMENTS);
            for (var entry : loaded.values()) {
                var parent = loaded.get(entry.parent);
                if (!entry.id.equals(ROOT) && (parent == null || !parent.folder || !within(loaded, entry, ROOT, false))) {
                    throw new IOException("The PC drive has an invalid folder structure.");
                }
            }
        }
        if (desktop == null) {
            desktop = loaded.values().stream().filter(entry -> ROOT.equals(entry.parent) && entry.folder
                    && !entry.trashed && entry.name.equalsIgnoreCase("Desktop")).map(Entry::id).findFirst().orElse(null);
            if (desktop == null) {
                desktop = UUID.randomUUID();
                String name = "Desktop";
                for (int suffix = 2; exists(loaded, ROOT, name, null); suffix++) {
                    name = "Desktop (" + suffix + ")";
                }
                loaded.put(desktop, new Entry(desktop, ROOT, name, true, "", 0, false));
            }
        } else {
            folder(loaded, desktop);
        }
        entries = loaded;
        return entries;
    }

    private static byte[] readPayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) return null;
        if (length < 0 || length > STORAGE_LIMIT) throw new IOException("The PC drive has an invalid file size.");
        byte[] payload = in.readNBytes(length);
        if (payload.length != length) throw new EOFException("The PC drive has incomplete file data.");
        return payload;
    }

    private void commit(Map<UUID, Entry> next) throws IOException {
        if (next.size() > ENTRY_LIMIT) {
            throw new IOException("The drive has reached its item limit.");
        }
        if (path != null) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "pc-drive-", ".tmp");
            try {
                try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                    out.writeInt(FORMAT);
                    out.writeInt(next.size() - 1);
                    out.writeLong(desktop.getMostSignificantBits());
                    out.writeLong(desktop.getLeastSignificantBits());
                    out.writeBoolean(desktopInitialized);
                    for (var entry : next.values()) {
                        if (entry.id.equals(ROOT)) {
                            continue;
                        }
                        out.writeLong(entry.id.getMostSignificantBits());
                        out.writeLong(entry.id.getLeastSignificantBits());
                        out.writeLong(entry.parent.getMostSignificantBits());
                        out.writeLong(entry.parent.getLeastSignificantBits());
                        out.writeUTF(entry.name);
                        out.writeBoolean(entry.folder);
                        out.writeUTF(entry.text);
                        out.writeLong(entry.modified);
                        out.writeBoolean(entry.trashed);
                        out.writeUTF(entry.appTarget);
                        out.writeInt(entry.binary() ? entry.payload.length : -1);
                        if (entry.binary()) out.write(entry.payload);
                    }
                }
                if (Files.size(temp) > STORAGE_LIMIT) {
                    throw new IOException("The PC drive is full.");
                }
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        entries = next;
        revision++;
    }
}
