package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class PcSettingsContent implements PcAppContent {
    private static final int ROW_HEIGHT = 23;
    private record Row(String title, String value, Runnable apply) {}

    private final PcDesktopWindows<?> windows;
    private final Runnable save;
    private final List<PcDesktopScreen.App> apps;
    private final java.util.function.Supplier<List<String>> extensions;
    private final java.util.function.Function<String, String> defaultApp;
    private final java.util.function.Consumer<String> chooseApp;
    private int x, y, width, height, scroll, selected = -1;
    private enum Page { APPEARANCE, NOTIFICATIONS, DEFAULT_APPS }
    private Page page = Page.APPEARANCE;
    private String appId;

    PcSettingsContent(PcDesktopWindows<?> windows, Runnable save, Collection<PcDesktopScreen.App> apps,
                      java.util.function.Supplier<List<String>> extensions,
                      java.util.function.Function<String, String> defaultApp, java.util.function.Consumer<String> chooseApp) {
        this.windows = windows;
        this.save = save;
        this.apps = List.copyOf(apps);
        this.extensions = extensions;
        this.defaultApp = defaultApp;
        this.chooseApp = chooseApp;
    }

    void openNotifications(String id) {
        selectPage(Page.NOTIFICATIONS);
        appId = id;
        scroll = 0;
        selected = -1;
    }

    void openDefaults(String extension) {
        selectPage(Page.DEFAULT_APPS);
        if (extension != null) {
            int index = extensions.get().indexOf(extension);
            if (index >= 0) {
                selected = index + 1;
                scroll = selected * ROW_HEIGHT;
            }
        }
    }

    void openAppearance() { selectPage(Page.APPEARANCE); }

    private void selectPage(Page page) {
        this.page = page;
        appId = null;
        scroll = 0;
        selected = -1;
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private List<Row> rows() {
        var rows = new ArrayList<Row>();
        var model = windows.notifications;
        if (page == Page.DEFAULT_APPS) {
            rows.add(new Row("Set defaults by file type", "", null));
            for (var extension : extensions.get()) {
                rows.add(new Row(extension.isEmpty() ? "No extension" : "." + extension, defaultApp.apply(extension),
                        () -> chooseApp.accept(extension)));
            }
        } else if (page == Page.APPEARANCE) {
            rows.add(new Row("File icon theme", windows.darkFileIcons ? "Dark" : "Light",
                    () -> windows.darkFileIcons = !windows.darkFileIcons));
        } else if (appId != null) {
            String id = appId;
            var app = model.app(id);
            var title = apps.stream().filter(item -> item.id().toString().equals(id))
                    .findFirst().map(item -> item.title().getString()).orElse(id);
            rows.add(new Row("All notifications", "Back", () -> openNotifications(null)));
            rows.add(new Row(title, "", null));
            toggle(rows, "Notifications", app.enabled, () -> app.enabled = !app.enabled);
            toggle(rows, "Show banners", app.banners, () -> app.banners = !app.banners);
            toggle(rows, "Show in Notification Center", app.history, () -> app.history = !app.history);
            toggle(rows, "Play a sound", app.sound, () -> app.sound = !app.sound);
            toggle(rows, "Show taskbar badges", app.badge, () -> app.badge = !app.badge);
            toggle(rows, "Hide details on lock screen", app.hideLockContent, () -> app.hideLockContent = !app.hideLockContent);
            toggle(rows, "Allow during Do Not Disturb", app.quietException, () -> app.quietException = !app.quietException);
            var values = PcNotifications.Priority.values();
            String name = app.priority.name().toLowerCase(Locale.ROOT);
            rows.add(new Row("Notification Center priority", Character.toUpperCase(name.charAt(0)) + name.substring(1),
                    () -> model.priority(id, values[(app.priority.ordinal() + 1) % values.length])));
        } else {
            toggle(rows, "Notifications", model.enabled, () -> model.enabled = !model.enabled);
            toggle(rows, "Play notification sounds", model.sound, () -> model.sound = !model.sound);
            toggle(rows, "Show on lock screen", model.lockScreen, () -> model.lockScreen = !model.lockScreen);
            toggle(rows, "Hide lock screen details", model.hideLockContent, () -> model.hideLockContent = !model.hideLockContent);
            toggle(rows, "Show calls on lock screen", model.lockCalls, () -> model.lockCalls = !model.lockCalls);
            rows.add(new Row("Do Not Disturb", "", null));
            toggle(rows, "Do Not Disturb", model.manualQuiet, () -> model.manualQuiet = !model.manualQuiet);
            toggle(rows, "Turn on daily", model.scheduled, () -> model.scheduled = !model.scheduled);
            if (model.scheduled) {
                rows.add(new Row("Quiet hours start", hour(model.scheduleStart), () -> model.scheduleStart = (model.scheduleStart + 1) % 24));
                rows.add(new Row("Quiet hours end", hour(model.scheduleEnd), () -> model.scheduleEnd = (model.scheduleEnd + 1) % 24));
            }
            toggle(rows, "Quiet during Photos presentation", model.presentationQuiet,
                    () -> model.presentationQuiet = !model.presentationQuiet);
            toggle(rows, "Allow incoming calls", model.allowCalls, () -> model.allowCalls = !model.allowCalls);
            toggle(rows, "Allow reminders", model.allowReminders, () -> model.allowReminders = !model.allowReminders);
            rows.add(new Row("Focus", "", null));
            boolean active = model.focus(System.currentTimeMillis());
            rows.add(new Row("Focus session", active ? "End" : "Start " + model.focusMinutes + " min",
                    () -> model.focusUntil = active ? 0 : System.currentTimeMillis() + model.focusMinutes * 60_000L));
            toggle(rows, "Turn on Do Not Disturb", model.focusQuiet, () -> model.focusQuiet = !model.focusQuiet);
            toggle(rows, "Hide taskbar badges", model.focusHideBadges, () -> model.focusHideBadges = !model.focusHideBadges);
            rows.add(new Row("App notifications", "", null));
            for (var app : apps) {
                String id = app.id().toString();
                rows.add(new Row(app.title().getString(), "Settings", () -> openNotifications(id)));
            }
        }
        return rows;
    }

    private static String hour(int value) { return String.format(Locale.ROOT, "%02d:00", value); }

    private static void toggle(List<Row> rows, String title, boolean on, Runnable run) {
        rows.add(new Row(title, on ? "On" : "Off", run));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        int left = x + 8, right = x + width - 8, top = y + 35, bottom = y + height - 6;
        if (right <= left || bottom <= top) return;
        int third = (right - left) / 3;
        tab(g, "Appearance", left, left + third - 2, page == Page.APPEARANCE);
        tab(g, "Notifications", left + third + 1, left + 2 * third - 2, page == Page.NOTIFICATIONS);
        tab(g, "Default apps", left + 2 * third + 1, right, page == Page.DEFAULT_APPS);
        var rows = rows();
        scroll = Math.clamp(scroll, 0, maxScroll(rows.size()));
        g.enableScissor(left, top, right, bottom);
        for (int i = 0; i < rows.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT <= top || rowY >= bottom) continue;
            var row = rows.get(i);
            if (row.apply != null) {
                boolean hover = mx >= left && mx < right && my >= top && my < bottom && my >= rowY && my < rowY + ROW_HEIGHT;
                g.fill(left, rowY, right, rowY + ROW_HEIGHT - 2, hover || i == selected ? 0xFF3B4962 : 0xFF293344);
            }
            int valueWidth = Minecraft.getInstance().font.width(PcDesktopScreen.label(row.value));
            draw(g, row.title, left + 8, rowY + 7, right - left - valueWidth - 20, row.apply == null ? 0xFFB5C5DE : 0xFFF0F5FF);
            draw(g, row.value, right - valueWidth - 7, rowY + 7, valueWidth, 0xFF83D7F4);
        }
        g.disableScissor();
        int overflow = maxScroll(rows.size());
        if (overflow > 0) {
            int railHeight = bottom - top;
            int thumb = Math.min(railHeight, Math.max(10, railHeight * railHeight / (rows.size() * ROW_HEIGHT)));
            int thumbY = top + (railHeight - thumb) * scroll / overflow;
            g.fill(right - 2, thumbY, right, thumbY + thumb, 0xFFB5C5DE);
        }
    }

    private void tab(GuiGraphics g, String title, int left, int right, boolean active) {
        g.fill(left, y + 8, right, y + 30, active ? 0xFF3B4962 : 0xFF202735);
        if (active) g.fill(left, y + 29, right, y + 30, 0xFF83D7F4);
        draw(g, title, left + 8, y + 16, right - left - 16, 0xFFF0F5FF);
    }

    private static void draw(GuiGraphics g, String title, int x, int y, int room, int color) {
        if (title.isEmpty() || room <= 0) return;
        var font = Minecraft.getInstance().font;
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(PcDesktopScreen.label(title), room)),
                x, y, color, false);
    }

    private int maxScroll(int rows) { return Math.max(0, rows * ROW_HEIGHT - Math.max(0, height - 41)); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || mx < x + 8 || mx >= x + width - 8) return false;
        if (my >= y + 8 && my < y + 30) {
            int third = Math.max(1, (width - 16) / 3);
            selectPage(Page.values()[Math.min(2, ((int) mx - x - 8) / third)]);
            return true;
        }
        if (my < y + 35 || my >= y + height - 6) return false;
        int index = ((int) my - y - 35 + scroll) / ROW_HEIGHT;
        activate(rows(), index);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < x || mx >= x + width || my < y || my >= y + height || dy == 0) return false;
        scroll = Math.clamp(scroll - (dy > 0 ? ROW_HEIGHT * 2 : -ROW_HEIGHT * 2), 0, maxScroll(rows().size()));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        var rows = rows();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, rows.size() - 1);
            if (selected * ROW_HEIGHT < scroll) scroll = selected * ROW_HEIGHT;
            int room = Math.max(0, height - 41);
            if ((selected + 1) * ROW_HEIGHT > scroll + room) scroll = Math.min(maxScroll(rows.size()), (selected + 1) * ROW_HEIGHT - room);
            return true;
        }
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER && key != GLFW.GLFW_KEY_SPACE) return false;
        activate(rows, selected < 0 ? 0 : selected);
        return true;
    }

    private void activate(List<Row> rows, int index) {
        if (index < 0 || index >= rows.size() || rows.get(index).apply == null) return;
        selected = index;
        rows.get(index).apply.run();
        save.run();
    }
}
