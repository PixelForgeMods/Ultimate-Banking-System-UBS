package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.api.pc.PcDesktopMenuEvent;
import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.austizz.ultimatebankingsystem.api.pc.PcAppServices;
import net.austizz.ultimatebankingsystem.api.pc.PcDrive;
import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;
import net.austizz.ultimatebankingsystem.api.pc.PcFileDialog;
import net.austizz.ultimatebankingsystem.api.pc.PcAppNotification;
import net.austizz.ultimatebankingsystem.api.pc.PcAppRegistrationEvent;
import net.austizz.ultimatebankingsystem.api.pc.PcWindowOptions;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.Bounds;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.Cell;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.ResizeEdge;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.SnapZone;
import net.austizz.ultimatebankingsystem.gui.screens.PcDesktopWindows.Window;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class PcDesktopScreen extends Screen {
    private static final ResourceLocation WALLPAPER = resource("textures/gui/pc/wallpaper.png");
    private static final ResourceLocation ICONS = resource("textures/gui/pc/icons.png");
    static final int FILE_ICON_PIXELS = 40;
    static final int HIGH_RES_FILE_ICON_PIXELS = 512;
    private static final ResourceLocation HIGH_RES_FOLDER_ICON = resource("textures/gui/pc/folder_high_res.png");
    private static final Map<PcFileType, FileIconTextures> FILE_ICONS = new EnumMap<>(PcFileType.class);

    private record FileIconTextures(ResourceLocation light, ResourceLocation dark,
                                    ResourceLocation highResLight, ResourceLocation highResDark) {
        ResourceLocation texture(boolean darkTheme, boolean highRes) {
            if (darkTheme) return highRes ? highResDark : dark;
            return highRes ? highResLight : light;
        }
    }

    static {
        for (var type : PcFileType.values()) {
            String base = "textures/gui/pc/file_types/";
            String name = type.icon + ".png";
            FILE_ICONS.put(type, new FileIconTextures(resource(base + name), resource(base + "dark/" + name),
                    resource(base + "high_res/" + name), resource(base + "dark/high_res/" + name)));
        }
    }

    private static final ResourceLocation FONT = resource("atm_regular");
    private static final ResourceLocation SEARCH_ART = resource("textures/gui/pc/search_art.png");
    private static final ResourceLocation BOLD_FONT = resource("atm_bold");
    private static final int WALLPAPER_WIDTH = 3840;
    private static final int WALLPAPER_HEIGHT = 2400;
    private static final int SURFACE = 0xFF1B1D23;
    private static final int FIELD = 0xFF282B33;
    private static final int CONTROL_FIELD = 0xEB282B33;
    private static final int TASKBAR_TINT = 0xDB1B1D23;
    private static final int LAUNCHER_TINT = 0xD9202020;
    private static final int JUMP_LIST_TINT = 0xE025262B;
    private static final int FLYOUT_TINT = 0xED25262B;
    private static final int BORDER = 0xFF484C57;
    private static final int TEXT = 0xFFF5F7FC;
    private static final int MUTED = 0xFFC2C7D3;
    private static final int ACCENT = 0xFF56C7FF;
    private static final int TOOL_WIDTH = 26;
    private static final int TASKBAR_ICON_SIZE = 20;
    private static final int TASKBAR_HOVER = 0x6643537F;
    private static final int TASKBAR_SELECTED = 0x5543537F;
    private static final int CAPTION_WIDTH = 24;
    private static final int SHOW_DESKTOP_WIDTH = 9;
    private static final int TITLE_HEIGHT = 26;
    private static final int RESIZE_BORDER = 4;
    private static final int SNAP_EDGE = 6;
    private static final int LAYER_STEP = 10;
    private static final int TASKBAR_DEPTH = -2 * LAYER_STEP;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);
    private static final DateTimeFormatter SCREENSHOT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss-SSS", Locale.ROOT);
    private static final int SLIDE_IN_MS = 200;
    private static final int SLIDE_OUT_MS = 155;
    private static final int PREVIEW_DELAY_MS = 400;
    private static final int PREVIEW_LEAVE_MS = 130;
    private static final int PEEK_DELAY_MS = 330;
    private static final int FLYOUT_ROW = 20;
    private static final int SHORTCUT_WIDTH = 66;
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 8;
    private static final int GRID_COLUMN = SHORTCUT_WIDTH + 4;
    static final int DOUBLE_CLICK_MS = 500;
    private static final int DRAG_THRESHOLD = 4;

    private enum Panel { DESKTOP, START, SEARCH }
    private enum Icon { START, MARKETPLACE, FILES, NOTES, SETTINGS, SEARCH, POWER, CLOSE, MINIMIZE, MAXIMIZE, RESTORE, LOCK, RESTART, OPEN, PIN, UNPIN }
    record App(ResourceLocation id, Component title, Icon icon, ResourceLocation texture, boolean pinnedToTaskbar, PcWindowOptions sizing,
               Supplier<? extends PcAppContent> factory) {
        static final App MARKETPLACE = builtin("marketplace", "Marketplace", Icon.MARKETPLACE, true);
        static final App FILES = builtin("files", "Files", Icon.FILES, true);
        static final App NOTES = builtin("notes", "Notes", Icon.NOTES, false);
        static final App SETTINGS = builtin("settings", "Settings", Icon.SETTINGS, true);
        static final App PHOTOS = new App(resource("photos"), label("Photos"), null,
                resource("textures/gui/pc/photos_app.png"), false,
                new PcWindowOptions(452, 270, 270, 166, Integer.MAX_VALUE, Integer.MAX_VALUE, true, true), null);
        static final App CLOCK = utility("clock", "Clock", 370, 232, 285, 172);
        static final App ARCHIVE = utility("archive_manager", "Archive Manager", 456, 255, 338, 166);
        static final App TERMINAL = utility("pc_terminal", "PC Terminal", 444, 245, 250, 138);
        static final List<App> BUILTINS = List.of(MARKETPLACE, FILES, NOTES, SETTINGS, PHOTOS, CLOCK, ARCHIVE, TERMINAL);

        private static App utility(String id, String title, int width, int height, int minWidth, int minHeight) {
            return new App(resource(id), label(title), null, resource("textures/gui/pc/" + id + "_app.png"), false,
                    new PcWindowOptions(width, height, minWidth, minHeight, Integer.MAX_VALUE, Integer.MAX_VALUE, true, true), null);
        }

        private static App builtin(String id, String title, Icon icon, boolean pinned) {
            var sizing = switch (icon) {
                case FILES -> new PcWindowOptions(410, 218, PcFilesPanes.MIN_WIDTH + 2, 154,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);
                case SETTINGS -> new PcWindowOptions(375, 235, 240, 130,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);
                case NOTES -> new PcWindowOptions(450, 239, 320, 178,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);
                default -> PcWindowOptions.DEFAULT;
            };
            return new App(resource(id), label(title), icon, null, pinned, sizing, null);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof App app && id.equals(app.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private final GlobalPos position;
    private final PcBackdrop backdrop = new PcBackdrop();
    private PcPhotoTexture photoWallpaper;
    private long wallpaperRevision = -1;
    private UUID wallpaperImage;
    private final PcDesktopWindows<App> windows;
    private final Map<ResourceLocation, App> catalog = new LinkedHashMap<>();
    private final UUID desktopUser;
    private final Set<ResourceLocation> installedApps;
    private final Path preferencesPath;
    private Window<App> focusedApp;
    private Window<App> pointerApp;
    private int pointerButton;
    private Window<App> captionClickWindow;
    private long captionClickTime;
    private double captionClickX;
    private double captionClickY;
    private SnapZone pendingSnap;
    private KeyboardWindowDrag keyboardWindowDrag;

    private record KeyboardWindowDrag(Window<App> window, Bounds start, int savedWidth, int savedHeight, ResizeEdge edge) {}
    private final List<PcButton> shortcuts = new ArrayList<>();
    private final Set<UUID> selectedShortcuts = new HashSet<>();
    private long desktopRevision = -1;
    private String desktopItemMessage;
    private long desktopMessageUntil;
    private DesktopPress desktopPress;
    private ShortcutClick lastShortcutClick;
    private EditBox shortcutEditor;
    private PcButton editingShortcut;
    private final List<PcButton> captionButtons = new ArrayList<>();
    private final List<AbstractWidget> launcherWidgets = new ArrayList<>();
    private final List<PcButton> startTiles = new ArrayList<>();
    private PcButton nextStartPage;
    private int startPage;
    private final Map<App, PcButton> resultRows = new LinkedHashMap<>();
    private final List<PcButton> powerItems = new ArrayList<>();
    private final List<PcButton> taskbarButtons = new ArrayList<>();
    private final List<PcButton> jumpItems = new ArrayList<>();
    private PcButton jumpAnchor;
    private PcButton hoverAnchor;
    private PcButton previewAnchor;
    private PcButton dismissedPreview;
    private PcButton startButton;
    private PcButton hoverLabel;
    private DesktopPopup desktopMenu;
    private DesktopPopup desktopSubmenu;
    private ContextCommand submenuParent;
    private int lastMenuX;
    private int lastMenuY;
    private long labelSince;
    private long hoverSince;
    private long leftPreviewAt;
    private long peekSince;
    private enum SearchKind { ALL, APPS, FILES, SETTINGS }
    private record SearchResult(String title, String detail, SearchKind kind, int score, App app,
                                PcFileSystem.Entry file, Runnable activate) {}
    private final List<PcButton> searchRows = new ArrayList<>();
    private final Map<SearchKind, PcButton> searchTabs = new EnumMap<>(SearchKind.class);
    private List<SearchResult> matches = List.of();
    private SearchResult selectedResult;
    private SearchKind searchKind = SearchKind.ALL;
    private int searchScroll;
    private PcButton openResult;
    private PcButton locateResult;
    private PcButton powerButton;
    private AbstractButton showDesktopButton;
    private boolean powerOpen;
    private Panel panel = Panel.DESKTOP;
    private Panel nextPanel;
    private Panel afterSlide;
    private long slideStarted;
    private double slideFrom;
    private Window<App> draggedWindow;
    private ResizeDrag resizeDrag;
    private final EnumMap<ResizeCursor, Long> resizeCursors = new EnumMap<>(ResizeCursor.class);
    private ResizeCursor currentResizeCursor;
    private double grabX;
    private double grabY;
    private EditBox search;
    private int barY;
    private final PcScreenshotOverlay screenshot = new PcScreenshotOverlay(this::saveScreenshot, this::copyScreenshotColor);
    private final PcNotificationCenter notificationCenter;
    private PcFilePicker filePicker;
    private PcOpenWith openWith;
    private final Map<ResourceLocation, Set<String>> fileHandlers;
    private boolean notificationEdgeDrag;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int restoredWidth;
    private int restoredHeight;

    PcDesktopScreen(GlobalPos position, PcDesktopWindows<App> windows) {
        super(label("PC"));
        this.position = position;
        this.windows = windows;
        App.BUILTINS.forEach(app -> catalog.put(app.id, app));
        var registration = NeoForge.EVENT_BUS.post(new PcAppRegistrationEvent(position));
        fileHandlers = registration.fileHandlers();
        for (var entry : registration.apps()) {
            catalog.putIfAbsent(entry.id(), new App(entry.id(), entry.title().copy().withStyle(style -> style.withFont(FONT)),
                    Icon.OPEN, entry.icon(), false, entry.options(), entry.factory()));
        }
        desktopUser = Minecraft.getInstance().getUser().getProfileId();
        preferencesPath = PcDesktopPreferences.path(Minecraft.getInstance(), position);
        if (windows.fileSystem == null) {
            windows.fileSystem = new PcFileSystem(preferencesPath == null ? null
                    : preferencesPath.resolveSibling(preferencesPath.getFileName() + ".drive"));
        }
        installedApps = PcAppInstallations.load(desktopUser);
        if (!windows.preferencesLoaded) {
            PcDesktopPreferences.load(preferencesPath, windows, catalog.values());
            windows.preferencesLoaded = true;
        }
        notificationCenter = new PcNotificationCenter(windows.notifications, windows.fileSystem,
                this::drawNotificationIcon, this::openNotificationSettings, this::openNotificationTarget,
                this::saveDesktopPreferences, e -> UltimateBankingSystem.LOGGER.warn("PC notification action failed", e));
        initializeDesktopFolder();
        initializeQuickFolders();
        initializeSamplePictures();
        var marketplace = windows.get(App.MARKETPLACE);
        if (marketplace != null) {
            marketplace.content = marketplaceContent();
            marketplace.contentWidth = -1;
        }
        var files = windows.get(App.FILES);
        if (files != null && files.content instanceof PcFilesContent explorer) {
            explorer.setHandlers(this::openFileEntry, this::drawFileIcon, this::saveDesktopPreferences);
            explorer.openWith(this::chooseFileApp);
        }
        var settings = windows.get(App.SETTINGS);
        if (settings != null) {
            settings.content = settingsContent();
            settings.contentWidth = -1;
        }
        var photos = windows.get(App.PHOTOS);
        if (photos != null && photos.content instanceof PcPhotosContent viewer) {
            viewer.setHandlers(this::showPhotoInFiles, this::copyPhotoToFiles, this::setPhotoWallpaper, this::setPhotoFullscreen);
        }
        var terminal = windows.get(App.TERMINAL);
        if (terminal != null && terminal.content instanceof PcTerminalContent shell) {
            shell.handlers(this::openFileEntry, this::openNote, this::launchFromTerminal, this::terminalApps);
        }
        windows.backToFront().forEach(this::bindAppServices);
        focusApp(windows.active());
    }

    private void initializeQuickFolders() {
        if (windows.quickFoldersInitialized) return;
        try {
            windows.quickFolders.add(windows.fileSystem.desktopFolder());
            var folders = windows.fileSystem.list(PcFileSystem.ROOT, "");
            for (String name : List.of("Downloads", "Documents", "Pictures", "Music", "Videos")) {
                folders.stream().filter(entry -> entry.folder() && entry.name().equalsIgnoreCase(name))
                        .findFirst().ifPresent(entry -> windows.quickFolders.add(entry.id()));
            }
            windows.quickFoldersInitialized = true;
        } catch (java.io.IOException e) {
            UltimateBankingSystem.LOGGER.warn("Could not initialize Quick access folders", e);
        }
    }

    private void saveDesktopPreferences() {
        PcDesktopPreferences.save(preferencesPath, windows);
    }

    private void initializeSamplePictures() {
        if (windows.samplePicturesInitialized) return;
        String folder = "/assets/ultimatebankingsystem/pc/sample-pictures/";
        // NASA photographs from the Blue Marble and Earthrise mission galleries.
        try (var marble = PcDesktopScreen.class.getResourceAsStream(folder + "blue-marble.jpg");
             var earthrise = PcDesktopScreen.class.getResourceAsStream(folder + "earthrise.jpg")) {
            if (marble == null || earthrise == null) throw new IOException("Bundled sample pictures are missing.");
            windows.fileSystem.addPictureSamples(Map.of("Blue Marble.jpg", marble.readAllBytes(),
                    "Earthrise.jpg", earthrise.readAllBytes()));
            windows.samplePicturesInitialized = true;
            saveDesktopPreferences();
        } catch (IOException e) {
            UltimateBankingSystem.LOGGER.warn("Could not add sample pictures to the PC drive", e);
        }
    }

    private void initializeDesktopFolder() {
        try {
            var defaults = new LinkedHashMap<String, String>();
            for (var app : catalog.values()) {
                if (app.equals(App.MARKETPLACE) || app.equals(App.SETTINGS) || installedApps.contains(app.id)) {
                    defaults.put(app.id.toString(), windows.desktopNames.getOrDefault(app, app.title.getString()));
                }
            }
            windows.fileSystem.initializeDesktop(defaults);
            if (!windows.desktopPositions.isEmpty()) {
                for (var entry : windows.fileSystem.list(windows.fileSystem.desktopFolder(), "")) {
                    var cell = windows.desktopPositions.get(shortcutTarget(entry));
                    if (cell != null) windows.desktopItemPositions.putIfAbsent(entry.id(), cell);
                }
            }
            windows.desktopNames.clear();
            windows.desktopPositions.clear();
        } catch (java.io.IOException e) {
            UltimateBankingSystem.LOGGER.warn("Could not initialize the PC Desktop folder", e);
        }
    }

    @Override
    protected void init() {
        screenshot.close();
        if (keyboardWindowDrag != null) {
            endKeyboardDrag(true);
        }
        if (resizeDrag != null) {
            finishResize(true);
        }
        if (shortcutEditor != null) {
            finishShortcutRename(true);
        }
        desktopPress = null;
        lastShortcutClick = null;
        String query = search == null ? "" : search.getValue();
        search = null;
        startTiles.clear();
        nextStartPage = null;
        taskbarButtons.clear();
        hoverLabel = null;
        jumpItems.clear();
        jumpAnchor = null;
        desktopMenu = null;
        desktopSubmenu = null;
        submenuParent = null;
        clearPreview();
        dismissedPreview = null;
        resultRows.clear();
        searchRows.clear();
        searchTabs.clear();
        powerItems.clear();
        powerButton = null;
        shortcuts.clear();
        captionButtons.clear();
        launcherWidgets.clear();
        int barHeight = Math.clamp(height / 12, 27, 40);
        barY = height - barHeight;
        panelWidth = Math.min(width - 16, Math.clamp(width * 42 / 100, 212, 420));
        panelHeight = Math.min(barY - 14, Math.clamp(height / 2, 138, 258));
        panelX = (width - panelWidth) / 2;
        panelY = barY - panelHeight - 7;
        restoredWidth = Math.min(width, panelWidth + 36);
        restoredHeight = Math.min(barY, panelHeight + 10);
        for (var window : windows.backToFront()) {
            if (window.snapped != null && !canSnap(window, window.snapped)) {
                window.snapped = null;
            }
            moveWindow(window, window.x, window.y);
        }

        syncDesktopItems();

        List<App> taskbarApps = Stream.concat(catalog.values().stream(), windows.recent().stream()).distinct()
                .filter(app -> isPinned(app) || windows.isOpen(app)).toList();
        int fixedWidth = (taskbarApps.size() + 1) * TOOL_WIDTH;
        int clockGutter = font.width(labelText("00-00-0000")) + SHOW_DESKTOP_WIDTH + 26;
        int searchWidth = Math.clamp(width - fixedWidth - 2 * clockGutter, 52, 138);
        int taskX = (width - searchWidth - fixedWidth) / 2;
        int taskY = barY + (barHeight - 23) / 2;
        startButton = button(taskX, taskY, TOOL_WIDTH, 23, "Start", Icon.START, false,
                () -> togglePanel(Panel.START));
        startButton.setTooltip(null);
        taskX += TOOL_WIDTH;
        button(taskX, taskY, searchWidth, 23, "Search", Icon.SEARCH, true,
                () -> togglePanel(Panel.SEARCH));
        taskX += searchWidth;
        for (App app : taskbarApps) {
            var taskButton = button(taskX, taskY, TOOL_WIDTH, 23, app.title, app.icon, false, () -> openApp(app));
            taskButton.taskApp = app;
            taskButton.setTooltip(null);
            taskbarButtons.add(taskButton);
            taskX += TOOL_WIDTH;
        }
        showDesktopButton = addRenderableWidget(new ShowDesktopButton(barY, barHeight));

        if (panel == Panel.DESKTOP) {
            var active = windows.active();
            if (active != null) {
                initCaption(active);
            }
        } else {
            initLauncher(query);
        }
    }

    private boolean isPinned(App app) {
        return windows.isPinned(app, windows.savedPins.getOrDefault(app.id.toString(), app.pinnedToTaskbar));
    }

    private void syncDesktopItems() {
        if (desktopRevision != windows.fileSystem.revision()) {
            for (var item : shortcuts) {
                windows.desktopItemPositions.putIfAbsent(item.desktopEntry.id(), item.shortcutCell);
            }
        }
        desktopRevision = windows.fileSystem.revision();
        if (shortcuts.contains(getFocused())) clearFocus();
        shortcuts.forEach(this::removeWidget);
        shortcuts.clear();
        if (!windows.showDesktopIcons) {
            selectedShortcuts.clear();
            return;
        }
        try {
            var items = new ArrayList<>(windows.fileSystem.list(windows.fileSystem.desktopFolder(), ""));
            Comparator<PcFileSystem.Entry> byName = Comparator.comparing(PcFileSystem.Entry::name, String.CASE_INSENSITIVE_ORDER);
            items.sort(windows.descendingDesktopOrder ? byName.reversed() : byName);
            var cells = new HashMap<UUID, Cell>();
            var occupied = new HashSet<Cell>();
            for (var entry : items) {
                Cell saved = windows.desktopItemPositions.get(entry.id());
                if (saved != null && fitsDesktop(saved) && occupied.add(saved)) cells.put(entry.id(), saved);
            }
            for (int i = 0; i < items.size(); i++) {
                var entry = items.get(i);
                Cell cell = cells.get(entry.id());
                if (cell == null && occupied.size() < (long) desktopRows() * desktopColumns()) {
                    Cell preferred = new Cell(i / desktopRows(), i % desktopRows());
                    cell = nearestDesktopCell(preferred, candidate -> !occupied.contains(candidate));
                    if (cell != null) {
                        occupied.add(cell);
                        cells.put(entry.id(), cell);
                    }
                }
                if (cell != null) shortcut(entry, cell);
            }
            selectedShortcuts.retainAll(cells.keySet());
        } catch (java.io.IOException e) {
            UltimateBankingSystem.LOGGER.warn("Could not list items on the PC desktop", e);
        }
    }

    private void shortcut(PcFileSystem.Entry entry, Cell cell) {
        PcButton button = new PcButton(GRID_LEFT + cell.column() * GRID_COLUMN, GRID_TOP + cell.row() * desktopRowHeight(),
                SHORTCUT_WIDTH, windows.desktopIconSize + 16, labelText(entry.name()), Icon.FILES, true, () -> activateDesktopItem(entry));
        button.desktopEntry = entry;
        button.shortcutCell = cell;
        button.shortcutSize = windows.desktopIconSize;
        shortcuts.add(button);
        if (panel == Panel.DESKTOP) {
            addWidget(button);
        }
    }

    private void initLauncher(String query) {
        Component hint = label("Search apps, files, and settings");
        search = addLauncherWidget(new EditBox(font, panelX + 30, panelY + 8, panelWidth - 44, 23, hint) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 7, 0);
                super.renderWidget(graphics, mouseX, mouseY - 7, partialTick);
                graphics.pose().popPose();
            }
        });
        search.setBordered(false);
        search.setTextShadow(false);
        search.setTextColor(TEXT);
        search.setHint(hint);
        search.setFormatter((value, offset) -> labelText(value).getVisualOrderText());
        search.setValue(query);

        if (panel == Panel.START) {
            int tileWidth = (panelWidth - 16) / 4;
            for (App app : App.BUILTINS) {
                var tile = new PcButton(0, 0, tileWidth, 42, app.title, app.icon, true, () -> openApp(app));
                tile.appIcon = app;
                startTiles.add(addLauncherWidget(tile));
            }
            for (UUID id : windows.startFolders) {
                try {
                    var entry = windows.fileSystem.get(id);
                    if (entry.folder()) {
                        startTiles.add(addLauncherWidget(new PcButton(0, 0, tileWidth, 42,
                                labelText(entry.name()), Icon.FILES, true, () -> activateDesktopItem(entry))));
                    }
                } catch (java.io.IOException ignored) {
                    // Keep recycled pins so a restore can bring them back.
                }
            }
            if (startTiles.size() > startPageSize()) {
                nextStartPage = addLauncherWidget(new PcButton(panelX + panelWidth - 67, panelY + 35,
                        58, 17, label("Next"), Icon.OPEN, true, () -> {
                            startPage = (startPage + 1) % startPageCount();
                            updateStartTiles();
                        }));
            }
        }
        for (App app : Stream.concat(catalog.values().stream().filter(this::installed),
                windows.recent().stream().filter(this::installed)).distinct().toList()) {
            var row = addLauncherWidget(new PcButton(panelX + 8, panelY + 51,
                    resultsSplit() - panelX - 13, 18, app.title, app.icon, true, () -> openApp(app)));
            row.resultApp = app;
            resultRows.put(app, row);
        }
        int tabWidth = (panelWidth - 20) / SearchKind.values().length;
        for (var kind : SearchKind.values()) {
            String text = switch (kind) {
                case ALL -> "All";
                case APPS -> "Apps";
                case FILES -> "Files";
                case SETTINGS -> "Settings";
            };
            var tab = addLauncherWidget(new PcButton(panelX + 8 + kind.ordinal() * tabWidth, panelY + 35,
                    tabWidth - 2, 18, label(text), null, false, () -> {
                        searchKind = kind;
                        filterApps(search.getValue());
                        setInitialFocus(search);
                    }));
            tab.searchTab = kind;
            searchTabs.put(kind, tab);
        }
        int slots = Math.max(1, (panelHeight - 78) / 18);
        for (int index = 0; index < slots; index++) {
            final int slot = index;
            var row = addLauncherWidget(new PcButton(panelX + 8, panelY + 68 + 18 * index,
                    resultsSplit() - panelX - 13, 17, label(""), Icon.OPEN, true, () -> {
                        SearchResult next = matches.get(searchScroll + slot);
                        if (next == selectedResult) openSelectedResult();
                        else { selectedResult = next; updateSearchRows(); }
                    }));
            searchRows.add(row);
        }
        openResult = addLauncherWidget(new PcButton(resultsSplit() + 5, panelY + 109,
                panelX + panelWidth - resultsSplit() - 13, 18,
                label("Open"), Icon.OPEN, true, this::openSelectedResult));
        locateResult = addLauncherWidget(new PcButton(resultsSplit() + 5, panelY + 128,
                panelX + panelWidth - resultsSplit() - 13, 18,
                label("Open location"), Icon.FILES, true, this::locateSelectedResult));
        if (panel == Panel.SEARCH) {
            setInitialFocus(search);
        } else {
            powerButton = addLauncherWidget(new PcButton(panelX + panelWidth - 31, panelY + panelHeight - 25,
                    24, 21, label("Power"), Icon.POWER, false, () -> setPowerOpen(!powerOpen)));
            powerItem(0, "Lock", Icon.LOCK, () -> OwnerPcScreenHelper.openDesktop(position));
            powerItem(1, "Shut down", Icon.POWER, () -> {
                focusApp(null);
                windows.closeAll();
                onClose();
            });
            powerItem(2, "Restart", Icon.RESTART, () -> {
                focusApp(null);
                windows.closeAll();
                OwnerPcScreenHelper.openDesktop(position);
            });
        }
        search.setResponder(this::filterApps);
        filterApps(query);
    }

    private void powerItem(int index, String name, Icon icon, Runnable action) {
        powerItems.add(addWidget(new PcButton(powerButton.getX() + powerButton.getWidth() - 103,
                powerButton.getY() - 64 + index * 19, 99, 19, label(name), icon, true, action)));
    }

    private void setPowerOpen(boolean open) {
        powerOpen = open;
        powerItems.forEach(widget -> show(widget, open));
    }

    private boolean overPowerMenu(double x, double y) {
        return powerOpen && x >= powerButton.getX() + powerButton.getWidth() - 107
                && x < powerButton.getX() + powerButton.getWidth()
                && y >= powerButton.getY() - 68 && y < powerButton.getY() - 3;
    }

    private <T extends AbstractWidget> T addLauncherWidget(T widget) {
        launcherWidgets.add(widget);
        return addWidget(widget);
    }

    private void filterApps(String query) {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        var found = new ArrayList<SearchResult>();
        if (!filter.isEmpty()) {
            if (searchKind == SearchKind.ALL || searchKind == SearchKind.APPS) {
                for (var app : resultRows.keySet()) {
                    int rank = PcSearch.rank(searchName(app), filter);
                    if (rank >= 0) found.add(new SearchResult(app.title.getString(), "App", SearchKind.APPS, rank,
                            app, null, () -> openApp(app)));
                }
            }
            if (searchKind == SearchKind.ALL || searchKind == SearchKind.FILES) {
                try {
                    for (var result : PcSearch.files(windows.fileSystem, filter)) {
                        var file = result.file();
                        found.add(new SearchResult(file.name(), file.folder() ? "Folder" : PcFileType.label(file.name()),
                                SearchKind.FILES, result.score(), null, file, () -> activateDesktopItem(file)));
                    }
                } catch (IOException ignored) {
                    // App and Settings results are still usable when the drive is unavailable.
                }
            }
            if (searchKind == SearchKind.ALL || searchKind == SearchKind.SETTINGS) {
                addSetting(found, filter, "Appearance", "theme icons personalization", PcSettingsContent::openAppearance);
                addSetting(found, filter, "Notifications", "focus do not disturb sounds badges", settings -> settings.openNotifications(null));
                addSetting(found, filter, "Default apps", "open with associations file types", settings -> settings.openDefaults(null));
                if (filter.startsWith(".") && defaultExtensions().contains(filter.substring(1))) {
                    addSetting(found, filter, "Default app for " + filter, filter, settings -> settings.openDefaults(filter.substring(1)));
                }
            }
        }
        matches = found.stream().sorted(Comparator.comparingInt(SearchResult::score)
                .thenComparing(SearchResult::kind).thenComparing(SearchResult::title, String.CASE_INSENSITIVE_ORDER)).toList();
        selectedResult = matches.isEmpty() ? null : matches.getFirst();
        searchScroll = 0;
        boolean recent = recentView();
        List<App> apps = recent ? (windows.recent().isEmpty()
                ? App.BUILTINS : windows.recent().stream().filter(this::installed).toList()) : List.of();
        updateStartTiles();
        resultRows.forEach((app, row) -> {
            int index = apps.indexOf(app);
            row.setWidth(panelWidth - 16);
            if (index >= 0) row.setY(panelY + 52 + index * 20);
            show(row, index >= 0 && row.getY() + row.getHeight() <= panelY + panelHeight - 8);
        });
        searchTabs.values().forEach(tab -> show(tab, !filter.isEmpty()));
        updateSearchRows();
        if (powerButton != null) show(powerButton, query.isEmpty());
        setPowerOpen(powerOpen && query.isEmpty());
    }

    private void addSetting(List<SearchResult> found, String filter, String title, String keywords, Consumer<PcSettingsContent> activate) {
        int rank = PcSearch.rank(title, filter);
        if (rank < 0) rank = PcSearch.rank(keywords, filter);
        if (rank >= 0) found.add(new SearchResult(title, "Setting", SearchKind.SETTINGS, rank, App.SETTINGS, null, () -> {
            openApp(App.SETTINGS);
            if (windows.get(App.SETTINGS).content instanceof PcSettingsContent settings) activate.accept(settings);
        }));
    }

    private void updateSearchRows() {
        for (int i = 0; i < searchRows.size(); i++) {
            int index = searchScroll + i;
            var row = searchRows.get(i);
            var result = index < matches.size() ? matches.get(index) : null;
            row.searchResult = result;
            if (result != null) row.setMessage(label(result.title));
            show(row, result != null);
        }
        show(openResult, selectedResult != null);
        show(locateResult, selectedResult != null && selectedResult.file != null && panelHeight > 152);
    }

    private void locateSelectedResult() {
        if (selectedResult == null || selectedResult.file == null) return;
        try {
            var file = windows.fileSystem.get(selectedResult.file.id());
            openApp(App.FILES);
            if (windows.get(App.FILES).content instanceof PcFilesContent files) files.revealFile(file);
            applyPanelChange();
        } catch (IOException e) { showDesktopError(e); }
    }

    private int startPageSize() {
        return 4 * Math.max(1, (panelHeight - 82) / 42);
    }

    private int startPageCount() {
        return Math.max(1, (startTiles.size() + startPageSize() - 1) / startPageSize());
    }

    private void updateStartTiles() {
        if (panel != Panel.START) return;
        boolean visible = search.getValue().isEmpty();
        startPage = Math.clamp(startPage, 0, startPageCount() - 1);
        int first = startPage * startPageSize();
        for (int i = 0; i < startTiles.size(); i++) {
            var tile = startTiles.get(i);
            int local = i - first;
            boolean onPage = local >= 0 && local < startPageSize();
            if (onPage) tile.setPosition(panelX + 8 + local % 4 * tile.getWidth(), panelY + 54 + local / 4 * 42);
            show(tile, visible && onPage);
        }
        if (nextStartPage != null) {
            nextStartPage.setMessage(labelText((startPage + 1) + "/" + startPageCount()));
            show(nextStartPage, visible);
        }
    }

    private boolean recentView() {
        return panel == Panel.SEARCH && search.getValue().isEmpty();
    }

    private static String searchName(App app) {
        return app.title.getString().toLowerCase(Locale.ROOT);
    }

    private static void show(AbstractWidget widget, boolean visible) {
        widget.visible = visible;
        widget.active = visible;
    }

    private int resultsSplit() {
        return panelX + panelWidth / 2;
    }

    private void openSelectedResult() {
        if (selectedResult != null) {
            selectedResult.activate.run();
            if (openWith != null) {
                nextPanel = Panel.DESKTOP;
                applyPanelChange();
            }
        }
    }

    private void openApp(App app) {
        int offset = windows.backToFront().size() * 16 - 16;
        var existing = windows.get(app);
        windows.open(existing == null ? app : existing.app,
                (width - preferredWidth(app)) / 2.0 + offset, (barY - preferredHeight(app)) / 2.0 + offset);
        var window = windows.get(app);
        if (existing == null && app.equals(App.MARKETPLACE)) {
            window.content = marketplaceContent();
        } else if (existing == null && app.equals(App.FILES)) {
            var files = new PcFilesContent(windows.fileSystem, this::openFileEntry, this::drawFileIcon,
                    windows.quickFolders, windows.startFolders, windows.fileViews, windows.filePanes,
                    this::saveDesktopPreferences);
            files.openWith(this::chooseFileApp);
            window.content = files;
        } else if (existing == null && app.equals(App.NOTES)) {
            window.content = new PcNotesContent(windows.fileSystem);
        } else if (existing == null && app.equals(App.SETTINGS)) {
            window.content = settingsContent();
        } else if (existing == null && app.equals(App.PHOTOS)) {
            window.content = photosContent();
        } else if (existing == null && app.equals(App.CLOCK)) {
            window.content = new PcClockContent(windows.clock, windows.notifications, this::saveDesktopPreferences);
        } else if (existing == null && app.equals(App.ARCHIVE)) {
            window.content = new PcArchiveContent(windows.fileSystem);
        } else if (existing == null && app.equals(App.TERMINAL)) {
            window.content = new PcTerminalContent(windows.fileSystem, this::openFileEntry, this::openNote,
                    this::launchFromTerminal, this::terminalApps);
        } else if (existing == null && app.factory != null) {
            try {
                window.content = app.factory.get();
            } catch (RuntimeException e) {
                UltimateBankingSystem.LOGGER.warn("Could not start PC app {}", app.id, e);
            }
        }
        if (existing == null) bindAppServices(window);
        focusApp(window);
        nextPanel = Panel.DESKTOP;
    }

    private List<String> terminalApps() {
        return catalog.values().stream().filter(this::installed)
                .map(app -> app.title.getString() + "  [" + app.id + "]").sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void launchFromTerminal(String query) throws IOException {
        if (query.isBlank()) throw new IOException("Usage: launch <app name or ID>");
        var apps = catalog.values().stream().filter(this::installed).filter(app ->
                app.title.getString().equalsIgnoreCase(query) || app.id.toString().equalsIgnoreCase(query)).toList();
        if (apps.isEmpty()) throw new IOException("No installed app matches. Use apps to list them.");
        if (apps.size() > 1) throw new IOException("Use the full app ID.");
        openApp(apps.getFirst());
        applyPanelChange();
    }

    private void openNote(PcFileSystem.Entry entry) throws java.io.IOException {
        var existing = windows.get(App.NOTES);
        if (existing != null && existing.content instanceof PcNotesContent note) {
            note.open(entry);
        }
        openApp(App.NOTES);
        var content = windows.get(App.NOTES).content;
        if (existing == null && content instanceof PcNotesContent note) {
            note.open(entry);
        }
        applyPanelChange();
    }

    private App shortcutTarget(PcFileSystem.Entry entry) {
        return entry.shortcut() ? catalog.get(ResourceLocation.tryParse(entry.appTarget())) : null;
    }

    private PcPhotosContent photosContent() {
        return new PcPhotosContent(windows.fileSystem, this::showPhotoInFiles, this::copyPhotoToFiles,
                this::setPhotoWallpaper, this::setPhotoFullscreen);
    }

    private void openPhoto(PcFileSystem.Entry entry) throws java.io.IOException {
        var existing = windows.get(App.PHOTOS);
        var viewer = existing != null && existing.content instanceof PcPhotosContent content ? content : photosContent();
        viewer.open(entry);
        openApp(App.PHOTOS);
        var window = windows.get(App.PHOTOS);
        if (window.content != viewer) {
            window.content = viewer;
            bindAppServices(window);
        }
        applyPanelChange();
    }

    private void showPhotoInFiles(PcFileSystem.Entry entry) {
        openApp(App.FILES);
        if (entry != null && windows.get(App.FILES).content instanceof PcFilesContent files) {
            files.revealFile(entry);
        }
        applyPanelChange();
    }

    private void copyPhotoToFiles(PcFileSystem.Entry entry) {
        windows.clipboard.set(new PcClipboard.Image(entry.name(), entry.payload()));
    }

    private void saveScreenshot(BufferedImage image) {
        try {
            var drive = windows.fileSystem;
            UUID pictures = screenshotFolder(PcFileSystem.ROOT, "Pictures");
            UUID folder = screenshotFolder(pictures, "Screenshots");
            String stem = "Screenshot " + SCREENSHOT_DATE.format(LocalDateTime.now());
            var names = new HashSet<String>();
            for (var entry : drive.list(folder, "")) names.add(entry.name().toLowerCase(Locale.ROOT));
            String name = stem + ".png";
            for (int suffix = 2; names.contains(name.toLowerCase(Locale.ROOT)); suffix++) {
                name = stem + " (" + suffix + ").png";
            }
            var entry = drive.get(drive.createBinary(folder, name, PcPhotoImage.encodePng(image)));
            copyPhotoToFiles(entry);
            notice(entry, "Screenshot saved");
        } catch (IOException e) {
            notice(null, e.getMessage());
        }
    }

    private UUID screenshotFolder(UUID parent, String name) throws IOException {
        for (var entry : windows.fileSystem.list(parent, "")) {
            if (entry.folder() && entry.name().equalsIgnoreCase(name)) return entry.id();
        }
        return windows.fileSystem.create(parent, name, true, "");
    }

    private void copyScreenshotColor(String hex) {
        minecraft.keyboardHandler.setClipboard(hex);
        windows.clipboard.set(new PcClipboard.Text(hex));
        notice(null, "Copied " + hex);
    }

    private void notice(PcFileSystem.Entry entry, String message) {
        postNotification(App.PHOTOS, new PcAppNotification("", message == null ? "Screenshot could not be saved" : message,
                entry == null ? "" : "Saved in Pictures / Screenshots", -1, entry == null ? null : entry.id(),
                PcAppNotification.Delivery.NORMAL, List.of(), null), entry == null ? "" : "file:" + entry.id());
    }

    private PcSettingsContent settingsContent() {
        return new PcSettingsContent(windows, this::saveDesktopPreferences, catalog.values(), this::defaultExtensions,
                this::defaultLabel, this::chooseDefaultApp);
    }

    private void bindAppServices(Window<App> window) {
        visitApp(window, content -> content.setServices(appServices(window, content)));
        var app = window.app;
        visitApp(window, content -> content.setNotificationPublisher(payload -> Minecraft.getInstance().execute(() -> {
            if (windows.get(app) == window && window.content == content) postNotification(app, payload, "app:" + app.id);
        })));
    }

    private PcAppServices appServices(Window<App> owner, PcAppContent content) {
        var drive = new PcDriveView(windows.fileSystem);
        return new PcAppServices() {
            @Override public PcDrive drive() { return drive; }
            @Override public PcClipboard clipboard() { return windows.clipboard; }
            @Override public PcDrive appData() {
                String app = owner.app.id.toString();
                return new PcDriveView(windows.appDrives.computeIfAbsent(app, key -> new PcFileSystem(
                        preferencesPath == null ? null : preferencesPath.resolveSibling(preferencesPath.getFileName()
                                + ".app-" + UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
            }
            @Override public void pick(PcFileDialog.Request request, PcFileDialog.Accepted accepted, Runnable cancelled) {
                if (minecraft == null || minecraft.screen != PcDesktopScreen.this || windows.get(owner.app) != owner
                        || owner.content != content || windows.active() != owner || screenshot.active() || filePicker != null || openWith != null) {
                    cancelled.run();
                    return;
                }
                openPicker(request, accepted, cancelled);
            }
            @Override public void open(UUID file) throws IOException { openFileEntry(windows.fileSystem.get(file)); }
        };
    }

    private void openPicker(PcFileDialog.Request request, PcFileDialog.Accepted accepted, Runnable cancelled) {
        notificationCenter.setOpen(false, System.currentTimeMillis());
        closeDesktopMenu();
        dismissFlyouts();
        filePicker = new PcFilePicker(windows.fileSystem, request, result -> {
            var current = filePicker;
            filePicker = null;
            try { accepted.accept(result); }
            catch (IOException e) { if (filePicker == null) filePicker = current; throw e; }
            catch (RuntimeException e) {
                UltimateBankingSystem.LOGGER.warn("PC file picker callback failed", e);
                if (filePicker == null) filePicker = current;
                throw new IOException("The app could not finish this operation.", e);
            }
        }, () -> {
            filePicker = null;
            try { cancelled.run(); }
            catch (RuntimeException e) { UltimateBankingSystem.LOGGER.warn("PC file picker cancellation failed", e); }
        }, this::drawFileIcon);
    }

    boolean isDesktop(GlobalPos pc, PcDesktopWindows<App> session) {
        return position.equals(pc) && windows == session;
    }

    void postClockNotification(PcAppNotification payload) {
        postNotification(App.CLOCK, payload, "app:" + App.CLOCK.id);
    }

    private void postNotification(App app, PcAppNotification payload, String target) {
        long now = System.currentTimeMillis();
        var model = windows.notifications;
        boolean play = model.post(app.id.toString(), app.title.getString(), payload, target, now, notificationQuiet(now));
        boolean centerOpen = notificationCenter.open();
        boolean showing = Minecraft.getInstance().screen == this;
        if (centerOpen) model.seen();
        if (centerOpen || !showing) model.clearBanners();
        if (showing && play && minecraft != null && (!centerOpen || payload.delivery() == PcAppNotification.Delivery.ALARM)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.15F, 0.40F));
        }
        saveDesktopPreferences();
    }

    private boolean notificationQuiet(long now) {
        var photoWindow = windows.get(App.PHOTOS);
        boolean presenting = photoWindow != null && !photoWindow.minimized
                && windows.active() == photoWindow && photoWindow.content instanceof PcPhotosContent photos && photos.presenting();
        return windows.notifications.quiet(now, LocalDateTime.now().getHour(), presenting);
    }

    private void drawNotificationIcon(GuiGraphics g, String appId, int x, int y, int size) {
        var app = catalog.get(ResourceLocation.tryParse(appId));
        if (app != null) drawAppIcon(g, app, x, y, size);
        else PcFilesIcon.INFO.draw(g, x, y, size, 0xFFB5C5DE);
    }

    private void openNotificationSettings(String appId) {
        openApp(App.SETTINGS);
        if (windows.get(App.SETTINGS).content instanceof PcSettingsContent settings) settings.openNotifications(appId);
        applyPanelChange();
    }

    private void openNotificationTarget(String target) {
        try {
            if (target.startsWith("file:")) {
                openPhoto(windows.fileSystem.get(UUID.fromString(target.substring(5))));
            } else if (target.startsWith("app:")) {
                var app = catalog.get(ResourceLocation.tryParse(target.substring(4)));
                if (app != null && (App.BUILTINS.contains(app) || installedApps.contains(app.id))) {
                    openApp(app);
                    applyPanelChange();
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            notice(null, "The notification's file is no longer available.");
        }
    }

    private boolean notificationClock(double x, double y) {
        return y >= barY && y < height && x >= notificationClockLeft() && x < width - SHOW_DESKTOP_WIDTH;
    }

    private int notificationClockLeft() {
        return width - SHOW_DESKTOP_WIDTH - font.width(labelText("00-00-0000")) - 25;
    }

    private void toggleNotifications() {
        dismissFlyouts();
        closeDesktopMenu();
        if (panel != Panel.DESKTOP) {
            nextPanel = Panel.DESKTOP;
            applyPanelChange();
        }
        notificationCenter.toggle(System.currentTimeMillis());
    }

    private void setPhotoWallpaper(PcFileSystem.Entry entry, boolean lockScreen) {
        UUID id = entry == null ? null : entry.id();
        if (lockScreen) windows.photoLockScreen = id;
        else windows.photoWallpaper = id;
        saveDesktopPreferences();
    }

    private void setPhotoFullscreen(boolean enabled) {
        var window = windows.get(App.PHOTOS);
        if (window == null) return;
        if (enabled) {
            windows.photoPresentationMaximized = !window.maximized;
            windows.photoPresentationSnap = window.snapped;
            if (windows.photoPresentationMaximized) {
                toggleMaximize(window);
                rebuildWidgets();
            }
        } else {
            if (windows.photoPresentationMaximized && window.maximized) {
                toggleMaximize(window);
                window.snapped = windows.photoPresentationSnap;
                rebuildWidgets();
            }
            windows.photoPresentationMaximized = false;
            windows.photoPresentationSnap = null;
        }
    }

    private void openFileEntry(PcFileSystem.Entry entry) throws java.io.IOException {
        if (entry.shortcut()) {
            var app = shortcutTarget(entry);
            if (app == null || !App.BUILTINS.contains(app) && !installedApps.contains(app.id)) {
                throw new java.io.IOException("The shortcut's app is unavailable.");
            }
            openApp(app);
            applyPanelChange();
        } else if (entry.folder()) {
            openApp(App.FILES);
            if (windows.get(App.FILES).content instanceof PcFilesContent explorer) explorer.openFolder(entry.id());
            applyPanelChange();
        } else {
            var candidates = candidates(PcFileType.extension(entry.name()), entry);
            var app = defaultHandler(PcFileType.extension(entry.name()), candidates);
            if (app != null) openIn(app, entry);
            else chooseFileApp(entry);
        }
    }

    private boolean installed(App app) {
        return App.BUILTINS.contains(app) || installedApps.contains(app.id);
    }

    private List<App> candidates(String extension, PcFileSystem.Entry entry) {
        var matches = new ArrayList<App>();
        if (entry == null ? PcPhotoImage.supports("image." + extension) : PcPhotosContent.canOpen(entry)) matches.add(App.PHOTOS);
        if ((entry == null || entry.binary()) && List.of("zip", "jar").contains(extension)) matches.add(App.ARCHIVE);
        if ((entry == null || !entry.binary()) && extension.equals("pcsh")) matches.add(App.TERMINAL);
        if (entry == null ? PcFileType.canImportAsText("text." + extension)
                || List.of("markdown", "mdown", "mkd").contains(extension) : !entry.binary()) matches.add(App.NOTES);
        for (var app : catalog.values()) {
            if (installed(app) && fileHandlers.getOrDefault(app.id, Set.of()).contains(extension) && !matches.contains(app)) matches.add(app);
        }
        return matches;
    }

    private List<String> defaultExtensions() {
        var extensions = new java.util.TreeSet<>(PcFileType.extensions());
        extensions.addAll(List.of("", "markdown", "mdown", "mkd", "jar", "pcsh"));
        for (var app : catalog.values()) if (installed(app)) extensions.addAll(fileHandlers.getOrDefault(app.id, Set.of()));
        extensions.addAll(windows.defaultApps.keySet());
        extensions.removeIf(ext -> candidates(ext, null).isEmpty());
        return List.copyOf(extensions);
    }

    private App defaultHandler(String extension, List<App> candidates) {
        String configured = windows.defaultApps.get(extension);
        for (var app : candidates) if (app.id.toString().equals(configured)) return app;
        if (candidates.contains(App.PHOTOS)) return App.PHOTOS;
        if (candidates.contains(App.ARCHIVE)) return App.ARCHIVE;
        if (candidates.contains(App.TERMINAL)) return App.TERMINAL;
        if (candidates.contains(App.NOTES)) return App.NOTES;
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private String defaultLabel(String extension) {
        var app = defaultHandler(extension, candidates(extension, null));
        return app == null ? "Choose app" : app.title.getString();
    }

    private void chooseDefaultApp(String extension) { chooseHandler(extension, null); }

    private void chooseFileApp(PcFileSystem.Entry entry) {
        if (entry.folder() || entry.shortcut()) return;
        chooseHandler(PcFileType.extension(entry.name()), entry);
    }

    private void chooseHandler(String extension, PcFileSystem.Entry file) {
        var candidates = candidates(extension, file);
        var current = defaultHandler(extension, candidates);
        String type = extension.isEmpty() ? "files without an extension" : "." + extension + " files";
        var choices = candidates.stream().map(app -> new PcOpenWith.Choice(app, app.equals(current))).toList();
        openWith = new PcOpenWith(file == null ? "Default app" : "Open with", file == null ? type : file.name(),
                choices, file == null, (app, always) -> {
                    if (!installed(app)) throw new IOException("This app is no longer available.");
                    if (file != null) {
                        var latest = windows.fileSystem.get(file.id());
                        if (!PcFileType.extension(latest.name()).equals(extension) || !candidates(extension, latest).contains(app)) {
                            throw new IOException("This file changed. Open it again to choose an app.");
                        }
                        var chooser = openWith;
                        openWith = null;
                        try { openIn(app, latest); }
                        catch (IOException e) { openWith = chooser; throw e; }
                    }
                    if (always) {
                        windows.defaultApps.put(extension, app.id.toString());
                        saveDesktopPreferences();
                    }
                }, () -> openWith = null, PcDesktopScreen::drawAppIcon);
        closeDesktopMenu();
        dismissFlyouts();
        notificationCenter.setOpen(false, System.currentTimeMillis());
    }

    private void openIn(App app, PcFileSystem.Entry entry) throws IOException {
        if (app.equals(App.NOTES)) openNote(entry);
        else if (app.equals(App.PHOTOS)) openPhoto(entry);
        else {
            openApp(app);
            var window = windows.get(app);
            if (window.content == null) throw new IOException("This app could not start.");
            try { window.content.openFile(entry.id()); }
            catch (RuntimeException e) {
                UltimateBankingSystem.LOGGER.warn("PC app {} could not open {}", app.id, entry.name(), e);
                throw new IOException("The app could not open this file.", e);
            }
            applyPanelChange();
        }
    }

    private void activateDesktopItem(PcFileSystem.Entry entry) {
        try {
            openFileEntry(windows.fileSystem.get(entry.id()));
        } catch (java.io.IOException e) {
            showDesktopError(e);
        }
    }

    private void showDesktopError(java.io.IOException error) {
        desktopItemMessage = error.getMessage();
        desktopMessageUntil = Util.getMillis() + 3_000;
    }

    private void createDesktopItem(boolean folder) {
        try {
            UUID id = windows.fileSystem.createDefault(windows.fileSystem.desktopFolder(), folder);
            syncDesktopItems();
            selectedShortcuts.clear();
            selectedShortcuts.add(id);
            shortcuts.stream().filter(button -> button.desktopEntry.id().equals(id)).findFirst()
                    .ifPresent(this::beginShortcutRename);
        } catch (java.io.IOException e) {
            showDesktopError(e);
        }
    }

    private void recycleDesktopSelection() {
        try {
            windows.fileSystem.recycleAll(selectedShortcuts);
            syncDesktopItems();
        } catch (java.io.IOException e) {
            showDesktopError(e);
        }
    }

    private void openDesktopFolder() {
        try {
            openFileEntry(windows.fileSystem.get(windows.fileSystem.desktopFolder()));
        } catch (java.io.IOException e) {
            showDesktopError(e);
        }
    }

    private void drawFileIcon(GuiGraphics graphics, PcFileSystem.Entry entry, int x, int y, int size) {
        var target = shortcutTarget(entry);
        boolean highRes = size * Minecraft.getInstance().getWindow().getGuiScale() > FILE_ICON_PIXELS;
        if (target != null) {
            drawAppIcon(graphics, target, x, y, size);
        } else if (entry.folder() && highRes) {
            drawFileTexture(graphics, HIGH_RES_FOLDER_ICON, x, y, size, HIGH_RES_FILE_ICON_PIXELS);
        } else if (entry.folder() || entry.shortcut()) {
            drawIcon(graphics, entry.folder() ? Icon.FILES : Icon.OPEN, x, y, size);
        } else {
            int pixels = highRes ? HIGH_RES_FILE_ICON_PIXELS : FILE_ICON_PIXELS;
            var textures = FILE_ICONS.get(PcFileType.iconFor(entry.name(), entry.empty()));
            drawFileTexture(graphics, textures.texture(windows.darkFileIcons, highRes), x, y, size, pixels);
        }
        if (entry.shortcut()) drawShortcutBadge(graphics, x, y + size);
    }

    private static void drawFileTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, int pixels) {
        RenderSystem.enableBlend();
        graphics.blit(texture, x, y, size, size, 0f, 0f, pixels, pixels, pixels, pixels);
        RenderSystem.disableBlend();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        var window = windows.active();
        if (panel == Panel.DESKTOP && window != null && window.content instanceof PcFilesContent explorer) {
            explorer.importFiles(paths);
        } else {
            showDesktopError(new java.io.IOException("Open a folder in Files before dropping files."));
        }
    }

    private static void drawShortcutBadge(GuiGraphics graphics, int x, int bottom) {
        int y = bottom - 7;
        graphics.fill(x, y, x + 7, y + 7, 0xFFF0F3FA);
        int ink = 0xFF375C91;
        graphics.hLine(x + 2, x + 5, y + 1, ink);
        graphics.vLine(x + 5, y + 1, y + 5, ink);
        for (int offset = 0; offset < 4; offset++) {
            graphics.fill(x + 1 + offset, y + 5 - offset, x + 2 + offset, y + 6 - offset, ink);
        }
    }

    private PcAppContent marketplaceContent() {
        var apps = catalog.values().stream().filter(app -> app.factory != null || app.equals(App.FILES)
                        || app.equals(App.NOTES) || app.equals(App.PHOTOS) || app.equals(App.CLOCK)
                        || app.equals(App.ARCHIVE) || app.equals(App.TERMINAL))
                .sorted(Comparator.comparing(PcDesktopScreen::searchName)).toList();
        return new PcMarketplaceContent(apps, index -> App.BUILTINS.contains(apps.get(index))
                || installedApps.contains(apps.get(index).id), index -> {
            openApp(apps.get(index));
            applyPanelChange();
        }, index -> {
            ResourceLocation id = apps.get(index).id;
            if (installedApps.add(id)) {
                if (PcAppInstallations.save(desktopUser, installedApps)) {
                    for (var shortcut : shortcuts) {
                        windows.desktopItemPositions.putIfAbsent(shortcut.desktopEntry.id(), shortcut.shortcutCell);
                    }
                    try {
                        windows.fileSystem.createDesktopShortcut(id.toString(), apps.get(index).title.getString());
                    } catch (java.io.IOException e) {
                        UltimateBankingSystem.LOGGER.warn("Could not create desktop shortcut for {}", id, e);
                    }
                    rebuildWidgets();
                    postNotification(App.MARKETPLACE, new PcAppNotification("App installed",
                            apps.get(index).title.getString() + " is ready to open."), "app:" + id);
                } else {
                    installedApps.remove(id);
                    postNotification(App.MARKETPLACE, new PcAppNotification("Installation failed", apps.get(index).title.getString()), "");
                }
            }
        }, index -> installedApps.contains(apps.get(index).id), index -> {
            App app = apps.get(index);
            installedApps.remove(app.id);
            if (PcAppInstallations.save(desktopUser, installedApps)) {
                windows.forget(app);
                windows.savedPins.remove(app.id.toString());
                rebuildWidgets();
                postNotification(App.MARKETPLACE, new PcAppNotification("App removed", app.title.getString()), "");
            } else {
                installedApps.add(app.id);
            }
        });
    }

    private void initCaption(Window<App> window) {
        caption("Minimize", Icon.MINIMIZE, () -> {
            window.minimized = true;
            focusApp(windows.active());
        });
        if (window.app.sizing.maximizable()) {
            caption(window.maximized ? "Restore" : "Maximize", window.maximized ? Icon.RESTORE : Icon.MAXIMIZE,
                    () -> toggleMaximize(window));
        }
        caption("Close", Icon.CLOSE, () -> closeApp(window.app));
        positionCaption(window);
    }

    private void caption(String title, Icon icon, Runnable action) {
        captionButtons.add(addWidget(new PcButton(0, 0, CAPTION_WIDTH, TITLE_HEIGHT - 2, label(title), icon, false, () -> {
            action.run();
            nextPanel = Panel.DESKTOP;
        })));
    }

    private void positionCaption(Window<App> window) {
        int firstX = (int) windowX(window) + windowWidth(window) - captionWidth(window) - 1;
        for (int i = 0; i < captionButtons.size(); i++) {
            captionButtons.get(i).setPosition(firstX + i * CAPTION_WIDTH, (int) windowY(window) + 1);
        }
    }

    private int captionWidth(Window<App> window) {
        return (window.app.sizing.maximizable() ? 3 : 2) * CAPTION_WIDTH;
    }

    private void toggleMaximize(Window<App> window) {
        window.maximized = !window.maximized;
        window.snapped = null;
    }

    private double windowX(Window<App> window) {
        return window.maximized ? 0 : window.snapped != null ? window.snapped.bounds(width, barY).x() : window.x;
    }

    private double windowY(Window<App> window) {
        return window.maximized ? 0 : window.snapped != null ? window.snapped.bounds(width, barY).y() : window.y;
    }

    private int windowWidth(Window<App> window) {
        return window.maximized ? width : window.snapped != null ? window.snapped.bounds(width, barY).width() : normalWidth(window);
    }

    private int windowHeight(Window<App> window) {
        return window.maximized ? barY : window.snapped != null ? window.snapped.bounds(width, barY).height() : normalHeight(window);
    }

    private int normalWidth(Window<App> window) {
        return window.width == 0 ? preferredWidth(window.app) : Math.min(width, window.width);
    }

    private int normalHeight(Window<App> window) {
        return window.height == 0 ? preferredHeight(window.app) : Math.min(barY, window.height);
    }

    private int preferredWidth(App app) {
        var sizing = app.sizing;
        return Math.min(width, Math.clamp(sizing.initialWidth() == 0 ? restoredWidth : sizing.initialWidth(),
                sizing.minWidth(), sizing.maxWidth()));
    }

    private int preferredHeight(App app) {
        var sizing = app.sizing;
        return Math.min(barY, Math.clamp(sizing.initialHeight() == 0 ? restoredHeight : sizing.initialHeight(),
                sizing.minHeight(), sizing.maxHeight()));
    }

    private void moveWindow(Window<App> window, double x, double y) {
        window.move(x, y, width - normalWidth(window), barY - normalHeight(window));
    }

    private ResizeEdge resizeEdgeAt(Window<App> window, double x, double y) {
        if (window.maximized || window.snapped != null || !window.app.sizing.resizable()) {
            return null;
        }
        var edge = ResizeEdge.at(x - window.x, y - window.y,
                normalWidth(window), normalHeight(window), RESIZE_BORDER);
        return edge == null ? null : edge.allowedBy(window.app.sizing);
    }

    private SnapZone snapAt(Window<App> window, double x, double y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        boolean top = y < SNAP_EDGE;
        boolean bottom = y >= barY - SNAP_EDGE;
        SnapZone zone = x < SNAP_EDGE ? (top ? SnapZone.TOP_LEFT : bottom ? SnapZone.BOTTOM_LEFT : SnapZone.LEFT)
                : x >= width - SNAP_EDGE ? (top ? SnapZone.TOP_RIGHT : bottom ? SnapZone.BOTTOM_RIGHT : SnapZone.RIGHT)
                : top ? SnapZone.MAXIMIZE : null;
        return zone != null && canSnap(window, zone) ? zone : null;
    }

    private boolean canSnap(Window<App> window, SnapZone zone) {
        if (zone == SnapZone.MAXIMIZE) {
            return window.app.sizing.maximizable();
        }
        Bounds bounds = zone.bounds(width, barY);
        var sizing = window.app.sizing;
        return sizing.resizable() && bounds.width() >= sizing.minWidth() && bounds.width() <= sizing.maxWidth()
                && bounds.height() >= sizing.minHeight() && bounds.height() <= sizing.maxHeight();
    }

    private void commitSnap(Window<App> window, SnapZone zone) {
        window.maximized = zone == SnapZone.MAXIMIZE;
        window.snapped = window.maximized ? null : zone;
        nextPanel = Panel.DESKTOP;
        applyPanelChange();
    }

    private void openWindowMenu(Window<App> window, int x, int y) {
        dismissFlyouts();
        focusApp(null);
        boolean normal = !window.maximized && window.snapped == null;
        var commands = new ArrayList<ContextCommand>();
        if (normal) {
            commands.add(new ContextCommand("Move", false, () -> beginKeyboardDrag(window, null)));
            ResizeEdge edge = ResizeEdge.BOTTOM_RIGHT.allowedBy(window.app.sizing);
            if (window.app.sizing.resizable() && edge != null) {
                commands.add(new ContextCommand("Size", false, () -> beginKeyboardDrag(window, edge)));
            }
        }
        if (window.app.sizing.maximizable() || !normal) {
            commands.add(new ContextCommand(normal ? "Maximize" : "Restore", false, () -> {
                if (normal) {
                    toggleMaximize(window);
                } else {
                    window.maximized = false;
                    window.snapped = null;
                }
                nextPanel = Panel.DESKTOP;
                applyPanelChange();
            }));
        }
        commands.add(new ContextCommand("Minimize", false, () -> {
            window.minimized = true;
            focusApp(windows.active());
            nextPanel = Panel.DESKTOP;
            applyPanelChange();
        }));
        commands.add(new ContextCommand("Close window", false, () -> {
            closeApp(window.app);
            applyPanelChange();
        }));
        desktopMenu = new DesktopPopup(x, y, commands);
        lastMenuX = x;
        lastMenuY = y;
    }

    private void beginKeyboardDrag(Window<App> window, ResizeEdge edge) {
        keyboardWindowDrag = new KeyboardWindowDrag(window,
                new Bounds(window.x, window.y, normalWidth(window), normalHeight(window)), window.width, window.height, edge);
    }

    private void endKeyboardDrag(boolean cancel) {
        var drag = keyboardWindowDrag;
        keyboardWindowDrag = null;
        if (cancel) {
            drag.window.x = drag.start.x();
            drag.window.y = drag.start.y();
            drag.window.width = drag.savedWidth;
            drag.window.height = drag.savedHeight;
            positionCaption(drag.window);
        }
        focusApp(drag.window);
    }

    private void handleKeyboardDrag(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            endKeyboardDrag(key == GLFW.GLFW_KEY_ESCAPE);
            return;
        }
        int dx = key == GLFW.GLFW_KEY_LEFT ? -8 : key == GLFW.GLFW_KEY_RIGHT ? 8 : 0;
        int dy = key == GLFW.GLFW_KEY_UP ? -8 : key == GLFW.GLFW_KEY_DOWN ? 8 : 0;
        if (dx == 0 && dy == 0) {
            return;
        }
        var drag = keyboardWindowDrag;
        var window = drag.window;
        if (drag.edge == null) {
            moveWindow(window, window.x + dx, window.y + dy);
        } else {
            var bounds = new Bounds(window.x, window.y, normalWidth(window), normalHeight(window))
                    .resize(drag.edge, dx, dy, width, barY, window.app.sizing);
            window.width = bounds.width();
            window.height = bounds.height();
        }
        positionCaption(window);
    }

    private Window<App> windowAt(double x, double y) {
        if (x < 0 || y < 0 || x >= width || y >= barY) {
            return null;
        }
        for (var window : windows.frontToBack()) {
            double left = windowX(window);
            double top = windowY(window);
            if (!window.minimized && (resizeEdgeAt(window, x, y) != null
                    || x >= left && x < left + windowWidth(window) && y >= top && y < top + windowHeight(window))) {
                return window;
            }
        }
        return null;
    }

    private record ResizeDrag(Window<App> window, ResizeEdge edge, Bounds start, int savedWidth, int savedHeight,
                              double mouseX, double mouseY) {}

    private enum ResizeCursor { HORIZONTAL, VERTICAL, DOWN_RIGHT, DOWN_LEFT, CROSSHAIR }

    private void startResize(Window<App> window, ResizeEdge edge, double x, double y) {
        clearFocus();
        resizeDrag = new ResizeDrag(window, edge, new Bounds(window.x, window.y, normalWidth(window), normalHeight(window)),
                window.width, window.height, x, y);
    }

    private void updateResize(double x, double y) {
        var drag = resizeDrag;
        Bounds bounds = drag.start.resize(drag.edge, x - drag.mouseX, y - drag.mouseY,
                width, barY, drag.window.app.sizing);
        var window = drag.window;
        window.x = bounds.x();
        window.y = bounds.y();
        window.width = bounds.width() == drag.start.width() ? drag.savedWidth : bounds.width();
        window.height = bounds.height() == drag.start.height() ? drag.savedHeight : bounds.height();
        positionCaption(window);
    }

    private void finishResize(boolean cancel) {
        var drag = resizeDrag;
        resizeDrag = null;
        if (cancel) {
            drag.window.x = drag.start.x();
            drag.window.y = drag.start.y();
            drag.window.width = drag.savedWidth;
            drag.window.height = drag.savedHeight;
            positionCaption(drag.window);
        }
    }

    private void updateResizeCursor(int x, int y, boolean blocked) {
        ResizeEdge edge = null;
        if (resizeDrag != null) {
            edge = resizeDrag.edge;
        } else if (pointerApp != null && pointerApp.content instanceof PcFilesContent files && files.resizingPane()) {
            edge = ResizeEdge.LEFT;
        } else if (!blocked && panel == Panel.DESKTOP && draggedWindow == null && desktopPress == null && shortcutEditor == null) {
            var window = windowAt(x, y);
            if (window != null) {
                edge = resizeEdgeAt(window, x, y);
                if (edge == null && window.content instanceof PcFilesContent files && files.overDivider(x, y)) {
                    edge = ResizeEdge.LEFT;
                }
            }
        }
        ResizeCursor cursor = edge == null ? null : switch (edge) {
            case LEFT, RIGHT -> ResizeCursor.HORIZONTAL;
            case TOP, BOTTOM -> ResizeCursor.VERTICAL;
            case TOP_LEFT, BOTTOM_RIGHT -> ResizeCursor.DOWN_RIGHT;
            case TOP_RIGHT, BOTTOM_LEFT -> ResizeCursor.DOWN_LEFT;
        };
        if (screenshot.active()) cursor = screenshot.crosshair(x, y) ? ResizeCursor.CROSSHAIR : null;
        if (currentResizeCursor != cursor) {
            currentResizeCursor = cursor;
            long handle = cursor == null ? 0 : resizeCursors.computeIfAbsent(cursor, type -> switch (type) {
                case HORIZONTAL -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
                case VERTICAL -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
                case CROSSHAIR -> GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
                case DOWN_RIGHT, DOWN_LEFT -> diagonalResizeCursor(type == ResizeCursor.DOWN_LEFT);
            });
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), handle);
        }
    }

    private static long diagonalResizeCursor(boolean mirrored) {
        // GLFW 3.3 has standard horizontal and vertical arrows only.
        try (var stack = MemoryStack.stackPush()) {
            int size = 19;
            var pixels = stack.malloc(size * size * 4);
            for (int y = 0; y < size; y++) {
                for (int column = 0; column < size; column++) {
                    int x = mirrored ? size - 1 - column : column;
                    boolean fill = diagonalArrow(x, y);
                    boolean outline = diagonalArrow(x - 1, y) || diagonalArrow(x + 1, y)
                            || diagonalArrow(x, y - 1) || diagonalArrow(x, y + 1);
                    byte color = (byte) (fill ? 255 : 0);
                    pixels.put(color).put(color).put(color).put((byte) (fill || outline ? 255 : 0));
                }
            }
            pixels.flip();
            return GLFW.glfwCreateCursor(GLFWImage.malloc(stack).set(size, size, pixels), size / 2, size / 2);
        }
    }

    private static boolean diagonalArrow(int x, int y) {
        return x >= 3 && x <= 15 && y >= 3 && y <= 15
                && (Math.abs(x - y) <= 1 || Math.min(x, y) == 3 && Math.max(x, y) <= 8
                || Math.max(x, y) == 15 && Math.min(x, y) >= 10);
    }

    private void togglePanel(Panel target) {
        Panel destination = afterSlide == null ? panel : afterSlide;
        nextPanel = destination == target ? Panel.DESKTOP : target;
    }

    private void applyPanelChange() {
        if (nextPanel == null) {
            return;
        }
        Panel target = nextPanel;
        nextPanel = null;
        if (panel == Panel.DESKTOP) {
            switchPanel(target);
        } else if (target == panel && afterSlide != null) {
            slideFrom = launcherOffset(Util.getMillis());
            slideStarted = Util.getMillis();
            afterSlide = null;
            if (panel == Panel.SEARCH) {
                setInitialFocus(search);
            }
        } else if (target != panel) {
            if (afterSlide == null) {
                long now = Util.getMillis();
                slideFrom = launcherOffset(now);
                slideStarted = now;
                clearFocus();
            }
            afterSlide = target;
        }
    }

    private void switchPanel(Panel target) {
        panel = target;
        searchKind = SearchKind.ALL;
        afterSlide = null;
        draggedWindow = null;
        search = null;
        powerOpen = false;
        rebuildWidgets();
        slideFrom = panelHeight + 7.0;
        slideStarted = Util.getMillis();
    }

    private double launcherOffset(long now) {
        if (afterSlide != null) {
            double elapsed = Math.clamp((now - slideStarted) / (double) SLIDE_OUT_MS, 0, 1);
            return slideFrom + (panelHeight + 7.0 - slideFrom) * elapsed * elapsed;
        }
        double remaining = 1 - Math.clamp((now - slideStarted) / (double) SLIDE_IN_MS, 0, 1);
        return slideFrom * remaining * remaining * remaining;
    }

    @Override
    public void tick() {
        if (screenshot.active()) return;
        if (desktopRevision != windows.fileSystem.revision() && desktopPress == null && shortcutEditor == null
                && desktopMenu == null) {
            syncDesktopItems();
        }
        if (afterSlide != null && Util.getMillis() - slideStarted >= SLIDE_OUT_MS) {
            switchPanel(afterSlide);
        }
    }

    private PcButton taskbarAppAt(double x, double y) {
        for (var button : taskbarButtons) {
            if (button.isMouseOver(x, y)) {
                return button;
            }
        }
        return null;
    }

    private record Flyout(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private Flyout flyoutAbove(PcButton anchor, int preferredWidth, int flyoutHeight) {
        int w = Math.min(preferredWidth, width - 12);
        int x = Math.clamp(anchor.getX() + (anchor.getWidth() - w) / 2, 6, width - 6 - w);
        return new Flyout(x, barY - 5 - flyoutHeight, w, flyoutHeight);
    }

    private Flyout jumpBounds() {
        return flyoutAbove(jumpAnchor, 166, jumpItems.size() * FLYOUT_ROW + 8);
    }

    private Flyout previewBounds() {
        return flyoutAbove(previewAnchor, 166, 103);
    }

    private Flyout previewClose(Flyout bounds) {
        return new Flyout(bounds.x + bounds.w - 23, bounds.y + 3, 20, 18);
    }

    private void showJumpList(App app, boolean keyboard) {
        if (panel != Panel.DESKTOP) {
            switchPanel(Panel.DESKTOP);
        }
        dismissFlyouts();
        jumpAnchor = taskbarButtons.stream().filter(button -> button.taskApp == app).findFirst().orElse(null);
        if (jumpAnchor == null) {
            return;
        }
        boolean running = windows.isOpen(app);
        boolean pinned = isPinned(app);
        var bounds = flyoutAbove(jumpAnchor, 166, (running ? 3 : 2) * FLYOUT_ROW + 8);
        jumpItem(bounds, app.title, app.icon, () -> openApp(app)).appIcon = app;
        jumpItem(bounds, label(pinned ? "Unpin from taskbar" : "Pin to taskbar"), pinned ? Icon.UNPIN : Icon.PIN, () -> {
            windows.setPinned(app, !pinned);
            windows.savedPins.put(app.id.toString(), !pinned);
            rebuildWidgets();
        });
        if (running) {
            jumpItem(bounds, label("Close window"), Icon.CLOSE, () -> closeApp(app));
        }
        if (keyboard) {
            setInitialFocus(jumpItems.getFirst());
        }
    }

    private PcButton jumpItem(Flyout bounds, Component name, Icon icon, Runnable action) {
        var item = addWidget(new PcButton(bounds.x + 4, bounds.y + 4 + jumpItems.size() * FLYOUT_ROW,
                bounds.w - 8, FLYOUT_ROW, name, icon, true, action));
        jumpItems.add(item);
        return item;
    }

    private void closeApp(App app) {
        var window = windows.get(app);
        if (window != null && window.content instanceof PcNotesContent notes) {
            notes.requestClose(() -> closeAppNow(app));
            if (windows.get(app) != null && focusedApp != window) openApp(app);
            return;
        }
        closeAppNow(app);
    }

    private void closeAppNow(App app) {
        if (focusedApp == windows.get(app)) {
            focusApp(null);
        }
        windows.close(app);
        focusApp(windows.active());
        nextPanel = Panel.DESKTOP;
    }

    private void focusApp(Window<App> window) {
        if (focusedApp == window) {
            return;
        }
        visitApp(focusedApp, app -> app.focusChanged(false));
        focusedApp = window;
        visitApp(window, app -> app.focusChanged(true));
    }

    private Window<App> keyboardApp() {
        return focusedApp != null && focusedApp == windows.active() && panel == Panel.DESKTOP
                && desktopMenu == null && jumpAnchor == null && shortcutEditor == null && resizeDrag == null
                && getFocused() == null ? focusedApp : null;
    }

    private void visitApp(Window<App> window, Consumer<PcAppContent> operation) {
        invokeApp(window, content -> {
            operation.accept(content);
            return true;
        });
    }

    private boolean invokeApp(Window<App> window, Predicate<PcAppContent> operation) {
        if (window == null || window.content == null) {
            return false;
        }
        try {
            return operation.test(window.content);
        } catch (RuntimeException e) {
            UltimateBankingSystem.LOGGER.warn("PC app {} stopped responding", window.app.id, e);
            window.disposeContent();
            return false;
        }
    }

    private boolean invokeKeyboardApp(Predicate<PcAppContent> operation) {
        var window = keyboardApp();
        return invokeApp(window, app -> {
            layoutApp(window, (int) windowX(window) + 1, contentTop(window), windowWidth(window) - 2,
                    windowHeight(window) - TITLE_HEIGHT - 2);
            return operation.test(app);
        });
    }

    private void layoutApp(Window<App> window, int x, int y, int width, int height) {
        if (window.contentX != x || window.contentY != y || window.contentWidth != width || window.contentHeight != height) {
            window.content.layout(x, y, width, height);
            window.contentX = x;
            window.contentY = y;
            window.contentWidth = width;
            window.contentHeight = height;
        }
    }

    private void clearPreview() {
        previewAnchor = null;
        hoverAnchor = null;
        leftPreviewAt = 0;
        peekSince = 0;
    }

    private void dismissFlyouts() {
        if (jumpItems.contains(getFocused())) {
            clearFocus();
        }
        jumpItems.forEach(this::removeWidget);
        jumpItems.clear();
        jumpAnchor = null;
        if (previewAnchor != null) {
            dismissedPreview = previewAnchor;
        }
        clearPreview();
    }

    private void updatePreview(int x, int y, long now) {
        if (panel != Panel.DESKTOP || jumpAnchor != null || desktopMenu != null || draggedWindow != null || resizeDrag != null) {
            clearPreview();
            return;
        }
        var candidate = taskbarAppAt(x, y);
        if (candidate != dismissedPreview) {
            dismissedPreview = null;
        }
        if (candidate != null && candidate != dismissedPreview && windows.isOpen(candidate.taskApp)) {
            if (hoverAnchor != candidate) {
                hoverAnchor = candidate;
                hoverSince = now;
            }
            if ((previewAnchor != null && previewAnchor != candidate) || now - hoverSince >= PREVIEW_DELAY_MS) {
                if (previewAnchor != candidate) {
                    peekSince = 0;
                }
                previewAnchor = candidate;
                leftPreviewAt = 0;
                peekSince = 0;
            }
        } else if (previewAnchor != null && candidate == null) {
            Flyout bounds = previewBounds();
            boolean inside = bounds.contains(x, y);
            boolean bridge = x >= bounds.x && x < bounds.x + bounds.w && y >= bounds.y + bounds.h
                    && y < previewAnchor.getY();
            if (inside || bridge) {
                leftPreviewAt = 0;
                if (inside && !previewClose(bounds).contains(x, y)) {
                    if (peekSince == 0) {
                        peekSince = now;
                    }
                } else {
                    peekSince = 0;
                }
            } else {
                peekSince = 0;
                if (leftPreviewAt == 0) {
                    leftPreviewAt = now;
                } else if (now - leftPreviewAt >= PREVIEW_LEAVE_MS) {
                    clearPreview();
                }
            }
        } else {
            clearPreview();
        }
    }

    private void renderHoverLabel(GuiGraphics graphics, int mouseX, int mouseY, long now) {
        PcButton next = null;
        if (jumpAnchor == null && previewAnchor == null && desktopMenu == null
                && draggedWindow == null && panel == Panel.DESKTOP) {
            var appButton = taskbarAppAt(mouseX, mouseY);
            if (appButton != null && !windows.isOpen(appButton.taskApp)) {
                next = appButton;
            } else if (startButton.isMouseOver(mouseX, mouseY)) {
                next = startButton;
            }
        }
        if (next != hoverLabel) {
            hoverLabel = next;
            labelSince = now;
        }
        if (hoverLabel == null || now - labelSince < PREVIEW_DELAY_MS) {
            return;
        }
        Component title = hoverLabel.getMessage();
        Flyout bounds = flyoutAbove(hoverLabel, font.width(title) + 16, 19);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, LAYER_STEP + 5);
        roundRect(graphics, bounds.x, bounds.y, bounds.w, bounds.h, 0xFF303030);
        drawCentered(graphics, title, bounds.x + bounds.w / 2, bounds.y + 5, TEXT);
        graphics.pose().popPose();
    }

    private record ContextCommand(Component title, boolean checked, List<ContextCommand> children, Runnable action) {
        ContextCommand(String title, boolean checked, Runnable action) {
            this(label(title), checked, List.of(), action);
        }

        ContextCommand(String title, List<ContextCommand> children) {
            this(label(title), false, children, null);
        }
    }

    private ContextCommand iconSize(String title, int size) {
        return new ContextCommand(title, windows.desktopIconSize == size, () -> {
            windows.desktopIconSize = size;
            rebuildWidgets();
        });
    }

    private ContextCommand desktopOrder(String title, boolean descending) {
        return new ContextCommand(title, windows.descendingDesktopOrder == descending, () -> {
            windows.descendingDesktopOrder = descending;
            windows.desktopItemPositions.clear();
            rebuildWidgets();
        });
    }

    private boolean isEmptyDesktop(double x, double y) {
        return panel == Panel.DESKTOP && x >= 0 && x < width && y >= 0 && y < barY
                && windowAt(x, y) == null && shortcutAt(x, y) == null;
    }

    private int desktopRows() {
        return Math.max(1, (barY - GRID_TOP + 4) / desktopRowHeight());
    }

    private int desktopColumns() {
        return Math.max(1, (width - GRID_LEFT + 4) / GRID_COLUMN);
    }

    private int desktopRowHeight() {
        return windows.desktopIconSize + 20;
    }

    private boolean fitsDesktop(Cell cell) {
        return cell.column() >= 0 && cell.column() < desktopColumns() && cell.row() >= 0 && cell.row() < desktopRows();
    }

    private Cell nearestDesktopCell(Cell preferred, Predicate<Cell> available) {
        if (fitsDesktop(preferred) && available.test(preferred)) {
            return preferred;
        }
        Cell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int column = 0; column < desktopColumns(); column++) {
            for (int row = 0; row < desktopRows(); row++) {
                double dx = (column - preferred.column()) * (double) GRID_COLUMN;
                double dy = (row - preferred.row()) * (double) desktopRowHeight();
                double distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    var cell = new Cell(column, row);
                    if (available.test(cell)) {
                        best = cell;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private PcButton shortcutAt(double x, double y) {
        for (var shortcut : shortcuts) {
            if (shortcut.isMouseOver(x, y)) {
                return shortcut;
            }
        }
        return null;
    }

    private record ShortcutClick(UUID id, long time, boolean label) {}

    private static final class DesktopPress {
        final PcButton shortcut;
        final double x;
        final double y;
        final boolean toggle;
        final boolean extend;
        final Set<UUID> previous;
        boolean dragging;
        double dx;
        double dy;

        DesktopPress(PcButton shortcut, double x, double y, boolean toggle, boolean extend, Set<UUID> previous) {
            this.shortcut = shortcut;
            this.x = x;
            this.y = y;
            this.toggle = toggle;
            this.extend = extend;
            this.previous = previous;
        }

        Flyout selectionBox() {
            int left = (int) Math.floor(x + Math.min(0, dx));
            int top = (int) Math.floor(y + Math.min(0, dy));
            return new Flyout(left, top, (int) Math.ceil(x + Math.max(0, dx)) - left,
                    (int) Math.ceil(y + Math.max(0, dy)) - top);
        }
    }

    private void pressDesktop(double x, double y) {
        clearFocus();
        var shortcut = shortcutAt(x, y);
        boolean toggle = hasControlDown();
        boolean extend = hasShiftDown();
        desktopPress = new DesktopPress(shortcut, x, y, toggle, extend, Set.copyOf(selectedShortcuts));
        if (shortcut == null || toggle || extend) {
            lastShortcutClick = null;
        }
        if (!toggle && !extend && (shortcut == null || !selectedShortcuts.contains(shortcut.desktopEntry.id()))) {
            selectedShortcuts.clear();
        }
        if (shortcut != null) {
            selectedShortcuts.add(shortcut.desktopEntry.id());
        }
    }

    private void updateDesktopDrag(DesktopPress press, double x, double y) {
        if (!press.dragging && Math.max(Math.abs(x - press.x), Math.abs(y - press.y)) < DRAG_THRESHOLD) {
            return;
        }
        press.dragging = true;
        lastShortcutClick = null;
        press.dx = Math.clamp(x, 0, width) - press.x;
        press.dy = Math.clamp(y, 0, barY) - press.y;
        if (press.shortcut == null) {
            var box = press.selectionBox();
            selectedShortcuts.clear();
            if (press.toggle || press.extend) {
                selectedShortcuts.addAll(press.previous);
            }
            for (var shortcut : shortcuts) {
                UUID id = shortcut.desktopEntry.id();
                if (shortcut.intersectsShortcut(box)) {
                    if (press.toggle && press.previous.contains(id)) {
                        selectedShortcuts.remove(id);
                    } else {
                        selectedShortcuts.add(id);
                    }
                }
            }
        }
    }

    private void dropShortcuts(DesktopPress press) {
        Cell origin = press.shortcut.shortcutCell;
        Cell preferred = origin.translate((int) Math.round(press.dx / GRID_COLUMN),
                (int) Math.round(press.dy / desktopRowHeight()));
        var moving = shortcuts.stream().filter(button -> selectedShortcuts.contains(button.desktopEntry.id())).toList();
        var occupied = shortcuts.stream().filter(button -> !selectedShortcuts.contains(button.desktopEntry.id()))
                .map(button -> button.shortcutCell).toList();
        Cell destination = nearestDesktopCell(preferred, cell -> {
            for (var shortcut : moving) {
                Cell moved = shortcut.shortcutCell.translate(cell.column() - origin.column(), cell.row() - origin.row());
                if (!fitsDesktop(moved) || occupied.contains(moved)) {
                    return false;
                }
            }
            return true;
        });
        if (destination != null && !destination.equals(origin)) {
            for (var shortcut : shortcuts) {
                windows.desktopItemPositions.put(shortcut.desktopEntry.id(), shortcut.shortcutCell);
            }
            for (var shortcut : moving) {
                windows.desktopItemPositions.put(shortcut.desktopEntry.id(), shortcut.shortcutCell.translate(
                        destination.column() - origin.column(), destination.row() - origin.row()));
            }
            rebuildWidgets();
        }
    }

    private void releaseDesktop(double x, double y) {
        var press = desktopPress;
        desktopPress = null;
        updateDesktopDrag(press, x, y);
        if (press.dragging) {
            if (press.shortcut != null && x >= 0 && x < width && y >= 0 && y < barY && windowAt(x, y) == null) {
                dropShortcuts(press);
            }
            return;
        }
        var shortcut = press.shortcut;
        if (shortcut == null) {
            return;
        }
        UUID id = shortcut.desktopEntry.id();
        if (press.toggle) {
            if (press.previous.contains(id)) {
                selectedShortcuts.remove(id);
            }
            return;
        }
        if (press.extend) {
            return;
        }
        selectedShortcuts.clear();
        selectedShortcuts.add(id);
        setInitialFocus(shortcut);
        long now = Util.getMillis();
        boolean onLabel = shortcut.labelBounds().contains(press.x, press.y) && shortcut.labelBounds().contains(x, y);
        ShortcutClick previous = lastShortcutClick;
        lastShortcutClick = new ShortcutClick(id, now, onLabel);
        if (previous != null && previous.id().equals(id)) {
            if (now - previous.time() <= DOUBLE_CLICK_MS) {
                activateDesktopItem(shortcut.desktopEntry);
            } else if (onLabel && previous.label()) {
                beginShortcutRename(shortcut);
            }
        }
    }

    private void beginShortcutRename(PcButton shortcut) {
        lastShortcutClick = null;
        editingShortcut = shortcut;
        int editorWidth = Math.min(width, Math.max(SHORTCUT_WIDTH + 10, font.width(shortcut.getMessage()) + 14));
        int x = Math.clamp(shortcut.getX() + (SHORTCUT_WIDTH - editorWidth) / 2, 0, width - editorWidth);
        shortcutEditor = addWidget(new EditBox(font, x, shortcut.labelBounds().y - 2, editorWidth, 13, label("Shortcut name")));
        shortcutEditor.setMaxLength(120);
        String name = shortcut.desktopEntry.name();
        shortcutEditor.setValue(name);
        setInitialFocus(shortcutEditor);
        int extension = !shortcut.desktopEntry.folder() && !shortcut.desktopEntry.shortcut() ? name.lastIndexOf('.') : -1;
        shortcutEditor.setCursorPosition(extension > 0 ? extension : name.length());
        shortcutEditor.setHighlightPos(0);
    }

    private void finishShortcutRename(boolean save) {
        String name = shortcutEditor.getValue();
        if (save && !name.equals(editingShortcut.desktopEntry.name())) {
            try {
                windows.fileSystem.rename(editingShortcut.desktopEntry.id(), name);
                for (var shortcut : shortcuts) {
                    windows.desktopItemPositions.putIfAbsent(shortcut.desktopEntry.id(), shortcut.shortcutCell);
                }
                editingShortcut.setMessage(labelText(name));
            } catch (java.io.IOException e) {
                showDesktopError(e);
            }
        }
        clearFocus();
        removeWidget(shortcutEditor);
        shortcutEditor = null;
        editingShortcut = null;
    }

    private static boolean overlaps(Flyout a, Flyout b) {
        return a.w > 0 && a.h > 0 && b.w > 0 && b.h > 0
                && a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
    }

    private void renderDesktopGesture(GuiGraphics graphics) {
        if (desktopPress == null || !desktopPress.dragging) {
            return;
        }
        var press = desktopPress;
        if (press.shortcut == null) {
            var box = press.selectionBox();
            if (box.w > 0 && box.h > 0) {
                graphics.fill(box.x, box.y, box.x + box.w, box.y + box.h, 0x3848A9FF);
                graphics.renderOutline(box.x, box.y, box.w, box.h, 0xCC62B9FF);
            }
        } else {
            for (var shortcut : shortcuts) {
                if (selectedShortcuts.contains(shortcut.desktopEntry.id())) {
                    shortcut.renderShortcut(graphics, shortcut.getX() + (int) Math.round(press.dx),
                            shortcut.getY() + (int) Math.round(press.dy), true);
                }
            }
        }
    }

    private void openDesktopMenu(int x, int y) {
        dismissFlyouts();
        var commands = new ArrayList<>(List.of(
                new ContextCommand("View", List.of(iconSize("Large icons", 28), iconSize("Medium icons", 22),
                        iconSize("Small icons", 16), new ContextCommand("Show desktop icons", windows.showDesktopIcons, () -> {
                            windows.showDesktopIcons = !windows.showDesktopIcons;
                            rebuildWidgets();
                        }))),
                new ContextCommand("Sort by", List.of(desktopOrder("Name (A to Z)", false), desktopOrder("Name (Z to A)", true))),
                new ContextCommand("Refresh", false, this::rebuildWidgets),
                new ContextCommand("New", List.of(new ContextCommand("Folder", false, () -> createDesktopItem(true)),
                        new ContextCommand("Text document", false, () -> createDesktopItem(false)))),
                new ContextCommand("Open Desktop folder", false, this::openDesktopFolder)));
        var event = NeoForge.EVENT_BUS.post(new PcDesktopMenuEvent(position));
        for (var entry : event.entries()) {
            Component title = entry.label().copy().withStyle(style -> style.withFont(FONT));
            commands.add(new ContextCommand(title, false, List.of(), () -> {
                try {
                    entry.action().run();
                } catch (RuntimeException e) {
                    UltimateBankingSystem.LOGGER.warn("PC desktop command failed", e);
                }
            }));
        }
        var ids = new HashSet<>(App.BUILTINS.stream().map(App::id).toList());
        for (var entry : event.apps()) {
            if (ids.add(entry.id())) {
                Component title = entry.title().copy().withStyle(style -> style.withFont(FONT));
                var app = catalog.getOrDefault(entry.id(), new App(entry.id(), title, Icon.OPEN, null, false, entry.options(), null));
                commands.add(new ContextCommand(title, false, List.of(), () -> {
                    openApp(app);
                    applyPanelChange();
                }));
            }
        }
        desktopMenu = new DesktopPopup(x, y, commands);
        lastMenuX = x;
        lastMenuY = y;
    }

    private void closeDesktopSubmenu() {
        if (desktopSubmenu != null) {
            desktopSubmenu.removeRows();
            desktopSubmenu = null;
            submenuParent = null;
        }
    }

    private void closeDesktopMenu() {
        closeDesktopSubmenu();
        if (desktopMenu != null) {
            desktopMenu.removeRows();
            desktopMenu = null;
        }
    }

    private void selectDesktopCommand(PcButton row) {
        var command = row.contextCommand;
        if (!command.children().isEmpty()) {
            if (submenuParent != command) {
                closeDesktopSubmenu();
                var parent = desktopMenu.bounds;
                int x = parent.x + parent.w + parent.w <= width ? parent.x + parent.w - 1 : parent.x - parent.w + 1;
                desktopSubmenu = new DesktopPopup(x, row.getY() - 4, command.children());
                submenuParent = command;
            }
        } else {
            closeDesktopMenu();
            command.action().run();
        }
    }

    private void updateDesktopSubmenu(int x, int y) {
        if (desktopMenu == null || x == lastMenuX && y == lastMenuY) {
            return;
        }
        lastMenuX = x;
        lastMenuY = y;
        if (!desktopMenu.bounds.contains(x, y)
                || desktopSubmenu != null && desktopSubmenu.bounds.contains(x, y)) {
            return;
        }
        for (var row : desktopMenu.rows) {
            if (row.isMouseOver(x, y)) {
                if (row.contextCommand.children().isEmpty()) {
                    closeDesktopSubmenu();
                } else {
                    selectDesktopCommand(row);
                }
                return;
            }
        }
    }

    private final class DesktopPopup {
        private final Flyout bounds;
        private final List<ContextCommand> commands;
        private final List<PcButton> rows = new ArrayList<>();
        private final int capacity;
        private int offset;

        DesktopPopup(int x, int y, List<ContextCommand> commands) {
            this.commands = commands;
            int menuWidth = Math.min(154, width - 8);
            capacity = Math.min(commands.size(), Math.max(1, (barY - 12) / FLYOUT_ROW));
            int menuHeight = capacity * FLYOUT_ROW + 8;
            bounds = new Flyout(Math.clamp(x, 4, width - menuWidth - 4),
                    Math.clamp(y, 2, Math.max(2, barY - menuHeight - 2)), menuWidth, menuHeight);
            buildRows();
        }

        private void buildRows() {
            for (int index = 0; index < capacity; index++) {
                var command = commands.get(offset + index);
                var row = new PcButton(bounds.x + 4, bounds.y + 4 + index * FLYOUT_ROW,
                        bounds.w - 8, FLYOUT_ROW, command.title(), null, true, () -> {});
                row.contextCommand = command;
                rows.add(addWidget(row));
            }
        }

        void removeRows() {
            if (rows.contains(getFocused())) {
                clearFocus();
            }
            rows.forEach(PcDesktopScreen.this::removeWidget);
            rows.clear();
        }

        void scroll(int nextOffset) {
            int clamped = Math.clamp(nextOffset, 0, commands.size() - capacity);
            if (offset != clamped) {
                if (this == desktopMenu) {
                    closeDesktopSubmenu();
                }
                removeRows();
                offset = clamped;
                buildRows();
            }
        }

        void moveFocus(int delta) {
            int current = rows.indexOf(getFocused());
            int index = current < 0 ? (delta > 0 ? offset : offset + capacity - 1)
                    : Math.floorMod(offset + current + delta, commands.size());
            if (index < offset || index >= offset + capacity) {
                scroll(delta < 0 ? index : index - capacity + 1);
            }
            setInitialFocus(rows.get(index - offset));
        }

        void render(GuiGraphics graphics, int mouseX, int mouseY) {
            renderFlyoutFrame(graphics, bounds, JUMP_LIST_TINT);
            if (this == desktopMenu && commands.size() > 3 && offset < 3 && offset + capacity > 3) {
                int y = bounds.y + 3 + (3 - offset) * FLYOUT_ROW;
                graphics.fill(bounds.x + 5, y, bounds.x + bounds.w - 5, y + 1, BORDER);
            }
            for (var row : rows) {
                row.render(graphics, mouseX, mouseY, 0);
            }
            if (commands.size() > capacity) {
                int track = bounds.h - 8;
                int thumb = Math.max(8, track * capacity / commands.size());
                int top = bounds.y + 4 + (track - thumb) * offset / (commands.size() - capacity);
                graphics.fill(bounds.x + bounds.w - 3, top, bounds.x + bounds.w - 2, top + thumb, BORDER);
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (openWith != null) { openWith.click(x, y, button); return true; }
        if (filePicker != null) { filePicker.click(x, y, button); return true; }
        if (screenshot.active()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) screenshot.mouseClicked(x, y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && notificationClock(x, y)) {
            toggleNotifications();
            return true;
        }
        if (notificationCenter.click(x, y, button, System.currentTimeMillis())) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && panel == Panel.DESKTOP && x >= width - 2 && y < barY
                && resizeDrag == null && pointerApp == null && draggedWindow == null) {
            notificationEdgeDrag = true;
            return true;
        }
        if (keyboardWindowDrag != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                endKeyboardDrag(false);
            }
            return true;
        }
        if (resizeDrag != null) {
            return true;
        }
        if (shortcutEditor != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shortcutEditor.isMouseOver(x, y)) {
                return super.mouseClicked(x, y, button);
            }
            finishShortcutRename(true);
        }
        if (showDesktopButton.mouseClicked(x, y, button)) {
            return true;
        }
        if (panel == Panel.DESKTOP && y < barY && desktopMenu == null && jumpAnchor == null
                && (previewAnchor == null || !previewBounds().contains(x, y))) {
            var target = windowAt(x, y);
            if (target != null && target.content != null && resizeEdgeAt(target, x, y) == null
                    && y >= contentTop(target) && x >= (int) windowX(target) + 1
                    && x < (int) windowX(target) + windowWidth(target) - 1
                    && y < (int) windowY(target) + windowHeight(target) - 1) {
                dismissFlyouts();
                if (target != windows.active()) {
                    openApp(target.app);
                    applyPanelChange();
                }
                clearFocus();
                focusApp(target);
                double clickX = x;
                double clickY = y;
                if (invokeApp(target, content -> {
                    layoutApp(target, (int) windowX(target) + 1, contentTop(target), windowWidth(target) - 2,
                            windowHeight(target) - TITLE_HEIGHT - 2);
                    return content.mouseClicked(clickX, clickY, button);
                })) {
                    pointerApp = target;
                    pointerButton = button;
                }
                return true;
            }
        }
        if (windowAt(x, y) != focusedApp || desktopMenu != null || jumpAnchor != null || y >= barY) {
            focusApp(null);
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || panel != Panel.DESKTOP || y >= barY
                || windowAt(x, y) != null || desktopMenu != null || jumpAnchor != null || previewAnchor != null) {
            lastShortcutClick = null;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            var appButton = taskbarAppAt(x, y);
            if (appButton != null) {
                closeDesktopMenu();
                showJumpList(appButton.taskApp, false);
                return true;
            }
        }
        if (desktopMenu != null) {
            var popup = desktopSubmenu != null && desktopSubmenu.bounds.contains(x, y) ? desktopSubmenu : desktopMenu;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && popup.bounds.contains(x, y)) {
                for (var row : popup.rows) {
                    if (row.isMouseOver(x, y)) {
                        selectDesktopCommand(row);
                        break;
                    }
                }
                return true;
            }
            closeDesktopMenu();
            if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isEmptyDesktop(x, y)) {
            openDesktopMenu((int) x, (int) y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && panel == Panel.DESKTOP && jumpAnchor == null && previewAnchor == null
                && windowAt(x, y) == null && y < barY) {
            var item = shortcutAt(x, y);
            if (item != null) {
                if (!selectedShortcuts.contains(item.desktopEntry.id())) {
                    selectedShortcuts.clear();
                    selectedShortcuts.add(item.desktopEntry.id());
                }
                var commands = new ArrayList<ContextCommand>();
                commands.add(new ContextCommand("Open", false, () -> activateDesktopItem(item.desktopEntry)));
                if (!item.desktopEntry.folder() && !item.desktopEntry.shortcut()) {
                    commands.add(new ContextCommand("Open with", false, () -> chooseFileApp(item.desktopEntry)));
                }
                commands.add(new ContextCommand("Rename", false, () -> beginShortcutRename(item)));
                commands.add(new ContextCommand("Delete", false, this::recycleDesktopSelection));
                desktopMenu = new DesktopPopup((int) x, (int) y, commands);
                lastMenuX = (int) x;
                lastMenuY = (int) y;
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && panel == Panel.DESKTOP && jumpAnchor == null && previewAnchor == null) {
            var target = windowAt(x, y);
            if (target != null && y >= windowY(target) && y < windowY(target) + TITLE_HEIGHT
                    && resizeEdgeAt(target, x, y) == null) {
                openApp(target.app);
                applyPanelChange();
                openWindowMenu(target, (int) x, (int) y);
                return true;
            }
        }
        if (jumpAnchor != null) {
            if (jumpBounds().contains(x, y)) {
                for (var item : jumpItems) {
                    if (item.mouseClicked(x, y, button)) {
                        break;
                    }
                }
                applyPanelChange();
                return true;
            }
            dismissFlyouts();
            return true;
        }
        if (previewAnchor != null) {
            var bounds = previewBounds();
            App app = previewAnchor.taskApp;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.contains(x, y)) {
                dismissFlyouts();
                if (previewClose(bounds).contains(x, y)) {
                    closeApp(app);
                } else {
                    openApp(app);
                }
                applyPanelChange();
                return true;
            }
            dismissFlyouts();
        }
        if (panel != Panel.DESKTOP && y < barY && (afterSlide != null || launcherOffset(Util.getMillis()) >= 1)) {
            return true;
        }
        if (powerOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (overPowerMenu(x, y)) {
                for (var item : powerItems) {
                    if (item.mouseClicked(x, y, button)) {
                        break;
                    }
                }
                return true;
            }
            setPowerOpen(false);
            if (powerButton.isMouseOver(x, y)) {
                return true;
            }
        }
        if (search != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && x >= panelX + 8 && x < panelX + panelWidth - 8 && y >= panelY + 8 && y < panelY + 31) {
            x = Math.clamp(x, search.getX(), search.getX() + search.getWidth() - 1);
        }
        if (panel == Panel.DESKTOP && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && y < barY) {
            var window = windowAt(x, y);
            if (window != null) {
                if (window != windows.active()) {
                    openApp(window.app);
                    applyPanelChange();
                }
                ResizeEdge edge = resizeEdgeAt(window, x, y);
                if (edge != null) {
                    startResize(window, edge, x, y);
                    return true;
                }
                double localX = x - windowX(window);
                double localY = y - windowY(window);
                if (localY < TITLE_HEIGHT) {
                    if (localX >= windowWidth(window) - captionWidth(window) - 1) {
                        for (var control : captionButtons) {
                            if (control.mouseClicked((int) windowX(window) + localX, (int) windowY(window) + localY, button)) {
                                break;
                            }
                        }
                        applyPanelChange();
                    } else {
                        long now = Util.getMillis();
                        if (window.app.sizing.maximizable() && captionClickWindow == window
                                && now - captionClickTime <= DOUBLE_CLICK_MS
                                && Math.abs(x - captionClickX) <= DRAG_THRESHOLD
                                && Math.abs(y - captionClickY) <= DRAG_THRESHOLD) {
                            captionClickWindow = null;
                            toggleMaximize(window);
                            nextPanel = Panel.DESKTOP;
                            applyPanelChange();
                            return true;
                        }
                        captionClickWindow = window;
                        captionClickTime = now;
                        captionClickX = x;
                        captionClickY = y;
                        draggedWindow = window;
                        grabX = localX;
                        grabY = localY;
                    }
                }
                return true;
            }
            if (x >= 0 && x < width && y >= 0) {
                pressDesktop(x, y);
                return true;
            }
        }
        boolean handled = super.mouseClicked(x, y, button);
        if (!handled && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (panel == Panel.START || panel == Panel.SEARCH)
                && (x < panelX || x >= panelX + panelWidth || y < panelY || y >= panelY + panelHeight)) {
            nextPanel = Panel.DESKTOP;
            handled = true;
        }
        applyPanelChange();
        return handled;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (filePicker != null || openWith != null) return true;
        if (screenshot.active()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) screenshot.mouseDragged(x, y);
            return true;
        }
        if (notificationEdgeDrag) return true;
        if (pointerApp != null && button == pointerButton) {
            visitApp(pointerApp, app -> app.mouseDragged(x, y, button, deltaX, deltaY));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resizeDrag != null) {
            updateResize(x, y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && desktopPress != null) {
            updateDesktopDrag(desktopPress, x, y);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggedWindow == null) {
            return super.mouseDragged(x, y, button, deltaX, deltaY);
        }
        if (Math.abs(x - captionClickX) > DRAG_THRESHOLD || Math.abs(y - captionClickY) > DRAG_THRESHOLD) {
            captionClickWindow = null;
        }
        if (draggedWindow.maximized || draggedWindow.snapped != null) {
            if (captionClickWindow != null) {
                return true;
            }
            grabX = grabX / windowWidth(draggedWindow) * normalWidth(draggedWindow);
            draggedWindow.maximized = false;
            draggedWindow.snapped = null;
            rebuildWidgets();
        }
        moveWindow(draggedWindow, x - grabX, y - grabY);
        pendingSnap = captionClickWindow == null ? snapAt(draggedWindow, x, y) : null;
        positionCaption(draggedWindow);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (filePicker != null || openWith != null) { pointerApp = null; return true; }
        if (screenshot.active()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) screenshot.mouseReleased(x, y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && notificationEdgeDrag) {
            notificationEdgeDrag = false;
            if (x < width - 35 && !notificationCenter.open()) toggleNotifications();
            return true;
        }
        if (pointerApp != null && button == pointerButton) {
            var target = pointerApp;
            pointerApp = null;
            visitApp(target, app -> app.mouseReleased(x, y, button));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resizeDrag != null) {
            updateResize(x, y);
            finishResize(false);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && desktopPress != null) {
            releaseDesktop(x, y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedWindow != null) {
            var window = draggedWindow;
            draggedWindow = null;
            if (pendingSnap != null) {
                pendingSnap = null;
                SnapZone zone = snapAt(window, x, y);
                if (zone != null) {
                    commitSnap(window, zone);
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (openWith != null) { if (deltaY != 0) openWith.scroll(deltaY); return true; }
        if (filePicker != null) { if (deltaY != 0) filePicker.scroll(deltaY); return true; }
        if (!screenshot.active() && notificationCenter.scroll(x, y, deltaY)) return true;
        if (search != null && !search.getValue().isBlank() && deltaY != 0 && x >= panelX && x < resultsSplit()
                && y >= panelY + 62 && y < panelY + panelHeight) {
            searchScroll = Math.clamp(searchScroll + (deltaY < 0 ? 1 : -1), 0, Math.max(0, matches.size() - searchRows.size()));
            updateSearchRows();
            return true;
        }
        if (screenshot.active()) return true;
        if (desktopMenu != null) {
            if (desktopMenu.bounds.contains(x, y)) {
                desktopMenu.scroll(desktopMenu.offset - (int) Math.signum(deltaY));
            }
            return true;
        }
        var target = windowAt(x, y);
        if (panel == Panel.DESKTOP && jumpAnchor == null && previewAnchor == null && resizeDrag == null
                && target != null && target.content != null && y >= contentTop(target)) {
            visitApp(target, app -> {
                layoutApp(target, (int) windowX(target) + 1, contentTop(target), windowWidth(target) - 2,
                        windowHeight(target) - TITLE_HEIGHT - 2);
                app.mouseScrolled(x, y, deltaX, deltaY);
            });
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (openWith != null) { openWith.key(keyCode); return true; }
        if (filePicker != null) { filePicker.key(keyCode, scanCode, modifiers); return true; }
        if (screenshot.active()) return screenshot.keyPressed(keyCode, modifiers);
        int screenshotKeys = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT;
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & screenshotKeys) == screenshotKeys
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
            if (keyboardWindowDrag == null && resizeDrag == null && draggedWindow == null && pointerApp == null
                    && desktopPress == null && !notificationEdgeDrag) {
                notificationCenter.suspend(System.currentTimeMillis());
                screenshot.begin();
            }
            return true;
        }
        int notificationKeys = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;
        if (keyCode == GLFW.GLFW_KEY_S && ((modifiers & GLFW.GLFW_MOD_SUPER) != 0
                || (modifiers & notificationKeys) == notificationKeys)) {
            notificationCenter.setOpen(false, System.currentTimeMillis());
            searchKind = SearchKind.ALL;
            nextPanel = Panel.SEARCH;
            applyPanelChange();
            if (search != null) setInitialFocus(search);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_N && ((modifiers & GLFW.GLFW_MOD_SUPER) != 0
                || (modifiers & notificationKeys) == notificationKeys)) {
            toggleNotifications();
            return true;
        }
        if (notificationCenter.key(keyCode, scanCode, modifiers, System.currentTimeMillis())) return true;
        if (keyboardWindowDrag != null) {
            handleKeyboardDrag(keyCode);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F6 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && panel == Panel.DESKTOP && desktopMenu == null && jumpAnchor == null && shortcutEditor == null
                && resizeDrag == null && draggedWindow == null && pointerApp == null && windows.backToFront().size() > 1) {
            var target = windows.backToFront().iterator().next();
            dismissFlyouts();
            openApp(target.app);
            applyPanelChange();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F10 && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && keyboardApp() != null) {
            var target = keyboardApp();
            openWindowMenu(target, (int) windowX(target) + 8, (int) windowY(target) + TITLE_HEIGHT);
            setInitialFocus(desktopMenu.rows.getFirst());
            return true;
        }
        if (invokeKeyboardApp(app -> app.keyPressed(keyCode, scanCode, modifiers))) {
            return true;
        }
        if (resizeDrag != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishResize(true);
            }
            return true;
        }
        if (shortcutEditor != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishShortcutRename(keyCode != GLFW.GLFW_KEY_ESCAPE);
            } else {
                shortcutEditor.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (desktopPress != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            selectedShortcuts.clear();
            selectedShortcuts.addAll(desktopPress.previous);
            desktopPress = null;
            lastShortcutClick = null;
            return true;
        }
        if (desktopMenu != null) {
            var focused = getFocused();
            var popup = desktopSubmenu != null && desktopSubmenu.rows.contains(focused) ? desktopSubmenu : desktopMenu;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_LEFT && popup == desktopSubmenu) {
                if (desktopSubmenu == null) {
                    closeDesktopMenu();
                } else {
                    var parent = submenuParent;
                    closeDesktopSubmenu();
                    desktopMenu.rows.stream().filter(row -> row.contextCommand == parent).findFirst().ifPresent(this::setInitialFocus);
                }
            } else if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
                popup.moveFocus(keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_TAB && hasShiftDown() ? -1 : 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE
                    || keyCode == GLFW.GLFW_KEY_RIGHT && popup == desktopMenu) {
                var row = popup.rows.contains(focused) ? (PcButton) focused : popup.rows.getFirst();
                if (keyCode != GLFW.GLFW_KEY_RIGHT || !row.contextCommand.children().isEmpty()) {
                    selectDesktopCommand(row);
                    if (desktopSubmenu != null && !row.contextCommand.children().isEmpty()) {
                        setInitialFocus(desktopSubmenu.rows.getFirst());
                    }
                }
            }
            return true;
        }
        if (panel == Panel.DESKTOP && jumpAnchor == null && getFocused() == null
                && (keyCode == GLFW.GLFW_KEY_MENU || keyCode == GLFW.GLFW_KEY_F10 && hasShiftDown())) {
            openDesktopMenu(width / 2, barY / 2);
            setInitialFocus(desktopMenu.rows.getFirst());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (jumpAnchor != null || previewAnchor != null)) {
            dismissFlyouts();
            return true;
        }
        if (jumpAnchor != null) {
            int index = jumpItems.indexOf(getFocused());
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
                int delta = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_TAB && hasShiftDown() ? -1 : 1;
                setInitialFocus(jumpItems.get(index < 0 ? (delta > 0 ? 0 : jumpItems.size() - 1)
                        : Math.floorMod(index + delta, jumpItems.size())));
            } else if (index >= 0) {
                jumpItems.get(index).keyPressed(keyCode, scanCode, modifiers);
                applyPanelChange();
            }
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_MENU || keyCode == GLFW.GLFW_KEY_F10 && hasShiftDown())
                && getFocused() instanceof PcButton button && button.taskApp != null) {
            showJumpList(button.taskApp, true);
            return true;
        }
        if (afterSlide != null) {
            return true;
        }
        if (panel == Panel.DESKTOP && keyCode == GLFW.GLFW_KEY_F2) {
            if (getFocused() instanceof PcButton button && button.desktopEntry != null) {
                beginShortcutRename(button);
                return true;
            }
            if (getFocused() == null && selectedShortcuts.size() == 1) {
                for (var shortcut : shortcuts) {
                    if (selectedShortcuts.contains(shortcut.desktopEntry.id())) {
                        beginShortcutRename(shortcut);
                        return true;
                    }
                }
            }
        }
        if (panel == Panel.DESKTOP && keyCode == GLFW.GLFW_KEY_DELETE && focusedApp == null
                && !selectedShortcuts.isEmpty() && (getFocused() == null || shortcuts.contains(getFocused()))) {
            recycleDesktopSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && panel != Panel.DESKTOP) {
            if (powerOpen) {
                setPowerOpen(false);
                setInitialFocus(powerButton);
                return true;
            }
            nextPanel = Panel.DESKTOP;
            applyPanelChange();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (getFocused() instanceof PcButton button && button.visible && button.searchResult != null) {
                selectedResult = button.searchResult;
                openSelectedResult();
                applyPanelChange();
                return true;
            }
            if (search != null && search.isFocused() && selectedResult != null) {
                openSelectedResult();
                applyPanelChange();
                return true;
            }
        }
        if (search != null && search.isFocused() && !matches.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
                int delta = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                int index = Math.floorMod(matches.indexOf(selectedResult) + delta, matches.size());
                selectedResult = matches.get(index);
                if (index < searchScroll) searchScroll = index;
                if (index >= searchScroll + searchRows.size()) searchScroll = index - searchRows.size() + 1;
                updateSearchRows();
                return true;
            }
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        applyPanelChange();
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (filePicker != null || openWith != null) return true;
        if (screenshot.active()) return true;
        return invokeKeyboardApp(app -> app.keyReleased(keyCode, scanCode, modifiers))
                || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (openWith != null) return true;
        if (filePicker != null) { filePicker.typed(character, modifiers); return true; }
        if (screenshot.active()) return true;
        return notificationCenter.typed(character, modifiers)
                || invokeKeyboardApp(app -> app.charTyped(character, modifiers)) || super.charTyped(character, modifiers);
    }

    private int contentTop(Window<App> window) {
        return (int) windowY(window) + TITLE_HEIGHT + 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        filePicker = null;
        openWith = null;
        screenshot.close();
        notificationCenter.close(System.currentTimeMillis());
        pointerApp = null;
        if (keyboardWindowDrag != null) {
            endKeyboardDrag(true);
        }
        focusApp(null);
        PcDesktopPreferences.save(preferencesPath, windows);
        if (resizeDrag != null) {
            finishResize(true);
        }
        updateResizeCursor(-1, -1, true);
        for (long cursor : resizeCursors.values()) {
            if (cursor != 0) {
                GLFW.glfwDestroyCursor(cursor);
            }
        }
        resizeCursors.clear();
        if (photoWallpaper != null) {
            photoWallpaper.close();
            photoWallpaper = null;
        }
        backdrop.close();
        minecraft.getMainRenderTarget().bindWrite(false);
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (wallpaperRevision != windows.fileSystem.revision() || !java.util.Objects.equals(wallpaperImage, windows.photoWallpaper)) {
            refreshPhotoWallpaper();
        }
        if (photoWallpaper == null) {
            drawWallpaper(graphics, width, height);
        } else {
            photoWallpaper.drawBackground(graphics, width, height);
        }
    }

    private void refreshPhotoWallpaper() {
        if (photoWallpaper != null) photoWallpaper.close();
        photoWallpaper = null;
        wallpaperImage = windows.photoWallpaper;
        wallpaperRevision = windows.fileSystem.revision();
        if (wallpaperImage == null) return;
        try {
            photoWallpaper = PcPhotoTexture.load(windows.fileSystem, wallpaperImage);
        } catch (java.io.IOException ignored) {
            // Use the default background while the saved image is unavailable.
        }
    }

    static void drawWallpaper(GuiGraphics graphics, int width, int height) {
        int sourceWidth = Math.min(WALLPAPER_WIDTH, Math.max(1, height == 0 ? 1 : width * WALLPAPER_HEIGHT / height));
        int sourceHeight = Math.min(WALLPAPER_HEIGHT, Math.max(1, width == 0 ? 1 : height * WALLPAPER_WIDTH / width));
        graphics.blit(WALLPAPER, 0, 0, width, height,
                (WALLPAPER_WIDTH - sourceWidth) / 2f, (WALLPAPER_HEIGHT - sourceHeight) / 2f,
                sourceWidth, sourceHeight, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (screenshot.active() && screenshot.ready()) {
            updateResizeCursor(mouseX, mouseY, true);
            renderScreenshotOverlay(graphics, mouseX, mouseY);
            return;
        }
        long now = Util.getMillis();
        updateDesktopSubmenu(mouseX, mouseY);
        updatePreview(mouseX, mouseY, now);
        Window<App> peek = previewAnchor != null && peekSince != 0 && now - peekSince >= PEEK_DELAY_MS
                ? windows.get(previewAnchor.taskApp) : null;
        boolean overFlyout = desktopMenu != null || jumpAnchor != null && jumpBounds().contains(mouseX, mouseY)
                || previewAnchor != null && previewBounds().contains(mouseX, mouseY);
        updateResizeCursor(mouseX, mouseY, overFlyout || peek != null || notificationCenter.open() || notificationCenter.over(mouseX, mouseY));
        var stack = windows.backToFront();
        graphics.pose().pushPose();
        // Keep desktop layers below shared screen overlays and tooltips.
        graphics.pose().translate(0, 0, TASKBAR_DEPTH - (stack.size() + 2) * LAYER_STEP);
        renderBackground(graphics, mouseX, mouseY, partialTick);
        advanceLayer(graphics);
        boolean shortcutsHovered = panel == Panel.DESKTOP && !overFlyout && desktopPress == null
                && shortcutEditor == null && peek == null && windowAt(mouseX, mouseY) == null;
        graphics.enableScissor(0, 0, width, barY);
        for (var shortcut : shortcuts) {
            shortcut.render(graphics, shortcutsHovered ? mouseX : -1, shortcutsHovered ? mouseY : -1, partialTick);
        }
        renderDesktopGesture(graphics);
        if (shortcutEditor != null) {
            shortcutEditor.render(graphics, mouseX, mouseY, partialTick);
        }
        if (desktopItemMessage != null && now < desktopMessageUntil) {
            var message = labelText(desktopItemMessage);
            int w = Math.min(width - 12, font.width(message) + 16);
            int left = (width - w) / 2;
            graphics.fill(left, barY - 30, left + w, barY - 8, SURFACE);
            graphics.enableScissor(left + 6, barY - 30, left + w - 6, barY - 8);
            graphics.drawString(font, message, left + 8, barY - 23, TEXT, false);
            graphics.disableScissor();
        }
        var active = windows.active();
        for (var window : stack) {
            advanceLayer(graphics);
            if (peek == null ? !window.minimized : window == peek) {
                renderWindow(graphics, window, window == active, peek == null && !overFlyout,
                        mouseX, mouseY, partialTick);
            }
        }
        if (pendingSnap != null) {
            Bounds region = pendingSnap.bounds(width, barY);
            int x = (int) region.x();
            int y = (int) region.y();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1);
            graphics.fill(x + 2, y + 2, x + region.width() - 2, y + region.height() - 2, 0x4256C7FF);
            graphics.renderOutline(x + 2, y + 2, region.width() - 4, region.height() - 4, ACCENT);
            graphics.pose().popPose();
        }
        graphics.disableScissor();
        backdrop.capture(graphics, panel != Panel.DESKTOP ? 14
                : jumpAnchor != null || desktopMenu != null ? 12 : previewAnchor != null ? 8 : 0);
        advanceLayer(graphics);
        renderTaskbar(graphics);
        graphics.enableScissor(0, barY, width, height);
        for (var widget : renderables) {
            widget.render(graphics, jumpAnchor == null && desktopMenu == null ? mouseX : -1,
                    jumpAnchor == null && desktopMenu == null ? mouseY : -1, partialTick);
        }
        graphics.disableScissor();
        renderHoverLabel(graphics, mouseX, mouseY, now);

        if (panel != Panel.DESKTOP) {
            advanceLayer(graphics);
            double offset = launcherOffset(Util.getMillis());
            boolean sliding = afterSlide != null || offset >= 1;
            graphics.enableScissor(0, 0, width, barY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, offset, 0);
            renderLauncher(graphics);
            boolean overMenu = overPowerMenu(mouseX, mouseY);
            for (var widget : launcherWidgets) {
                widget.render(graphics, overMenu || sliding ? -1 : mouseX, overMenu || sliding ? -1 : mouseY, partialTick);
            }
            if (powerOpen) {
                graphics.flush();
                graphics.pose().translate(0, 0, LAYER_STEP / 2);
                int right = powerButton.getX() + powerButton.getWidth();
                int top = powerButton.getY() - 68;
                roundRect(graphics, right - 107, top, 107, 65, BORDER);
                acrylicPanel(graphics, right - 106, top + 1, 105, 63, FLYOUT_TINT, false);
                for (var item : powerItems) {
                    item.render(graphics, sliding ? -1 : mouseX, sliding ? -1 : mouseY, partialTick);
                }
            }
            graphics.flush();
            graphics.pose().popPose();
            graphics.disableScissor();
        } else if (desktopMenu != null) {
            advanceLayer(graphics);
            boolean overSubmenu = desktopSubmenu != null && desktopSubmenu.bounds.contains(mouseX, mouseY);
            desktopMenu.render(graphics, overSubmenu ? -1 : mouseX, overSubmenu ? -1 : mouseY);
            if (desktopSubmenu != null) {
                graphics.flush();
                graphics.pose().translate(0, 0, LAYER_STEP / 2);
                desktopSubmenu.render(graphics, mouseX, mouseY);
            }
        } else if (jumpAnchor != null || previewAnchor != null) {
            advanceLayer(graphics);
            if (jumpAnchor != null) {
                var bounds = jumpBounds();
                renderFlyoutFrame(graphics, bounds, JUMP_LIST_TINT);
                for (var item : jumpItems) {
                    item.render(graphics, mouseX, mouseY, partialTick);
                }
            } else {
                renderPreview(graphics, mouseX, mouseY);
            }
        }
        graphics.flush();
        graphics.pose().popPose();
        if (screenshot.active()) {
            var bounds = new ArrayList<Rectangle>();
            for (var window : windows.frontToBack()) {
                if (!window.minimized) {
                    bounds.add(new Rectangle((int) windowX(window), (int) windowY(window),
                            windowWidth(window), windowHeight(window)).intersection(new Rectangle(0, 0, width, barY)));
                }
            }
            try {
                screenshot.freeze(width, height, bounds);
            } catch (RuntimeException e) {
                screenshot.close();
                UltimateBankingSystem.LOGGER.warn("Could not read the PC desktop framebuffer", e);
                notice(null, "Screenshot could not be captured.");
            }
            if (screenshot.ready()) renderScreenshotOverlay(graphics, mouseX, mouseY);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 150);
            long notificationTime = System.currentTimeMillis();
            if (openWith != null) {
                openWith.render(graphics, width, height, mouseX, mouseY);
            } else if (filePicker == null) {
                notificationCenter.render(graphics, width, barY, mouseX, mouseY, notificationTime, notificationQuiet(notificationTime));
            } else {
                filePicker.render(graphics, width, height, mouseX, mouseY, partialTick);
            }
            graphics.flush();
            graphics.pose().popPose();
        }
    }

    private void renderScreenshotOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 150);
        screenshot.render(graphics, mouseX, mouseY);
        graphics.flush();
        graphics.pose().popPose();
    }

    private void acrylicPanel(GuiGraphics graphics, int x, int y, int w, int h, int tint, boolean useFlyout) {
        if (backdrop.ready()) {
            backdrop.draw(graphics, x, y, w, h, true, useFlyout);
        }
        roundRect(graphics, x, y, w, h, backdrop.ready() ? tint : SURFACE);
    }

    private void renderFlyoutFrame(GuiGraphics graphics, Flyout bounds, int tint) {
        roundRect(graphics, bounds.x - 2, bounds.y + 2, bounds.w + 4, bounds.h + 1, 0x55000000);
        roundRect(graphics, bounds.x, bounds.y, bounds.w, bounds.h, BORDER);
        acrylicPanel(graphics, bounds.x + 1, bounds.y + 1, bounds.w - 2, bounds.h - 2, tint, true);
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        var window = windows.get(previewAnchor.taskApp);
        var bounds = previewBounds();
        renderFlyoutFrame(graphics, bounds, FLYOUT_TINT);
        drawAppIcon(graphics, window.app, bounds.x + 7, bounds.y + 6, 13);
        graphics.drawString(font, window.app.title, bounds.x + 24, bounds.y + 8, TEXT, false);
        var close = previewClose(bounds);
        if (close.contains(mouseX, mouseY)) {
            roundRect(graphics, close.x, close.y, close.w, close.h, 0xFFC42B38);
        }
        drawIcon(graphics, Icon.CLOSE, close.x + 4, close.y + 3, 12);

        int windowWidth = windowWidth(window);
        int windowHeight = windowHeight(window);
        float scale = Math.min((bounds.w - 14f) / windowWidth, (bounds.h - 28f) / windowHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x + (bounds.w - windowWidth * scale) / 2, bounds.y + 23, 1);
        graphics.pose().scale(scale, scale, 1);
        drawWindow(graphics, window, 0, 0, windowWidth, windowHeight, false, false, -1, -1, 0);
        if (window.content != null && windowWidth > 2 && windowHeight > TITLE_HEIGHT + 2) {
            float left = bounds.x + (bounds.w - windowWidth * scale) / 2;
            int top = bounds.y + 23;
            graphics.enableScissor((int) (left + scale), (int) (top + (TITLE_HEIGHT + 1) * scale),
                    (int) Math.ceil(left + (windowWidth - 1) * scale), (int) Math.ceil(top + (windowHeight - 1) * scale));
            try {
                visitApp(window, app -> app.renderPreview(graphics, 1, TITLE_HEIGHT + 1, windowWidth - 2,
                        windowHeight - TITLE_HEIGHT - 2));
            } finally {
                graphics.disableScissor();
            }
        }
        graphics.pose().popPose();
    }

    private static void advanceLayer(GuiGraphics graphics) {
        graphics.flush();
        graphics.pose().translate(0, 0, LAYER_STEP);
    }

    private void renderTaskbar(GuiGraphics graphics) {
        if (backdrop.ready()) {
            backdrop.draw(graphics, 0, barY, width, height - barY, false, false);
        }
        graphics.fill(0, barY, width, height, backdrop.ready() ? TASKBAR_TINT : SURFACE);
        graphics.fill(0, barY, width, barY + 1, BORDER);
        var now = LocalDateTime.now();
        Component time = labelText(now.format(CLOCK));
        Component date = labelText(now.format(DATE));
        int top = barY + (height - barY - 20) / 2;
        int right = width - SHOW_DESKTOP_WIDTH - 4;
        int bellX = notificationClockLeft() + 4;
        long millis = System.currentTimeMillis();
        if (notificationCenter.open()) roundRect(graphics, notificationClockLeft(), barY + 2,
                right - notificationClockLeft() + 2, height - barY - 4, FIELD);
        (notificationQuiet(millis) ? PcFilesIcon.BELL_OFF : PcFilesIcon.BELL)
                .draw(graphics, bellX, top + 4, 13, TEXT);
        if (windows.notifications.unread(null) > 0) graphics.fill(bellX + 10, top + 4, bellX + 14, top + 8, ACCENT);
        graphics.drawString(font, time, right - font.width(time), top, TEXT, false);
        graphics.drawString(font, date, right - font.width(date), top + 11, TEXT, false);
    }

    private final class ShowDesktopButton extends AbstractButton {
        ShowDesktopButton(int top, int height) {
            super(PcDesktopScreen.this.width - SHOW_DESKTOP_WIDTH, top, SHOW_DESKTOP_WIDTH, height, label("Show desktop"));
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        public void onPress() {
            closeDesktopMenu();
            dismissFlyouts();
            clearFocus();
            lastShortcutClick = null;
            windows.toggleShowDesktop();
            focusApp(windows.active());
            nextPanel = Panel.DESKTOP;
            applyPanelChange();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY() + 2, getX() + getWidth(), getY() + getHeight() - 2, 0x223D4964);
                int x = getX() + getWidth() / 2;
                int top = getY() + (getHeight() - 11) / 2;
                graphics.fill(x, top, x + 1, top + 11, isFocused() ? ACCENT : MUTED);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private void renderLauncher(GuiGraphics graphics) {
        roundRect(graphics, panelX - 2, panelY + 3, panelWidth + 4, panelHeight + 2, 0x40000000);
        roundRect(graphics, panelX, panelY, panelWidth, panelHeight, BORDER);
        acrylicPanel(graphics, panelX + 1, panelY + 1, panelWidth - 2, panelHeight - 2, LAUNCHER_TINT, true);

        roundRect(graphics, panelX + 9, panelY + 9, panelWidth - 18, 21, backdrop.ready() ? CONTROL_FIELD : FIELD);
        roundedStroke(graphics, panelX + 8, panelY + 8, panelWidth - 16, 23, search.isFocused() ? ACCENT : BORDER, false);
        drawIcon(graphics, Icon.SEARCH, panelX + 13, panelY + 13, 13);
        if (!search.getValue().isEmpty()) {
            renderSearchResults(graphics);
            return;
        }
        if (recentView()) {
            drawHeading(graphics, windows.recent().isEmpty() ? "Apps" : "Recent", panelX + 11, panelY + 39);
            return;
        }
        drawHeading(graphics, "Pinned", panelX + 11, panelY + 41);

        if (panel == Panel.START) {
            int footerY = panelY + panelHeight - 28;
            graphics.fill(panelX + 7, footerY, panelX + panelWidth - 7, footerY + 1, BORDER);
            if (minecraft.player != null) {
                PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin().texture(), panelX + 10, footerY + 7, 16);
                graphics.drawString(font, labelText(minecraft.player.getGameProfile().getName()),
                        panelX + 32, footerY + 11, TEXT, false);
            }
        }
    }

    private void renderSearchResults(GuiGraphics graphics) {
        if (selectedResult == null) {
            graphics.drawString(font, label("No matching results"), panelX + 11, panelY + 62, MUTED, false);
            return;
        }
        drawHeading(graphics, searchScroll == 0 ? "Best match" : "Results", panelX + 11, panelY + 55);
        int left = resultsSplit() + 2;
        int right = panelX + panelWidth - 8;
        int center = (left + right) / 2;
        roundRect(graphics, left, panelY + 55, right - left, panelHeight - 63, backdrop.ready() ? CONTROL_FIELD : FIELD);
        if (selectedResult.file != null) drawFileIcon(graphics, selectedResult.file, center - 10, panelY + 60, 20);
        else drawAppIcon(graphics, selectedResult.app, center - 10, panelY + 60, 20);
        var title = Language.getInstance().getVisualOrder(font.substrByWidth(label(selectedResult.title), Math.max(1, right - left - 8)));
        graphics.drawString(font, title, center - font.width(title) / 2, panelY + 84, TEXT, false);
        drawCentered(graphics, label(selectedResult.detail), center, panelY + 96, MUTED);
        if (matches.size() > searchRows.size()) {
            int track = Math.max(1, panelHeight - 78);
            int thumb = Math.max(7, track * searchRows.size() / matches.size());
            int at = panelY + 68 + (track - thumb) * searchScroll / (matches.size() - searchRows.size());
            graphics.fill(left - 5, at, left - 3, at + thumb, MUTED);
        }
    }

    private void drawHeading(GuiGraphics graphics, String text, int x, int y) {
        Component title = UbsTranslations.literal(text).withStyle(style -> style.withFont(BOLD_FONT));
        graphics.drawString(font, title, x, y, TEXT, false);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int center, int y, int color) {
        graphics.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    private void renderWindow(GuiGraphics graphics, Window<App> window, boolean active, boolean interactive,
                              int mouseX, int mouseY, float partialTick) {
        double left = windowX(window);
        double top = windowY(window);
        int x = (int) left;
        int y = (int) top;
        int w = windowWidth(window);
        int h = windowHeight(window);
        graphics.pose().pushPose();
        graphics.pose().translate(left - x, top - y, 0);
        drawWindow(graphics, window, x, y, w, h, active, interactive, mouseX, mouseY, partialTick);
        if (window.content != null && w > 2 && h > TITLE_HEIGHT + 2) {
            int topOfContent = y + TITLE_HEIGHT + 1;
            boolean hovered = interactive && panel == Panel.DESKTOP && windowAt(mouseX, mouseY) == window
                    && resizeEdgeAt(window, mouseX, mouseY) == null && mouseY >= topOfContent;
            graphics.enableScissor(x + 1, topOfContent, x + w - 1, y + h - 1);
            try {
                visitApp(window, app -> {
                    layoutApp(window, x + 1, topOfContent, w - 2, h - TITLE_HEIGHT - 2);
                    app.render(graphics, hovered ? mouseX : -1, hovered ? mouseY : -1, partialTick);
                });
            } finally {
                graphics.disableScissor();
            }
        }
        graphics.pose().popPose();
    }

    private void drawWindow(GuiGraphics graphics, Window<App> window, int x, int y, int w, int h,
                            boolean active, boolean interactive, int mouseX, int mouseY, float partialTick) {
        if (window.maximized) {
            graphics.fill(x, y, x + w, y + h, SURFACE);
        } else {
            roundRect(graphics, x, y, w, h, active ? BORDER : FIELD);
            roundRect(graphics, x + 1, y + 1, w - 2, h - 2, SURFACE);
        }
        if (window.app.equals(App.FILES)) {
            PcFilesIcon.FOLDER_OPEN.draw(graphics, x + 8, y + 6, 14, active ? TEXT : MUTED);
        } else {
            drawAppIcon(graphics, window.app, x + 8, y + 6, 14);
        }
        var title = font.substrByWidth(window.app.title, Math.max(0, w - captionWidth(window) - 30));
        graphics.drawString(font, Language.getInstance().getVisualOrder(title), x + 27, y + 9, active ? TEXT : MUTED, false);
        graphics.fill(x + 1, y + TITLE_HEIGHT, x + w - 1, y + TITLE_HEIGHT + 1, FIELD);
        if (window.content == null) {
            graphics.drawCenteredString(font, label("App unavailable"), x + w / 2, y + h / 2, MUTED);
        }
        if (active && interactive && panel == Panel.DESKTOP) {
            boolean overCaption = currentResizeCursor == null;
            for (var control : captionButtons) {
                control.render(graphics, overCaption ? mouseX : -1, overCaption ? mouseY : -1, partialTick);
            }
        } else {
            int firstX = x + w - captionWidth(window) + 5;
            drawCaptionIcon(graphics, window, Icon.MINIMIZE, firstX, y + 7);
            if (window.app.sizing.maximizable()) {
                drawCaptionIcon(graphics, window, window.maximized ? Icon.RESTORE : Icon.MAXIMIZE,
                        firstX + CAPTION_WIDTH, y + 7);
            }
            drawCaptionIcon(graphics, window, Icon.CLOSE, x + w - CAPTION_WIDTH + 5, y + 7);
        }
    }

    private static void drawCaptionIcon(GuiGraphics graphics, Window<App> window, Icon icon, int x, int y) {
        if (window != null && window.app.equals(App.FILES)) {
            var glyph = switch (icon) {
                case MINIMIZE -> PcFilesIcon.MINIMIZE;
                case MAXIMIZE -> PcFilesIcon.MAXIMIZE;
                case RESTORE -> PcFilesIcon.RESTORE_WINDOW;
                case CLOSE -> PcFilesIcon.CLOSE;
                default -> throw new IllegalArgumentException("Unsupported caption icon " + icon);
            };
            glyph.draw(graphics, x, y, 12, TEXT);
        } else {
            drawIcon(graphics, icon, x, y, 12);
        }
    }

    private PcButton button(int x, int y, int w, int h, String text, Icon icon, boolean showLabel, Runnable action) {
        return button(x, y, w, h, label(text), icon, showLabel, action);
    }

    private PcButton button(int x, int y, int w, int h, Component text, Icon icon, boolean showLabel, Runnable action) {
        return addRenderableWidget(new PcButton(x, y, w, h, text, icon, showLabel, action));
    }

    static void roundRect(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 3, y, x + w - 3, y + 1, color);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, color);
        graphics.fill(x, y + 3, x + w, y + h - 3, color);
        graphics.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, color);
        graphics.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
    }

    private static void capsule(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y + 5, x + w, y + h - 5, color);
        for (int row = 0; row < 5; row++) {
            int inset = roundedInset(row, true);
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            graphics.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    private static int roundedInset(int row, boolean capsule) {
        return capsule ? (row == 0 ? 7 : Math.max(0, 5 - row)) : (row == 0 ? 3 : row < 3 ? 1 : 0);
    }

    private static void roundedStroke(GuiGraphics graphics, int x, int y, int w, int h, int color, boolean capsule) {
        int edge = capsule ? 6 : 4;
        for (int row = 0; row < edge; row++) {
            int outer = roundedInset(row, capsule);
            if (row == 0) {
                graphics.fill(x + outer, y, x + w - outer, y + 1, color);
                graphics.fill(x + outer, y + h - 1, x + w - outer, y + h, color);
            } else {
                int inner = roundedInset(row - 1, capsule) + 1;
                graphics.fill(x + outer, y + row, x + inner, y + row + 1, color);
                graphics.fill(x + w - inner, y + row, x + w - outer, y + row + 1, color);
                graphics.fill(x + outer, y + h - row - 1, x + inner, y + h - row, color);
                graphics.fill(x + w - inner, y + h - row - 1, x + w - outer, y + h - row, color);
            }
        }
        graphics.fill(x, y + edge, x + 1, y + h - edge, color);
        graphics.fill(x + w - 1, y + edge, x + w, y + h - edge, color);
    }

    private static void drawIcon(GuiGraphics graphics, Icon icon, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ICONS, x, y, size, size, icon.ordinal() * 32f, 0, 32, 32, Icon.values().length * 32, 32);
        RenderSystem.disableBlend();
    }

    static void drawAppIcon(GuiGraphics graphics, App app, int x, int y, int size) {
        if (app.texture == null) {
            drawIcon(graphics, app.icon, x, y, size);
        } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(app.texture, x, y, size, size, 0f, 0f, 1, 1, 1, 1);
            RenderSystem.disableBlend();
        }
    }

    static void drawPowerIcon(GuiGraphics graphics, int x, int y, int size) {
        drawIcon(graphics, Icon.POWER, x, y, size);
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", path);
    }

    static Component label(String text) {
        return UbsTranslations.literal(text).withStyle(style -> style.withFont(FONT));
    }

    private static Component labelText(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(FONT));
    }

    private final class PcButton extends AbstractButton {
        private final Icon icon;
        private final boolean showLabel;
        private final Runnable action;
        private App taskApp;
        private App resultApp;
        private SearchResult searchResult;
        private SearchKind searchTab;
        private App appIcon;
        private PcFileSystem.Entry desktopEntry;
        private Cell shortcutCell;
        private ContextCommand contextCommand;
        private int shortcutSize = 22;

        private PcButton(int x, int y, int w, int h, Component title, Icon icon, boolean showLabel, Runnable action) {
            super(x, y, w, h, title);
            this.icon = icon;
            this.showLabel = showLabel;
            this.action = action;
            if (!showLabel) {
                setTooltip(Tooltip.create(title));
            }
        }

        @Override
        public void onPress() {
            action.run();
        }

        private Flyout labelBounds() {
            int textWidth = Math.min(width - 4, font.width(getMessage()));
            return new Flyout(getX() + (width - textWidth) / 2, getY() + shortcutSize + 4, textWidth, 11);
        }

        private boolean intersectsShortcut(Flyout box) {
            return overlaps(box, labelBounds())
                    || overlaps(box, new Flyout(getX() + (width - shortcutSize) / 2, getY() + 2, shortcutSize, shortcutSize));
        }

        private void renderShortcut(GuiGraphics graphics, int x, int y, boolean ghost) {
            if (ghost) {
                graphics.setColor(1, 1, 1, 0.65f);
            }
            drawFileIcon(graphics, desktopEntry, x + (width - shortcutSize) / 2, y + 2, shortcutSize);
            if (ghost) {
                graphics.setColor(1, 1, 1, 1);
            }
            if (this != editingShortcut) {
                String name = getMessage().getString();
                if (font.width(getMessage()) > width - 4) {
                    name = font.substrByWidth(getMessage(), width - 4 - font.width(labelText("…"))).getString() + "…";
                }
                drawCentered(graphics, labelText(name), x + width / 2, y + shortcutSize + 5, ghost ? 0xABF5F7FC : TEXT);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int centerX = x + width / 2;
            int centerY = y + height / 2;
            if (desktopEntry != null) {
                boolean selected = selectedShortcuts.contains(desktopEntry.id());
                if (selected || isHovered() || isFocused()) {
                    roundRect(graphics, x, y, width, height, selected ? 0x4276B9FA : 0x2BFFFFFF);
                    if (selected || isFocused()) {
                        roundedStroke(graphics, x, y, width, height, 0x76A5D8FF, false);
                    }
                }
                renderShortcut(graphics, x, y, false);
                return;
            }
            if (contextCommand != null) {
                if (isHoveredOrFocused() || contextCommand == submenuParent) {
                    roundRect(graphics, x, y + 1, width, height - 2, 0xFF353B48);
                }
                if (contextCommand.checked()) {
                    graphics.fill(x + 5, centerY, x + 7, centerY + 2, ACCENT);
                    graphics.fill(x + 7, centerY - 2, x + 9, centerY + 1, ACCENT);
                }
                if (!contextCommand.children().isEmpty()) {
                    int right = x + width - 8;
                    graphics.fill(right - 2, centerY - 2, right - 1, centerY - 1, TEXT);
                    graphics.fill(right - 1, centerY - 1, right, centerY, TEXT);
                    graphics.fill(right, centerY, right + 1, centerY + 1, TEXT);
                    graphics.fill(right - 1, centerY + 1, right, centerY + 2, TEXT);
                    graphics.fill(right - 2, centerY + 2, right - 1, centerY + 3, TEXT);
                }
                graphics.enableScissor(x + 16, y, x + width - 14, y + height);
                graphics.drawString(font, getMessage(), x + 16, centerY - 4, TEXT, false);
                graphics.disableScissor();
                return;
            }
            var foreground = windows.active();
            boolean selected = icon == Icon.START && panel == Panel.START
                    || icon == Icon.SEARCH && panel == Panel.SEARCH
                    || taskApp != null && foreground != null && taskApp == foreground.app;
            if (icon == Icon.SEARCH) {
                int fill = isHoveredOrFocused() || selected ? 0xFF3B455F : 0xFF30394F;
                capsule(graphics, x + 2, y + 2, width - 4, height - 4,
                        backdrop.ready() ? (fill & 0x00FFFFFF) | 0xEB000000 : fill);
                roundedStroke(graphics, x + 1, y + 1, width - 2, height - 2, isFocused() ? ACCENT : BORDER, true);
                drawIcon(graphics, icon, x + 7, centerY - 6, 12);
                graphics.drawString(font, getMessage(), x + 24, centerY - 4, TEXT, false);
                if (width >= 104) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    graphics.blit(SEARCH_ART, x + width - 37, centerY - 8, 32, 16,
                            0f, 0f, 64, 32, 64, 32);
                    RenderSystem.disableBlend();
                }
                return;
            }
            if (taskApp != null || icon == Icon.START) {
                if (isHoveredOrFocused() || selected || this == jumpAnchor || this == previewAnchor) {
                    int fill = isHoveredOrFocused() ? TASKBAR_HOVER : TASKBAR_SELECTED;
                    roundRect(graphics, x + 1, y, width - 2, height, fill);
                    if (isFocused()) {
                        roundedStroke(graphics, x + 1, y, width - 2, height, ACCENT, false);
                    }
                }
                if (taskApp != null) {
                    drawAppIcon(graphics, taskApp, centerX - TASKBAR_ICON_SIZE / 2, y + 1, TASKBAR_ICON_SIZE);
                    var notifications = windows.notifications;
                    if (!notifications.focusHideBadges || !notifications.focus(System.currentTimeMillis())) {
                        int count = notifications.unread(taskApp.id.toString());
                        if (count > 0) {
                            var badge = label(count > 9 ? "9+" : Integer.toString(count));
                            int badgeWidth = font.width(badge) + 4;
                            roundRect(graphics, x + getWidth() - badgeWidth - 1, y, badgeWidth, 11, ACCENT);
                            graphics.drawString(font, badge, x + getWidth() - badgeWidth + 1, y + 2, 0xFF162338, false);
                        }
                    }
                } else {
                    drawIcon(graphics, icon, centerX - TASKBAR_ICON_SIZE / 2, y + 1, TASKBAR_ICON_SIZE);
                }
                if (selected || taskApp != null && windows.isOpen(taskApp)) {
                    int halfWidth = selected ? 5 : 2;
                    graphics.fill(centerX - halfWidth, y + height - 2, centerX + halfWidth, y + height - 1,
                            selected ? ACCENT : MUTED);
                }
                return;
            }
            boolean resultSelected = searchResult != null && searchResult == selectedResult
                    || searchTab != null && searchTab == searchKind;
            if (active && (isHoveredOrFocused() || resultSelected)) {
                int hover = icon == Icon.CLOSE && isHovered() ? 0xFFC42B38 : FIELD;
                roundRect(graphics, x + 1, y, width - 2, height, isFocused() ? ACCENT : resultSelected ? 0xFF353B48 : hover);
                if (isFocused()) {
                    roundRect(graphics, x + 2, y + 1, width - 4, height - 2, FIELD);
                }
            }
            if (searchTab != null) {
                drawCentered(graphics, getMessage(), centerX, centerY - 4, TEXT);
            } else if (height > TITLE_HEIGHT) {
                drawButtonIcon(graphics, centerX - shortcutSize / 2, y + 2, shortcutSize);
                var clipped = font.substrByWidth(getMessage(), Math.max(0, width - 4));
                var title = Language.getInstance().getVisualOrder(clipped);
                graphics.drawString(font, title, centerX - font.width(title) / 2, y + shortcutSize + 5, TEXT, false);
            } else if (showLabel) {
                drawButtonIcon(graphics, x + 6, centerY - 6, 12);
                graphics.drawString(font, Language.getInstance().getVisualOrder(
                        font.substrByWidth(getMessage(), Math.max(0, width - 25))), x + 22, centerY - 4, TEXT, false);
            } else {
                int size = height == TITLE_HEIGHT - 2 ? 12 : 16;
                if (captionButtons.contains(this)) {
                    drawCaptionIcon(graphics, windows.active(), icon, centerX - 6, centerY - 6);
                } else {
                    drawIcon(graphics, icon, centerX - size / 2, centerY - size / 2, size);
                }
            }
            if (resultSelected) {
                graphics.fill(x + 1, y + 4, x + 2, y + height - 4, ACCENT);
            }
        }

        private void drawButtonIcon(GuiGraphics graphics, int x, int y, int size) {
            if (searchResult != null && searchResult.file != null) {
                drawFileIcon(graphics, searchResult.file, x, y, size);
                return;
            }
            App app = searchResult != null ? searchResult.app : resultApp != null ? resultApp : appIcon;
            if (app != null) {
                drawAppIcon(graphics, app, x, y, size);
            } else {
                drawIcon(graphics, icon == null ? Icon.OPEN : icon, x, y, size);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
