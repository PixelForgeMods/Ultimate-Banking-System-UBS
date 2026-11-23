package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcWindowOptions;
import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedCollection;

final class PcDesktopWindows<A> {
    private final LinkedHashMap<A, Window<A>> stack = new LinkedHashMap<>();
    private final LinkedHashSet<A> recent = new LinkedHashSet<>();
    private final Map<A, Boolean> pins = new HashMap<>();
    final Map<A, Cell> desktopPositions = new HashMap<>();
    final Map<A, String> desktopNames = new HashMap<>();
    final Map<java.util.UUID, Cell> desktopItemPositions = new HashMap<>();
    final LinkedHashSet<java.util.UUID> quickFolders = new LinkedHashSet<>();
    final LinkedHashSet<java.util.UUID> startFolders = new LinkedHashSet<>();
    final Map<java.util.UUID, Integer> fileViews = new HashMap<>();
    final PcFilesPanes filePanes = new PcFilesPanes();
    boolean quickFoldersInitialized;
    boolean samplePicturesInitialized;
    final Map<String, Boolean> savedPins = new HashMap<>();
    int desktopIconSize = 22;
    boolean showDesktopIcons = true;
    boolean darkFileIcons;
    final PcClipboardState clipboard = new PcClipboardState();
    final Map<String, String> defaultApps = new HashMap<>();
    java.util.UUID photoWallpaper;
    java.util.UUID photoLockScreen;
    boolean photoPresentationMaximized;
    SnapZone photoPresentationSnap;
    boolean descendingDesktopOrder;
    boolean preferencesLoaded;
    PcFileSystem fileSystem;
    final Map<String, PcFileSystem> appDrives = new HashMap<>();
    final PcNotifications notifications = new PcNotifications();
    final PcClock clock = new PcClock();

    record Cell(int column, int row) {
        Cell translate(int columns, int rows) {
            return new Cell(column + columns, row + rows);
        }
    }

    enum ResizeEdge {
        LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1),
        TOP_LEFT(-1, -1), TOP_RIGHT(1, -1), BOTTOM_LEFT(-1, 1), BOTTOM_RIGHT(1, 1);

        final int horizontal;
        final int vertical;

        ResizeEdge(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        static ResizeEdge at(double x, double y, int width, int height, int border) {
            if (x < -border || y < -border || x >= width + border || y >= height + border) {
                return null;
            }
            int horizontal = x < border ? -1 : x >= width - border ? 1 : 0;
            int vertical = y < border ? -1 : y >= height - border ? 1 : 0;
            int corner = 3 * border;
            if (vertical != 0) {
                horizontal = x < corner ? -1 : x >= width - corner ? 1 : horizontal;
            } else if (horizontal != 0) {
                vertical = y < corner ? -1 : y >= height - corner ? 1 : vertical;
            }
            return of(horizontal, vertical);
        }

        ResizeEdge allowedBy(PcWindowOptions options) {
            return of(options.minWidth() == options.maxWidth() ? 0 : horizontal,
                    options.minHeight() == options.maxHeight() ? 0 : vertical);
        }

        private static ResizeEdge of(int horizontal, int vertical) {
            for (var edge : VALUES) {
                if (edge.horizontal == horizontal && edge.vertical == vertical) {
                    return edge;
                }
            }
            return null;
        }

        private static final ResizeEdge[] VALUES = values();
    }

    record Bounds(double x, double y, int width, int height) {
        Bounds resize(ResizeEdge edge, double dx, double dy, int workWidth, int workHeight,
                      PcWindowOptions options) {
            int w = trackSize(x, width, dx, edge.horizontal, workWidth, options.minWidth(), options.maxWidth());
            int h = trackSize(y, height, dy, edge.vertical, workHeight, options.minHeight(), options.maxHeight());
            return new Bounds(edge.horizontal < 0 ? x + width - w : x,
                    edge.vertical < 0 ? y + height - h : y, w, h);
        }

        private static int trackSize(double origin, int size, double delta, int direction, int limit,
                                     int minimum, int maximum) {
            if (direction == 0) {
                return size;
            }
            maximum = Math.min(maximum, (int) Math.floor(direction < 0 ? origin + size : limit - origin));
            return (int) Math.clamp(size + Math.round(delta * direction), Math.min(minimum, maximum), maximum);
        }
    }

    enum SnapZone {
        LEFT(0, 0, 1, 2), RIGHT(1, 0, 2, 2),
        TOP_LEFT(0, 0, 1, 1), TOP_RIGHT(1, 0, 2, 1),
        BOTTOM_LEFT(0, 1, 1, 2), BOTTOM_RIGHT(1, 1, 2, 2),
        MAXIMIZE(0, 0, 2, 2);

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        SnapZone(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        Bounds bounds(int width, int height) {
            int x = (int) ((long) left * width / 2);
            int y = (int) ((long) top * height / 2);
            return new Bounds(x, y, (int) ((long) right * width / 2) - x, (int) ((long) bottom * height / 2) - y);
        }
    }

    void open(A app, double x, double y) {
        var window = stack.computeIfAbsent(app, key -> new Window<>(key, x, y));
        window.minimized = false;
        window.hiddenByShowDesktop = false;
        stack.putLast(app, window);
        recent.remove(app);
        recent.addFirst(app);
    }

    void toggleShowDesktop() {
        boolean restore = stack.values().stream().anyMatch(window -> window.hiddenByShowDesktop);
        for (var window : stack.values()) {
            if (restore) {
                if (window.hiddenByShowDesktop) {
                    window.hiddenByShowDesktop = false;
                    window.minimized = false;
                }
            } else if (!window.minimized) {
                window.hiddenByShowDesktop = true;
                window.minimized = true;
            }
        }
    }

    void close(A app) {
        var window = stack.remove(app);
        if (window != null) {
            window.disposeContent();
        }
    }

    void closeAll() {
        for (A app : List.copyOf(stack.keySet())) {
            close(app);
        }
    }

    void forget(A app) {
        close(app);
        pins.remove(app);
        recent.remove(app);
    }

    boolean isOpen(A app) {
        return stack.containsKey(app);
    }

    Window<A> get(A app) {
        return stack.get(app);
    }

    boolean isPinned(A app, boolean defaultPinned) {
        return pins.getOrDefault(app, defaultPinned);
    }

    void setPinned(A app, boolean pinned) {
        pins.put(app, pinned);
    }

    SequencedCollection<A> recent() {
        return recent;
    }

    Collection<Window<A>> backToFront() {
        return stack.sequencedValues();
    }

    SequencedCollection<Window<A>> frontToBack() {
        return stack.sequencedValues().reversed();
    }

    Window<A> active() {
        for (var window : frontToBack()) {
            if (!window.minimized) {
                return window;
            }
        }
        return null;
    }

    static final class Window<A> {
        final A app;
        double x;
        double y;
        int width;
        int height;
        boolean minimized;
        boolean hiddenByShowDesktop;
        boolean maximized;
        SnapZone snapped;
        PcAppContent content;
        int contentX;
        int contentY;
        int contentWidth = -1;
        int contentHeight = -1;

        Window(A app, double x, double y) {
            this.app = app;
            this.x = x;
            this.y = y;
        }

        void disposeContent() {
            var closing = content;
            content = null;
            if (closing != null) {
                try {
                    closing.closed();
                } catch (RuntimeException e) {
                    UltimateBankingSystem.LOGGER.warn("PC app failed while closing", e);
                }
            }
        }

        void move(double x, double y, int maxX, int maxY) {
            this.x = Math.clamp(x, 0, maxX);
            this.y = Math.clamp(y, 0, maxY);
        }
    }
}
