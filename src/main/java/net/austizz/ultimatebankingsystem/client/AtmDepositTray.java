package net.austizz.ultimatebankingsystem.client;

/** Local drag selection; money stays server-owned until a feed request is accepted. */
final class AtmDepositTray {
    final int[] available = new int[7], dragging = new int[7];
    boolean pending;
    double opacity;
    String message = "";
    void stock(int[] counts) {
        for (int i = 0; i < 7; i++) { available[i] = Math.max(0, counts[i]); if (!pending) dragging[i] = Math.min(dragging[i], available[i]); }
    }
    boolean take(int denomination, boolean stack) {
        if (pending || denomination < 0 || denomination >= 7) return false;
        int count = Math.min(stack ? 64 : 1, available[denomination] - dragging[denomination]);
        if (count <= 0) return false;
        dragging[denomination] += count; message = ""; return true;
    }
    int count() { return java.util.Arrays.stream(dragging).sum(); }
    boolean submit(boolean overFeeder) { if (!overFeeder || pending || count() == 0) return false; pending = true; return true; }
    void result(boolean accepted, String notice) {
        pending = false; message = accepted ? "" : notice;
        if (accepted) java.util.Arrays.fill(dragging, 0);
        else for (int i = 0; i < 7; i++) dragging[i] = Math.min(dragging[i], available[i]);
    }
    void clear() { if (!pending) java.util.Arrays.fill(dragging, 0); }
    void fade(boolean visible, double seconds) { opacity += ((visible ? 1 : 0) - opacity) * (1 - Math.exp(-Math.clamp(seconds, 0, .1) * 12)); }
    static int rowHeight(int height) { return Math.clamp((height - 40) / 7, 18, 28); }
    static int top(int height) { return Math.max(8, (height - rowHeight(height) * 7) / 2); }
    static int hit(double x, double y, int width, int height) {
        int row = rowHeight(height), top = top(height);
        if (x < 8 || x >= 100 || y < top || y >= top + row * 7) return -1;
        return (int) (y - top) / row;
    }
}
