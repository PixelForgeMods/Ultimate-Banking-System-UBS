package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcFileDialog;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

final class PcArchiveContent extends PcUtilityContent {
    private static final int ROW = 19;
    private static final List<PcFileDialog.Filter> ARCHIVES = List.of(
            new PcFileDialog.Filter("ZIP and JAR archives", List.of("zip", "jar")));
    private record Row(String name, String path, boolean folder, int bytes) {}

    private final PcFileSystem drive;
    private PcFileSystem.Entry archive;
    private List<PcZip.Item> items = List.of();
    private List<Row> rows = List.of();
    private String prefix = "";
    private int offset, selected = -1;
    private long clickAt;
    private String clicked;
    private String preview;
    private Row removing;
    private int previewOffset;

    PcArchiveContent(PcFileSystem drive) {
        this.drive = drive;
        status = "Open a ZIP or JAR archive, or create a ZIP.";
    }

    @Override public void openFile(UUID id) throws IOException {
        var candidate = drive.get(id);
        if (!candidate.binary()) throw new IOException("Choose a ZIP or JAR archive.");
        var decoded = PcZip.read(candidate.payload());
        archive = candidate;
        items = decoded;
        prefix = "";
        preview = null;
        removing = null;
        refresh();
        status = items.size() + " archive entries";
    }

    private void refresh() {
        var view = new LinkedHashMap<String, Row>();
        for (var item : items) {
            if (!item.path().startsWith(prefix) || item.path().length() == prefix.length()) continue;
            String rest = item.path().substring(prefix.length());
            int slash = rest.indexOf('/');
            String name = slash < 0 ? rest : rest.substring(0, slash);
            boolean folder = slash >= 0 || item.directory();
            view.putIfAbsent(name, new Row(name, prefix + name, folder, folder ? 0 : item.bytes().length));
        }
        rows = view.values().stream().sorted(Comparator.comparing(Row::folder).reversed()
                .thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
        offset = 0;
        selected = -1;
        clicked = null;
    }

    private void openDialog() {
        services.pick(new PcFileDialog.Request(PcFileDialog.Mode.OPEN, archive == null ? null : archive.parent(), "",
                ARCHIVES, false), result -> openFile(result.items().getFirst()));
    }

    private void create() {
        var zip = List.of(new PcFileDialog.Filter("ZIP archive", List.of("zip")));
        services.pick(new PcFileDialog.Request(PcFileDialog.Mode.SAVE, archive == null ? null : archive.parent(),
                "Archive.zip", zip, false), result -> {
            byte[] bytes = PcZip.encode(List.of());
            UUID id = result.replace();
            if (id == null) id = drive.createBinary(result.folder(), result.name(), bytes);
            else drive.writeBinary(id, bytes);
            openFile(id);
            status = "ZIP created. Use Add files or Add folder.";
        });
    }

    private void add(boolean folders) {
        if (!editable()) return;
        services.pick(new PcFileDialog.Request(folders ? PcFileDialog.Mode.FOLDER : PcFileDialog.Mode.OPEN,
                archive.parent(), "", List.of(), !folders), result -> {
            var updated = new ArrayList<>(items);
            long[] budget = {items.stream().mapToLong(item -> item.bytes().length).sum()};
            for (var id : result.items()) addItem(updated, drive.get(id), prefix, 0, budget);
            save(updated, "Archive updated.");
        });
    }

    private void addItem(List<PcZip.Item> target, PcFileSystem.Entry entry, String parent, int depth, long[] budget) throws IOException {
        if (entry.shortcut() || archive.id().equals(entry.id())) return;
        if (depth >= 24 || target.size() >= PcZip.MAX_ENTRIES || budget[0] + entry.size() > PcZip.LIMIT) {
            throw new IOException("These items exceed the archive's size or depth limit.");
        }
        String path = parent + entry.name();
        byte[] bytes = entry.folder() ? new byte[0] : entry.binary() ? entry.payload() : entry.text().getBytes(StandardCharsets.UTF_8);
        budget[0] += bytes.length;
        target.add(new PcZip.Item(path, entry.folder(), bytes));
        if (entry.folder()) for (var child : drive.list(entry.id(), "")) addItem(target, child, path + "/", depth + 1, budget);
    }

    private boolean editable() { return archive != null && PcFileType.extension(archive.name()).equals("zip"); }

    private void save(List<PcZip.Item> next, String success) throws IOException {
        if (drive.get(archive.id()) != archive) throw new IOException("The archive changed elsewhere. Open it again.");
        drive.writeBinary(archive.id(), PcZip.encode(next));
        archive = drive.get(archive.id());
        items = List.copyOf(next);
        removing = null;
        refresh();
        status = success;
    }

    private Row current() { return selected >= 0 && selected < rows.size() ? rows.get(selected) : null; }

    private boolean isWithin(PcZip.Item item, Row row) {
        return item.path().equals(row.path) || row.folder && item.path().startsWith(row.path + "/");
    }

    private void extract(boolean all) {
        var row = current();
        if (archive == null || !all && row == null) return;
        UUID archiveId = archive.id();
        var chosen = all ? items : items.stream().filter(item -> isWithin(item, row)).toList();
        services.pick(new PcFileDialog.Request(PcFileDialog.Mode.FOLDER, archive.parent(), "", List.of(), false), result -> {
            if (drive.get(archiveId) != archive) throw new IOException("The archive changed elsewhere. Open it again.");
            drive.extract(result.folder(), chosen);
            status = "Extracted " + chosen.size() + " entries to " + drive.path(result.folder());
        });
    }

    private void test() {
        if (archive == null) return;
        guard(() -> {
            var checked = PcZip.read(drive.get(archive.id()).payload());
            status = "ZIP integrity check passed · " + checked.size() + " entries.";
        });
    }

    private void view() {
        var row = current();
        if (row == null) return;
        removing = null;
        if (row.folder) {
            prefix = row.path + "/";
            refresh();
        } else {
            var item = items.stream().filter(entry -> entry.path().equals(row.path)).findFirst().orElseThrow();
            previewOffset = 0;
            preview = (PcFileType.canImportAsText(row.name) || row.name.endsWith(".lua"))
                    ? new String(item.bytes(), 0, Math.min(item.bytes().length, 8192), StandardCharsets.UTF_8)
                    : row.name + "\n" + item.bytes().length + " bytes\nUse Extract selected to open this file in another app.";
        }
    }

    private void up() {
        if (prefix.isEmpty()) return;
        prefix = prefix.substring(0, prefix.lastIndexOf('/', prefix.length() - 2) + 1);
        refresh();
    }

    private void remove() {
        var row = current();
        if (row == null || !editable()) return;
        if (!row.equals(removing)) {
            removing = row;
            status = "Press Remove again to remove " + row.name + " from the ZIP.";
            return;
        }
        guard(() -> save(items.stream().filter(item -> !isWithin(item, row)).toList(), "Removed " + row.name + "."));
    }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }

    private void guard(IoAction action) {
        try { action.run(); }
        catch (IOException e) { status = e.getMessage(); }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        background(g);
        String[] titles = {"Open", "New ZIP", "Add files", "Add folder", "Extract all", "Test"};
        Runnable[] actions = {this::openDialog, this::create, editable() ? () -> add(false) : null, editable() ? () -> add(true) : null,
                archive != null && !items.isEmpty() ? () -> extract(true) : null, archive != null ? this::test : null};
        var glyphs = new PcFilesIcon[]{PcFilesIcon.OPEN, PcFilesIcon.NEW, PcFilesIcon.ADD, PcFilesIcon.FOLDER,
                PcFilesIcon.DOWNLOADS, PcFilesIcon.INFO};
        int step = (w - 13) / titles.length;
        for (int i = 0; i < titles.length; i++) {
            int left = x + 7 + i * step, center = left + (step - 3) / 2;
            button(g, left, y + 6, step - 3, 28, "", actions[i], mx, my, false);
            int color = actions[i] == null ? MUTED : ACCENT;
            glyphs[i].draw(g, center - 5, y + 9, 10, color);
            int textWidth = Math.min(step - 7, font.width(PcDesktopScreen.label(titles[i])));
            label(g, titles[i], center - textWidth / 2, y + 22, textWidth, actions[i] == null ? MUTED : TEXT);
        }

        button(g, x + 7, y + 39, 23, 17, "", prefix.isEmpty() ? null : this::up, mx, my, false);
        PcFilesIcon.UP.draw(g, x + 13, y + 42, 11, prefix.isEmpty() ? MUTED : ACCENT);
        label(g, archive == null ? "Archive Manager" : archive.name() + "  /  " + prefix, x + 36, y + 43, w - 44, ACCENT);
        int top = y + 75;
        int limit = Math.max(1, (h - 117) / ROW);
        offset = Math.clamp(offset, 0, Math.max(0, rows.size() - limit));
        label(g, "Name", x + 13, y + 61, w - 100, MUTED);
        label(g, "Size", x + w - 72, y + 61, 60, MUTED);
        g.fill(x + 8, top - 4, x + w - 7, top - 3, LINE);
        for (int i = offset; i < Math.min(rows.size(), offset + limit); i++) {
            var row = rows.get(i);
            int index = i, yy = top + (i - offset) * ROW;
            button(g, x + 7, yy, w - 14, ROW - 2, "", () -> {
                selected = index;
                removing = null;
                long now = System.currentTimeMillis();
                if (row.path.equals(clicked) && now - clickAt < PcDesktopScreen.DOUBLE_CLICK_MS) view();
                clicked = row.path;
                clickAt = now;
            }, mx, my, i == selected);
            label(g, row.name, x + 30, yy + 4, w - 110, TEXT);
            (row.folder ? PcFilesIcon.FOLDER : PcFilesIcon.FILE_TEXT).draw(g, x + 13, yy + 3, 11, ACCENT);
            if (!row.folder) label(g, row.bytes + " B", x + w - 72, yy + 4, 60, MUTED);
        }
        if (rows.isEmpty()) label(g, archive == null ? "No archive open" : "Empty folder", x + 14, top + 6, w - 28, MUTED);
        button(g, x + 7, y + h - 39, 54, 17, "View", current() == null ? null : this::view, mx, my, false);
        button(g, x + 66, y + h - 39, 103, 17, "Extract selected", current() == null ? null : () -> extract(false), mx, my, false);
        button(g, x + 173, y + h - 39, 80, 17, removing == null ? "Remove" : "Confirm remove",
                editable() && current() != null ? this::remove : null, mx, my, false);
        status(g);
        if (preview != null) {
            hits.clear();
            g.fill(x + 1, y + 57, x + w - 1, y + h - 18, CARD);
            button(g, x + 8, y + 61, 63, 18, "Close view", () -> preview = null, mx, my, true);
            var lines = font.split(PcDesktopScreen.label(preview), Math.max(1, w - 30));
            int capacity = Math.max(0, (h - 105) / 11);
            previewOffset = Math.clamp(previewOffset, 0, Math.max(0, lines.size() - capacity));
            for (int i = 0; i < Math.min(capacity, lines.size() - previewOffset); i++) {
                g.drawString(font, lines.get(i + previewOffset), x + 13, y + 85 + i * 11, TEXT, false);
            }
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int step = dy > 0 ? -3 : 3;
        if (preview != null) previewOffset = Math.max(0, previewOffset + step);
        else offset = Math.clamp(offset + step, 0, Math.max(0, rows.size() - Math.max(1, (h - 117) / ROW)));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (preview != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { preview = null; return true; }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                return mouseScrolled(0, 0, 0, key == GLFW.GLFW_KEY_UP ? 1 : -1);
            }
            return false;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_O) { openDialog(); return true; }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_N) { create(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER) { view(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) { up(); return true; }
        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && !rows.isEmpty()) {
            selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, rows.size() - 1);
            int capacity = Math.max(1, (h - 117) / ROW);
            if (selected < offset) offset = selected;
            if (selected >= offset + capacity) offset = selected - capacity + 1;
            removing = null;
            return true;
        }
        return false;
    }
}
