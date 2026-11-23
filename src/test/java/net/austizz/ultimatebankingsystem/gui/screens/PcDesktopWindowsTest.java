package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcWindowOptions;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.Bounds;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.ResizeEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcDesktopWindowsTest {
    @Test
    void showDesktopRestoresOnlyTheWindowsItHidInTheirOriginalOrder() {
        var windows = new PcDesktopWindows<String>();
        windows.open("Files", 20, 30);
        windows.open("Notes", 40, 50);
        windows.open("Settings", 10, 10);
        var files = windows.get("Files");
        var notes = windows.get("Notes");
        var settings = windows.get("Settings");
        settings.minimized = true;
        notes.maximized = true;
        files.snapped = PcDesktopWindows.SnapZone.LEFT;

        windows.toggleShowDesktop();
        assertNull(windows.active());
        assertTrue(windows.isOpen("Notes"));
        windows.toggleShowDesktop();

        assertSame(notes, windows.active());
        assertEquals(List.of(files, notes, settings), List.copyOf(windows.backToFront()));
        assertTrue(notes.maximized);
        assertSame(PcDesktopWindows.SnapZone.LEFT, files.snapped);
        assertTrue(settings.minimized);
    }

    @Test
    void explicitlyReopenedAndClosedWindowsLeaveTheShowDesktopRestoreSet() {
        var windows = new PcDesktopWindows<String>();
        windows.open("Files", 0, 0);
        windows.open("Notes", 0, 0);
        windows.open("Settings", 0, 0);
        windows.toggleShowDesktop();
        windows.open("Files", 0, 0);
        windows.get("Files").minimized = true;
        windows.close("Settings");
        windows.toggleShowDesktop();
        assertSame(windows.get("Notes"), windows.active());
        assertTrue(windows.get("Files").minimized);
        assertFalse(windows.isOpen("Settings"));
    }

    @Test
    void resizeBordersRecognizeAllEightDirections() {
        assertSame(ResizeEdge.TOP_LEFT, ResizeEdge.at(8, 1, 240, 160, 4));
        assertSame(ResizeEdge.TOP, ResizeEdge.at(120, -2, 240, 160, 4));
        assertSame(ResizeEdge.TOP_RIGHT, ResizeEdge.at(241, 8, 240, 160, 4));
        assertSame(ResizeEdge.RIGHT, ResizeEdge.at(241, 80, 240, 160, 4));
        assertSame(ResizeEdge.BOTTOM_RIGHT, ResizeEdge.at(241, 161, 240, 160, 4));
        assertSame(ResizeEdge.BOTTOM, ResizeEdge.at(120, 161, 240, 160, 4));
        assertSame(ResizeEdge.BOTTOM_LEFT, ResizeEdge.at(1, 153, 240, 160, 4));
        assertSame(ResizeEdge.LEFT, ResizeEdge.at(-2, 80, 240, 160, 4));
        assertNull(ResizeEdge.at(120, 80, 240, 160, 4));
        assertNull(ResizeEdge.at(245, 80, 240, 160, 4));
    }

    @Test
    void resizingMovesOnlyGrabbedEdgesAndKeepsFractionalOppositeEdgesFixed() {
        var start = new Bounds(40.5, 30.25, 240, 160);
        for (var edge : ResizeEdge.values()) {
            var result = start.resize(edge, 10, 20, 480, 300, PcWindowOptions.DEFAULT);
            assertEquals(edge.horizontal < 0 ? 50.5 : 40.5, result.x());
            assertEquals(edge.vertical < 0 ? 50.25 : 30.25, result.y());
            assertEquals(240 + 10 * edge.horizontal, result.width());
            assertEquals(160 + 20 * edge.vertical, result.height());
        }
    }

    @Test
    void resizingClampsToMinimumSizeAndDesktopBounds() {
        var start = new Bounds(40.5, 30.25, 240, 160);
        assertEquals(new Bounds(110.5, 114.25, 170, 76),
                start.resize(ResizeEdge.TOP_LEFT, 1_000, 1_000, 480, 300, PcWindowOptions.DEFAULT));
        assertEquals(new Bounds(0.5, 0.25, 280, 190),
                start.resize(ResizeEdge.TOP_LEFT, -1_000, -1_000, 480, 300, PcWindowOptions.DEFAULT));
        assertEquals(new Bounds(40.5, 30.25, 439, 269),
                start.resize(ResizeEdge.BOTTOM_RIGHT, 1_000, 1_000, 480, 300, PcWindowOptions.DEFAULT));
        var small = new Bounds(0, 0, 110, 60);
        assertEquals(small, small.resize(ResizeEdge.BOTTOM_RIGHT, -20, -20, 110, 60, PcWindowOptions.DEFAULT));
    }

    @Test
    void appCanSetMaximumTrackingSizeAndLockOneAxis() {
        var options = new PcWindowOptions(180, 100, 150, 100, 230, 100, true, false);
        var bounds = new Bounds(80.5, 60.25, 180, 100);
        assertSame(ResizeEdge.LEFT, ResizeEdge.TOP_LEFT.allowedBy(options));
        assertNull(ResizeEdge.TOP.allowedBy(options));
        assertEquals(new Bounds(30.5, 60.25, 230, 100),
                bounds.resize(ResizeEdge.LEFT, -1_000, -1_000, 480, 300, options));
        assertEquals(new Bounds(80.5, 60.25, 150, 100),
                bounds.resize(ResizeEdge.RIGHT, -1_000, 0, 480, 300, options));
        assertThrows(IllegalArgumentException.class,
                () -> new PcWindowOptions(180, 100, 180, 100, 179, 100, true, true));
    }

    @Test
    void snappedHalvesAndQuartersMeetWithoutGapsAtOddViewportSizes() {
        assertEquals(new Bounds(0, 0, 225, 233), PcDesktopWindows.SnapZone.LEFT.bounds(451, 233));
        assertEquals(new Bounds(225, 0, 226, 233), PcDesktopWindows.SnapZone.RIGHT.bounds(451, 233));
        assertEquals(new Bounds(225, 116, 226, 117), PcDesktopWindows.SnapZone.BOTTOM_RIGHT.bounds(451, 233));
        assertEquals(new Bounds(0, 0, 451, 233), PcDesktopWindows.SnapZone.MAXIMIZE.bounds(451, 233));
    }

    @Test
    void pinsRemainIndependentOfRunningWindows() {
        var windows = new PcDesktopWindows<String>();
        windows.open("Files", 0, 0);
        windows.setPinned("Files", false);
        assertTrue(windows.isOpen("Files"));
        assertFalse(windows.isPinned("Files", true));

        windows.setPinned("Notes", true);
        windows.closeAll();
        assertFalse(windows.isOpen("Notes"));
        assertTrue(windows.isPinned("Notes", false));
        assertFalse(windows.isPinned("Files", true));
    }

    @Test
    void recentAppsStayInUseOrderAfterWindowsClose() {
        var windows = new PcDesktopWindows<String>();
        windows.open("Files", 0, 0);
        windows.open("Notes", 0, 0);
        windows.open("Files", 0, 0);
        windows.closeAll();

        assertEquals(List.of("Files", "Notes"), List.copyOf(windows.recent()));
        assertNull(windows.active());
    }

    @Test
    void reopeningRaisesAndUnminimizesTheSameWindow() {
        var windows = new PcDesktopWindows<String>();
        windows.open("Files", 20, 30);
        var files = windows.active();
        files.maximized = true;
        windows.open("Notes", 40, 50);
        var notes = windows.active();
        assertEquals(List.of(files, notes), List.copyOf(windows.backToFront()));

        windows.open("Files", 0, 0);
        assertSame(files, windows.active());
        files.minimized = true;
        assertSame(notes, windows.active());
        assertTrue(windows.isOpen("Files"));

        windows.open("Files", 0, 0);
        assertSame(files, windows.active());
        assertFalse(files.minimized);
        assertTrue(files.maximized);
        assertEquals(20.0, files.x);
        assertEquals(30.0, files.y);

        windows.close("Files");
        assertFalse(windows.isOpen("Files"));
        assertSame(notes, windows.active());
        notes.minimized = true;
        assertNull(windows.active());
    }

    @Test
    void draggingKeepsFractionalPositionAndClampsToTheDesktop() {
        var windows = new PcDesktopWindows<String>();
        windows.open("Files", 0, 0);
        var files = windows.active();
        files.move(14.25, 25.75, 180, 100);
        assertEquals(14.25, files.x);
        assertEquals(25.75, files.y);

        files.move(-4, 130, 180, 100);
        assertEquals(0.0, files.x);
        assertEquals(100.0, files.y);
    }
}
