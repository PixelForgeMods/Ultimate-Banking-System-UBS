package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload;
import net.austizz.ultimatebankingsystem.network.ShelfFillOpenPayload;
import net.austizz.ultimatebankingsystem.shelf.ShelfDisplayRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registry-backed admin wizard. Item names index incrementally; only visible item models render. */
public final class ShelfFillWizardScreen extends Screen {
    private static final int BACKDROP = 0xFF091521;
    private static final int SURFACE = 0xFF10263A;
    private static final int HEADER = 0xFF0C1D2D;
    private static final int ROW = 0xFF173249;
    private static final int ROW_HOVER = 0xFF21435B;
    private static final int RULE = 0xFF31546C;
    private static final int WHITE = 0xFFEAF4FA;
    private static final int MUTED = 0xFF9AB3C4;
    private static final int CYAN = 0xFF49C7F4;
    private static final int GREEN = 0xFF62DCA0;
    private static final int GOLD = 0xFFF4C66A;
    private static final int RED = 0xFFFF8585;
    private static final int ROW_HEIGHT = 43;
    private static final ResourceLocation BOXES_ICON = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/shelf_fill/boxes.png");
    private static final ResourceLocation LIST_CHECK_ICON = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/shelf_fill/list_check.png");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static ShelfFillWizardScreen current;
    private static boolean selecting;
    private static final Set<BlockPos> selectedPositions = new LinkedHashSet<>();
    private static final Set<BlockPos> priorPositions = new LinkedHashSet<>();
    private static final Set<BlockPos> readonlyPositions = Collections.unmodifiableSet(selectedPositions);
    private static boolean cancelPending;

    private enum Step { SOURCE, CATALOG, REVIEW, COMPLETE }
    private enum Modal { NONE, FILTER_HOME, FILTERS, TAG_FILTERS, INVENTORY, PRICE, OVERFLOW }
    private record CatalogItem(String id, String modId, String label, Item item) {}
    private record ModEntry(String id, String label, int count, ItemStack icon) {}
    private record TagEntry(TagKey<Item> key, int count) {}

    private final List<Item> registryItems = new ArrayList<>();
    private final List<CatalogItem> catalog = new ArrayList<>();
    private final Map<String, CatalogItem> byId = new HashMap<>();
    private final Map<String, Integer> modCounts = new HashMap<>();
    private final Map<TagKey<Item>, Integer> tagCounts = new HashMap<>();
    private final Map<String, ItemStack> creativeIcons = new HashMap<>();
    private final Map<String, Integer> modFilters = new HashMap<>();
    private final Map<TagKey<Item>, Integer> tagFilters = new HashMap<>();
    private final Map<String, Long> priceOverrides = new HashMap<>();
    private final Map<String, ItemStack> inventoryItems = new HashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Integer> inventorySlots = new ArrayList<>();
    private List<CatalogItem> visibleItems = List.of();
    private List<ModEntry> visibleMods = List.of();
    private List<ModEntry> filteredMods = List.of();
    private List<TagEntry> filteredTags = List.of();
    private List<String> reviewIds = List.of();
    private ShelfFillOpenPayload snapshot;
    private Step step = Step.SOURCE;
    private Modal modal = Modal.NONE;
    private boolean bulk;
    private boolean editingBulkItems;
    private boolean dirty = true;
    private String selectedMod = "";
    private String itemQuery = "";
    private String modQuery = "";
    private String filterQuery = "";
    private String tagQuery = "";
    private String reviewQuery = "";
    private String defaultPriceText = "";
    private String customPriceText = "";
    private String pricingItemId = "";
    private String localNotice = "";
    private int indexed;
    private int scroll;
    private int modalScroll;
    private int reviewScroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentWidth;
    private int toolbarY;
    private int listY;
    private int listHeight;
    private int footerY;
    private int modalX;
    private int modalY;
    private int modalWidth;
    private int modalHeight;
    private boolean compact;
    private DesktopEditBox defaultPriceInput;
    private DesktopEditBox customPriceInput;
    private String hoverDetail = "";
    private ItemStack hoveredStack = ItemStack.EMPTY;

    private ShelfFillWizardScreen(ShelfFillOpenPayload snapshot) {
        super(Component.literal("Shop Shelf Fill"));
        this.snapshot = snapshot;
        for (Item item : BuiltInRegistries.ITEM) registryItems.add(item);
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && !tab.getIconItem().isEmpty()) {
                creativeIcons.putIfAbsent(id.getNamespace(), tab.getIconItem().copy());
            }
        }
    }

    public static boolean isSelecting() {
        return selecting && current != null && Minecraft.getInstance().player != null;
    }

    public static Set<BlockPos> selectedPositions() { return readonlyPositions; }

    public static void togglePosition(BlockPos pos) {
        if (!selectedPositions.remove(pos)) selectedPositions.add(pos.immutable());
    }

    public static void cancelSelection() {
        if (!isSelecting()) return;
        cancelPending = true;
        selecting = false;
        PacketDistributor.sendToServer(ShelfFillActionPayload.of("BACK"));
    }

    public static void accept(ShelfFillOpenPayload payload) {
        if (payload.fresh() || current == null) {
            current = new ShelfFillWizardScreen(payload);
            selectedPositions.clear();
            priorPositions.clear();
            cancelPending = false;
        } else {
            current.snapshot = payload;
        }
        ShelfFillWizardScreen screen = current;
        selecting = payload.selecting();
        if (cancelPending && !selecting) {
            selectedPositions.clear();
            selectedPositions.addAll(priorPositions);
            cancelPending = false;
        }
        screen.localNotice = payload.notice();
        Minecraft minecraft = Minecraft.getInstance();
        if (selecting) {
            minecraft.setScreen(null);
            return;
        }
        screen.modal = Modal.NONE;
        if (payload.complete()) screen.step = Step.COMPLETE;
        else if (payload.shelves() > 0) screen.step = Step.REVIEW;
        else selectedPositions.clear();
        screen.refreshLists();
        if (minecraft.screen != screen) minecraft.setScreen(screen);
        else screen.rebuild();
    }

    @Override
    protected void init() { rebuild(); }

    private void rebuild() {
        if (defaultPriceInput != null) defaultPriceText = defaultPriceInput.getValue();
        if (customPriceInput != null) customPriceText = customPriceInput.getValue();
        clearWidgets();
        defaultPriceInput = null;
        customPriceInput = null;
        panelWidth = Math.min(820, Math.max(260, width - 14));
        panelHeight = Math.min(step == Step.SOURCE ? 390 : step == Step.COMPLETE ? 300 : 630,
                Math.max(220, height - 14));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        compact = panelWidth < 650 || (step != Step.SOURCE && panelHeight < 450);
        contentX = left + (compact ? 14 : 25);
        contentWidth = panelWidth - (compact ? 28 : 50);
        footerY = top + panelHeight - (compact ? 38 : 48);
        toolbarY = top + (step == Step.REVIEW ? compact ? 109 : 145 : compact ? 76 : 101);
        listY = toolbarY + 33;
        listHeight = Math.max(40, footerY - listY - 25);
        modalWidth = Math.min(560, panelWidth - 28);
        modalHeight = modal == Modal.FILTERS || modal == Modal.TAG_FILTERS || modal == Modal.INVENTORY
                ? Math.min(470, panelHeight - 20)
                : Math.min(242, panelHeight - 20);
        modalX = (width - modalWidth) / 2;
        modalY = (height - modalHeight) / 2;

        if (modal != Modal.NONE) {
            buildModalWidgets();
            return;
        }
        FlatButton close = button(left + panelWidth - 35, top + 14, 22, 22, "x", MUTED,
                false, this::onClose);
        close.setTooltip(Tooltip.create(Component.literal("Close shelf fill setup")));
        if (step == Step.SOURCE) buildSourceWidgets();
        else if (step == Step.CATALOG) buildCatalogWidgets();
        else if (step == Step.REVIEW) buildReviewWidgets();
        else button(contentX + contentWidth - 120, footerY, 120, 30, "Close", GREEN, true, this::onClose);
    }

    private void buildSourceWidgets() {
        int groupWidth = Math.min(contentWidth, 620);
        int groupX = left + (panelWidth - groupWidth) / 2;
        if (compact) {
            int cardHeight = Math.min(108, Math.max(82, (panelHeight - 135) / 2));
            addRenderableWidget(new SourceOption(groupX, top + 92, groupWidth, cardHeight,
                    "Entire mod", "Fill from one mod's catalog", BOXES_ICON, 96, 96,
                    CYAN, () -> chooseSource(true)));
            addRenderableWidget(new SourceOption(groupX, top + 104 + cardHeight,
                    groupWidth, cardHeight, "Choose items", "Pick products one by one",
                    LIST_CHECK_ICON, 96, 96, GREEN, () -> chooseSource(false)));
        } else {
            int cardWidth = (groupWidth - 14) / 2;
            addRenderableWidget(new SourceOption(groupX, top + 128, cardWidth, 150,
                    "Entire mod", "Fill empty slots from one mod", BOXES_ICON, 96, 96,
                    CYAN, () -> chooseSource(true)));
            addRenderableWidget(new SourceOption(groupX + cardWidth + 14, top + 128,
                    cardWidth, 150, "Choose items", "Build a custom product catalog",
                    LIST_CHECK_ICON, 96, 96, GREEN, () -> chooseSource(false)));
        }
    }

    private void buildCatalogWidgets() {
        boolean pickingMod = bulk && !editingBulkItems;
        int filterWidth = !bulk ? compact ? 72 : 108 : 0;
        int inventoryWidth = !bulk ? compact ? 88 : 108 : 0;
        int searchWidth = contentWidth - (filterWidth == 0 ? 0 : filterWidth + inventoryWidth + 20);
        DesktopEditBox search = editBox(contentX, toolbarY, searchWidth, 25,
                pickingMod ? "Search mod or modid" : "Search item or item ID",
                pickingMod ? modQuery : itemQuery);
        search.setResponder(value -> {
            if (pickingMod) modQuery = value;
            else itemQuery = value;
            scroll = 0;
            dirty = true;
        });
        if (!bulk) {
            button(contentX + searchWidth + 10, toolbarY, inventoryWidth, 25,
                    compact ? "My items" : "Inventory", GREEN, false, () -> openModal(Modal.INVENTORY));
            button(contentX + contentWidth - filterWidth, toolbarY,
                    filterWidth, 25, "Filter", CYAN, false,
                    () -> openModal(Modal.FILTER_HOME));
        }
        button(contentX, footerY, compact ? 65 : 94, 30, "Back", MUTED, false,
                () -> {
                    step = editingBulkItems || snapshot.shelves() > 0 ? Step.REVIEW : Step.SOURCE;
                    editingBulkItems = false;
                    rebuild();
                });
        if (!pickingMod) button(contentX + contentWidth - (compact ? 140 : 170), footerY,
                compact ? 140 : 170, 30, snapshot.shelves() > 0 ? "Return to review" : "Select shelves",
                GREEN, true, this::continueFromCatalog);
    }

    private void buildReviewWidgets() {
        int priceWidth = compact ? Math.min(118, contentWidth / 2) : 190;
        int searchWidth = contentWidth - priceWidth - 10;
        DesktopEditBox search = editBox(contentX, toolbarY, searchWidth, 25,
                "Search selected items", reviewQuery);
        search.setResponder(value -> { reviewQuery = value; reviewScroll = 0; dirty = true; });
        defaultPriceInput = editBox(contentX + searchWidth + 10, toolbarY, priceWidth, 25,
                "Default price ($)", defaultPriceText);
        defaultPriceInput.setMaxLength(18);
        defaultPriceInput.setResponder(value -> {
            defaultPriceText = value;
            localNotice = "";
        });
        if (compact) {
            button(contentX, footerY, 58, 30, "Back", MUTED, false,
                    () -> { step = Step.SOURCE; rebuild(); });
            button(contentX + 63, footerY, 54, 30, "Edit", CYAN, false, this::editItems);
            button(contentX + contentWidth - 94, footerY, 94, 30, "Fill", GREEN, true,
                    this::requestFill);
            button(contentX + contentWidth - 82, top + 62, 82, 20, "Shelves", GOLD, false,
                    this::beginSelection);
        } else {
            button(contentX, footerY, 94, 30, "Source", MUTED, false,
                    () -> { step = Step.SOURCE; rebuild(); });
            button(contentX + 103, footerY, 126, 30, "Adjust items", CYAN, false,
                    this::editItems);
            button(contentX + 238, footerY, 136, 30, "Select shelves", GOLD, false,
                    this::beginSelection);
            button(contentX + contentWidth - 154, footerY, 154, 30,
                    "Fill empty slots", GREEN, true, this::requestFill);
        }
    }

    private void buildModalWidgets() {
        int innerX = modalX + 20;
        int innerWidth = modalWidth - 40;
        if (modal == Modal.FILTER_HOME) {
            button(innerX, modalY + 55, innerWidth, 43,
                    "Mods  (" + activeCount(modFilters) + ")", CYAN, false,
                    () -> openModal(Modal.FILTERS));
            button(innerX, modalY + 108, innerWidth, 43,
                    "Item tags  (" + activeCount(tagFilters) + ")", GREEN, false,
                    () -> openModal(Modal.TAG_FILTERS));
            button(innerX + innerWidth - 92, modalY + modalHeight - 42, 92, 28,
                    "Done", GREEN, true, this::closeModal);
        } else if (modal == Modal.FILTERS) {
            DesktopEditBox search = editBox(innerX, modalY + 53, innerWidth, 25,
                    "Search mod or modid", filterQuery);
            search.setResponder(value -> { filterQuery = value; modalScroll = 0; dirty = true; });
            button(innerX, modalY + modalHeight - 42, 82, 28, "Reset", MUTED, false,
                    () -> { modFilters.clear(); dirty = true; });
            button(innerX + innerWidth - 92, modalY + modalHeight - 42, 92, 28,
                    "Done", GREEN, true, this::backToFilterHome);
        } else if (modal == Modal.TAG_FILTERS) {
            DesktopEditBox search = editBox(innerX, modalY + 53, innerWidth, 25,
                    "Search item tag or namespace", tagQuery);
            search.setResponder(value -> { tagQuery = value; modalScroll = 0; dirty = true; });
            button(innerX, modalY + modalHeight - 42, 82, 28, "Reset", MUTED, false,
                    () -> { tagFilters.clear(); dirty = true; });
            button(innerX + innerWidth - 92, modalY + modalHeight - 42, 92, 28,
                    "Done", GREEN, true, this::backToFilterHome);
        } else if (modal == Modal.INVENTORY) {
            button(innerX + innerWidth - 92, modalY + modalHeight - 42, 92, 28,
                    "Done", GREEN, true, this::closeModal);
        } else if (modal == Modal.PRICE) {
            customPriceInput = editBox(innerX, modalY + 115, innerWidth, 28,
                    "Price ($)", customPriceText);
            customPriceInput.setMaxLength(18);
            button(innerX, modalY + modalHeight - 43, 96, 28, "Cancel", MUTED, false,
                    this::closeModal);
            button(innerX + 104, modalY + modalHeight - 43, 110, 28,
                    "Use default", CYAN, false, () -> {
                        priceOverrides.remove(pricingItemId);
                        closeModal();
                    });
            button(innerX + innerWidth - 104, modalY + modalHeight - 43, 104, 28,
                    "Save price", GREEN, true, this::saveItemPrice);
        } else if (modal == Modal.OVERFLOW) {
            button(innerX, modalY + modalHeight - 43, 108, 28, "Go back", MUTED, false,
                    this::closeModal);
            button(innerX + innerWidth - 158, modalY + modalHeight - 43, 158, 28,
                    "Fill what fits", GOLD, true, () -> {
                        closeModal();
                        sendApply(true);
                    });
        }
    }

    private DesktopEditBox editBox(int x, int y, int w, int h, String hint, String value) {
        DesktopEditBox box = new DesktopEditBox(font, x, y, Math.max(40, w), h, Component.literal(hint));
        box.setMaxLength(100);
        box.setHint(Component.literal(hint));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private FlatButton button(int x, int y, int w, int h, String label, int accent,
                              boolean filled, Runnable action) {
        return addRenderableWidget(new FlatButton(x, y, Math.max(22, w), h,
                Component.literal(label), accent, filled, action));
    }

    private void chooseSource(boolean bulk) {
        this.bulk = bulk;
        selected.clear();
        inventoryItems.clear();
        priceOverrides.clear();
        selectedMod = "";
        editingBulkItems = false;
        localNotice = "";
        scroll = 0;
        step = Step.CATALOG;
        dirty = true;
        rebuild();
    }

    private void chooseBulkMod(ModEntry mod) {
        if (indexed < registryItems.size()) {
            localNotice = "Finishing the mod catalog before selecting all its items.";
            return;
        }
        selectedMod = mod.id();
        selected.clear();
        priceOverrides.clear();
        catalog.stream().filter(item -> item.modId().equals(selectedMod))
                .sorted(Comparator.comparing(CatalogItem::id))
                .forEach(item -> selected.add(item.id()));
        refreshLists();
        localNotice = "";
        if (snapshot.shelves() > 0) { step = Step.REVIEW; rebuild(); }
        else beginSelection();
    }

    private void continueFromCatalog() {
        if (selected.isEmpty()) { localNotice = "Select at least one item."; return; }
        localNotice = "";
        editingBulkItems = false;
        if (snapshot.shelves() > 0) { step = Step.REVIEW; rebuild(); }
        else beginSelection();
    }

    private void editItems() {
        step = Step.CATALOG;
        editingBulkItems = bulk;
        scroll = 0;
        dirty = true;
        rebuild();
    }

    private void beginSelection() {
        step = Step.REVIEW;
        priorPositions.clear();
        priorPositions.addAll(selectedPositions);
        PacketDistributor.sendToServer(ShelfFillActionPayload.of("BEGIN"));
    }

    private void requestFill() {
        if (defaultPriceInput != null) defaultPriceText = defaultPriceInput.getValue();
        if (selected.isEmpty()) { localNotice = "Choose at least one item."; return; }
        if (snapshot.freeSlots() <= 0) { localNotice = "No empty display slots remain."; return; }
        if (!bulk && selected.size() > 2048) {
            localNotice = "Manual batches are limited to 2,048 items.";
            return;
        }
        long defaultPrice;
        try {
            defaultPrice = defaultPriceText.isBlank() ? 0L : parsePrice(defaultPriceText);
        } catch (RuntimeException exception) {
            localNotice = "Enter a valid default price from $0.01 to $1,000,000,000.00.";
            return;
        }
        int filling = Math.min(selected.size(), snapshot.freeSlots());
        int checked = 0;
        for (String id : orderedSelection()) {
            if (checked++ >= filling) break;
            if (priceOverrides.getOrDefault(id, defaultPrice) < 1) {
                localNotice = "Set a default or individual price for " + id + ".";
                return;
            }
        }
        localNotice = "";
        if (selected.size() > snapshot.freeSlots()) openModal(Modal.OVERFLOW);
        else sendApply(false);
    }

    private void sendApply(boolean force) {
        long defaultPrice = defaultPriceText.isBlank() ? 0L : parsePrice(defaultPriceText);
        List<String> itemIds;
        if (bulk) {
            itemIds = catalog.stream().filter(item -> item.modId().equals(selectedMod)
                    && !selected.contains(item.id())).map(CatalogItem::id).toList();
        } else itemIds = new ArrayList<>(selected);
        if (itemIds.size() > 2048 || priceOverrides.size() > 2048) {
            localNotice = "Too many item changes for one batch. Reduce the selection.";
            return;
        }
        List<ShelfFillActionPayload.PriceOverride> prices = priceOverrides.entrySet().stream()
                .filter(entry -> selected.contains(entry.getKey()))
                .map(entry -> new ShelfFillActionPayload.PriceOverride(entry.getKey(), entry.getValue()))
                .toList();
        List<ShelfFillActionPayload.InventoryChoice> inventoryChoices = new ArrayList<>();
        if (!bulk) for (String key : selected) {
            ItemStack stack = inventoryItems.get(key);
            if (stack != null) inventoryChoices.add(new ShelfFillActionPayload.InventoryChoice(
                    Integer.parseInt(key.substring("@inventory:".length())), stack));
        }
        PacketDistributor.sendToServer(new ShelfFillActionPayload("APPLY", 0, 0, 0, bulk,
                force, bulk ? selectedMod : "", defaultPrice, itemIds, prices, inventoryChoices));
    }

    private static long parsePrice(String text) {
        long cents = new BigDecimal(text.trim()).movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        if (cents < 1 || cents > 1_000_000_000_00L) throw new IllegalArgumentException("Invalid price");
        return cents;
    }

    private void saveItemPrice() {
        if (customPriceInput != null) customPriceText = customPriceInput.getValue();
        try {
            priceOverrides.put(pricingItemId, parsePrice(customPriceText));
            localNotice = "";
            closeModal();
        } catch (RuntimeException exception) {
            localNotice = "Enter a price from $0.01 to $1,000,000,000.00.";
        }
    }

    private void openItemPrice(String id) {
        pricingItemId = id;
        customPriceText = priceOverrides.containsKey(id) ? priceText(priceOverrides.get(id))
                : defaultPriceText;
        openModal(Modal.PRICE);
    }

    private void openModal(Modal next) {
        modal = next;
        modalScroll = 0;
        if (next == Modal.INVENTORY) {
            inventorySlots.clear();
            if (minecraft.player != null) for (int slot = 0;
                    slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                if (!minecraft.player.getInventory().getItem(slot).isEmpty()) inventorySlots.add(slot);
            }
        }
        rebuild();
    }
    private void closeModal() { modal = Modal.NONE; rebuild(); }
    private void backToFilterHome() { openModal(Modal.FILTER_HOME); }

    private static int activeCount(Map<?, Integer> filters) {
        return (int) filters.values().stream().filter(value -> value != 0).count();
    }

    @Override
    public void tick() {
        super.tick();
        int processed = 0;
        while (indexed < registryItems.size() && processed++ < 128) {
            Item item = registryItems.get(indexed++);
            ItemStack stack = new ItemStack(item);
            if (!ShelfDisplayRules.isAllowedDisplayItem(stack)) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String label;
            try { label = stack.getHoverName().getString(); }
            catch (RuntimeException exception) { label = id.getPath(); }
            CatalogItem entry = new CatalogItem(id.toString(), id.getNamespace(), label, item);
            catalog.add(entry);
            byId.put(entry.id(), entry);
            modCounts.merge(entry.modId(), 1, Integer::sum);
            stack.getTags().forEach(tag -> tagCounts.merge(tag, 1, Integer::sum));
            dirty = true;
        }
        if (dirty) refreshLists();
    }

    private void refreshLists() {
        dirty = false;
        List<ModEntry> mods = new ArrayList<>();
        for (var entry : modCounts.entrySet()) {
            String label = ModList.get().getModContainerById(entry.getKey())
                    .map(container -> container.getModInfo().getDisplayName()).orElse(entry.getKey());
            mods.add(new ModEntry(entry.getKey(), label, entry.getValue(),
                    creativeIcons.getOrDefault(entry.getKey(), new ItemStack(Items.BOOK))));
        }
        mods.sort(Comparator.comparing(ModEntry::label, String.CASE_INSENSITIVE_ORDER));
        visibleMods = mods.stream().filter(mod -> matches(mod.label(), mod.id(), modQuery)).toList();
        filteredMods = mods.stream().filter(mod -> matches(mod.label(), mod.id(), filterQuery)).toList();
        String search = tagQuery.trim();
        String tagSearch = search.startsWith("#") ? search.substring(1) : search;
        filteredTags = tagCounts.entrySet().stream()
                .map(entry -> new TagEntry(entry.getKey(), entry.getValue()))
                .filter(tag -> matches(tag.key().location().getPath().replace('/', ' ').replace('_', ' '),
                        tag.key().location().toString(), tagSearch))
                .sorted(Comparator.comparing(tag -> tag.key().location().toString()))
                .toList();

        boolean hasIncludes = modFilters.containsValue(1);
        visibleItems = catalog.stream().filter(item -> {
            if (bulk && !item.modId().equals(selectedMod)) return false;
            if (!bulk) {
                int filter = modFilters.getOrDefault(item.modId(), 0);
                if (filter == 2 || (hasIncludes && filter != 1)) return false;
                if (!tagFilters.isEmpty()) {
                    ItemStack stack = new ItemStack(item.item());
                    if (!ShelfCatalogFilters.matches(tagFilters, stack::is)) return false;
                }
            }
            return matches(item.label(), item.id(), itemQuery);
        }).toList();
        reviewIds = orderedSelection().stream().filter(id -> {
            CatalogItem item = byId.get(id);
            ItemStack stack = inventoryItems.get(id);
            return (item != null && matches(item.label(), id, reviewQuery))
                    || (stack != null && matches(stack.getHoverName().getString(), id, reviewQuery));
        }).toList();
    }

    private List<String> orderedSelection() {
        List<String> ids = new ArrayList<>(selected);
        if (bulk) ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private static boolean matches(String label, String id, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || label.toLowerCase(Locale.ROOT).contains(needle)
                || id.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverDetail = "";
        hoveredStack = ItemStack.EMPTY;
        graphics.fill(0, 0, width, height, BACKDROP);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, RULE);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, SURFACE);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 58, HEADER);
        graphics.fill(left + 1, top + 58, left + panelWidth - 1, top + 59, RULE);
        int activeStep = switch (step) {
            case SOURCE -> 1;
            case CATALOG -> 2;
            case REVIEW -> 4;
            case COMPLETE -> 4;
        };
        int segmentWidth = (contentWidth - 18) / 4;
        for (int i = 0; i < 4; i++) {
            int segmentX = contentX + i * (segmentWidth + 6);
            graphics.fill(segmentX, top + 57, segmentX + segmentWidth, top + 59,
                    i < activeStep ? step == Step.COMPLETE ? GREEN : CYAN : RULE);
        }
        draw(graphics, "SHOP SHELF FILL", contentX, top + 13, WHITE);
        drawFit(graphics, stepLabel(), contentX, top + 34, contentWidth - 50, MUTED);
        if (!compact) draw(graphics, progressLabel(), left + panelWidth - 142, top + 17, CYAN);
        if (step == Step.SOURCE) renderSource(graphics);
        else if (step == Step.CATALOG) renderCatalog(graphics, mouseX, mouseY);
        else if (step == Step.REVIEW) renderReview(graphics, mouseX, mouseY);
        else renderComplete(graphics);
        if (modal != Modal.NONE) {
            hoverDetail = "";
            hoveredStack = ItemStack.EMPTY;
            renderModal(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hoveredStack.isEmpty()) graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        else if (!hoverDetail.isEmpty()) graphics.renderTooltip(font, Component.literal(hoverDetail), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The wizard is opaque and does not use inherited background blur.
    }

    private String stepLabel() {
        return switch (step) {
            case SOURCE -> "Choose a catalog source";
            case CATALOG -> bulk && !editingBulkItems ? "Select one mod" : "Choose display items";
            case REVIEW -> selected.size() + " items  /  " + snapshot.shelves() + " shelves  /  "
                    + snapshot.freeSlots() + " empty slots";
            case COMPLETE -> "Batch complete";
        };
    }

    private String progressLabel() {
        return switch (step) {
            case SOURCE -> "01  /  04";
            case CATALOG -> "02  /  04";
            case REVIEW -> "04  /  04";
            case COMPLETE -> "DONE";
        };
    }

    private void renderSource(GuiGraphics graphics) {
        int groupWidth = Math.min(contentWidth, 620);
        int groupX = left + (panelWidth - groupWidth) / 2;
        draw(graphics, "BUILD A DISPLAY CATALOG", groupX, top + (compact ? 73 : 85), CYAN);
        if (!compact) draw(graphics, "Choose a starting point", groupX, top + 105, MUTED);
    }

    private void renderCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean pickingMod = bulk && !editingBulkItems;
        String label = pickingMod ? "AVAILABLE MODS" : "ITEM CATALOG";
        draw(graphics, label, contentX, toolbarY - 14, CYAN);
        int total = pickingMod ? visibleMods.size() : visibleItems.size();
        draw(graphics, total + " results", contentX + contentWidth - 70, toolbarY - 14, MUTED);
        renderListBackground(graphics, contentX, listY, contentWidth, listHeight);
        if (pickingMod) renderModRows(graphics, visibleMods, contentX, listY, contentWidth,
                listHeight, scroll, false, mouseX, mouseY);
        else renderItemRows(graphics, mouseX, mouseY);
        if (total == 0 && indexed == registryItems.size()) {
            graphics.drawCenteredString(font, "No matching items", contentX + contentWidth / 2,
                    listY + listHeight / 2, MUTED);
        }
        if (indexed < registryItems.size()) drawStatus(graphics,
                "Indexing " + indexed + " / " + registryItems.size() + " registry items", GOLD);
        else drawStatus(graphics, localNotice.isBlank() ? pickingMod
                ? "Selecting a mod continues to shelf selection." : selected.size() + " items selected"
                : localNotice, localNotice.isBlank() ? MUTED : GOLD);
    }

    private void renderReview(GuiGraphics graphics, int mouseX, int mouseY) {
        int fit = Math.min(selected.size(), snapshot.freeSlots());
        int overflow = Math.max(0, selected.size() - snapshot.freeSlots());
        drawFit(graphics, bulk ? selectedMod.toUpperCase(Locale.ROOT) : "CUSTOM CATALOG",
                contentX, top + (compact ? 64 : 80), contentWidth - (compact ? 90 : 0), CYAN);
        drawFit(graphics, fit + " items will fill  /  " + overflow + " unplaced",
                contentX, top + (compact ? 78 : 97), contentWidth - (compact ? 85 : 0),
                overflow > 0 ? GOLD : GREEN);
        if (!compact) {
            int barY = top + 113;
            graphics.fill(contentX, barY, contentX + contentWidth, barY + 3, RULE);
            if (snapshot.freeSlots() > 0) graphics.fill(contentX, barY,
                    contentX + (int) (contentWidth * Math.min(1.0,
                            (double) fit / snapshot.freeSlots())), barY + 3, GREEN);
        }
        draw(graphics, "ITEMS", contentX, toolbarY - 14, CYAN);
        draw(graphics, "DEFAULT PRICE ($)", contentX + contentWidth - (compact ? 118 : 190),
                toolbarY - 14, MUTED);
        renderListBackground(graphics, contentX, listY, contentWidth, listHeight);
        renderReviewRows(graphics, mouseX, mouseY);
        drawStatus(graphics, localNotice.isBlank()
                ? "Click an item to set its price. Only empty display slots change."
                : localNotice, localNotice.isBlank() ? MUTED : GOLD);
    }

    private void renderComplete(GuiGraphics graphics) {
        int bandWidth = Math.min(contentWidth, 600);
        int x = left + (panelWidth - bandWidth) / 2;
        int y = top + 96;
        graphics.fill(x, y, x + bandWidth, y + 116, ROW);
        graphics.fill(x, y, x + 3, y + 116, GREEN);
        graphics.fill(x + 14, y + 16, x + 54, y + 56, HEADER);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 18, y + 20, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.renderItem(new ItemStack(Items.EMERALD), 0, 0);
        graphics.pose().popPose();
        draw(graphics, "SHELVES UPDATED", x + 68, y + 18, GREEN);
        drawWrapped(graphics, snapshot.notice(), x + 68, y + 42,
                bandWidth - 86, 5, WHITE);
    }

    private void renderModal(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xBB030B13);
        graphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, RULE);
        graphics.fill(modalX + 1, modalY + 1, modalX + modalWidth - 1,
                modalY + modalHeight - 1, SURFACE);
        graphics.fill(modalX + 1, modalY + 1, modalX + modalWidth - 1, modalY + 40, HEADER);
        String title = switch (modal) {
            case FILTER_HOME -> "FILTER ITEMS";
            case FILTERS -> "FILTER MODS";
            case TAG_FILTERS -> "FILTER ITEM TAGS";
            case INVENTORY -> "SELECT FROM INVENTORY";
            case PRICE -> "ITEM PRICE";
            case OVERFLOW -> "NOT ENOUGH SHELF SPACE";
            default -> "";
        };
        draw(graphics, title, modalX + 20, modalY + 15, WHITE);
        if (modal == Modal.FILTERS) {
            int listTop = modalY + 88;
            int listH = modalHeight - 141;
            renderListBackground(graphics, modalX + 20, listTop, modalWidth - 40, listH);
            renderModRows(graphics, filteredMods, modalX + 20, listTop, modalWidth - 40,
                    listH, modalScroll, true, mouseX, mouseY);
        } else if (modal == Modal.TAG_FILTERS) {
            int listTop = modalY + 88;
            int listH = modalHeight - 141;
            renderListBackground(graphics, modalX + 20, listTop, modalWidth - 40, listH);
            renderTagRows(graphics, modalX + 20, listTop, modalWidth - 40,
                    listH, mouseX, mouseY);
            if (filteredTags.isEmpty() && indexed == registryItems.size()) {
                graphics.drawCenteredString(font, "No matching item tags",
                        modalX + modalWidth / 2, listTop + listH / 2, MUTED);
            }
        } else if (modal == Modal.INVENTORY) {
            drawWrapped(graphics, "Select a stack to use its item and custom data as the display template.",
                    modalX + 20, modalY + 49, modalWidth - 40, 2, MUTED);
            int listTop = modalY + 78;
            int listH = modalHeight - 131;
            renderListBackground(graphics, modalX + 20, listTop, modalWidth - 40, listH);
            renderInventoryRows(graphics, modalX + 20, listTop, modalWidth - 40,
                    listH, mouseX, mouseY);
            if (inventorySlots.isEmpty()) graphics.drawCenteredString(font, "Inventory is empty",
                    modalX + modalWidth / 2, listTop + listH / 2, MUTED);
        } else if (modal == Modal.PRICE) {
            CatalogItem item = byId.get(pricingItemId);
            ItemStack stack = inventoryItems.get(pricingItemId);
            if (item != null || stack != null) graphics.renderItem(
                    stack != null ? stack : new ItemStack(item.item()), modalX + 22, modalY + 57);
            drawFit(graphics, stack != null ? stack.getHoverName().getString()
                    : item == null ? pricingItemId : item.label(), modalX + 49,
                    modalY + 52, modalWidth - 70, WHITE);
            drawFit(graphics, pricingItemId.startsWith("@inventory:")
                    ? "From inventory, slot " + (Integer.parseInt(pricingItemId.substring("@inventory:".length())) + 1)
                    : pricingItemId, modalX + 49, modalY + 68, modalWidth - 70, MUTED);
            draw(graphics, "PRICE PER ITEM ($)", modalX + 20, modalY + 98, CYAN);
            if (!localNotice.isBlank()) drawFit(graphics, localNotice, modalX + 20,
                    modalY + modalHeight - 62, modalWidth - 40, GOLD);
        } else if (modal == Modal.OVERFLOW) {
            int remaining = selected.size() - snapshot.freeSlots();
            draw(graphics, snapshot.freeSlots() + " EMPTY SLOTS", modalX + 20, modalY + 57, GREEN);
            draw(graphics, selected.size() + " SELECTED ITEMS", modalX + 20, modalY + 79, WHITE);
            drawWrapped(graphics, "Fill the first " + snapshot.freeSlots() + " items in receipt order? "
                    + remaining + " items will not be placed. Occupied slots will not change.",
                    modalX + 20, modalY + 110, modalWidth - 40, 4, MUTED);
        }
    }

    private void renderModRows(GuiGraphics graphics, List<ModEntry> mods, int x, int y,
                               int w, int h, int offset, boolean filter, int mouseX, int mouseY) {
        int rows = Math.max(1, h / ROW_HEIGHT);
        int start = Math.min(offset, Math.max(0, mods.size() - rows));
        if (filter) modalScroll = start;
        else scroll = start;
        graphics.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < rows && start + i < mods.size(); i++) {
            ModEntry mod = mods.get(start + i);
            int rowY = y + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY
                    && mouseY < rowY + ROW_HEIGHT;
            drawRow(graphics, x, rowY, w, hovered);
            graphics.fill(x + 7, rowY + 7, x + 33, rowY + 35, HEADER);
            if (!mod.icon().isEmpty()) graphics.renderItem(mod.icon(), x + 12, rowY + 13);
            int badgeWidth = filter ? 72 : 80;
            drawFit(graphics, mod.label(), x + 42, rowY + 7, w - badgeWidth - 54, WHITE);
            drawFit(graphics, mod.id(), x + 42, rowY + 23, w - badgeWidth - 54, MUTED);
            if (filter) {
                int state = modFilters.getOrDefault(mod.id(), 0);
                draw(graphics, state == 1 ? "INCLUDE" : state == 2 ? "EXCLUDE" : "ALL",
                        x + w - badgeWidth, rowY + 17, state == 1 ? GREEN : state == 2 ? RED : MUTED);
            } else {
                draw(graphics, mod.count() + " items", x + w - badgeWidth,
                        rowY + 17, CYAN);
            }
            if (hovered) hoverDetail = filter
                    ? mod.label() + " - click to cycle All / Include / Exclude"
                    : mod.label() + " (" + mod.id() + ")";
        }
        graphics.disableScissor();
    }

    private void renderTagRows(GuiGraphics graphics, int x, int y, int w, int h,
                               int mouseX, int mouseY) {
        int rows = Math.max(1, h / ROW_HEIGHT);
        modalScroll = Math.min(modalScroll, Math.max(0, filteredTags.size() - rows));
        graphics.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < rows && modalScroll + i < filteredTags.size(); i++) {
            TagEntry tag = filteredTags.get(modalScroll + i);
            int rowY = y + i * ROW_HEIGHT;
            boolean hovered = overRow(mouseX, mouseY, x, rowY, w);
            drawRow(graphics, x, rowY, w, hovered);
            graphics.fill(x + 7, rowY + 7, x + 33, rowY + 35, HEADER);
            draw(graphics, "#", x + 15, rowY + 17, CYAN);
            ResourceLocation id = tag.key().location();
            drawFit(graphics, id.getPath().replace('/', ' ').replace('_', ' '),
                    x + 42, rowY + 7, w - 130, WHITE);
            drawFit(graphics, "#" + id, x + 42, rowY + 23, w - 130, MUTED);
            int state = tagFilters.getOrDefault(tag.key(), 0);
            draw(graphics, state == 1 ? "INCLUDE" : state == 2 ? "EXCLUDE" : "ALL",
                    x + w - 82, rowY + 9, state == 1 ? GREEN : state == 2 ? RED : MUTED);
            draw(graphics, tag.count() + " items", x + w - 82, rowY + 25, MUTED);
            if (hovered) hoverDetail = "#" + id
                    + " - click to cycle All / Include / Exclude. Includes match any; excludes win.";
        }
        graphics.disableScissor();
    }

    private void renderItemRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = Math.max(1, listHeight / ROW_HEIGHT);
        scroll = Math.min(scroll, Math.max(0, visibleItems.size() - rows));
        graphics.enableScissor(contentX, listY, contentX + contentWidth, listY + listHeight);
        for (int i = 0; i < rows && scroll + i < visibleItems.size(); i++) {
            CatalogItem item = visibleItems.get(scroll + i);
            int y = listY + i * ROW_HEIGHT;
            boolean hovered = overRow(mouseX, mouseY, contentX, y, contentWidth);
            drawRow(graphics, contentX, y, contentWidth, hovered);
            graphics.fill(contentX + 7, y + 7, contentX + 33, y + 35, HEADER);
            graphics.renderItem(new ItemStack(item.item()), contentX + 12, y + 13);
            drawFit(graphics, item.label(), contentX + 42, y + 7, contentWidth - 104, WHITE);
            drawFit(graphics, item.id(), contentX + 42, y + 23, contentWidth - 104, MUTED);
            draw(graphics, selected.contains(item.id()) ? "SELECTED" : "ADD",
                    contentX + contentWidth - 70, y + 17, selected.contains(item.id()) ? GREEN : CYAN);
            if (hovered) hoverDetail = item.label() + " (" + item.id() + ")";
        }
        graphics.disableScissor();
    }

    private void renderInventoryRows(GuiGraphics graphics, int x, int y, int w, int h,
                                     int mouseX, int mouseY) {
        int rows = Math.max(1, h / ROW_HEIGHT);
        modalScroll = Math.min(modalScroll, Math.max(0, inventorySlots.size() - rows));
        graphics.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < rows && modalScroll + i < inventorySlots.size(); i++) {
            int slot = inventorySlots.get(modalScroll + i);
            ItemStack stack = minecraft.player == null ? ItemStack.EMPTY
                    : minecraft.player.getInventory().getItem(slot);
            String key = ShelfFillActionPayload.inventoryKey(slot);
            int rowY = y + i * ROW_HEIGHT;
            boolean hovered = overRow(mouseX, mouseY, x, rowY, w);
            drawRow(graphics, x, rowY, w, hovered);
            graphics.fill(x + 7, rowY + 7, x + 33, rowY + 35, HEADER);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 12, rowY + 13);
                graphics.renderItemDecorations(font, stack, x + 12, rowY + 13);
            }
            drawFit(graphics, stack.isEmpty() ? "Empty slot" : stack.getHoverName().getString(),
                    x + 42, rowY + 7, w - 125, WHITE);
            drawFit(graphics, slot < 9 ? "Hotbar " + (slot + 1)
                    : slot < 36 ? "Inventory " + (slot - 8)
                    : slot < 40 ? "Armor" : "Offhand", x + 42, rowY + 23, w - 125, MUTED);
            boolean allowed = ShelfDisplayRules.isAllowedDisplayItem(stack);
            draw(graphics, selected.contains(key) ? "SELECTED" : allowed ? "ADD" : "BLOCKED",
                    x + w - 82, rowY + 17, selected.contains(key) ? GREEN : allowed ? CYAN : RED);
            if (hovered) {
                hoveredStack = stack;
                if (!allowed) hoverDetail = ShelfDisplayRules.blockedReason(stack);
            }
        }
        graphics.disableScissor();
    }

    private void renderReviewRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = Math.max(1, listHeight / ROW_HEIGHT);
        reviewScroll = Math.min(reviewScroll, Math.max(0, reviewIds.size() - rows));
        List<String> order = orderedSelection();
        graphics.enableScissor(contentX, listY, contentX + contentWidth, listY + listHeight);
        for (int i = 0; i < rows && reviewScroll + i < reviewIds.size(); i++) {
            String id = reviewIds.get(reviewScroll + i);
            CatalogItem item = byId.get(id);
            ItemStack stack = inventoryItems.get(id);
            int y = listY + i * ROW_HEIGHT;
            int position = order.indexOf(id);
            boolean willFit = position < snapshot.freeSlots();
            boolean hovered = overRow(mouseX, mouseY, contentX, y, contentWidth);
            drawRow(graphics, contentX, y, contentWidth, hovered);
            draw(graphics, String.valueOf(position + 1), contentX + 8, y + 17,
                    willFit ? CYAN : GOLD);
            graphics.fill(contentX + 26, y + 7, contentX + 52, y + 35, HEADER);
            if (item != null || stack != null) graphics.renderItem(
                    stack != null ? stack : new ItemStack(item.item()), contentX + 31, y + 13);
            int priceArea = compact ? 88 : 140;
            String label = stack != null ? stack.getHoverName().getString()
                    : item == null ? id : item.label();
            drawFit(graphics, label, contentX + 59,
                    y + 7, contentWidth - priceArea - 70, WHITE);
            drawFit(graphics, stack == null ? id : "From inventory slot "
                    + (Integer.parseInt(id.substring("@inventory:".length())) + 1) + " | "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem()), contentX + 59, y + 23,
                    contentWidth - priceArea - 70, MUTED);
            long price = priceOverrides.getOrDefault(id, defaultPriceOrZero());
            drawFit(graphics, price > 0 ? formatMoney(price) : "Set price",
                    contentX + contentWidth - priceArea, y + 16, priceArea - 31,
                    priceOverrides.containsKey(id) ? GREEN : price > 0 ? WHITE : GOLD);
            draw(graphics, "x", contentX + contentWidth - 20, y + 16, RED);
            if (hovered) {
                if (stack != null) hoveredStack = stack;
                else hoverDetail = label + (willFit ? " - will fill" : " - unplaced");
            }
        }
        graphics.disableScissor();
    }

    private static boolean overRow(int mx, int my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + ROW_HEIGHT;
    }

    private void renderListBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, HEADER);
        graphics.fill(x, y, x + w, y + 1, RULE);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, int w, boolean hovered) {
        graphics.fill(x + 1, y + 1, x + w - 1, y + ROW_HEIGHT - 1,
                hovered ? ROW_HOVER : ROW);
        graphics.fill(x + 1, y + ROW_HEIGHT - 1, x + w - 1, y + ROW_HEIGHT, RULE);
    }

    private long defaultPriceOrZero() {
        try { return defaultPriceText.isBlank() ? 0L : parsePrice(defaultPriceText); }
        catch (RuntimeException exception) { return 0L; }
    }

    private static String priceText(long cents) {
        return BigDecimal.valueOf(cents, 2).setScale(2).toPlainString();
    }

    private static String formatMoney(long cents) {
        return "$" + MONEY.format(BigDecimal.valueOf(cents, 2));
    }

    private void drawStatus(GuiGraphics graphics, String message, int color) {
        drawFit(graphics, message, contentX, footerY - 17, contentWidth, color);
    }

    private void draw(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, false);
    }

    private void drawFit(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        String fitted = font.width(value) <= maxWidth ? value
                : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
        draw(graphics, fitted, x, y, color);
    }

    private void drawWrapped(GuiGraphics graphics, String value, int x, int y,
                             int maxWidth, int maxLines, int color) {
        int line = 0;
        for (var text : font.split(Component.literal(value), maxWidth)) {
            if (line++ >= maxLines) break;
            graphics.drawString(font, text, x, y + (line - 1) * 12, color, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int delta = vertical > 0 ? -3 : 3;
        if (modal == Modal.INVENTORY) {
            int y = modalY + 78;
            if (mouseX >= modalX + 20 && mouseX < modalX + modalWidth - 20
                    && mouseY >= y && mouseY < y + modalHeight - 131) {
                modalScroll = Math.max(0, modalScroll + delta);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }
        if (modal == Modal.FILTERS || modal == Modal.TAG_FILTERS) {
            int y = modalY + 88;
            if (mouseX >= modalX + 20 && mouseX < modalX + modalWidth - 20
                    && mouseY >= y && mouseY < y + modalHeight - 141) {
                modalScroll = Math.max(0, modalScroll + delta);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }
        if (modal != Modal.NONE) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if (mouseX >= contentX && mouseX < contentX + contentWidth
                && mouseY >= listY && mouseY < listY + listHeight) {
            if (step == Step.CATALOG) scroll = Math.max(0, scroll + delta);
            if (step == Step.REVIEW) reviewScroll = Math.max(0, reviewScroll + delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && modal == Modal.INVENTORY) {
            int y = modalY + 78;
            int x = modalX + 20;
            int w = modalWidth - 40;
            int h = modalHeight - 131;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                int row = modalScroll + ((int) mouseY - y) / ROW_HEIGHT;
                if (row - modalScroll < Math.max(1, h / ROW_HEIGHT) && row < inventorySlots.size()
                        && minecraft.player != null) {
                    int slot = inventorySlots.get(row);
                    String key = ShelfFillActionPayload.inventoryKey(slot);
                    if (selected.remove(key)) {
                        inventoryItems.remove(key);
                        priceOverrides.remove(key);
                    } else {
                        ItemStack stack = minecraft.player.getInventory().getItem(slot);
                        if (ShelfDisplayRules.isAllowedDisplayItem(stack)) {
                            selected.add(key);
                            inventoryItems.put(key, stack.copyWithCount(1));
                        }
                    }
                    dirty = true;
                }
                return true;
            }
        }
        if (button == 0 && (modal == Modal.FILTERS || modal == Modal.TAG_FILTERS)) {
            int y = modalY + 88;
            int x = modalX + 20;
            int w = modalWidth - 40;
            int h = modalHeight - 141;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                int row = modalScroll + ((int) mouseY - y) / ROW_HEIGHT;
                if (row - modalScroll < Math.max(1, h / ROW_HEIGHT)) {
                    if (modal == Modal.FILTERS && row < filteredMods.size()) {
                        ModEntry mod = filteredMods.get(row);
                        modFilters.put(mod.id(), (modFilters.getOrDefault(mod.id(), 0) + 1) % 3);
                        dirty = true;
                    } else if (modal == Modal.TAG_FILTERS && row < filteredTags.size()) {
                        TagKey<Item> tag = filteredTags.get(row).key();
                        int next = (tagFilters.getOrDefault(tag, 0) + 1) % 3;
                        if (next == 0) tagFilters.remove(tag);
                        else tagFilters.put(tag, next);
                        dirty = true;
                    }
                }
                return true;
            }
        }
        if (modal != Modal.NONE) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && step == Step.CATALOG && mouseX >= contentX
                && mouseX < contentX + contentWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int row = scroll + ((int) mouseY - listY) / ROW_HEIGHT;
            if (row - scroll >= Math.max(1, listHeight / ROW_HEIGHT)) return true;
            if (bulk && !editingBulkItems) {
                if (row < visibleMods.size()) chooseBulkMod(visibleMods.get(row));
            } else if (row < visibleItems.size()) {
                String id = visibleItems.get(row).id();
                if (!selected.remove(id)) selected.add(id);
                dirty = true;
            }
            return true;
        }
        if (button == 0 && step == Step.REVIEW && mouseX >= contentX
                && mouseX < contentX + contentWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int row = reviewScroll + ((int) mouseY - listY) / ROW_HEIGHT;
            if (row - reviewScroll < Math.max(1, listHeight / ROW_HEIGHT) && row < reviewIds.size()) {
                String id = reviewIds.get(row);
                if (mouseX >= contentX + contentWidth - 35) {
                    selected.remove(id);
                    inventoryItems.remove(id);
                    priceOverrides.remove(id);
                    dirty = true;
                } else openItemPrice(id);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (selecting) return;
        if (modal == Modal.FILTERS || modal == Modal.TAG_FILTERS) { backToFilterHome(); return; }
        if (modal != Modal.NONE) { closeModal(); return; }
        current = null;
        selectedPositions.clear();
        priorPositions.clear();
        PacketDistributor.sendToServer(ShelfFillActionPayload.of("CANCEL"));
        super.onClose();
    }

    private static final class SourceOption extends AbstractButton {
        private final String detail;
        private final ResourceLocation art;
        private final int textureWidth;
        private final int textureHeight;
        private final int accent;
        private final Runnable action;

        private SourceOption(int x, int y, int width, int height, String title, String detail,
                             ResourceLocation art, int textureWidth, int textureHeight,
                             int accent, Runnable action) {
            super(x, y, width, height, Component.literal(title));
            this.detail = detail;
            this.art = art;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.accent = accent;
            this.action = action;
        }

        @Override public void onPress() { action.run(); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hovered = isHoveredOrFocused();
            graphics.fill(x, y, x + width, y + height, hovered ? accent : RULE);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    hovered ? ROW_HOVER : ROW);
            graphics.fill(x, y, x + 3, y + height, accent);
            int artHeight = Math.min(100, height - 16);
            int artWidth = 88;
            int artX = x + 11;
            int artY = y + (height - artHeight) / 2;
            graphics.fill(artX, artY, artX + artWidth, artY + artHeight, HEADER);
            float scale = Math.min((float) artWidth / textureWidth,
                    (float) artHeight / textureHeight);
            graphics.pose().pushPose();
            graphics.pose().translate(artX + (artWidth - textureWidth * scale) / 2,
                    artY + (artHeight - textureHeight * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.blit(art, 0, 0, 0, 0, textureWidth, textureHeight,
                    textureWidth, textureHeight);
            graphics.pose().popPose();
            var font = Minecraft.getInstance().font;
            int textX = x + 112;
            int textWidth = Math.max(20, width - 133);
            String title = getMessage().getString();
            if (font.width(title) > textWidth) title = font.plainSubstrByWidth(title,
                    Math.max(0, textWidth - font.width("..."))) + "...";
            String subtitle = detail;
            if (font.width(subtitle) > textWidth) subtitle = font.plainSubstrByWidth(subtitle,
                    Math.max(0, textWidth - font.width("..."))) + "...";
            graphics.drawString(font, title, textX, y + height / 2 - 13, WHITE, false);
            graphics.drawString(font, subtitle, textX, y + height / 2 + 5, MUTED, false);
            graphics.drawString(font, ">", x + width - 16, y + height / 2 - 4,
                    hovered ? accent : MUTED, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }

    private static final class FlatButton extends AbstractButton {
        private final int accent;
        private final boolean filled;
        private final Runnable action;

        private FlatButton(int x, int y, int width, int height, Component label,
                           int accent, boolean filled, Runnable action) {
            super(x, y, width, height, label);
            this.accent = accent;
            this.filled = filled;
            this.action = action;
        }

        @Override public void onPress() { action.run(); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hover = isHoveredOrFocused();
            int fill = filled ? hover ? 0xFF2D9B75 : 0xFF247C61
                    : hover ? ROW_HOVER : ROW;
            graphics.fill(x, y, x + width, y + height, hover ? accent : RULE);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
            var font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            if (font.width(label) > width - 12) label = font.plainSubstrByWidth(label,
                    Math.max(0, width - 18)) + "...";
            graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2, WHITE);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }
}
