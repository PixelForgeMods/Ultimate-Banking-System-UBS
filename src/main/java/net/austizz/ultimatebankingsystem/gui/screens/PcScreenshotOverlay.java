package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class PcScreenshotOverlay {
    private static final int DIM = 0x78090C14;
    private static final int PANEL = 0xFF232A38;
    private static final int BORDER = 0xFF59687C;
    private static final int HOVER = 0xFF39485C;
    private static final int TEXT = 0xFFF2F6FB;
    private static final int ACCENT = 0xFF82DBEF;
    private static final int INK = 0xFFFF9A78;
    private static final int SLOT = 27;
    private static final int TOOLS = 5;
    private static final int BAR_Y = 12;

    private enum Mode {
        RECTANGLE("Rectangle", PcScreenshotIcon.RECTANGLE),
        WINDOW("Window", PcScreenshotIcon.WINDOW),
        FULL_SCREEN("Full screen", PcScreenshotIcon.FULL_SCREEN),
        FREEFORM("Freeform", PcScreenshotIcon.LASSO);

        final String label;
        final PcScreenshotIcon icon;
        Mode(String label, PcScreenshotIcon icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private final Consumer<BufferedImage> save;
    private final Consumer<String> copyColor;
    private Mode mode = Mode.RECTANGLE;
    private boolean active;
    private boolean quickMarkup;
    private boolean pickingColor;
    private boolean menu;
    private int choice;
    private int focusedTool = -1;
    private int width;
    private int height;
    private BufferedImage frame;
    private BufferedImage markup;
    private PcPhotoTexture texture;
    private List<Rectangle> windows = List.of();
    private Point start;
    private final List<Point> outline = new ArrayList<>();
    private final List<List<Point>> strokes = new ArrayList<>();
    private List<Point> stroke;

    PcScreenshotOverlay(Consumer<BufferedImage> save, Consumer<String> copyColor) {
        this.save = save;
        this.copyColor = copyColor;
    }

    void begin() { active = true; }
    boolean active() { return active; }
    boolean ready() { return frame != null; }

    boolean crosshair(int x, int y) {
        if (!ready()) return false;
        if (markup != null) return editBox().contains(x, y);
        return pickingColor || start != null
                || mode != Mode.WINDOW && !tools().contains(x, y) && !(menu && menuBounds().contains(x, y));
    }

    void freeze(int width, int height, List<Rectangle> windows) {
        this.width = width;
        this.height = height;
        this.windows = windows;
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            frame = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            int[] row = new int[image.getWidth()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < row.length; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    row[x] = (abgr & 0x00FF00) | (abgr >>> 16 & 0xFF) | (abgr & 0xFF) << 16;
                }
                frame.setRGB(0, y, row.length, 1, row, 0, row.length);
            }
        }
        texture = PcPhotoTexture.upload(frame);
    }

    void close() {
        if (texture != null) texture.close();
        texture = null;
        frame = markup = null;
        active = menu = pickingColor = false;
        start = null;
        outline.clear();
        stroke = null;
        strokes.clear();
        windows = List.of();
        focusedTool = -1;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (markup != null) {
            renderMarkup(graphics, mouseX, mouseY);
            return;
        }
        texture.draw(graphics, 0, 0, width, height);
        var mouse = point(mouseX, mouseY);
        var area = selection(mouse);
        if (start != null && mode == Mode.FREEFORM && outline.size() >= 3) {
            shadeOutsideOutline(graphics);
            for (int i = 1; i < outline.size(); i++) line(graphics, outline.get(i - 1), outline.get(i), TEXT, true);
            line(graphics, outline.getLast(), outline.getFirst(), TEXT, true);
        } else {
            shadeOutside(graphics, area);
            if (!area.isEmpty()) border(graphics, area);
        }
        if (start != null) {
            var pixels = pixels(area);
            if (!pixels.isEmpty()) badge(graphics, pixels.width + " × " + pixels.height, mouseX + 10, mouseY + 10);
        } else if (pickingColor) {
            int argb = colorAt(mouse);
            graphics.fill(mouse.x - 3, mouse.y - 3, mouse.x + 4, mouse.y + 4, argb);
            graphics.renderOutline(mouse.x - 4, mouse.y - 4, 9, 9, TEXT);
            badge(graphics, hex(argb) + " · Click to copy · Esc to cancel", mouseX + 10, mouseY + 10);
        } else {
            renderTools(graphics, mouseX, mouseY);
        }
    }

    boolean keyPressed(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
        } else if (markup != null) {
            if (key == GLFW.GLFW_KEY_ENTER || (key == GLFW.GLFW_KEY_S && has(modifiers, GLFW.GLFW_MOD_CONTROL))) {
                saveMarkup();
            } else if (key == GLFW.GLFW_KEY_Z && has(modifiers, GLFW.GLFW_MOD_CONTROL)) {
                undo();
            }
        } else if (!ready() || start != null) {
            return true;
        } else if (key == GLFW.GLFW_KEY_E && has(modifiers, GLFW.GLFW_MOD_CONTROL)) {
            quickMarkup = !quickMarkup;
        } else if (key == GLFW.GLFW_KEY_M && has(modifiers, GLFW.GLFW_MOD_ALT)) {
            toggleMenu();
        } else if (menu && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            choice = Math.floorMod(choice + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), Mode.values().length);
        } else if (key == GLFW.GLFW_KEY_TAB && !menu && !pickingColor) {
            boolean reverse = has(modifiers, GLFW.GLFW_MOD_SHIFT);
            focusedTool = focusedTool < 0 ? (reverse ? TOOLS - 1 : 0)
                    : Math.floorMod(focusedTool + (reverse ? -1 : 1), TOOLS);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (menu) selectMode(choice);
            else if (focusedTool >= 0 && !pickingColor) activateTool(focusedTool);
            else if (mode == Mode.FULL_SCREEN && !pickingColor) capture(screen(), List.of());
            else if (mode == Mode.WINDOW && !windows.isEmpty() && !pickingColor) capture(windows.getFirst(), List.of());
        }
        return true;
    }

    void mouseClicked(double x, double y) {
        if (!ready()) return;
        var point = point(x, y);
        if (markup != null) {
            int control = markupTool(point);
            if (control == 0) undo();
            else if (control == 1) close();
            else if (control == 2) saveMarkup();
            else if (editBox().contains(point)) {
                stroke = new ArrayList<>();
                strokes.add(stroke);
                stroke.add(editPoint(point));
            }
        } else if (pickingColor) {
            String color = hex(colorAt(point));
            close();
            copyColor.accept(color);
        } else if (menu && menuBounds().contains(point)) {
            selectMode((point.y - menuBounds().y - 3) / 19);
        } else if (tools().contains(point)) {
            int tool = (point.x - tools().x - 4) / SLOT;
            if (tool < TOOLS) activateTool(tool);
        } else if (menu) {
            menu = false;
        } else if (mode == Mode.FULL_SCREEN) {
            capture(screen(), List.of());
        } else if (mode == Mode.WINDOW) {
            var area = selection(point);
            if (!area.isEmpty()) capture(area, List.of());
        } else {
            start = point;
            outline.clear();
            outline.add(point);
        }
    }

    void mouseDragged(double x, double y) {
        var point = point(x, y);
        if (stroke != null) {
            var next = editPoint(point);
            if (!stroke.getLast().equals(next)) stroke.add(next);
        } else if (start != null && mode == Mode.FREEFORM) {
            if (outline.getLast().distanceSq(point) >= 4) outline.add(point);
        }
    }

    void mouseReleased(double x, double y) {
        if (stroke != null) {
            mouseDragged(x, y);
            stroke = null;
        }
        if (start == null) return;
        var point = point(x, y);
        if (mode == Mode.FREEFORM && !outline.getLast().equals(point)) outline.add(point);
        var area = selection(point);
        start = null;
        if (mode != Mode.FREEFORM || outline.size() >= 3) {
            capture(area, mode == Mode.FREEFORM ? outline : List.of());
        }
        outline.clear();
    }

    private Rectangle selection(Point mouse) {
        if (pickingColor || menu) return new Rectangle();
        if (mode == Mode.WINDOW && !tools().contains(mouse)) {
            for (var window : windows) if (window.contains(mouse)) return window.intersection(screen());
        }
        if (start == null) return new Rectangle();
        if (mode != Mode.FREEFORM) return PcScreenshotImage.bounds(start, mouse);
        var area = new Rectangle(start);
        for (var point : outline) area.add(point);
        return area;
    }

    private Rectangle pixels(Rectangle area) {
        return PcScreenshotImage.toPixels(area, width, height, frame.getWidth(), frame.getHeight());
    }

    private Rectangle screen() { return new Rectangle(0, 0, width, height); }

    private Point point(double x, double y) {
        return new Point(Math.clamp((int) Math.round(x), 0, width), Math.clamp((int) Math.round(y), 0, height));
    }

    private int colorAt(Point point) {
        int x = Math.min(frame.getWidth() - 1, (int) ((long) point.x * frame.getWidth() / width));
        int y = Math.min(frame.getHeight() - 1, (int) ((long) point.y * frame.getHeight() / height));
        return frame.getRGB(x, y);
    }

    private static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }

    private static boolean has(int modifiers, int mask) { return (modifiers & mask) != 0; }

    private void capture(Rectangle area, List<Point> path) {
        var image = PcScreenshotImage.crop(frame, pixels(area), path, width, height);
        if (image == null) return;
        if (quickMarkup) {
            markup = image;
            texture.close();
            texture = PcPhotoTexture.upload(markup);
        } else {
            close();
            save.accept(image);
        }
    }

    private Rectangle tools() { return new Rectangle((width - SLOT * TOOLS - 8) / 2, BAR_Y, SLOT * TOOLS + 8, 30); }
    private Rectangle menuBounds() { return new Rectangle(tools().x + SLOT, BAR_Y + 34, 116, Mode.values().length * 19 + 6); }

    private void activateTool(int tool) {
        focusedTool = tool;
        switch (tool) {
            case 0 -> {
                menu = false;
                if (mode == Mode.FULL_SCREEN) capture(screen(), List.of());
            }
            case 1 -> toggleMenu();
            case 2 -> { quickMarkup = !quickMarkup; menu = false; }
            case 3 -> { pickingColor = true; menu = false; }
            case 4 -> close();
        }
    }

    private void toggleMenu() {
        menu = !menu;
        choice = mode.ordinal();
    }

    private void selectMode(int index) {
        if (index >= Mode.values().length) return;
        mode = Mode.values()[index];
        menu = false;
        if (mode == Mode.FULL_SCREEN) capture(screen(), List.of());
    }

    private void renderTools(GuiGraphics graphics, int x, int y) {
        var bar = tools();
        panel(graphics, bar);
        String[] hints = {"Screenshot", "Capture mode", "Quick markup (Ctrl+E)", "Color picker", "Cancel (Esc)"};
        var mouse = new Point(x, y);
        int hovered = -1;
        for (int i = 0; i < TOOLS; i++) {
            var button = new Rectangle(bar.x + 4 + i * SLOT, bar.y + 4, SLOT, bar.height - 8);
            boolean over = button.contains(mouse);
            if (over) hovered = i;
            if (over || focusedTool == i || i == 0 || i == 2 && quickMarkup) {
                PcDesktopScreen.roundRect(graphics, button.x + 1, button.y, button.width - 2, button.height, HOVER);
            }
            var glyph = switch (i) {
                case 0 -> PcScreenshotIcon.CAMERA;
                case 1 -> mode.icon;
                case 2 -> PcScreenshotIcon.MARKUP;
                case 3 -> PcScreenshotIcon.PIPETTE;
                default -> PcScreenshotIcon.CLOSE;
            };
            glyph.draw(graphics, button.x + (i == 1 ? 3 : 5), button.y + 2, 16, i == 2 && quickMarkup ? ACCENT : TEXT);
            if (i == 1) PcScreenshotIcon.CHEVRON_DOWN.draw(graphics, button.x + 18, button.y + 7, 8, TEXT);
            if (i == 0 || i == 2 && quickMarkup) graphics.fill(button.x + 10, button.y + 19, button.x + 17, button.y + 21, ACCENT);
        }
        if (menu) {
            var box = menuBounds();
            panel(graphics, box);
            for (var option : Mode.values()) {
                int top = box.y + 3 + option.ordinal() * 19;
                var row = new Rectangle(box.x + 3, top, box.width - 6, 19);
                if (option.ordinal() == choice || row.contains(mouse)) {
                    PcDesktopScreen.roundRect(graphics, row.x, row.y, row.width, row.height, HOVER);
                }
                if (option == mode) graphics.fill(row.x + 2, top + 4, row.x + 4, top + 15, ACCENT);
                option.icon.draw(graphics, row.x + 9, top + 2, 15, TEXT);
                graphics.drawString(Minecraft.getInstance().font, PcDesktopScreen.label(option.label),
                        row.x + 29, top + 6, TEXT, false);
            }
        } else if (hovered >= 0) {
            badge(graphics, hints[hovered], x + 7, bar.y + bar.height + 4);
        } else {
            String hint = switch (mode) {
                case RECTANGLE -> "Drag to capture a rectangle";
                case WINDOW -> "Click a window to capture it";
                case FULL_SCREEN -> "Click to capture the full screen";
                case FREEFORM -> "Draw around the area to capture";
            };
            var font = Minecraft.getInstance().font;
            badge(graphics, hint, (width - font.width(PcDesktopScreen.label(hint)) - 12) / 2, bar.y + bar.height + 6);
        }
    }

    private void shadeOutside(GuiGraphics graphics, Rectangle area) {
        if (area.isEmpty()) {
            graphics.fill(0, 0, width, height, DIM);
            return;
        }
        int right = area.x + area.width, bottom = area.y + area.height;
        graphics.fill(0, 0, width, area.y, DIM);
        graphics.fill(0, bottom, width, height, DIM);
        graphics.fill(0, area.y, area.x, bottom, DIM);
        graphics.fill(right, area.y, width, bottom, DIM);
    }

    private void shadeOutsideOutline(GuiGraphics graphics) {
        var crossings = new ArrayList<Integer>();
        for (int y = 0; y < height; y++) {
            crossings.clear();
            Point previous = outline.getLast();
            for (var point : outline) {
                double scan = y + 0.5;
                if ((previous.y > scan) != (point.y > scan)) {
                    int x = (int) Math.round(previous.x + (scan - previous.y) * (point.x - previous.x) / (point.y - previous.y));
                    crossings.add(x);
                }
                previous = point;
            }
            crossings.sort(null);
            int left = 0;
            for (int i = 0; i + 1 < crossings.size(); i += 2) {
                int end = crossings.get(i);
                if (end > left) graphics.fill(left, y, end, y + 1, DIM);
                left = crossings.get(i + 1);
            }
            if (left < width) graphics.fill(left, y, width, y + 1, DIM);
        }
    }

    private static void border(GuiGraphics graphics, Rectangle area) {
        int right = area.x + area.width, bottom = area.y + area.height;
        line(graphics, new Point(area.x, area.y), new Point(right, area.y), TEXT, true);
        line(graphics, new Point(right, area.y), new Point(right, bottom), TEXT, true);
        line(graphics, new Point(area.x, bottom), new Point(right, bottom), TEXT, true);
        line(graphics, new Point(area.x, area.y), new Point(area.x, bottom), TEXT, true);
    }

    private static void line(GuiGraphics graphics, Point a, Point b, int color, boolean dashed) {
        int count = Math.max(1, Math.max(Math.abs(b.x - a.x), Math.abs(b.y - a.y)));
        int size = dashed ? 1 : color == INK ? 3 : 2;
        for (int i = 0; i <= count; i++) {
            if (dashed && i % 5 >= 3) continue;
            int x = a.x + (b.x - a.x) * i / count;
            int y = a.y + (b.y - a.y) * i / count;
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private static void panel(GuiGraphics graphics, Rectangle box) {
        PcDesktopScreen.roundRect(graphics, box.x, box.y, box.width, box.height, BORDER);
        PcDesktopScreen.roundRect(graphics, box.x + 1, box.y + 1, box.width - 2, box.height - 2, PANEL);
    }

    private void badge(GuiGraphics graphics, String text, int x, int y) {
        var font = Minecraft.getInstance().font;
        var label = PcDesktopScreen.label(text);
        int w = font.width(label) + 12;
        x = Math.clamp(x, 0, Math.max(0, width - w - 3));
        y = Math.clamp(y, 0, Math.max(0, height - 21));
        panel(graphics, new Rectangle(x, y, w, 18));
        graphics.drawString(font, label, x + 6, y + 5, TEXT, false);
    }

    private Rectangle editBox() {
        double scale = Math.min((width - 24.0) / markup.getWidth(), (height - 70.0) / markup.getHeight());
        int w = Math.max(1, (int) (markup.getWidth() * scale));
        int h = Math.max(1, (int) (markup.getHeight() * scale));
        return new Rectangle((width - w) / 2, 52 + (Math.max(1, height - 70) - h) / 2, w, h);
    }

    private Rectangle markupBar() { return new Rectangle((width - 150) / 2, BAR_Y, 150, 30); }

    private int markupTool(Point point) {
        var bar = markupBar();
        return bar.contains(point) ? (point.x - bar.x) / 50 : -1;
    }

    private Point editPoint(Point point) {
        var box = editBox();
        return new Point(Math.clamp((int) ((long) (point.x - box.x) * markup.getWidth() / box.width), 0, markup.getWidth() - 1),
                Math.clamp((int) ((long) (point.y - box.y) * markup.getHeight() / box.height), 0, markup.getHeight() - 1));
    }

    private Point displayPoint(Rectangle box, Point point) {
        return new Point(box.x + (int) ((long) point.x * box.width / markup.getWidth()),
                box.y + (int) ((long) point.y * box.height / markup.getHeight()));
    }

    private void renderMarkup(GuiGraphics graphics, int x, int y) {
        graphics.fill(0, 0, width, height, 0xFF151C28);
        var box = editBox();
        graphics.fill(box.x, box.y, box.x + box.width, box.y + box.height, 0xFF303947);
        texture.draw(graphics, box.x, box.y, box.width, box.height);
        graphics.enableScissor(box.x, box.y, box.x + box.width, box.y + box.height);
        for (var points : strokes) {
            var previous = displayPoint(box, points.getFirst());
            for (var point : points) {
                var current = displayPoint(box, point);
                line(graphics, previous, current, INK, false);
                previous = current;
            }
        }
        graphics.disableScissor();
        var bar = markupBar();
        panel(graphics, bar);
        String[] labels = {"Undo", "Cancel", "Save"};
        PcScreenshotIcon[] icons = {PcScreenshotIcon.UNDO, PcScreenshotIcon.CLOSE, PcScreenshotIcon.SAVE};
        int hovered = markupTool(new Point(x, y));
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < labels.length; i++) {
            int left = bar.x + i * 50;
            if (hovered == i) PcDesktopScreen.roundRect(graphics, left + 3, bar.y + 4, 44, 22, HOVER);
            var label = PcDesktopScreen.label(labels[i]);
            int color = i == 2 ? ACCENT : TEXT;
            int iconX = left + (50 - 14 - font.width(label)) / 2;
            icons[i].draw(graphics, iconX, bar.y + 9, 11, color);
            graphics.drawString(font, label, iconX + 14, bar.y + 11, color, false);
        }
    }

    private void undo() {
        stroke = null;
        if (!strokes.isEmpty()) strokes.removeLast();
    }

    private void saveMarkup() {
        var result = markup;
        var canvas = result.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(new Color(INK, true));
            float weight = Math.max(1, 3f * result.getWidth() / editBox().width);
            canvas.setStroke(new BasicStroke(weight, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (var points : strokes) {
                var previous = points.getFirst();
                canvas.fillOval(previous.x, previous.y, (int) Math.ceil(weight), (int) Math.ceil(weight));
                for (var point : points) {
                    canvas.drawLine(previous.x, previous.y, point.x, point.y);
                    previous = point;
                }
            }
        } finally {
            canvas.dispose();
        }
        close();
        save.accept(result);
    }
}
