package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.austizz.ultimatebankingsystem.api.pc.PcAppServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

abstract class PcUtilityContent implements PcAppContent {
    static final int BACK = 0xFF1B2534, CARD = 0xFF263449, HOVER = 0xFF3C5470, LINE = 0xFF4A5D76;
    static final int TEXT = 0xFFF0F6FF, MUTED = 0xFFB3C4DB, ACCENT = 0xFF91DAF3, ERROR = 0xFFFFCEAD;
    record Hit(Rectangle rect, Runnable action) {}

    final Font font = Minecraft.getInstance().font;
    final List<Hit> hits = new ArrayList<>();
    PcAppServices services;
    int x, y, w, h;
    String status = "";

    @Override public void setServices(PcAppServices services) { this.services = services; }
    @Override public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.w = width;
        this.h = height;
    }

    void background(GuiGraphics g) {
        hits.clear();
        g.fill(x, y, x + w, y + h, BACK);
    }

    void label(GuiGraphics g, String value, int x, int y, int width, int color) {
        if (width <= 0 || value.isEmpty()) return;
        var text = Language.getInstance().getVisualOrder(font.substrByWidth(PcDesktopScreen.label(value), width));
        g.drawString(font, text, x, y, color, false);
    }

    void button(GuiGraphics g, int x, int y, int width, int height, String title, Runnable action,
                int mx, int my, boolean selected) {
        if (width <= 4 || height <= 4) return;
        var bounds = new Rectangle(x, y, width, height);
        if (action != null) hits.add(new Hit(bounds, action));
        int color = selected || action != null && bounds.contains(mx, my) ? HOVER : CARD;
        PcDesktopScreen.roundRect(g, x, y, width, height, color);
        label(g, title, x + 6, y + (height - 9) / 2, width - 10, action == null ? MUTED : TEXT);
        if (selected) g.fill(x + 3, y + height - 2, x + width - 3, y + height - 1, ACCENT);
    }

    EditBox field(String hint, int max) {
        var box = new EditBox(font, 0, 0, 80, 17, PcDesktopScreen.label(hint));
        box.setMaxLength(max);
        box.setBordered(false);
        box.setTextShadow(false);
        box.setHint(PcDesktopScreen.label(hint));
        box.setFormatter((value, offset) -> PcDesktopScreen.label(value).getVisualOrderText());
        return box;
    }

    void drawField(GuiGraphics g, EditBox box, int x, int y, int width, int mx, int my, float partial) {
        g.fill(x, y, x + width, y + 18, 0xFF101A27);
        g.fill(x, y + 17, x + width, y + 18, box.isFocused() ? ACCENT : LINE);
        box.setPosition(x + 4, y + 5);
        box.setWidth(Math.max(1, width - 8));
        hits.add(new Hit(new Rectangle(x, y, width, 18), () -> {
            box.setFocused(true);
            box.mouseClicked(mx, my, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }));
        box.render(g, mx, my, partial);
    }

    void status(GuiGraphics g) {
        g.fill(x, y + h - 18, x + w, y + h - 17, LINE);
        label(g, status, x + 8, y + h - 13, w - 16, MUTED);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (var hit : hits.reversed()) {
            if (hit.rect.contains(mx, my)) { hit.action.run(); return true; }
        }
        return false;
    }
}
