package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.austizz.ultimatebankingsystem.api.pc.PcAppServices;
import net.austizz.ultimatebankingsystem.api.pc.PcFileDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;

final class PcNotesContent implements PcAppContent {
    private static final int CANVAS = 0xFF252932, TABS = 0xFF161D2A, BAND = 0xFF202B3C;
    private static final int CARD = 0xFF344358, MENU = 0xFF283345, RULE = 0xFF4B6079;
    private static final int TEXT = 0xFFF0F3F8, MUTED = 0xFFADBED2, ACCENT = 0xFF8BD5EC;
    private static final int TAB = 22, BAR = 23, FOOT = 17, ROW = 19, MAX_TABS = 64;
    private enum View { FORMATTED, MARKDOWN, PLAIN }

    private static final class Document {
        final PcNotesEditor editor;
        final String eol;
        final boolean markdown;
        UUID id;
        UUID parent;
        String name;
        String saved;
        View view;

        Document(PcFileSystem.Entry entry, boolean markdown) {
            this.markdown = markdown;
            id = entry == null ? null : entry.id();
            parent = entry == null ? PcFileSystem.DOCUMENTS : entry.parent();
            name = entry == null ? "Untitled" : entry.name();
            String original = entry == null ? "" : entry.text();
            eol = original.contains("\r\n") || entry == null && !markdown ? "\r\n"
                    : original.indexOf('\r') >= 0 ? "\r" : "\n";
            saved = PcNotesEditor.clean(original);
            view = entry == null || markdown ? View.FORMATTED : View.PLAIN;
            editor = new PcNotesEditor(saved, view == View.FORMATTED);
        }

        boolean dirty() { return !saved.equals(editor.value()); }
        String diskText() { return editor.value().replace("\n", eol); }
    }

    private record Action(String title, String key, Runnable run) {}
    private record Hit(int x, int y, int w, int h, Runnable run) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Popup(int x, int width, List<Action> items, boolean table) {}
    private record Form(String title, List<EditBox> fields, List<String> labels, List<Action> buttons) {}
    @FunctionalInterface private interface Submit { void apply(List<String> values) throws IOException; }

    private final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
    private final PcFileSystem drive;
    private PcAppServices services;
    private final List<Document> tabs = new ArrayList<>();
    private final LinkedHashSet<UUID> recent = new LinkedHashSet<>();
    private final List<Hit> toolbar = new ArrayList<>();
    private final List<Hit> floating = new ArrayList<>();
    private int active, firstTab, x, y, width, height, choice, zoom = 100;
    private boolean focused, selecting, wrap = true, statusBar = true;
    private String status = "", search = "";
    private Popup popup;
    private Form form;

    PcNotesContent(PcFileSystem drive) {
        this.drive = drive;
        tabs.add(new Document(null, false));
    }

    private Document document() { return tabs.get(active); }
    private PcNotesEditor editor() {
        var editor = document().editor;
        if (services != null) editor.clipboard(services.clipboard());
        return editor;
    }

    void open(PcFileSystem.Entry entry) throws IOException {
        if (entry.folder() || entry.binary() || entry.shortcut()) throw new IOException("Choose a text file.");
        for (int i = 0; i < tabs.size(); i++) {
            var tab = tabs.get(i);
            if (entry.id().equals(tab.id)) {
                tab.name = entry.name();
                tab.parent = entry.parent();
                switchTab(i);
                return;
            }
        }
        boolean markdown = List.of("md", "markdown", "mdown", "mkd").contains(PcFileType.extension(entry.name()));
        var opened = new Document(entry, markdown);
        if (tabs.size() == 1 && document().id == null && !document().dirty()) tabs.set(0, opened);
        else {
            if (tabs.size() >= MAX_TABS) throw new IOException("Close a tab before opening another.");
            tabs.add(opened);
        }
        recent.remove(entry.id());
        recent.add(entry.id());
        switchTab(tabs.indexOf(opened));
    }

    private void switchTab(int index) {
        active = index;
        status = "";
        popup = null;
        selecting = false;
        revealTab();
        layoutEditor();
    }

    private void newTab(boolean markdown) {
        if (tabs.size() >= MAX_TABS) { status = "Close a tab first."; return; }
        tabs.add(new Document(null, markdown));
        switchTab(tabs.size() - 1);
    }

    private void remove(Document tab) {
        int index = tabs.indexOf(tab);
        tabs.remove(tab);
        if (tabs.isEmpty()) tabs.add(new Document(null, false));
        switchTab(Math.min(index, tabs.size() - 1));
    }

    private void closeTab(int index) {
        switchTab(index);
        var tab = document();
        if (tab.dirty()) confirm(tab, () -> remove(tab), () -> remove(tab));
        else remove(tab);
    }

    void requestClose(Runnable close) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).dirty()) {
                switchTab(i);
                var tab = document();
                confirm(tab, () -> requestClose(close), () -> { remove(tab); requestClose(close); });
                return;
            }
        }
        close.run();
    }

    private void confirm(Document tab, Runnable afterSave, Runnable discard) {
        form = new Form("Save changes to " + tab.name + "?", List.of(), List.of(),
                List.of(action("Save", () -> { form = null; save(false, afterSave); }),
                        action("Discard", () -> { form = null; discard.run(); }),
                        action("Cancel", () -> form = null)));
    }

    private void save(boolean copy, Runnable afterSave) {
        var tab = document();
        if (copy || tab.id == null) {
            String extension = tab.markdown ? "md" : "txt";
            String name = tab.id == null ? "Untitled." + extension : tab.name;
            String existing = PcFileType.extension(name);
            var filters = existing.isEmpty() ? List.<PcFileDialog.Filter>of()
                    : List.of(new PcFileDialog.Filter(tab.markdown ? "Markdown" : "Text files", List.of(existing)),
                            new PcFileDialog.Filter("All files", List.of()));
            services.pick(new PcFileDialog.Request(PcFileDialog.Mode.SAVE, tab.parent, name, filters, false), selected -> {
                UUID id = selected.replace();
                if (id == null) id = drive.create(selected.folder(), selected.name(), false, tab.diskText());
                else drive.write(id, tab.diskText());
                tab.id = id;
                markSaved(tab, drive.get(id), afterSave);
            });
        } else {
            try {
                if (tab.dirty()) drive.write(tab.id, tab.diskText());
                markSaved(tab, drive.get(tab.id), afterSave);
            } catch (IOException e) { status = e.getMessage(); }
        }
    }

    private void markSaved(Document tab, PcFileSystem.Entry entry, Runnable done) {
        tab.parent = entry.parent();
        tab.name = entry.name();
        tab.saved = tab.editor.value();
        recent.remove(tab.id);
        recent.add(tab.id);
        status = "Saved";
        done.run();
    }

    private void saveAll() {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).dirty()) {
                switchTab(i);
                save(false, this::saveAll);
                return;
            }
        }
    }

    @Override public void setServices(PcAppServices services) { this.services = services; }

    private void openPath() {
        var filters = List.of(new PcFileDialog.Filter("Text and Markdown", List.of("txt", "md", "markdown", "log", "ini", "json")),
                new PcFileDialog.Filter("All files", List.of()));
        services.pick(new PcFileDialog.Request(PcFileDialog.Mode.OPEN, document().parent, "", filters, true), result -> {
            var documents = new ArrayList<PcFileSystem.Entry>();
            for (var id : result.items()) {
                var entry = drive.get(id);
                if (entry.folder() || entry.binary() || entry.shortcut()) throw new IOException("Choose a text file.");
                documents.add(entry);
            }
            long extra = documents.stream().filter(entry -> tabs.stream().noneMatch(tab -> entry.id().equals(tab.id))).count();
            int blank = tabs.size() == 1 && document().id == null && !document().dirty() ? 1 : 0;
            if (tabs.size() + extra - blank > MAX_TABS) throw new IOException("Close a tab before opening more files.");
            for (var entry : documents) open(entry);
        });
    }

    private void setView(View view) {
        document().view = view;
        editor().setFormatted(view == View.FORMATTED);
        layoutEditor();
    }

    private void toggleView() { setView(document().view == View.FORMATTED ? View.MARKDOWN : View.FORMATTED); }

    private void zoom(int step) {
        zoom = step == 0 ? 100 : Math.clamp(zoom + step, 60, 200);
        layoutEditor();
    }

    private int bodyTop() { return y + TAB + BAR; }
    private int bodyBottom() { return y + height - (statusBar ? FOOT : 1); }
    private int tabCapacity() { return Math.max(1, (width - 58) / 107); }

    private void revealTab() {
        if (active < firstTab) firstTab = active;
        if (active >= firstTab + tabCapacity()) firstTab = active - tabCapacity() + 1;
        firstTab = Math.clamp(firstTab, 0, Math.max(0, tabs.size() - tabCapacity()));
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        revealTab();
        layoutEditor();
    }

    private void layoutEditor() {
        editor().layout(x + 9, bodyTop() + 9, width - 18, bodyBottom() - bodyTop() - 14, zoom, wrap);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        toolbar.clear();
        floating.clear();
        g.fill(x, y, x + width, y + height, CANVAS);
        g.fill(x, y, x + width, y + TAB, TABS);
        g.fill(x, y + TAB, x + width, bodyTop(), BAND);
        drawTabs(g, mx, my);
        drawToolbar(g, mx, my);
        editor().render(g, focused && popup == null && form == null);
        if (statusBar) drawStatus(g, mx, my);
        if (popup != null) drawPopup(g, mx, my);
        if (form != null) drawForm(g, mx, my, partialTick);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        int left = x + 5;
        boolean overflow = tabs.size() > tabCapacity();
        if (overflow) {
            button(g, toolbar, left, y + 3, 15, 17, "‹", null, mx, my, () -> firstTab = Math.max(0, firstTab - 1), false);
            left += 15;
        }
        for (int i = firstTab; i < Math.min(firstTab + tabCapacity(), tabs.size()); i++) {
            int index = i;
            var tab = tabs.get(i);
            var hit = new Hit(left, y + 3, 105, 19, () -> switchTab(index));
            toolbar.add(hit);
            if (i == active || hover(hit, toolbar, mx, my)) PcDesktopScreen.roundRect(g, left, y + 3, 105, 19, CARD);
            text(g, tab.name, left + 6, y + 8, 77, i == active ? TEXT : MUTED);
            if (tab.dirty()) g.fill(left + 85, y + 10, left + 88, y + 13, ACCENT);
            button(g, toolbar, left + 90, y + 4, 14, 15, "", PcFilesIcon.CLOSE, mx, my, () -> closeTab(index), false);
            left += 107;
        }
        if (overflow) {
            button(g, toolbar, left, y + 3, 15, 17, "›", null, mx, my,
                    () -> firstTab = Math.min(tabs.size() - tabCapacity(), firstTab + 1), false);
            left += 15;
        }
        button(g, toolbar, left, y + 2, 19, 18, "", PcFilesIcon.ADD, mx, my,
                () -> newTab(false), false);
    }

    private void drawToolbar(GuiGraphics g, int mx, int my) {
        int top = y + TAB + 2;
        button(g, toolbar, x + 3, top, 32, 19, "File", null, mx, my, this::fileMenu, false);
        button(g, toolbar, x + 35, top, 32, 19, "Edit", null, mx, my, this::editMenu, false);
        button(g, toolbar, x + 67, top, 37, 19, "View", null, mx, my, this::viewMenu, false);
        int left = Math.max(x + 110, x + (width - 187) / 2);
        if (document().view == View.FORMATTED) {
            if (width < 300) {
                button(g, toolbar, left, top, 64, 19, "Format", null, mx, my, () -> formatMenu(left), false);
                PcFilesIcon.DOWN.draw(g, left + 50, top + 6, 8, TEXT);
                return;
            }
            int heading = editor().heading();
            button(g, toolbar, left, top, 33, 19, heading == 0 ? "H" : "H" + heading, null,
                    mx, my, () -> headingMenu(left), false);
            PcFilesIcon.DOWN.draw(g, left + 22, top + 6, 8, TEXT);
            button(g, toolbar, left + 35, top, 23, 19, "", PcFilesIcon.VIEW, mx, my, () -> listMenu(left + 35), false);
            styleButton(g, left + 58, top, "B", "**", PcNotesMarkdown.BOLD, mx, my);
            styleButton(g, left + 78, top, "I", "_", PcNotesMarkdown.ITALIC, mx, my);
            styleButton(g, left + 98, top, "S", "~~", PcNotesMarkdown.STRIKE, mx, my);
            button(g, toolbar, left + 120, top, 21, 19, "", PcFilesIcon.LINK, mx, my, this::linkDialog, false);
            button(g, toolbar, left + 142, top, 23, 19, "", PcFilesIcon.TABLE, mx, my, () -> tableMenu(left + 142), false);
            button(g, toolbar, left + 166, top, 21, 19, "", PcFilesIcon.CLEAR_FORMAT, mx, my, this::clearFormatting, false);
        } else {
            button(g, toolbar, left, top, 75, 19, "Formatting", null, mx, my, () -> setView(View.FORMATTED), false);
        }
    }

    private void styleButton(GuiGraphics g, int left, int top, String label, String marks, int style, int mx, int my) {
        boolean active = (editor().stylesAtCaret() & style) != 0;
        button(g, toolbar, left, top, 20, 19, "", null, mx, my, () -> editor().toggle(marks, style), active);
        var glyph = PcDesktopScreen.label(label).copy().withStyle(s -> s.withBold(style == PcNotesMarkdown.BOLD)
                .withItalic(style == PcNotesMarkdown.ITALIC).withStrikethrough(style == PcNotesMarkdown.STRIKE));
        g.drawString(font, glyph, left + (20 - font.width(glyph)) / 2, top + 5, active ? ACCENT : TEXT, false);
    }

    private void drawStatus(GuiGraphics g, int mx, int my) {
        int top = bodyBottom(), right = x + width;
        g.fill(x, top, right, y + height, TABS);
        String value = editor().value();
        int caret = editor().caret(), line = 1;
        for (int i = 0; i < caret; i++) if (value.charAt(i) == '\n') line++;
        int col = caret - value.lastIndexOf('\n', caret - 1);
        text(g, "Ln " + line + ", Col " + col, x + 6, top + 4, 67, MUTED);
        text(g, value.codePointCount(0, value.length()) + " chars", x + 76, top + 4, 50, MUTED);
        String mode = switch (document().view) {
            case FORMATTED -> "Formatted";
            case MARKDOWN -> "Markdown syntax";
            case PLAIN -> "Plain text";
        };
        button(g, toolbar, x + 128, top + 1, Math.min(font.width(PcDesktopScreen.label(mode)) + 12,
                        Math.max(38, width - 237)), FOOT - 2, mode, null,
                mx, my, this::toggleView, false);
        text(g, zoom + "%", right - 106, top + 4, 34, MUTED);
        text(g, document().eol.equals("\r\n") ? "CRLF" : document().eol.equals("\r") ? "CR" : "LF",
                right - 68, top + 4, 30, MUTED);
        text(g, "UTF-8", right - 36, top + 4, 35, MUTED);
        if (status != null && !status.isEmpty() && form == null) {
            g.fill(x + 6, top - 16, x + width - 6, top - 1, MENU);
            text(g, status, x + 10, top - 13, width - 20, TEXT);
        }
    }

    private void text(GuiGraphics g, String value, int left, int top, int room, int color) {
        if (value == null || room <= 0) return;
        var clipped = font.substrByWidth(PcDesktopScreen.label(value), room);
        g.drawString(font, Language.getInstance().getVisualOrder(clipped), left, top, color, false);
    }

    private boolean hover(Hit hit, List<Hit> buttons, int mx, int my) {
        return hit.contains(mx, my) && (buttons == floating || popup == null && form == null);
    }

    private void button(GuiGraphics g, List<Hit> buttons, int x, int y, int w, int h, String label,
                        PcFilesIcon icon, int mx, int my, Runnable function, boolean selected) {
        var hit = new Hit(x, y, w, h, function);
        buttons.add(hit);
        if (selected || hover(hit, buttons, mx, my)) PcDesktopScreen.roundRect(g, x, y, w, h, CARD);
        if (icon == null) text(g, label, x + 6, y + (h - 9) / 2, w - 7, selected ? ACCENT : TEXT);
        else {
            int size = Math.min(12, w - 1);
            icon.draw(g, x + (w - size) / 2, y + (h - size) / 2, size, selected ? ACCENT : TEXT);
        }
    }


    private static Action action(String name, Runnable run) { return new Action(name, "", run); }
    private static Action action(String name, String key, Runnable run) { return new Action(name, key, run); }

    private void menu(int left, int w, Action... commands) { menu(left, w, List.of(commands), false); }

    private void menu(int left, int w, List<Action> commands, boolean table) {
        popup = new Popup(Math.clamp(left, x + 2, Math.max(x + 2, x + width - w - 2)), w, commands, table);
        choice = -1;
    }

    private void fileMenu() {
        menu(x + 3, 170, action("New tab", "Ctrl+N", () -> newTab(false)),
                action("New Markdown tab", () -> newTab(true)), action("Open…", "Ctrl+O", this::openPath),
                action("Recent files ›", this::recentMenu), action("Save", "Ctrl+S", () -> save(false, () -> {})),
                action("Save as…", () -> save(true, () -> {})), action("Save all", "Ctrl+Alt+S", this::saveAll),
                action("Close tab", "Ctrl+W", () -> closeTab(active)));
    }

    private void recentMenu() {
        var items = new ArrayList<Action>();
        for (var id : recent.reversed()) {
            try {
                items.add(action(drive.get(id).name(), () -> {
                    try { open(drive.get(id)); } catch (IOException e) { status = e.getMessage(); }
                }));
            } catch (IOException ignored) { }
            if (items.size() >= 7) break;
        }
        if (items.isEmpty()) items.add(action("No recent files", null));
        menu(x + 3, 170, items, false);
    }

    private void editMenu() {
        menu(x + 35, 166, action("Undo", "Ctrl+Z", () -> editor().undo(false)),
                action("Redo", "Ctrl+Y", () -> editor().undo(true)), action("Cut", "Ctrl+X", () -> editKey(GLFW.GLFW_KEY_X)),
                action("Copy", "Ctrl+C", () -> editKey(GLFW.GLFW_KEY_C)), action("Paste", "Ctrl+V", () -> editKey(GLFW.GLFW_KEY_V)),
                action("Clear formatting", this::clearFormatting), action("Find…", "Ctrl+F", this::findDialog),
                action("Replace all…", "Ctrl+H", this::replaceDialog), action("Go to line…", "Ctrl+G", this::goToDialog),
                action("Select all", "Ctrl+A", () -> editKey(GLFW.GLFW_KEY_A)), action("Time and date", "F5", this::insertTime));
    }

    private void viewMenu() {
        menu(x + 67, 156, action("Zoom ›", () -> menu(x + 67, 156, action("Zoom in", () -> zoom(10)),
                        action("Zoom out", () -> zoom(-10)), action("Reset zoom", () -> zoom(0)))),
                action((statusBar ? "✓  " : "") + "Status bar", () -> { statusBar = !statusBar; layoutEditor(); }),
                action((wrap ? "✓  " : "") + "Word wrap", () -> { wrap = !wrap; layoutEditor(); }),
                action("Markdown ›", () -> menu(x + 67, 156, action("Formatted", () -> setView(View.FORMATTED)),
                        action("Markdown syntax", () -> setView(View.MARKDOWN)), action("Plain text", () -> setView(View.PLAIN)))));
    }

    private void headingMenu(int left) {
        var items = new ArrayList<Action>();
        for (int i = 1; i <= 6; i++) {
            int level = i;
            items.add(action("Heading " + i, () -> heading(level)));
        }
        items.add(action("Body text", () -> heading(0)));
        menu(left, 115, items, false);
    }

    private void formatMenu(int left) {
        menu(left, 140, action("Headings ›", () -> headingMenu(left)), action("Lists ›", () -> listMenu(left)),
                action("Bold", () -> editor().toggle("**", PcNotesMarkdown.BOLD)),
                action("Italic", () -> editor().toggle("_", PcNotesMarkdown.ITALIC)),
                action("Strikethrough", () -> editor().toggle("~~", PcNotesMarkdown.STRIKE)),
                action("Insert link…", this::linkDialog), action("Table ›", () -> tableMenu(left)),
                action("Clear formatting", this::clearFormatting));
    }

    private void heading(int level) {
        changeLines((line, ignored) -> {
            String body = PcNotesMarkdown.HEADING_PREFIX.matcher(line).replaceFirst("$1").stripLeading();
            return level == 0 ? body : "#".repeat(level) + " " + body;
        });
    }

    private void listMenu(int left) {
        menu(left, 145, action("Bulleted list", () -> list(false)), action("Numbered list", () -> list(true)),
                action("Increase indent", () -> changeLines((line, ignored) -> "    " + line)),
                action("Decrease indent", () -> changeLines((line, ignored) -> line.replaceFirst("^(?: {1,4}|\\t)", ""))));
    }

    private void list(boolean ordered) {
        changeLines((line, index) -> {
            String body = PcNotesMarkdown.LIST_PREFIX.matcher(line).replaceFirst("$1");
            int leading = body.length() - body.stripLeading().length();
            return body.substring(0, leading) + (ordered ? (index + 1) + ". " : "- ") + body.substring(leading);
        });
    }

    private void changeLines(BiFunction<String, Integer, String> transform) {
        var editor = editor();
        String source = editor.value();
        int from = source.lastIndexOf('\n', editor.start() - 1) + 1;
        int end = editor.end();
        if (editor.selected() && end > 0 && source.charAt(end - 1) == '\n') end--;
        int nextLine = source.indexOf('\n', end);
        int to = nextLine < 0 ? source.length() : nextLine;
        var lines = source.substring(from, to).split("\n", -1);
        for (int i = 0; i < lines.length; i++) lines[i] = transform.apply(lines[i], i);
        String changed = String.join("\n", lines);
        int caret = from + changed.length();
        editor.replace(from, to, changed, editor.selected() ? from : caret, caret);
    }

    private void clearFormatting() {
        var editor = editor();
        if (!editor.selected()) {
            String plain = PcNotesMarkdown.plain(editor.value());
            int cursor = Math.min(editor.caret(), plain.length());
            editor.replace(0, editor.value().length(), plain, cursor, cursor);
            setView(View.PLAIN);
        } else {
            int from = editor.start(), to = editor.end();
            String source = editor.value();
            for (String marks : List.of("**", "~~", "_", "*")) {
                int length = marks.length();
                if (from >= length && source.startsWith(marks, from - length) && source.startsWith(marks, to)) {
                    from -= length;
                    to += length;
                }
            }
            String plain = PcNotesMarkdown.plain(source.substring(from, to));
            editor.replace(from, to, plain, from, from + plain.length());
        }
    }

    private void linkDialog() {
        dialog("Insert link", List.of("Display text", "Address"),
                List.of(editor().selectedText().replace('\n', ' '), ""), "Insert", values -> {
                    var uri = URI.create(values.get(1).strip());
                    if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())
                            || uri.getHost() == null) throw new IOException("Enter an HTTP or HTTPS address.");
                    String address = uri.toASCIIString();
                    String label = values.getFirst().isBlank() ? address : values.getFirst();
                    label = label.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
                    editor().insert("[" + label + "](<" + address + ">)");
                });
    }

    private void tableMenu(int left) {
        menu(left, 120, List.of(action("Insert 3 × 3 table", () -> insertTable(3, 3)),
                action("Edit table ›", () -> tableEditMenu(left))), true);
    }

    private void tableEditMenu(int left) {
        menu(left, 143, action("Add row below", () -> editTable(1, 0)),
                action("Remove body row", () -> editTable(-1, 0)), action("Add column", () -> editTable(0, 1)),
                action("Remove column", () -> editTable(0, -1)));
    }

    private void editTable(int rows, int columns) {
        var editor = editor();
        String changed = PcNotesMarkdown.editTable(editor.value(), editor.caret(), rows, columns);
        if (changed.equals(editor.value())) { status = "Place the caret in a table to edit its rows or columns."; return; }
        int caret = Math.min(editor.caret(), changed.length());
        editor.replace(0, editor.value().length(), changed, caret, caret);
    }

    private void insertTable(int columns, int rows) {
        var editor = editor();
        int start = editor.start(), end = editor.end();
        String before = start == 0 ? "" : "\n\n";
        String after = end == editor.value().length() ? "\n" : "\n\n";
        int caret = start + before.length() + 2;
        editor.replace(start, end, before + PcNotesMarkdown.table(columns, rows) + after, caret, caret);
    }

    private void drawPopup(GuiGraphics g, int mx, int my) {
        int top = bodyTop() + 1;
        int available = y + height - top;
        int cell = popup.table ? Math.clamp((available - 62) / 5, 6, 15) : 0;
        int grid = popup.table ? 18 + cell * 5 : 0;
        int visible = Math.max(1, (available - grid - 6) / ROW);
        int height = Math.min(visible, popup.items.size()) * ROW + grid + 6;
        panel(g, popup.x, top, popup.width, height);
        if (popup.table) tableGrid(g, popup.x + 9, top + 17, cell, mx, my);
        int first = Math.max(0, choice - visible + 1);
        for (int i = first; i < Math.min(first + visible, popup.items.size()); i++) {
            var item = popup.items.get(i);
            int row = top + grid + 3 + (i - first) * ROW;
            var hit = new Hit(popup.x + 3, row, popup.width - 6, ROW, item.run);
            if (item.run != null) floating.add(hit);
            if (i == choice || item.run != null && hit.contains(mx, my)) PcDesktopScreen.roundRect(g, hit.x, hit.y, hit.w, hit.h, CARD);
            int keyWidth = font.width(PcDesktopScreen.label(item.key));
            text(g, item.title, hit.x + 7, row + 5, hit.w - keyWidth - 14, item.run == null ? MUTED : TEXT);
            text(g, item.key, hit.x + hit.w - keyWidth - 7, row + 5, keyWidth, MUTED);
        }
    }

    private void tableGrid(GuiGraphics g, int left, int top, int size, int mx, int my) {
        int col = mx >= left && mx < left + 5 * size && my >= top && my < top + 5 * size ? (mx - left) / size : -1;
        int row = col < 0 ? -1 : (my - top) / size;
        text(g, col < 0 ? "Choose table size" : (col + 1) + " × " + (row + 1), left, top - 12, 104, MUTED);
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            int columns = c + 1, rows = r + 1;
            int cx = left + c * size, cy = top + r * size;
            boolean hot = c <= col && r <= row;
            if (hot) g.fill(cx, cy, cx + size - 2, cy + size - 2, CARD);
            g.renderOutline(cx, cy, size - 2, size - 2, hot ? ACCENT : RULE);
            floating.add(new Hit(cx, cy, size, size, () -> insertTable(columns, rows)));
        }
    }

    private void panel(GuiGraphics g, int left, int top, int w, int h) {
        PcDesktopScreen.roundRect(g, left, top, w, h, RULE);
        PcDesktopScreen.roundRect(g, left + 1, top + 1, w - 2, h - 2, MENU);
    }

    private void dialog(String title, List<String> labels, List<String> values, String submit, Submit apply) {
        var fields = new ArrayList<EditBox>();
        for (int i = 0; i < labels.size(); i++) {
            var input = new EditBox(font, 0, 0, 80, 17, Component.literal(labels.get(i)));
            input.setMaxLength(512);
            input.setBordered(false);
            input.setFormatter((value, offset) -> PcDesktopScreen.label(value).getVisualOrderText());
            input.setValue(values.get(i));
            fields.add(input);
        }
        fields.getFirst().setFocused(true);
        fields.getFirst().setHighlightPos(0);
        status = "";
        form = new Form(title, fields, labels, List.of(action(submit, () -> {
            var current = form;
            form = null;
            try {
                apply.apply(fields.stream().map(EditBox::getValue).toList());
            } catch (IOException | IllegalArgumentException e) {
                form = current;
                status = e instanceof IllegalArgumentException ? "Check the entered value." : e.getMessage();
            }
        }), action("Cancel", this::dismiss)));
    }

    private void dismiss() { form = null; status = ""; }

    private void drawForm(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(x, y, x + width, y + height, 0x870B1019);
        int w = Math.min(width - 18, 242), h = 57 + form.fields.size() * 31;
        int left = x + (width - w) / 2, top = y + Math.max(2, (height - h) / 2);
        panel(g, left, top, w, h);
        text(g, form.title, left + 12, top + 10, w - 24, TEXT);
        for (int i = 0; i < form.fields.size(); i++) {
            int row = top + 23 + i * 31;
            var field = form.fields.get(i);
            field.setPosition(left + 17, row + 14);
            field.setWidth(w - 34);
            text(g, form.labels.get(i), left + 12, row, w - 24, MUTED);
            PcDesktopScreen.roundRect(g, left + 12, row + 10, w - 24, 18, BAND);
            g.fill(left + 15, row + 26, left + w - 15, row + 27, field.isFocused() ? ACCENT : RULE);
            field.render(g, mx, my, partialTick);
        }
        if (status != null && !status.isEmpty()) text(g, status, left + 12, top + h - 32, w - 24, ACCENT);
        int step = (w - 20) / form.buttons.size();
        for (int i = 0; i < form.buttons.size(); i++) {
            var action = form.buttons.get(i);
            button(g, floating, left + 10 + i * step, top + h - 21, step - 4, 16,
                    action.title, null, mx, my, action.run, i == 0);
        }
    }

    private void findDialog() {
        String selected = editor().selectedText().replace('\n', ' ');
        dialog("Find", List.of("Find what"), List.of(selected.isBlank() ? search : selected), "Find next",
                values -> { search = values.getFirst(); seek(false); });
    }

    private void seek(boolean backwards) {
        if (search.isEmpty()) return;
        var editor = editor();
        String value = editor.value();
        int at = backwards ? value.lastIndexOf(search, editor.start() - 1) : value.indexOf(search, editor.end());
        if (at < 0) at = backwards ? value.lastIndexOf(search) : value.indexOf(search);
        if (at < 0) { status = "No matches."; return; }
        if (document().view == View.FORMATTED) setView(View.MARKDOWN);
        editor.select(at, at + search.length());
        status = "";
    }

    private void replaceDialog() {
        dialog("Replace all", List.of("Find what", "Replace with"), List.of(search, ""), "Replace all", values -> {
            String find = values.getFirst();
            if (find.isEmpty()) throw new IOException("Enter text to find.");
            String original = editor().value();
            String next = original.replace(find, values.get(1));
            if (next.length() > PcFileSystem.TEXT_LIMIT) throw new IOException("The result exceeds the document limit.");
            editor().replace(0, original.length(), next, 0, 0);
            status = next.equals(original) ? "No changes." : "Replaced all matches.";
        });
    }

    private void goToDialog() {
        dialog("Go to line", List.of("Line number"), List.of(""), "Go", values -> {
            int target = Integer.parseInt(values.getFirst());
            int at = 0;
            String value = editor().value();
            for (int line = 1; line < target && at >= 0; line++) {
                int next = value.indexOf('\n', at);
                at = next < 0 ? -1 : next + 1;
            }
            if (target < 1 || at < 0) throw new IOException("Line number is outside the document.");
            editor().select(at, at);
        });
    }

    private void insertTime() {
        editor().insert(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm  yyyy-MM-dd", Locale.ROOT)));
    }

    private void editKey(int key) { editor().key(key, GLFW.GLFW_MOD_CONTROL); }

    @Override
    public void renderPreview(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, CANVAS);
        String draft = editor().value();
        g.enableScissor(x, y, x + w, y + h);
        g.drawWordWrap(font, PcDesktopScreen.label(draft.substring(0, Math.min(384, draft.length()))),
                x + 7, y + 8, Math.max(1, w - 14), TEXT);
        g.disableScissor();
    }

    @Override
    public void focusChanged(boolean focus) {
        focused = focus;
        if (!focus) { popup = null; selecting = false; }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (form != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                for (var field : form.fields) field.setFocused(field.isMouseOver(mx, my));
                for (var field : form.fields) if (field.isFocused() && field.mouseClicked(mx, my, button)) return true;
                for (var hit : floating) if (hit.contains(mx, my)) { hit.run.run(); return true; }
            }
            return true;
        }
        if (popup != null) {
            popup = null;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) for (var hit : floating) {
                if (hit.contains(mx, my)) { hit.run.run(); return true; }
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && my >= bodyTop() && my < bodyBottom()) {
            if (!editor().selected()) editor().movePointer(mx, my, false);
            if (editor().inTable()) tableEditMenu((int) mx);
            else editMenu();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (int i = toolbar.size() - 1; i >= 0; i--) {
            var hit = toolbar.get(i);
            if (hit.contains(mx, my)) { hit.run.run(); return true; }
        }
        if (my >= bodyTop() && my < bodyBottom()) {
            status = "";
            editor().movePointer(mx, my, net.minecraft.client.gui.screens.Screen.hasShiftDown());
            selecting = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!selecting || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        editor().movePointer(x, y, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean wasSelecting = selecting;
        selecting = false;
        return wasSelecting;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int direction = (int) Math.signum(dy);
        if (form != null || direction == 0) return true;
        if (popup != null) choice = Math.clamp(Math.max(0, choice) - direction, 0, popup.items.size() - 1);
        else if (net.minecraft.client.gui.screens.Screen.hasControlDown()) zoom(direction * 10);
        else editor().scroll(dy);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (form != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) dismiss();
            else if (enter) form.buttons.getFirst().run.run();
            else if (key == GLFW.GLFW_KEY_TAB && !form.fields.isEmpty()) {
                int current = shift ? 0 : -1;
                for (int i = 0; i < form.fields.size(); i++) if (form.fields.get(i).isFocused()) current = i;
                form.fields.forEach(field -> field.setFocused(false));
                form.fields.get(Math.floorMod(current + (shift ? -1 : 1), form.fields.size())).setFocused(true);
            } else for (var field : form.fields) if (field.isFocused()) field.keyPressed(key, scan, mods);
            return true;
        }
        if (popup != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) popup = null;
            else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int step = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                choice = choice < 0 ? (step > 0 ? 0 : popup.items.size() - 1) : Math.floorMod(choice + step, popup.items.size());
            } else if (enter && choice >= 0) {
                var action = popup.items.get(choice);
                popup = null;
                if (action.run != null) action.run.run();
            }
            return true;
        }
        if (alt && !ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_F -> { fileMenu(); return true; }
                case GLFW.GLFW_KEY_E -> { editMenu(); return true; }
                case GLFW.GLFW_KEY_V -> { viewMenu(); return true; }
            }
        }
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_N -> { newTab(shift); return true; }
                case GLFW.GLFW_KEY_T -> { newTab(false); return true; }
                case GLFW.GLFW_KEY_TAB -> { switchTab(Math.floorMod(active + (shift ? -1 : 1), tabs.size())); return true; }
                case GLFW.GLFW_KEY_W -> { closeTab(active); return true; }
                case GLFW.GLFW_KEY_O -> { openPath(); return true; }
                case GLFW.GLFW_KEY_S -> { if (alt) saveAll(); else save(false, () -> {}); return true; }
                case GLFW.GLFW_KEY_F -> { findDialog(); return true; }
                case GLFW.GLFW_KEY_H -> { replaceDialog(); return true; }
                case GLFW.GLFW_KEY_G -> { goToDialog(); return true; }
                case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> { zoom(10); return true; }
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> { zoom(-10); return true; }
                case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> { zoom(0); return true; }
                case GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_K -> {
                    if (document().view == View.FORMATTED) {
                        if (key == GLFW.GLFW_KEY_K) linkDialog();
                        else if (key == GLFW.GLFW_KEY_B) editor().toggle("**", PcNotesMarkdown.BOLD);
                        else editor().toggle("_", PcNotesMarkdown.ITALIC);
                        return true;
                    }
                }
            }
        }
        if (key == GLFW.GLFW_KEY_F3) { seek(shift); return true; }
        if (key == GLFW.GLFW_KEY_F5) { insertTime(); return true; }
        return editor().key(key, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (popup != null) return true;
        if (form != null) {
            for (var field : form.fields) if (field.isFocused()) field.charTyped(ch, mods);
            return true;
        }
        if (Character.isISOControl(ch) || (mods & GLFW.GLFW_MOD_CONTROL) != 0 && (mods & GLFW.GLFW_MOD_ALT) == 0) return false;
        editor().insert(String.valueOf(ch));
        status = "";
        return true;
    }
}
