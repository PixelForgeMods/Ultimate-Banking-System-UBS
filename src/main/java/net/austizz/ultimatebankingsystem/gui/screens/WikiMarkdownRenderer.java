package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.wiki.WikiMarkdown;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayList;
import java.util.List;

/** Draws CommonMark nodes directly in a clipped Minecraft GUI. */
final class WikiMarkdownRenderer {
    private static final int WHITE = 0xFFE9F4F8;
    private static final int MUTED = 0xFF98B3C4;
    private static final int CYAN = 0xFF51C9F4;
    private static final int GREEN = 0xFF65DDA7;
    private static final int GOLD = 0xFFF4C66A;
    private static final int PANEL = 0xFF132A3D;
    private static final int RULE = 0xFF31546C;

    private record LinkLine(int x, int y, int width, FormattedCharSequence sequence) {}
    private record ScreenshotHit(int x, int y, int width, int height, Screenshot screenshot) {}
    record Screenshot(WikiMarkdown.Media media, String caption) {}

    private final Font font;
    private final List<LinkLine> links = new ArrayList<>();
    private final List<ScreenshotHit> screenshots = new ArrayList<>();
    private ItemStack hoveredItem = ItemStack.EMPTY;
    private String hoveredText;
    private int clipTop;
    private int clipBottom;
    private int mouseX;
    private int mouseY;

    WikiMarkdownRenderer(Font font) {
        this.font = font;
    }

    int render(GuiGraphics graphics, Node document, int x, int y, int width,
               int clipTop, int clipBottom, int mouseX, int mouseY) {
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        links.clear();
        screenshots.clear();
        hoveredItem = ItemStack.EMPTY;
        hoveredText = null;
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            y = block(graphics, child, x, y, width, 0);
        }
        if (hoveredText == null) {
            Style hoveredStyle = linkAt(mouseX, mouseY);
            if (hoveredStyle != null && hoveredStyle.getClickEvent() != null
                    && hoveredStyle.getClickEvent().getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
                hoveredText = "Click to copy command";
            }
        }
        return y;
    }

    ItemStack hoveredItem() {
        return hoveredItem;
    }

    String hoveredText() {
        return hoveredText;
    }

    Style linkAt(double x, double y) {
        for (LinkLine line : links) {
            if (x >= line.x() && x < line.x() + line.width()
                    && y >= line.y() && y < line.y() + 12) {
                Style style = font.getSplitter().componentStyleAtWidth(line.sequence(), (int) x - line.x());
                ClickEvent event = style == null ? null : style.getClickEvent();
                return event == null ? null : style;
            }
        }
        return null;
    }

    Screenshot screenshotAt(double x, double y) {
        for (ScreenshotHit hit : screenshots) {
            if (x >= hit.x() && x < hit.x() + hit.width() && y >= hit.y()
                    && y < hit.y() + hit.height() && y >= clipTop && y < clipBottom) return hit.screenshot();
        }
        return null;
    }

    void renderExpanded(GuiGraphics g, Screenshot screenshot, int screenWidth, int screenHeight) {
        g.fill(0, 0, screenWidth, screenHeight, 0xD9000B13);
        int availableWidth = Math.max(1, screenWidth - 48);
        int availableHeight = Math.max(1, screenHeight - 85);
        double scale = Math.min((double) availableWidth / screenshot.media().width(),
                (double) availableHeight / screenshot.media().height());
        int imageWidth = Math.max(1, (int) (screenshot.media().width() * scale));
        int imageHeight = Math.max(1, (int) (screenshot.media().height() * scale));
        int x = (screenWidth - imageWidth) / 2;
        int y = (screenHeight - imageHeight - 26) / 2;
        g.fill(x - 5, y - 5, x + imageWidth + 5, y + imageHeight + 5, RULE);
        drawScreenshot(g, screenshot.media(), x, y, scale);
        String caption = font.plainSubstrByWidth(screenshot.caption(), availableWidth);
        g.drawString(font, caption, x, y + imageHeight + 12, WHITE, false);
        g.drawString(font, "Esc or click to close", x, y + imageHeight + 25, MUTED, false);
    }

    private int block(GuiGraphics g, Node node, int x, int y, int width, int depth) {
        if (node instanceof Heading heading) {
            int level = heading.getLevel();
            y += level == 1 ? 5 : 15;
            int color = level == 1 ? WHITE : GREEN;
            y = lines(g, inline(heading), x, y, width, color, level <= 2 ? 15 : 13);
            if (level == 1 && visible(y, 1)) g.fill(x, y + 2, x + width, y + 3, RULE);
            return y + (level == 1 ? 13 : 5);
        }
        if (node instanceof Paragraph paragraph) {
            int currentY = y;
            MutableComponent pending = Component.empty();
            for (Node child = paragraph.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Image image && WikiMarkdown.media(image.getDestination()).isPresent()) {
                    if (!pending.getString().isEmpty()) currentY = lines(g, pending, x, currentY, width, WHITE, 12);
                    currentY = media(g, image, x, currentY + 2, width) + 4;
                    pending = Component.empty();
                } else {
                    append(child, pending, Style.EMPTY);
                }
            }
            if (!pending.getString().isEmpty()) currentY = lines(g, pending, x, currentY, width, WHITE, 12);
            return currentY + 9;
        }
        if (node instanceof TableBlock table) return table(g, table, x, y + 5, width) + 11;
        if (node instanceof FencedCodeBlock code) return code(g, code.getLiteral(), x, y, width);
        if (node instanceof IndentedCodeBlock code) return code(g, code.getLiteral(), x, y, width);
        if (node instanceof ThematicBreak) {
            if (visible(y, 1)) g.fill(x, y + 5, x + width, y + 6, RULE);
            return y + 17;
        }
        if (node instanceof HtmlBlock html) return lines(g, Component.literal(html.getLiteral()), x, y, width, MUTED, 12) + 8;
        if (node instanceof BlockQuote) {
            int end = children(g, node, x + 12, y + 5, width - 16, depth + 1);
            if (visible(y, end - y)) {
                g.fill(x, y, x + 3, end + 3, CYAN);
            }
            return end + 8;
        }
        if (node instanceof BulletList || node instanceof OrderedList) {
            int number = node instanceof OrderedList ordered ? ordered.getStartNumber() : 0;
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                String marker = node instanceof OrderedList ? (number++) + "." : "-";
                if (visible(y, 11)) g.drawString(font, marker, x, y, CYAN, false);
                y = children(g, item, x + 15, y, width - 15, depth + 1);
            }
            return y + 5;
        }
        if (node instanceof ListItem) return children(g, node, x, y, width, depth + 1);
        return children(g, node, x, y, width, depth + 1);
    }

    private int children(GuiGraphics g, Node parent, int x, int y, int width, int depth) {
        if (depth > 12) return y;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            y = block(g, child, x, y, Math.max(40, width), depth);
        }
        return y;
    }

    private int code(GuiGraphics g, String value, int x, int y, int width) {
        String[] rows = value.stripTrailing().split("\n", -1);
        boolean command = value.stripLeading().startsWith("/");
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String row : rows) {
            wrapped.addAll(font.split(Component.literal(row.isEmpty() ? " " : row), Math.max(24, width - (command ? 54 : 16))));
        }
        int height = Math.max(1, wrapped.size()) * 12 + 14;
        if (visible(y, height)) {
            g.fill(x, y, x + width, y + height, PANEL);
            g.fill(x, y, x + 3, y + height, GOLD);
            for (int i = 0; i < wrapped.size(); i++) {
                g.drawString(font, wrapped.get(i), x + 8, y + 7 + i * 12, GOLD, false);
            }
            if (command) {
                String copy = "Copy";
                int copyX = x + width - font.width(copy) - 8;
                g.drawString(font, copy, copyX, y + 7, CYAN, false);
                links.add(new LinkLine(copyX, y + 7, font.width(copy),
                        Component.literal(copy).withStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value.strip()))).getVisualOrderText()));
            }
        }
        return y + height + 10;
    }

    private int table(GuiGraphics g, TableBlock table, int x, int y, int width) {
        List<TableRow> rows = new ArrayList<>();
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (row instanceof TableRow tableRow) rows.add(tableRow);
            }
        }
        int columns = rows.stream().mapToInt(row -> childCount(row)).max().orElse(0);
        if (columns == 0) return y;
        int cellWidth = Math.max(1, width / columns);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            TableRow row = rows.get(rowIndex);
            int rowHeight = 22;
            int col = 0;
            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                if (cell instanceof TableCell) rowHeight = Math.max(rowHeight,
                        tableCell(g, cell, x + col * cellWidth, y + 5, cellWidth - 8, false) - y + 5);
                col++;
            }
            if (visible(y, rowHeight)) {
                g.fill(x, y, x + width, y + rowHeight, rowIndex == 0 ? PANEL : (rowIndex % 2 == 0 ? 0xFF102638 : 0xFF0F2232));
                g.fill(x, y + rowHeight - 1, x + width, y + rowHeight, RULE);
                col = 0;
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (col > 0) g.fill(x + col * cellWidth, y, x + col * cellWidth + 1, y + rowHeight, RULE);
                    if (cell instanceof TableCell) tableCell(g, cell, x + col * cellWidth + 4, y + 5, cellWidth - 8, true);
                    col++;
                }
            }
            y += rowHeight;
        }
        return y;
    }

    private int tableCell(GuiGraphics g, Node cell, int x, int y, int width, boolean draw) {
        MutableComponent pending = Component.empty();
        for (Node child = cell.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Image image && WikiMarkdown.media(image.getDestination()).isPresent()) {
                if (!pending.getString().isEmpty()) {
                    y = draw ? lines(g, pending, x, y, width, WHITE, 12) : measure(pending, width, 12, y);
                    pending = Component.empty();
                }
                y = draw ? media(g, image, x, y, width) : y + mediaSize(image, width);
            } else append(child, pending, Style.EMPTY);
        }
        if (!pending.getString().isEmpty()) y = draw ? lines(g, pending, x, y, width, WHITE, 12)
                : measure(pending, width, 12, y);
        return y;
    }

    private int childCount(Node node) {
        int count = 0;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) count++;
        return count;
    }

    private int measure(Component content, int width, int step, int y) {
        return y + Math.max(1, font.split(content, Math.max(24, width)).size()) * step;
    }

    private int lines(GuiGraphics g, Component content, int x, int y, int width, int color, int step) {
        for (FormattedCharSequence line : font.split(content, Math.max(24, width))) {
            if (visible(y, step)) {
                g.drawString(font, line, x, y, color, false);
                links.add(new LinkLine(x, y, font.width(line), line));
            }
            y += step;
        }
        return y;
    }

    private int mediaSize(Image image, int width) {
        return WikiMarkdown.media(image.getDestination()).map(media -> media.kind().equals("screenshot")
                ? screenshotHeight(media, width) + 19 : Math.min(media.size(), width)).orElse(16);
    }

    private int media(GuiGraphics g, Image image, int x, int y, int width) {
        WikiMarkdown.Media media = WikiMarkdown.media(image.getDestination()).orElseThrow();
        if (media.kind().equals("screenshot")) {
            int imageWidth = screenshotWidth(media, width);
            int imageHeight = screenshotHeight(media, width);
            if (visible(y, imageHeight + 19)) {
                g.fill(x, y, x + imageWidth, y + imageHeight, RULE);
                double scale = (double) imageWidth / media.width();
                drawScreenshot(g, media, x, y, scale);
                String caption = inline(image).getString();
                g.drawString(font, font.plainSubstrByWidth(caption, width), x, y + imageHeight + 5, MUTED, false);
                screenshots.add(new ScreenshotHit(x, y, imageWidth, imageHeight, new Screenshot(media, caption)));
                if (mouseX >= x && mouseX < x + imageWidth && mouseY >= y && mouseY < y + imageHeight
                        && mouseY >= clipTop && mouseY < clipBottom) hoveredText = "Click to enlarge";
            }
            return y + imageHeight + 19;
        }
        int size = Math.max(12, Math.min(media.size(), width));
        ResourceLocation id = ResourceLocation.tryParse(media.id());
        if (id == null) return lines(g, Component.literal("Invalid image: " + media.id()), x, y, width, MUTED, 12);
        if (media.kind().equals("item")) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                return lines(g, Component.literal("Missing item: " + media.id()), x, y, width, MUTED, 12);
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            if (visible(y, size)) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                float scale = size / 16.0F;
                g.pose().scale(scale, scale, 1);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
                if (media.tooltip() && hovered(x, y, size)) {
                    hoveredItem = stack;
                }
            }
        } else if (visible(y, size)) {
            g.blit(id, x, y, 0, 0, size, size, size, size);
            if (media.tooltip() && hovered(x, y, size)) hoveredText = inline(image).getString();
        }
        return y + size;
    }

    private int screenshotWidth(WikiMarkdown.Media media, int width) {
        double scale = Math.min(1.0, Math.min((double) Math.min(width, 560) / media.width(),
                220.0 / media.height()));
        return Math.max(1, (int) (media.width() * scale));
    }

    private int screenshotHeight(WikiMarkdown.Media media, int width) {
        return Math.max(1, (int) ((double) screenshotWidth(media, width) * media.height() / media.width()));
    }

    private void drawScreenshot(GuiGraphics g, WikiMarkdown.Media media, int x, int y, double scale) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem",
                "textures/gui/wiki/screenshots/" + media.id().substring(media.id().indexOf(':') + 1) + ".png");
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) scale, (float) scale, 1);
        g.blit(id, 0, 0, 0, 0, media.width(), media.height(), media.width(), media.height());
        g.pose().popPose();
    }

    private MutableComponent inline(Node parent) {
        MutableComponent out = Component.empty();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) append(child, out, Style.EMPTY);
        return out;
    }

    private void append(Node node, MutableComponent out, Style style) {
        if (node instanceof Text text) {
            out.append(Component.literal(text.getLiteral()).setStyle(style));
            return;
        }
        if (node instanceof Code code) {
            String value = code.getLiteral();
            Style codeStyle = style.withColor(TextColor.fromRgb(GOLD & 0xFFFFFF));
            if (value.startsWith("/")) {
                codeStyle = codeStyle.withUnderlined(true).withClickEvent(
                        new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value));
            }
            out.append(Component.literal(value).setStyle(codeStyle));
            return;
        }
        if (node instanceof HtmlInline html) {
            out.append(Component.literal(html.getLiteral()).setStyle(style));
            return;
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            out.append(Component.literal(node instanceof HardLineBreak ? "\n" : " ").setStyle(style));
            return;
        }
        if (node instanceof StrongEmphasis) style = style.withBold(true);
        else if (node instanceof Emphasis) style = style.withItalic(true);
        else if (node instanceof Strikethrough) style = style.withStrikethrough(true);
        else if (node instanceof Link link && allowedLink(link.getDestination())) {
            style = style.withColor(TextColor.fromRgb(CYAN & 0xFFFFFF)).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link.getDestination()));
        } else if (node instanceof Image image) {
            String alt = inline(image).getString();
            if (alt.isBlank()) alt = "image";
            if (allowedLink(image.getDestination())) {
                style = style.withColor(TextColor.fromRgb(CYAN & 0xFFFFFF)).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, image.getDestination()));
            }
            out.append(Component.literal("[" + alt + "]").setStyle(style));
            return;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) append(child, out, style);
    }

    private boolean allowedLink(String destination) {
        return destination.startsWith("wiki:") || destination.startsWith("https://") || destination.startsWith("http://");
    }

    private boolean visible(int y, int height) {
        return y + height >= clipTop && y < clipBottom;
    }

    private boolean hovered(int x, int y, int size) {
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size
                && mouseY >= clipTop && mouseY < clipBottom;
    }
}
