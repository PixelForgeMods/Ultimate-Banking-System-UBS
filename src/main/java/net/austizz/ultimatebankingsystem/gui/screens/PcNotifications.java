package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppNotification;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class PcNotifications {
    static final int LIMIT = 80;
    enum Priority { NORMAL, HIGH, TOP }

    static final class AppSettings {
        boolean enabled = true, banners = true, history = true, sound = true, badge = true, quietException, hideLockContent;
        Priority priority = Priority.NORMAL;
    }

    static final class Entry {
        final long id, time;
        final String app, appName;
        PcAppNotification payload;
        String target;
        boolean unread = true;

        Entry(long id, long time, String app, String appName, PcAppNotification payload, String target) {
            this.id = id;
            this.time = time;
            this.app = app;
            this.appName = appName;
            this.payload = payload;
            this.target = target;
        }
    }

    record Group(String app, String name, List<Entry> entries) {}

    private final ArrayList<Entry> history = new ArrayList<>();
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private final Map<String, AppSettings> apps = new LinkedHashMap<>();
    private final Map<String, Entry> updates = new LinkedHashMap<>();
    private long nextId;
    boolean enabled = true, sound = true, lockScreen = true, hideLockContent = true, lockCalls = true;
    boolean manualQuiet, scheduled, presentationQuiet, allowCalls = true, allowReminders = true;
    boolean focusQuiet = true, focusHideBadges = true;
    int scheduleStart = 22, scheduleEnd = 7, focusMinutes = 30;
    long focusUntil;

    AppSettings app(String id) { return apps.computeIfAbsent(id, ignored -> new AppSettings()); }

    boolean focus(long now) { return now < focusUntil; }
    boolean quiet(long now, int hour, boolean presenting) {
        boolean inSchedule = scheduleStart < scheduleEnd ? hour >= scheduleStart && hour < scheduleEnd
                : scheduleStart > scheduleEnd && (hour >= scheduleStart || hour < scheduleEnd);
        return manualQuiet || focusQuiet && focus(now) || scheduled && inSchedule || presentationQuiet && presenting;
    }

    boolean allowsBanner(Entry entry, boolean quiet) {
        var app = app(entry.app);
        if (!enabled || !app.enabled || !app.banners) return false;
        return !quiet || app.quietException || switch (entry.payload.delivery()) {
            case ALARM -> true;
            case REMINDER -> allowReminders;
            case CALL -> allowCalls;
            case NORMAL -> false;
        };
    }

    boolean post(String appId, String appName, PcAppNotification payload, String target, long now, boolean quiet) {
        var app = app(appId);
        if (!enabled || !app.enabled) return false;
        String updateKey = appId + "\n" + payload.key();
        var previous = payload.key().isEmpty() ? null : updates.get(updateKey);
        if (previous != null) {
            previous.payload = payload;
            previous.target = target;
            return false;
        }
        var entry = new Entry(++nextId, now, appId, appName, payload, target);
        if (!payload.key().isEmpty()) {
            updates.put(updateKey, entry);
            if (updates.size() > LIMIT) updates.remove(updates.keySet().iterator().next());
        }
        if (app.history) {
            history.addLast(entry);
            if (history.size() > LIMIT) {
                var old = history.removeFirst();
                updates.values().removeIf(item -> item == old);
            }
        }
        boolean banner = allowsBanner(entry, quiet);
        if (banner) {
            if (queue.size() >= 8) queue.removeFirst();
            queue.addLast(entry);
        }
        return banner && sound && app.sound;
    }

    Entry nextBanner(boolean quiet) {
        Entry entry;
        while ((entry = queue.pollFirst()) != null) if (allowsBanner(entry, quiet)) return entry;
        return null;
    }

    void clearBanners() { queue.clear(); }

    List<Group> groups() {
        var groups = new LinkedHashMap<String, List<Entry>>();
        for (var entry : history.reversed()) {
            if (app(entry.app).history && app(entry.app).enabled) {
                groups.computeIfAbsent(entry.app, id -> new ArrayList<>()).add(entry);
            }
        }
        return groups.values().stream()
                .sorted(Comparator.<List<Entry>, Priority>comparing(group -> app(group.getFirst().app).priority).reversed())
                .map(group -> new Group(group.getFirst().app, group.getFirst().appName, group)).toList();
    }

    int unread(String id) {
        int count = 0;
        if (!enabled) return 0;
        for (var entry : history) {
            var app = app(entry.app);
            if (entry.unread && app.enabled && app.history && (id == null || entry.app.equals(id) && app.badge)) count++;
        }
        return count;
    }

    List<Entry> lockEntries() {
        if (!enabled || !lockScreen) return List.of();
        return history.reversed().stream().filter(entry -> entry.unread && app(entry.app).enabled && app(entry.app).history
                && (lockCalls || entry.payload.delivery() != PcAppNotification.Delivery.CALL)).limit(3).toList();
    }

    void seen() { history.forEach(entry -> entry.unread = false); }
    void dismiss(Entry entry) {
        history.remove(entry);
        queue.remove(entry);
        updates.values().removeIf(item -> item == entry);
    }
    void clear(String appId) {
        history.removeIf(entry -> appId == null || entry.app.equals(appId));
        queue.removeIf(entry -> appId == null || entry.app.equals(appId));
        updates.values().removeIf(entry -> appId == null || entry.app.equals(appId));
    }

    void priority(String appId, Priority value) {
        if (value == Priority.TOP) apps.values().stream().filter(app -> app.priority == Priority.TOP)
                .forEach(app -> app.priority = Priority.NORMAL);
        app(appId).priority = value;
    }

    void store(Properties p) {
        p.setProperty("notify.global", bits(enabled, sound, lockScreen, hideLockContent, lockCalls, manualQuiet,
                scheduled, presentationQuiet, allowCalls, allowReminders, focusQuiet, focusHideBadges));
        p.setProperty("notify.hours", scheduleStart + "," + scheduleEnd);
        p.setProperty("notify.focus", focusMinutes + "," + focusUntil);
        apps.forEach((id, app) -> p.setProperty("notify.app." + id, bits(app.enabled, app.banners, app.history,
                app.sound, app.badge, app.quietException) + app.priority.ordinal() + bits(app.hideLockContent)));
        p.setProperty("notify.count", Integer.toString(history.size()));
        for (int i = 0; i < history.size(); i++) {
            var e = history.get(i);
            String k = "notify.item." + i + ".";
            p.setProperty(k + "app", e.app);
            p.setProperty(k + "sender", e.appName);
            p.setProperty(k + "key", e.payload.key());
            p.setProperty(k + "title", e.payload.title());
            p.setProperty(k + "body", e.payload.body());
            p.setProperty(k + "progress", Integer.toString(e.payload.progress()));
            p.setProperty(k + "kind", e.payload.delivery().name());
            if (e.payload.image() != null) p.setProperty(k + "image", e.payload.image().toString());
            p.setProperty(k + "time", Long.toString(e.time));
            p.setProperty(k + "target", e.target);
            p.setProperty(k + "unread", Boolean.toString(e.unread));
        }
    }

    void load(Properties p) {
        String global = p.getProperty("notify.global", "");
        enabled = bit(global, 0, true); sound = bit(global, 1, true);
        lockScreen = bit(global, 2, true); hideLockContent = bit(global, 3, true); lockCalls = bit(global, 4, true);
        manualQuiet = bit(global, 5, false); scheduled = bit(global, 6, false); presentationQuiet = bit(global, 7, false);
        allowCalls = bit(global, 8, true); allowReminders = bit(global, 9, true);
        focusQuiet = bit(global, 10, true); focusHideBadges = bit(global, 11, true);
        try {
            var hours = p.getProperty("notify.hours", "22,7").split(",", -1);
            if (hours.length == 2) {
                scheduleStart = Math.clamp(Integer.parseInt(hours[0]), 0, 23);
                scheduleEnd = Math.clamp(Integer.parseInt(hours[1]), 0, 23);
            }
            var focus = p.getProperty("notify.focus", "30,0").split(",", -1);
            if (focus.length == 2) {
                focusMinutes = Math.clamp(Integer.parseInt(focus[0]), 5, 120);
                focusUntil = Long.parseLong(focus[1]);
            }
        } catch (IllegalArgumentException ignored) {}
        for (String k : p.stringPropertyNames()) {
            if (!k.startsWith("notify.app.")) continue;
            String bits = p.getProperty(k);
            String id = k.substring("notify.app.".length());
            var app = app(id);
            app.enabled = bit(bits, 0, true); app.banners = bit(bits, 1, true); app.history = bit(bits, 2, true);
            app.sound = bit(bits, 3, true); app.badge = bit(bits, 4, true); app.quietException = bit(bits, 5, false);
            app.hideLockContent = bit(bits, 7, false);
            if (bits.length() > 6) priority(id, Priority.values()[Math.clamp(bits.charAt(6) - '0', 0, 2)]);
        }
        int count;
        try { count = Math.clamp(Integer.parseInt(p.getProperty("notify.count", "0")), 0, LIMIT); }
        catch (NumberFormatException e) { count = 0; }
        history.clear();
        updates.clear();
        queue.clear();
        for (int i = 0; i < count; i++) {
            String k = "notify.item." + i + ".";
            try {
                String id = p.getProperty(k + "app", "");
                if (id.isBlank()) continue;
                String image = p.getProperty(k + "image");
                var notification = new PcAppNotification(p.getProperty(k + "key", ""), p.getProperty(k + "title", ""),
                        p.getProperty(k + "body", ""), Integer.parseInt(p.getProperty(k + "progress", "-1")),
                        image == null ? null : UUID.fromString(image),
                        PcAppNotification.Delivery.valueOf(p.getProperty(k + "kind", "NORMAL")), List.of(), null);
                var entry = new Entry(++nextId, Long.parseLong(p.getProperty(k + "time", "0")), id,
                        p.getProperty(k + "sender", id), notification, p.getProperty(k + "target", ""));
                entry.unread = Boolean.parseBoolean(p.getProperty(k + "unread", "true"));
                history.add(entry);
                if (!notification.key().isEmpty()) updates.put(id + "\n" + notification.key(), entry);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static boolean bit(String value, int index, boolean fallback) {
        return index < value.length() ? value.charAt(index) == '1' : fallback;
    }

    private static String bits(boolean... flags) {
        var bits = new StringBuilder(flags.length);
        for (boolean flag : flags) bits.append(flag ? '1' : '0');
        return bits.toString();
    }
}
