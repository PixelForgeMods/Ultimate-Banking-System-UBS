package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.wiki.WikiCatalog;
import net.austizz.ultimatebankingsystem.wiki.WikiMarkdown;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.commonmark.node.Node;

import java.util.ArrayList;
import java.util.List;

/** Offline, searchable guide with a documentation-style article sidebar. */
public final class WikiScreen extends Screen {
    private static final int BACK = 0xFF0A1825;
    private static final int SURFACE = 0xFF12283B;
    private static final int SIDEBAR = 0xFF0E2232;
    private static final int HOVER = 0xFF1B3B51;
    private static final int BORDER = 0xFF31546C;
    private static final int WHITE = 0xFFE9F4F8;
    private static final int MUTED = 0xFF98B3C4;
    private static final int CYAN = 0xFF51C9F4;
    private static final int GREEN = 0xFF65DDA7;
    private static final int ROW = 24;

    private WikiCatalog.Page current = WikiCatalog.pages().getFirst();
    private Node document = WikiMarkdown.parse(current.markdown());
    private WikiMarkdownRenderer markdownRenderer;
    private EditBox search;
    private String query = "";
    private int navScroll;
    private int articleScroll;
    private int articleHeight;
    private int navHeight;
    private boolean showNavigation = true;
    private WikiMarkdownRenderer.Screenshot expandedScreenshot;

    public WikiScreen() {
        super(Component.literal("UBS Guide"));
    }

    @Override
    protected void init() {
        markdownRenderer = new WikiMarkdownRenderer(font);
        int searchWidth = Math.min(260, Math.max(88, width / 3));
        search = new EditBox(font, width - searchWidth - 61, 14, searchWidth, 19, Component.literal("Search guide"));
        search.setHint(Component.literal("Search guide..."));
        search.setMaxLength(80);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            navScroll = 0;
            showNavigation = true;
        });
        addRenderableWidget(search);
    }

    private boolean compact() {
        return width < 620;
    }

    private int navWidth() {
        return compact() ? width - 24 : Math.min(224, Math.max(164, width / 4));
    }

    private int bodyTop() {
        return compact() ? 76 : 53;
    }

    private int bodyBottom() {
        return height - 37;
    }

    private int articleX() {
        return compact() ? 18 : navWidth() + 30;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACK);
        graphics.fill(0, 0, width, 45, SIDEBAR);
        graphics.fill(0, 44, width, 45, CYAN);
        text(graphics, width < 270 ? "UBS" : "UBS / GUIDE", 16, 17, WHITE);
        if (compact()) {
            action(graphics, 8, 47, 70, 22, showNavigation ? "Article" : "Browse",
                    mouseX, mouseY, true);
        }
        action(graphics, width - 39, 13, 25, 21, "X", mouseX, mouseY, false);
        if (!compact() || showNavigation) renderNavigation(graphics, mouseX, mouseY);
        if (!compact() || !showNavigation) renderArticle(graphics, mouseX, mouseY);
        graphics.fill(0, height - 32, width, height, SIDEBAR);
        graphics.fill(0, height - 33, width, height - 32, BORDER);
        if (!compact() || !showNavigation) {
            int index = WikiCatalog.pages().indexOf(current);
            action(graphics, articleX(), height - 28, 56, 23, "< Prev", mouseX, mouseY, false);
            action(graphics, articleX() + 62, height - 28, 56, 23, "Next >", mouseX, mouseY, false);
            text(graphics, (index + 1) + " / " + WikiCatalog.pages().size(),
                    Math.min(width - 67, articleX() + 128), height - 21, MUTED);
        } else {
            text(graphics, "Select an article", 16, height - 22, MUTED);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (expandedScreenshot == null) {
            if (!compact() || !showNavigation) {
                if (!markdownRenderer.hoveredItem().isEmpty()) {
                    graphics.renderTooltip(font, markdownRenderer.hoveredItem(), mouseX, mouseY);
                } else if (markdownRenderer.hoveredText() != null) {
                    graphics.renderTooltip(font, Component.literal(markdownRenderer.hoveredText()), mouseX, mouseY);
                }
            }
        } else {
            // Item previews use elevated GUI depth, so the whole modal must sit above them.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            markdownRenderer.renderExpanded(graphics, expandedScreenshot, width, height);
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Opaque screen: do not blur the game behind the guide.
    }

    private void renderNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = compact() ? 8 : 0;
        int w = navWidth();
        int top = compact() ? 76 : bodyTop();
        int bottom = bodyBottom();
        graphics.fill(x, top, x + w, bottom, SIDEBAR);
        graphics.fill(x + w - 1, top, x + w, bottom, BORDER);
        List<WikiCatalog.Page> pages = matchingPages();
        text(graphics, query.isBlank() ? "CONTENTS" : pages.size() + " RESULTS", x + 15, top + 13, CYAN);
        int listTop = top + 33;
        graphics.enableScissor(x + 1, listTop, x + w - 1, bottom - 5);
        int y = listTop - navScroll;
        String category = "";
        for (WikiCatalog.Page page : pages) {
            if (!category.equals(page.category())) {
                category = page.category();
                y += 6;
                text(graphics, category.toUpperCase(), x + 15, y + 2, MUTED);
                y += 17;
            }
            if (y + ROW >= listTop && y < bottom - 5) {
                boolean selected = page == current;
                boolean hover = mouseX >= x + 7 && mouseX < x + w - 8 && mouseY >= y && mouseY < y + ROW;
                if (selected || hover) {
                    graphics.fill(x + 7, y, x + w - 8, y + ROW, selected ? SURFACE : HOVER);
                    graphics.fill(x + 7, y, x + 10, y + ROW, selected ? GREEN : CYAN);
                }
                textFit(graphics, page.title(), x + 17, y + 8, w - 32, selected ? WHITE : MUTED);
            }
            y += ROW;
        }
        graphics.disableScissor();
        navHeight = y + navScroll - listTop;
        if (pages.isEmpty()) text(graphics, "No matching pages", x + 15, listTop + 8, MUTED);
    }

    private void renderArticle(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = articleX();
        int right = width - 19;
        int top = bodyTop();
        int bottom = bodyBottom();
        graphics.enableScissor(x, top, right, bottom);
        int y = top + 14 - articleScroll;
        text(graphics, "UBS GUIDE  /  " + current.category().toUpperCase(), x, y, CYAN);
        y += 23;
        y = markdownRenderer.render(graphics, document, x, y, right - x, top, bottom, mouseX, mouseY);
        articleHeight = y + articleScroll - top + 10;
        graphics.disableScissor();
        if (articleHeight > bottom - top) {
            int trackX = width - 12;
            int trackH = bottom - top - 12;
            int thumbH = Math.max(16, trackH * trackH / articleHeight);
            int max = Math.max(1, articleHeight - (bottom - top));
            int thumbY = top + 6 + (trackH - thumbH) * articleScroll / max;
            graphics.fill(trackX, top + 6, trackX + 3, top + 6 + trackH, BORDER);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, CYAN);
        }
    }

    private void text(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, false);
    }

    private void textFit(GuiGraphics graphics, String value, int x, int y, int available, int color) {
        String fitted = font.width(value) <= available ? value : font.plainSubstrByWidth(value, available - 12) + "...";
        text(graphics, fitted, x, y, color);
    }

    private void action(GuiGraphics graphics, int x, int y, int w, int h, String label,
                        int mouseX, int mouseY, boolean selected) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, selected ? CYAN : BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SIDEBAR);
        text(graphics, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, WHITE);
    }

    private List<WikiCatalog.Page> matchingPages() {
        List<WikiCatalog.Page> result = new ArrayList<>();
        for (WikiCatalog.Page page : WikiCatalog.pages()) if (page.matches(query)) result.add(page);
        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (expandedScreenshot != null) {
            expandedScreenshot = null;
            return true;
        }
        if (button == 0) {
            if (mouseX >= width - 39 && mouseX < width - 14 && mouseY >= 13 && mouseY < 34) {
                onClose();
                return true;
            }
            if (compact() && mouseX >= 8 && mouseX < 78 && mouseY >= 47 && mouseY < 69) {
                showNavigation = !showNavigation;
                return true;
            }
            if ((!compact() || showNavigation) && selectNavigation(mouseX, mouseY)) return true;
            if ((!compact() || !showNavigation) && mouseY >= bodyTop() && mouseY < bodyBottom()
                    && mouseX >= articleX()) {
                WikiMarkdownRenderer.Screenshot screenshot = markdownRenderer.screenshotAt(mouseX, mouseY);
                if (screenshot != null) {
                    expandedScreenshot = screenshot;
                    return true;
                }
                Style link = markdownRenderer.linkAt(mouseX, mouseY);
                if (link != null && link.getClickEvent() != null) {
                    ClickEvent event = link.getClickEvent();
                    if (event.getAction() == ClickEvent.Action.OPEN_URL && event.getValue().startsWith("wiki:")) {
                        WikiCatalog.pages().stream().filter(page -> page.id().equals(event.getValue().substring(5)))
                                .findFirst().ifPresent(this::select);
                    } else {
                        handleComponentClicked(link);
                    }
                    return true;
                }
            }
            if ((!compact() || !showNavigation) && mouseY >= height - 28 && mouseY < height - 5) {
                int index = WikiCatalog.pages().indexOf(current);
                if (mouseX >= articleX() && mouseX < articleX() + 56 && index > 0) {
                    select(WikiCatalog.pages().get(index - 1));
                    return true;
                }
                if (mouseX >= articleX() + 62 && mouseX < articleX() + 118
                        && index + 1 < WikiCatalog.pages().size()) {
                    select(WikiCatalog.pages().get(index + 1));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expandedScreenshot != null) {
            if (keyCode == 256) expandedScreenshot = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean selectNavigation(double mouseX, double mouseY) {
        int x = compact() ? 8 : 0;
        int top = compact() ? 76 : bodyTop();
        int listTop = top + 33;
        if (mouseX < x + 7 || mouseX >= x + navWidth() - 8
                || mouseY < listTop || mouseY >= bodyBottom() - 5) return false;
        int y = listTop - navScroll;
        String category = "";
        for (WikiCatalog.Page page : matchingPages()) {
            if (!category.equals(page.category())) {
                category = page.category();
                y += 23;
            }
            if (mouseY >= y && mouseY < y + ROW) {
                select(page);
                return true;
            }
            y += ROW;
        }
        return false;
    }

    private void select(WikiCatalog.Page page) {
        current = page;
        document = WikiMarkdown.parse(page.markdown());
        articleScroll = 0;
        if (compact()) showNavigation = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (expandedScreenshot != null) return true;
        int amount = (int) Math.round(vertical * 36);
        if ((!compact() || showNavigation) && mouseX < (compact() ? navWidth() + 8 : navWidth())) {
            int viewport = bodyBottom() - (compact() ? 109 : bodyTop() + 33) - 5;
            navScroll = Math.max(0, Math.min(Math.max(0, navHeight - viewport), navScroll - amount));
            return true;
        }
        if (!compact() || !showNavigation) {
            articleScroll = Math.max(0, Math.min(Math.max(0, articleHeight - (bodyBottom() - bodyTop())),
                    articleScroll - amount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
