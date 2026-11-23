package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.Minecraft;
import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class PcNotesEditor {
    private static final int LINE = 13;
    private static final int TEXT = 0xFFF0F3F8;
    private static final int ACCENT = 0xFF8BD5EC;
    private static final int MAX_UNDO = 60;
    private static final Style FONT_STYLE = PcDesktopScreen.label("").getStyle();
    private final Font font = Minecraft.getInstance().font;
    private record Snapshot(String text, int anchor, int caret) {}
    private record Span(int beginIndex, int endIndex) {}

    private final class Field {
        private final List<Span> lines = new ArrayList<>();
        private final int[] gaps;
        private int anchor;
        private int cursor;

        Field() {
            String text = display.text();
            font.getSplitter().splitLines(text, fieldWidth, FONT_STYLE, false,
                    (ignored, start, end) -> lines.add(new Span(start, end)));
            if (lines.isEmpty() || text.endsWith("\n")) lines.add(new Span(text.length(), text.length()));
            gaps = new int[text.length()];
            if (formatted) alignTables();
        }

        private void alignTables() {
            List<int[]> rows = new ArrayList<>();
            for (var line : lines) {
                String row = display.text().substring(line.beginIndex, line.endIndex).strip();
                if ((display.style(line.beginIndex) & PcNotesMarkdown.CELL) != 0
                        && row.length() >= 2 && row.startsWith("│") && row.endsWith("│")) {
                    rows.add(java.util.stream.IntStream.range(line.beginIndex, line.endIndex)
                            .filter(i -> display.text().charAt(i) == '│').toArray());
                } else if (!rows.isEmpty()) {
                    alignColumns(rows);
                    rows.clear();
                }
            }
            if (!rows.isEmpty()) alignColumns(rows);
        }

        private void alignColumns(List<int[]> rows) {
            int columns = rows.getFirst().length - 1;
            if (columns <= 0) return;
            int[] widths = new int[columns];
            Arrays.fill(widths, 27);
            for (var row : rows) {
                for (int i = 0; i < Math.min(columns, row.length - 1); i++) {
                    widths[i] = Math.max(widths[i], measuredWidth(row[i] + 1, row[i + 1], gaps));
                }
            }
            int total = Arrays.stream(widths).sum() + (columns + 1) * font.width(Component.literal("│").setStyle(FONT_STYLE));
            if (total > fieldWidth) return;
            for (var row : rows) {
                for (int i = 0; i < Math.min(columns, row.length - 1); i++) {
                    gaps[row[i + 1]] = widths[i] - measuredWidth(row[i] + 1, row[i + 1], gaps);
                }
            }
        }

        int cursor() { return cursor; }
        boolean hasSelection() { return anchor != cursor; }
        Span getSelected() { return new Span(Math.min(anchor, cursor), Math.max(anchor, cursor)); }
        String getSelectedText() {
            var selection = getSelected();
            return display.text().substring(selection.beginIndex, selection.endIndex);
        }
        int getLineCount() { return lines.size(); }
        Span getLineView(int row) { return lines.get(row); }
        int getLineAtCursor() {
            int low = 0, high = lines.size();
            while (low + 1 < high) {
                int mid = (low + high) / 2;
                if (lines.get(mid).beginIndex <= cursor) low = mid;
                else high = mid;
            }
            return low;
        }
        void move(int position, boolean selecting) {
            cursor = Math.clamp(position, 0, display.text().length());
            if (!selecting) anchor = cursor;
        }
    }

    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    private String source;
    private boolean formatted;
    private PcNotesMarkdown.Display display;
    private Field field;
    private int fieldWidth;
    private int preferredCaret;
    private boolean allSelected;
    private int scrollLine;
    private int scrollX;
    private int x, y, width, height;
    private float scale = 1;
    private int pending;

    PcNotesEditor(String source, boolean formatted) {
        this.source = source;
        this.formatted = formatted;
        refreshDisplay();
        fieldWidth = 260;
        buildField(0, 0);
    }

    String value() { return source; }

    int caret() {
        int position = field.cursor();
        return display.visible(preferredCaret) == position ? preferredCaret : display.caret(position);
    }

    int start() { return allSelected ? 0 : field.hasSelection() ? display.start(field.getSelected().beginIndex()) : caret(); }
    int end() { return allSelected ? source.length() : field.hasSelection() ? display.end(field.getSelected().endIndex()) : caret(); }
    boolean selected() { return allSelected || field.hasSelection(); }
    String selectedText() { return field.getSelectedText(); }

    void setFormatted(boolean formatted) {
        if (this.formatted == formatted) return;
        int from = start(), to = end();
        boolean backwards = field.hasSelection() && field.cursor() == field.getSelected().beginIndex();
        this.formatted = formatted;
        refreshDisplay();
        buildField(backwards ? to : from, backwards ? from : to);
    }

    void layout(int x, int y, int w, int h, int zoom, boolean wrap) {
        this.x = x;
        this.y = y;
        width = Math.max(1, w);
        height = Math.max(1, h);
        scale = zoom / 100f;
        int available = wrap ? Math.max(1, (int) (width / scale) - 6) : 262144;
        if (fieldWidth != available) {
            var state = snapshot();
            fieldWidth = available;
            buildField(state.anchor, state.caret);
            reveal();
        } else limitScroll();
    }

    private void refreshDisplay() {
        display = formatted ? PcNotesMarkdown.formatted(source) : PcNotesMarkdown.source(source);
    }

    private void buildField(int anchor, int cursor) {
        field = new Field();
        select(anchor, cursor);
    }

    private Snapshot snapshot() {
        boolean backwards = field.hasSelection() && field.cursor() == field.getSelected().beginIndex();
        return new Snapshot(source, backwards ? end() : start(), backwards ? start() : end());
    }

    void select(int anchor, int cursor) {
        allSelected = anchor == 0 && cursor == source.length() && anchor != cursor;
        preferredCaret = Math.clamp(cursor, 0, source.length());
        field.move(display.visible(Math.clamp(anchor, 0, source.length())), false);
        field.move(display.visible(preferredCaret), anchor != cursor);
        reveal();
    }

    void replace(int from, int to, String text, int anchor, int cursor) {
        if (source.length() - (to - from) + text.length() > PcFileSystem.TEXT_LIMIT) return;
        String next = source.substring(0, from) + text + source.substring(to);
        if (!next.equals(source)) {
            if (undo.size() >= MAX_UNDO) undo.removeFirst();
            undo.addLast(snapshot());
            redo.clear();
            source = next;
            refreshDisplay();
            buildField(anchor, cursor);
        } else select(anchor, cursor);
    }

    void undo(boolean forward) {
        var previous = forward ? redo : undo;
        if (previous.isEmpty()) return;
        (forward ? undo : redo).addLast(snapshot());
        var state = previous.removeLast();
        source = state.text;
        pending = 0;
        refreshDisplay();
        buildField(state.anchor, state.caret);
    }

    void insert(String text) {
        int from = start(), to = end();
        if (text.isEmpty() && from != to) {
            deleteRange(from, to);
            return;
        }
        if (formatted && from == to && !text.isEmpty() && Character.isWhitespace(text.charAt(0))) {
            int style = stylesAtCaret();
            int next = to;
            for (String delimiter : new String[]{"~~", "_", "**", "*"}) {
                if (source.startsWith(delimiter, next) && next > 0 && !Character.isWhitespace(source.charAt(next - 1))) {
                    next += delimiter.length();
                }
            }
            if (next != to) {
                replace(next, next, text, next + text.length(), next + text.length());
                pending = text.indexOf('\n') < 0 ? style & (PcNotesMarkdown.BOLD | PcNotesMarkdown.ITALIC | PcNotesMarkdown.STRIKE) : 0;
                return;
            }
        }
        if (pending != 0 && !text.isBlank() && text.indexOf('\n') < 0) {
            String mark = ((pending & PcNotesMarkdown.BOLD) != 0 ? "**" : "")
                    + ((pending & PcNotesMarkdown.ITALIC) != 0 ? "_" : "")
                    + ((pending & PcNotesMarkdown.STRIKE) != 0 ? "~~" : "");
            String end = ((pending & PcNotesMarkdown.STRIKE) != 0 ? "~~" : "")
                    + ((pending & PcNotesMarkdown.ITALIC) != 0 ? "_" : "")
                    + ((pending & PcNotesMarkdown.BOLD) != 0 ? "**" : "");
            int caret = from + mark.length() + text.length();
            replace(from, to, mark + text + end, caret, caret);
            pending = 0;
        } else replace(from, to, text, from + text.length(), from + text.length());
    }

    int stylesAtCaret() {
        int index = field.hasSelection() ? field.getSelected().beginIndex() : Math.max(0, field.cursor() - 1);
        return display.style(index) | pending;
    }

    int heading() {
        if (field.cursor() == display.text().length() && field.cursor() > 0 && display.text().endsWith("\n")) return 0;
        return display.heading(Math.min(field.cursor(), Math.max(0, display.text().length() - 1)));
    }

    boolean inTable() { return (stylesAtCaret() & PcNotesMarkdown.CELL) != 0; }

    void toggle(String delimiter, int style) {
        int from = start(), to = end();
        int count = delimiter.length();
        if (from >= count && source.startsWith(delimiter, from - count) && source.startsWith(delimiter, to)) {
            replace(from - count, to + count, source.substring(from, to), from - count, to - count);
        } else if (from != to) {
            String selection = source.substring(from, to);
            if (selection.startsWith(delimiter) && selection.endsWith(delimiter) && selection.length() > 2 * count) {
                replace(from, to, selection.substring(count, selection.length() - count), from, to - 2 * count);
            } else {
                replace(from, to, delimiter + selection + delimiter, from + count, to + count);
            }
        } else if (source.startsWith(delimiter, to)) {
            select(to + count, to + count);
            pending &= ~style;
        } else pending ^= style;
    }

    private PcClipboard clipboard;

    void clipboard(PcClipboard clipboard) { this.clipboard = clipboard; }

    boolean key(int key, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> { select(0, source.length()); return true; }
                case GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_X -> {
                    if (selected()) {
                        keyboard.setClipboard(selectedText());
                        if (clipboard != null) clipboard.set(new PcClipboard.Text(selectedText()));
                        if (key == GLFW.GLFW_KEY_X) insert("");
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    var value = clipboard == null ? null : clipboard.get();
                    if (value instanceof PcClipboard.Text text) insert(clean(text.text()));
                    else if (value == null) insert(clean(keyboard.getClipboard()));
                    return true;
                }
                case GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_Y -> { undo(shift || key == GLFW.GLFW_KEY_Y); return true; }
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { newLine(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) { insert("    "); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            if (!selected()) {
                boolean back = key == GLFW.GLFW_KEY_BACKSPACE;
                int pos = field.cursor();
                int edge;
                if (ctrl) edge = StringSplitter.getWordPosition(display.text(), back ? -1 : 1, pos, true);
                else edge = back ? display.text().offsetByCodePoints(pos, pos == 0 ? 0 : -1)
                        : display.text().offsetByCodePoints(pos, pos == display.text().length() ? 0 : 1);
                int low = Math.min(pos, edge), high = Math.max(pos, edge);
                if (low < high) {
                    deleteRange(display.start(low), display.end(high));
                }
            } else insert("");
            return true;
        }
        int before = field.cursor();
        int row = field.getLineAtCursor();
        var line = field.getLineView(row);
        int position;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                boolean left = key == GLFW.GLFW_KEY_LEFT;
                if (!shift && field.hasSelection()) {
                    position = left ? field.getSelected().beginIndex : field.getSelected().endIndex;
                } else if (ctrl) {
                    position = StringSplitter.getWordPosition(display.text(), left ? -1 : 1, before, true);
                } else {
                    int delta = left ? (before > 0 ? -1 : 0) : (before < display.text().length() ? 1 : 0);
                    position = display.text().offsetByCodePoints(before, delta);
                }
            }
            case GLFW.GLFW_KEY_HOME -> position = ctrl ? 0 : line.beginIndex;
            case GLFW.GLFW_KEY_END -> position = ctrl ? display.text().length() : line.endIndex;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                int onePage = Math.max(1, (int) (height / (LINE * scale)) - 1);
                int delta = switch (key) {
                    case GLFW.GLFW_KEY_UP -> -1;
                    case GLFW.GLFW_KEY_DOWN -> 1;
                    case GLFW.GLFW_KEY_PAGE_UP -> -onePage;
                    default -> onePage;
                };
                int target = Math.clamp(row + delta, 0, field.getLineCount() - 1);
                position = hitColumn(target, visualWidth(line.beginIndex, before) * rowScale(row));
            }
            default -> { return false; }
        }
        field.move(position, shift);
        allSelected = false;
        if (ctrl && key == GLFW.GLFW_KEY_END) {
            preferredCaret = source.length();
            pending = 0;
        } else if (ctrl && key == GLFW.GLFW_KEY_HOME) {
            preferredCaret = 0;
            pending = 0;
        } else if (key == GLFW.GLFW_KEY_END && !shift) {
            preferredCaret = display.start(field.cursor());
            pending = 0;
        } else if (field.cursor() != before) {
            preferredCaret = display.caret(field.cursor());
            pending = 0;
        }
        reveal();
        return true;
    }

    static String clean(String value) { return value.replace("\r\n", "\n").replace('\r', '\n').replace("\0", ""); }

    private void deleteRange(int from, int to) {
        if (formatted) {
            for (String delimiter : new String[]{"~~", "_", "**", "*"}) {
                int size = delimiter.length();
                if (from >= size && source.startsWith(delimiter, from - size) && source.startsWith(delimiter, to)) {
                    from -= size;
                    to += size;
                }
            }
        }
        replace(from, to, "", from, from);
    }

    private void newLine() {
        if (formatted && !selected()) {
            int caret = caret();
            int from = source.lastIndexOf('\n', caret - 1) + 1;
            int end = source.indexOf('\n', caret);
            if (end < 0) end = source.length();
            if (caret == end) {
                var markers = PcNotesMarkdown.LIST_PREFIX.matcher(source.substring(from, caret));
                if (markers.find()) {
                    if (markers.end() == caret - from) replace(from, caret, "", from, from);
                    else {
                        String marker = markers.group(2);
                        if (marker.length() > 1) {
                            try { marker = (Long.parseLong(marker.substring(0, marker.length() - 1)) + 1) + "."; }
                            catch (NumberFormatException ignored) { marker = "1."; }
                        }
                        insert("\n" + markers.group(1) + marker + " ");
                    }
                    return;
                }
            }
        }
        insert("\n");
    }

    void movePointer(double mx, double my, boolean select) {
        allSelected = false;
        int row = scrollLine;
        double remaining = (my - y) / scale;
        while (row < field.getLineCount() - 1 && remaining >= rowHeight(row)) remaining -= rowHeight(row++);
        if (my < y) row = Math.max(0, row - 1);
        field.move(hitColumn(row, (mx - x) / scale + scrollX), select);
        preferredCaret = display.caret(field.cursor());
        pending = 0;
        reveal();
    }

    private int hitColumn(int row, double pixel) {
        var line = field.getLineView(row);
        double target = pixel / rowScale(row);
        int hit = line.beginIndex(), measured = 0;
        while (hit < line.endIndex()) {
            int next = display.text().offsetByCodePoints(hit, 1);
            int advance = visualWidth(hit, next);
            if (measured + advance / 2.0 >= target) break;
            measured += advance;
            hit = next;
        }
        return hit;
    }

    void scroll(double delta) {
        scrollLine -= (int) Math.signum(delta) * 3;
        limitScroll();
    }

    private void limitScroll() {
        int visible = Math.max(1, (int) (height / (LINE * scale)));
        scrollLine = Math.clamp(scrollLine, 0, Math.max(0, field.getLineCount() - visible));
        if (fieldWidth < 262144) scrollX = 0;
    }

    private void reveal() {
        int row = field.getLineAtCursor();
        if (row < scrollLine) scrollLine = row;
        limitScroll();
        int roomY = (int) (height / scale);
        int used = 0;
        for (int i = scrollLine; i <= row && used <= roomY; i++) used += rowHeight(i);
        if (used > roomY) {
            scrollLine = row;
            used = rowHeight(row);
            while (scrollLine > 0 && used + rowHeight(scrollLine - 1) <= roomY) used += rowHeight(--scrollLine);
        }

        if (fieldWidth == 262144) {
            int position = (int) (visualWidth(field.getLineView(row).beginIndex(), field.cursor()) * rowScale(row));
            int room = Math.max(1, (int) (width / scale) - 5);
            scrollX = Math.clamp(scrollX, Math.max(0, position - room), position);
        }
    }

    private int rowHeight(int row) {
        int heading = display.heading(field.getLineView(row).beginIndex());
        return heading == 0 ? LINE : LINE + (7 - heading);
    }

    private float rowScale(int row) {
        int rowHeight = rowHeight(row);
        if (rowHeight == LINE) return 1;
        float enlarged = rowHeight / (float) LINE;
        if (fieldWidth == 262144) return enlarged;
        var line = field.getLineView(row);
        return Math.min(enlarged, Math.max(1, fieldWidth / (float) Math.max(1, visualWidth(line.beginIndex(), line.endIndex()))));
    }

    private static Style style(int flags) {
        return FONT_STYLE.withBold((flags & PcNotesMarkdown.BOLD) != 0).withItalic((flags & PcNotesMarkdown.ITALIC) != 0)
                .withStrikethrough((flags & PcNotesMarkdown.STRIKE) != 0).withUnderlined((flags & PcNotesMarkdown.LINK) != 0);
    }

    private int visualWidth(int from, int to) {
        if (!formatted) return font.width(Component.literal(display.text().substring(from, to)).setStyle(FONT_STYLE));
        return measuredWidth(from, to, field.gaps);
    }

    private int measuredWidth(int from, int to, int[] gaps) {
        int pixels = 0;
        for (int i = from; i < to;) {
            int flags = display.style(i), end = i + 1;
            while (end < to && gaps[end] == 0 && display.style(end) == flags) end++;
            pixels += gaps[i] + font.width(Component.literal(display.text().substring(i, end)).setStyle(style(flags)));
            i = end;
        }
        return pixels;
    }

    void render(GuiGraphics g, boolean focused) {
        g.enableScissor(x, y, x + width, y + height);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        try {
            var selection = field.getSelected();
            int top = 0;
            for (int row = scrollLine; row < field.getLineCount() && top < height / scale; row++) {
                var line = field.getLineView(row);
                int from = line.beginIndex(), to = line.endIndex();
                float lineScale = rowScale(row);
                g.pose().pushPose();
                g.pose().translate(-scrollX, top, 0);
                g.pose().scale(lineScale, lineScale, 1);
                if ((display.style(from) & PcNotesMarkdown.CELL) != 0) {
                    g.fill(0, 0, visualWidth(from, to), LINE, 0xFF29384A);
                }
                int a = Math.max(from, selection.beginIndex()), b = Math.min(to, selection.endIndex());
                if (a < b) {
                    int left = visualWidth(from, a);
                    g.fill(left, 0, left + visualWidth(a, b), LINE, 0xFF3E587A);
                }
                int left = 0;
                for (int i = from; i < to;) {
                    int flags = display.style(i), heading = display.heading(i);
                    int end = i + 1;
                    while (end < to && field.gaps[end] == 0 && display.style(end) == flags && display.heading(end) == heading) end++;
                    left += field.gaps[i];
                    var text = Component.literal(display.text().substring(i, end)).setStyle(style(flags));
                    int color = (flags & PcNotesMarkdown.LINK) != 0 ? ACCENT
                            : heading > 0 && heading <= 2 ? 0xFFA8DAF3
                            : (flags & PcNotesMarkdown.CELL) != 0 ? 0xFFB8CEDF : TEXT;
                    g.drawString(font, text, left, 2, color, false);
                    left += font.width(text);
                    i = end;
                }
                if (focused && row == field.getLineAtCursor() && net.minecraft.Util.getMillis() / 500 % 2 == 0) {
                    int cursor = Math.clamp(field.cursor(), from, to);
                    int caretX = visualWidth(from, cursor);
                    g.fill(caretX, 1, caretX + 1, 11, TEXT);
                }
                g.pose().popPose();
                top += rowHeight(row);
            }
        } finally {
            g.pose().popPose();
            g.disableScissor();
        }
    }
}
