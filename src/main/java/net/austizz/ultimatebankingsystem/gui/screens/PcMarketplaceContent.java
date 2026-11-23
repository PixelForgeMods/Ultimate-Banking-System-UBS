package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

final class PcMarketplaceContent implements PcAppContent {
    private static final int HEADER = 26;
    private static final int ROW = 22;
    private final List<PcDesktopScreen.App> apps;
    private final IntPredicate installed;
    private final IntConsumer launch;
    private final IntConsumer install;
    private final IntPredicate removable;
    private final IntConsumer remove;
    private int x;
    private int y;
    private int width;
    private int height;
    private int offset;
    private int selected;

    PcMarketplaceContent(List<PcDesktopScreen.App> apps, IntPredicate installed, IntConsumer launch, IntConsumer install,
                         IntPredicate removable, IntConsumer remove) {
        this.apps = apps;
        this.installed = installed;
        this.launch = launch;
        this.install = install;
        this.removable = removable;
        this.remove = remove;
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        revealSelection();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + width, y + height, 0xFF22252C);
        graphics.drawString(font, "Apps available on this client", x + 10, y + 9, 0xFFF5F7FC, false);
        if (apps.isEmpty()) {
            graphics.drawString(font, "No apps registered", x + 10, y + HEADER + 7, 0xFFC2C7D3, false);
        }
        for (int i = offset; i < Math.min(apps.size(), offset + visibleRows()); i++) {
            int top = y + HEADER + (i - offset) * ROW;
            int right = x + width - 8;
            boolean hovered = mouseY >= top && mouseY < top + ROW && mouseX >= x + 8 && mouseX < right;
            graphics.fill(x + 8, top, right, top + ROW - 2, i == selected || hovered ? 0xFF384154 : 0xFF2B303B);
            boolean canRemove = removable.test(i);
            int actionRight = canRemove ? right - 50 : right;
            PcDesktopScreen.drawAppIcon(graphics, apps.get(i), x + 14, top + 3, 14);
            String title = font.plainSubstrByWidth(apps.get(i).title().getString(), Math.max(1, actionRight - x - 75));
            graphics.drawString(font, title, x + 34, top + 6, 0xFFF5F7FC, false);
            String action = installed.test(i) ? "Open" : "Install";
            graphics.drawString(font, action, actionRight - font.width(action) - 7, top + 6, 0xFF74D4FF, false);
            if (canRemove) {
                graphics.drawString(font, "Remove", right - font.width("Remove") - 7, top + 6, 0xFFC2C7D3, false);
            }
        }
    }

    private int visibleRows() {
        return Math.max(1, (height - HEADER) / ROW);
    }

    private void revealSelection() {
        offset = Math.clamp(Math.clamp(offset, Math.max(0, selected - visibleRows() + 1), selected),
                0, Math.max(0, apps.size() - visibleRows()));
    }

    private void activate(int index) {
        if (installed.test(index)) {
            launch.accept(index);
        } else {
            install.accept(index);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = (int) Math.floor((mouseY - y - HEADER) / ROW);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= x + 8 && mouseX < x + width - 8
                && row >= 0 && row < visibleRows() && offset + row < apps.size()) {
            selected = offset + row;
            if (mouseX >= x + width - 58 && removable.test(selected)) {
                remove.accept(selected);
            } else {
                activate(selected);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        offset = Math.clamp(offset - (int) Math.signum(dy), 0, Math.max(0, apps.size() - visibleRows()));
        return dy != 0;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (apps.isEmpty()) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, apps.size() - 1);
            revealSelection();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            activate(selected);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE && removable.test(selected)) {
            remove.accept(selected);
            return true;
        }
        return false;
    }
}
