package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcFileDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

final class PcFilePicker {
    private static final int PANEL = 0xFF222C3C, FIELD = 0xFF151F2D, LINE = 0xFF465970;
    private static final int TEXT = 0xFFEAF3FF, MUTED = 0xFFADC1D9, ACCENT = 0xFF8BD5EC, SELECTED = 0xFF385570;
    private static final int ROW = 19;
    private record Hit(Rectangle box, Runnable action) {}
    private final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
    private final PcFileSystem drive;
    private final PcFileDialog.Request request;
    private final PcFileDialog.Accepted accepted;
    private final Runnable cancel;
    private final PcFilesContent.FileIcon icon;
    private final EditBox address;
    private final EditBox name;
    private final EditBox find;
    private final List<Hit> hits = new ArrayList<>();
    private final LinkedHashSet<UUID> selection = new LinkedHashSet<>();
    private final List<UUID> history = new ArrayList<>();
    private List<PcFileSystem.Entry> entries = List.of();
    private List<PcFileSystem.Entry> places = List.of();
    private UUID folder, clicked;
    private int anchor = -1;
    private long clickedAt, revision = -1;
    private PcFileSystem.Entry overwrite;
    private int filter, scroll, cursor = -1, capacity, historyIndex;
    private int x, y, w, h;
    private boolean filterOpen, newFolder;
    private String error = "";

    PcFilePicker(PcFileSystem drive, PcFileDialog.Request request, PcFileDialog.Accepted accepted,
                 Runnable cancel, PcFilesContent.FileIcon icon) {
        this.drive = drive;
        this.request = request;
        this.accepted = accepted;
        this.cancel = cancel;
        this.icon = icon;
        address = field("Location");
        name = field("File name");
        find = field("Search folder");
        find.setHint(PcDesktopScreen.label("Search folder"));
        find.setMaxLength(120);
        name.setValue(request.suggestedName());
        name.setResponder(ignored -> overwrite = null);
        folder = request.initialFolder() == null ? PcFileSystem.DOCUMENTS : request.initialFolder();
        try {
            if (!drive.get(folder).folder()) folder = PcFileSystem.DOCUMENTS;
        } catch (IOException e) { folder = PcFileSystem.DOCUMENTS; }
        history.add(folder);
        find.setResponder(ignored -> {
            selection.clear();
            clicked = null;
            cursor = anchor = -1;
            scroll = 0;
            overwrite = null;
            if (request.mode() == PcFileDialog.Mode.OPEN) name.setValue("");
            refresh();
        });
        refresh();
        name.setFocused(request.mode() == PcFileDialog.Mode.SAVE);
        if (name.isFocused()) {
            name.moveCursorToEnd(false);
            name.setHighlightPos(0);
        }
    }

    private EditBox field(String label) {
        var field = new EditBox(font, 0, 0, 80, 16, PcDesktopScreen.label(label));
        field.setMaxLength(4096);
        field.setBordered(false);
        field.setTextShadow(false);
        field.setFormatter((value, offset) -> PcDesktopScreen.label(value).getVisualOrderText());
        return field;
    }

    private String title() {
        return switch (request.mode()) {
            case OPEN -> "Open";
            case SAVE -> "Save as";
            case FOLDER -> "Select folder";
        };
    }

    private void refresh() {
        try {
            var listing = drive.list(folder, find.getValue());
            entries = listing.stream().filter(entry -> !entry.shortcut()).filter(entry -> entry.folder()
                    || request.mode() != PcFileDialog.Mode.FOLDER && matches(entry.name())).toList();
            places = drive.list(PcFileSystem.ROOT, "").stream().filter(PcFileSystem.Entry::folder).toList();
            if (!address.isFocused()) address.setValue(drive.path(folder));
            revision = drive.revision();
        } catch (IOException e) {
            entries = List.of();
            error = e.getMessage();
        }
    }

    private boolean matches(String fileName) {
        return request.filters().isEmpty() || currentFilter().extensions().isEmpty()
                || currentFilter().extensions().contains(PcFileType.extension(fileName));
    }

    private PcFileDialog.Filter currentFilter() { return request.filters().get(filter); }

    private void move(UUID id, boolean append) {
        try {
            if (!drive.get(id).folder()) throw new IOException("Choose a folder.");
            if (append && !id.equals(folder)) {
                history.subList(historyIndex + 1, history.size()).clear();
                history.add(id);
                historyIndex++;
            }
            folder = id;
            selection.clear();
            clicked = null;
            cursor = anchor = -1;
            scroll = 0;
            overwrite = null;
            error = "";
            address.setFocused(false);
            find.setValue("");
            if (request.mode() != PcFileDialog.Mode.SAVE) name.setValue("");
            refresh();
        } catch (IOException e) { error = e.getMessage(); }
    }

    private void history(int step) {
        int next = historyIndex + step;
        if (next < 0 || next >= history.size()) return;
        UUID dest = history.get(next);
        move(dest, false);
        if (folder.equals(dest)) historyIndex = next;
    }

    void render(GuiGraphics g, int width, int height, int mx, int my, float partial) {
        if (revision != drive.revision()) refresh();
        hits.clear();
        w = Math.min(484, width - 14);
        h = Math.min(274, height - 12);
        x = (width - w) / 2;
        y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x93081120);
        PcDesktopScreen.roundRect(g, x, y, w, h, LINE);
        PcDesktopScreen.roundRect(g, x + 1, y + 1, w - 2, h - 2, PANEL);
        text(g, title(), x + 11, y + 8, w - 40, TEXT);
        button(g, x + w - 27, y + 3, 22, 19, "×", cancel, mx, my, false);
        button(g, x + 7, y + 28, 19, 18, "‹", () -> history(-1), mx, my, false);
        button(g, x + 28, y + 28, 19, 18, "›", () -> history(1), mx, my, false);
        button(g, x + 49, y + 28, 19, 18, "↑", () -> {
            try {
                UUID parent = drive.get(folder).parent();
                if (parent != null) move(parent, true);
            } catch (IOException e) { error = e.getMessage(); }
        }, mx, my, false);
        int folderButton = w > 335 ? 74 : 28;
        int searchWidth = Math.min(115, w / 4);
        int searchX = x + w - folderButton - searchWidth - 10;
        drawField(g, address, x + 71, y + 28, searchX - x - 75, mx, my, partial);
        drawField(g, find, searchX, y + 28, searchWidth, mx, my, partial);
        button(g, x + w - folderButton - 6, y + 28, folderButton, 18, w > 335 ? "New folder" : "+", () -> {
            newFolder = true;
            name.setValue("");
            focus(name);
        }, mx, my, false);

        int listTop = y + 57, footer = y + h - 65, side = Math.min(117, w / 3);
        int listing = x + side + 7;
        capacity = Math.max(1, (footer - listTop - 14) / ROW);
        scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - capacity));
        g.fill(x + 6, listTop, x + side + 2, footer, FIELD);
        button(g, x + 8, listTop + 2, side - 8, 17, "PC Drive", () -> move(PcFileSystem.ROOT, true), mx, my,
                folder.equals(PcFileSystem.ROOT));
        for (int i = 0; i < places.size() && listTop + 21 + i * 18 < footer - 17; i++) {
            var place = places.get(i);
            int ry = listTop + 21 + 18 * i;
            button(g, x + 8, ry, side - 8, 17, "    " + place.name(), () -> move(place.id(), true), mx, my, folder.equals(place.id()));
            icon.render(g, place, x + 11, ry + 3, 11);
        }

        text(g, "Name", listing + 4, listTop - 1, w - side - 20, MUTED);
        g.fill(listing, listTop + 11, x + w - 9, listTop + 12, LINE);
        for (int i = scroll; i < Math.min(entries.size(), scroll + capacity); i++) {
            var entry = entries.get(i);
            int rowY = listTop + 14 + ROW * (i - scroll);
            button(g, listing, rowY, x + w - 9 - listing, ROW - 1, "     " + entry.name(),
                    () -> choose(entry, net.minecraft.client.gui.screens.Screen.hasControlDown(),
                            net.minecraft.client.gui.screens.Screen.hasShiftDown(), true), mx, my, selection.contains(entry.id()));
            icon.render(g, entry, listing + 4, rowY + 3, 12);
        }
        if (entries.isEmpty()) text(g, request.mode() == PcFileDialog.Mode.FOLDER ? "No subfolders" : "No matching items", listing + 4, listTop + 24, w - side - 25, MUTED);
        if (entries.size() > capacity) {
            int track = Math.max(1, footer - listTop - 16);
            int thumb = Math.max(6, track * capacity / entries.size());
            int at = listTop + 14 + (track - thumb) * scroll / (entries.size() - capacity);
            g.fill(x + w - 8, at, x + w - 6, at + thumb, MUTED);
        }
        g.fill(x + 1, footer, x + w - 1, footer + 1, LINE);
        int fieldStart = x + (w >= 355 ? 72 : 61);
        boolean showName = newFolder || request.mode() != PcFileDialog.Mode.FOLDER;
        if (showName) {
            text(g, newFolder ? "Folder" : "File name", x + 9, footer + 9, fieldStart - x - 12, MUTED);
            int filterWidth = request.filters().isEmpty() || newFolder ? 0 : Math.min(145, w / 3);
            drawField(g, name, fieldStart, footer + 5, x + w - fieldStart - filterWidth - 10, mx, my, partial);
            if (filterWidth > 0) {
                button(g, x + w - filterWidth - 7, footer + 5, filterWidth, 18,
                        currentFilter().label(), () -> filterOpen = !filterOpen, mx, my, filterOpen);
                PcFilesIcon.DOWN.draw(g, x + w - 22, footer + 9, 10, MUTED);
            }
        }
        String detail = overwrite != null ? "Replace " + overwrite.name() + "?" : error;
        text(g, detail, x + 9, footer + 27, w - 20, overwrite != null || !error.isEmpty() ? 0xFFFFCBAE : MUTED);
        String action = newFolder ? "Create" : overwrite != null ? "Replace" : title();
        if (request.mode() == PcFileDialog.Mode.SAVE && overwrite == null && !newFolder) action = "Save";
        button(g, x + w - 145, footer + 41, 76, 18, action, this::submit, mx, my, true);
        button(g, x + w - 65, footer + 41, 57, 18, "Cancel", this::back, mx, my, false);
        if (filterOpen) {
            int fw = Math.min(145, w / 3), bottom = footer + 5;
            int count = Math.min(request.filters().size(), Math.max(1, (bottom - y - 52) / 17));
            int first = Math.clamp(filter - count + 1, 0, request.filters().size() - count);
            g.fill(x + w - fw - 7, bottom - count * 17, x + w - 7, bottom, PANEL);
            for (int i = first; i < first + count; i++) {
                int index = i;
                button(g, x + w - fw - 7, bottom - (first + count - i) * 17, fw, 17,
                        request.filters().get(i).label(), () -> setFilter(index, false), mx, my, i == filter);
            }
        }
    }

    private void drawField(GuiGraphics g, EditBox field, int x, int y, int width, int mx, int my, float partial) {
        g.fill(x, y, x + width, y + 18, FIELD);
        g.fill(x, y + 17, x + width, y + 18, field.isFocused() ? ACCENT : LINE);
        field.setPosition(x + 4, y + 5);
        field.setWidth(Math.max(1, width - 8));
        hits.add(new Hit(new Rectangle(x, y, width, 18), () -> { focus(field); field.mouseClicked(mx, my, 0); }));
        field.render(g, mx, my, partial);
    }

    private void text(GuiGraphics g, String value, int x, int y, int room, int color) {
        if (room > 0 && value != null && !value.isBlank()) {
            g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(PcDesktopScreen.label(value), room)),
                    x, y, color, false);
        }
    }

    private void button(GuiGraphics g, int x, int y, int width, int height, String label, Runnable action,
                        int mx, int my, boolean selected) {
        var box = new Rectangle(x, y, width, height);
        hits.add(new Hit(box, action));
        if (selected || box.contains(mx, my)) PcDesktopScreen.roundRect(g, x, y, width, height, SELECTED);
        text(g, label, x + 5, y + (height - 9) / 2, width - 8, TEXT);
    }

    private void focus(EditBox field) {
        address.setFocused(field == address);
        name.setFocused(field == name);
        find.setFocused(field == find);
    }

    private void choose(PcFileSystem.Entry entry, boolean toggle, boolean range, boolean pointer) {
        focus(null);
        overwrite = null;
        cursor = entries.indexOf(entry);
        if (request.multiple() && range && anchor >= 0 && !entry.folder()) {
            if (!toggle) selection.clear();
            for (int i = Math.min(cursor, anchor); i <= Math.max(cursor, anchor); i++) {
                if (!entries.get(i).folder()) selection.add(entries.get(i).id());
            }
        } else {
            anchor = cursor;
            if (!toggle || !request.multiple() || entry.folder()) selection.clear();
            if (toggle && request.multiple() && !entry.folder() && selection.contains(entry.id())) selection.remove(entry.id());
            else selection.add(entry.id());
        }
        syncName();
        long now = System.currentTimeMillis();
        boolean doubleClick = pointer && !toggle && !range && entry.id().equals(clicked) && now - clickedAt < 360;
        clicked = pointer ? entry.id() : null;
        clickedAt = now;
        if (doubleClick) {
            if (entry.folder()) move(entry.id(), true);
            else submit();
        }
    }

    private void syncName() {
        if (selection.size() > 1) name.setValue(selection.size() + " files selected");
        else if (selection.size() == 1) {
            entries.stream().filter(entry -> entry.id().equals(selection.getFirst()) && !entry.folder())
                    .findFirst().ifPresentOrElse(entry -> name.setValue(entry.name()), () -> {
                        if (request.mode() != PcFileDialog.Mode.SAVE) name.setValue("");
                    });
        } else if (request.mode() != PcFileDialog.Mode.SAVE) name.setValue("");
    }

    private void setFilter(int index, boolean keepMenu) {
        filter = Math.floorMod(index, request.filters().size());
        filterOpen = keepMenu;
        overwrite = null;
        scroll = 0;
        cursor = anchor = -1;
        selection.clear();
        if (request.mode() == PcFileDialog.Mode.OPEN) name.setValue("");
        refresh();
    }

    private void tryAddress() {
        try { move(drive.resolve(folder, address.getValue()).id(), true); }
        catch (IOException e) { error = e.getMessage(); }
    }

    private void back() {
        if (filterOpen) { filterOpen = false; return; }
        if (newFolder) { newFolder = false; name.setValue(request.suggestedName()); return; }
        if (overwrite != null) { overwrite = null; return; }
        cancel.run();
    }

    private void submit() {
        try {
            if (newFolder) {
                UUID id = drive.create(folder, name.getValue(), true, "");
                newFolder = false;
                name.setValue(request.suggestedName());
                move(id, true);
                return;
            }
            UUID first = selection.size() == 1 ? selection.getFirst() : null;
            var picked = first == null ? null : drive.get(first);
            if (request.mode() == PcFileDialog.Mode.FOLDER) {
                UUID id = picked == null ? folder : picked.id();
                if (!drive.get(id).folder()) throw new IOException("Choose a folder.");
                accepted.accept(new PcFileDialog.Selection(List.of(id), id, "", null));
            } else if (picked != null && picked.folder() && !name.isFocused()) {
                move(picked.id(), true);
            } else if (request.mode() == PcFileDialog.Mode.OPEN) {
                List<UUID> result = !selection.isEmpty() ? List.copyOf(selection) : List.of(drive.resolve(folder, name.getValue()).id());
                for (var id : result) {
                    var entry = drive.get(id);
                    if (entry.folder()) { move(entry.id(), true); return; }
                    if (entry.shortcut() || !matches(entry.name())) throw new IOException("Choose a file that matches the selected type.");
                }
                accepted.accept(new PcFileDialog.Selection(result, folder, "", null));
            } else {
                String value = name.getValue().strip().replace('/', '\\');
                if (value.isEmpty()) throw new IOException("Enter a file name.");
                int slash = value.lastIndexOf('\\');
                UUID destination = slash < 0 ? picked == null ? folder : picked.parent()
                        : drive.resolve(folder, value.substring(0, slash + 1)).id();
                value = value.substring(slash + 1);
                if (value.isBlank()) throw new IOException("Enter a file name.");
                if (PcFileType.extension(value).isEmpty() && !request.filters().isEmpty() && !currentFilter().extensions().isEmpty()) {
                    value = value.strip() + "." + currentFilter().extensions().getFirst();
                }
                String target = PcFileSystem.checkedName(value);
                if (!matches(target)) throw new IOException("Choose a name that matches the selected type.");
                var collision = drive.list(destination, "").stream().filter(item -> item.name().equalsIgnoreCase(target)).findFirst().orElse(null);
                if (collision != null && (collision.folder() || collision.shortcut())) {
                    throw new IOException("A folder or shortcut already uses this name.");
                }
                if (collision != null && overwrite != collision) { overwrite = collision; return; }
                accepted.accept(new PcFileDialog.Selection(List.of(), destination, target, collision == null ? null : collision.id()));
            }
        } catch (IOException e) { error = e.getMessage(); }
    }

    void click(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        for (var hit : hits.reversed()) if (hit.box.contains(x, y)) { hit.action.run(); return; }
        filterOpen = false;
    }

    void scroll(double delta) {
        if (filterOpen) setFilter(filter + (delta > 0 ? -1 : 1), true);
        else scroll = Math.clamp(scroll + (delta > 0 ? -3 : 3), 0, Math.max(0, entries.size() - capacity));
    }

    void key(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { back(); return; }
        if (filterOpen) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) setFilter(filter + (key == GLFW.GLFW_KEY_UP ? -1 : 1), true);
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) filterOpen = false;
            return;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) {
            focus(find);
            return;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean hasName = request.mode() != PcFileDialog.Mode.FOLDER || newFolder;
            focus(address.isFocused() ? find : find.isFocused() && hasName ? name
                    : find.isFocused() || name.isFocused() ? null : address);
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (address.isFocused()) tryAddress();
            else if (find.isFocused()) focus(null);
            else submit();
            return;
        }
        if (address.isFocused()) address.keyPressed(key, scan, modifiers);
        else if (find.isFocused()) find.keyPressed(key, scan, modifiers);
        else if (name.isFocused()) {
            selection.clear();
            name.keyPressed(key, scan, modifiers);
        } else if (key == GLFW.GLFW_KEY_A && request.multiple() && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            selection.clear();
            entries.stream().filter(entry -> !entry.folder() && !entry.shortcut()).forEach(entry -> selection.add(entry.id()));
            syncName();
        } else if (key == GLFW.GLFW_KEY_SPACE && request.multiple() && cursor >= 0 && cursor < entries.size()) {
            choose(entries.get(cursor), true, false, false);
        } else if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !entries.isEmpty()) {
            int next = Math.clamp(cursor + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, entries.size() - 1);
            choose(entries.get(next), false, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0, false);
            if (next < scroll) scroll = next;
            if (next >= scroll + capacity) scroll = next - capacity + 1;
        }
    }

    void typed(char value, int modifiers) {
        if (address.isFocused()) address.charTyped(value, modifiers);
        else if (find.isFocused()) find.charTyped(value, modifiers);
        else if (name.isFocused()) {
            selection.clear();
            name.charTyped(value, modifiers);
        }
    }
}
