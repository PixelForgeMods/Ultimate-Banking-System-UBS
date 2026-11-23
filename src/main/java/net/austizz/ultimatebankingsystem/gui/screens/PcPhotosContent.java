package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.austizz.ultimatebankingsystem.api.pc.PcAppServices;
import net.austizz.ultimatebankingsystem.api.pc.PcFileDialog;
import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class PcPhotosContent implements PcAppContent {
    private static final int CANVAS = 0xFF11151D;
    private static final int PANEL = 0xFF212A38;
    private static final int CARD = 0xFF313D50;
    private static final int TEXT = 0xFFF4F7FB;
    private static final int MUTED = 0xFFBBC7D9;
    private static final int ACCENT = 0xFF80D8CA;
    private static final int BAR = 24;
    private static final int THUMB = 56;
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 8;
    private static final int[] PRESETS = {10, 25, 50, 75, 100, 200, 300, 400, 500, 600, 700, 800};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    private record Box(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        double centerX() { return x + width / 2.0; }
        double centerY() { return y + height / 2.0; }
        boolean contains(double mx, double my) { return mx >= x && mx < right() && my >= y && my < bottom(); }
    }

    private record Button(Box bounds, Runnable activate) {}
    private record MenuItem(String label, Runnable activate) {}
    private enum Menu { MORE, OPEN, ZOOM, SET_AS }
    private enum Dialog { SAVE, RENAME, RESIZE, DELETE }
    private enum Drag { PAN, ZOOM, CROP, STRIP }

    private final PcFileSystem drive;
    private PcAppServices services;
    private Consumer<PcFileSystem.Entry> showInFiles;
    private Consumer<PcFileSystem.Entry> copyToFiles;
    private BiConsumer<PcFileSystem.Entry, Boolean> wallpaper;
    private Consumer<Boolean> fullscreen;
    private final Map<UUID, PcPhotoTexture> thumbs = new HashMap<>();
    private final Set<UUID> unavailableThumbs = new HashSet<>();
    private final List<Button> buttons = new ArrayList<>();
    private PcFileSystem.Entry entry;
    private BufferedImage original;
    private BufferedImage pixels;
    private PcPhotoTexture texture;
    private String format = "";
    private List<PcFileSystem.Entry> folderImages = List.of();
    private long revision = -1;
    private int stripOffset;
    private int infoScroll;
    private boolean filmstrip;
    private boolean info;
    private boolean editing;
    private boolean crop;
    private boolean presentation;
    private long nextSlide;
    private double zoom; // Zero follows the current viewport's fit ratio.
    private double panX;
    private double panY;
    private Drag drag;
    private double dragX;
    private double dragY;
    private double cropX;
    private double cropY;
    private Menu menu;
    private int menuIndex;
    private Dialog dialog;
    private EditBox input;
    private String message = "";
    private Box window = new Box(0, 0, 1, 1);
    private Box stage = window;
    private Box slider;

    PcPhotosContent(PcFileSystem drive, Consumer<PcFileSystem.Entry> showInFiles,
                    Consumer<PcFileSystem.Entry> copyToFiles, BiConsumer<PcFileSystem.Entry, Boolean> wallpaper,
                    Consumer<Boolean> fullscreen) {
        this.drive = drive;
        setHandlers(showInFiles, copyToFiles, wallpaper, fullscreen);
    }

    void setHandlers(Consumer<PcFileSystem.Entry> showInFiles, Consumer<PcFileSystem.Entry> copyToFiles,
                     BiConsumer<PcFileSystem.Entry, Boolean> wallpaper, Consumer<Boolean> fullscreen) {
        this.showInFiles = showInFiles;
        this.copyToFiles = copyToFiles;
        this.wallpaper = wallpaper;
        this.fullscreen = fullscreen;
    }

    static boolean canOpen(PcFileSystem.Entry candidate) {
        return candidate.binary() && PcPhotoImage.supports(candidate.name());
    }

    void open(PcFileSystem.Entry source) throws IOException {
        if (!canOpen(source)) throw new IOException("Unsupported image file.");
        var image = PcPhotoImage.decode(source.payload(), 0);
        var newTexture = PcPhotoTexture.upload(image.pixels());
        if (texture != null) texture.close();
        texture = newTexture;
        if (entry == null || !entry.parent().equals(source.parent())) clearThumbs();
        entry = source;
        original = pixels = image.pixels();
        format = image.format();
        editing = crop = false;
        dialog = null;
        input = null;
        menu = null;
        infoScroll = 0;
        message = "";
        resetZoom();
        refreshFolder();
        revealPhoto();
    }

    private void refreshFolder() throws IOException {
        revision = drive.revision();
        folderImages = entry == null ? List.of() : drive.list(entry.parent(), "").stream()
                .filter(PcPhotosContent::canOpen).toList();
    }

    private int imageIndex() {
        if (entry == null) return -1;
        for (int i = 0; i < folderImages.size(); i++) {
            if (folderImages.get(i).id().equals(entry.id())) return i;
        }
        return -1;
    }

    private int visibleThumbs() {
        return Math.max(1, stage.width / THUMB);
    }

    private void revealPhoto() {
        int index = imageIndex();
        if (index < 0) return;
        stripOffset = Math.clamp(stripOffset, Math.max(0, index - visibleThumbs() + 1), index);
    }

    private void browse(int offset, boolean loop) {
        int index = imageIndex();
        for (int count = 0; count < folderImages.size() - 1; count++) {
            index += offset;
            if (!loop && (index < 0 || index >= folderImages.size())) return;
            index = Math.floorMod(index, folderImages.size());
            try {
                open(folderImages.get(index));
                return;
            } catch (IOException e) {
                message = e.getMessage();
            }
        }
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        window = new Box(x, y, width, height);
    }

    boolean presenting() { return presentation; }

    private boolean showFilmstrip() { return filmstrip && !editing && !presentation && entry != null; }
    private boolean showInfo() { return info && !editing && !presentation && entry != null; }

    private void computeStage() {
        int side = showInfo() ? Math.clamp(window.width / 3, 110, 180) : 0;
        int top = presentation ? 3 : (editing ? 54 : 30);
        int bottom = presentation ? 3 : BAR + (showFilmstrip() ? 46 : 0);
        stage = new Box(window.x + 5, window.y + top, Math.max(1, window.width - side - 10),
                Math.max(1, window.height - top - bottom));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entry != null && drive.revision() != revision) {
            try {
                entry = drive.get(entry.id());
                refreshFolder();
                clearThumbs();
            } catch (IOException e) {
                closePhoto();
                message = "The image is no longer available.";
            }
        }
        if (nextSlide != 0 && Util.getMillis() >= nextSlide) {
            browse(1, true);
            nextSlide = entry == null || folderImages.size() < 2 ? 0 : Util.getMillis() + 3000;
        }
        computeStage();
        clampPan();
        buttons.clear();
        slider = null;
        fill(graphics, window, CANVAS);
        if (texture == null) {
            button(graphics, new Box(stage.x + 10, stage.y + stage.height / 2 - 10, 112, 22),
                    "Open image", PcFilesIcon.OPEN, false, this::chooseImage, mouseX, mouseY);
        } else {
            drawImage(graphics);
        }
        if (showFilmstrip()) drawFilmstrip(graphics, mouseX, mouseY);
        if (showInfo()) drawInfo(graphics, mouseX, mouseY);
        if (!presentation) {
            drawToolbar(graphics, mouseX, mouseY);
            drawFooter(graphics, mouseX, mouseY);
        }
        if (texture != null && !editing && nextSlide == 0) drawArrows(graphics, mouseX, mouseY);
        if (presentation) {
            button(graphics, new Box(window.right() - 26, window.y + 6, 20, 18), "",
                    PcFilesIcon.CLOSE, false, this::togglePresentation, mouseX, mouseY);
        }
        if (menu != null) drawMenu(graphics, mouseX, mouseY);
        if (dialog != null) drawDialog(graphics, mouseX, mouseY, partialTick);
    }

    private void drawImage(GuiGraphics graphics) {
        var view = imageBounds();
        graphics.enableScissor(stage.x, stage.y, stage.right(), stage.bottom());
        try {
            texture.draw(graphics, view.x, view.y, view.width, view.height);
            if (drag == Drag.CROP) {
                int left = (int) Math.min(cropX, dragX);
                int top = (int) Math.min(cropY, dragY);
                int width = (int) Math.abs(cropX - dragX);
                int height = (int) Math.abs(cropY - dragY);
                if (width > 0 && height > 0) graphics.renderOutline(left, top, width, height, ACCENT);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private double guiScale() { return Minecraft.getInstance().getWindow().getGuiScale(); }

    private double effectiveZoom() {
        if (zoom != 0 || texture == null) return zoom == 0 ? 1 : zoom;
        return Math.min(1, Math.min(stage.width * guiScale() / texture.width(),
                stage.height * guiScale() / texture.height()));
    }

    private Box imageBounds() {
        double scale = effectiveZoom() / guiScale();
        int width = Math.max(1, (int) Math.round(texture.width() * scale));
        int height = Math.max(1, (int) Math.round(texture.height() * scale));
        return new Box((int) Math.round(stage.centerX() + panX - width / 2.0),
                (int) Math.round(stage.centerY() + panY - height / 2.0), width, height);
    }

    private void clampPan() {
        if (texture == null) return;
        var image = imageBounds();
        double maxX = Math.max(0, (image.width - stage.width) / 2.0);
        double maxY = Math.max(0, (image.height - stage.height) / 2.0);
        panX = Math.clamp(panX, -maxX, maxX);
        panY = Math.clamp(panY, -maxY, maxY);
    }

    private void zoomAt(double target, double mouseX, double mouseY) {
        if (texture == null) return;
        double current = effectiveZoom();
        zoom = Math.clamp(target, MIN_ZOOM, MAX_ZOOM);
        double ratio = zoom / current;
        panX = (panX + stage.centerX() - mouseX) * ratio - (stage.centerX() - mouseX);
        panY = (panY + stage.centerY() - mouseY) * ratio - (stage.centerY() - mouseY);
        clampPan();
    }

    private void zoomBy(double factor) {
        zoomAt(effectiveZoom() * factor, stage.centerX(), stage.centerY());
    }

    private void resetZoom() {
        zoom = 0;
        panX = panY = 0;
    }

    private void drawToolbar(GuiGraphics graphics, int mx, int my) {
        fill(graphics, new Box(window.x, window.y, window.width, BAR), PANEL);
        if (entry == null) {
            button(graphics, new Box(window.x + 5, window.y + 3, 89, 18), "Browse files",
                    PcFilesIcon.FOLDER_OPEN, false, () -> showInFiles.accept(null), mx, my);
            return;
        }
        int left = window.x + 5;
        int top = window.y + 3;
        button(graphics, new Box(left, top, 48, 18), editing ? "Done" : "Edit",
                PcFilesIcon.RENAME, editing, this::toggleEdit, mx, my);
        button(graphics, new Box(left + 52, top, 20, 18), "", PcFilesIcon.ROTATE_RIGHT, false,
                () -> transform(PcPhotoImage.rotate(pixels, true)), mx, my);
        button(graphics, new Box(left + 74, top, 20, 18), "", PcFilesIcon.DELETE, false,
                () -> beginDialog(Dialog.DELETE), mx, my);
        button(graphics, new Box(left + 96, top, 20, 18), "", PcFilesIcon.MORE, menu == Menu.MORE,
                () -> toggleMenu(Menu.MORE), mx, my);
        button(graphics, new Box(window.right() - 78, top, 73, 18), "Open with",
                PcFilesIcon.DOWN, menu == Menu.OPEN, () -> toggleMenu(Menu.OPEN), mx, my);
        label(graphics, entry.name() + (pixels != original ? " *" : ""), left + 121, top + 5,
                window.width - 210, MUTED);
        if (editing) drawEditTools(graphics, mx, my);
    }

    private void drawEditTools(GuiGraphics graphics, int mx, int my) {
        fill(graphics, new Box(window.x, window.y + BAR + 1, window.width, BAR), PANEL);
        int left = window.x + 5;
        int top = window.y + BAR + 4;
        button(graphics, new Box(left, top, 21, 18), "", PcFilesIcon.RESET, false,
                () -> transform(PcPhotoImage.rotate(pixels, false)), mx, my);
        button(graphics, new Box(left + 23, top, 45, 18), "Flip H", null, false,
                () -> transform(PcPhotoImage.flip(pixels, true)), mx, my);
        button(graphics, new Box(left + 70, top, 45, 18), "Flip V", null, false,
                () -> transform(PcPhotoImage.flip(pixels, false)), mx, my);
        button(graphics, new Box(left + 117, top, 40, 18), "Crop", null, crop, () -> {
            crop = !crop;
            if (crop) resetZoom();
        }, mx, my);
        button(graphics, new Box(left + 159, top, 42, 18), "Reset", null, false,
                () -> transform(original), mx, my);
        button(graphics, new Box(left + 203, top, 60, 18), "Save copy", null, false,
                () -> beginDialog(Dialog.SAVE), mx, my);
    }

    private void drawFooter(GuiGraphics graphics, int mx, int my) {
        int top = window.bottom() - BAR;
        fill(graphics, new Box(window.x, top, window.width, BAR), PANEL);
        if (entry == null) {
            label(graphics, message, window.x + 6, top + 7, window.width - 12, MUTED);
            return;
        }
        int left = window.x + 5;
        button(graphics, new Box(left, top + 3, 19, 18), "", PcFilesIcon.TILES, filmstrip, this::toggleFilmstrip, mx, my);
        button(graphics, new Box(left + 21, top + 3, 19, 18), "", PcFilesIcon.INFO, info, this::toggleInfo, mx, my);
        button(graphics, new Box(left + 42, top + 3, 19, 18), "", PcFilesIcon.PLAY, nextSlide != 0,
                this::toggleSlideshow, mx, my);
        int right = window.right() - 5;
        button(graphics, new Box(right - 20, top + 3, 20, 18), "", PcFilesIcon.EXTRA_LARGE, false,
                this::togglePresentation, mx, my);
        button(graphics, new Box(right - 43, top + 3, 19, 18), "", PcFilesIcon.ADD, false, () -> zoomBy(1.2), mx, my);
        button(graphics, new Box(right - 112, top + 3, 20, 18), "", PcFilesIcon.MINIMIZE, false, () -> zoomBy(1 / 1.2), mx, my);
        slider = new Box(right - 89, top + 5, 43, 14);
        drawSlider(graphics);
        button(graphics, new Box(right - 166, top + 3, 50, 18), Math.round(effectiveZoom() * 100) + "%",
                PcFilesIcon.DOWN, menu == Menu.ZOOM, () -> toggleMenu(Menu.ZOOM), mx, my);
        button(graphics, new Box(right - 196, top + 3, 27, 18), "Fit", null, zoom == 0, this::resetZoom, mx, my);
        String metadata = format + "  ·  " + pixels.getWidth() + " × " + pixels.getHeight() + "  ·  " + bytes(entry.size());
        label(graphics, message.isEmpty() ? metadata : message, left + 65, top + 7,
                right - 200 - (left + 65), message.isEmpty() ? MUTED : ACCENT);
        if (!message.isEmpty() && window.width < 430) {
            label(graphics, message, window.x + 8, stage.bottom() - 10, stage.width - 10, ACCENT);
        }
    }

    private void drawSlider(GuiGraphics graphics) {
        int center = slider.y + slider.height / 2;
        int thumb = slider.x + (int) Math.round(slider.width * Math.clamp(
                Math.log(effectiveZoom() / MIN_ZOOM) / Math.log(MAX_ZOOM / MIN_ZOOM), 0, 1));
        graphics.fill(slider.x, center - 1, slider.right(), center + 1, MUTED);
        graphics.fill(slider.x, center - 1, thumb, center + 1, ACCENT);
        graphics.fill(thumb - 2, center - 3, thumb + 3, center + 4, ACCENT);
    }

    private void moveSlider(double mx) {
        double ratio = Math.clamp((mx - slider.x) / slider.width, 0, 1);
        zoomAt(MIN_ZOOM * Math.pow(MAX_ZOOM / MIN_ZOOM, ratio), stage.centerX(), stage.centerY());
    }

    private void drawArrows(GuiGraphics graphics, int mx, int my) {
        int index = imageIndex();
        int top = (int) stage.centerY() - 14;
        if (index > 0) {
            button(graphics, new Box(stage.x + 2, top, 19, 28), "", PcFilesIcon.LEFT, false,
                    () -> browse(-1, false), mx, my);
        }
        if (index >= 0 && index < folderImages.size() - 1) {
            button(graphics, new Box(stage.right() - 21, top, 19, 28), "", PcFilesIcon.RIGHT, false,
                    () -> browse(1, false), mx, my);
        }
    }

    private void drawFilmstrip(GuiGraphics graphics, int mx, int my) {
        int top = stage.bottom() + 2;
        fill(graphics, new Box(stage.x, top, stage.width, 42), PANEL);
        int visible = visibleThumbs();
        stripOffset = Math.clamp(stripOffset, 0, Math.max(0, folderImages.size() - visible));
        Set<UUID> retain = new HashSet<>();
        for (int n = stripOffset; n < Math.min(stripOffset + visible, folderImages.size()); n++) {
            retain.add(folderImages.get(n).id());
        }
        thumbs.entrySet().removeIf(item -> {
            if (retain.contains(item.getKey())) return false;
            item.getValue().close();
            return true;
        });
        boolean loaded = false;
        for (int n = stripOffset; n < Math.min(stripOffset + visible, folderImages.size()); n++) {
            var photo = folderImages.get(n);
            int left = stage.x + (n - stripOffset) * THUMB;
            var tile = new Box(left + 1, top + 1, THUMB - 3, 37);
            boolean current = photo.id().equals(entry.id());
            fill(graphics, tile, current || tile.contains(mx, my) ? CARD : CANVAS);
            if (!loaded && !current && !thumbs.containsKey(photo.id()) && !unavailableThumbs.contains(photo.id())) {
                try {
                    thumbs.put(photo.id(), PcPhotoTexture.upload(PcPhotoImage.decode(photo.payload(), 112).pixels()));
                } catch (IOException e) {
                    unavailableThumbs.add(photo.id());
                }
                loaded = true;
            }
            PcPhotoTexture preview = current ? texture : thumbs.get(photo.id());
            if (preview == null) {
                PcFilesIcon.PICTURES.draw(graphics, tile.x + 19, tile.y + 10, 16, MUTED);
            } else {
                double scale = Math.min((tile.width - 4.0) / preview.width(), (tile.height - 4.0) / preview.height());
                int w = Math.max(1, (int) (preview.width() * scale));
                int h = Math.max(1, (int) (preview.height() * scale));
                preview.draw(graphics, tile.x + (tile.width - w) / 2, tile.y + (tile.height - h) / 2, w, h);
            }
            if (current) graphics.renderOutline(tile.x, tile.y, tile.width, tile.height, ACCENT);
            buttons.add(new Button(tile, () -> {
                try { open(photo); } catch (IOException e) { message = e.getMessage(); }
            }));
        }
        if (folderImages.size() > visible) {
            int thumbWidth = Math.max(8, stage.width * visible / folderImages.size());
            int left = stage.x + (stage.width - thumbWidth) * stripOffset / (folderImages.size() - visible);
            graphics.fill(left, top + 39, left + thumbWidth, top + 41, ACCENT);
        }
    }

    private Box infoBounds() {
        return new Box(stage.right() + 5, window.y + 26, window.right() - stage.right() - 10,
                Math.max(1, window.height - BAR - 28));
    }

    private void drawInfo(GuiGraphics graphics, int mx, int my) {
        var bounds = infoBounds();
        fill(graphics, bounds, PANEL);
        int left = bounds.x + 7;
        infoScroll = Math.clamp(infoScroll, 0, Math.max(0, 230 - bounds.height));
        int top = bounds.y + 7 - infoScroll;
        int width = bounds.width - 14;
        label(graphics, "Image info", left, bounds.y + 7, width, ACCENT);
        button(graphics, new Box(bounds.right() - 22, bounds.y + 2, 18, 17), "",
                PcFilesIcon.CLOSE, false, this::toggleInfo, mx, my);
        graphics.enableScissor(bounds.x, bounds.y + 22, bounds.right(), bounds.bottom());
        try {
            infoButton(graphics, bounds, new Box(left, top + 15, width, 16), "Show in Files", PcFilesIcon.FOLDER_OPEN,
                    () -> showInFiles.accept(entry), mx, my);
            infoButton(graphics, bounds, new Box(left, top + 33, width, 16), "Copy path", PcFilesIcon.COPY,
                    this::copyPath, mx, my);
            infoButton(graphics, bounds, new Box(left, top + 51, width, 16), "Rename", PcFilesIcon.RENAME,
                    () -> beginDialog(Dialog.RENAME), mx, my);
            infoButton(graphics, bounds, new Box(left, top + 69, width, 16), "Resize", PcFilesIcon.EXTRA_LARGE,
                    () -> beginDialog(Dialog.RESIZE), mx, my);
            label(graphics, "Name", left, top + 92, width, ACCENT);
            label(graphics, entry.name(), left, top + 104, width, TEXT);
            label(graphics, "Modified", left, top + 119, width, ACCENT);
            label(graphics, DATE.format(Instant.ofEpochMilli(entry.modified())), left, top + 131, width, MUTED);
            label(graphics, "Format", left, top + 146, width, ACCENT);
            label(graphics, format + "  ·  " + pixels.getWidth() + " × " + pixels.getHeight(), left, top + 158, width, TEXT);
            label(graphics, bytes(entry.size()), left, top + 170, width, MUTED);
            label(graphics, "Virtual path", left, top + 185, width, ACCENT);
            try {
                graphics.drawWordWrap(Minecraft.getInstance().font, PcDesktopScreen.label(drive.path(entry.id())),
                        left, top + 197, width, MUTED);
            } catch (IOException ignored) {
                // The selection will refresh on the next drive revision.
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void infoButton(GuiGraphics graphics, Box pane, Box bounds, String text, PcFilesIcon icon,
                            Runnable activate, int mx, int my) {
        if (bounds.y >= pane.y + 22 && bounds.bottom() <= pane.bottom()) {
            button(graphics, bounds, text, icon, false, activate, mx, my);
        }
    }

    private List<MenuItem> menuItems() {
        return switch (menu) {
            case MORE -> List.of(new MenuItem("Save as…", () -> beginDialog(Dialog.SAVE)),
                    new MenuItem("Copy image", this::copyImage),
                    new MenuItem("Resize image…", () -> beginDialog(Dialog.RESIZE)),
                    new MenuItem("Set as…", () -> toggleMenu(Menu.SET_AS)),
                    new MenuItem("Image info", this::toggleInfo));
            case SET_AS -> List.of(new MenuItem("Desktop background", () -> setBackground(false)),
                    new MenuItem("Lock screen background", () -> setBackground(true)),
                    new MenuItem("Restore desktop background", () -> restoreBackground(false)),
                    new MenuItem("Restore lock screen background", () -> restoreBackground(true)));
            case OPEN -> List.of(new MenuItem("Open image…", this::chooseImage), new MenuItem("Show in Files", () -> showInFiles.accept(entry)),
                    new MenuItem("Browse folder images", () -> { if (!filmstrip) toggleFilmstrip(); }));
            case ZOOM -> {
                var items = new ArrayList<MenuItem>();
                for (int i = PRESETS.length - 1; i >= 0; i--) {
                    int percent = PRESETS[i];
                    items.add(new MenuItem(percent + "%", () -> zoomAt(percent / 100.0, stage.centerX(), stage.centerY())));
                }
                yield items;
            }
        };
    }

    private void setBackground(boolean lockScreen) {
        if (pixels != original) {
            message = "Save a copy before using the edited image.";
        } else {
            wallpaper.accept(entry, lockScreen);
            message = lockScreen ? "Lock screen background updated." : "Desktop background updated.";
        }
    }

    private void restoreBackground(boolean lockScreen) {
        wallpaper.accept(null, lockScreen);
        message = lockScreen ? "Default lock screen restored." : "Default background restored.";
    }

    private Box menuBounds(List<MenuItem> items) {
        int width = menu == Menu.ZOOM ? 57 : 169;
        int height = items.size() * menuRowHeight(items) + 4;
        int left = switch (menu) {
            case MORE, SET_AS -> window.x + 9;
            case OPEN -> window.right() - width - 5;
            case ZOOM -> window.right() - 171;
        };
        return new Box(left, menu == Menu.ZOOM ? window.bottom() - BAR - height : window.y + BAR, width, height);
    }

    private int menuRowHeight(List<MenuItem> items) {
        return menu == Menu.ZOOM ? Math.clamp((window.height - BAR - 6) / items.size(), 10, 16) : 18;
    }

    private void drawMenu(GuiGraphics graphics, int mx, int my) {
        var items = menuItems();
        var bounds = menuBounds(items);
        int row = menuRowHeight(items);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 4);
        try {
            fill(graphics, bounds, PANEL);
            graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, CARD);
            for (int index = 0; index < items.size(); index++) {
                var item = items.get(index);
                var cell = new Box(bounds.x + 2, bounds.y + 2 + index * row, bounds.width - 4, row);
                if (cell.contains(mx, my) || index == menuIndex) fill(graphics, cell, CARD);
                label(graphics, item.label, cell.x + 5, cell.y + (row - 9) / 2, cell.width - 8, TEXT);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private void toggleMenu(Menu requested) {
        menu = menu == requested ? null : requested;
        menuIndex = 0;
    }

    @Override public void setServices(PcAppServices services) { this.services = services; }

    private void beginDialog(Dialog type) {
        menu = null;
        if (type == Dialog.SAVE && services != null) {
            var source = entry;
            var image = pixels;
            boolean edited = image != original;
            String name = copyName();
            var filters = List.of(new PcFileDialog.Filter("Image file", List.of(PcFileType.extension(name))));
            services.pick(new PcFileDialog.Request(PcFileDialog.Mode.SAVE, source.parent(), name, filters, false), result -> {
                byte[] bytes = edited ? PcPhotoImage.encodePng(image) : source.payload();
                if (result.replace() == null) drive.createBinary(result.folder(), result.name(), bytes);
                else drive.writeBinary(result.replace(), bytes);
                message = "Image saved on the PC drive.";
            });
            return;
        }
        dialog = type;
        input = null;
        message = "";
        if (type == Dialog.DELETE) return;
        input = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 18, Component.literal(dialogTitle()));
        input.setMaxLength(120);
        input.setValue(switch (type) {
            case SAVE -> copyName();
            case RENAME -> entry.name();
            case RESIZE -> pixels.getWidth() + " x " + pixels.getHeight();
            default -> "";
        });
        input.setHighlightPos(0);
        input.setFocused(true);
    }

    private String copyName() {
        String name = entry.name();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = pixels == original ? " - copy" + name.substring(dot) : " - edited.png";
        return stem.substring(0, Math.min(stem.length(), 120 - suffix.length())) + suffix;
    }

    private String dialogTitle() {
        return switch (dialog) {
            case SAVE -> "Save a copy";
            case RENAME -> "Rename image";
            case RESIZE -> "Resize · width × height";
            case DELETE -> "Move image to the Recycle Bin?";
        };
    }

    private Box dialogBounds() {
        int width = Math.min(214, window.width - 20);
        return new Box((int) window.centerX() - width / 2, (int) window.centerY() - 40, width, 80);
    }

    private void drawDialog(GuiGraphics graphics, int mx, int my, float partialTick) {
        var bounds = dialogBounds();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 6);
        try {
            fill(graphics, window, 0xA0000000);
            fill(graphics, bounds, PANEL);
            graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, CARD);
            label(graphics, dialogTitle(), bounds.x + 8, bounds.y + 8, bounds.width - 16, TEXT);
            if (input != null) {
                input.setPosition(bounds.x + 8, bounds.y + 22);
                input.setWidth(bounds.width - 16);
                input.render(graphics, mx, my, partialTick);
            } else {
                label(graphics, entry.name(), bounds.x + 8, bounds.y + 26, bounds.width - 16, MUTED);
            }
            label(graphics, message, bounds.x + 8, bounds.y + 43, bounds.width - 16, ACCENT);
            button(graphics, new Box(bounds.right() - 109, bounds.bottom() - 22, 48, 17), "Cancel", null,
                    false, this::endDialog, mx, my);
            button(graphics, new Box(bounds.right() - 56, bounds.bottom() - 22, 48, 17),
                    dialog == Dialog.DELETE ? "Delete" : "Apply", null, true, this::submitDialog, mx, my);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void submitDialog() {
        try {
            String value = input == null ? "" : input.getValue().strip();
            switch (dialog) {
                case SAVE -> {
                    String path = value.replace('/', '\\');
                    int separator = path.lastIndexOf('\\');
                    UUID parent = entry.parent();
                    if (separator >= 0) {
                        String directory = separator == 0 ? "\\" : path.substring(0, separator);
                        parent = drive.resolve(parent, directory).id();
                    }
                    String name = path.substring(separator + 1);
                    if (pixels != original && !PcFileType.extension(name).equals("png")) {
                        throw new IOException("Save edits with a .png extension.");
                    }
                    if (pixels == original && !PcFileType.extension(name).equals(PcFileType.extension(entry.name()))) {
                        throw new IOException("Keep the original file extension.");
                    }
                    byte[] bytes = pixels == original ? entry.payload() : PcPhotoImage.encodePng(pixels);
                    drive.createBinary(parent, name, bytes);
                    endDialog();
                    message = "Image copy saved on the PC drive.";
                }
                case RENAME -> {
                    if (!PcFileType.extension(value).equals(PcFileType.extension(entry.name()))) {
                        throw new IOException("Keep the original file extension.");
                    }
                    drive.rename(entry.id(), value);
                    entry = drive.get(entry.id());
                    endDialog();
                }
                case RESIZE -> {
                    String[] dimensions = value.split("\\s*[xX×]\\s*", -1);
                    if (dimensions.length != 2) throw new IOException("Enter width x height.");
                    transform(PcPhotoImage.resize(pixels, Integer.parseInt(dimensions[0]), Integer.parseInt(dimensions[1])));
                    endDialog();
                    message = "Resized. Save a copy to keep the edit.";
                }
                case DELETE -> deletePhoto();
            }
        } catch (IOException e) {
            message = e.getMessage();
        } catch (NumberFormatException e) {
            message = "Enter whole numbers for width and height.";
        }
    }

    private void deletePhoto() throws IOException {
        int index = imageIndex();
        UUID id = entry.id();
        drive.recycle(id);
        var remaining = folderImages.stream().filter(photo -> !photo.id().equals(id)).toList();
        closePhoto();
        if (!remaining.isEmpty()) {
            try {
                open(remaining.get(Math.clamp(index, 0, remaining.size() - 1)));
            } catch (IOException ignored) {
                // The deletion succeeded even when the next image cannot be decoded.
            }
        }
        message = "Moved image to the Recycle Bin.";
    }

    private void endDialog() {
        dialog = null;
        input = null;
        message = "";
    }

    private void transform(BufferedImage image) {
        var uploaded = PcPhotoTexture.upload(image);
        if (texture != null) texture.close();
        texture = uploaded;
        pixels = image;
        crop = false;
        resetZoom();
    }

    private void toggleEdit() { editing = !editing; crop = false; nextSlide = 0; resetZoom(); }
    private void toggleFilmstrip() { filmstrip = !filmstrip; computeStage(); revealPhoto(); clampPan(); }
    private void toggleInfo() { info = !info; computeStage(); clampPan(); }
    private void togglePresentation() {
        setPresentation(!presentation);
    }

    private void setPresentation(boolean enabled) {
        if (presentation == enabled) return;
        presentation = enabled;
        if (!enabled) nextSlide = 0;
        fullscreen.accept(enabled);
        computeStage();
        clampPan();
    }
    private void toggleSlideshow() {
        if (nextSlide != 0) {
            setPresentation(false);
        } else if (folderImages.size() > 1) {
            editing = crop = false;
            setPresentation(true);
            nextSlide = Util.getMillis() + 3000;
        }
        resetZoom();
    }

    private void copyPath() {
        try {
            String path = drive.path(entry.id());
            Minecraft.getInstance().keyboardHandler.setClipboard(path);
            if (services != null) services.clipboard().set(new PcClipboard.Text(path));
            message = "Virtual path copied.";
        } catch (IOException e) {
            message = e.getMessage();
        }
    }

    private void chooseImage() {
        if (services == null) return;
        menu = null;
        UUID start = entry == null ? null : entry.parent();
        var formats = PcPhotoImage.extensions();
        services.pick(new PcFileDialog.Request(PcFileDialog.Mode.OPEN, start, "",
                List.of(new PcFileDialog.Filter("Images", formats)), false),
                result -> open(drive.get(result.items().getFirst())));
    }

    private void copyImage() {
        if (services == null) {
            if (pixels == original) copyToFiles.accept(entry);
            return;
        }
        try {
            if (pixels == original) services.clipboard().set(new PcClipboard.Image(entry.name(), entry.payload()));
            else services.clipboard().set(new PcClipboard.Image(copyName(), PcPhotoImage.encodePng(pixels)));
            message = "Image copied.";
        } catch (IOException | IllegalArgumentException e) { message = e.getMessage(); }
    }

    private void pasteImage() {
        if (services == null) return;
        try {
            switch (services.clipboard().get()) {
                case PcClipboard.Image image -> {
                    if (!PcPhotoImage.supports(image.name())) throw new IOException("Unsupported image file.");
                    PcPhotoImage.decode(image.bytes(), 0);
                    UUID folder = entry == null ? drive.resolve(PcFileSystem.ROOT, "Pictures").id() : entry.parent();
                    open(drive.get(PcClipboardState.pasteImage(drive, folder, image)));
                }
                case PcClipboard.Files files -> {
                    if (files.ids().size() != 1) throw new IOException("Choose one image.");
                    open(drive.get(files.ids().getFirst()));
                }
                default -> throw new IOException("Copy an image first.");
            }
        } catch (IOException e) { message = e.getMessage(); }
    }

    private void moveStrip(double mx) {
        int range = Math.max(0, folderImages.size() - visibleThumbs());
        stripOffset = (int) Math.round(Math.clamp((mx - stage.x) / stage.width, 0, 1) * range);
    }

    private void button(GuiGraphics graphics, Box bounds, String title, PcFilesIcon icon, boolean selected,
                        Runnable activate, int mx, int my) {
        var button = new Button(bounds, activate);
        buttons.add(button);
        if (selected || bounds.contains(mx, my)) fill(graphics, bounds, CARD);
        int color = selected ? ACCENT : TEXT;
        int textX = bounds.x + 5;
        if (icon != null) {
            int iconX = title.isEmpty() ? bounds.x + (bounds.width - 12) / 2 : bounds.x + 3;
            icon.draw(graphics, iconX, bounds.y + (bounds.height - 12) / 2, 12, color);
            textX = bounds.x + 17;
        }
        if (!title.isEmpty()) label(graphics, title, textX, bounds.y + (bounds.height - 9) / 2,
                bounds.right() - textX - 2, color);
    }

    private static void fill(GuiGraphics graphics, Box bounds, int color) {
        graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), color);
    }

    private static void label(GuiGraphics graphics, String value, int x, int y, int width, int color) {
        if (width <= 0 || value.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        var text = font.substrByWidth(PcDesktopScreen.label(value), width);
        graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(text), x, y, color, false);
    }

    private static String bytes(int size) {
        return size < 1024 ? size + " B" : String.format(java.util.Locale.ROOT, "%.1f KiB", size / 1024.0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int mouseButton) {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (dialog != null) {
            if (input != null && input.isMouseOver(mx, my)) input.mouseClicked(mx, my, mouseButton);
            else {
                // The last two buttons belong to the modal dialog.
                for (int index = Math.max(0, buttons.size() - 2); index < buttons.size(); index++) {
                    var button = buttons.get(index);
                    if (button.bounds.contains(mx, my)) { button.activate.run(); break; }
                }
            }
            return true;
        }
        if (menu != null) {
            var items = menuItems();
            var box = menuBounds(items);
            int index = (int) (my - box.y - 2) / menuRowHeight(items);
            menu = null;
            if (box.contains(mx, my) && my >= box.y + 2 && index >= 0 && index < items.size()) {
                items.get(index).activate.run();
            }
            return true;
        }
        for (var button : buttons.reversed()) {
            if (button.bounds.contains(mx, my)) {
                button.activate.run();
                return true;
            }
        }
        if (slider != null && slider.contains(mx, my)) {
            drag = Drag.ZOOM;
            moveSlider(mx);
            return true;
        }
        if (showFilmstrip() && folderImages.size() > visibleThumbs() && mx >= stage.x && mx < stage.right()
                && my >= stage.bottom() + 39 && my < window.bottom() - BAR) {
            drag = Drag.STRIP;
            moveStrip(mx);
            return true;
        }
        if (texture != null && stage.contains(mx, my)) {
            if (crop && !imageBounds().contains(mx, my)) return true;
            drag = crop ? Drag.CROP : Drag.PAN;
            dragX = cropX = mx;
            dragY = cropY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == null) return false;
        switch (drag) {
            case ZOOM -> { if (slider != null) moveSlider(mx); }
            case STRIP -> moveStrip(mx);
            case CROP -> { dragX = mx; dragY = my; }
            case PAN -> { panX += dx; panY += dy; clampPan(); }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag == null) return false;
        if (drag == Drag.CROP && texture != null && Math.abs(mx - cropX) >= 2 && Math.abs(my - cropY) >= 2) {
            var box = imageBounds();
            int left = Math.clamp((int) Math.floor((Math.min(mx, cropX) - box.x) * pixels.getWidth() / box.width), 0, pixels.getWidth() - 1);
            int top = Math.clamp((int) Math.floor((Math.min(my, cropY) - box.y) * pixels.getHeight() / box.height), 0, pixels.getHeight() - 1);
            int right = Math.clamp((int) Math.ceil((Math.max(mx, cropX) - box.x) * pixels.getWidth() / box.width), left + 1, pixels.getWidth());
            int bottom = Math.clamp((int) Math.ceil((Math.max(my, cropY) - box.y) * pixels.getHeight() / box.height), top + 1, pixels.getHeight());
            transform(pixels.getSubimage(left, top, right - left, bottom - top));
        }
        drag = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dialog != null || menu != null) return true;
        if (entry == null) return false;
        if (showInfo() && infoBounds().contains(mx, my)) {
            infoScroll = Math.clamp(infoScroll - (int) Math.signum(dy) * 18, 0, Math.max(0, 230 - infoBounds().height));
            return true;
        }
        if (showFilmstrip() && mx >= stage.x && mx < stage.right() && my >= stage.bottom() && my < window.bottom() - BAR) {
            double delta = dx != 0 ? dx : dy;
            stripOffset = Math.clamp(stripOffset - (int) Math.signum(delta), 0, Math.max(0, folderImages.size() - visibleThumbs()));
            return true;
        }
        if (stage.contains(mx, my) && dy != 0) {
            zoomAt(effectiveZoom() * Math.pow(1.15, dy), mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (dialog != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) endDialog();
            else if (enter) submitDialog();
            else if (input != null) input.keyPressed(key, scanCode, modifiers);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) menu = null;
            else if (presentation) togglePresentation();
            else if (editing) toggleEdit();
            else return false;
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_O) {
            chooseImage();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_V) {
            pasteImage();
            return true;
        }
        if (entry == null) return false;
        if (menu != null) {
            var items = menuItems();
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                menuIndex = Math.floorMod(menuIndex + (key == GLFW.GLFW_KEY_UP ? -1 : 1), items.size());
            } else if (enter) {
                menu = null;
                items.get(menuIndex).activate.run();
            }
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            switch (key) {
                case GLFW.GLFW_KEY_E -> toggleEdit();
                case GLFW.GLFW_KEY_R -> transform(PcPhotoImage.rotate(pixels, true));
                case GLFW.GLFW_KEY_S -> beginDialog(Dialog.SAVE);
                case GLFW.GLFW_KEY_C -> {
                    if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) copyPath();
                    else copyImage();
                }
                case GLFW.GLFW_KEY_0 -> resetZoom();
                case GLFW.GLFW_KEY_B -> setBackground(false);
                case GLFW.GLFW_KEY_L -> setBackground(true);
                default -> { return false; }
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> { if (!editing) browse(-1, false); }
            case GLFW.GLFW_KEY_RIGHT -> { if (!editing) browse(1, false); }
            case GLFW.GLFW_KEY_F -> toggleFilmstrip();
            case GLFW.GLFW_KEY_I -> toggleInfo();
            case GLFW.GLFW_KEY_F2 -> beginDialog(Dialog.RENAME);
            case GLFW.GLFW_KEY_F5 -> toggleSlideshow();
            case GLFW.GLFW_KEY_F11 -> togglePresentation();
            case GLFW.GLFW_KEY_DELETE -> beginDialog(Dialog.DELETE);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoomBy(1.2);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoomBy(1 / 1.2);
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return input != null && input.charTyped(character, modifiers);
    }

    @Override
    public void focusChanged(boolean focused) {
        if (!focused) {
            menu = null;
            drag = null;
            nextSlide = 0;
        }
    }

    private void clearThumbs() {
        thumbs.values().forEach(PcPhotoTexture::close);
        thumbs.clear();
        unavailableThumbs.clear();
    }

    private void closePhoto() {
        if (texture != null) texture.close();
        texture = null;
        entry = null;
        original = pixels = null;
        folderImages = List.of();
        nextSlide = 0;
        setPresentation(false);
        editing = crop = false;
        menu = null;
        endDialog();
        clearThumbs();
    }

    @Override
    public void closed() { closePhoto(); }
}
