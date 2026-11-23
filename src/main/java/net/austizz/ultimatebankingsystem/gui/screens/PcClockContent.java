package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class PcClockContent extends PcUtilityContent {
    private enum Page { TIMERS, ALARMS, STOPWATCH, WORLD, FOCUS }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM");
    private final PcClock clock;
    private final PcNotifications notifications;
    private final Runnable save;
    private Page page = Page.TIMERS;
    private int first;
    private boolean form;
    private EditBox title, value;
    private PcClock.Timer editingTimer;
    private PcClock.Alarm editingAlarm;
    private PcClock.Repeat repeat = PcClock.Repeat.ONCE;

    PcClockContent(PcClock clock, PcNotifications notifications, Runnable save) {
        this.clock = clock;
        this.notifications = notifications;
        this.save = save;
    }

    private void page(Page target) {
        page = target;
        first = 0;
        form = false;
        status = "";
    }

    private void changed() { save.run(); }

    private void begin() { begin(null, null); }

    private void begin(PcClock.Timer timer, PcClock.Alarm alarm) {
        editingTimer = timer;
        editingAlarm = alarm;
        form = true;
        status = "";
        title = field("Label", 45);
        value = field(switch (page) {
            case TIMERS -> "Minutes or HH:MM:SS";
            case ALARMS -> "Time  HH:MM";
            default -> "Time zone, e.g. Europe/Amsterdam";
        }, 64);
        if (page == Page.TIMERS) {
            value.setValue(timer == null ? "00:05:00" : time(timer.duration, false));
            if (timer != null) title.setValue(timer.title);
        }
        if (page == Page.ALARMS) {
            value.setValue((alarm == null ? LocalTime.now().plusHours(1) : alarm.time).format(TIME));
            if (alarm != null) title.setValue(alarm.title);
            repeat = alarm == null ? PcClock.Repeat.ONCE : alarm.repeat;
        }
        var focused = page == Page.WORLD ? value : title;
        focused.setFocused(true);
        focused.moveCursorToEnd(false);
        focused.setHighlightPos(0);
    }

    private void submit() {
        String name = title.getValue().strip();
        String input = value.getValue().strip();
        try {
            boolean added = switch (page) {
                case TIMERS -> {
                    long length = duration(input);
                    String label = name.isEmpty() ? "Timer" : name;
                    if (editingTimer == null) yield clock.addTimer(new PcClock.Timer(label, length));
                    editingTimer.title = label;
                    if (editingTimer.duration != length) {
                        editingTimer.duration = length;
                        editingTimer.reset();
                    }
                    yield true;
                }
                case ALARMS -> {
                    if (!input.matches("[0-9]{1,2}:[0-9]{2}")) throw new IllegalArgumentException("Enter a time like 07:30.");
                    var parts = input.split(":");
                    var at = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    String label = name.isEmpty() ? "Alarm" : name;
                    if (editingAlarm == null) {
                        var alarm = new PcClock.Alarm(label, at, repeat);
                        alarm.toggle(System.currentTimeMillis(), ZoneId.systemDefault());
                        yield clock.addAlarm(alarm);
                    }
                    editingAlarm.title = label;
                    if (!editingAlarm.time.equals(at) || editingAlarm.repeat != repeat) {
                        boolean enabled = editingAlarm.next > 0 || editingAlarm.snooze > 0;
                        editingAlarm.time = at;
                        editingAlarm.repeat = repeat;
                        editingAlarm.next = enabled ? PcClock.nextAlarm(at, repeat, System.currentTimeMillis(), ZoneId.systemDefault()) : 0;
                        editingAlarm.snooze = 0;
                    }
                    yield true;
                }
                case WORLD -> clock.addZone(ZoneId.of(input));
                default -> false;
            };
            if (!added) { status = "This Clock list is full or already contains the entry."; return; }
            changed();
            form = false;
            first = Math.max(0, count() - capacity());
            status = editingTimer == null && editingAlarm == null ? "Added." : "Updated.";
        } catch (IllegalArgumentException | DateTimeException e) {
            status = e.getMessage() == null ? "Check the time or time zone." : e.getMessage();
        }
    }

    static long duration(String input) {
        if (!input.matches("[0-9]{1,4}(:[0-9]{2}){0,2}")) throw new IllegalArgumentException("Enter minutes or HH:MM:SS.");
        var parts = input.split(":");
        long seconds = Long.parseLong(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            int sub = Integer.parseInt(parts[i]);
            if (sub > 59) throw new IllegalArgumentException("Minutes and seconds must be below 60.");
            seconds = seconds * 60 + sub;
        }
        if (parts.length == 1) seconds *= 60;
        if (seconds < 1 || seconds > 86_400) throw new IllegalArgumentException("Choose between 1 second and 24 hours.");
        return seconds * 1000;
    }

    private int count() {
        return switch (page) {
            case TIMERS -> clock.timers.size();
            case ALARMS -> clock.alarms.size();
            case WORLD -> clock.zones.size();
            case STOPWATCH -> clock.laps.size();
            case FOCUS -> 0;
        };
    }

    private int capacity() { return Math.max(1, (h - 72) / 30); }
    private static String time(long millis, boolean round) {
        long seconds = (millis + (round ? 999 : 0)) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        background(g);
        String[] names = {"Timers", "Alarms", "Stopwatch", "World clock", "Focus"};
        int tab = (w - 12) / names.length;
        for (var target : Page.values()) {
            int index = target.ordinal();
            button(g, x + 6 + index * tab, y + 6, tab - 3, 20, names[index], () -> page(target), mx, my, page == target);
        }
        long now = System.currentTimeMillis();
        switch (page) {
            case TIMERS, ALARMS, WORLD -> renderList(g, mx, my, now);
            case STOPWATCH -> renderStopwatch(g, mx, my, stopwatchTime());
            case FOCUS -> renderFocus(g, mx, my, now);
        }
        status(g);
        if (form) renderForm(g, mx, my, partial);
    }

    private void renderList(GuiGraphics g, int mx, int my, long now) {
        button(g, x + w - 82, y + 32, 73, 18, page == Page.WORLD ? "Add city" : "Add", this::begin, mx, my, false);
        String heading = switch (page) {
            case TIMERS -> "Countdown timers";
            case ALARMS -> "Scheduled alarms";
            default -> "Current times";
        };
        label(g, heading, x + 10, y + 39, w - 110, ACCENT);
        if (count() == 0) label(g, "Nothing scheduled", x + 10, y + 70, w - 20, MUTED);
        first = Math.clamp(first, 0, Math.max(0, count() - capacity()));
        for (int i = first; i < Math.min(count(), first + capacity()); i++) {
            int yy = y + 54 + (i - first) * 30;
            PcDesktopScreen.roundRect(g, x + 8, yy, w - 17, 26, CARD);
            if (page == Page.TIMERS) {
                var timer = clock.timers.get(i);
                hits.add(new Hit(new java.awt.Rectangle(x + 9, yy, w - 155, 26), () -> begin(timer, null)));
                label(g, timer.title, x + 15, yy + 5, w - 171, TEXT);
                label(g, time(timer.remaining(now), true), x + 15, yy + 16, w - 160, ACCENT);
                button(g, x + w - 145, yy + 3, 52, 19, timer.until == 0 ? "Start" : "Pause", () -> {
                    timer.startPause(System.currentTimeMillis()); changed();
                }, mx, my, false);
                button(g, x + w - 89, yy + 3, 42, 19, "Reset", () -> { timer.reset(); changed(); }, mx, my, false);
                button(g, x + w - 41, yy + 3, 28, 19, "×", () -> { clock.timers.remove(timer); changed(); }, mx, my, false);
            } else if (page == Page.ALARMS) {
                var alarm = clock.alarms.get(i);
                hits.add(new Hit(new java.awt.Rectangle(x + 9, yy, w - 108, 26), () -> begin(null, alarm)));
                label(g, alarm.title, x + 15, yy + 5, w - 143, TEXT);
                label(g, alarm.time.format(TIME) + "  ·  " + alarm.repeat.name().toLowerCase(Locale.ROOT),
                        x + 15, yy + 16, w - 136, ACCENT);
                boolean enabled = alarm.next > 0 || alarm.snooze > 0;
                button(g, x + w - 99, yy + 3, 54, 19, enabled ? "On" : "Off", () -> {
                    alarm.toggle(System.currentTimeMillis(), ZoneId.systemDefault()); changed();
                }, mx, my, enabled);
                button(g, x + w - 41, yy + 3, 28, 19, "×", () -> {
                    alarm.next = alarm.snooze = 0; clock.alarms.remove(alarm); changed();
                }, mx, my, false);
            } else {
                var zone = clock.zones.get(i);
                var date = Instant.ofEpochMilli(now).atZone(zone);
                label(g, zone.getId().replace('_', ' '), x + 15, yy + 5, w - 96, TEXT);
                label(g, TIME.format(date) + "   " + DATE.format(date), x + 15, yy + 16, w - 96, ACCENT);
                button(g, x + w - 41, yy + 3, 28, 19, "×", () -> { clock.zones.remove(zone); changed(); }, mx, my, false);
            }
        }
    }

    private static long stopwatchTime() { return net.minecraft.Util.getMillis(); }

    private void renderStopwatch(GuiGraphics g, int mx, int my, long now) {
        long elapsed = clock.stopwatch(now);
        boolean running = clock.stopwatchStarted != 0;
        digits(g, time(elapsed, false) + String.format(Locale.ROOT, ".%02d", elapsed % 1000 / 10), y + 43);
        int left = x + w / 2 - 96;
        button(g, left, y + 73, 60, 20, running ? "Pause" : "Start", () -> clock.toggleStopwatch(stopwatchTime()), mx, my, true);
        button(g, left + 65, y + 73, 60, 20, "Lap", running ? () -> clock.lap(stopwatchTime()) : null, mx, my, false);
        button(g, left + 130, y + 73, 60, 20, "Reset", () -> {
            clock.stopwatchStarted = clock.stopwatchElapsed = 0;
            clock.laps.clear();
        }, mx, my, false);
        for (int i = 0; i < Math.min(clock.laps.size(), Math.max(0, (h - 121) / 15)); i++) {
            int index = clock.laps.size() - i - 1;
            label(g, "Lap " + (index + 1) + "   " + time(clock.laps.get(index), false),
                    x + 10, y + 103 + i * 15, w - 20, MUTED);
        }
    }

    private void renderFocus(GuiGraphics g, int mx, int my, long now) {
        boolean active = notifications.focus(now);
        long remaining = active ? notifications.focusUntil - now : notifications.focusMinutes * 60_000L;
        digits(g, time(remaining, true), y + 52);
        int left = x + w / 2 - 100;
        button(g, left, y + 87, 42, 21, "− 5", active ? null : () -> focusMinutes(-5), mx, my, false);
        button(g, left + 48, y + 87, 102, 21, active ? "End focus" : "Start focus", () -> {
            notifications.focusUntil = active ? 0 : System.currentTimeMillis() + notifications.focusMinutes * 60_000L;
            changed();
        }, mx, my, true);
        button(g, left + 156, y + 87, 42, 21, "+ 5", active ? null : () -> focusMinutes(5), mx, my, false);
        label(g, notifications.focusQuiet ? "Do Not Disturb follows the session." : "Do Not Disturb is controlled in Settings.",
                x + 11, y + 121, w - 22, MUTED);
    }

    private void focusMinutes(int delta) {
        notifications.focusMinutes = Math.clamp(notifications.focusMinutes + delta, 5, 120);
        changed();
    }

    private void digits(GuiGraphics g, String display, int y) {
        var text = PcDesktopScreen.label(display);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y, 0);
        g.pose().scale(2.1f, 2.1f, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, TEXT, false);
        g.pose().popPose();
    }

    private void renderForm(GuiGraphics g, int mx, int my, float partial) {
        hits.clear();
        int width = Math.min(w - 22, 250), x = this.x + (w - width) / 2, y = this.y + (h - 117) / 2;
        g.fill(this.x, this.y, this.x + w, this.y + h, 0x9D07101D);
        PcDesktopScreen.roundRect(g, x, y, width, 117, CARD);
        label(g, switch (page) { case TIMERS -> editingTimer == null ? "New timer" : "Edit timer";
                    case ALARMS -> editingAlarm == null ? "New alarm" : "Edit alarm"; default -> "Add time zone"; },
                x + 9, y + 9, width - 18, TEXT);
        if (page != Page.WORLD) drawField(g, title, x + 9, y + 26, width - 18, mx, my, partial);
        drawField(g, value, x + 9, y + (page == Page.WORLD ? 26 : 50), width - 18, mx, my, partial);
        if (page == Page.ALARMS) button(g, x + 9, y + 75, 84, 17, repeat.name().toLowerCase(Locale.ROOT), () -> {
            var choices = PcClock.Repeat.values();
            repeat = choices[(repeat.ordinal() + 1) % choices.length];
        }, mx, my, false);
        button(g, x + width - 112, y + 75, 47, 17, "Save", this::submit, mx, my, true);
        button(g, x + width - 60, y + 75, 51, 17, "Cancel", () -> form = false, mx, my, false);
        label(g, status, x + 9, y + 101, width - 18, ERROR);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (form && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            title.setFocused(false);
            value.setFocused(false);
        }
        return super.mouseClicked(mx, my, button) || form;
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!form && (page == Page.TIMERS || page == Page.ALARMS || page == Page.WORLD)) {
            first = Math.clamp(first + (dy < 0 ? 1 : -1), 0, Math.max(0, count() - capacity()));
        }
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!form) {
            if (key == GLFW.GLFW_KEY_N && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                    && (page == Page.TIMERS || page == Page.ALARMS || page == Page.WORLD)) { begin(); return true; }
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) form = false;
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) submit();
        else if (key == GLFW.GLFW_KEY_TAB) {
            boolean focusTitle = page != Page.WORLD && value.isFocused();
            title.setFocused(focusTitle);
            value.setFocused(!focusTitle);
        } else if (title.isFocused()) title.keyPressed(key, scan, modifiers);
        else if (value.isFocused()) value.keyPressed(key, scan, modifiers);
        return true;
    }

    @Override public boolean charTyped(char c, int modifiers) {
        if (!form) return false;
        if (title.isFocused()) title.charTyped(c, modifiers);
        if (value.isFocused()) value.charTyped(c, modifiers);
        return true;
    }
}
