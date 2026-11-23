package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import static net.austizz.ultimatebankingsystem.gui.screens.PcFilesPanes.Divider.INSPECTOR;
import static net.austizz.ultimatebankingsystem.gui.screens.PcFilesPanes.Divider.NAVIGATION;
import static org.junit.jupiter.api.Assertions.*;

class PcFilesPanesTest {
    @Test
    void draggingEachDividerKeepsTheOtherStillAndStopsAtThePaneMinimums() {
        var panes = new PcFilesPanes();
        var initial = panes.layout(600, true, true);

        assertTrue(panes.resize(NAVIGATION, 600, initial));
        var leftWide = panes.layout(600, true, true);
        assertEquals(initial.inspector(), leftWide.inspector());
        assertEquals(PcFilesPanes.MIN_CONTENT, leftWide.content());

        assertTrue(panes.resize(NAVIGATION, -100, leftWide));
        var leftNarrow = panes.layout(600, true, true);
        assertEquals(PcFilesPanes.MIN_NAVIGATION, leftNarrow.navigation());
        assertEquals(initial.inspector(), leftNarrow.inspector());

        assertTrue(panes.resize(INSPECTOR, 600, leftNarrow));
        var rightWide = panes.layout(600, true, true);
        assertEquals(leftNarrow.navigation(), rightWide.navigation());
        assertEquals(PcFilesPanes.MIN_CONTENT, rightWide.content());

        assertTrue(panes.resize(INSPECTOR, 0, rightWide));
        assertEquals(PcFilesPanes.MIN_INSPECTOR, panes.layout(600, true, true).inspector());
    }

    @Test
    void aNarrowWindowOrHiddenPaneDoesNotDiscardSavedWidths() {
        var panes = new PcFilesPanes();
        panes.navigationWidth = 180;
        panes.inspectorWidth = 210;
        var wide = new PcFilesPanes.Layout(180, 210, 210);
        assertEquals(wide, panes.layout(600, true, true));
        assertEquals(new PcFilesPanes.Layout(PcFilesPanes.MIN_NAVIGATION, PcFilesPanes.MIN_CONTENT,
                PcFilesPanes.MIN_INSPECTOR), panes.layout(PcFilesPanes.MIN_WIDTH, true, true));
        assertEquals(new PcFilesPanes.Layout(0, 390, 210), panes.layout(600, false, true));
        assertEquals(new PcFilesPanes.Layout(180, 420, 0), panes.layout(600, true, false));
        assertEquals(wide, panes.layout(600, true, true));
    }

    @Test
    void aGameViewportBelowTheMinimumKeepsAllPanesVisible() {
        var panes = new PcFilesPanes();
        var layout = panes.layout(250, true, true);
        assertEquals(250, layout.navigation() + layout.content() + layout.inspector());
        assertTrue(layout.navigation() > 0 && layout.content() > 0 && layout.inspector() > 0);
        assertFalse(panes.resize(NAVIGATION, 100, layout));
        assertFalse(panes.resize(INSPECTOR, 100, layout));
    }
}
