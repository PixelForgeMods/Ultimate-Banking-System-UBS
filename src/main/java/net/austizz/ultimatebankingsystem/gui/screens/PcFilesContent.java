package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppContent;
import net.austizz.ultimatebankingsystem.api.pc.PcAppServices;
import net.austizz.ultimatebankingsystem.api.pc.PcClipboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class PcFilesContent implements PcAppContent {
    private static final int TEXT = 0xFFF5F7FC;
    private static final int MUTED = 0xFFC2C7D3;
    private static final int ROW = 18;
    private static final int LIST_TOP = 82;
    private static final UUID BIN = new UUID(0, -1);
    private static final UUID HOME = new UUID(0, -2);
    private static final UUID STORAGE = new UUID(0, -3);
    private static final int ACCENT = 0xFFB2CCF3;
    private static final int RAIL = 0xFF20232A;
    private static final int PANEL = 0xFF181B20;
    private static final int CARD = 0xFF292E38;
    private static final int BAND = 0xFF22262E;
    private static final int RULE = 0xFF353B46;
    private static final int MENU_ROW = 19;
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "atm_regular");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yy")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DETAILS_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    private final PcFileSystem drive;
    private final EditBox field = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 15, Component.literal("Search or name"));
    private final EditBox address = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 13, Component.literal("Address"));
    private final LinkedHashSet<UUID> addressHistory = new LinkedHashSet<>();
    private List<String> addressSuggestions = List.of();
    private int addressChoice = -1;
    private ArrayDeque<UUID> back = new ArrayDeque<>();
    private ArrayDeque<UUID> forward = new ArrayDeque<>();
    private OpenHandler open;
    private FileIcon icon;
    private final LinkedHashSet<UUID> quickFolders;
    private final LinkedHashSet<UUID> startFolders;
    private final Map<UUID, Integer> folderViews;
    private final PcFilesPanes paneWidths;
    private PaneDrag paneDrag;
    private boolean paneWidthsChanged;
    private PcFileSystem.Entry inspectedEntry;
    private UUID inspectedFolder;
    private PcPhotoTexture inspectorThumbnail;
    private final Map<UUID, PcPhotoTexture> thumbnails = new HashMap<>();
    private int thumbnailEdge;
    private String imageDimensions;
    private int inspectorScroll;
    private int inspectorScrollLimit;
    private Runnable savePins;
    private UUID folder = HOME;
    private UUID selection;
    private PcClipboard clipboard = new PcClipboardState();
    private java.util.function.Consumer<PcFileSystem.Entry> chooseApp = ignored -> {};
    private String query = "";
    private String status = "";
    private Action prompt;
    private EditBox nameEditor;
    private UUID editingName;
    private List<PcFileSystem.Entry> entries = List.of();
    private List<FileRow> rows = List.of();
    private ItemClick lastClick;
    private long revision = -1;
    private boolean focused;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private int railScroll;
    private final List<Tab> tabs = new ArrayList<>(List.of(new Tab(HOME, "", new ArrayDeque<>(), new ArrayDeque<>(), 0, 0, false, false)));
    private final LinkedHashSet<UUID> recent = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> favorites = new LinkedHashSet<>();
    private boolean showFavorites;
    private int activeTab;
    private List<PcFileSystem.Entry> quick = List.of();
    private static final int QUICK_TOP = 83;
    private int quickPage;
    private boolean quickCollapsed;
    private long used;
    private int sortColumn;
    private boolean descending;
    private boolean details;
    private boolean preview;
    private boolean groupDates;
    private boolean compact;
    private boolean navigationPane = true;
    private boolean showExtensions = true;
    private int viewStep = PcFilesView.DETAILS.step;
    private double zoomRemainder;
    private long saveViewAt;
    private Popup popup;
    private enum Command {
        NEW("New", PcFilesIcon.NEW), CUT("", PcFilesIcon.CUT), COPY("", PcFilesIcon.COPY), PASTE("", PcFilesIcon.PASTE),
        RENAME("", PcFilesIcon.RENAME), DELETE("", PcFilesIcon.DELETE), SORT("Sort", PcFilesIcon.SORT),
        VIEW("View", PcFilesIcon.VIEW), MORE("", PcFilesIcon.MORE), DETAILS("Details", PcFilesIcon.DETAILS);

        final String label;
        final PcFilesIcon icon;
        Command(String label, PcFilesIcon icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private record MenuItem(PcFilesIcon icon, String label, Runnable action, boolean checked,
                            boolean enabled, boolean viewOption) {
        MenuItem(PcFilesIcon icon, String label, Runnable action, boolean checked, boolean enabled) {
            this(icon, label, action, checked, enabled, false);
        }
    }

    private static final class Popup {
        final int x;
        final int y;
        final int width;
        final List<MenuItem> items;
        final List<Command> actions;
        final int visible;
        int offset;
        int cursor;

        Popup(int x, int y, int width, List<MenuItem> items, List<Command> actions, int visible) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.items = items;
            this.actions = actions;
            this.visible = visible;
        }

        int rowsY() { return y + 4 + (actions.isEmpty() ? 0 : 23); }
        int bottom() { return rowsY() + visible * MENU_ROW + 4; }
    }

    private record Tab(UUID folder, String query, ArrayDeque<UUID> back, ArrayDeque<UUID> forward,
                       int scroll, int sortColumn, boolean descending, boolean grouped) {}

    private record Place(UUID id, String label, PcFilesIcon icon) {}
    private record FileRow(PcFileSystem.Entry entry, String heading) {}
    private record ItemClick(UUID id, long time, boolean onName) {}
    private record PaneDrag(PcFilesPanes.Divider divider, double startX, int startWidth) {}
    private record Cell(int x, int y, int width, int height) {}
    private record EntryLayout(int left, int top, int columns, int rows, int width, int height, boolean list) {
        int visible() { return rows * columns; }
        int stride() { return list ? Math.max(1, rows) : columns; }
        Cell cell(int index) {
            int col = list ? index / rows : index % columns;
            int row = list ? index % rows : index / columns;
            return new Cell(left + col * width, top + row * height, width, height);
        }
        int index(double x, double y) {
            if (x < left || x >= left + columns * width || y < top || y >= top + rows * height) return -1;
            int col = (int) (x - left) / width;
            int row = (int) (y - top) / height;
            return list ? col * rows + row : row * columns + col;
        }
    }
    private record Crumb(UUID id, String label, int width) {}

    @FunctionalInterface
    interface OpenHandler {
        void open(PcFileSystem.Entry entry) throws IOException;
    }

    @FunctionalInterface
    interface FileIcon {
        void render(GuiGraphics graphics, PcFileSystem.Entry entry, int x, int y, int size);
    }

    private enum Action {
        BACK, FORWARD, UP, FOLDER, FILE, RENAME, COPY, CUT, PASTE, DELETE, RESTORE, EMPTY, DETAILS, FAVORITE
    }

    private static final List<Action> NAV = List.of(Action.BACK, Action.FORWARD, Action.UP);
    private static final List<Command> COMMANDS = List.of(Command.NEW, Command.CUT, Command.COPY, Command.PASTE,
            Command.RENAME, Command.DELETE, Command.SORT, Command.VIEW, Command.MORE);

    PcFilesContent(PcFileSystem drive, OpenHandler open, FileIcon icon, LinkedHashSet<UUID> quickFolders,
                   LinkedHashSet<UUID> startFolders, Map<UUID, Integer> folderViews, PcFilesPanes paneWidths,
                   Runnable savePins) {
        this.drive = drive;
        this.open = open;
        this.icon = icon;
        this.quickFolders = quickFolders;
        this.startFolders = startFolders;
        this.folderViews = folderViews;
        this.paneWidths = paneWidths;
        this.savePins = savePins;
        field.setMaxLength(120);
        field.setBordered(false);
        field.setTextShadow(false);
        field.setFormatter((value, offset) -> uiText(value).getVisualOrderText());
        field.setHint(uiText("Search Home"));
        address.setBordered(false);
        address.setTextShadow(false);
        address.setMaxLength(32767);
    }

    void setHandlers(OpenHandler open, FileIcon icon, Runnable savePins) {
        this.open = open;
        this.icon = icon;
        this.savePins = savePins;
    }

    void openFolder(UUID folder) {
        if (nameEditor != null) finishNameEdit(false);
        navigate(folder);
    }

    void revealFile(PcFileSystem.Entry entry) {
        openFolder(entry.parent());
        if (!query.isBlank()) {
            query = "";
            field.setValue("");
            refresh();
        }
        selection = entry.id();
        reveal(selection);
    }

    @Override public void setServices(PcAppServices services) { clipboard = services.clipboard(); }

    void openWith(java.util.function.Consumer<PcFileSystem.Entry> chooseApp) { this.chooseApp = chooseApp; }

    void importFiles(List<Path> paths) {
        if (paths.isEmpty() || nameEditor != null && !finishNameEdit(true)) return;
        if (special()) {
            status = "Open a folder before dropping files.";
            return;
        }
        popup = null;
        endAddress();
        int imported = 0;
        String error = null;
        for (Path path : paths) {
            try {
                selection = drive.importFile(folder, path);
                imported++;
            } catch (IOException e) {
                if (error == null) error = message(e);
            }
        }
        refresh();
        if (imported > 0) reveal(selection);
        status = imported + (imported == 1 ? " file imported." : " files imported.");
        if (error != null) status += " " + error;
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        popup = null;
        clampScroll();
        int searchWidth = Math.clamp(width / 3, 72, 180);
        field.setPosition(x + width - searchWidth - 10, y + 28);
        field.setWidth(searchWidth - 12);
        address.setPosition(addressLeft() + 4, y + 28);
        address.setWidth(Math.max(1, addressRight() - addressLeft() - 22));
        quickPage = Math.clamp(quickPage, 0, quickPages() - 1);
        if (editingName != null) reveal(editingName);
    }

    private void refresh() {
        if (revision != drive.revision()) clearThumbnails();
        revision = drive.revision();
        try {
            if (!special()) {
                try {
                    drive.get(folder);
                } catch (IOException e) {
                    folder = PcFileSystem.ROOT;
                }
            }
            var pinned = new ArrayList<PcFileSystem.Entry>();
            for (UUID id : quickFolders) {
                try {
                    var entry = drive.get(id);
                    if (entry.folder()) pinned.add(entry);
                } catch (IOException ignored) {
                    // A restored folder keeps its pin.
                }
            }
            quick = pinned;
            quickPage = Math.clamp(quickPage, 0, quickPages() - 1);
            if (folder.equals(HOME) && query.isBlank()) {
                var source = showFavorites ? favorites : recent;
                var activity = new ArrayList<PcFileSystem.Entry>();
                for (UUID id : source.reversed()) {
                    try {
                        activity.add(drive.get(id));
                    } catch (IOException ignored) {
                        // Recycled items are hidden until restored.
                    }
                }
                entries = activity;
            } else if (folder.equals(STORAGE) && query.isBlank()) {
                used = drive.usedBytes();
                entries = List.of();
            } else {
                entries = folder.equals(BIN) ? drive.trash().stream().filter(entry -> entry.name()
                        .toLowerCase(java.util.Locale.ROOT).contains(query.strip().toLowerCase(java.util.Locale.ROOT))).toList()
                        : drive.list(special() ? PcFileSystem.ROOT : folder, query);
                var order = switch (sortColumn) {
                    case 1 -> Comparator.comparingLong(PcFileSystem.Entry::modified);
                    case 2 -> Comparator.comparing(PcFilesContent::itemType, String.CASE_INSENSITIVE_ORDER);
                    case 3 -> Comparator.comparingInt(PcFilesContent::bytes);
                    default -> Comparator.comparing(PcFileSystem.Entry::name, String.CASE_INSENSITIVE_ORDER);
                };
                if (descending) order = order.reversed();
                if (sortColumn == 0 && !folder.equals(BIN)) {
                    order = Comparator.comparing(PcFileSystem.Entry::folder).reversed().thenComparing(order);
                }
                entries = entries.stream().sorted(order).toList();
            }
            if (entries.stream().noneMatch(entry -> entry.id().equals(selection))) {
                selection = null;
            }
            rebuildRows();
            clampScroll();
        } catch (IOException e) {
            entries = List.of();
            rows = List.of();
            status = message(e);
        }
    }

    private void clampScroll() {
        var layout = entryLayout();
        int stride = layout.stride();
        int maxScroll = Math.max(0, Math.ceilDiv(rows.size(), stride) * stride - layout.visible());
        scroll = layout.visible() == 0 ? 0 : Math.clamp(scroll / stride * stride, 0, maxScroll);
    }

    private void rebuildRows() {
        var result = new ArrayList<FileRow>();
        boolean grouping = !special() && groupDates && sortColumn == 1 && query.isBlank()
                && (view() == PcFilesView.DETAILS || view() == PcFilesView.CONTENT);
        LocalDate today = LocalDate.now();
        String previous = null;
        for (var entry : entries) {
            if (grouping) {
                String group = dateGroup(entry.modified(), today);
                if (!group.equals(previous)) {
                    result.add(new FileRow(null, group));
                    previous = group;
                }
            }
            result.add(new FileRow(entry, null));
        }
        rows = result;
    }

    private static String dateGroup(long modified, LocalDate today) {
        if (modified == 0) return "No date";
        var date = Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDate();
        if (date.equals(today)) return "Today";
        if (date.equals(today.minusDays(1))) return "Yesterday";
        if (date.isAfter(today.minusDays(7)) && date.isBefore(today)) return "Past week";
        if (date.getYear() == today.getYear() && date.getMonth() == today.getMonth()) return "This month";
        return "Other dates";
    }

    private boolean special() {
        return folder.equals(BIN) || folder.equals(HOME) || folder.equals(STORAGE);
    }

    private List<Place> places() {
        var result = new ArrayList<Place>();
        result.add(new Place(HOME, "Home", PcFilesIcon.HOME));
        result.add(new Place(STORAGE, "Storage", PcFilesIcon.DRIVE));
        result.add(new Place(BIN, "Trash", PcFilesIcon.DELETE));
        for (var entry : quick) {
            result.add(new Place(entry.id(), entry.name(), folderIcon(entry)));
        }
        return result;
    }

    private String name(UUID id) {
        if (id.equals(HOME)) return "Home";
        if (id.equals(STORAGE)) return "Storage";
        if (id.equals(BIN)) return "Recycle Bin";
        try {
            return drive.get(id).name();
        } catch (IOException e) {
            return "PC Drive";
        }
    }

    private void switchTab(int index) {
        tabs.set(activeTab, new Tab(folder, query, back, forward, scroll, sortColumn, descending, groupDates));
        activeTab = index;
        var tab = tabs.get(index);
        back = tab.back;
        forward = tab.forward;
        resetFolder(tab.folder);
        sortColumn = tab.sortColumn;
        descending = tab.descending;
        groupDates = tab.grouped;
        query = tab.query;
        field.setValue(query);
        scroll = tab.scroll;
        refresh();
    }

    private void newTab() {
        if (tabs.size() < 8) {
            tabs.add(new Tab(HOME, "", new ArrayDeque<>(), new ArrayDeque<>(), 0, 0, false, false));
            switchTab(tabs.size() - 1);
        }
    }

    private void closeTab() {
        if (tabs.size() > 1) {
            int old = activeTab;
            switchTab(old == 0 ? 1 : old - 1);
            tabs.remove(old);
            if (old == 0) activeTab--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (saveViewAt != 0 && net.minecraft.Util.getMillis() >= saveViewAt) saveView();
        if (revision != drive.revision()) {
            refresh();
        }
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 42, BAND);
        graphics.hLine(x, x + width - 1, y + 42, RULE);
        graphics.hLine(x, x + width - 1, y + 62, RULE);
        renderTabs(graphics, mouseX, mouseY);
        drawNavigation(graphics, mouseX, mouseY);
        drawCommands(graphics, mouseX, mouseY);
        renderAddress(graphics, mouseX, mouseY, partialTick);
        graphics.fill(field.getX() - 5, y + 24, x + width - 5, y + 41, CARD);
        if (field.isFocused()) graphics.hLine(field.getX() - 5, x + width - 6, y + 40, ACCENT);
        PcFilesIcon.SEARCH.draw(graphics, x + width - 19, y + 27, 10, MUTED);
        field.render(graphics, mouseX, mouseY, partialTick);
        int bodyBottom = y + height - 14;
        if (bodyBottom > y + 62) {
            if (railWidth() > 0) {
                graphics.enableScissor(x, y + 62, x + railWidth(), bodyBottom);
                renderRail(graphics, mouseX, mouseY);
                graphics.disableScissor();
            }
            if (paneWidth() > 0) {
                graphics.enableScissor(paneX() - 2, y + 62, inspectorX(), bodyBottom);
                if (folder.equals(STORAGE) && query.isBlank()) drawStorage(graphics);
                else drawEntries(graphics, mouseX, mouseY, partialTick);
                graphics.disableScissor();
            }
            if (inspectorVisible()) renderInspector(graphics);
            renderDividers(graphics, mouseX, mouseY);
        }
        graphics.fill(x, y + height - 14, x + width, y + height, BAND);
        String itemCount = entries.size() == 1 ? "1 item" : entries.size() + " items";
        text(graphics, status.isEmpty() ? itemCount : status, x + 8, y + height - 11, width - 16, MUTED);
        renderPopup(graphics, mouseX, mouseY);
        renderAddressSuggestions(graphics, mouseX, mouseY);
    }

    private int railWidth() {
        return panes().navigation();
    }

    private int paneX() {
        return x + railWidth() + 9;
    }

    private int paneWidth() {
        return Math.max(0, panes().content() - 17);
    }

    private PcFilesPanes.Layout panes() {
        return paneWidths.layout(width, navigationPane, inspectorVisible());
    }

    private boolean inspectorVisible() { return details || preview; }

    private int inspectorX() { return x + width - panes().inspector(); }

    private PcFilesPanes.Divider dividerAt(double mx, double my) {
        if (my < y + 62 || my >= y + height - 14) return null;
        if (railWidth() > 0 && Math.abs(mx - x - railWidth()) <= 3) return PcFilesPanes.Divider.NAVIGATION;
        if (panes().inspector() > 0 && Math.abs(mx - inspectorX()) <= 3) return PcFilesPanes.Divider.INSPECTOR;
        return null;
    }

    boolean overDivider(double mx, double my) {
        return popup == null && prompt == null && !address.isFocused() && dividerAt(mx, my) != null;
    }

    boolean resizingPane() { return paneDrag != null; }

    private void renderDividers(GuiGraphics graphics, int mouseX, int mouseY) {
        var active = paneDrag != null ? paneDrag.divider : overDivider(mouseX, mouseY) ? dividerAt(mouseX, mouseY) : null;
        if (railWidth() > 0) {
            int split = x + railWidth();
            graphics.fill(split, y + 62, split + 1, y + height - 14,
                    active == PcFilesPanes.Divider.NAVIGATION ? ACCENT : RULE);
        }
        if (panes().inspector() > 0) {
            int split = inspectorX();
            graphics.fill(split, y + 62, split + 1, y + height - 14,
                    active == PcFilesPanes.Divider.INSPECTOR ? ACCENT : RULE);
        }
    }

    private int tabWidth() {
        return Math.min(95, Math.max(18, (width - 35) / tabs.size()));
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = x + 6;
        int step = tabWidth();
        for (int i = 0; i < tabs.size(); i++) {
            boolean active = i == activeTab;
            graphics.fill(left, y + 4, left + step - 3, y + 20, active ? CARD : BAND);
            if (active) graphics.hLine(left + 4, left + step - 7, y + 20, ACCENT);
            UUID destination = active ? folder : tabs.get(i).folder;
            int inset = step >= 47 ? 19 : 5;
            if (inset == 19) {
                var tabIcon = destination.equals(HOME) ? PcFilesIcon.HOME
                        : destination.equals(STORAGE) ? PcFilesIcon.DRIVE
                        : destination.equals(BIN) ? PcFilesIcon.DELETE : PcFilesIcon.FOLDER;
                tabIcon.draw(graphics, left + 5, y + 7, 10, active ? ACCENT : MUTED);
            }
            text(graphics, name(destination), left + inset, y + 8, step - inset - 15,
                    active ? TEXT : MUTED);
            if (active && tabs.size() > 1) {
                PcFilesIcon.CLOSE.draw(graphics, left + step - 13, y + 7, 9, TEXT);
            }
            left += step;
        }
        iconButton(graphics, PcFilesIcon.ADD, left + 1, y + 4, 21, 16, mouseX, mouseY);
    }

    private void renderRail(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(x, y + 62, x + railWidth(), y + height - 14, RAIL);
        text(graphics, "PLACES", x + 8, y + 68, railWidth() - 12, ACCENT);
        int top = y + 82;
        int rowHeight = railRowHeight();
        var places = places();
        railScroll = Math.clamp(railScroll, 0, Math.max(0, places.size() - railRows()));
        for (int index = railScroll; index < Math.min(places.size(), railScroll + railRows()); index++) {
            var place = places.get(index);
            boolean hover = mouseX >= x && mouseX < x + railWidth() && mouseY >= top && mouseY < top + rowHeight;
            if (hover || place.id.equals(folder)) {
                graphics.fill(x + 4, top, x + railWidth() - 3, top + rowHeight, CARD);
            }
            place.icon.draw(graphics, x + 7, top + (rowHeight - 11) / 2, 11,
                    place.id.equals(folder) ? ACCENT : MUTED);
            text(graphics, place.label, x + 22, top + 3, railWidth() - 25, TEXT);
            top += rowHeight;
        }
    }

    private int railRows() {
        return Math.max(0, (height - 96) / railRowHeight());
    }

    private int railRowHeight() {
        return compact ? 13 : 15;
    }

    private boolean homeCards() {
        return folder.equals(HOME) && query.isBlank();
    }

    private int cardColumns() {
        return Math.clamp(paneWidth() / 108, 1, 5);
    }

    private int quickPageSize() {
        return cardColumns() * Math.max(1, Math.min(4, (height - 137) / quickRowHeight()));
    }

    private int quickRowHeight() {
        return height < 150 ? 26 : 39;
    }

    private int quickPages() {
        return Math.max(1, (quick.size() + quickPageSize() - 1) / quickPageSize());
    }

    private int quickCount() {
        return quickCollapsed ? 0 : Math.min(quickPageSize(), Math.max(0, quick.size() - quickPage * quickPageSize()));
    }

    private int activityTop() {
        return QUICK_TOP + (quickCount() + cardColumns() - 1) / cardColumns() * quickRowHeight() + 4;
    }

    private int listTop() {
        return homeCards() ? activityTop() + 22 : view() == PcFilesView.DETAILS ? LIST_TOP : 67;
    }

    private void drawEntries(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = paneX();
        int w = paneWidth();
        if (homeCards()) {
            (quickCollapsed ? PcFilesIcon.RIGHT : PcFilesIcon.DOWN).draw(graphics, left, y + 67, 10, ACCENT);
            boolean paged = !quickCollapsed && quickPages() > 1;
            text(graphics, "Quick access", left + 12, y + 68, w - (paged ? 67 : 12), ACCENT);
            if (paged) {
                iconButton(graphics, PcFilesIcon.LEFT, left + w - 49, y + 65, 16, 15, mouseX, mouseY);
                text(graphics, (quickPage + 1) + "/" + quickPages(), left + w - 30, y + 69, 19, MUTED);
                iconButton(graphics, PcFilesIcon.RIGHT, left + w - 13, y + 65, 15, 15, mouseX, mouseY);
            }
            int step = w / cardColumns();
            for (int i = 0; i < quickCount(); i++) {
                int cx = left + i % cardColumns() * step;
                int cy = y + QUICK_TOP + i / cardColumns() * quickRowHeight();
                var entry = quick.get(quickPage * quickPageSize() + i);
                if (mouseX >= cx && mouseX < cx + step - 4 && mouseY >= cy && mouseY < cy + quickRowHeight() - 3) {
                    graphics.fill(cx, cy, cx + step - 4, cy + quickRowHeight() - 3, CARD);
                }
                int iconSize = quickRowHeight() == 26 ? 19 : 27;
                int textX = cx + iconSize + 11;
                int textWidth = step - iconSize - 18;
                folderIcon(entry).draw(graphics, cx + 5, cy + 4, iconSize, ACCENT);
                filename(graphics, entry.name(), textX, cy + 5, textWidth, TEXT);
                String location = PcFileSystem.ROOT.equals(entry.parent()) ? "Stored locally" : "In " + name(entry.parent());
                filename(graphics, location, textX, cy + 17, textWidth, MUTED);
                PcFilesIcon.PIN.draw(graphics, quickRowHeight() == 26 ? cx + 2 : textX + 1,
                        cy + quickRowHeight() - 10, 8, MUTED);
            }
            int recentWidth = activityWidth("Recent");
            activityButton(graphics, PcFilesIcon.RECENT, "Recent", left, recentWidth, mouseX, mouseY, !showFavorites);
            activityButton(graphics, PcFilesIcon.STAR, "Favorites", left + recentWidth + 4,
                    activityWidth("Favorites"), mouseX, mouseY, showFavorites);
        } else if (view() == PcFilesView.DETAILS) {
            String[] names = {"Name", "Modified", "Type", "Size"};
            for (int col = 0; col < columnCount(); col++) {
                int titleX = left + column(col);
                int room = column(col + 1) - column(col) - 4;
                text(graphics, names[col], titleX, y + 68, room, ACCENT);
                if (sortColumn == col) {
                    int end = Minecraft.getInstance().font.width(uiText(names[col])) + 3;
                    if (end + 8 <= room) {
                        (descending ? PcFilesIcon.DOWN : PcFilesIcon.UP).draw(graphics, titleX + end, y + 68, 8, ACCENT);
                    }
                }
            }
        }
        var grid = entryLayout();
        var mode = view();
        int iconSize = entryIconSize();
        int end = Math.min(rows.size(), scroll + grid.visible());
        prepareThumbnails(iconSize, end);
        int remainingLoads = 2;
        for (int n = scroll; n < end; n++) {
            var row = rows.get(n);
            var entry = row.entry;
            var cell = grid.cell(n - scroll);
            int top = cell.y;
            if (entry == null) {
                text(graphics, row.heading, left + 5, top + 5, w - 10, ACCENT);
                continue;
            }
            if (entry.id().equals(selection)) {
                graphics.fill(cell.x, top, cell.x + cell.width, top + cell.height - 1, 0xFF343D4C);
                graphics.fill(cell.x, top, cell.x + 2, top + cell.height - 1, ACCENT);
            } else if (mouseX >= cell.x && mouseX < cell.x + cell.width && mouseY >= top && mouseY < top + cell.height) {
                graphics.fill(cell.x, top, cell.x + cell.width, top + cell.height - 1, CARD);
            }
            int iconX = stackedIcons() ? cell.x + (cell.width - iconSize) / 2 : cell.x + 5;
            int iconY = stackedIcons() ? top + 5 : top + (cell.height - iconSize) / 2;
            if (entry.folder()) {
                folderIcon(entry).draw(graphics, iconX, iconY, iconSize, ACCENT);
            } else {
                PcPhotoTexture thumbnail = null;
                if (thumbnailEdge > 0 && PcPhotosContent.canOpen(entry)) {
                    if (remainingLoads > 0 && !thumbnails.containsKey(entry.id())) {
                        loadThumbnail(entry);
                        remainingLoads--;
                    }
                    thumbnail = thumbnails.get(entry.id());
                }
                if (thumbnail != null) {
                    thumbnail.drawFitted(graphics, iconX, iconY, iconSize, iconSize);
                } else {
                    icon.render(graphics, entry, iconX, iconY, iconSize);
                }
            }
            var label = labelBounds(cell, entry);
            if (entry.id().equals(editingName) && nameEditor != null) {
                renderNameEditor(graphics, cell, label, mouseX, mouseY, partialTick);
            } else {
                filename(graphics, displayName(entry), label.x, label.y, label.width, TEXT);
            }
            if (!homeCards() && (mode == PcFilesView.TILES || mode == PcFilesView.CONTENT)) {
                text(graphics, itemType(entry), label.x, top + 17, label.width, MUTED);
                String size = entry.folder() || entry.shortcut() ? "" : size(bytes(entry));
                String modified = entry.modified() == 0 ? "" : DATE.format(Instant.ofEpochMilli(entry.modified()));
                String metadata = mode == PcFilesView.CONTENT && !modified.isEmpty()
                        ? modified + (size.isEmpty() ? "" : "  ·  " + size) : size;
                text(graphics, metadata, label.x, top + 28, label.width, MUTED);
                if (mode == PcFilesView.CONTENT) graphics.hLine(cell.x, cell.x + cell.width - 1, top + cell.height - 1, RULE);
            } else if (!homeCards() && mode == PcFilesView.DETAILS) {
                String[] values = {entry.name(), entry.modified() == 0 ? "" : DATE.format(Instant.ofEpochMilli(entry.modified())),
                        itemType(entry), entry.folder() || entry.shortcut() ? "" : size(bytes(entry))};
                for (int col = 1; col < columnCount(); col++) {
                    text(graphics, values[col], left + column(col), top + 5, column(col + 1) - column(col) - 3, MUTED);
                }
            }
        }
        if (grid.list && grid.visible() > 0 && rows.size() > grid.visible()) {
            int total = Math.ceilDiv(rows.size(), grid.rows);
            int thumb = Math.max(8, w * grid.columns / total);
            int thumbX = left + (w - thumb) * (scroll / grid.rows) / (total - grid.columns);
            graphics.fill(left, y + height - 19, left + w, y + height - 16, BAND);
            graphics.fill(thumbX, y + height - 19, thumbX + thumb, y + height - 16, MUTED);
        }
        if (entries.isEmpty()) {
            text(graphics, homeCards() ? (showFavorites ? "Star a file or folder to see it here." : "Opened files appear here.")
                    : query.isEmpty() ? "This folder is empty." : "No matches", left, y + listTop() + 5, w, MUTED);
        }
    }

    private void prepareThumbnails(int iconSize, int end) {
        int edge = homeCards() || viewStep <= PcFilesView.SMALL.step ? 0
                : Math.clamp(Integer.highestOneBit(iconSize * 4 - 1), 32, 256);
        if (edge != thumbnailEdge) {
            clearThumbnails();
            thumbnailEdge = edge;
        }
        if (thumbnails.isEmpty()) return;

        var visible = new HashSet<UUID>();
        for (int n = scroll; n < end; n++) {
            var entry = rows.get(n).entry;
            if (entry != null) visible.add(entry.id());
        }
        thumbnails.entrySet().removeIf(cached -> {
            if (visible.contains(cached.getKey())) return false;
            if (cached.getValue() != null) cached.getValue().close();
            return true;
        });
    }

    private void loadThumbnail(PcFileSystem.Entry entry) {
        PcPhotoTexture thumbnail = null;
        try {
            thumbnail = PcPhotoTexture.upload(PcPhotoImage.decode(entry.payload(), thumbnailEdge).pixels());
        } catch (IOException ignored) {
            // Keep the file icon and avoid re-decoding bad data every frame.
        }
        thumbnails.put(entry.id(), thumbnail);
    }

    private void clearThumbnails() {
        for (var thumbnail : thumbnails.values()) {
            if (thumbnail != null) thumbnail.close();
        }
        thumbnails.clear();
    }

    private void renderInspector(GuiGraphics graphics) {
        int left = inspectorX();
        int right = x + width;
        int top = y + 62;
        int bottom = y + height - 14;
        int room = right - left - 16;
        if (room <= 0) return;

        var entry = selection == null ? null : entries.stream()
                .filter(candidate -> candidate.id().equals(selection)).findFirst().orElse(null);
        if (!Objects.equals(inspectedFolder, folder) || entry != inspectedEntry) {
            resetInspector();
            inspectedFolder = folder;
            inspectedEntry = entry;
            if (entry != null && PcPhotosContent.canOpen(entry)) {
                try {
                    var image = PcPhotoImage.decode(entry.payload(), 256);
                    inspectorThumbnail = PcPhotoTexture.upload(image.pixels());
                    imageDimensions = image.sourceWidth() + " × " + image.sourceHeight();
                } catch (IOException ignored) {
                    // A damaged image still has a file icon and filesystem details.
                }
            }
        }

        int viewport = bottom - top;
        graphics.fill(left, top, right, bottom, RAIL);
        graphics.enableScissor(left + 1, top, right, bottom);
        try {
            int cursor = top - inspectorScroll;
            int hero = Math.clamp(viewport / 4, 30, 108);
            graphics.fill(left, cursor, right, cursor + hero, BAND);
            drawInspectorHero(graphics, entry, left, cursor, right - left, hero);
            cursor += hero + 10;
            int textX = left + 8;
            if (entry == null) {
                String count = entries.size() == 1 ? "1 item" : entries.size() + " items";
                cursor = inspectorText(graphics, name(folder) + " (" + count + ")", textX, cursor, room, TEXT);
                cursor = inspectorText(graphics, "Select an item to see its details.", textX, cursor + 6, room, MUTED);
            } else {
                cursor = inspectorText(graphics, entry.name(), textX, cursor, room, TEXT);
                if (preview && !entry.folder() && !entry.shortcut() && !entry.binary()) {
                    String content = entry.text();
                    String snippet = content.isEmpty() ? "This file is empty."
                            : content.length() > 640 ? content.substring(0, 640) + "…" : content;
                    cursor = inspectorText(graphics, snippet, textX, cursor + 6, room, MUTED);
                } else {
                    cursor = inspectorProperty(graphics, "Type", itemType(entry), textX, cursor + 6, room);
                    if (!entry.folder() && !entry.shortcut()) {
                        cursor = inspectorProperty(graphics, "Size", size(bytes(entry)), textX, cursor, room);
                    }
                    cursor = inspectorProperty(graphics, "Location", inspectorLocation(entry), textX, cursor, room);
                    if (entry.modified() != 0) {
                        cursor = inspectorProperty(graphics, "Modified",
                                DETAILS_DATE.format(Instant.ofEpochMilli(entry.modified())), textX, cursor, room);
                    }
                    if (imageDimensions != null) {
                        cursor = inspectorProperty(graphics, "Dimensions", imageDimensions, textX, cursor, room);
                    }
                }
            }
            inspectorScrollLimit = Math.max(0, cursor + inspectorScroll + 8 - bottom);
            inspectorScroll = Math.min(inspectorScroll, inspectorScrollLimit);
        } finally {
            graphics.disableScissor();
        }
        if (inspectorScrollLimit > 0) {
            int thumb = Math.min(viewport, Math.max(10, viewport * viewport / (viewport + inspectorScrollLimit)));
            int thumbY = top + (viewport - thumb) * inspectorScroll / inspectorScrollLimit;
            graphics.fill(right - 3, thumbY, right - 1, thumbY + thumb, MUTED);
        }
    }

    private void drawInspectorHero(GuiGraphics graphics, PcFileSystem.Entry entry, int left, int top, int width, int height) {
        if (inspectorThumbnail != null) {
            inspectorThumbnail.drawFitted(graphics, left + 6, top + 4, width - 12, height - 8);
            return;
        }
        int size = Math.min(48, Math.min(height, width) - 8);
        int iconX = left + (width - size) / 2;
        int iconY = top + (height - size) / 2;
        if (entry != null) {
            icon.render(graphics, entry, iconX, iconY, size);
        } else {
            var locationIcon = folder.equals(HOME) ? PcFilesIcon.HOME : folder.equals(BIN) ? PcFilesIcon.DELETE
                    : folder.equals(STORAGE) ? PcFilesIcon.DRIVE : PcFilesIcon.FOLDER;
            locationIcon.draw(graphics, iconX, iconY, size, ACCENT);
        }
    }

    private String inspectorLocation(PcFileSystem.Entry entry) {
        if (entry.trashed()) return name(BIN);
        try {
            return drive.path(entry.parent());
        } catch (IOException e) {
            return name(entry.parent());
        }
    }

    private int inspectorProperty(GuiGraphics graphics, String label, String value, int x, int y, int width) {
        var font = Minecraft.getInstance().font;
        int labelWidth = font.width(uiText(label));
        int valueWidth = font.width(uiText(value));
        if (labelWidth + 8 + valueWidth <= width) {
            text(graphics, label, x, y, labelWidth, MUTED);
            text(graphics, value, x + width - valueWidth, y, valueWidth, TEXT);
            return y + 13;
        }
        return inspectorText(graphics, value, x, inspectorText(graphics, label, x, y, width, MUTED), width, TEXT) + 3;
    }

    private int inspectorText(GuiGraphics graphics, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        for (var line : font.split(uiText(value), width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void resetInspector() {
        if (inspectorThumbnail != null) {
            inspectorThumbnail.close();
            inspectorThumbnail = null;
        }
        inspectedEntry = null;
        inspectedFolder = null;
        imageDimensions = null;
        inspectorScroll = 0;
        inspectorScrollLimit = 0;
    }

    private Cell labelBounds(Cell cell, PcFileSystem.Entry entry) {
        int iconSize = entryIconSize();
        int labelX = cell.x + iconSize + 7;
        int labelY = cell.y + (cell.height - 9) / 2;
        int room = (!homeCards() && view() == PcFilesView.DETAILS ? column(1) : cell.width) - iconSize - 9;
        if (stackedIcons()) {
            room = cell.width - 8;
            int nameWidth = Minecraft.getInstance().font.width(uiText(displayName(entry)));
            labelX = cell.x + (cell.width - Math.min(room, nameWidth)) / 2;
            labelY = cell.y + iconSize + 9;
        } else if (view() == PcFilesView.TILES || view() == PcFilesView.CONTENT) {
            labelY = cell.y + 5;
        }
        return new Cell(labelX, labelY, Math.max(0, room), 9);
    }

    private String displayName(PcFileSystem.Entry entry) {
        String name = entry.name();
        int dot = name.lastIndexOf('.');
        return !showExtensions && !entry.folder() && !entry.shortcut() && dot > 0 ? name.substring(0, dot) : name;
    }

    private static PcFilesIcon folderIcon(PcFileSystem.Entry entry) {
        if (!PcFileSystem.ROOT.equals(entry.parent())) return PcFilesIcon.FOLDER;
        return switch (entry.name()) {
            case "Desktop" -> PcFilesIcon.DESKTOP;
            case "Downloads" -> PcFilesIcon.DOWNLOADS;
            case "Documents" -> PcFilesIcon.DOCUMENTS;
            case "Pictures" -> PcFilesIcon.PICTURES;
            case "Music" -> PcFilesIcon.MUSIC;
            case "Videos" -> PcFilesIcon.VIDEOS;
            default -> PcFilesIcon.FOLDER;
        };
    }

    private void renderNameEditor(GuiGraphics graphics, Cell cell, Cell label,
                                  int mouseX, int mouseY, float partialTick) {
        int available = Math.max(4, label.width);
        int textWidth = Minecraft.getInstance().font.width(nameEditor.getValue()) + 12;
        int editorWidth = Math.clamp(textWidth, Math.min(58, available), available);
        int left = stackedIcons() ? cell.x + (cell.width - editorWidth) / 2 : label.x - 1;
        graphics.renderOutline(cell.x, cell.y, cell.width, cell.height - 1, ACCENT);
        nameEditor.setPosition(left, label.y - 3);
        nameEditor.setWidth(editorWidth);
        nameEditor.render(graphics, mouseX, mouseY, partialTick);
    }

    private int columnCount() {
        return paneWidth() >= 240 ? 4 : paneWidth() >= 155 ? 2 : 1;
    }

    private int column(int index) {
        int w = Math.min(paneWidth(), 550);
        return switch (index) {
            case 0 -> 4;
            case 1 -> columnCount() == 1 ? w : w * 53 / 100;
            case 2 -> columnCount() == 2 ? w : w * 72 / 100;
            case 3 -> w * 90 / 100;
            default -> w;
        };
    }

    private static int bytes(PcFileSystem.Entry entry) {
        return entry.size();
    }

    private static String itemType(PcFileSystem.Entry entry) {
        return entry.folder() ? "Folder" : entry.shortcut() ? "Shortcut" : PcFileType.label(entry.name());
    }

    private static String size(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
        if (bytes >= 1024) return String.format(java.util.Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return bytes + " B";
    }

    private void drawStorage(GuiGraphics graphics) {
        int left = paneX();
        int w = paneWidth();
        text(graphics, "Local storage", left, y + 69, w, ACCENT);
        graphics.fill(left, y + 85, left + w, y + 147, CARD);
        PcFilesIcon.DRIVE.draw(graphics, left + 8, y + 95, 14, ACCENT);
        text(graphics, "PC Drive", left + 27, y + 97, w - 35, TEXT);
        int meter = Math.max(0, w - 37);
        graphics.fill(left + 27, y + 112, left + 27 + meter, y + 117, 0xFF405753);
        int occupied = (int) (meter * Math.min(used, PcFileSystem.STORAGE_LIMIT) / PcFileSystem.STORAGE_LIMIT);
        graphics.fill(left + 27, y + 112, left + 27 + occupied, y + 117, ACCENT);
        text(graphics, size(used) + " used / 16 MiB  •  Open drive", left + 27, y + 125, w - 35, MUTED);
    }

    private void iconButton(GuiGraphics graphics, PcFilesIcon icon, int x, int y, int w, int h,
                            int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF323945 : CARD);
        icon.draw(graphics, x + (w - 11) / 2, y + (h - 11) / 2, 11, TEXT);
    }

    private void activityButton(GuiGraphics graphics, PcFilesIcon icon, String title, int x, int w,
                                int mouseX, int mouseY, boolean selected) {
        int y = this.y + activityTop();
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        graphics.fill(x, y, x + w, y + 16, selected ? 0xFF354257 : hover ? 0xFF323945 : CARD);
        int color = selected ? ACCENT : TEXT;
        boolean hasLabel = w >= 18 + Minecraft.getInstance().font.width(uiText(title));
        icon.draw(graphics, x + (hasLabel ? 4 : (w - 10) / 2), y + 3, 10, color);
        if (hasLabel) text(graphics, title, x + 16, y + 4, w - 18, color);
    }

    private int activityWidth(String title) {
        var font = Minecraft.getInstance().font;
        int fullWidth = font.width(uiText("Recent")) + font.width(uiText("Favorites")) + 50;
        return paneWidth() < fullWidth ? 24 : font.width(uiText(title)) + 23;
    }

    private String location() {
        if (special()) return name(folder);
        try {
            return drive.path(folder);
        } catch (IOException e) {
            return name(folder);
        }
    }

    private int addressLeft() { return x + 84; }
    private int addressRight() { return field.getX() - 10; }
    private int addressBottom() { return y + 41; }
    private int addressClearLeft() { return addressRight() - 15; }

    private List<Crumb> breadcrumbs() {
        var result = new ArrayDeque<Crumb>();
        int available = addressRight() - addressLeft() - 13;
        List<PcFileSystem.Entry> path = List.of();
        if (!special()) {
            try {
                path = drive.pathEntries(folder);
            } catch (IOException ignored) {
                // A location removed by another app is reset on the next refresh.
            }
        }
        var font = Minecraft.getInstance().font;
        if (path.isEmpty()) return List.of(new Crumb(folder, name(folder), Math.max(0, available)));
        for (var entry : path.reversed()) {
            String label = entry.id().equals(PcFileSystem.ROOT) ? "C:" : entry.name();
            int desired = font.width(uiText(label)) + 12;
            if (!result.isEmpty() && desired > available) {
                if (available >= 18) result.addFirst(new Crumb(entry.id(), "…", 18));
                break;
            }
            int size = Math.clamp(desired, 0, Math.max(0, available));
            result.addFirst(new Crumb(entry.id(), label, size));
            available -= size;
        }
        return List.copyOf(result);
    }

    private void renderAddress(GuiGraphics graphics, int mx, int my, float partialTick) {
        int left = addressLeft();
        int right = addressRight();
        int top = y + 24;
        graphics.fill(left, top, right, addressBottom(), CARD);
        if (address.isFocused()) {
            graphics.hLine(left, right - 1, addressBottom() - 1, ACCENT);
            address.render(graphics, mx, my, partialTick);
            if (mx >= addressClearLeft() && mx < right && my >= top && my < addressBottom()) {
                graphics.fill(addressClearLeft(), top, right, addressBottom() - 1, RULE);
            }
            PcFilesIcon.CLOSE.draw(graphics, right - 12, y + 28, 9, MUTED);
            return;
        }
        for (var crumb : breadcrumbs()) {
            int end = left + crumb.width();
            if (mx >= left && mx < end && my >= top && my < addressBottom()) {
                graphics.fill(left, top, end, addressBottom(), RULE);
            }
            filename(graphics, crumb.label(), left + 4, y + 29, crumb.width() - 12, TEXT);
            if (!crumb.id().equals(folder)) PcFilesIcon.RIGHT.draw(graphics, end - 9, y + 28, 8, MUTED);
            left = end;
        }
    }

    private void beginAddress() {
        popup = null;
        field.setFocused(false);
        address.setValue(location());
        address.setHighlightPos(0);
        address.setFocused(true);
        addressChoice = -1;
        var history = new ArrayList<String>();
        for (UUID id : addressHistory.reversed()) {
            if (id.equals(folder)) continue;
            try {
                if (drive.get(id).folder()) history.add(drive.path(id));
            } catch (IOException ignored) {
                // Deleted locations no longer appear in the address history.
            }
        }
        addressSuggestions = history;
    }

    private void endAddress() {
        address.setFocused(false);
        addressSuggestions = List.of();
        addressChoice = -1;
    }

    private UUID addressBase() { return special() ? PcFileSystem.ROOT : folder; }

    private void completeAddress() {
        addressChoice = -1;
        addressSuggestions = List.of();
        String input = address.getValue().stripLeading().replace('/', '\\');
        if (input.startsWith("\"")) input = input.substring(1);
        int separator = input.lastIndexOf('\\') + 1;
        if (input.equalsIgnoreCase("C:")) separator = input.length();
        try {
            var parent = drive.resolve(addressBase(), separator == 0 ? "." : input.substring(0, separator));
            if (!parent.folder()) return;
            String prefix = input.substring(separator);
            String canonical = drive.path(parent.id());
            String start = canonical.endsWith("\\") ? canonical : canonical + "\\";
            addressSuggestions = drive.list(parent.id(), "").stream()
                    .filter(entry -> entry.name().regionMatches(true, 0, prefix, 0, prefix.length()))
                    .limit(8).map(entry -> start + entry.name()).toList();
        } catch (IOException ignored) {
            // The user can keep editing an incomplete path.
        }
    }

    private int visibleAddressSuggestions() {
        return Math.min(Math.min(8, addressSuggestions.size()), Math.max(0, (height - 55) / MENU_ROW));
    }

    private void renderAddressSuggestions(GuiGraphics graphics, int mx, int my) {
        int count = visibleAddressSuggestions();
        if (count == 0) return;
        int left = addressLeft();
        int right = addressRight();
        int top = addressBottom();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2);
        try {
            graphics.fill(left, top, right, top + count * MENU_ROW, BAND);
            graphics.renderOutline(left, top, right - left, count * MENU_ROW, RULE);
            for (int i = 0; i < count; i++) {
                int row = top + i * MENU_ROW;
                if (i == addressChoice || mx >= left && mx < right && my >= row && my < row + MENU_ROW) {
                    graphics.fill(left + 1, row + 1, right - 1, row + MENU_ROW, CARD);
                }
                filename(graphics, addressSuggestions.get(i), left + 6, row + 6, right - left - 12, TEXT);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private void submitAddress(String value) {
        // The shell locations have labels, while drive entries have canonical paths.
        String label = value.strip();
        for (UUID place : List.of(HOME, STORAGE, BIN)) {
            if (label.equalsIgnoreCase(name(place))) {
                endAddress();
                status = "";
                navigate(place);
                return;
            }
        }
        try {
            var entry = drive.resolve(addressBase(), value);
            if (!entry.folder()) openFile(entry);
            endAddress();
            status = "";
            if (entry.folder()) navigate(entry.id());
        } catch (IOException e) {
            status = message(e);
        }
    }

    @Override
    public void renderPreview(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF202831);
        text(graphics, folder.equals(BIN) ? "Recycle Bin" : location(), x + 8, y + 6, width - 16, MUTED);
        for (int row = 0; row < Math.min(entries.size(), Math.max(0, (height - 20) / ROW)); row++) {
            text(graphics, entries.get(row).name(), x + 10, y + 20 + row * ROW, width - 20, TEXT);
        }
    }

    private void text(GuiGraphics graphics, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        var clipped = font.substrByWidth(uiText(value), Math.max(0, width));
        graphics.drawString(font, Language.getInstance().getVisualOrder(clipped), x, y, color, false);
    }

    private static Component uiText(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(FONT));
    }

    private void filename(GuiGraphics graphics, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        if (font.width(uiText(value)) > width && width > font.width(uiText("…"))) {
            int available = width - font.width(uiText("…"));
            value = font.substrByWidth(uiText(value), available).getString() + "…";
        }
        text(graphics, value, x, y, width, color);
    }

    private void drawNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = x + 5;
        int top = y + 24;
        for (var action : NAV) {
            var icon = switch (action) {
                case BACK -> PcFilesIcon.BACK;
                case FORWARD -> PcFilesIcon.FORWARD;
                case UP -> PcFilesIcon.UP;
                default -> throw new IllegalStateException("Unsupported navigation action " + action);
            };
            boolean enabled = switch (action) {
                case BACK -> !back.isEmpty();
                case FORWARD -> !forward.isEmpty();
                case UP -> !folder.equals(HOME);
                default -> false;
            };
            if (enabled && mouseX >= left && mouseX < left + 26 && mouseY >= top && mouseY < top + 17) {
                graphics.fill(left, top, left + 25, top + 17, CARD);
            }
            icon.draw(graphics, left + 7, top + 2, 13, enabled ? TEXT : 0xFF6A707C);
            left += 26;
        }
    }

    private PcFilesView view() {
        return homeCards() ? PcFilesView.DETAILS : PcFilesView.at(viewStep);
    }

    private boolean supportsLayouts() {
        return !homeCards() && (!folder.equals(STORAGE) || !query.isBlank());
    }

    private boolean stackedIcons() {
        return view() == PcFilesView.MEDIUM || view() == PcFilesView.LARGE || view() == PcFilesView.EXTRA_LARGE;
    }

    private int entryIconSize() {
        if (stackedIcons()) {
            return Math.min(PcFilesView.iconSize(viewStep),
                    Math.max(8, Math.min(paneWidth() - 12, height - listTop() - 34)));
        }
        return switch (view()) {
            case TILES -> 30;
            case CONTENT -> 25;
            case SMALL -> PcFilesView.iconSize(viewStep);
            default -> 12;
        };
    }

    private EntryLayout entryLayout() {
        var mode = view();
        int icon = entryIconSize();
        int available = Math.max(1, paneWidth());
        int cellWidth = switch (mode) {
            case LIST -> 126;
            case TILES -> 144;
            case SMALL -> 102;
            case MEDIUM, LARGE, EXTRA_LARGE -> Math.max(68, icon + 22);
            default -> available;
        };
        int cellHeight = switch (mode) {
            case TILES, CONTENT -> 41;
            case SMALL -> icon + 7;
            case MEDIUM, LARGE, EXTRA_LARGE -> icon + 20;
            default -> compact ? 14 : ROW;
        };
        boolean list = mode == PcFilesView.LIST;
        int columns = Math.max(1, available / cellWidth);
        int visible = Math.max(0, (height - listTop() - (list ? 21 : 14)) / cellHeight);
        return new EntryLayout(paneX(), y + listTop(), columns, visible, available / columns, cellHeight, list);
    }

    private int commandWidth(Command command) {
        if (width < 252) return 18;
        if (command.label.isEmpty() || width < 325 && command != Command.NEW) return 20;
        return command == Command.DETAILS ? 63 : command == Command.NEW ? 48 : 53;
    }

    private void drawCommands(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = x + 5;
        for (var command : COMMANDS) {
            int step = commandWidth(command);
            drawCommand(graphics, command, left, y + 44, step, mouseX, mouseY, false);
            left += step;
            if (command == Command.NEW || command == Command.DELETE || command == Command.VIEW) {
                graphics.vLine(left, y + 46, y + 58, RULE);
                left += 4;
            }
        }
        int w = commandWidth(Command.DETAILS);
        drawCommand(graphics, Command.DETAILS, x + width - w - 5, y + 44, w, mouseX, mouseY, false);
    }

    private void drawCommand(GuiGraphics graphics, Command command, int left, int top, int w,
                             int mouseX, int mouseY, boolean inMenu) {
        boolean enabled = enabled(command);
        boolean hover = mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < top + 17;
        if (hover && enabled) graphics.fill(left, top, left + w, top + 17, CARD);
        int color = enabled ? TEXT : 0xFF6A707C;
        command.icon.draw(graphics, left + (w > 24 && !inMenu ? 4 : (w - 11) / 2), top + 3, 11, color);
        if (!command.label.isEmpty() && w > 24 && !inMenu) {
            boolean dropdown = command == Command.NEW || command == Command.SORT || command == Command.VIEW;
            text(graphics, command.label, left + 16, top + 5, w - (dropdown ? 22 : 18), color);
            if (dropdown) {
                PcFilesIcon.DOWN.draw(graphics, left + w - 9, top + 5, 7, color);
            }
        }
    }

    private boolean enabled(Command command) {
        return switch (command) {
            case NEW -> !special();
            case CUT, RENAME, DELETE -> !special() && selection != null;
            case COPY -> !folder.equals(BIN) && selection != null;
            case PASTE -> !special() && clipboard.get() != null;
            default -> true;
        };
    }

    private void command(Command command, int anchorX, int anchorY) {
        if (!enabled(command)) return;
        switch (command) {
            case CUT -> run(Action.CUT);
            case COPY -> run(Action.COPY);
            case PASTE -> run(Action.PASTE);
            case RENAME -> run(Action.RENAME);
            case DELETE -> run(Action.DELETE);
            case DETAILS -> run(Action.DETAILS);
            case NEW -> showPopup(anchorX, anchorY, List.of(item(PcFilesIcon.FOLDER, "Folder", () -> run(Action.FOLDER)),
                    item(PcFilesIcon.FILE_TEXT, "Text document", () -> run(Action.FILE))), List.of());
            case SORT -> showPopup(anchorX, anchorY, List.of(
                    choice(PcFilesIcon.RENAME, "Name", sortColumn == 0, () -> setSort(0, descending)),
                    choice(PcFilesIcon.CALENDAR, "Date modified", sortColumn == 1, () -> setSort(1, descending)),
                    choice(PcFilesIcon.EXTENSION, "Type", sortColumn == 2, () -> setSort(2, descending)),
                    choice(PcFilesIcon.DRIVE, "Size", sortColumn == 3, () -> setSort(3, descending)),
                    choice(PcFilesIcon.SORT_UP, "Ascending", !descending, () -> setSort(sortColumn, false)),
                    choice(PcFilesIcon.SORT_DOWN, "Descending", descending, () -> setSort(sortColumn, true)),
                    choice(PcFilesIcon.GROUP, "Group by date", groupDates, () -> {
                        groupDates = !groupDates;
                        if (groupDates) setSort(1, true); else refresh();
                    })), List.of());
            case VIEW -> showViewMenu(anchorX, anchorY);
            case MORE -> showPopup(anchorX, anchorY, moreItems(), List.of());
        }
    }

    private void setSort(int column, boolean reverse) {
        sortColumn = column;
        descending = reverse;
        scroll = 0;
        refresh();
    }

    private void showViewMenu(int anchorX, int anchorY) {
        var options = new ArrayList<MenuItem>();
        for (var mode : PcFilesView.values()) {
            options.add(new MenuItem(viewIcon(mode), mode.label, () -> {
                zoomRemainder = 0;
                setView(mode.step);
            }, mode == view(), supportsLayouts(), true));
        }
        options.add(choice(PcFilesIcon.DETAILS, "Details pane", details, () -> run(Action.DETAILS)));
        options.add(choice(PcFilesIcon.PREVIEW, "Preview pane", preview, this::togglePreview));
        options.add(item(null, "Show", () -> showPopup(anchorX, anchorY, List.of(
                choice(PcFilesIcon.LEFT_PANE, "Navigation pane", navigationPane, () -> {
                    navigationPane = !navigationPane;
                    refreshPaneLayout();
                }),
                choice(PcFilesIcon.EXTENSION, "File name extensions", showExtensions, () -> showExtensions = !showExtensions),
                choice(PcFilesIcon.COMPACT, "Compact view", compact, () -> {
                    compact = !compact;
                    clampScroll();
                }),
                item(PcFilesIcon.BACK, "Back to View", () -> showViewMenu(anchorX, anchorY))), List.of())));
        showPopup(anchorX, anchorY, options, List.of());
    }

    private void togglePreview() {
        preview = !preview;
        details = false;
        resetInspector();
        refreshPaneLayout();
    }

    private void refreshPaneLayout() {
        quickPage = Math.clamp(quickPage, 0, quickPages() - 1);
        clampScroll();
    }

    private void setView(int next) {
        if (viewStep == next) return;
        UUID anchor = scroll < rows.size() && rows.get(scroll).entry != null ? rows.get(scroll).entry.id() : null;
        viewStep = next;
        lastClick = null;
        folderViews.put(folder, next);
        saveViewAt = net.minecraft.Util.getMillis() + 350;
        rebuildRows();
        scroll = 0;
        if (anchor != null) reveal(anchor);
        if (selection != null) reveal(selection);
        clampScroll();
    }

    private void saveView() {
        saveViewAt = 0;
        savePins.run();
    }

    @Override
    public void closed() {
        finishPaneDrag();
        clearThumbnails();
        resetInspector();
        if (saveViewAt != 0) saveView();
    }

    private static MenuItem item(PcFilesIcon icon, String label, Runnable action) {
        return new MenuItem(icon, label, action, false, true);
    }

    private static MenuItem choice(PcFilesIcon icon, String label, boolean checked, Runnable action) {
        return new MenuItem(icon, label, action, checked, true);
    }

    private List<MenuItem> moreItems() {
        boolean selected = selection != null;
        if (folder.equals(BIN)) {
            return List.of(new MenuItem(PcFilesIcon.RESET, "Restore", () -> run(Action.RESTORE), false, selected),
                    item(PcFilesIcon.DELETE, "Empty Recycle Bin", () -> run(Action.EMPTY)));
        }
        var items = new ArrayList<MenuItem>();
        entries.stream().filter(entry -> entry.id().equals(selection) && entry.folder()).findFirst()
                .ifPresent(entry -> items.addAll(folderPinItems(entry.id())));
        boolean favorite = favorites.contains(selection);
        items.addAll(List.of(new MenuItem(favorite ? PcFilesIcon.STAR_OFF : PcFilesIcon.STAR,
                        favorite ? "Remove from Favorites" : "Add to Favorites",
                        () -> run(Action.FAVORITE), false, selected),
                new MenuItem(PcFilesIcon.PATH, "Copy virtual path", this::copyPath, false, selected),
                new MenuItem(PcFilesIcon.CLEAR, "Clear selection", () -> selection = null, false, selected),
                new MenuItem(PcFilesIcon.INFO, "Properties", this::properties, false, selected),
                item(PcFilesIcon.RESET, "Refresh", this::refresh)));
        return items;
    }

    private List<MenuItem> folderPinItems(UUID id) {
        boolean quick = quickFolders.contains(id);
        boolean start = startFolders.contains(id);
        return List.of(item(quick ? PcFilesIcon.UNPIN : PcFilesIcon.PIN,
                        quick ? "Unpin from Quick access" : "Pin to Quick access",
                        () -> toggleFolderPin(id, quickFolders, "Quick access")),
                item(start ? PcFilesIcon.UNPIN : PcFilesIcon.PIN, start ? "Unpin from Start" : "Pin to Start",
                        () -> toggleFolderPin(id, startFolders, "Start")));
    }

    private void toggleFolderPin(UUID id, LinkedHashSet<UUID> pins, String destination) {
        try {
            if (!drive.get(id).folder()) return;
            boolean removed = pins.remove(id);
            if (!removed) pins.add(id);
            savePins.run();
            refresh();
            if (homeCards() && !removed && pins == quickFolders) {
                quickPage = quickPages() - 1;
                clampScroll();
            }
            status = (removed ? "Unpinned from " : "Pinned to ") + destination + ".";
        } catch (IOException e) {
            status = message(e);
        }
    }

    private void copyPath() {
        if (selection == null) return;
        try {
            String path = drive.path(selection);
            Minecraft.getInstance().keyboardHandler.setClipboard(path);
            clipboard.set(new PcClipboard.Text(path));
            status = "Virtual path copied.";
        } catch (IOException e) {
            status = message(e);
        }
    }

    private void properties() {
        if (!details) run(Action.DETAILS);
    }

    private void showPopup(int left, int top, List<MenuItem> items, List<Command> actions) {
        int popupWidth = Math.min(148, width - 8);
        int header = actions.isEmpty() ? 0 : 23;
        int available = top == y + 63 ? y + height - top : height;
        int visible = Math.min(items.size(), Math.max(1, (available - header - 12) / MENU_ROW));
        int popupHeight = visible * MENU_ROW + header + 8;
        popup = new Popup(Math.clamp(left, x + 4, x + width - popupWidth - 4),
                Math.clamp(top, y + 2, Math.max(y + 2, y + height - popupHeight - 2)),
                popupWidth, items, actions, visible);
    }

    private void renderPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        if (popup == null) return;
        var menu = popup;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2);
        try {
            graphics.fill(menu.x, menu.y, menu.x + menu.width, menu.bottom(), BAND);
            graphics.renderOutline(menu.x, menu.y, menu.width, menu.bottom() - menu.y, RULE);
            if (!menu.actions.isEmpty()) {
                int step = menu.width / menu.actions.size();
                for (int i = 0; i < menu.actions.size(); i++) {
                    drawCommand(graphics, menu.actions.get(i), menu.x + i * step, menu.y + 4,
                            step, mouseX, mouseY, true);
                }
                graphics.hLine(menu.x + 5, menu.x + menu.width - 6, menu.rowsY() - 3, RULE);
            }
            for (int row = 0; row < menu.visible; row++) {
                int index = row + menu.offset;
                var entry = menu.items.get(index);
                int top = menu.rowsY() + row * MENU_ROW;
                boolean hover = mouseX >= menu.x && mouseX < menu.x + menu.width && mouseY >= top && mouseY < top + MENU_ROW;
                if (entry.enabled && (hover || index == menu.cursor)) {
                    graphics.fill(menu.x + 4, top, menu.x + menu.width - 4, top + MENU_ROW - 1, CARD);
                }
                if (entry.checked) graphics.fill(menu.x + 8, top + 8, menu.x + 11, top + 11, ACCENT);
                int labelX = menu.x + 18;
                if (entry.icon != null) {
                    entry.icon.draw(graphics, labelX, top + 4, 12, entry.enabled ? ACCENT : 0xFF6A707C);
                    labelX += 15;
                }
                if (menu.items.getFirst().viewOption) {
                    if (index == PcFilesView.values().length || entry.label.equals("Show")) {
                        graphics.hLine(menu.x + 4, menu.x + menu.width - 5, top - 1, RULE);
                    }
                    if (entry.icon == null) {
                        PcFilesIcon.RIGHT.draw(graphics, menu.x + menu.width - 16, top + 4, 11, MUTED);
                        labelX += 15;
                    }
                }
                text(graphics, entry.label, labelX, top + 6, menu.x + menu.width - labelX - 6,
                        entry.enabled ? TEXT : MUTED);
            }
            if (menu.visible < menu.items.size()) {
                int track = menu.visible * MENU_ROW;
                int thumb = Math.max(6, track * menu.visible / menu.items.size());
                int top = menu.rowsY() + (track - thumb) * menu.offset / (menu.items.size() - menu.visible);
                graphics.fill(menu.x + menu.width - 3, top, menu.x + menu.width - 2, top + thumb, MUTED);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static PcFilesIcon viewIcon(PcFilesView mode) {
        return switch (mode) {
            case EXTRA_LARGE -> PcFilesIcon.EXTRA_LARGE;
            case LARGE -> PcFilesIcon.LARGE;
            case MEDIUM -> PcFilesIcon.MEDIUM;
            case SMALL -> PcFilesIcon.SMALL;
            case LIST -> PcFilesIcon.LIST;
            case DETAILS -> PcFilesIcon.TABLE;
            case TILES -> PcFilesIcon.TILES;
            case CONTENT -> PcFilesIcon.CONTENT;
        };
    }

    private void navigate(UUID destination) {
        if (!destination.equals(folder)) {
            back.push(folder);
            forward.clear();
            resetFolder(destination);
        }
    }

    private void resetFolder(UUID destination) {
        endAddress();
        folder = destination;
        viewStep = folderViews.getOrDefault(destination, PcFilesView.DETAILS.step);
        zoomRemainder = 0;
        if (!special()) {
            addressHistory.addLast(folder);
            if (addressHistory.size() > 24) addressHistory.removeFirst();
        }
        sortColumn = name(folder).equals("Downloads") ? 1 : 0;
        descending = sortColumn == 1;
        groupDates = sortColumn == 1;
        scroll = 0;
        selection = null;
        lastClick = null;
        query = "";
        prompt = null;
        field.setValue("");
        field.setHint(uiText("Search " + name(folder)));
        field.setFocused(false);
        status = "";
        refresh();
    }

    private UUID selected() throws IOException {
        if (selection == null) {
            throw new IOException("Select an item first.");
        }
        return selection;
    }

    private void run(Action action) {
        try {
            status = "";
            switch (action) {
                case BACK -> history(back, forward);
                case FORWARD -> history(forward, back);
                case UP -> {
                    UUID parent = folder.equals(HOME) ? null : special() ? HOME : drive.get(folder).parent();
                    if (folder.equals(PcFileSystem.ROOT)) parent = STORAGE;
                    if (parent != null) navigate(parent);
                }
                case FOLDER, FILE -> {
                    UUID created = drive.createDefault(folder, action == Action.FOLDER);
                    query = "";
                    field.setValue("");
                    selection = created;
                    refresh();
                    beginNameEdit(drive.get(created));
                }
                case RENAME -> beginNameEdit(drive.get(selected()));
                case COPY, CUT -> {
                    boolean cut = action == Action.CUT;
                    clipboard.set(new PcClipboard.Files(List.of(selected()), cut));
                    status = cut ? "Ready to move. Choose a folder and paste." : "Copied. Choose a folder and paste.";
                }
                case PASTE -> {
                    PcClipboardState.paste(clipboard, drive, folder);
                    status = "Item pasted.";
                }
                case DELETE -> {
                    drive.recycle(selected());
                    status = "Moved to Recycle Bin.";
                }
                case RESTORE -> {
                    drive.restore(selected());
                    status = "Item restored.";
                }
                case EMPTY -> beginEmptyPrompt();
                case DETAILS -> {
                    details = !details;
                    preview = false;
                    resetInspector();
                    refreshPaneLayout();
                }
                case FAVORITE -> {
                    var id = selected();
                    if (favorites.remove(id)) {
                        status = "Removed from Favorites.";
                    } else {
                        favorites.add(id);
                        status = "Added to Favorites.";
                    }
                }
            }
            refresh();
        } catch (IOException e) {
            status = message(e);
        }
    }

    private void history(ArrayDeque<UUID> from, ArrayDeque<UUID> to) {
        if (!from.isEmpty()) {
            to.push(folder);
            resetFolder(from.pop());
        }
    }

    private void beginEmptyPrompt() {
        prompt = Action.EMPTY;
        field.setValue("");
        field.setHint(Component.literal("Type EMPTY to confirm"));
        field.setFocused(true);
        status = "Type EMPTY to permanently delete recycled items.";
    }

    private void submit() {
        try {
            if (!field.getValue().equals("EMPTY")) throw new IOException("Type EMPTY exactly to confirm.");
            drive.emptyTrash();
            endPrompt();
            status = "Done.";
            refresh();
        } catch (IOException e) {
            status = message(e);
        }
    }

    private void beginNameEdit(PcFileSystem.Entry entry) {
        editingName = entry.id();
        String name = entry.name();
        nameEditor = new EditBox(Minecraft.getInstance().font, 0, 0, 70, 12, Component.literal("File or folder name"));
        nameEditor.setMaxLength(120);
        nameEditor.setValue(name);
        nameEditor.setTextShadow(false);
        nameEditor.setFocused(focused);
        int extension = entry.folder() || entry.shortcut() ? -1 : name.lastIndexOf('.');
        nameEditor.setCursorPosition(extension > 0 ? extension : name.length());
        nameEditor.setHighlightPos(0);
        field.setFocused(false);
        lastClick = null;
        status = "Enter to keep the name. Esc to cancel.";
        reveal(entry.id());
    }

    private void reveal(UUID id) {
        var layout = entryLayout();
        if (layout.visible() == 0) return;
        for (int row = 0; row < rows.size(); row++) {
            var entry = rows.get(row).entry;
            if (entry != null && entry.id().equals(id)) {
                int stride = layout.stride();
                int start = row / stride * stride;
                scroll = Math.clamp(scroll, Math.max(0, start - layout.visible() + stride), start);
                return;
            }
        }
    }

    private boolean finishNameEdit(boolean commit) {
        if (commit) {
            try {
                String name = nameEditor.getValue();
                if (!name.equals(drive.get(editingName).name())) {
                    drive.rename(editingName, name);
                }
            } catch (IOException e) {
                status = message(e);
                return false;
            }
        }
        nameEditor = null;
        editingName = null;
        status = "";
        refresh();
        if (selection != null) reveal(selection);
        return true;
    }

    private void endPrompt() {
        prompt = null;
        field.setValue(query);
        field.setHint(uiText("Search " + name(folder)));
        field.setFocused(false);
        status = "";
    }

    private void activate() {
        lastClick = null;
        try {
            UUID id = selected();
            if (folder.equals(BIN)) {
                run(Action.RESTORE);
            } else {
                var entry = drive.get(id);
                if (entry.folder()) {
                    navigate(id);
                } else {
                    openFile(entry);
                }
            }
        } catch (IOException e) {
            status = message(e);
        }
    }

    private void openFile(PcFileSystem.Entry entry) throws IOException {
        open.open(entry);
        recent.addLast(entry.id());
        if (recent.size() > 32) recent.removeFirst();
    }

    private static String message(IOException e) {
        return e.getMessage() == null ? "The file operation failed." : e.getMessage();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ItemClick previous = lastClick;
        lastClick = null;
        if (revision != drive.revision()) {
            refresh();
        }
        if (nameEditor != null) {
            if (nameEditor.isMouseOver(mx, my)) {
                nameEditor.mouseClicked(mx, my, button);
                return true;
            }
            if (!finishNameEdit(true)) return true;
        }
        if (popup != null) {
            var menu = popup;
            popup = null;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mx >= menu.x && mx < menu.x + menu.width
                    && my >= menu.y && my < menu.bottom()) {
                if (my < menu.rowsY() && !menu.actions.isEmpty() && my >= menu.y + 4 && my < menu.y + 21) {
                    int slot = Math.min(menu.actions.size() - 1,
                            ((int) mx - menu.x) / (menu.width / menu.actions.size()));
                    command(menu.actions.get(slot), menu.x, menu.y);
                } else if (my >= menu.rowsY()) {
                    int row = ((int) my - menu.rowsY()) / MENU_ROW;
                    if (row < menu.visible) activateMenuItem(menu.items.get(menu.offset + row));
                }
                return true;
            }
            if (my < y + 44 || my >= y + 61) return true;
        }
        if (prompt == null && mx >= addressLeft() && mx < addressRight()) {
            if (my >= y + 24 && my < addressBottom() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (address.isFocused()) {
                    if (mx >= addressClearLeft()) {
                        address.setValue("");
                        completeAddress();
                    } else {
                        address.mouseClicked(mx, my, button);
                    }
                } else {
                    int left = addressLeft();
                    for (var crumb : breadcrumbs()) {
                        left += crumb.width();
                        if (mx < left) {
                            if (!crumb.id().equals(folder)) {
                                navigate(crumb.id());
                                return true;
                            }
                            break;
                        }
                    }
                    beginAddress();
                }
                return true;
            }
            if (my >= addressBottom() && my < addressBottom() + visibleAddressSuggestions() * MENU_ROW) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    submitAddress(addressSuggestions.get(((int) my - addressBottom()) / MENU_ROW));
                }
                return true;
            }
        }
        if (address.isFocused()) endAddress();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && prompt == null) {
            var divider = dividerAt(mx, my);
            if (divider != null) {
                var current = panes();
                int startWidth = divider == PcFilesPanes.Divider.NAVIGATION ? current.navigation() : current.inspector();
                paneDrag = new PaneDrag(divider, mx, startWidth);
                field.setFocused(false);
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && prompt == null) {
            int localY = (int) my - y;
            if (homeCards() && mx >= paneX() && mx < paneX() + paneWidth()) {
                var card = quickItemAt((int) mx - paneX(), localY);
                if (card != null) {
                    field.setFocused(false);
                    showPopup((int) mx, (int) my, folderPinItems(card.id()), List.of());
                    return true;
                }
            }
            if (mx < x + railWidth() && localY >= 82 && localY < 82 + railRows() * railRowHeight()) {
                int index = railScroll + (localY - 82) / railRowHeight();
                if (index >= 3 && index < places().size()) {
                    field.setFocused(false);
                    showPopup((int) mx, (int) my, folderPinItems(places().get(index).id()), List.of());
                    return true;
                }
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && prompt == null && mx >= paneX()
                && mx < paneX() + paneWidth() && my >= y + listTop() && my < y + height - 14) {
            var entry = itemAt(mx, (int) my - y);
            selection = entry == null ? null : entry.id();
            field.setFocused(false);
            if (entry == null && !special()) {
                command(Command.NEW, (int) mx, (int) my);
            } else if (entry == null || folder.equals(BIN)) {
                command(Command.MORE, (int) mx, (int) my);
            } else {
                var items = new ArrayList<MenuItem>();
                items.add(item(PcFilesIcon.OPEN, "Open", this::activate));
                if (entry != null && !entry.folder() && !entry.shortcut()) {
                    var file = entry;
                    items.add(item(PcFilesIcon.OPEN, "Open with", () -> chooseApp.accept(file)));
                }
                items.addAll(moreItems());
                showPopup((int) mx, (int) my, items,
                        List.of(Command.CUT, Command.COPY, Command.RENAME, Command.DELETE));
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (mx >= field.getX() - 5 && mx < x + width - 5 && my >= y + 24 && my < y + 41) {
            field.setFocused(true);
            field.mouseClicked(mx, my, button);
            return true;
        }
        if (prompt != null) {
            return true;
        }
        field.setFocused(false);
        int localY = (int) my - y;
        if (localY >= 4 && localY < 20) {
            int tabX = (int) mx - x - 6;
            int index = tabX / tabWidth();
            if (tabX >= 0 && index < tabs.size()) {
                if (index == activeTab && tabX % tabWidth() >= tabWidth() - 16) {
                    closeTab();
                } else {
                    switchTab(index);
                }
            } else if (tabX >= tabs.size() * tabWidth() && tabX < tabs.size() * tabWidth() + 22) {
                newTab();
            }
        } else if (localY >= 24 && localY < 41) {
            int localX = (int) mx - x - 5;
            int index = localX / (78 / NAV.size());
            if (localX >= 0 && index < NAV.size()) {
                run(NAV.get(index));
            }
        } else if (localY >= 44 && localY < 61) {
            clickCommands((int) mx);
        } else if (mx < x + railWidth() && localY >= 82 && localY + 14 < height) {
            int row = (localY - 82) / railRowHeight();
            int index = railScroll + row;
            var destinations = places();
            if (index < destinations.size() && row < railRows()) {
                navigate(destinations.get(index).id);
            }
        } else if (mx >= paneX() && mx < paneX() + paneWidth()) {
            clickPane(mx, localY, previous);
        }
        return true;
    }

    private void clickCommands(int mouseX) {
        int left = x + 5;
        for (var item : COMMANDS) {
            int step = commandWidth(item);
            if (mouseX >= left && mouseX < left + step) {
                command(item, left, y + 63);
                return;
            }
            left += step + (item == Command.NEW || item == Command.DELETE || item == Command.VIEW ? 4 : 0);
        }
        if (mouseX >= x + width - commandWidth(Command.DETAILS) - 5 && mouseX < x + width - 5) {
            command(Command.DETAILS, mouseX, y + 63);
        }
    }

    private PcFileSystem.Entry itemAt(double mx, int localY) {
        int index = entryLayout().index(mx, y + localY);
        return index >= 0 && scroll + index < rows.size() ? rows.get(scroll + index).entry : null;
    }

    private void activateMenuItem(MenuItem item) {
        if (item.enabled) item.action.run();
    }

    private void clickPane(double mx, int localY, ItemClick previous) {
        int localX = (int) mx - paneX();
        if (folder.equals(STORAGE) && query.isBlank()) {
            if (localY >= 85 && localY < 147 && localY < height - 14) navigate(PcFileSystem.ROOT);
            return;
        }
        if (homeCards() && localY >= 65 && localY < activityTop()) {
            if (localY < 80) {
                if (localX < 115) {
                    quickCollapsed = !quickCollapsed;
                } else if (!quickCollapsed && quickPages() > 1 && localX >= paneWidth() - 49) {
                    int step = localX >= paneWidth() - 13 ? 1 : localX < paneWidth() - 33 ? -1 : 0;
                    quickPage = Math.clamp(quickPage + step, 0, quickPages() - 1);
                }
                clampScroll();
            } else {
                var card = quickItemAt(localX, localY);
                if (card != null) navigate(card.id());
            }
            return;
        }
        if (homeCards() && localY >= activityTop() && localY < activityTop() + 16) {
            int recentWidth = activityWidth("Recent");
            int favoriteLeft = recentWidth + 4;
            if (localX < recentWidth || localX >= favoriteLeft && localX < favoriteLeft + activityWidth("Favorites")) {
                showFavorites = localX >= favoriteLeft;
                scroll = 0;
                refresh();
                return;
            }
        }
        if (!homeCards() && view() == PcFilesView.DETAILS && localY >= 64 && localY < LIST_TOP) {
            for (int col = 0; col < columnCount(); col++) {
                if (localX < column(col + 1)) {
                    descending = sortColumn == col ? !descending : col == 1;
                    sortColumn = col;
                    scroll = 0;
                    refresh();
                    break;
                }
            }
            return;
        }
        var layout = entryLayout();
        if (layout.list && layout.visible() > 0 && rows.size() > layout.visible()
                && localY >= height - 20 && localY < height - 14) {
            int remaining = Math.ceilDiv(rows.size(), layout.rows) - layout.columns;
            scroll = Math.round((float) localX / paneWidth() * remaining) * layout.rows;
            clampScroll();
            return;
        }
        if (localY >= listTop() && localY < height - 14) {
            var entry = itemAt(mx, localY);
            selection = entry != null ? entry.id() : null;
            if (entry == null) return;

            var label = labelBounds(layout.cell(layout.index(mx, y + localY)), entry);
            int labelWidth = Math.min(label.width, Minecraft.getInstance().font.width(uiText(displayName(entry))));
            boolean onName = mx >= label.x && mx < label.x + labelWidth
                    && y + localY >= label.y - 1 && y + localY < label.y + label.height;
            long now = net.minecraft.Util.getMillis();
            lastClick = new ItemClick(selection, now, onName);
            if (previous != null && selection.equals(previous.id())) {
                if (now - previous.time() <= PcDesktopScreen.DOUBLE_CLICK_MS) {
                    activate();
                } else if (!special() && onName && previous.onName()) {
                    beginNameEdit(entry);
                }
            }
        }
    }

    private PcFileSystem.Entry quickItemAt(int localX, int localY) {
        if (localY < QUICK_TOP || localY >= activityTop() - 4) return null;
        int step = Math.max(1, paneWidth() / cardColumns());
        int column = localX / step;
        int row = (localY - QUICK_TOP) / quickRowHeight();
        int index = row * cardColumns() + column;
        if (column >= cardColumns() || localX % step >= step - 4 || index >= quickCount()) return null;
        return quick.get(quickPage * quickPageSize() + index);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (paneDrag == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        int delta = (int) Math.round(mx - paneDrag.startX);
        if (paneDrag.divider == PcFilesPanes.Divider.INSPECTOR) delta = -delta;
        if (paneWidths.resize(paneDrag.divider, paneDrag.startWidth + delta, panes())) {
            paneWidthsChanged = true;
            refreshPaneLayout();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (paneDrag == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        mouseDragged(mx, my, button, 0, 0);
        finishPaneDrag();
        return true;
    }

    private void finishPaneDrag() {
        paneDrag = null;
        if (paneWidthsChanged) {
            paneWidthsChanged = false;
            savePins.run();
        }
    }

    @Override
    public void focusChanged(boolean focused) {
        this.focused = focused;
        if (nameEditor != null) {
            nameEditor.setFocused(focused);
        }
        if (!focused) {
            finishPaneDrag();
            field.setFocused(false);
            endAddress();
            lastClick = null;
            popup = null;
        } else if (prompt != null) {
            field.setFocused(true);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return scrollFiles(x, y, dx, dy, Screen.hasControlDown());
    }

    boolean scrollFiles(double x, double y, double dx, double dy, boolean control) {
        if (dx != 0 || dy != 0) lastClick = null;
        if (nameEditor != null) return true;
        if (popup != null) {
            popup.offset = Math.clamp(popup.offset - (int) Math.signum(dy), 0, popup.items.size() - popup.visible);
            popup.cursor = Math.clamp(popup.cursor, popup.offset, popup.offset + popup.visible - 1);
            return true;
        }
        if (control && supportsLayouts() && x >= paneX() && x < paneX() + paneWidth()
                && y >= this.y + 62 && y < this.y + height - 14 && dy != 0) {
            if (Math.signum(zoomRemainder) != Math.signum(dy)) zoomRemainder = 0;
            zoomRemainder += dy;
            int clicks = (int) zoomRemainder;
            zoomRemainder -= clicks;
            setView(PcFilesView.zoom(viewStep, clicks));
            return true;
        }
        zoomRemainder = 0;
        if (inspectorVisible() && x >= inspectorX() && y >= this.y + 62 && y < this.y + height - 14) {
            inspectorScroll = Math.clamp(inspectorScroll - (int) Math.signum(dy) * 24, 0, inspectorScrollLimit);
            return dy != 0;
        }
        if (x < this.x + railWidth() && y >= this.y + 62) {
            railScroll = Math.clamp(railScroll - (int) Math.signum(dy),
                    0, Math.max(0, places().size() - railRows()));
            return dy != 0;
        }
        var layout = entryLayout();
        double delta = layout.list && dx != 0 ? dx : dy;
        scroll -= (int) Math.signum(delta) * (layout.list ? 1 : 3) * layout.stride();
        clampScroll();
        return delta != 0;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!focused) return false;
        lastClick = null;
        if (nameEditor != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                finishNameEdit(true);
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                finishNameEdit(false);
            } else {
                nameEditor.keyPressed(key, scanCode, modifiers);
            }
            return true;
        }
        if (popup != null) {
            var menu = popup;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                popup = null;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                popup = null;
                activateMenuItem(menu.items.get(menu.cursor));
            } else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                menu.cursor = Math.floorMod(menu.cursor + (key == GLFW.GLFW_KEY_UP ? -1 : 1), menu.items.size());
                menu.offset = Math.clamp(menu.offset, Math.max(0, menu.cursor - menu.visible + 1), menu.cursor);
            }
            return true;
        }
        if (prompt == null && (key == GLFW.GLFW_KEY_F4
                || key == GLFW.GLFW_KEY_D && (modifiers & GLFW.GLFW_MOD_ALT) != 0
                || key == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)) {
            beginAddress();
            return true;
        }
        if (address.isFocused()) {
            int count = visibleAddressSuggestions();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                endAddress();
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitAddress(addressChoice >= 0 && addressChoice < count
                        ? addressSuggestions.get(addressChoice) : address.getValue());
            } else if (count > 0 && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                if (addressChoice < 0 || addressChoice >= count) {
                    addressChoice = key == GLFW.GLFW_KEY_UP ? count - 1 : 0;
                } else {
                    addressChoice = Math.floorMod(addressChoice + (key == GLFW.GLFW_KEY_UP ? -1 : 1), count);
                }
            } else {
                String previous = address.getValue();
                address.keyPressed(key, scanCode, modifiers);
                if (!previous.equals(address.getValue())) completeAddress();
            }
            return true;
        }
        if (revision != drive.revision()) {
            refresh();
        }
        if (prompt == null && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            switch (key) {
                case GLFW.GLFW_KEY_T -> { newTab(); return true; }
                case GLFW.GLFW_KEY_W -> { closeTab(); return true; }
                case GLFW.GLFW_KEY_TAB -> {
                    int step = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
                    switchTab(Math.floorMod(activeTab + step, tabs.size()));
                    return true;
                }
                default -> {}
            }
        }
        if (prompt == null && key == GLFW.GLFW_KEY_F5) {
            refresh();
            return true;
        }
        if (prompt == null && key == GLFW.GLFW_KEY_P
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT)) == (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT)) {
            run(Action.DETAILS);
            return true;
        }
        if (prompt == null && key == GLFW.GLFW_KEY_P && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            togglePreview();
            return true;
        }
        int viewModifiers = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT;
        if (prompt == null && supportsLayouts() && (modifiers & viewModifiers) == viewModifiers
                && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            zoomRemainder = 0;
            setView(PcFilesView.values()[key - GLFW.GLFW_KEY_1].step);
            return true;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (field.isFocused()) {
            if (enter && prompt != null) submit();
            else if (key == GLFW.GLFW_KEY_ESCAPE) endPrompt();
            else if (enter) field.setFocused(false);
            else if (field.keyPressed(key, scanCode, modifiers)) updateQuery();
            return true;
        }
        if (enter) {
            if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) properties(); else activate();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            switch (key) {
                case GLFW.GLFW_KEY_F -> { field.setFocused(true); return true; }
                case GLFW.GLFW_KEY_C -> {
                    if (!folder.equals(BIN)) {
                        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) copyPath(); else run(Action.COPY);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X -> { if (!folder.equals(BIN)) run(Action.CUT); return true; }
                case GLFW.GLFW_KEY_V -> { if (!special()) run(Action.PASTE); return true; }
                default -> {}
            }
        }
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            if (key == GLFW.GLFW_KEY_LEFT) { run(Action.BACK); return true; }
            if (key == GLFW.GLFW_KEY_RIGHT) { run(Action.FORWARD); return true; }
            if (key == GLFW.GLFW_KEY_UP) { run(Action.UP); return true; }
        }
        if (key == GLFW.GLFW_KEY_F2 && !special()) { run(Action.RENAME); return true; }
        if (key == GLFW.GLFW_KEY_DELETE && !special()) { run(Action.DELETE); return true; }
        if (key >= GLFW.GLFW_KEY_RIGHT && key <= GLFW.GLFW_KEY_UP && !entries.isEmpty()) {
            moveSelection(key);
            return true;
        }
        return false;
    }

    private void moveSelection(int key) {
        var layout = entryLayout();
        if (!layout.list && layout.columns == 1 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) return;
        int horizontal = layout.list ? layout.stride() : 1;
        int vertical = layout.list ? 1 : layout.columns;
        int delta = switch (key) {
            case GLFW.GLFW_KEY_LEFT -> -horizontal;
            case GLFW.GLFW_KEY_RIGHT -> horizontal;
            case GLFW.GLFW_KEY_UP -> -vertical;
            default -> vertical;
        };
        if (selection == null) {
            selection = entries.getFirst().id();
        } else {
            for (int index = 0; index < rows.size(); index++) {
                var entry = rows.get(index).entry;
                if (entry == null || !entry.id().equals(selection)) continue;
                for (int next = index + delta; next >= 0 && next < rows.size(); next += Integer.signum(delta)) {
                    if (rows.get(next).entry != null) {
                        selection = rows.get(next).entry.id();
                        break;
                    }
                }
                break;
            }
        }
        reveal(selection);
    }

    private void updateQuery() {
        if (prompt == null) {
            query = field.getValue();
            scroll = 0;
            refresh();
        }
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (nameEditor != null) return nameEditor.charTyped(character, modifiers);
        if (address.isFocused()) {
            if (address.charTyped(character, modifiers)) completeAddress();
            return true;
        }
        boolean handled = field.isFocused() && field.charTyped(character, modifiers);
        if (handled) updateQuery();
        return handled;
    }
}
