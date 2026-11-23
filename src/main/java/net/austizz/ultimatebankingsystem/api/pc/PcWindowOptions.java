package net.austizz.ultimatebankingsystem.api.pc;

/**
 * Outer window dimensions in Minecraft GUI pixels. An initial dimension of zero
 * uses the desktop default. Tracking limits apply to normal windows. The desktop
 * takes precedence when its work area is smaller than the requested minimum.
 */
public record PcWindowOptions(int initialWidth, int initialHeight, int minWidth, int minHeight,
                              int maxWidth, int maxHeight, boolean resizable, boolean maximizable) {
    public static final PcWindowOptions DEFAULT = new PcWindowOptions(0, 0, 170, 76,
            Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);

    public PcWindowOptions {
        if (initialWidth < 0 || initialHeight < 0 || minWidth < 120 || minHeight < 60
                || maxWidth < minWidth || maxHeight < minHeight) {
            throw new IllegalArgumentException("Initial dimensions must be nonnegative. Limits must be ordered with a minimum of 120 × 60.");
        }
    }
}
