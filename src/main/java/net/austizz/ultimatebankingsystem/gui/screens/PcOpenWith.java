package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.util.List;

final class PcOpenWith {
    private static final int BACK = 0xFF202B3A, LINE = 0xFF4A6078, HILITE = 0xFF39516C;
    private static final int TEXT = 0xFFF1F6FF, MUTED = 0xFFACBFD8;
    record Choice(PcDesktopScreen.App app, boolean current) {}
    @FunctionalInterface interface Choose { void apply(PcDesktopScreen.App app, boolean always) throws java.io.IOException; }
    @FunctionalInterface interface Icon { void draw(GuiGraphics g, PcDesktopScreen.App app, int x, int y, int size); }
    private final String title, detail;
    private final List<Choice> options;
    private final boolean settings;
    private final Choose choose;
    private final Runnable close;
    private final Icon icon;
    private int selected, first, count, x, y, w, h;
    private String error = "";

    PcOpenWith(String title, String detail, List<Choice> options, boolean settings, Choose choose, Runnable close, Icon icon) {
        this.title = title;
        this.detail = detail;
        this.options = options;
        this.settings = settings;
        this.choose = choose;
        this.close = close;
        this.icon = icon;
        for (int i = 0; i < options.size(); i++) if (options.get(i).current) selected = i;
    }

    void render(GuiGraphics g, int width, int height, int mx, int my) {
        w = Math.min(276, width - 16);
        h = Math.min(height - 16, 99 + 29 * Math.min(6, Math.max(1, options.size())));
        x = (width - w) / 2;
        y = (height - h) / 2;
        count = Math.max(1, (h - 97) / 29);
        first = Math.clamp(first, 0, Math.max(0, options.size() - count));
        g.fill(0, 0, width, height, 0x93081120);
        PcDesktopScreen.roundRect(g, x, y, w, h, LINE);
        PcDesktopScreen.roundRect(g, x + 1, y + 1, w - 2, h - 2, BACK);
        label(g, title, x + 12, y + 11, w - 35, TEXT);
        label(g, detail, x + 12, y + 28, w - 24, MUTED);
        if (options.isEmpty()) label(g, "No installed app handles this file type.", x + 12, y + 51, w - 24, MUTED);
        for (int i = first; i < Math.min(first + count, options.size()); i++) {
            var row = row(i);
            var choice = options.get(i);
            button(g, row, "", mx, my, i == selected);
            icon.draw(g, choice.app, row.x + 7, row.y + 5, 16);
            label(g, choice.app.title().getString(), row.x + 31, row.y + 9, row.width - (choice.current ? 87 : 35), TEXT);
            if (choice.current) label(g, "Default", row.x + row.width - 48, row.y + 9, 47, MUTED);
        }
        if (options.size() > count) {
            int heightOfTrack = count * 29 - 2;
            int thumb = Math.max(7, heightOfTrack * count / options.size());
            int at = y + 46 + (heightOfTrack - thumb) * first / (options.size() - count);
            g.fill(x + w - 9, at, x + w - 7, at + thumb, MUTED);
        }
        label(g, error, x + 12, y + h - 47, w - 24, 0xFFFFCBAE);
        if (!options.isEmpty()) button(g, footer(0), settings ? "Set default" : "Always", mx, my, true);
        if (!settings && !options.isEmpty()) button(g, footer(1), "Just once", mx, my, false);
        button(g, footer(2), "Cancel", mx, my, false);
    }

    private Rectangle row(int index) { return new Rectangle(x + 10, y + 46 + (index - first) * 29, w - 24, 27); }
    private Rectangle footer(int index) {
        int slot = (w - 23) / 3;
        return new Rectangle(x + 10 + slot * index, y + h - 28, slot - 4, 19);
    }

    private static void label(GuiGraphics g, String text, int x, int y, int width, int color) {
        if (width <= 0 || text.isBlank()) return;
        var font = Minecraft.getInstance().font;
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(PcDesktopScreen.label(text), width)), x, y, color, false);
    }

    private static void button(GuiGraphics g, Rectangle button, String title, int mx, int my, boolean active) {
        PcDesktopScreen.roundRect(g, button.x, button.y, button.width, button.height,
                active || button.contains(mx, my) ? HILITE : 0xFF293648);
        label(g, title, button.x + 6, button.y + 5, button.width - 10, TEXT);
    }

    void click(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        for (int i = first; i < Math.min(first + count, options.size()); i++) if (row(i).contains(mx, my)) { selected = i; return; }
        if (footer(2).contains(mx, my)) close.run();
        else if (footer(0).contains(mx, my)) apply(true);
        else if (!settings && footer(1).contains(mx, my)) apply(false);
    }

    void scroll(double delta) { first = Math.clamp(first + (delta > 0 ? -1 : 1), 0, Math.max(0, options.size() - count)); }

    void key(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) close.run();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) apply(settings);
        else if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && !options.isEmpty()) {
            selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, options.size() - 1);
            if (selected < first) first = selected;
            if (selected >= first + count) first = selected - count + 1;
        }
    }

    private void apply(boolean always) {
        if (options.isEmpty()) return;
        try { choose.apply(options.get(selected).app, always); close.run(); }
        catch (java.io.IOException e) { error = e.getMessage() == null ? "Could not open the file." : e.getMessage(); }
    }
}
