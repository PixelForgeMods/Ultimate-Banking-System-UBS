package net.austizz.ultimatebankingsystem.gui.screens;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class PcNotesMarkdown {
    static final int BOLD = 1, ITALIC = 2, STRIKE = 4, LINK = 8, CODE = 16, HEADING = 32, CELL = 64;
    static final Pattern HEADING_PREFIX = Pattern.compile("^( {0,3})#{1,6}(?:[ \\t]+|$)");
    static final Pattern LIST_PREFIX = Pattern.compile("^(\\s*)([-+*]|\\d+[.)])([ \\t]+|$)");
    private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create())).build();

    record Display(String text, int[] origins, int[] offsets, int[] styles, int[] levels) {
        int start(int position) {
            return origins == null ? position : position < origins.length ? origins[position] : offsets.length - 1;
        }

        int end(int position) {
            return origins == null || position == 0 ? position : origins[position - 1] + 1;
        }

        int caret(int position) {
            if (origins == null) return position;
            // At the beginning of a visual line, place the caret after any hidden prefix.
            if (position == 0 || text.charAt(position - 1) == '\n') return start(position);
            return end(position);
        }

        int visible(int offset) { return offsets == null ? offset : offsets[offset]; }
        int style(int position) { return styles == null || position >= styles.length ? 0 : styles[position]; }
        int heading(int position) { return levels == null || position >= levels.length ? 0 : levels[position]; }
    }

    static Display source(String source) { return new Display(source, null, null, null, null); }

    static Display formatted(String source) {
        var visible = new StringBuilder(source.length());
        boolean[] hidden = new boolean[source.length()];
        int[] styles = new int[source.length()];
        int[] headings = new int[source.length()];
        char[] glyphs = source.toCharArray();
        decorate(PARSER.parse(source), source, glyphs, hidden, styles, headings, 0);

        int[] origins = new int[source.length()];
        int[] offsets = new int[source.length() + 1];
        int[] visibleStyles = new int[source.length()];
        int[] visibleHeadings = new int[source.length()];
        for (int i = 0; i < source.length(); i++) {
            int at = visible.length();
            offsets[i] = at;
            if (!hidden[i]) {
                origins[at] = i;
                visibleStyles[at] = styles[i];
                visibleHeadings[at] = headings[i];
                visible.append(glyphs[i]);
            }
        }
        offsets[source.length()] = visible.length();
        return new Display(visible.toString(), Arrays.copyOf(origins, visible.length()), offsets,
                visibleStyles, visibleHeadings);
    }

    private static void decorate(Node node, String source, char[] glyphs, boolean[] hidden,
                                 int[] styles, int[] headings, int depth) {
        if (depth > 64) return;
        var spans = node.getSourceSpans();
        if (!spans.isEmpty()) {
            int from = spans.getFirst().getInputIndex();
            int to = spans.getLast().getInputIndex() + spans.getLast().getLength();
            int style = switch (node) {
                case StrongEmphasis ignored -> BOLD;
                case Emphasis ignored -> ITALIC;
                case Strikethrough ignored -> STRIKE;
                case Link ignored -> LINK;
                case Code ignored -> CODE;
                case Heading ignored -> HEADING | BOLD;
                case TableBlock ignored -> CELL;
                default -> 0;
            };
            for (int i = from; i < to; i++) styles[i] |= style;
            if (node instanceof Heading heading) {
                Arrays.fill(headings, from, to, heading.getLevel());
                var prefix = HEADING_PREFIX.matcher(source.substring(from, to));
                if (prefix.find()) Arrays.fill(hidden, from, from + prefix.end(), true);
            } else if (node instanceof BulletList) {
                for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                    if (!item.getSourceSpans().isEmpty()) {
                        var first = item.getSourceSpans().getFirst();
                        int offset = first.getInputIndex();
                        var marker = LIST_PREFIX.matcher(source.substring(offset, offset + first.getLength()));
                        if (marker.find()) glyphs[offset + marker.start(2)] = '•';
                    }
                }
            } else if (node instanceof TableBlock) {
                for (int i = from; i < to; i++) {
                    if (source.charAt(i) == '|' && (i == from || source.charAt(i - 1) != '\\')) glyphs[i] = '│';
                }
            } else if ((style & (BOLD | ITALIC | STRIKE | LINK)) != 0 && node.getFirstChild() != null) {
                var first = node.getFirstChild().getSourceSpans();
                var last = node.getLastChild().getSourceSpans();
                if (!first.isEmpty() && !last.isEmpty()) {
                    Arrays.fill(hidden, from, first.getFirst().getInputIndex(), true);
                    int end = last.getLast().getInputIndex() + last.getLast().getLength();
                    Arrays.fill(hidden, end, to, true);
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            decorate(child, source, glyphs, hidden, styles, headings, depth + 1);
        }
    }

    static String plain(String markdown) { return formatted(markdown).text(); }

    static String table(int columns, int rows) {
        var header = new StringBuilder("|");
        for (int col = 1; col <= columns; col++) header.append(" Column ").append(col).append(" |");
        return header + "\n|" + " --- |".repeat(columns) + ("\n|" + "  |".repeat(columns)).repeat(rows - 1);
    }

    static String editTable(String source, int caret, int rowDelta, int columnDelta) {
        String[] lines = source.split("\n", -1);
        int at = source.substring(0, caret).split("\n", -1).length - 1;
        if (!isTableLine(lines[at])) return source;
        int start = at;
        int end = at + 1;
        while (start > 0 && isTableLine(lines[start - 1])) start--;
        while (end < lines.length && isTableLine(lines[end])) end++;
        if (end - start < 2 || !lines[start + 1].matches("\\s*\\|[\\s|:-]+\\|\\s*")) return source;

        var changed = new java.util.ArrayList<>(List.of(lines).subList(start, end));
        int columns = cells(changed.getFirst()).length;
        if (rowDelta > 0) changed.add(Math.max(2, at - start + 1), "|" + "  |".repeat(columns));
        if (rowDelta < 0 && at - start >= 2) changed.remove(at - start);
        if (columnDelta != 0 && (columnDelta > 0 || columns > 1)) {
            int lineOffset = source.lastIndexOf('\n', Math.max(-1, caret - 1)) + 1;
            String prefix = lines[at].substring(0, Math.min(caret - lineOffset, lines[at].length()));
            int column = Math.clamp((int) Pattern.compile("(?<!\\\\)\\|").matcher(prefix).results().count() - 1, 0, columns - 1);
            for (int i = 0; i < changed.size(); i++) {
                var cells = new java.util.ArrayList<>(Arrays.asList(cells(changed.get(i))));
                while (cells.size() < columns) cells.add("  ");
                if (columnDelta > 0) cells.add(column + 1, i == 1 ? " --- " : "  ");
                else cells.remove(column);
                changed.set(i, "|" + String.join("|", cells) + "|");
            }
        }
        var result = new java.util.ArrayList<>(List.of(lines).subList(0, start));
        result.addAll(changed);
        result.addAll(List.of(lines).subList(end, lines.length));
        return String.join("\n", result);
    }

    private static boolean isTableLine(String line) {
        String clean = line.strip();
        return clean.length() >= 2 && clean.startsWith("|") && clean.endsWith("|");
    }

    private static String[] cells(String line) {
        String clean = line.strip();
        return clean.substring(1, clean.length() - 1).split("(?<!\\\\)\\|", -1);
    }

    private PcNotesMarkdown() {}
}
