package net.austizz.ultimatebankingsystem.gui.screens;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

final class PcClock {
    private static final int MAX_ENTRIES = 12;
    private static final long DAY = 86_400_000L;
    enum Repeat { ONCE, DAILY, WEEKDAYS }
    enum Kind { TIMER, ALARM, FOCUS }
    record Alert(Kind kind, String title, Alarm alarm) {}

    static final class Timer {
        String title;
        long duration, remaining, until;
        Timer(String title, long duration) { this.title = title; this.duration = this.remaining = duration; }
        long remaining(long now) { return until == 0 ? remaining : Math.max(0, until - now); }
        void startPause(long now) {
            if (until != 0) { remaining = remaining(now); until = 0; }
            else until = now + (remaining == 0 ? duration : remaining);
        }
        void reset() { until = 0; remaining = duration; }
    }

    static final class Alarm {
        String title;
        LocalTime time;
        Repeat repeat;
        long next, snooze;
        Alarm(String title, LocalTime time, Repeat repeat) {
            this.title = title;
            this.time = time;
            this.repeat = repeat;
        }
        void toggle(long now, ZoneId zone) {
            if (next > 0 || snooze > 0) { next = 0; snooze = 0; }
            else next = nextAlarm(time, repeat, now, zone);
        }
    }

    final List<Timer> timers = new ArrayList<>(List.of(new Timer("Quick timer", 300_000),
            new Timer("Break", 600_000), new Timer("Work timer", 900_000), new Timer("Half hour", 1_800_000)));
    final List<Alarm> alarms = new ArrayList<>();
    final List<ZoneId> zones = new ArrayList<>(List.of(ZoneOffset.UTC, ZoneId.of("Europe/London"), ZoneId.of("America/New_York"),
            ZoneId.of("Asia/Tokyo")));
    final List<Long> laps = new ArrayList<>();
    long stopwatchStarted, stopwatchElapsed;

    boolean addTimer(Timer timer) {
        return timers.size() < MAX_ENTRIES && timers.add(timer);
    }
    boolean addAlarm(Alarm alarm) {
        return alarms.size() < MAX_ENTRIES && alarms.add(alarm);
    }
    boolean addZone(ZoneId zone) { return zones.size() < MAX_ENTRIES && !zones.contains(zone) && zones.add(zone); }

    long stopwatch(long now) { return stopwatchElapsed + (stopwatchStarted == 0 ? 0 : Math.max(0, now - stopwatchStarted)); }
    void toggleStopwatch(long now) {
        if (stopwatchStarted == 0) stopwatchStarted = now;
        else { stopwatchElapsed = stopwatch(now); stopwatchStarted = 0; }
    }

    void lap(long now) {
        if (stopwatchStarted != 0 && laps.size() < 100) laps.add(stopwatch(now));
    }

    static long nextAlarm(LocalTime time, Repeat repeat, long after, ZoneId zone) {
        var instant = Instant.ofEpochMilli(after);
        var date = instant.atZone(zone).toLocalDate();
        for (int i = 0; i <= 7; i++) {
            var day = date.plusDays(i);
            if (repeat == Repeat.WEEKDAYS && day.getDayOfWeek().getValue() > 5) continue;
            var candidate = day.atTime(time).atZone(zone).toInstant();
            if (candidate.isAfter(instant)) return candidate.toEpochMilli();
        }
        throw new IllegalStateException("No alarm date within the next week.");
    }

    boolean tick(long now, ZoneId zone, PcNotifications notifications, Consumer<Alert> send) {
        boolean changed = false;
        for (var timer : timers) {
            if (timer.until == 0 || now < timer.until) continue;
            timer.until = 0;
            timer.remaining = 0;
            send.accept(new Alert(Kind.TIMER, timer.title, null));
            changed = true;
        }
        for (var alarm : alarms) {
            boolean due = alarm.next > 0 && alarm.next <= now;
            boolean snoozed = alarm.snooze > 0 && alarm.snooze <= now;
            if (!due && !snoozed) continue;
            if (due) alarm.next = alarm.repeat == Repeat.ONCE ? 0 : nextAlarm(alarm.time, alarm.repeat, now, zone);
            if (snoozed) alarm.snooze = 0;
            send.accept(new Alert(Kind.ALARM, alarm.title, alarm));
            changed = true;
        }
        if (notifications.focusUntil > 0 && now >= notifications.focusUntil) {
            notifications.focusUntil = 0;
            send.accept(new Alert(Kind.FOCUS, "Focus complete", null));
            changed = true;
        }
        return changed;
    }

    void store(Properties values) {
        values.setProperty("clock.timer.count", Integer.toString(timers.size()));
        for (int i = 0; i < timers.size(); i++) {
            var t = timers.get(i);
            String key = "clock.timer." + i;
            values.setProperty(key + ".title", t.title);
            values.setProperty(key, t.duration + "," + t.remaining + "," + t.until);
        }
        values.setProperty("clock.alarm.count", Integer.toString(alarms.size()));
        for (int i = 0; i < alarms.size(); i++) {
            var alarm = alarms.get(i);
            String key = "clock.alarm." + i;
            values.setProperty(key + ".title", alarm.title);
            values.setProperty(key, alarm.time + "," + alarm.repeat + "," + alarm.next + "," + alarm.snooze);
        }
        values.setProperty("clock.zones", String.join(",", zones.stream().map(ZoneId::getId).toList()));
    }

    void load(Properties values) {
        if (values.containsKey("clock.timer.count")) {
            timers.clear();
            for (int i = 0; i < count(values, "clock.timer.count"); i++) {
                String key = "clock.timer." + i;
                try {
                    var data = values.getProperty(key, "").split(",");
                    if (data.length != 3) continue;
                    var timer = new Timer(values.getProperty(key + ".title", "Timer"), Math.clamp(Long.parseLong(data[0]), 1000, DAY));
                    timer.remaining = Math.clamp(Long.parseLong(data[1]), 0, timer.duration);
                    timer.until = Math.max(0, Long.parseLong(data[2]));
                    timers.add(timer);
                } catch (RuntimeException ignored) { /* Ignore malformed saved entries. */ }
            }
        }
        alarms.clear();
        for (int i = 0; i < count(values, "clock.alarm.count"); i++) {
            String key = "clock.alarm." + i;
            try {
                var data = values.getProperty(key, "").split(",");
                if (data.length != 4) continue;
                var alarm = new Alarm(values.getProperty(key + ".title", "Alarm"), LocalTime.parse(data[0]), Repeat.valueOf(data[1]));
                alarm.next = Math.max(0, Long.parseLong(data[2]));
                alarm.snooze = Math.max(0, Long.parseLong(data[3]));
                alarms.add(alarm);
            } catch (RuntimeException ignored) { /* Ignore malformed saved entries. */ }
        }
        if (values.containsKey("clock.zones")) {
            zones.clear();
            for (String zone : values.getProperty("clock.zones").split(",")) {
                try { addZone(ZoneId.of(zone)); }
                catch (DateTimeException ignored) { /* Ignore unavailable zone identifiers. */ }
            }
        }
    }

    private static int count(Properties values, String key) {
        try { return Math.clamp(Integer.parseInt(values.getProperty(key, "0")), 0, MAX_ENTRIES); }
        catch (NumberFormatException e) { return 0; }
    }
}
