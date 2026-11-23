package net.austizz.ultimatebankingsystem.gui.screens;

enum PcFilesView {
    EXTRA_LARGE("Extra large icons", 75),
    LARGE("Large icons", 36),
    MEDIUM("Medium icons", 12),
    SMALL("Small icons", 0),
    LIST("List", -1),
    DETAILS("Details", -2),
    TILES("Tiles", -3),
    CONTENT("Content", -4);

    private static final PcFilesView[] MODES = values();
    final String label;
    final int step;

    PcFilesView(String label, int step) {
        this.label = label;
        this.step = step;
    }

    static boolean validStep(int step) {
        return step >= CONTENT.step && step <= EXTRA_LARGE.step;
    }

    static int zoom(int step, int clicks) {
        return Math.clamp((long) step + clicks, CONTENT.step, EXTRA_LARGE.step);
    }

    static PcFilesView at(int step) {
        if (step < 0) return MODES[SMALL.ordinal() - step];
        if (step >= 56) return EXTRA_LARGE;
        if (step >= 24) return LARGE;
        return step >= 6 ? MEDIUM : SMALL;
    }

    static int iconSize(int step) {
        return step <= LARGE.step ? 12 + step : 48 + Math.round((step - LARGE.step) * 80f / 39);
    }
}
