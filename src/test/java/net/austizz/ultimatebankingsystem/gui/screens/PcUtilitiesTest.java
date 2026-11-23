package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PcUtilitiesTest {
    @Test void zipRoundTripKeepsFoldersAndExtractionIsAtomicOnCollision() throws Exception {
        var drive = new PcFileSystem(null);
        byte[] text = "Hello ZIP".getBytes(StandardCharsets.UTF_8);
        var content = List.of(new PcZip.Item("Folder", true, new byte[0]),
                new PcZip.Item("Folder/Hello.txt", false, text), new PcZip.Item("busy.txt", false, text));
        var original = drive.create(PcFileSystem.DOCUMENTS, "busy.txt", false, "existing");
        var parsed = PcZip.read(PcZip.encode(content));
        assertArrayEquals(text, parsed.get(1).bytes());
        assertThrows(IOException.class, () -> drive.extract(PcFileSystem.DOCUMENTS, parsed));
        assertThrows(IOException.class, () -> drive.resolve(PcFileSystem.DOCUMENTS, "Folder"));
        assertEquals("existing", drive.get(original).text());
        drive.recycle(original);
        drive.extract(PcFileSystem.DOCUMENTS, parsed);
        assertEquals("Hello ZIP", drive.resolve(PcFileSystem.DOCUMENTS, "Folder/Hello.txt").text());
        assertTrue(PcZip.read(PcZip.encode(List.of())).isEmpty());
    }

    @Test void scanRejectsTraversalAndCaseCollisions() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../escape.txt"));
            zip.write(0);
        }
        assertThrows(IOException.class, () -> PcZip.read(bytes.toByteArray()));
        assertThrows(IOException.class, () -> PcZip.encode(List.of(new PcZip.Item("a.TXT", false, new byte[0]),
                new PcZip.Item("A.txt", false, new byte[0]))));
        var valid = PcZip.encode(List.of(new PcZip.Item("file.txt", false, new byte[0])));
        valid[valid.length - 22] = 0;
        assertThrows(IOException.class, () -> PcZip.read(valid));
    }

    @Test void clockReschedulesWeekdayAlarmsAndPersistsTimerAndSnoozeState() {
        var zone = ZoneId.of("Europe/Amsterdam");
        long friday = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, zone).toInstant().toEpochMilli();
        var clock = new PcClock();
        var notifications = new PcNotifications();
        var alarm = new PcClock.Alarm("Weekday", LocalTime.of(9, 0), PcClock.Repeat.WEEKDAYS);
        alarm.toggle(friday, zone);
        long monday = ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, zone).toInstant().toEpochMilli();
        assertEquals(monday, alarm.next);
        assertTrue(clock.addAlarm(alarm));
        var timer = new PcClock.Timer("Short timer", 1000);
        assertTrue(clock.addTimer(timer));
        timer.startPause(monday - 1000);
        notifications.focusUntil = monday;
        var alerts = new ArrayList<PcClock.Alert>();
        assertFalse(clock.tick(monday - 1, zone, notifications, alerts::add));
        assertTrue(clock.tick(monday, zone, notifications, alerts::add));
        assertEquals(List.of(PcClock.Kind.TIMER, PcClock.Kind.ALARM, PcClock.Kind.FOCUS),
                alerts.stream().map(PcClock.Alert::kind).toList());
        assertEquals(0, timer.remaining(monday + 20));
        assertFalse(clock.tick(monday + 1, zone, notifications, alerts::add));
        alarm.snooze = monday + 600_000;
        var props = new Properties();
        clock.store(props);
        var reopened = new PcClock();
        reopened.load(props);
        assertEquals(alarm.snooze, reopened.alarms.getFirst().snooze);
        assertEquals(0, reopened.timers.getLast().remaining(monday + 20));
        assertTrue(reopened.tick(alarm.snooze, zone, notifications, alerts::add));
        assertEquals(PcClock.Kind.ALARM, alerts.getLast().kind());
    }

    @Test void shellUsesQuotedVirtualPathsAndStopsScriptsOnFirstError() throws Exception {
        var drive = new PcFileSystem(null);
        var output = new ArrayList<String>();
        var shell = new PcShell(drive, new PcClipboardState(), output::add, output::clear, entry -> {}, entry -> {},
                app -> {}, List::of, text -> {}, false, () -> "source", text -> {});
        shell.run("mkdir \"A folder\"");
        shell.run("cd \"A folder\"");
        shell.run("touch test.txt");
        shell.run("write test.txt \"line one\"");
        shell.run("cat test.txt");
        assertEquals("line one", output.getLast());
        assertEquals(List.of("cat", "C:\\Documents\\A folder\\test.txt"),
                PcShell.tokenize("cat \"C:\\Documents\\A folder\\test.txt\""));
        assertEquals(List.of("cat test.txt"), shell.completions("cat te"));
        assertThrows(IOException.class, () -> PcShell.tokenize("cat \"bad quote"));
        var folder = drive.resolve(PcFileSystem.DOCUMENTS, "A folder").id();
        drive.create(folder, "batch.pcsh", false, "echo ok\ncc import unsafe.lua\nmkdir ShouldNotRun");
        shell.run("run batch.pcsh");
        assertTrue(output.contains("ok"));
        assertTrue(output.getLast().contains("Line 2"));
        assertThrows(IOException.class, () -> drive.resolve(folder, "unsafe.lua"));
        assertThrows(IOException.class, () -> drive.resolve(folder, "ShouldNotRun"));
    }
}
