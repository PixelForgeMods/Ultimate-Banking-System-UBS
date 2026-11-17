package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.JobActionPayload;
import net.austizz.ultimatebankingsystem.network.JobAchievementPayload;
import net.austizz.ultimatebankingsystem.network.JobCatalogEntryPayload;
import net.austizz.ultimatebankingsystem.network.CourierOrderPayload;
import net.austizz.ultimatebankingsystem.network.CourierDeliveryStatePayload;
import net.austizz.ultimatebankingsystem.network.JobDailyQuestPayload;
import net.austizz.ultimatebankingsystem.network.JobLevelRoadmapNodePayload;
import net.austizz.ultimatebankingsystem.network.JobMarketEntryPayload;
import net.austizz.ultimatebankingsystem.network.JobOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared job-market and registered-job buyer screen. */
public final class JobScreen extends Screen {
    private static final int BG = 0xFF07111D;
    private static final int FRAME = 0xFF28547A;
    private static final int HEADER = 0xFF0D263F;
    private static final int PANEL = 0xFF0C2034;
    private static final int CARD = 0xFF102A42;
    private static final int CARD_ALT = 0xFF0A1B2D;
    private static final int TRACK = 0xFF071521;
    private static final int CYAN = 0xFF42C8FF;
    private static final int GREEN = 0xFF57E39B;
    private static final int GOLD = 0xFFFFC84A;
    private static final int ORANGE = 0xFFFFA45B;
    private static final int RED = 0xFFFF7272;
    private static final int TEXT = 0xFFF2F8FF;
    private static final int MUTED = 0xFFAAC3DB;
    private static final int DIM = 0xFF718BA4;

    private enum Tab {
        JOBS("Jobs"),
        OVERVIEW("Overview"),
        ORDERS("Dispatch"),
        CATALOG("Catalog"),
        PROGRESS("Progress"),
        ACHIEVEMENTS("Achievements"),
        GUIDE("Guide");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record JobRoadmapRectHitbox(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record JobRoadmapNodeHitbox(int centerX, int centerY, int radius,
                                        JobLevelRoadmapNodePayload node) {
        boolean contains(double mouseX, double mouseY) {
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            return dx * dx + dy * dy <= (double) radius * radius;
        }
    }

    private record JobAchievementHitbox(int x, int y, int width, int height,
                                       int claimX, int claimY, int claimWidth, int claimHeight,
                                       JobAchievementPayload achievement) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        boolean claimContains(double mouseX, double mouseY) {
            return mouseX >= claimX && mouseX <= claimX + claimWidth
                    && mouseY >= claimY && mouseY <= claimY + claimHeight;
        }
    }

    private JobOpenPayload payload;
    private String selectedItemId = "";
    private String selectedJobId = "";
    private String categoryFilter = "ALL";
    private boolean carriedOnlyFilter;
    private int scrollIndex;
    private int jobScrollIndex;
    private DesktopEditBox searchInput;
    private DesktopEditBox quantityInput;
    private boolean leaveConfirmation;
    private boolean menuOpen;
    private float menuProgress;
    private Tab activeTab;
    private int achievementListLeft;
    private int achievementListTop;
    private int achievementListWidth;
    private int achievementListHeight;
    private int achievementScrollIndex;
    private int achievementRowHeight;
    private final List<JobAchievementHitbox> visibleAchievementCards = new ArrayList<>();

    private int courierListLeft;
    private int courierListTop;
    private int courierListWidth;
    private int courierListHeight;
    private int courierScrollIndex;
    private int courierRowHeight;
    private long courierSnapshotReceivedAtMillis = System.currentTimeMillis();

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int contentBottom;

    private int catalogListLeft;
    private int catalogListTop;
    private int catalogListWidth;
    private int catalogListHeight;
    private int catalogDetailLeft;
    private int catalogDetailTop;
    private int catalogDetailWidth;
    private int catalogDetailHeight;
    private int detailInputY;
    private int detailActionY;
    private int detailPreviewY;
    private boolean catalogShort;

    private int jobListLeft;
    private int jobListTop;
    private int jobListWidth;
    private int jobListHeight;
    private int jobDetailLeft;
    private int jobDetailTop;
    private int jobDetailWidth;
    private int jobDetailHeight;
    private int levelRoadmapViewportX;
    private int levelRoadmapViewportY;
    private int levelRoadmapViewportWidth;
    private int levelRoadmapViewportHeight;
    private int levelRoadmapSidePadding;
    private int levelRoadmapNodeSpacing;
    private int levelRoadmapContentWidth;
    private int levelRoadmapMaxScrollX;
    private int levelRoadmapScrollX;
    private boolean levelRoadmapScrollbarDragging;
    private int levelRoadmapScrollbarDragOffsetX;
    private JobRoadmapRectHitbox levelRoadmapViewportHitbox;
    private JobRoadmapRectHitbox levelRoadmapScrollbarTrackHitbox;
    private JobRoadmapRectHitbox levelRoadmapScrollbarThumbHitbox;
    private JobRoadmapRectHitbox levelRoadmapModalCloseHitbox;
    private JobLevelRoadmapNodePayload levelRoadmapSelectedNode;
    private final List<JobRoadmapNodeHitbox> visibleLevelRoadmapNodes = new ArrayList<>();

    public JobScreen(JobOpenPayload payload) {
        super(Component.literal(payload != null && "BUYER".equals(payload.mode())
                ? (payload.title().isBlank() ? "Job Buyer" : payload.title() + " Buyer") : "Job Market"));
        this.payload = payload;
        courierSnapshotReceivedAtMillis = System.currentTimeMillis();
        this.activeTab = defaultTab();
        selectPendingOrFirst();
        selectJobOrFirst();
    }

    public boolean matches(JobOpenPayload next) {
        return next != null && payload != null && next.mode().equals(payload.mode());
    }

    public void refresh(JobOpenPayload next) {
        if (next == null) return;
        payload = next;
        courierSnapshotReceivedAtMillis = System.currentTimeMillis();
        if (!next.active()) leaveConfirmation = false;
        if (next.levelRoadmap().isEmpty()) {
            levelRoadmapSelectedNode = null;
            levelRoadmapModalCloseHitbox = null;
            levelRoadmapScrollX = 0;
        }
        if (!availableTabs().contains(activeTab)) activeTab = defaultTab();
        selectPendingOrFirst();
        selectJobOrFirst();
        rebuild();
    }

    /** Applies accepted-route updates without discarding the available offers on the dispatch desk. */
    public void applyCourierDeliveryState(CourierDeliveryStatePayload state) {
        if (state == null || payload == null || !isCourierJob()) {
            return;
        }
        List<CourierOrderPayload> previousOrders = payload.courierOrders();
        java.util.Map<String, CourierOrderPayload> accepted = new java.util.LinkedHashMap<>();
        for (CourierOrderPayload order : state.deliveries()) {
            accepted.put(order.shortId().toUpperCase(Locale.ROOT), order);
        }

        java.util.LinkedHashMap<String, CourierOrderPayload> merged = new java.util.LinkedHashMap<>();
        for (CourierOrderPayload order : payload.courierOrders()) {
            if ("ACCEPTED".equals(order.status())) {
                CourierOrderPayload replacement = accepted.get(order.shortId().toUpperCase(Locale.ROOT));
                if (replacement == null) {
                    continue;
                }
                order = replacement;
            }
            merged.put(order.orderId(), order);
        }
        for (CourierOrderPayload order : state.deliveries()) {
            merged.putIfAbsent(order.orderId(), order);
        }
        List<CourierOrderPayload> nextOrders = List.copyOf(merged.values());
        boolean contentChanged = previousOrders.size() != nextOrders.size();
        if (!contentChanged) {
            for (int index = 0; index < previousOrders.size(); index++) {
                if (!sameCourierOrderContent(previousOrders.get(index), nextOrders.get(index))) {
                    contentChanged = true;
                    break;
                }
            }
        }

        payload = new JobOpenPayload(
                payload.mode(), payload.jobId(), payload.title(), payload.description(), payload.notice(),
                payload.level(), payload.xp(), payload.xpToNext(), payload.dailyMoneyCents(),
                payload.dailyPayoutCapCents(), payload.dailyUnitsMilli(), payload.dailyUnitsCapMilli(),
                payload.moneySpotlightFamily(), payload.xpSpotlightFamily(),
                payload.moneySpotlightBonusPercent(), payload.xpSpotlightBonusPercent(), payload.active(),
                payload.readOnly(), payload.confirmationRequired(), payload.pendingItemId(),
                payload.pendingAmount(), payload.pendingQuoteCents(), payload.catalog(),
                payload.availableJobs(), payload.levelRoadmap(), payload.achievements(), payload.dailyQuests(),
                payload.rewards(), payload.dailyQuestResetEpochSeconds(), nextOrders);
        courierSnapshotReceivedAtMillis = System.currentTimeMillis();
        if (contentChanged) {
            rebuild();
        }
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        float target = menuOpen ? 1.0F : 0.0F;
        menuProgress = Mth.approach(menuProgress, target, 0.18F);
    }

    private void rebuild() {
        String previousSearch = searchInput == null ? "" : searchInput.getValue();
        String previousQuantity = quantityInput == null ? "1" : quantityInput.getValue();
        clearWidgets();
        panelWidth = Math.min(1040, Math.max(320, width - 16));
        panelHeight = Math.min(620, Math.max(260, height - 16));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        contentLeft = panelLeft + 18;
        contentTop = panelTop + 76;
        contentWidth = Math.max(1, panelWidth - 36);
        contentBottom = panelTop + panelHeight - 42;

        buildCommonControls();
        if (payload != null) {
            switch (activeTab) {
                case JOBS -> buildJobList();
                case OVERVIEW -> buildOverviewActions();
                case ORDERS -> buildCourierOrders();
                case CATALOG -> buildCatalog(previousSearch, previousQuantity);
                case PROGRESS -> buildProgressActions();
                case ACHIEVEMENTS -> {
                }
                case GUIDE -> {
                }
            }
        }
    }

    private void buildCommonControls() {
        addButton(panelLeft + 12, panelTop + 12, 72, 28, "Menu", CYAN,
                ignored -> {
                    menuOpen = !menuOpen;
                    rebuildKeepingInputs();
                }, "Open the job dashboard sections.");
        addButton(panelLeft + 14, panelTop + panelHeight - 30, 82, 22, "Close", MUTED,
                ignored -> onClose(), "Close the job panel.");
        addButton(panelLeft + panelWidth - 98, panelTop + panelHeight - 30, 84, 22, "Refresh", CYAN,
                ignored -> send("REFRESH", "", 0, 0L, false),
                "Refresh your job progress, carried item counts, and today’s market rotation.");
    }

    private void buildOverviewActions() {
        int actionY = contentBottom - 30;
        if (payload == null) return;
        if ("MARKET".equals(payload.mode())) {
            int buttonWidth = Math.min(220, Math.max(130, contentWidth / 2 - 5));
            if (payload.active()) {
                String leaveLabel = leaveConfirmation ? "Confirm Leave" : "Leave Job";
                DesktopButton leave = addButton(contentLeft, actionY, buttonWidth, 28, leaveLabel, RED,
                        ignored -> {
                            if (leaveConfirmation) {
                                send("LEAVE", "", 0, 0L, true);
                            } else {
                                leaveConfirmation = true;
                                rebuildKeepingInputs();
                            }
                        }, leaveConfirmation
                                ? "Confirm leaving " + payload.title() + ". Your level and XP remain saved."
                                : "Start the leave confirmation step. Your progress remains saved.");
                leave.active = !payload.readOnly();
            } else {
                DesktopButton apply = addButton(contentLeft, actionY, buttonWidth, 28,
                        "Apply: " + payload.title(), GREEN,
                        ignored -> send("APPLY", selectedJobId, 0, 0L, true),
                        "Apply instantly. Progress for this job is saved separately.");
                apply.active = !payload.readOnly();
            }
        } else if ("BUYER".equals(payload.mode())) {
            boolean courier = isCourierJob();
            DesktopButton catalog = addButton(contentLeft, actionY, Math.min(220, contentWidth - 4), 28,
                    courier ? "Open Dispatch" : "Open Catalog", CYAN,
                    ignored -> {
                        activeTab = courier ? Tab.ORDERS : Tab.CATALOG;
                        rebuildKeepingInputs();
                    }, courier
                            ? "Open the Courier dispatch board and accept an NPC-to-NPC delivery route."
                            : "Browse " + payload.title().toLowerCase(Locale.ROOT)
                            + " products and open the sale desk.");
            catalog.active = payload.active() || payload.readOnly();
        }
    }

    private void buildCourierOrders() {
        courierListLeft = contentLeft;
        courierListTop = contentTop + 88;
        courierListWidth = contentWidth;
        int noticeReserve = payload != null && !payload.notice().isBlank() ? 40 : 0;
        courierListHeight = Math.max(60, contentBottom - courierListTop - noticeReserve);
        courierRowHeight = 68;
        if (payload == null || !isCourierJob()) {
            return;
        }
        List<CourierOrderPayload> orders = payload.courierOrders();
        int visibleRows = Math.max(1, courierListHeight / courierRowHeight);
        int maxScroll = Math.max(0, orders.size() - visibleRows);
        courierScrollIndex = Mth.clamp(courierScrollIndex, 0, maxScroll);
        int buttonWidth = Math.min(132, Math.max(96, courierListWidth / 5));
        for (int index = 0; index < visibleRows && courierScrollIndex + index < orders.size(); index++) {
            CourierOrderPayload order = orders.get(courierScrollIndex + index);
            int rowY = courierListTop + index * courierRowHeight;
            if ("AVAILABLE".equals(order.status())) {
                DesktopButton accept = addButton(courierListLeft + courierListWidth - buttonWidth - 14,
                        rowY + courierRowHeight - 31, buttonWidth, 22, "Accept", GREEN,
                        ignored -> send("COURIER_ACCEPT", order.orderId(), 0, 0L, true),
                        "Accept this NPC order and place its sealed parcel in your inventory.");
                accept.active = payload.active() && !payload.readOnly();
            } else if ("ACCEPTED".equals(order.status())) {
                DesktopButton cancel = addButton(courierListLeft + courierListWidth - buttonWidth - 14,
                        rowY + courierRowHeight - 31, buttonWidth, 22, "Cancel", RED,
                        ignored -> send("COURIER_CANCEL", order.shortId(), 0, 0L, true),
                        "Cancel this accepted delivery and remove its parcel. No payout is issued.");
                cancel.active = payload.active() && !payload.readOnly();
            }
        }
    }

    private void buildProgressActions() {
        if (payload == null || !"MARKET".equals(payload.mode()) || !payload.active()) return;
        DesktopButton leave = addButton(contentLeft, contentBottom - 30,
                Math.min(188, Math.max(112, contentWidth / 3)), 26,
                leaveConfirmation ? "Confirm Leave Job" : "Leave Job", RED,
                ignored -> {
                    if (leaveConfirmation) {
                        send("LEAVE", "", 0, 0L, true);
                    } else {
                        leaveConfirmation = true;
                        rebuildKeepingInputs();
                    }
                }, leaveConfirmation
                        ? "Confirm leaving " + payload.title() + ". Your saved level and career XP remain available."
                        : "Leave " + payload.title() + ". Your saved level and career XP remain available when you return.");
        leave.active = !payload.readOnly();
    }

    private void buildJobList() {
        if (!isJoblessMarket()) {
            return;
        }
        int availableHeight = Math.max(1, contentBottom - contentTop);
        boolean compact = panelWidth < 720;
        if (compact) {
            jobListLeft = contentLeft;
            jobListTop = contentTop + 28;
            jobListWidth = contentWidth;
            jobListHeight = Math.max(70, Math.min(148, availableHeight / 3));
            jobDetailLeft = contentLeft;
            jobDetailTop = jobListTop + jobListHeight + 10;
            jobDetailWidth = contentWidth;
            jobDetailHeight = Math.max(1, contentBottom - jobDetailTop);
        } else {
            jobListLeft = contentLeft;
            jobListTop = contentTop + 28;
            jobListWidth = Math.max(190, (contentWidth - 12) * 42 / 100);
            jobListHeight = Math.max(70, availableHeight - 28);
            jobDetailLeft = jobListLeft + jobListWidth + 12;
            jobDetailTop = contentTop;
            jobDetailWidth = Math.max(180, contentWidth - jobListWidth - 12);
            jobDetailHeight = availableHeight;
        }

        JobMarketEntryPayload selected = selectedJob();
        if (selected == null) {
            return;
        }
        int applyWidth = Math.min(150, Math.max(104, jobDetailWidth / 2));
        DesktopButton apply = addButton(jobDetailLeft + jobDetailWidth - applyWidth - 12,
                jobDetailTop + 12, applyWidth, 26, "Apply for Job", GREEN,
                ignored -> send("APPLY", selected.jobId(), 0, 0L, true),
                "Apply instantly for " + selected.displayName() + ". Its progress is saved separately.");
        apply.active = !payload.readOnly();
    }

    private void buildCatalog(String previousSearch, String previousQuantity) {
        int availableHeight = Math.max(1, contentBottom - contentTop);
        boolean compact = panelWidth < 720;
        catalogShort = availableHeight < 260 || (compact && availableHeight < 330);
        if (catalogShort) {
            catalogListLeft = contentLeft;
            catalogListTop = contentTop + 36;
            catalogListWidth = contentWidth;
            catalogListHeight = Math.max(50, availableHeight - 84);
            catalogDetailLeft = contentLeft;
            catalogDetailTop = contentBottom;
            catalogDetailWidth = contentWidth;
            catalogDetailHeight = 0;
        } else if (compact) {
            catalogListLeft = contentLeft;
            catalogListTop = contentTop + 36;
            catalogListWidth = contentWidth;
            catalogListHeight = Math.max(76, Math.min(132, availableHeight / 3));
            catalogDetailLeft = contentLeft;
            catalogDetailTop = catalogListTop + catalogListHeight + 10;
            catalogDetailWidth = contentWidth;
            catalogDetailHeight = Math.max(1, contentBottom - catalogDetailTop);
        } else {
            catalogListWidth = Math.max(180, (contentWidth - 12) * 56 / 100);
            catalogListLeft = contentLeft;
            catalogListTop = contentTop + 36;
            catalogListHeight = availableHeight;
            catalogDetailLeft = contentLeft + catalogListWidth + 12;
            catalogDetailTop = contentTop;
            catalogDetailWidth = Math.max(160, contentWidth - catalogListWidth - 12);
            catalogDetailHeight = availableHeight;
        }

        if (payload == null || (!payload.active() && !payload.readOnly())) return;
        int controlsWidth = compact ? contentWidth : catalogListWidth;
        int filterWidth = Math.max(72, Math.min(94, controlsWidth / 4));
        int categoryWidth = Math.max(64, Math.min(104, controlsWidth / 4));
        int searchWidth = Math.max(42, controlsWidth - filterWidth - categoryWidth - 12);
        searchInput = new DesktopEditBox(font, catalogListLeft, contentTop, searchWidth, 26,
                Component.literal("Search " + payload.title().toLowerCase(Locale.ROOT) + " products"));
        searchInput.setHint(Component.literal("Search products"));
        searchInput.setMaxLength(80);
        searchInput.setValue(previousSearch == null ? "" : previousSearch);
        addRenderableWidget(searchInput);

        addButton(catalogListLeft + searchWidth + 6, contentTop, categoryWidth, 26,
                categoryLabel(), CYAN, ignored -> cycleCategory(),
                "Filter the " + payload.title().toLowerCase(Locale.ROOT) + " catalog by category.");
        addButton(catalogListLeft + searchWidth + categoryWidth + 12, contentTop, filterWidth, 26,
                carriedOnlyFilter ? "Carried" : "All Items", carriedOnlyFilter ? GREEN : CYAN,
                ignored -> toggleCarriedFilter(), carriedOnlyFilter
                        ? "Show only sellable products currently carried in your inventory or supported containers."
                        : "Show every sellable product. Click to show only products you currently carry.");

        int rowHeight = 38;
        int visibleRows = Math.max(1, catalogListHeight / rowHeight);
        int maxScroll = Math.max(0, filteredEntries().size() - visibleRows);
        scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex));
        List<JobCatalogEntryPayload> visible = filteredEntries();
        for (int index = 0; index < visibleRows && scrollIndex + index < visible.size(); index++) {
            JobCatalogEntryPayload entry = visible.get(scrollIndex + index);
            int y = catalogListTop + index * rowHeight;
            DesktopButton row = addButton(catalogListLeft, y, catalogListWidth, rowHeight - 4, "",
                    entry.itemId().equals(selectedItemId) ? GREEN : CYAN,
                    ignored -> {
                        selectedItemId = entry.itemId();
                        rebuildKeepingInputs();
                    }, entry.displayName() + " | item mode: " + displayCategory(entry.category())
                            + " | family: " + displayFamily(entry.family()) + " | owned: "
                            + entry.ownedCount() + " | quote: " + formatCents(entry.quoteCents()));
            row.setChromeVisible(false);
            row.active = true;
        }

        if (catalogShort) return;
        JobCatalogEntryPayload selected = selectedEntry();
        if (selected == null) return;
        int detailBottom = catalogDetailTop + catalogDetailHeight;
        detailPreviewY = Math.max(catalogDetailTop + 64, detailBottom - 28);
        detailActionY = Math.max(catalogDetailTop + 54, detailPreviewY - 32);
        detailInputY = Math.max(catalogDetailTop + 42, detailActionY - 32);
        quantityInput = new DesktopEditBox(font, catalogDetailLeft + 10, detailInputY,
                Math.max(40, catalogDetailWidth - 20), 26, Component.literal("Quantity"));
        quantityInput.setMaxLength(9);
        quantityInput.setValue(previousQuantity == null || previousQuantity.isBlank() ? "1" : previousQuantity);
        quantityInput.setHint(Component.literal("Exact quantity"));
        addRenderableWidget(quantityInput);

        int buttonWidth = Math.max(42, (catalogDetailWidth - 26) / 2);
        DesktopButton stack = addButton(catalogDetailLeft + 10, detailActionY, buttonWidth, 26,
                "Sell Stack", GOLD, ignored -> previewStack(),
                "Preview selling one complete carried stack of this product.");
        stack.active = selected.ownedCount() > 0 && !payload.readOnly();
        DesktopButton all = addButton(catalogDetailLeft + 16 + buttonWidth, detailActionY, buttonWidth, 26,
                "Sell All", GOLD, ignored -> previewAll(),
                "Preview selling all carried units of this product.");
        all.active = selected.ownedCount() > 0 && !payload.readOnly();

        DesktopButton preview = addButton(catalogDetailLeft + 10, detailPreviewY,
                Math.max(42, catalogDetailWidth - 20), 26,
                payload.confirmationRequired() ? "Confirm Sale" : "Preview Sale",
                payload.confirmationRequired() ? GREEN : CYAN,
                ignored -> {
                    if (payload.confirmationRequired()) {
                        send("SELL", payload.pendingItemId(), payload.pendingAmount(),
                                payload.pendingQuoteCents(), true);
                    } else {
                        preview(parseQuantity());
                    }
                }, payload.confirmationRequired()
                        ? "Commit the displayed sale using the server’s live quote."
                        : "Calculate a live quote before anything is removed.");
        preview.active = !payload.readOnly()
                && (!payload.confirmationRequired() || payload.pendingAmount() > 0);
    }

    private void renderCourierOrders(GuiGraphics graphics, int mouseX, int mouseY) {
        if (payload == null) {
            return;
        }
        drawCard(graphics, contentLeft, contentTop, contentWidth, 74, CYAN);
        drawItemIcon(graphics, new ItemStack(Items.PAPER), contentLeft + 12, contentTop + 10, 1.25F);
        int headerTextWidth = Math.max(70, contentWidth - 194);
        drawFit(graphics, "COURIER DISPATCH", contentLeft + 42, contentTop + 8,
                headerTextWidth, TEXT);
        drawWrapped(graphics,
                "Accept a sealed manifest, carry it to the named NPC, and hand it over in person.",
                contentLeft + 42, contentTop + 26, headerTextWidth, MUTED, 11, 2);
        long activeRoutes = payload.courierOrders().stream()
                .filter(order -> "ACCEPTED".equals(order.status()))
                .count();
        drawBadge(graphics, contentLeft + contentWidth - 142, contentTop + 10, 128, 20,
                activeRoutes + " / 3 IN TRANSIT", activeRoutes > 0 ? GOLD : CYAN);
        drawFitRight(graphics, payload.active() ? "15:00 from acceptance" : "Apply for Courier to work routes",
                contentLeft + contentWidth - 14, contentTop + 40, 180,
                payload.active() ? MUTED : RED);

        drawCard(graphics, courierListLeft, courierListTop, courierListWidth, courierListHeight, FRAME);
        List<CourierOrderPayload> orders = payload.courierOrders();
        if (orders.isEmpty()) {
            drawItemIcon(graphics, new ItemStack(Items.COMPASS), courierListLeft + 16,
                    courierListTop + 20, 1.5F);
            drawFit(graphics, payload.active() ? "NO ROUTES IN RANGE" : "COURIER DESK LOCKED",
                    courierListLeft + 52, courierListTop + 20, Math.max(80, courierListWidth - 68), TEXT);
            drawWrapped(graphics,
                    payload.active()
                            ? "A dispatcher needs at least one other placed job NPC as a receiving destination."
                            : "Visit the Job Market and apply for Courier before accepting NPC orders.",
                    courierListLeft + 16, courierListTop + 48, Math.max(80, courierListWidth - 32), MUTED, 12, 3);
            drawNoticeCard(graphics, payload.notice(), contentLeft, contentBottom - 34, contentWidth);
            return;
        }

        int visibleRows = Math.max(1, courierListHeight / courierRowHeight);
        for (int index = 0; index < visibleRows && courierScrollIndex + index < orders.size(); index++) {
            CourierOrderPayload order = orders.get(courierScrollIndex + index);
            int y = courierListTop + index * courierRowHeight;
            int accent = "ACCEPTED".equals(order.status()) ? GOLD : CYAN;
            drawCard(graphics, courierListLeft + 8, y + 4, courierListWidth - 16,
                    courierRowHeight - 8, accent);
            drawItemIcon(graphics, itemStack(order.itemId()), courierListLeft + 18, y + 10, 1.15F);
            int infoX = courierListLeft + 48;
            int infoWidth = Math.max(70, courierListWidth - 214);
            drawFit(graphics, "#" + order.shortId() + "  " + order.originName() + "  >  " + order.destinationName(), infoX, y + 8,
                    infoWidth, TEXT);
            drawFit(graphics, order.quantity() + " x " + order.itemName(), infoX, y + 29,
                    infoWidth, MUTED);
            String route = order.routeDistanceBlocks() < 0
                    ? "Cross-dimension route"
                    : "Route " + formatNumber(order.routeDistanceBlocks()) + " blocks"
                    + (order.destinationX() == 0 && order.destinationY() == 0 && order.destinationZ() == 0
                    ? "" : "  @ " + order.destinationX() + ", " + order.destinationY() + ", " + order.destinationZ());
            drawFit(graphics, route, infoX, y + 46, infoWidth, DIM);

            int right = courierListLeft + courierListWidth - 18;
            drawFitRight(graphics, formatCents(order.rewardCents()), right, y + 8, 146, GOLD);
            long seconds = courierSecondsRemaining(order);
            drawFitRight(graphics, "TIME LEFT " + courierCountdown(seconds), right, y + 27,
                    146, seconds <= 60L ? RED : MUTED);
            String modifier = order.moneyMultiplierPercent() == 100 && order.xpMultiplierPercent() == 100
                    ? ("ACCEPTED".equals(order.status()) ? "IN TRANSIT" : "AVAILABLE")
                    : order.contractTier() + "  PAY +" + (order.moneyMultiplierPercent() - 100)
                    + "%  XP +" + (order.xpMultiplierPercent() - 100) + "%";
            drawFitRight(graphics, modifier, right, y + 46, 146, accent);
        }
        if (orders.size() > visibleRows) {
            drawFitRight(graphics, "Scroll for more routes", courierListLeft + courierListWidth - 12,
                    courierListTop + courierListHeight - 13, 150, DIM);
        }
        drawNoticeCard(graphics, payload.notice(), contentLeft, contentBottom - 34, contentWidth);
    }

    private String courierCountdown(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60L, safe % 60L);
    }

    private long courierSecondsRemaining(CourierOrderPayload order) {
        if (order == null) {
            return 0L;
        }
        long elapsed = Math.max(0L, (System.currentTimeMillis() - courierSnapshotReceivedAtMillis) / 1000L);
        return Math.max(0L, order.secondsRemaining() - elapsed);
    }

    private static boolean sameCourierOrderContent(CourierOrderPayload left, CourierOrderPayload right) {
        return left != null && right != null
                && Objects.equals(left.orderId(), right.orderId())
                && Objects.equals(left.shortId(), right.shortId())
                && Objects.equals(left.originName(), right.originName())
                && Objects.equals(left.destinationName(), right.destinationName())
                && Objects.equals(left.destinationDimension(), right.destinationDimension())
                && left.destinationX() == right.destinationX()
                && left.destinationY() == right.destinationY()
                && left.destinationZ() == right.destinationZ()
                && Objects.equals(left.itemId(), right.itemId())
                && Objects.equals(left.itemName(), right.itemName())
                && left.quantity() == right.quantity()
                && left.rewardCents() == right.rewardCents()
                && Objects.equals(left.status(), right.status())
                && left.routeDistanceBlocks() == right.routeDistanceBlocks()
                && left.marketValueCents() == right.marketValueCents()
                && left.moneyMultiplierPercent() == right.moneyMultiplierPercent()
                && left.xpMultiplierPercent() == right.xpMultiplierPercent()
                && Objects.equals(left.contractTier(), right.contractTier());
    }

    private void renderOverview(GuiGraphics graphics) {
        if (payload == null) return;
        int width = contentWidth;
        int heroHeight = panelHeight < 380 ? 64 : 82;
        drawCard(graphics, contentLeft, contentTop, width, heroHeight, CYAN);
        ItemStack jobIcon = jobIcon();
        if (!jobIcon.isEmpty()) graphics.renderItem(jobIcon, contentLeft + 14, contentTop + 16);
        int textX = contentLeft + 40;
        String heading = payload.active() ? payload.title() + " operations online" : "Choose your next role";
        drawFit(graphics, heading, textX, contentTop + 12, Math.max(40, width - 150), TEXT);
        drawWrapped(graphics, payload.description(), textX, contentTop + 31,
                Math.max(40, width - 56), MUTED, 11, panelHeight < 380 ? 2 : 3);
        drawBadge(graphics, contentLeft + width - 112, contentTop + 12, 98, 20,
                payload.active() ? "ACTIVE" : payload.readOnly() ? "READ ONLY" : "AVAILABLE",
                payload.active() ? GREEN : payload.readOnly() ? GOLD : CYAN);

        int metricsY = contentTop + heroHeight + 10;
        int columns = panelWidth < 560 ? 2 : 4;
        int metricGap = 8;
        int metricWidth = Math.max(70, (width - metricGap * (columns - 1)) / columns);
        int metricHeight = panelHeight < 380 ? 54 : 62;
        List<Metric> metrics = List.of(
                new Metric("LEVEL", "Lv " + payload.level(), "Job rank", jobIconItem(), CYAN),
                new Metric("TODAY", formatCents(payload.dailyMoneyCents()),
                        "of " + formatCents(payload.dailyPayoutCapCents()), Items.GOLD_INGOT, GOLD),
                new Metric("MATERIAL", formatUnits(payload.dailyUnitsMilli()),
                        "of " + formatUnits(payload.dailyUnitsCapMilli()) + " units", jobIconItem(), GREEN),
                new Metric("SPOTLIGHT", spotlightSummary(), "Money bonus", Items.EMERALD, ORANGE)
        );
        for (int index = 0; index < metrics.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            int x = contentLeft + column * (metricWidth + metricGap);
            int y = metricsY + row * (metricHeight + metricGap);
            drawMetric(graphics, x, y, metricWidth, metricHeight, metrics.get(index));
        }

        int rows = (metrics.size() + columns - 1) / columns;
        int progressY = metricsY + rows * (metricHeight + metricGap) + 8;
        int progressHeight = Math.max(64, contentBottom - progressY - 44);
        drawCard(graphics, contentLeft, progressY, width, progressHeight, GREEN);
        graphics.drawString(font, "SHIFT PROGRESS", contentLeft + 14, progressY + 10, GREEN, false);
        graphics.drawString(font, payload.active() ? "Level progress" : "Apply to begin earning XP",
                contentLeft + 14, progressY + 27, TEXT, false);
        float xpProgress = xpProgress();
        drawProgressBar(graphics, contentLeft + 14, progressY + 43, width - 28, 10, xpProgress, GREEN);
        String xpLabel = payload.level() >= 100 ? "MAX LEVEL" : formatNumber(payload.xpToNext()) + " XP to next level";
        drawFit(graphics, formatNumber(payload.xp()) + " XP", contentLeft + 14, progressY + 59,
                Math.max(40, width / 2 - 20), MUTED);
        drawFitRight(graphics, xpLabel, contentLeft + width - 14, progressY + 59,
                Math.max(40, width / 2 - 20), MUTED);
        drawNoticeCard(graphics, payload.notice(), contentLeft, contentBottom - 64, width);
    }

    private void renderJobList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isJoblessMarket()) {
            return;
        }
        List<JobMarketEntryPayload> jobs = availableJobs();
        drawCard(graphics, jobListLeft - 6, jobListTop - 6, jobListWidth + 12,
                Math.max(50, jobListHeight + 12), CYAN);
        graphics.drawString(font, "AVAILABLE JOBS", jobListLeft, jobListTop - 20, CYAN, false);
        if (jobs.isEmpty()) {
            drawItemIcon(graphics, new ItemStack(Items.COMPASS), jobListLeft + 14, jobListTop + 16, 1.0F);
            drawFit(graphics, "No jobs are available", jobListLeft + 42, jobListTop + 16,
                    Math.max(40, jobListWidth - 52), TEXT);
            drawFit(graphics, "Ask an operator to register a job.", jobListLeft + 42, jobListTop + 32,
                    Math.max(40, jobListWidth - 52), MUTED);
            return;
        }

        int rowHeight = 52;
        int visibleRows = Math.max(1, jobListHeight / rowHeight);
        int maxScroll = Math.max(0, jobs.size() - visibleRows);
        jobScrollIndex = Math.max(0, Math.min(maxScroll, jobScrollIndex));
        for (int index = 0; index < visibleRows && jobScrollIndex + index < jobs.size(); index++) {
            JobMarketEntryPayload job = jobs.get(jobScrollIndex + index);
            int y = jobListTop + index * rowHeight;
            boolean hovered = mouseX >= jobListLeft && mouseX <= jobListLeft + jobListWidth
                    && mouseY >= y && mouseY <= y + rowHeight - 4;
            drawJobListRow(graphics, job, y, hovered);
        }

        JobMarketEntryPayload selected = selectedJob();
        if (selected == null) {
            return;
        }
        drawCard(graphics, jobDetailLeft, jobDetailTop, jobDetailWidth,
                Math.max(1, jobDetailHeight), selected.active() ? GREEN : GOLD);
        drawItemIcon(graphics, itemStack(selected.iconItemId()), jobDetailLeft + 14, jobDetailTop + 16, 2.0F);
        int titleX = jobDetailLeft + 52;
        int titleWidth = Math.max(48, jobDetailWidth - 226);
        graphics.drawString(font, "JOB PROFILE", titleX, jobDetailTop + 11, selected.active() ? GREEN : GOLD, false);
        drawFit(graphics, selected.displayName(), titleX, jobDetailTop + 27, titleWidth, TEXT);
        drawFit(graphics, "Level " + selected.level() + "  |  " + formatNumber(selected.xp()) + " XP",
                titleX, jobDetailTop + 43, titleWidth, MUTED);
        drawBadge(graphics, jobDetailLeft + 14, jobDetailTop + 62, Math.max(78, jobDetailWidth - 28), 18,
                selected.active() ? "CURRENT JOB" : "OPEN POSITION", selected.active() ? GREEN : CYAN);

        int descriptionY = jobDetailTop + 94;
        drawFit(graphics, "ABOUT THIS JOB", jobDetailLeft + 14, descriptionY, Math.max(40, jobDetailWidth - 28), DIM);
        drawWrapped(graphics, selected.description(), jobDetailLeft + 14, descriptionY + 18,
                Math.max(40, jobDetailWidth - 28), MUTED, 12, 4);

        int progressY = Math.max(descriptionY + 72, jobDetailTop + jobDetailHeight - 74);
        drawCard(graphics, jobDetailLeft + 14, progressY, Math.max(60, jobDetailWidth - 28), 56, FRAME);
        graphics.drawString(font, "CAREER PROGRESS", jobDetailLeft + 24, progressY + 9, CYAN, false);
        drawProgressBar(graphics, jobDetailLeft + 24, progressY + 26,
                Math.max(30, jobDetailWidth - 48), 9, jobXpProgress(selected), selected.active() ? GREEN : CYAN);
        drawFit(graphics, selected.level() >= 100 ? "Max level" : formatNumber(selected.xpToNext()) + " XP to next level",
                jobDetailLeft + 24, progressY + 41, Math.max(40, jobDetailWidth - 48), MUTED);
        drawNoticeCard(graphics, payload.notice(), jobListLeft, contentBottom - 34, jobListWidth);
    }

    private void drawJobListRow(GuiGraphics graphics, JobMarketEntryPayload job, int y, boolean hovered) {
        boolean selected = job.jobId().equals(selectedJobId);
        int accent = selected ? GREEN : hovered ? CYAN : FRAME;
        drawCard(graphics, jobListLeft, y, jobListWidth, 48, accent);
        drawItemIcon(graphics, itemStack(job.iconItemId()), jobListLeft + 8, y + 16, 1.0F);
        int textX = jobListLeft + 32;
        drawFit(graphics, job.displayName(), textX, y + 7, Math.max(40, jobListWidth - 98), TEXT);
        drawFit(graphics, job.active() ? "Current position" : "Open position", textX, y + 24,
                Math.max(40, jobListWidth - 98), job.active() ? GREEN : MUTED);
        drawFitRight(graphics, "LV " + job.level(), jobListLeft + jobListWidth - 8, y + 8, 48, CYAN);
        drawFitRight(graphics, formatNumber(job.xp()) + " XP", jobListLeft + jobListWidth - 8, y + 24, 72, DIM);
    }

    private void renderCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        if (payload == null) return;
        if (!payload.active() && !payload.readOnly()) {
            drawCard(graphics, contentLeft, contentTop, contentWidth,
                    Math.max(60, contentBottom - contentTop), GOLD);
            drawItemIcon(graphics, new ItemStack(Items.CHEST), contentLeft + 18, contentTop + 18, 1.0F);
            graphics.drawString(font, "SALE DESK LOCKED", contentLeft + 48, contentTop + 16, GOLD, false);
            drawWrapped(graphics, "Apply at a Job Market NPC first. Your sale inventory stays untouched.",
                    contentLeft + 48, contentTop + 34, contentWidth - 64, MUTED, 12, 3);
            return;
        }

        drawCard(graphics, catalogListLeft - 6, catalogListTop - 6,
                catalogListWidth + 12, Math.max(50, catalogListHeight + 12), CYAN);
        graphics.drawString(font, payload.title().toUpperCase(Locale.ROOT) + " CATALOG",
                catalogListLeft, catalogListTop - 20, CYAN, false);
        List<JobCatalogEntryPayload> visible = filteredEntries();
        int rowHeight = 38;
        int visibleRows = Math.max(1, catalogListHeight / rowHeight);
        if (visible.isEmpty()) {
            drawItemIcon(graphics, new ItemStack(Items.COMPASS), catalogListLeft + 16, catalogListTop + 18, 1.0F);
            drawFit(graphics, "No matching products", catalogListLeft + 42, catalogListTop + 18,
                    catalogListWidth - 52, TEXT);
            drawFit(graphics, "Try another category or search term.", catalogListLeft + 42,
                    catalogListTop + 34, catalogListWidth - 52, MUTED);
        } else {
            for (int index = 0; index < visibleRows && scrollIndex + index < visible.size(); index++) {
                JobCatalogEntryPayload entry = visible.get(scrollIndex + index);
                int y = catalogListTop + index * rowHeight;
                boolean hovered = mouseX >= catalogListLeft && mouseX <= catalogListLeft + catalogListWidth
                        && mouseY >= y && mouseY <= y + rowHeight - 4;
                drawCatalogRow(graphics, entry, y, hovered);
            }
        }

        if (catalogShort) {
            drawNoticeCard(graphics, "Scroll the catalog and select an item to view its live quote.",
                    contentLeft, contentBottom - 34, contentWidth);
            return;
        }
        drawCatalogDetails(graphics);
    }

    private void drawCatalogRow(GuiGraphics graphics, JobCatalogEntryPayload entry, int y, boolean hovered) {
        boolean selected = entry.itemId().equals(selectedItemId);
        int accent = selected ? GREEN : hovered ? CYAN : FRAME;
        drawCard(graphics, catalogListLeft, y, catalogListWidth, 34, accent);
        drawItemIcon(graphics, itemStack(entry.itemId()), catalogListLeft + 8, y + 8, 1.0F);
        int textX = catalogListLeft + 32;
        int rightWidth = Math.min(112, Math.max(68, catalogListWidth / 3));
        drawFit(graphics, entry.displayName(), textX, y + 5,
                Math.max(30, catalogListWidth - 40 - rightWidth), TEXT);
        drawFit(graphics, displayCategory(entry.category()) + "  |  " + displayFamily(entry.family()),
                textX, y + 20, Math.max(30, catalogListWidth - 40 - rightWidth), DIM);
        if (hasAnySpotlight(entry)) {
            String modifier = hasMoneySpotlight(entry) && hasXpSpotlight(entry)
                    ? "$ +" + payload.moneySpotlightBonusPercent() + "%  XP +" + payload.xpSpotlightBonusPercent() + "%"
                    : hasMoneySpotlight(entry) ? "$ +" + payload.moneySpotlightBonusPercent() + "%"
                    : "XP +" + payload.xpSpotlightBonusPercent() + "%";
            drawBadge(graphics, catalogListLeft + catalogListWidth - rightWidth, y + 4, rightWidth - 4, 14,
                    modifier, hasMoneySpotlight(entry) ? GOLD : GREEN);
            drawFitRight(graphics, formatCents(entry.quoteCents()), catalogListLeft + catalogListWidth - 8,
                    y + 20, rightWidth, GOLD);
        } else {
            drawFitRight(graphics, formatCents(entry.quoteCents()), catalogListLeft + catalogListWidth - 8,
                    y + 5, rightWidth, GOLD);
            drawFitRight(graphics, "owned " + entry.ownedCount(), catalogListLeft + catalogListWidth - 8,
                    y + 20, rightWidth, GREEN);
        }
    }

    private void drawCatalogDetails(GuiGraphics graphics) {
        drawCard(graphics, catalogDetailLeft, catalogDetailTop, catalogDetailWidth,
                Math.max(1, catalogDetailHeight), GOLD);
        JobCatalogEntryPayload selected = selectedEntry();
        if (selected == null) {
            drawItemIcon(graphics, new ItemStack(Items.CHEST), catalogDetailLeft + 14, catalogDetailTop + 16, 1.0F);
            graphics.drawString(font, "Select an item", catalogDetailLeft + 40, catalogDetailTop + 18, TEXT, false);
            return;
        }
        int titleX = catalogDetailLeft + 58;
        drawItemIcon(graphics, itemStack(selected.itemId()), catalogDetailLeft + 14, catalogDetailTop + 16, 2.0F);
        drawFit(graphics, selected.displayName(), titleX, catalogDetailTop + 12,
                Math.max(36, catalogDetailWidth - 70), TEXT);
        drawFit(graphics, "ITEM MODE  /  " + displayCategory(selected.category()), titleX,
                catalogDetailTop + 29, Math.max(36, catalogDetailWidth - 70), CYAN);
        drawFit(graphics, displayFamily(selected.family()) + " material", titleX,
                catalogDetailTop + 44, Math.max(36, catalogDetailWidth - 70), MUTED);

        int multiplierY = catalogDetailTop + 66;
        int multiplierHeight = 42;
        if (hasAnySpotlight(selected)) {
            drawCard(graphics, catalogDetailLeft + 10, multiplierY, Math.max(60, catalogDetailWidth - 20),
                    multiplierHeight, GOLD);
            graphics.drawString(font, "TODAY'S MULTIPLIERS", catalogDetailLeft + 20, multiplierY + 8, GOLD, false);
            String money = hasMoneySpotlight(selected) ? "PAYOUT +" + payload.moneySpotlightBonusPercent() + "%" : "PAYOUT standard";
            String xp = hasXpSpotlight(selected) ? "XP +" + payload.xpSpotlightBonusPercent() + "%" : "XP standard";
            drawFit(graphics, money + "  |  " + xp, catalogDetailLeft + 20, multiplierY + 24,
                    Math.max(40, catalogDetailWidth - 40), hasXpSpotlight(selected) ? GREEN : MUTED);
        } else {
            drawCard(graphics, catalogDetailLeft + 10, multiplierY, Math.max(60, catalogDetailWidth - 20),
                    multiplierHeight, FRAME);
            graphics.drawString(font, "STANDARD PAYOUT", catalogDetailLeft + 20, multiplierY + 8, MUTED, false);
            drawFit(graphics, "No active family multiplier", catalogDetailLeft + 20, multiplierY + 24,
                    Math.max(40, catalogDetailWidth - 40), DIM);
        }

        int statsY = multiplierY + multiplierHeight + 8;
        int statWidth = Math.max(50, (catalogDetailWidth - 28) / 3);
        drawSmallStat(graphics, catalogDetailLeft + 10, statsY, statWidth, "QUOTE",
                formatCents(selected.quoteCents()), GOLD);
        drawSmallStat(graphics, catalogDetailLeft + 14 + statWidth, statsY, statWidth, "CARRIED",
                String.valueOf(selected.ownedCount()), GREEN);
        drawSmallStat(graphics, catalogDetailLeft + 18 + statWidth * 2, statsY, statWidth, "RAW UNITS",
                formatUnits(selected.rawUnitsMilli()), CYAN);

        if (payload.confirmationRequired() && detailInputY > statsY + 44) {
            drawBadge(graphics, catalogDetailLeft + 10, detailInputY - 22,
                    Math.max(80, catalogDetailWidth - 20), 18,
                    "REVIEW REQUIRED  " + formatCents(
                            payload.pendingQuoteCents() * Math.max(0, payload.pendingAmount())), GREEN);
        }
        if (detailInputY > catalogDetailTop + 120) {
            graphics.drawString(font, "QUANTITY TO SELL", catalogDetailLeft + 10, detailInputY - 10, MUTED, false);
        }
    }

    private void renderProgress(GuiGraphics graphics) {
        if (payload == null) return;
        int top = contentTop;
        int available = Math.max(100, contentBottom - top);
        boolean tight = available < 260;
        int heroHeight = tight ? 72 : Math.min(108, Math.max(84, available / 4));
        int roadmapHeight = tight ? 86 : Math.min(236, Math.max(184, available / 2));
        int questsY = top + heroHeight + roadmapHeight + 16;
        int questsBottom = contentBottom - (payload.active() && "MARKET".equals(payload.mode()) ? 38 : 0);
        int questsHeight = Math.max(tight ? 46 : 92, questsBottom - questsY);

        drawCard(graphics, contentLeft, top, contentWidth, heroHeight, GREEN);
        drawItemIcon(graphics, jobIcon(), contentLeft + 14, top + 18, 1.5F);
        graphics.drawString(font, payload.title().toUpperCase(Locale.ROOT) + " CAREER",
                contentLeft + 48, top + 12, GREEN, false);
        JobLevelRoadmapNodePayload currentNode = currentRoadmapNode();
        int payoutBonus = currentNode == null ? 0 : currentNode.payoutBonusPercent();
        drawFit(graphics, "Level " + payload.level() + "  |  Payout bonus +" + payoutBonus + "%",
                contentLeft + 48, top + 29, Math.max(56, contentWidth - 172), TEXT);
        drawBadge(graphics, contentLeft + contentWidth - 120, top + 12, 106, 20,
                payload.level() >= 100 ? "MAX RANK" : "RANK " + payload.level(), CYAN);
        int progressY = top + (tight ? 47 : 53);
        drawProgressBar(graphics, contentLeft + 14, progressY, contentWidth - 28, tight ? 8 : 12,
                xpProgress(), GREEN);
        if (!tight) {
            drawFit(graphics, formatNumber(payload.xp()) + " career XP", contentLeft + 14, progressY + 19,
                    Math.max(48, contentWidth / 2 - 18), MUTED);
                    drawFitRight(graphics, payload.level() >= 100 ? "Every payout bonus unlocked"
                            : formatNumber(payload.xpToNext()) + " XP to level " + (payload.level() + 1),
                    contentLeft + contentWidth - 14, progressY + 19, Math.max(48, contentWidth / 2 - 18), CYAN);
        }

        int roadmapY = top + heroHeight + 8;
        drawJobLevelRoadmapPanel(graphics, roadmapY, contentWidth, roadmapHeight);
        drawDailyQuestBoard(graphics, contentLeft, questsY, contentWidth, questsHeight);
    }

    private void drawJobLevelRoadmapPanel(GuiGraphics graphics, int y, int width, int height) {
        int x = contentLeft;
        visibleLevelRoadmapNodes.clear();
        levelRoadmapViewportHitbox = null;
        levelRoadmapScrollbarTrackHitbox = null;
        levelRoadmapScrollbarThumbHitbox = null;
        levelRoadmapModalCloseHitbox = null;

        int headerY = y + 6;
        graphics.fill(x, y, x + width, y + 22, 0x6C21405F);
        List<JobLevelRoadmapNodePayload> nodes = payload.levelRoadmap();
        if (nodes.isEmpty()) {
            levelRoadmapMaxScrollX = 0;
            levelRoadmapScrollX = 0;
            drawFit(graphics, "Level roadmap is not loaded yet.", x + 6, y + 30,
                    Math.max(80, width - 12), TEXT);
            drawFit(graphics, "Refresh the job panel to fetch progression milestones.", x + 6, y + 44,
                    Math.max(80, width - 12), MUTED);
            return;
        }

        int currentLevel = Mth.clamp(payload.level(), 1, 100);
        long currentXp = Math.max(0L, payload.xp());
        long currentFloor = roadmapRequiredXp(nodes, currentLevel);
        long nextTarget = currentLevel >= 100 ? currentFloor : roadmapRequiredXp(nodes, currentLevel + 1);
        double progressRatio = currentLevel >= 100
                ? 1.0D
                : (currentXp - currentFloor) / (double) Math.max(1L, nextTarget - currentFloor);
        progressRatio = Math.max(0.0D, Math.min(1.0D, progressRatio));

        String header = fitToWidth(payload.title() + " | Level " + currentLevel,
                Math.max(80, width - 12));
        String subHeader = fitToWidth("XP " + formatNumber(currentXp) + " / Next "
                        + formatNumber(nextTarget) + " | Progress " + Math.round(progressRatio * 100.0D) + "%",
                Math.max(80, width - 12));
        graphics.drawString(font, header, x + 6, headerY, TEXT, false);
        graphics.drawString(font, subHeader, x + 6, headerY + 10, MUTED, false);

        int scrollbarHeight = 8;
        levelRoadmapViewportX = x + 4;
        levelRoadmapViewportY = y + 24;
        levelRoadmapViewportWidth = Math.max(40, width - 8);
        levelRoadmapViewportHeight = Math.max(80, height - 24 - scrollbarHeight - 6);
        levelRoadmapViewportHitbox = new JobRoadmapRectHitbox(levelRoadmapViewportX, levelRoadmapViewportY,
                levelRoadmapViewportWidth, levelRoadmapViewportHeight);
        int baselineY = levelRoadmapViewportY + levelRoadmapViewportHeight / 2;
        levelRoadmapNodeSpacing = 100;
        levelRoadmapSidePadding = 64;
        levelRoadmapContentWidth = Math.max(levelRoadmapViewportWidth,
                levelRoadmapSidePadding * 2 + Math.max(0, nodes.size() - 1) * levelRoadmapNodeSpacing + 2);
        levelRoadmapMaxScrollX = Math.max(0, levelRoadmapContentWidth - levelRoadmapViewportWidth);
        levelRoadmapScrollX = Mth.clamp(levelRoadmapScrollX, 0, levelRoadmapMaxScrollX);

        graphics.fill(levelRoadmapViewportX, levelRoadmapViewportY,
                levelRoadmapViewportX + levelRoadmapViewportWidth,
                levelRoadmapViewportY + levelRoadmapViewportHeight, 0x3B18314A);
        graphics.fill(levelRoadmapViewportX, baselineY - 1,
                levelRoadmapViewportX + levelRoadmapViewportWidth, baselineY + 1, 0x664F6F91);
        graphics.enableScissor(levelRoadmapViewportX + 1, levelRoadmapViewportY + 1,
                levelRoadmapViewportX + levelRoadmapViewportWidth - 1,
                levelRoadmapViewportY + levelRoadmapViewportHeight - 1);

        int startX = levelRoadmapViewportX + levelRoadmapSidePadding - levelRoadmapScrollX;
        for (int index = 0; index < nodes.size() - 1; index++) {
            JobLevelRoadmapNodePayload node = nodes.get(index);
            int nodeX = startX + index * levelRoadmapNodeSpacing;
            int nextX = startX + (index + 1) * levelRoadmapNodeSpacing;
            int segmentStart = nodeX + 12;
            int segmentEnd = nextX - 12;
            if (segmentEnd <= segmentStart || segmentEnd < levelRoadmapViewportX
                    || segmentStart > levelRoadmapViewportX + levelRoadmapViewportWidth) {
                continue;
            }
            graphics.fill(segmentStart, baselineY - 1, segmentEnd, baselineY + 1, 0x6650698B);
            if (currentLevel > node.level()) {
                graphics.fill(segmentStart, baselineY - 1, segmentEnd, baselineY + 1, 0xCC59C988);
            } else if (currentLevel == node.level()) {
                int fillEnd = segmentStart + (int) Math.round(
                        (segmentEnd - segmentStart) * progressRatio);
                if (fillEnd > segmentStart) {
                    graphics.fill(segmentStart, baselineY - 1, fillEnd, baselineY + 1, 0xCC59C988);
                }
            }
        }

        int cardWidth = 118;
        int cardHeight = 34;
        for (int index = 0; index < nodes.size(); index++) {
            JobLevelRoadmapNodePayload node = nodes.get(index);
            int nodeX = startX + index * levelRoadmapNodeSpacing;
            if (nodeX < levelRoadmapViewportX - cardWidth
                    || nodeX > levelRoadmapViewportX + levelRoadmapViewportWidth + cardWidth) {
                continue;
            }
            boolean reached = node.level() <= currentLevel;
            boolean current = node.level() == currentLevel;
            boolean milestone = isJobRoadmapMilestoneLevel(node.level());
            int radius = milestone ? (current ? 18 : 16) : (current ? 14 : 11);
            int ringColor = milestone ? GOLD : reached ? 0xFF8DE0A8 : 0xFF8FB4D7;
            int fillColor = reached ? (current ? 0xFF55C980 : 0xFF4AAA70) : 0xFF325576;
            int textColor = reached ? 0xFF103523 : 0xFFE6F2FF;
            if (milestone) {
                drawFilledCircle(graphics, nodeX, baselineY, radius + 3,
                        reached ? 0x6659C988 : 0x666EAEE0);
            }
            drawFilledCircle(graphics, nodeX, baselineY, radius, ringColor);
            drawFilledCircle(graphics, nodeX, baselineY, Math.max(1, radius - 2), fillColor);
            graphics.drawCenteredString(font, String.valueOf(node.level()), nodeX, baselineY - 4, textColor);
            visibleLevelRoadmapNodes.add(new JobRoadmapNodeHitbox(nodeX, baselineY, radius, node));

            boolean topCard = (node.level() & 1) == 1;
            List<String> unlocks = node.unlocks();
            boolean hasUnlocks = unlocks != null && !unlocks.isEmpty();
            int nodeCardWidth = hasUnlocks ? 132 : cardWidth;
            int nodeCardHeight = cardHeight + (milestone ? 4 : 0) + (hasUnlocks ? 12 : 0);
            int cardX = nodeX - nodeCardWidth / 2;
            int cardY = topCard ? baselineY - nodeCardHeight - 32 : baselineY + 24;
            int accent = hasUnlocks ? ORANGE : milestone ? GOLD : reached ? 0xFF68D493 : 0xFF6EAEE0;
            graphics.fill(cardX - 1, cardY - 1, cardX + nodeCardWidth + 1, cardY + nodeCardHeight + 1, 0xFF2F4F73);
            graphics.fill(cardX, cardY, cardX + nodeCardWidth, cardY + nodeCardHeight,
                    milestone ? 0xB81E344A : 0xA81A3149);
            graphics.fill(cardX, cardY, cardX + nodeCardWidth, cardY + (milestone ? 3 : 2), accent);
            int connectorStartY = topCard ? cardY + nodeCardHeight : cardY;
            int connectorEndY = topCard ? baselineY - radius : baselineY + radius;
            graphics.fill(nodeX, Math.min(connectorStartY, connectorEndY), nodeX + 1,
                    Math.max(connectorStartY, connectorEndY), 0x88A9C8E7);

            String title = milestone ? "Level " + node.level() + " Milestone" : "Level " + node.level();
            String required = node.requiredXp() <= 0L
                    ? "Unlock: Start" : "Unlock: " + formatNumber(node.requiredXp()) + " XP";
            String stateLine = current ? "Current" : reached ? "Reached" : "Locked";
            drawFit(graphics, title, cardX + 4, cardY + 6, nodeCardWidth - 8, TEXT);
            drawFit(graphics, required, cardX + 4, cardY + 16, nodeCardWidth - 8, MUTED);
            if (hasUnlocks) {
                drawFit(graphics, jobRoadmapUnlockCardLine(unlocks), cardX + 4, cardY + 27,
                        nodeCardWidth - 8, ORANGE);
            }
            drawFit(graphics, stateLine, cardX + 4, cardY + (hasUnlocks ? 38 : 25),
                    nodeCardWidth - 8, reached ? 0xFF91E1AA : 0xFFB9D1E9);
        }
        graphics.disableScissor();

        int barY = y + height - scrollbarHeight - 2;
        graphics.fill(levelRoadmapViewportX, barY,
                levelRoadmapViewportX + levelRoadmapViewportWidth, barY + scrollbarHeight, 0x5D2D4763);
        if (levelRoadmapMaxScrollX > 0) {
            int thumbWidth = Math.max(28, (int) Math.round(
                    levelRoadmapViewportWidth * (levelRoadmapViewportWidth / (double) levelRoadmapContentWidth)));
            thumbWidth = Math.min(levelRoadmapViewportWidth, thumbWidth);
            int travel = Math.max(1, levelRoadmapViewportWidth - thumbWidth);
            int thumbX = levelRoadmapViewportX + (int) Math.round(
                    levelRoadmapScrollX / (double) levelRoadmapMaxScrollX * travel);
            graphics.fill(thumbX, barY, thumbX + thumbWidth, barY + scrollbarHeight, 0xCC9EC4E8);
            levelRoadmapScrollbarTrackHitbox = new JobRoadmapRectHitbox(levelRoadmapViewportX, barY,
                    levelRoadmapViewportWidth, scrollbarHeight);
            levelRoadmapScrollbarThumbHitbox = new JobRoadmapRectHitbox(thumbX, barY, thumbWidth, scrollbarHeight);
        }

        if (levelRoadmapSelectedNode != null) {
            drawJobLevelRoadmapModal(graphics, levelRoadmapSelectedNode, x, y, width, height);
        }
    }

    private void drawJobLevelRoadmapModal(GuiGraphics graphics, JobLevelRoadmapNodePayload node,
                                          int panelX, int panelY, int panelW, int panelH) {
        if (node == null) {
            levelRoadmapModalCloseHitbox = null;
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 950.0F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xA8101F30);
        List<String> unlocks = node.unlocks() == null ? List.of() : node.unlocks();
        int visibleUnlocks = Math.min(4, unlocks.size());
        int modalW = Math.min(360, Math.max(250, panelW - 36));
        int modalH = unlocks.isEmpty() ? 182 : 194 + visibleUnlocks * 12;
        modalH = Math.min(Math.max(182, modalH), Math.max(182, panelH - 20));
        int modalX = panelX + (panelW - modalW) / 2;
        int modalY = panelY + (panelH - modalH) / 2;
        graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF355474);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xEE19324A);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xCC2A5E94);
        graphics.fill(modalX, modalY + 22, modalX + modalW, modalY + 23, 0x88A8CDEE);

        String state = node.state() == null ? "LOCKED" : node.state();
        String stateLabel = switch (state) {
            case "COMPLETED" -> "Reached";
            case "CURRENT" -> "Current";
            default -> "Locked";
        };
        int stateColor = "COMPLETED".equals(state) || "CURRENT".equals(state) ? 0xFF91E1AA : MUTED;
        drawFit(graphics, "Level " + node.level() + " Unlock Details", modalX + 8, modalY + 7,
                Math.max(80, modalW - 86), TEXT);
        drawFitRight(graphics, stateLabel, modalX + modalW - 8, modalY + 7, 76, stateColor);

        int lineY = modalY + 30;
        drawFit(graphics, "Required XP: " + formatNumber(node.requiredXp()), modalX + 8, lineY,
                modalW - 16, TEXT);
        lineY += 12;
        drawFit(graphics, "Payout Rate: +" + node.payoutBonusPercent() + "%", modalX + 8, lineY,
                modalW - 16, MUTED);
        lineY += 12;
        drawFit(graphics, "Sale XP: +" + node.saleXpBonusPercent() + "%", modalX + 8, lineY,
                modalW - 16, MUTED);
        lineY += 12;
        drawFit(graphics, "Daily Payout Capacity: " + formatCents(node.dailyPayoutCapCents()),
                modalX + 8, lineY, modalW - 16, MUTED);
        lineY += 12;
        drawFit(graphics, "Daily Intake Capacity: " + formatUnits(node.dailyUnitsCapMilli()) + " units",
                modalX + 8, lineY, modalW - 16, MUTED);
        lineY += 12;
        if (!unlocks.isEmpty() && lineY < modalY + modalH - 40) {
            drawFit(graphics, "Career Unlocks:", modalX + 8, lineY, modalW - 16, GOLD);
            lineY += 12;
            for (int index = 0; index < visibleUnlocks && lineY < modalY + modalH - 38; index++) {
                drawFit(graphics, "- " + unlocks.get(index), modalX + 8, lineY, modalW - 16, 0xFFFFE4A3);
                lineY += 12;
            }
        }
        if ("CURRENT".equals(state)) {
            int percent = (int) Math.round(jobRoadmapProgress(node.level()) * 100.0D);
            drawFit(graphics, "Current Progress: " + formatNumber(payload.xp()) + " XP (" + percent + "%)",
                    modalX + 8, lineY, modalW - 16, 0xFF9DE6B2);
            lineY += 12;
        }
        drawFit(graphics, "Click any roadmap node to inspect its unlocks.", modalX + 8, lineY + 4,
                modalW - 16, 0xFFBCD7F3);

        int closeW = 108;
        int closeH = 18;
        int closeX = modalX + (modalW - closeW) / 2;
        int closeY = modalY + modalH - closeH - 8;
        drawRoadmapButton(graphics, closeX, closeY, closeW, closeH, "Back", 0xFF6BAED6);
        levelRoadmapModalCloseHitbox = new JobRoadmapRectHitbox(closeX, closeY, closeW, closeH);
        graphics.pose().popPose();
    }

    private void drawDailyQuestBoard(GuiGraphics graphics, int x, int y, int width, int height) {
        drawCard(graphics, x, y, width, height, GOLD);
        graphics.drawString(font, "DAILY QUESTS", x + 14, y + 9, GOLD, false);
        drawFitRight(graphics, "Resets in: " + dailyQuestResetCountdown(), x + width - 14, y + 9,
                Math.max(100, width / 2), MUTED);
        List<JobDailyQuestPayload> quests = payload.dailyQuests();
        if (quests.isEmpty()) {
            drawFit(graphics, "No daily quests are available yet. Refresh to request a new rotation.",
                    x + 14, y + 32, Math.max(40, width - 28), MUTED);
            return;
        }
        int bodyTop = y + 27;
        int bodyHeight = Math.max(36, height - 34);
        int columns = quests.size() >= 3 && width >= 300 ? 3 : 1;
        int gap = 8;
        int cardWidth = Math.max(72, (width - gap * (columns - 1) - 20) / columns);
        int rows = (quests.size() + columns - 1) / columns;
        int cardHeight = Math.max(42, (bodyHeight - gap * (rows - 1)) / rows);
        for (int index = 0; index < quests.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            drawDailyQuestCard(graphics, quests.get(index),
                    x + 10 + column * (cardWidth + gap),
                    bodyTop + row * (cardHeight + gap), cardWidth, cardHeight, index);
        }
    }

    private void drawDailyQuestCard(GuiGraphics graphics, JobDailyQuestPayload quest,
                                    int x, int y, int width, int height, int index) {
        int accent = quest.completed() ? GREEN : switch (index % 3) {
            case 0 -> CYAN;
            case 1 -> GOLD;
            default -> ORANGE;
        };
        drawCard(graphics, x, y, width, height, accent);
        boolean compact = height < 68 || width < 150;
        if (compact) {
            drawFit(graphics, quest.title(), x + 7, y + 6, Math.max(30, width - 14), TEXT);
            drawFitRight(graphics, quest.completed() ? "COMPLETE" : questProgressText(quest),
                    x + width - 7, y + 21, Math.max(30, width - 14), quest.completed() ? GREEN : accent);
            drawProgressBar(graphics, x + 7, y + height - 11, Math.max(22, width - 14), 7,
                    capProgress(quest.progress(), quest.target()), accent);
            return;
        }
        drawItemIcon(graphics, questIcon(index), x + 7, y + 7, 1.0F);
        drawFit(graphics, quest.title(), x + 30, y + 7, Math.max(32, width - 38), TEXT);
        drawFit(graphics, quest.description(), x + 9, y + 28, Math.max(32, width - 18), MUTED);
        drawFitRight(graphics, quest.completed() ? "COMPLETE" : questProgressText(quest),
                x + width - 9, y + height - 24, Math.max(40, width - 18),
                quest.completed() ? GREEN : accent);
        drawProgressBar(graphics, x + 9, y + height - 13, Math.max(24, width - 18), 7,
                capProgress(quest.progress(), quest.target()), accent);
    }

    private void renderAchievements(GuiGraphics graphics, int mouseX, int mouseY) {
        if (payload == null) {
            return;
        }
        int x = contentLeft;
        int y = contentTop;
        int width = contentWidth;
        int height = Math.max(100, contentBottom - contentTop);
        List<JobAchievementPayload> achievements = payload.achievements();
        long completed = achievements.stream().filter(JobAchievementPayload::completed).count();
        long claimable = achievements.stream()
                .filter(achievement -> achievement.completed() && !achievement.claimed())
                .count();

        boolean compactHeader = width < 500;
        int headerHeight = Math.min(compactHeader ? 70 : 54, height);
        drawCard(graphics, x, y, width, headerHeight, claimable > 0 ? GREEN : GOLD);
        drawItemIcon(graphics, new ItemStack(Items.NETHER_STAR), x + 12, y + 15, 1.25F);
        drawFit(graphics, payload.title().toUpperCase(Locale.ROOT) + " ACHIEVEMENTS", x + 40, y + 10,
                Math.max(80, compactHeader ? width - 50 : width - 250), TEXT);
        drawFit(graphics, "Work, trade, and complete contracts to unlock bank rewards.", x + 40, y + 27,
                Math.max(80, compactHeader ? width - 50 : width - 250), MUTED);
        if (compactHeader) {
            drawBadge(graphics, x + 10, y + 44, 96, 20,
                    completed + " / " + achievements.size() + " DONE", completed == achievements.size() ? GREEN : CYAN);
            drawBadge(graphics, x + 114, y + 44, 104, 20,
                    claimable + " READY", claimable > 0 ? GREEN : DIM);
        } else {
            drawBadge(graphics, x + width - 222, y + 10, 96, 20,
                    completed + " / " + achievements.size() + " DONE", completed == achievements.size() ? GREEN : CYAN);
            drawBadge(graphics, x + width - 118, y + 10, 104, 20,
                    claimable + " READY", claimable > 0 ? GREEN : DIM);
        }

        achievementListLeft = x;
        achievementListTop = y + headerHeight + 8;
        achievementListWidth = width;
        achievementListHeight = Math.max(30, height - headerHeight - 8);
        visibleAchievementCards.clear();
        if (achievements.isEmpty()) {
            drawCard(graphics, x, achievementListTop, width, achievementListHeight, FRAME);
            drawItemIcon(graphics, new ItemStack(Items.BOOK), x + 14, achievementListTop + 18, 1.0F);
            drawFit(graphics, "No achievements are registered for this job yet.", x + 44,
                    achievementListTop + 20, Math.max(50, width - 58), TEXT);
            return;
        }

        int columns = achievementColumns();
        int gap = 8;
        int scrollbarWidth = 10;
        int usableWidth = Math.max(60, width - (achievementRowCount(achievements.size(), columns) > 1
                ? scrollbarWidth : 0));
        int cardWidth = Math.max(80, (usableWidth - gap * (columns - 1)) / columns);
        achievementRowHeight = columns == 1 ? 92 : 86;
        int visibleRows = Math.max(1, achievementListHeight / achievementRowHeight);
        int totalRows = (achievements.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        achievementScrollIndex = Mth.clamp(achievementScrollIndex, 0, maxScroll);

        graphics.fill(x, achievementListTop, x + usableWidth,
                achievementListTop + achievementListHeight, 0x2C18314A);
        graphics.enableScissor(x + 1, achievementListTop + 1,
                x + usableWidth - 1, achievementListTop + achievementListHeight - 1);
        int first = achievementScrollIndex * columns;
        int last = Math.min(achievements.size(), first + visibleRows * columns);
        for (int index = first; index < last; index++) {
            int local = index - first;
            int column = local % columns;
            int row = local / columns;
            int cardX = x + column * (cardWidth + gap);
            int cardY = achievementListTop + row * achievementRowHeight;
            JobAchievementPayload achievement = achievements.get(index);
            int buttonWidth = Math.min(118, Math.max(78, cardWidth / 3));
            int buttonX = cardX + cardWidth - buttonWidth - 8;
            int buttonY = cardY + 8;
            boolean hovered = mouseX >= cardX && mouseX <= cardX + cardWidth
                    && mouseY >= cardY && mouseY <= cardY + achievementRowHeight - 4;
            drawAchievementCard(graphics, achievement, cardX, cardY, cardWidth,
                    achievementRowHeight - 4, buttonX, buttonY, buttonWidth, hovered);
            visibleAchievementCards.add(new JobAchievementHitbox(cardX, cardY, cardWidth,
                    achievementRowHeight - 4, buttonX, buttonY, buttonWidth, 22, achievement));
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = x + usableWidth + 2;
            int trackHeight = achievementListHeight;
            graphics.fill(trackX, achievementListTop, trackX + scrollbarWidth,
                    achievementListTop + trackHeight, 0x66304F70);
            int thumbHeight = Math.max(24, (int) Math.round(trackHeight * (visibleRows / (double) totalRows)));
            int travel = Math.max(1, trackHeight - thumbHeight);
            int thumbY = achievementListTop + (int) Math.round(
                    achievementScrollIndex / (double) maxScroll * travel);
            graphics.fill(trackX, thumbY, trackX + scrollbarWidth, thumbY + thumbHeight, 0xCC8CC3E8);
        }
    }

    private void drawAchievementCard(GuiGraphics graphics, JobAchievementPayload achievement,
                                     int x, int y, int width, int height,
                                     int buttonX, int buttonY, int buttonWidth, boolean hovered) {
        boolean claimable = achievement.completed() && !achievement.claimed();
        int accent = achievement.claimed() ? FRAME : claimable ? GREEN
                : achievement.completed() ? GOLD : achievementAccent(achievement.category());
        if (hovered && !achievement.claimed()) {
            accent = claimable ? 0xFF8CFFBD : CYAN;
        }
        drawCard(graphics, x, y, width, height, accent);
        drawItemIcon(graphics, itemStack(achievement.iconItemId()), x + 8, y + 9, 1.0F);

        int textWidth = Math.max(40, width - buttonWidth - 54);
        drawFit(graphics, achievement.title(), x + 32, y + 7, textWidth, TEXT);
        drawFit(graphics, achievement.category(), x + 32, y + 21, textWidth, accent);
        drawFit(graphics, achievement.description(), x + 8, y + 38,
                Math.max(40, width - 16), MUTED);
        drawFit(graphics, "Reward: " + achievement.rewardSummary(), x + 8, y + 50,
                Math.max(40, width - 16), achievement.claimed() ? DIM : GOLD);
        drawFitRight(graphics, achievementProgressText(achievement), x + width - 8, y + height - 22,
                Math.max(60, width - 16), achievement.completed() ? GREEN : CYAN);
        drawProgressBar(graphics, x + 8, y + height - 12, Math.max(24, width - 16), 7,
                capProgress(achievement.progress(), achievement.target()), accent);

        String action = achievement.claimed() ? "CLAIMED" : claimable ? "CLAIM REWARD" : "IN PROGRESS";
        int actionColor = achievement.claimed() ? DIM : claimable ? GREEN : FRAME;
        drawRoadmapButton(graphics, buttonX, buttonY, buttonWidth, 22, action, actionColor);
    }

    private int achievementColumns() {
        return contentWidth >= 680 ? 2 : 1;
    }

    private int achievementRowCount(int count, int columns) {
        return Math.max(1, (count + columns - 1) / columns);
    }

    private int achievementAccent(String category) {
        if (category == null) return CYAN;
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "CONTRACTS" -> GOLD;
            case "TRADE" -> GREEN;
            case "PRODUCTION" -> ORANGE;
            case "CAREER" -> 0xFFB99BFF;
            case "WOOD FAMILIES" -> 0xFF9EDB86;
            default -> CYAN;
        };
    }

    private String achievementProgressText(JobAchievementPayload achievement) {
        String current = achievement.unit().equals("$")
                ? formatCents(achievement.progress()) : formatNumber(achievement.progress());
        String target = achievement.unit().equals("$")
                ? formatCents(achievement.target()) : formatNumber(achievement.target());
        return current + " / " + target + (achievement.unit().equals("$") ? "" : " " + achievement.unit());
    }

    private ItemStack questIcon(int index) {
        return switch (index % 3) {
            case 0 -> new ItemStack(Items.OAK_LOG);
            case 1 -> new ItemStack(Items.OAK_PLANKS);
            default -> new ItemStack(Items.GOLD_INGOT);
        };
    }

    private String questProgressText(JobDailyQuestPayload quest) {
        return quest.monetary()
                ? formatCents(quest.progress()) + " / " + formatCents(quest.target())
                : formatNumber(quest.progress()) + " / " + formatNumber(quest.target());
    }

    private String dailyQuestResetCountdown() {
        if (payload == null || payload.dailyQuestResetEpochSeconds() <= 0L) {
            return "pending";
        }
        long remaining = Math.max(0L, payload.dailyQuestResetEpochSeconds() - System.currentTimeMillis() / 1000L);
        long hours = remaining / 3600L;
        long minutes = (remaining % 3600L) / 60L;
        long seconds = remaining % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
                : String.format(Locale.ROOT, "%02dm %02ds", minutes, seconds);
    }

    private void renderGuide(GuiGraphics graphics) {
        int gap = 8;
        int columns = panelWidth < 600 ? 1 : 2;
        int cardWidth = Math.max(100, (contentWidth - gap * (columns - 1)) / columns);
        int cardHeight = Math.max(72, (contentBottom - contentTop - gap) / 2);
        List<GuideCard> cards = new ArrayList<>();
        if ("BUYER".equals(payload == null ? "" : payload.mode())) {
            if (isCourierJob()) {
                cards.add(new GuideCard("1  ACCEPT", "Take an available sealed order from the Courier dispatch board.", Items.PAPER, CYAN));
                cards.add(new GuideCard("2  CARRY", "Keep the one-use parcel in your inventory while travelling to its named destination.", Items.CHEST, GREEN));
                cards.add(new GuideCard("3  HAND OFF", "Interact with the destination NPC while carrying the matching parcel.", Items.COMPASS, GOLD));
                cards.add(new GuideCard("4  GET PAID", "The route payout is deposited to your UBS primary account and awards Courier XP.", Items.GOLD_INGOT, ORANGE));
            } else {
                cards.add(new GuideCard("1  COLLECT", "Carry supported " + payload.title().toLowerCase(Locale.ROOT)
                        + " products in your inventory or supported nested containers.", jobIconItem(), CYAN));
                cards.add(new GuideCard("2  SELECT", "Use Catalog to search by item, material family, or product category.", Items.COMPASS, GREEN));
                cards.add(new GuideCard("3  REVIEW", "Choose a quantity, preview the live quote, and confirm the sale when asked.", Items.PAPER, GOLD));
                cards.add(new GuideCard("4  GET PAID", "Accepted products are removed and the payout goes to your UBS primary account.", Items.GOLD_INGOT, ORANGE));
            }
        } else {
            cards.add(new GuideCard("1  APPLY", "Choose " + payload.title() + " from the market. Acceptance is instant and progress is saved.", jobIconItem(), GREEN));
            cards.add(new GuideCard("2  WORK", "Complete supported " + payload.title().toLowerCase(Locale.ROOT)
                    + " activities and collect eligible products.", jobIconItem(), CYAN));
            cards.add(new GuideCard("3  SELL", "Visit the " + payload.title() + " Buyer to see live quotes, item multipliers, and caps.", Items.EMERALD, GOLD));
            cards.add(new GuideCard("4  GROW", "Sales award money and XP. Level progress improves your long-term earning power.", Items.EXPERIENCE_BOTTLE, ORANGE));
        }
        for (int index = 0; index < cards.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            drawGuideCard(graphics, contentLeft + column * (cardWidth + gap),
                    contentTop + row * (cardHeight + gap), cardWidth, cardHeight, cards.get(index));
        }
        drawNoticeCard(graphics, payload == null ? "" : payload.notice(), contentLeft, contentBottom - 34, contentWidth);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG);
        drawBackgroundGrid(graphics);
        graphics.fill(panelLeft - 2, panelTop - 2, panelLeft + panelWidth + 2,
                panelTop + panelHeight + 2, FRAME);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1, panelTop + 58, HEADER);
        graphics.fill(panelLeft + 1, panelTop + 57, panelLeft + panelWidth - 1, panelTop + 59, 0x5542C8FF);
        renderHeader(graphics);

        if (payload != null) {
            switch (activeTab) {
                case JOBS -> renderJobList(graphics, mouseX, mouseY);
                case OVERVIEW -> renderOverview(graphics);
                case ORDERS -> renderCourierOrders(graphics, mouseX, mouseY);
                case CATALOG -> renderCatalog(graphics, mouseX, mouseY);
                case PROGRESS -> renderProgress(graphics);
                case ACHIEVEMENTS -> renderAchievements(graphics, mouseX, mouseY);
                case GUIDE -> renderGuide(graphics);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderNavigationDrawer(graphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics) {
        if (payload == null) return;
        int titleX = panelLeft + 94;
        int right = panelLeft + panelWidth - 14;
        String title = UbsClientTranslations.resolve(
                "BUYER".equals(payload.mode())
                        ? (payload.title().isBlank() ? "Job Buyer" : payload.title() + " Buyer") : "Job Market");
        drawFit(graphics, title, titleX, panelTop + 12, Math.max(50, panelWidth - 248), TEXT);
        drawFit(graphics, payload.title() + "  /  " + activeTab.label,
                titleX, panelTop + 31, Math.max(50, panelWidth - 248), MUTED);
        drawItemIcon(graphics, jobIcon(), right - 154, panelTop + 15, 1.5F);
        drawBadge(graphics, right - 102, panelTop + 12, 88, 20,
                payload.active() ? "ON SHIFT" : payload.readOnly() ? "PREVIEW" : "NOT ACTIVE",
                payload.active() ? GREEN : payload.readOnly() ? GOLD : DIM);
        drawFitRight(graphics, "LV " + payload.level(), right, panelTop + 37, 88, CYAN);
    }

    private void renderNavigationDrawer(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menuProgress <= 0.01F) return;
        int drawerX = panelLeft + 8;
        int drawerY = panelTop + 62;
        int drawerWidth = Math.min(194, panelWidth - 16);
        int drawerHeight = Math.max(60, contentBottom - drawerY + 4);
        int alpha = Math.max(0, Math.min(0xF2, (int) (0xF2 * menuProgress)));
        graphics.fill(drawerX, drawerY, drawerX + drawerWidth, drawerY + drawerHeight,
                (alpha << 24) | 0x000A1A2C);
        graphics.fill(drawerX, drawerY, drawerX + 3, drawerY + drawerHeight,
                (alpha << 24) | 0x0042C8FF);
        graphics.drawString(font, "JOB DASHBOARD", drawerX + 14, drawerY + 10, TEXT, false);
        graphics.drawString(font, "Choose a section", drawerX + 14, drawerY + 25, MUTED, false);
        int y = drawerY + 42;
        for (Tab tab : availableTabs()) {
            boolean hovered = mouseX >= drawerX + 8 && mouseX <= drawerX + drawerWidth - 8
                    && mouseY >= y && mouseY <= y + 32;
            drawCard(graphics, drawerX + 8, y, drawerWidth - 16, 32,
                    tab == activeTab ? GREEN : hovered ? CYAN : FRAME);
            drawFit(graphics, tab.label, drawerX + 20, y + 11, drawerWidth - 34,
                    tab == activeTab ? GREEN : TEXT);
            y += 38;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen is intentionally opaque so the job dashboard stays readable without vanilla blur.
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDelta) {
        if (activeTab == Tab.JOBS && mouseX >= jobListLeft && mouseX <= jobListLeft + jobListWidth
                && mouseY >= jobListTop && mouseY <= jobListTop + jobListHeight) {
            int max = Math.max(0, availableJobs().size() - Math.max(1, jobListHeight / 52));
            int next = Math.max(0, Math.min(max, jobScrollIndex + (scrollDelta < 0 ? 1 : -1)));
            if (next != jobScrollIndex) {
                jobScrollIndex = next;
            }
            return true;
        }
        if (payload != null && activeTab == Tab.ORDERS
                && mouseX >= courierListLeft && mouseX <= courierListLeft + courierListWidth
                && mouseY >= courierListTop && mouseY <= courierListTop + courierListHeight) {
            int visibleRows = Math.max(1, courierListHeight / Math.max(1, courierRowHeight));
            int max = Math.max(0, payload.courierOrders().size() - visibleRows);
            int next = Mth.clamp(courierScrollIndex + (scrollDelta < 0 ? 1 : -1), 0, max);
            if (next != courierScrollIndex) {
                courierScrollIndex = next;
                rebuildKeepingInputs();
            }
            return true;
        }
        if (payload != null && activeTab == Tab.CATALOG
                && mouseX >= catalogListLeft && mouseX <= catalogListLeft + catalogListWidth
                && mouseY >= catalogListTop && mouseY <= catalogListTop + catalogListHeight) {
            int max = Math.max(0, filteredEntries().size() - Math.max(1, catalogListHeight / 38));
            int next = Math.max(0, Math.min(max, scrollIndex + (scrollDelta < 0 ? 1 : -1)));
            if (next != scrollIndex) {
                scrollIndex = next;
                rebuildKeepingInputs();
            }
            return true;
        }
        if (payload != null && activeTab == Tab.PROGRESS
                && levelRoadmapViewportHitbox != null
                && levelRoadmapViewportHitbox.contains(mouseX, mouseY)
                && levelRoadmapMaxScrollX > 0) {
            levelRoadmapScrollX = Mth.clamp(levelRoadmapScrollX + (scrollDelta < 0 ? 104 : -104),
                    0, levelRoadmapMaxScrollX);
            return true;
        }
        if (payload != null && activeTab == Tab.ACHIEVEMENTS
                && mouseX >= achievementListLeft && mouseX <= achievementListLeft + achievementListWidth
                && mouseY >= achievementListTop && mouseY <= achievementListTop + achievementListHeight) {
            int columns = achievementColumns();
            int visibleRows = Math.max(1, achievementListHeight / Math.max(1, achievementRowHeight));
            int totalRows = (payload.achievements().size() + columns - 1) / columns;
            int maxScroll = Math.max(0, totalRows - visibleRows);
            achievementScrollIndex = Mth.clamp(achievementScrollIndex + (scrollDelta < 0 ? 1 : -1),
                    0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menuOpen && menuProgress > 0.85F) {
            Tab selectedTab = navigationTabAt(mouseX, mouseY);
            if (selectedTab != null) {
                activeTab = selectedTab;
                menuOpen = false;
                rebuildKeepingInputs();
                return true;
            }
            if (insideNavigationDrawer(mouseX, mouseY)) {
                return true;
            }
        }
        if (button == 0 && activeTab == Tab.JOBS && mouseX >= jobListLeft && mouseX <= jobListLeft + jobListWidth
                && mouseY >= jobListTop && mouseY <= jobListTop + jobListHeight) {
            int index = (int) ((mouseY - jobListTop) / 52.0D) + jobScrollIndex;
            List<JobMarketEntryPayload> jobs = availableJobs();
            if (index >= 0 && index < jobs.size()) {
                selectedJobId = jobs.get(index).jobId();
                rebuildKeepingInputs();
            }
            return true;
        }
        if (button == 0 && activeTab == Tab.PROGRESS && payload != null) {
            if (levelRoadmapSelectedNode != null) {
                if (levelRoadmapModalCloseHitbox != null
                        && levelRoadmapModalCloseHitbox.contains(mouseX, mouseY)) {
                    levelRoadmapSelectedNode = null;
                    levelRoadmapModalCloseHitbox = null;
                    return true;
                }
                if (levelRoadmapViewportHitbox != null
                        && levelRoadmapViewportHitbox.contains(mouseX, mouseY)) {
                    return true;
                }
            }

            if (levelRoadmapScrollbarThumbHitbox != null
                    && levelRoadmapScrollbarThumbHitbox.contains(mouseX, mouseY)) {
                levelRoadmapScrollbarDragging = true;
                levelRoadmapScrollbarDragOffsetX = (int) Math.round(mouseX)
                        - levelRoadmapScrollbarThumbHitbox.x();
                return true;
            }
            if (levelRoadmapScrollbarTrackHitbox != null
                    && levelRoadmapScrollbarTrackHitbox.contains(mouseX, mouseY)) {
                int thumbWidth = levelRoadmapScrollbarThumbHitbox == null
                        ? Math.max(20, levelRoadmapScrollbarTrackHitbox.width() / 6)
                        : levelRoadmapScrollbarThumbHitbox.width();
                updateLevelRoadmapScrollFromThumbLeft((int) Math.round(mouseX) - (thumbWidth / 2));
                levelRoadmapScrollbarDragging = true;
                levelRoadmapScrollbarDragOffsetX = thumbWidth / 2;
                return true;
            }
            if (levelRoadmapViewportHitbox != null
                    && levelRoadmapViewportHitbox.contains(mouseX, mouseY)) {
                for (JobRoadmapNodeHitbox hitbox : visibleLevelRoadmapNodes) {
                    if (hitbox.contains(mouseX, mouseY)) {
                        levelRoadmapSelectedNode = hitbox.node();
                        return true;
                    }
                }
                return true;
            }
        }
        if (button == 0 && activeTab == Tab.ACHIEVEMENTS && payload != null) {
            for (JobAchievementHitbox hitbox : visibleAchievementCards) {
                if (!hitbox.contains(mouseX, mouseY)) {
                    continue;
                }
                JobAchievementPayload achievement = hitbox.achievement();
                if (hitbox.claimContains(mouseX, mouseY)
                        && achievement.completed() && !achievement.claimed()) {
                    send("CLAIM_ACHIEVEMENT", achievement.id(), 0, 0L, true);
                }
                return true;
            }
            if (mouseX >= achievementListLeft && mouseX <= achievementListLeft + achievementListWidth
                    && mouseY >= achievementListTop && mouseY <= achievementListTop + achievementListHeight) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            levelRoadmapScrollbarDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && levelRoadmapScrollbarDragging && activeTab == Tab.PROGRESS) {
            int thumbLeft = (int) Math.round(mouseX) - levelRoadmapScrollbarDragOffsetX;
            updateLevelRoadmapScrollFromThumbLeft(thumbLeft);
            return true;
        }
        if ((button == 0 || button == 1)
                && activeTab == Tab.PROGRESS
                && levelRoadmapSelectedNode == null
                && levelRoadmapMaxScrollX > 0
                && levelRoadmapViewportHitbox != null
                && levelRoadmapViewportHitbox.contains(mouseX, mouseY)) {
            int previous = levelRoadmapScrollX;
            int deltaX = (int) Math.round(dragX);
            if (deltaX != 0) {
                levelRoadmapScrollX = Mth.clamp(levelRoadmapScrollX - deltaX, 0, levelRoadmapMaxScrollX);
            }
            return previous != levelRoadmapScrollX;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void preview(int amount) {
        if (selectedEntry() == null || payload.readOnly()) return;
        int safeAmount = amount > 0 ? amount : parseQuantity();
        send("PREVIEW", selectedEntry().itemId(), safeAmount, 0L, false);
    }

    private void previewStack() {
        if (selectedEntry() == null || payload.readOnly()) return;
        send("PREVIEW_STACK", selectedEntry().itemId(), -1, 0L, false);
    }

    private void previewAll() {
        if (selectedEntry() == null || payload.readOnly()) return;
        send("PREVIEW", selectedEntry().itemId(), 0, 0L, false);
    }

    private int parseQuantity() {
        if (quantityInput == null) return 1;
        try {
            return Math.max(1, Integer.parseInt(quantityInput.getValue().trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void send(String action, String itemId, int amount, long expectedQuote, boolean confirmed) {
        PacketDistributor.sendToServer(new JobActionPayload(action, itemId, amount, expectedQuote, confirmed));
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.getConnection() != null) {
            send("CLOSE", "", 0, 0L, false);
        }
        super.onClose();
    }

    private void cycleCategory() {
        List<String> categories = new ArrayList<>(new LinkedHashSet<>(payload.catalog().stream()
                .map(JobCatalogEntryPayload::category).sorted().toList()));
        categories.add(0, "ALL");
        int index = categories.indexOf(categoryFilter);
        categoryFilter = categories.get((index + 1) % categories.size());
        scrollIndex = 0;
        rebuildKeepingInputs();
    }

    private void toggleCarriedFilter() {
        carriedOnlyFilter = !carriedOnlyFilter;
        scrollIndex = 0;
        rebuildKeepingInputs();
    }

    private String categoryLabel() {
        return categoryFilter.length() > 11 ? categoryFilter.substring(0, 11) : categoryFilter;
    }

    private List<JobCatalogEntryPayload> filteredEntries() {
        if (payload == null) return List.of();
        String search = searchInput == null ? "" : searchInput.getValue().trim().toLowerCase(Locale.ROOT);
        return payload.catalog().stream()
                .filter(entry -> "ALL".equals(categoryFilter) || categoryFilter.equals(entry.category()))
                .filter(entry -> !carriedOnlyFilter || entry.ownedCount() > 0)
                .filter(entry -> search.isBlank()
                        || entry.displayName().toLowerCase(Locale.ROOT).contains(search)
                        || entry.itemId().toLowerCase(Locale.ROOT).contains(search)
                        || entry.family().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparing(JobCatalogEntryPayload::displayName))
                .toList();
    }

    private JobCatalogEntryPayload selectedEntry() {
        if (payload == null) return null;
        for (JobCatalogEntryPayload entry : payload.catalog()) {
            if (entry.itemId().equals(selectedItemId)) return entry;
        }
        return payload.catalog().isEmpty() ? null : payload.catalog().get(0);
    }

    private void selectPendingOrFirst() {
        if (payload == null) return;
        if (payload.confirmationRequired() && !payload.pendingItemId().isBlank()) {
            selectedItemId = payload.pendingItemId();
            return;
        }
        if (selectedEntry() == null && !payload.catalog().isEmpty()) {
            selectedItemId = payload.catalog().get(0).itemId();
        }
    }

    private void rebuildKeepingInputs() {
        String search = searchInput == null ? "" : searchInput.getValue();
        String quantity = quantityInput == null ? "1" : quantityInput.getValue();
        rebuild();
        if (searchInput != null) searchInput.setValue(search);
        if (quantityInput != null) quantityInput.setValue(quantity);
    }

    private DesktopButton addButton(int x, int y, int width, int height, String label, int accent,
                                    java.util.function.Consumer<DesktopButton> action, String tooltip) {
        DesktopButton button = new DesktopButton(x, y, Math.max(1, width), Math.max(1, height),
                Component.literal(label), accent, action);
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return addRenderableWidget(button);
    }

    private List<Tab> availableTabs() {
        if (payload != null && "BUYER".equals(payload.mode())) {
            if (isCourierJob()) {
                return List.of(Tab.ORDERS, Tab.PROGRESS, Tab.ACHIEVEMENTS, Tab.GUIDE);
            }
            return List.of(Tab.CATALOG, Tab.PROGRESS, Tab.ACHIEVEMENTS, Tab.GUIDE);
        }
        if (isJoblessMarket()) {
            return List.of(Tab.JOBS, Tab.GUIDE);
        }
        return List.of(Tab.PROGRESS, Tab.ACHIEVEMENTS, Tab.GUIDE);
    }

    private Tab defaultTab() {
        if (payload != null && "BUYER".equals(payload.mode())) {
            return isCourierJob() ? Tab.ORDERS : Tab.CATALOG;
        }
        return isJoblessMarket() ? Tab.JOBS : Tab.PROGRESS;
    }

    private boolean isCourierJob() {
        return payload != null && "BUYER".equals(payload.mode())
                && "courier".equalsIgnoreCase(payload.jobId());
    }

    private ItemStack itemStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ItemStack jobIcon() {
        if ("BUYER".equals(payload == null ? "" : payload.mode())) {
            if (payload != null) {
                for (JobMarketEntryPayload job : payload.availableJobs()) {
                    if (job.jobId().equals(payload.jobId())) {
                        ItemStack icon = itemStack(job.iconItemId());
                        if (!icon.isEmpty()) return icon;
                    }
                }
            }
            return new ItemStack(Items.EMERALD);
        }
        JobMarketEntryPayload selected = selectedJob();
        return selected == null ? new ItemStack(Items.IRON_AXE) : itemStack(selected.iconItemId());
    }

    private Item jobIconItem() {
        ItemStack stack = jobIcon();
        return stack.isEmpty() ? Items.PAPER : stack.getItem();
    }

    private boolean isJoblessMarket() {
        return payload != null && "MARKET".equals(payload.mode()) && !payload.active();
    }

    private List<JobMarketEntryPayload> availableJobs() {
        if (payload == null) {
            return List.of();
        }
        return payload.availableJobs().stream()
                .sorted(Comparator.comparing(JobMarketEntryPayload::displayName))
                .toList();
    }

    private JobMarketEntryPayload selectedJob() {
        for (JobMarketEntryPayload job : availableJobs()) {
            if (job.jobId().equals(selectedJobId)) {
                return job;
            }
        }
        List<JobMarketEntryPayload> jobs = availableJobs();
        return jobs.isEmpty() ? null : jobs.getFirst();
    }

    private void selectJobOrFirst() {
        if (!selectedJobId.isBlank()) {
            for (JobMarketEntryPayload job : availableJobs()) {
                if (job.jobId().equals(selectedJobId)) {
                    return;
                }
            }
        }
        List<JobMarketEntryPayload> jobs = availableJobs();
        if (!jobs.isEmpty()) {
            selectedJobId = jobs.getFirst().jobId();
        }
    }

    private float jobXpProgress(JobMarketEntryPayload job) {
        if (job == null || job.level() >= 100 || job.xpToNext() <= 0L) {
            return 1.0F;
        }
        double total = Math.max(1.0D, (double) job.xp() + job.xpToNext());
        return Mth.clamp((float) (job.xp() / total), 0.0F, 1.0F);
    }

    private boolean insideNavigationDrawer(double mouseX, double mouseY) {
        int drawerX = panelLeft + 8;
        int drawerY = panelTop + 62;
        int drawerWidth = Math.min(194, panelWidth - 16);
        int drawerHeight = Math.max(60, contentBottom - drawerY + 4);
        return mouseX >= drawerX && mouseX <= drawerX + drawerWidth
                && mouseY >= drawerY && mouseY <= drawerY + drawerHeight;
    }

    private Tab navigationTabAt(double mouseX, double mouseY) {
        int drawerX = panelLeft + 8;
        int drawerWidth = Math.min(194, panelWidth - 16);
        int y = panelTop + 104;
        for (Tab tab : availableTabs()) {
            if (mouseX >= drawerX + 8 && mouseX <= drawerX + drawerWidth - 8
                    && mouseY >= y && mouseY <= y + 32) {
                return tab;
            }
            y += 38;
        }
        return null;
    }

    private boolean hasMoneySpotlight(JobCatalogEntryPayload entry) {
        return entry != null && payload != null && payload.moneySpotlightBonusPercent() > 0
                && !payload.moneySpotlightFamily().isBlank()
                && payload.moneySpotlightFamily().equalsIgnoreCase(entry.family());
    }

    private boolean hasXpSpotlight(JobCatalogEntryPayload entry) {
        return entry != null && payload != null && payload.xpSpotlightBonusPercent() > 0
                && !payload.xpSpotlightFamily().isBlank()
                && payload.xpSpotlightFamily().equalsIgnoreCase(entry.family());
    }

    private boolean hasAnySpotlight(JobCatalogEntryPayload entry) {
        return hasMoneySpotlight(entry) || hasXpSpotlight(entry);
    }

    private String spotlightSummary() {
        if (payload == null || payload.moneySpotlightFamily().isBlank()) return "None today";
        return displayFamily(payload.moneySpotlightFamily()) + " +" + payload.moneySpotlightBonusPercent() + "%";
    }

    private float xpProgress() {
        if (payload == null) return 0.0F;
        if (!payload.levelRoadmap().isEmpty()) {
            return jobRoadmapProgress(payload.level());
        }
        if (payload.level() >= 100 || payload.xpToNext() <= 0L) return 1.0F;
        double total = Math.max(1.0D, (double) payload.xp() + payload.xpToNext());
        return Mth.clamp((float) (payload.xp() / total), 0.0F, 1.0F);
    }

    private JobLevelRoadmapNodePayload currentRoadmapNode() {
        if (payload == null || payload.levelRoadmap().isEmpty()) return null;
        int currentLevel = Mth.clamp(payload.level(), 1, 100);
        for (JobLevelRoadmapNodePayload node : payload.levelRoadmap()) {
            if (node.level() == currentLevel) return node;
        }
        return payload.levelRoadmap().getLast();
    }

    private long roadmapRequiredXp(List<JobLevelRoadmapNodePayload> nodes, int level) {
        if (nodes == null || nodes.isEmpty()) return 0L;
        int target = Mth.clamp(level, 1, 100);
        for (JobLevelRoadmapNodePayload node : nodes) {
            if (node.level() == target) return Math.max(0L, node.requiredXp());
        }
        return Math.max(0L, nodes.getLast().requiredXp());
    }

    private float jobRoadmapProgress(int level) {
        if (payload == null || payload.levelRoadmap().isEmpty() || level >= 100) return 1.0F;
        long floor = roadmapRequiredXp(payload.levelRoadmap(), level);
        long next = roadmapRequiredXp(payload.levelRoadmap(), level + 1);
        if (next <= floor) return 1.0F;
        return Mth.clamp((float) ((payload.xp() - floor) / (double) (next - floor)), 0.0F, 1.0F);
    }

    private boolean isJobRoadmapMilestoneLevel(int level) {
        return level > 0 && level % 10 == 0;
    }

    private String jobRoadmapUnlockCardLine(List<String> unlocks) {
        if (unlocks == null || unlocks.isEmpty()) return "";
        String first = unlocks.getFirst();
        return unlocks.size() > 1 ? first + " +" + (unlocks.size() - 1) : first;
    }

    private void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int safeRadius = Math.max(1, radius);
        for (int dy = -safeRadius; dy <= safeRadius; dy++) {
            int row = (int) Math.floor(Math.sqrt(Math.max(0, (safeRadius * safeRadius) - (dy * dy))));
            graphics.fill(centerX - row, centerY + dy, centerX + row + 1, centerY + dy + 1, color);
        }
    }

    private void drawRoadmapButton(GuiGraphics graphics, int x, int y, int width, int height,
                                   String label, int accent) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, 0xFF2E5277);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xB01D3D5E);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, accent);
        graphics.drawCenteredString(font, fitToWidth(label, Math.max(10, width - 8)),
                x + width / 2, y + 4, TEXT);
    }

    private void updateLevelRoadmapScrollFromThumbLeft(int thumbLeft) {
        if (levelRoadmapScrollbarTrackHitbox == null || levelRoadmapScrollbarThumbHitbox == null) {
            return;
        }
        if (levelRoadmapMaxScrollX <= 0) {
            levelRoadmapScrollX = 0;
            return;
        }
        int travel = Math.max(1, levelRoadmapScrollbarTrackHitbox.width()
                - levelRoadmapScrollbarThumbHitbox.width());
        int minThumbLeft = levelRoadmapScrollbarTrackHitbox.x();
        int maxThumbLeft = minThumbLeft + travel;
        int clampedThumbLeft = Mth.clamp(thumbLeft, minThumbLeft, maxThumbLeft);
        double ratio = (clampedThumbLeft - minThumbLeft) / (double) travel;
        levelRoadmapScrollX = Mth.clamp((int) Math.round(ratio * levelRoadmapMaxScrollX),
                0, levelRoadmapMaxScrollX);
    }

    private static float capProgress(long current, long cap) {
        if (cap <= 0L) return 0.0F;
        return Mth.clamp((float) (Math.max(0L, current) / (double) cap), 0.0F, 1.0F);
    }

    private void drawBackgroundGrid(GuiGraphics graphics) {
        int spacing = 32;
        for (int x = 0; x < width; x += spacing) graphics.fill(x, 0, x + 1, height, 0x160F2A42);
        for (int y = 0; y < height; y += spacing) graphics.fill(0, y, width, y + 1, 0x160F2A42);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, accent);
        if (width > 2 && height > 2) graphics.fill(x + 2, y + 1, x + width - 1, y + height - 1, CARD);
        if (width > 6 && height > 4) graphics.fill(x + 4, y + 2, x + width - 2, y + 4, 0x443FCAFF);
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, int width, int height, Metric metric) {
        drawCard(graphics, x, y, width, height, metric.accent());
        drawItemIcon(graphics, new ItemStack(metric.item()), x + 8, y + 9, 1.0F);
        int textX = x + 30;
        drawFit(graphics, metric.label(), textX, y + 7, Math.max(30, width - 36), DIM);
        drawFit(graphics, metric.value(), textX, y + 21, Math.max(30, width - 36), TEXT);
        drawFit(graphics, metric.detail(), textX, y + height - 14, Math.max(30, width - 36), metric.accent());
    }

    private void drawSmallStat(GuiGraphics graphics, int x, int y, int width, String label,
                               String value, int color) {
        drawCard(graphics, x, y, width, 42, FRAME);
        drawFit(graphics, label, x + 7, y + 6, Math.max(25, width - 12), DIM);
        drawFit(graphics, value, x + 7, y + 21, Math.max(25, width - 12), color);
    }

    private void drawLimitCard(GuiGraphics graphics, int x, int y, int width, int height, LimitCard card) {
        drawCard(graphics, x, y, width, height, card.accent());
        drawItemIcon(graphics, new ItemStack(card.item()), x + 10, y + 10, 1.0F);
        if (height >= 42) {
            drawFit(graphics, card.label(), x + 34, y + 8, Math.max(35, width - 42), DIM);
            drawFit(graphics, card.value(), x + 34, y + 25, Math.max(35, width - 42), TEXT);
            drawProgressBar(graphics, x + 10, y + height - 16, Math.max(25, width - 20), 7,
                    card.progress(), card.accent());
        }
    }

    private void drawGuideCard(GuiGraphics graphics, int x, int y, int width, int height, GuideCard card) {
        drawCard(graphics, x, y, width, height, card.accent());
        drawItemIcon(graphics, new ItemStack(card.icon()), x + 14, y + 14, 1.25F);
        drawFit(graphics, card.title(), x + 48, y + 14, Math.max(40, width - 62), TEXT);
        drawWrapped(graphics, card.body(), x + 14, y + 46, Math.max(40, width - 28), MUTED, 12, 4);
    }

    private void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height,
                                 float progress, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, TRACK);
        int filled = (int) ((width - 2) * Mth.clamp(progress, 0.0F, 1.0F));
        if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, color);
        if (height > 3) graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x5542C8FF);
    }

    private void drawBadge(GuiGraphics graphics, int x, int y, int width, int height, String label, int color) {
        if (width <= 2 || height <= 2) return;
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x + 2, y + 2, x + width - 1, y + height - 1, CARD_ALT);
        drawCenteredFit(graphics, label, x + width / 2, y + Math.max(3, (height - 8) / 2),
                Math.max(10, width - 8), color);
    }

    private void drawNoticeCard(GuiGraphics graphics, String notice, int x, int y, int width) {
        if (notice == null || notice.isBlank() || width <= 0) return;
        String lower = notice.toLowerCase(Locale.ROOT);
        int color = lower.contains("cancel") || lower.contains("error") || lower.contains("only")
                || lower.contains("unavailable") ? RED : GREEN;
        drawCard(graphics, x, y, width, 30, color);
        drawFit(graphics, notice, x + 12, y + 10, Math.max(30, width - 24), color);
    }

    private void drawItemIcon(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        int size = Math.max(1, (int) Math.ceil(16.0F * scale));
        if (isCoveredByNavigationDrawer(x, y, size, size)) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /** Minecraft item rendering is depth-elevated, so exclusion is more reliable than draw-order masking. */
    private boolean isCoveredByNavigationDrawer(int x, int y, int width, int height) {
        if (menuProgress <= 0.01F) {
            return false;
        }
        int drawerX = panelLeft + 8;
        int drawerY = panelTop + 62;
        int drawerWidth = Math.min(194, panelWidth - 16);
        int drawerHeight = Math.max(60, contentBottom - drawerY + 4);
        return x < drawerX + drawerWidth && x + width > drawerX
                && y < drawerY + drawerHeight && y + height > drawerY;
    }

    private void drawFit(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, fitToWidth(value, maxWidth), x, y, color, false);
    }

    private void drawFitRight(GuiGraphics graphics, String value, int rightX, int y, int maxWidth, int color) {
        String fitted = fitToWidth(value, maxWidth);
        graphics.drawString(font, fitted, rightX - font.width(fitted), y, color, false);
    }

    private void drawCenteredFit(GuiGraphics graphics, String value, int centerX, int y,
                                 int maxWidth, int color) {
        String fitted = fitToWidth(value, maxWidth);
        graphics.drawCenteredString(font, fitted, centerX, y, color);
    }

    private void drawWrapped(GuiGraphics graphics, String value, int x, int y, int width,
                             int color, int gap, int maxLines) {
        if (value == null || value.isBlank()) return;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(value), Math.max(20, width));
        int current = y;
        int count = 0;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            if (count++ >= Math.max(1, maxLines)) break;
            graphics.drawString(font, line, x, current, color, false);
            current += gap;
        }
    }

    private String fitToWidth(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end)) + ellipsisWidth > maxWidth) end--;
        return value.substring(0, end) + ellipsis;
    }

    private static String formatCents(long cents) {
        long safe = Math.max(0L, cents);
        return "$" + safe / 100L + "." + String.format(Locale.ROOT, "%02d", safe % 100L);
    }

    private static String formatUnits(long unitsMilli) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0L, unitsMilli) / 1000.0D);
    }

    private static String formatNumber(long number) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, number));
    }

    private static String displayFamily(String family) {
        if (family == null || family.isBlank()) return "none";
        return Character.toUpperCase(family.charAt(0)) + family.substring(1);
    }

    private static String displayCategory(String category) {
        if (category == null || category.isBlank()) return "Item";
        String normalized = category.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private record Metric(String label, String value, String detail, Item item, int accent) {
    }

    private record GuideCard(String title, String body, Item icon, int accent) {
    }

    private record LimitCard(String label, String value, float progress, Item item, int accent) {
    }
}
