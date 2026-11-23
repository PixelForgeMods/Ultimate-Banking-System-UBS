package net.austizz.ultimatebankingsystem.gui.screens;

final class PcFilesPanes {
    static final int MIN_NAVIGATION = 80;
    // Includes the file list's horizontal padding.
    static final int MIN_CONTENT = 172;
    static final int MIN_INSPECTOR = 108;
    static final int MIN_WIDTH = MIN_NAVIGATION + MIN_CONTENT + MIN_INSPECTOR;

    enum Divider { NAVIGATION, INSPECTOR }

    record Layout(int navigation, int content, int inspector) {}

    int navigationWidth;
    int inspectorWidth;

    Layout layout(int width, boolean showNavigation, boolean showInspector) {
        int navigationMin = showNavigation ? MIN_NAVIGATION : 0;
        int inspectorMin = showInspector ? MIN_INSPECTOR : 0;
        int minimum = navigationMin + MIN_CONTENT + inspectorMin;
        if (width < minimum) {
            // The game can force a window smaller than its minimum at high GUI scales.
            int navigation = navigationMin * width / minimum;
            int inspector = inspectorMin * width / minimum;
            return new Layout(navigation, width - navigation - inspector, inspector);
        }
        int preferredNavigation = navigationWidth > 0 ? navigationWidth : Math.clamp(width / 5, MIN_NAVIGATION, 110);
        int preferredInspector = inspectorWidth > 0 ? inspectorWidth : Math.clamp(width / 4, 118, 175);
        int navigation = showNavigation
                ? Math.clamp(preferredNavigation, navigationMin, width - MIN_CONTENT - inspectorMin) : 0;
        int inspector = showInspector
                ? Math.clamp(preferredInspector, inspectorMin, width - MIN_CONTENT - navigation) : 0;
        return new Layout(navigation, width - navigation - inspector, inspector);
    }

    boolean resize(Divider divider, int requested, Layout current) {
        if (current.content < MIN_CONTENT) return false;
        int available = current.navigation + current.content + current.inspector;
        int other = divider == Divider.NAVIGATION ? current.inspector : current.navigation;
        int minimum = divider == Divider.NAVIGATION ? MIN_NAVIGATION : MIN_INSPECTOR;
        int maximum = available - other - MIN_CONTENT;
        if (maximum < minimum) return false;

        int next = Math.clamp(requested, minimum, maximum);
        if (divider == Divider.NAVIGATION) {
            if (next == current.navigation) return false;
            navigationWidth = next;
            // Keep the opposite divider still, including when a restored width was clamped.
            if (current.inspector > 0 && layout(available, true, true).inspector != current.inspector) {
                inspectorWidth = current.inspector;
            }
        } else {
            if (next == current.inspector) return false;
            inspectorWidth = next;
        }
        return true;
    }
}
