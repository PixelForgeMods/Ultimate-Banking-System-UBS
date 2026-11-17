package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.hud.HudPosition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-frame placement service for UBS overlays that share the vanilla HUD. */
@OnlyIn(Dist.CLIENT)
public final class HudLayoutManager {
    private static final int DEFAULT_GAP = 6;
    private static final Map<String, Bounds> RESERVED = new LinkedHashMap<>();
    private static int screenWidth;
    private static int screenHeight;

    private HudLayoutManager() {
    }

    public record Bounds(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean intersects(Bounds other, int gap) {
            if (other == null || width <= 0 || height <= 0 || other.width <= 0 || other.height <= 0) {
                return false;
            }
            int safeGap = Math.max(0, gap);
            return x < other.right() + safeGap
                    && right() + safeGap > other.x
                    && y < other.bottom() + safeGap
                    && bottom() + safeGap > other.y;
        }
    }

    /** Starts a fresh reservation pass and protects the vanilla hotbar/boss-bar bands. */
    public static void beginFrame(int width, int height) {
        screenWidth = Math.max(0, width);
        screenHeight = Math.max(0, height);
        RESERVED.clear();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        reserveInternal("vanilla.boss-bar", new Bounds(
                Math.max(0, screenWidth / 2 - 180), 0, Math.min(360, screenWidth), 42));
        reserveInternal("vanilla.hotbar", new Bounds(
                Math.max(0, screenWidth / 2 - 100), Math.max(0, screenHeight - 46),
                Math.min(200, screenWidth), Math.min(46, screenHeight)));
    }

    public static Bounds placeTopCenter(String id, int width, int height, int padding) {
        return placeVertical(id, width, height, padding, true);
    }

    public static Bounds placeTopRight(String id, int width, int height, int padding) {
        return placeCorner(id, width, height, padding, true, false);
    }

    public static Bounds placeBottomRight(String id, int width, int height, int padding) {
        return placeCorner(id, width, height, padding, true, true);
    }

    public static Bounds placeBottomCenter(String id, int width, int height, int padding) {
        return placeVertical(id, width, height, padding, false);
    }

    public static Bounds placeRightCenter(String id, int width, int height, int padding) {
        return placeCenterRight(id, width, height, padding);
    }

    public static Bounds placeConfigured(String id, int width, int height,
                                          HudPosition position, int padding) {
        HudPosition safePosition = position == null ? HudPosition.TOP_RIGHT : position;
        if (safePosition.isMiddle()) {
            return safePosition.isRight()
                    ? placeRightCenter(id, width, height, padding)
                    : placeCenterLeft(id, width, height, padding);
        }
        return placeCorner(id, width, height, padding, safePosition.isRight(), safePosition.isBottom());
    }

    /** Reserves a fixed-position overlay so later widgets can move around it. */
    public static Bounds reserve(String id, Bounds bounds) {
        if (id == null || id.isBlank() || bounds == null) {
            return bounds;
        }
        RESERVED.remove(id);
        RESERVED.put(id, bounds);
        return bounds;
    }

    public static boolean overlaps(Bounds bounds) {
        return intersectsAny("", bounds);
    }

    private static Bounds placeCorner(String id, int width, int height, int padding,
                                      boolean right, boolean bottom) {
        int safeWidth = Math.max(1, Math.min(width, screenWidth));
        int safeHeight = Math.max(1, Math.min(height, screenHeight));
        int safePadding = Math.max(0, padding);
        int x = right
                ? Math.max(0, screenWidth - safeWidth - safePadding)
                : Math.min(safePadding, Math.max(0, screenWidth - safeWidth));
        int y = bottom
                ? Math.max(0, screenHeight - safeHeight - safePadding)
                : Math.min(safePadding, Math.max(0, screenHeight - safeHeight));
        Bounds preferred = new Bounds(x, y, safeWidth, safeHeight);
        if (!intersectsAny(id, preferred)) {
            return reserve(id, preferred);
        }

        int direction = bottom ? -1 : 1;
        int step = safeHeight + DEFAULT_GAP;
        for (int candidateY = y + direction * step;
             candidateY >= safePadding && candidateY + safeHeight <= screenHeight - safePadding;
             candidateY += direction * step) {
            Bounds candidate = new Bounds(x, candidateY, safeWidth, safeHeight);
            if (!intersectsAny(id, candidate)) {
                return reserve(id, candidate);
            }
        }

        int sideX = right
                ? Math.min(safePadding, Math.max(0, screenWidth - safeWidth))
                : Math.max(0, screenWidth - safeWidth - safePadding);
        Bounds oppositeSide = new Bounds(sideX, y, safeWidth, safeHeight);
        if (!intersectsAny(id, oppositeSide)) {
            return reserve(id, oppositeSide);
        }
        return reserve(id, clamp(preferred));
    }

    private static Bounds placeVertical(String id, int width, int height, int padding, boolean top) {
        int safeWidth = Math.max(1, Math.min(width, screenWidth));
        int safeHeight = Math.max(1, Math.min(height, screenHeight));
        int safePadding = Math.max(0, padding);
        int x = Math.max(0, (screenWidth - safeWidth) / 2);
        int y = top
                ? safePadding
                : Math.max(0, screenHeight - safeHeight - safePadding);
        Bounds preferred = new Bounds(x, y, safeWidth, safeHeight);
        if (!intersectsAny(id, preferred)) {
            return reserve(id, preferred);
        }

        int direction = top ? 1 : -1;
        int step = safeHeight + DEFAULT_GAP;
        for (int candidateY = y + direction * step;
             candidateY >= safePadding && candidateY + safeHeight <= screenHeight - safePadding;
             candidateY += direction * step) {
            Bounds candidate = new Bounds(x, candidateY, safeWidth, safeHeight);
            if (!intersectsAny(id, candidate)) {
                return reserve(id, candidate);
            }
        }
        return reserve(id, clamp(preferred));
    }

    private static Bounds placeCenterRight(String id, int width, int height, int padding) {
        return placeCenterSide(id, width, height, padding, true);
    }

    private static Bounds placeCenterLeft(String id, int width, int height, int padding) {
        return placeCenterSide(id, width, height, padding, false);
    }

    private static Bounds placeCenterSide(String id, int width, int height, int padding, boolean right) {
        int safeWidth = Math.max(1, Math.min(width, screenWidth));
        int safeHeight = Math.max(1, Math.min(height, screenHeight));
        int safePadding = Math.max(0, padding);
        int x = right
                ? Math.max(0, screenWidth - safeWidth - safePadding)
                : Math.min(safePadding, Math.max(0, screenWidth - safeWidth));
        int y = Math.max(safePadding, (screenHeight - safeHeight) / 2);
        Bounds preferred = new Bounds(x, y, safeWidth, safeHeight);
        if (!intersectsAny(id, preferred)) {
            return reserve(id, preferred);
        }

        List<Integer> candidates = new ArrayList<>();
        candidates.add(safePadding);
        candidates.add(Math.max(safePadding, screenHeight - safeHeight - safePadding));
        for (Bounds occupied : RESERVED.values()) {
            candidates.add(occupied.y - safeHeight - DEFAULT_GAP);
            candidates.add(occupied.bottom() + DEFAULT_GAP);
        }
        for (int candidateY : candidates) {
            Bounds candidate = new Bounds(x, candidateY, safeWidth, safeHeight);
            if (candidate.y >= safePadding
                    && candidate.bottom() <= screenHeight - safePadding
                    && !intersectsAny(id, candidate)) {
                return reserve(id, candidate);
            }
        }
        return reserve(id, clamp(preferred));
    }

    private static boolean intersectsAny(String id, Bounds candidate) {
        if (candidate == null) {
            return false;
        }
        for (Map.Entry<String, Bounds> entry : RESERVED.entrySet()) {
            if (entry.getKey().equals(id)) {
                continue;
            }
            if (candidate.intersects(entry.getValue(), DEFAULT_GAP)) {
                return true;
            }
        }
        return false;
    }

    private static void reserveInternal(String id, Bounds bounds) {
        RESERVED.put(id, bounds);
    }

    private static Bounds clamp(Bounds bounds) {
        int width = Math.max(1, Math.min(bounds.width(), screenWidth));
        int height = Math.max(1, Math.min(bounds.height(), screenHeight));
        int x = Math.max(0, Math.min(bounds.x(), screenWidth - width));
        int y = Math.max(0, Math.min(bounds.y(), screenHeight - height));
        return new Bounds(x, y, width, height);
    }
}
