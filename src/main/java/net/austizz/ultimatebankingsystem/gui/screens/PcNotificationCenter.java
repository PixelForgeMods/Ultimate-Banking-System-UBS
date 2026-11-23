package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

final class PcNotificationCenter {
    private static final int PANEL = 0xFF202735, CARD = 0xFF2A3445, RULE = 0xFF4C5B73;
    private static final int HOVER = 0xFF3B4962, TEXT = 0xFFF0F5FF, MUTED = 0xFFB5C5DE, ACCENT = 0xFF83D7F4;
    static final long BANNER_MS = 5000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    record Hit(Rectangle box, Runnable run) {}
    @FunctionalInterface interface Icon { void draw(GuiGraphics g, String app, int x, int y, int size); }

    private final PcNotifications model;
    private final PcFileSystem drive;
    private final Icon icon;
    private final Consumer<String> settings;
    private final Consumer<String> activate;
    private final Runnable save;
    private final Consumer<RuntimeException> error;
    private final Set<String> expanded = new HashSet<>();
    private final Set<Long> details = new HashSet<>();
    private final ArrayList<Hit> hits = new ArrayList<>();
    private final LinkedHashMap<java.util.UUID, PcPhotoTexture> images = new LinkedHashMap<>();
    private final Font font = Minecraft.getInstance().font;
    private boolean open;
    private long changedAt;
    private double from;
    private int scroll, maxScroll;
    private int keyboardFocus = -1;
    private YearMonth calendarMonth = YearMonth.now();
    private boolean calendarExpanded = true;
    private Rectangle historyBounds = new Rectangle();
    private Rectangle historyListBounds = new Rectangle();
    private Rectangle quietBounds = new Rectangle();
    private Rectangle replyBounds = new Rectangle();
    private Rectangle hover = new Rectangle();
    private int width, barY, mx, my;
    private long now, bannerStart, bannerUntil, bannerExit;
    private PcNotifications.Entry banner, replying;
    private EditBox reply;
    private Rectangle currentBounds = new Rectangle();

    PcNotificationCenter(PcNotifications model, PcFileSystem drive, Icon icon, Consumer<String> settings,
                         Consumer<String> activate, Runnable save, Consumer<RuntimeException> error) {
        this.model = model;
        this.drive = drive;
        this.icon = icon;
        this.settings = settings;
        this.activate = activate;
        this.save = save;
        this.error = error;
    }

    boolean open() { return open; }
    boolean over(double x, double y) { return hover.contains(x, y); }

    void toggle(long time) { setOpen(!open, time); }

    void setOpen(boolean value, long time) {
        if (open == value) return;
        from = openness(time);
        keyboardFocus = -1;
        changedAt = time;
        open = value;
        if (open) {
            model.seen();
            save.run();
            banner = null;
            model.clearBanners();
        }
        reply = null;
        replying = null;
    }

    static double easeOut(double t) { return 1 - Math.pow(1 - Math.clamp(t, 0, 1), 3); }

    private double openness(long time) {
        double destination = open ? 1 : 0;
        double progress = Math.clamp((time - changedAt) / (open ? 250.0 : 185.0), 0, 1);
        double eased = open ? easeOut(progress) : progress * progress;
        return from + (destination - from) * eased;
    }

    void suspend(long time) {
        setOpen(false, time);
        from = 0;
        banner = null;
        reply = null;
        replying = null;
        model.clearBanners();
        hover = new Rectangle();
        hits.clear();
    }

    void close(long time) {
        suspend(time);
        images.values().forEach(texture -> { if (texture != null) texture.close(); });
        images.clear();
    }

    void render(GuiGraphics g, int width, int barY, int mouseX, int mouseY, long now, boolean quiet) {
        this.width = width;
        this.barY = barY;
        mx = mouseX;
        my = mouseY;
        this.now = now;
        hits.clear();
        hover = new Rectangle();
        currentBounds = new Rectangle();
        replyBounds = new Rectangle();
        double progress = openness(now);
        if (progress > 0 || open) {
            int panelWidth = Math.min(250, width - 12);
            int left = width - panelWidth - 7 + (int) Math.round((panelWidth + 8) * (1 - progress));
            renderCenter(g, left, panelWidth, quiet);
        } else {
            renderBanner(g, quiet);
        }
    }

    private void renderBanner(GuiGraphics g, boolean quiet) {
        if (banner != null && !model.allowsBanner(banner, quiet)) {
            if (replying == banner) { replying = null; reply = null; }
            banner = null;
        }
        if (banner != null && bannerExit != 0 && now - bannerExit >= 160) banner = null;
        if (banner == null) {
            banner = model.nextBanner(quiet);
            bannerStart = now;
            bannerUntil = now + BANNER_MS;
            bannerExit = 0;
        }
        if (banner == null) return;
        int w = Math.min(230, width - 14);
        int h = height(banner, true, true);
        int baseX = width - w - 7, y = Math.max(4, barY - h - 8);
        var steady = new Rectangle(baseX, y, w, h);
        if (steady.contains(mx, my) || replying == banner) bannerUntil = now + BANNER_MS;
        if (now >= bannerUntil && bannerExit == 0) bannerExit = now;
        double offset = bannerExit != 0 ? Math.pow(Math.clamp((now - bannerExit) / 160.0, 0, 1), 2)
                : 1 - easeOut((now - bannerStart) / 230.0);
        int x = baseX + (int) ((w + 8) * offset);
        y += (int) (8 * offset);
        currentBounds = new Rectangle(x, y, w, h);
        hover = currentBounds;
        drawCard(g, banner, x, y, w, true, true);
    }

    private void renderCenter(GuiGraphics g, int x, int w, boolean quiet) {
        int top = 7, bottom = barY - 7;
        var today = LocalDate.now();
        int step = Math.clamp((barY * 43 / 100 - 80) / 6, 10, 20);
        boolean showMonth = calendarExpanded && barY >= 240;
        int calendarHeight = (showMonth ? 27 + 25 + 6 * step : 27) + 25;
        int calendarTop = bottom - calendarHeight;
        int historyBottom = calendarTop - 6;
        currentBounds = new Rectangle(x, top, w, bottom - top);
        historyBounds = new Rectangle(x, top, w, historyBottom - top);
        if (open) hover = currentBounds;
        panel(g, x, top, w, historyBottom - top, PANEL);
        var groups = model.groups();
        text(g, "Notifications", x + 10, top + 11, w - 102, TEXT);
        quietBounds = new Rectangle(x + w - 89, top + 5, 22, 19);
        var bell = quiet ? PcFilesIcon.BELL_OFF : PcFilesIcon.BELL;
        var mute = quietBounds;
        PcDesktopScreen.roundRect(g, mute.x, mute.y, mute.width, mute.height, quiet ? HOVER : CARD);
        iconButton(g, mute.x, mute.y, mute.width, mute.height, bell, () -> {
            model.manualQuiet = !model.manualQuiet;
            save.run();
        });
        if (!groups.isEmpty()) {
            PcDesktopScreen.roundRect(g, x + w - 62, top + 5, 54, 19, CARD);
            button(g, x + w - 62, top + 5, 54, 19, "Clear all", () -> {
                model.clear(null);
                expanded.clear();
                details.clear();
                replying = null;
                reply = null;
                save.run();
            }, false);
        }
        int listTop = top + 29, listBottom = historyBottom - 5;
        int listHeight = Math.max(0, listBottom - listTop);
        historyListBounds = new Rectangle(x, listTop, w, listHeight);
        int contentHeight = 0;
        for (var group : groups) {
            contentHeight += 24;
            for (var entry : group.entries().subList(0, shown(group))) {
                contentHeight += height(entry, cardExpanded(entry), false) + 4;
            }
            if (group.entries().size() > 1) contentHeight += 21;
        }
        maxScroll = Math.max(0, contentHeight - listHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        if (listHeight > 0) {
            g.enableScissor(x, listTop, x + w, listBottom);
            int pos = listTop - scroll;
            int begin = hits.size();
            if (groups.isEmpty()) text(g, "You're all caught up", x + 12, listTop + 20, w - 22, MUTED);
            for (var group : groups) {
                if (pos + 23 >= listTop && pos < listBottom) {
                    icon.draw(g, group.app(), x + 11, pos + 4, 13);
                    text(g, group.name(), x + 29, pos + 7, w - 94, TEXT);
                    button(g, x + w - 56, pos, 24, 20, "•••", () -> openSettings(group.app()), false);
                    iconButton(g, x + w - 29, pos, 21, 20, PcFilesIcon.CLOSE, () -> {
                        model.clear(group.app());
                        expanded.remove(group.app());
                        group.entries().forEach(entry -> details.remove(entry.id));
                        if (replying != null && replying.app.equals(group.app())) {
                            reply = null;
                            replying = null;
                        }
                        save.run();
                    });
                }
                pos += 24;
                for (var entry : group.entries().subList(0, shown(group))) {
                    boolean showDetails = cardExpanded(entry);
                    int h = height(entry, showDetails, false);
                    if (pos + h >= listTop && pos < listBottom) drawCard(g, entry, x + 5, pos, w - 13, showDetails, false);
                    pos += h + 4;
                }
                int more = group.entries().size() - 1;
                if (more > 0) {
                    if (pos + 18 >= listTop && pos < listBottom) {
                        String label = expanded.contains(group.app()) ? "Show fewer"
                                : "+" + more + (more == 1 ? " notification" : " notifications");
                        int buttonWidth = Math.min(w - 30, font.width(PcDesktopScreen.label(label)) + 13);
                        PcDesktopScreen.roundRect(g, x + 11, pos, buttonWidth, 17, CARD);
                        button(g, x + 11, pos, buttonWidth, 17, label, () -> {
                            if (!expanded.remove(group.app())) expanded.add(group.app());
                        }, false);
                    }
                    pos += 21;
                }
            }
            // Scrolled controls cannot be activated through the header or Focus strip.
            for (int i = hits.size() - 1; i >= begin; i--) {
                var hit = hits.get(i);
                var clipped = hit.box.intersection(historyListBounds);
                if (clipped.isEmpty()) hits.remove(i); else hits.set(i, new Hit(clipped, hit.run));
            }
            g.disableScissor();
            if (maxScroll > 0) {
                int thumb = Math.min(listHeight, Math.max(9, listHeight * listHeight / Math.max(1, contentHeight)));
                int at = listTop + (listHeight - thumb) * scroll / maxScroll;
                g.fill(x + w - 5, at, x + w - 3, at + thumb, MUTED);
            }
        }
        panel(g, x, calendarTop, w, calendarHeight, PANEL);
        text(g, today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), x + 10, calendarTop + 10, w - 43, TEXT);
        if (barY >= 240) {
            iconButton(g, x + w - 28, calendarTop + 4, 20, 19,
                    showMonth ? PcFilesIcon.DOWN : PcFilesIcon.UP, () -> calendarExpanded = !calendarExpanded);
        }
        if (showMonth) renderCalendar(g, x + 5, calendarTop + 27, w - 10, today, step);

        int footer = bottom - 25;
        boolean focus = model.focus(now);
        long seconds = focus ? (model.focusUntil - now + 999) / 1000 : 0;
        String timer = focus ? String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60) : model.focusMinutes + " min";
        g.fill(x + 1, footer, x + w - 1, footer + 1, RULE);
        if (focus) {
            text(g, timer, x + 10, footer + 8, w - 86, TEXT);
        } else {
            button(g, x + 9, footer + 3, 19, 18, "−", () -> changeFocus(-5), false);
            text(g, timer, x + 34, footer + 8, 51, TEXT);
            button(g, x + 87, footer + 3, 19, 18, "+", () -> changeFocus(5), false);
        }
        button(g, x + w - 69, footer + 3, 60, 18, focus ? "End" : "Focus", () -> {
            model.focusUntil = model.focus(now) ? 0 : now + model.focusMinutes * 60000L;
            save.run();
        }, true);
        if (!open) hits.clear();
        if (open && keyboardFocus >= 0 && !hits.isEmpty()) {
            keyboardFocus = Math.min(keyboardFocus, hits.size() - 1);
            var box = hits.get(keyboardFocus).box;
            g.renderOutline(box.x, box.y, box.width, box.height, ACCENT);
        }
    }

    private void renderCalendar(GuiGraphics g, int x, int y, int w, LocalDate today, int step) {
        text(g, calendarMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")), x + 5, y + 4, w - 65, TEXT);
        iconButton(g, x + w - 49, y, 21, 16, PcFilesIcon.UP, () -> calendarMonth = calendarMonth.minusMonths(1));
        iconButton(g, x + w - 26, y, 21, 16, PcFilesIcon.DOWN, () -> calendarMonth = calendarMonth.plusMonths(1));
        int colWidth = (w - 8) / 7;
        var days = new String[]{"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (int col = 0; col < days.length; col++) {
            centered(g, days[col], x + 4 + col * colWidth, y + 15, colWidth, MUTED);
        }
        var first = calendarMonth.atDay(1);
        var weekStart = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        for (int i = 0; i < 42; i++) {
            var day = weekStart.plusDays(i);
            int cellX = x + 4 + i % 7 * colWidth, cellY = y + 25 + i / 7 * step;
            boolean selected = day.equals(today);
            int dot = Math.min(18, step);
            if (selected) PcDesktopScreen.roundRect(g, cellX + (colWidth - dot) / 2, cellY, dot, dot, ACCENT);
            centered(g, Integer.toString(day.getDayOfMonth()), cellX, cellY + (step - 9) / 2, colWidth,
                    selected ? PANEL : day.getMonth() == calendarMonth.getMonth() ? TEXT : 0xFF8693AA);
        }
    }

    private void centered(GuiGraphics g, String label, int x, int y, int w, int color) {
        text(g, label, x + (w - font.width(PcDesktopScreen.label(label))) / 2, y, w, color);
    }

    private boolean cardExpanded(PcNotifications.Entry entry) {
        return (entry.payload.image() != null) != details.contains(entry.id);
    }

    private int shown(PcNotifications.Group group) { return expanded.contains(group.app()) ? group.entries().size() : 1; }

    private void changeFocus(int delta) {
        model.focusMinutes = Math.clamp(model.focusMinutes + delta, 5, 120);
        save.run();
    }

    private boolean hasActions(PcNotifications.Entry e) {
        return !e.target.isBlank() || !e.payload.actions().isEmpty() || e.payload.reply() != null;
    }

    private int height(PcNotifications.Entry e, boolean expanded, boolean toast) {
        return textHeight(e, expanded, toast)
                + previewHeight(e, expanded, toast)
                + (expanded && e.payload.progress() != -1 ? 15 : 0)
                + (expanded && hasActions(e) ? 21 : 0)
                + (expanded && e == replying ? 26 : 0);
    }

    private int textHeight(PcNotifications.Entry entry, boolean expanded, boolean toast) {
        if (toast) return 55;
        if (!expanded) return 45;
        int room = Math.max(1, currentBounds.width - 33);
        int lines = Math.min(3, font.split(PcDesktopScreen.label(entry.payload.body()), room).size());
        return 41 + 10 * lines;
    }

    private int previewHeight(PcNotifications.Entry entry, boolean expanded, boolean toast) {
        return !toast && expanded && entry.payload.image() != null ? Math.clamp(barY / 3, 58, 140) : 0;
    }

    private void drawCard(GuiGraphics g, PcNotifications.Entry e, int x, int y, int w, boolean expanded, boolean toast) {
        int h = height(e, expanded, toast);
        panel(g, x, y, w, h, CARD);
        if (!e.target.isBlank()) hits.add(new Hit(new Rectangle(x, y, w, h), () -> {
            run(() -> activate.accept(e.target));
            remove(e);
        }));
        int line = y + 8, bodyX = x + 10, room = w - 20;
        if (toast) {
            icon.draw(g, e.app, x + 8, y + 6, 12);
            text(g, e.appName, x + 24, line - 1, w - 72, MUTED);
            line += 17;
        }
        int dismissY = y + 3;
        if (!toast) {
            text(g, TIME.format(Instant.ofEpochMilli(e.time)), bodyX, y + 8, 45, MUTED);
            iconButton(g, bodyX + 34, dismissY, 19, 16, expanded ? PcFilesIcon.UP : PcFilesIcon.DOWN, () -> {
                if (!details.remove(e.id)) details.add(e.id);
            });
            line += 17;
        }
        if (toast || new Rectangle(x, y, w, h).contains(mx, my)) {
            iconButton(g, x + w - 21, dismissY, 17, 16, PcFilesIcon.CLOSE, () -> remove(e));
        } else if (!toast) {
            hits.add(new Hit(new Rectangle(x + w - 21, dismissY, 17, 16), () -> remove(e)));
        }
        text(g, e.payload.title(), bodyX, line, room, TEXT);
        if (toast && e.payload.image() != null) {
            var preview = image(e.payload.image());
            if (preview != null) preview.drawFitted(g, x + w - 46, line + 10, 35, 21);
            room -= 40;
        }
        if (toast || expanded) {
            var lines = font.split(PcDesktopScreen.label(e.payload.body()), Math.max(1, room));
            for (int i = 0; i < Math.min(lines.size(), expanded && !toast ? 3 : 1); i++) {
                g.drawString(font, lines.get(i), bodyX, line + 12 + i * 10, MUTED, false);
            }
        }
        int bottom = y + textHeight(e, expanded, toast);
        int previewHeight = previewHeight(e, expanded, toast);
        if (previewHeight > 0) {
            var preview = image(e.payload.image());
            if (preview != null) {
                g.fill(x + 1, bottom, x + w - 1, bottom + previewHeight - 3, 0xFF141B28);
                preview.drawFitted(g, x + 1, bottom, w - 2, previewHeight - 3);
            }
            bottom += previewHeight;
        }
        int progress = e.payload.progress();
        if (expanded && progress != -1) {
            g.fill(bodyX, bottom + 4, x + w - 10, bottom + 7, RULE);
            int available = w - 20;
            if (progress >= 0) g.fill(bodyX, bottom + 4, bodyX + available * progress / 100, bottom + 7, ACCENT);
            else {
                int segment = Math.max(5, available / 4);
                int at = (int) ((now % 1200) * (available - segment) / 1200);
                g.fill(bodyX + at, bottom + 4, bodyX + at + segment, bottom + 7, ACCENT);
            }
            bottom += 15;
        }
        if (expanded && hasActions(e)) {
            var actions = new ArrayList<PcAppNotification.Action>();
            if (!e.target.isBlank()) actions.add(new PcAppNotification.Action("Open", () -> activate.accept(e.target)));
            actions.addAll(e.payload.actions());
            if (e.payload.reply() != null) actions.add(new PcAppNotification.Action("Reply", () -> beginReply(e)));
            int step = (w - 18) / actions.size();
            for (int i = 0; i < actions.size(); i++) {
                var action = actions.get(i);
                boolean isReply = e.payload.reply() != null && i == actions.size() - 1;
                int actionX = x + 9 + step * i;
                PcDesktopScreen.roundRect(g, actionX, bottom - 1, step - 3, 18, PANEL);
                button(g, actionX, bottom - 1, step - 3, 18, action.title(), () -> {
                    if (isReply) run(action.run());
                    else { run(action.run()); remove(e); }
                }, false);
            }
            bottom += 21;
        }
        if (expanded && e == replying && reply != null) {
            PcDesktopScreen.roundRect(g, bodyX, bottom, w - 58, 19, PANEL);
            g.fill(bodyX + 2, bottom + 18, x + w - 50, bottom + 19, ACCENT);
            reply.setPosition(bodyX + 4, bottom + 5);
            reply.setWidth(w - 64);
            replyBounds = new Rectangle(bodyX, bottom, w - 58, 19);
            if (!toast) replyBounds = replyBounds.intersection(historyListBounds);
            reply.render(g, mx, my, 0);
            button(g, x + w - 47, bottom, 39, 19, "Send", this::sendReply, true);
        }
    }

    private PcPhotoTexture image(java.util.UUID id) {
        if (!images.containsKey(id)) {
            if (images.size() >= 8) {
                var evicted = images.pollFirstEntry().getValue();
                if (evicted != null) evicted.close();
            }
            PcPhotoTexture texture = null;
            try {
                var entry = drive.get(id);
                if (PcPhotosContent.canOpen(entry)) texture = PcPhotoTexture.upload(PcPhotoImage.decode(entry.payload(), 720).pixels());
            } catch (IOException ignored) {}
            images.put(id, texture);
        }
        return images.get(id);
    }

    private void remove(PcNotifications.Entry entry) {
        if (replying == entry) { replying = null; reply = null; }
        if (banner == entry) banner = null;
        model.dismiss(entry);
        save.run();
    }

    private void openSettings(String app) {
        setOpen(false, now);
        settings.accept(app);
    }

    private void beginReply(PcNotifications.Entry e) {
        replying = e;
        reply = new EditBox(font, 0, 0, 100, 18, PcDesktopScreen.label(e.payload.reply().hint()));
        reply.setHint(PcDesktopScreen.label(e.payload.reply().hint()));
        reply.setBordered(false);
        reply.setTextShadow(false);
        reply.setTextColor(TEXT);
        reply.setMaxLength(240);
        reply.setFocused(true);
    }

    private void sendReply() {
        if (reply == null || reply.getValue().isBlank()) return;
        var entry = replying;
        String value = reply.getValue();
        if (entry.payload.reply() != null) run(() -> entry.payload.reply().submit().accept(value));
        remove(entry);
    }

    boolean click(double x, double y, int button, long time) {
        if (!open && !over(x, y)) return false;
        if (open && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && quietBounds.contains(x, y)) {
            openSettings(null);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (reply != null && replyBounds.contains(x, y)) { reply.mouseClicked(x, y, button); return true; }
        keyboardFocus = -1;
        for (var hit : hits.reversed()) {
            if (hit.box.contains(x, y)) { hit.run.run(); return true; }
        }
        if (open && !currentBounds.contains(x, y)) {
            setOpen(false, time);
        } else if (!open && banner != null && !banner.target.isBlank()) {
            var entry = banner;
            activate.accept(entry.target);
            remove(entry);
        }
        return true;
    }

    boolean scroll(double x, double y, double delta) {
        if (!open || !over(x, y)) return false;
        if (!historyBounds.contains(x, y)) return true;
        scroll = Math.clamp(scroll - (int) Math.signum(delta) * 36, 0, maxScroll);
        return true;
    }

    boolean key(int key, int scan, int modifiers, long time) {
        if (!open && reply == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (reply != null) { replying = null; reply = null; }
            else setOpen(false, time);
        } else if (reply != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) sendReply();
            else reply.keyPressed(key, scan, modifiers);
        } else if (key == GLFW.GLFW_KEY_TAB && !hits.isEmpty()) {
            boolean backward = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            keyboardFocus = keyboardFocus < 0 ? (backward ? hits.size() - 1 : 0)
                    : Math.floorMod(keyboardFocus + (backward ? -1 : 1), hits.size());
        } else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)
                && keyboardFocus >= 0 && keyboardFocus < hits.size()) {
            hits.get(keyboardFocus).run.run();
        } else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            scroll = Math.clamp(scroll + (key == GLFW.GLFW_KEY_DOWN ? 24 : -24), 0, maxScroll);
        }
        return true;
    }

    boolean typed(char c, int modifiers) { return reply != null ? reply.charTyped(c, modifiers) : open; }

    private void run(Runnable task) {
        try { task.run(); } catch (RuntimeException e) { error.accept(e); }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String title, Runnable onClick, boolean active) {
        var box = new Rectangle(x, y, w, h);
        hits.add(new Hit(box, onClick));
        if (box.contains(mx, my) || active) PcDesktopScreen.roundRect(g, x, y, w, h, HOVER);
        text(g, title, x + 5, y + (h - 9) / 2, w - 9, active ? ACCENT : TEXT);
    }

    private void iconButton(GuiGraphics g, int x, int y, int w, int h, PcFilesIcon glyph, Runnable onClick) {
        button(g, x, y, w, h, "", onClick, false);
        glyph.draw(g, x + (w - 10) / 2, y + (h - 10) / 2, 10, MUTED);
    }

    private void text(GuiGraphics g, String value, int x, int y, int room, int color) {
        if (room <= 0) return;
        var clipped = font.substrByWidth(PcDesktopScreen.label(value), room);
        g.drawString(font, Language.getInstance().getVisualOrder(clipped), x, y, color, false);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int tint) {
        PcDesktopScreen.roundRect(g, x, y, w, h, RULE);
        PcDesktopScreen.roundRect(g, x + 1, y + 1, w - 2, h - 2, tint);
    }
}
