package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceScope;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceStatistics;
import net.austizz.ultimatebankingsystem.api.ApiTransactionResult;
import net.austizz.ultimatebankingsystem.api.job.ApiJobSaleResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.entity.custom.JobNpcEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.item.CourierParcelItem;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.JobActionPayload;
import net.austizz.ultimatebankingsystem.network.JobCatalogEntryPayload;
import net.austizz.ultimatebankingsystem.network.CourierDeliveryStatePayload;
import net.austizz.ultimatebankingsystem.network.CourierOrderPayload;
import net.austizz.ultimatebankingsystem.network.JobDailyQuestPayload;
import net.austizz.ultimatebankingsystem.network.JobLevelRoadmapNodePayload;
import net.austizz.ultimatebankingsystem.network.JobMarketEntryPayload;
import net.austizz.ultimatebankingsystem.network.JobOpenPayload;
import net.austizz.ultimatebankingsystem.network.JobRewardPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.shop.ShopMarketPriceService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-side owner of job applications, market rotation, activity tracking, and job turn-ins. */
public final class JobService {
    public static final String LUMBERJACK_ID = "lumberjack";
    public static final String MINER_ID = "miner";
    public static final String FISHERMAN_ID = "fisherman";
    public static final String FARMER_ID = "farmer";
    public static final String HUNTER_ID = "hunter";
    public static final String BLACKSMITH_ID = "blacksmith";
    public static final String COURIER_ID = "courier";
    public static final String CHEF_ID = "chef";
    public static final String HERBALIST_ID = "herbalist";
    public static final String SALVAGER_ID = "salvager";
    public static final String TAILOR_ID = "tailor";
    private static final int MAX_NESTED_INVENTORY_DEPTH = 4;
    private static final long XP_PER_RAW_UNIT = 10L;
    private static final int COURIER_ORDER_TARGET = 5;
    private static final int COURIER_MAX_ACTIVE_ORDERS = 3;
    private static final long COURIER_ORDER_LIFETIME_MILLIS = 15L * 60L * 1000L;
    private static final Item[] COURIER_MANIFEST_ITEMS = {
            Items.PAPER, Items.EMERALD, Items.BREAD, Items.IRON_INGOT, Items.REDSTONE, Items.LEATHER,
            Items.DIAMOND, Items.AMETHYST_SHARD, Items.ENDER_PEARL, Items.BLAZE_ROD,
            Items.PRISMARINE_SHARD, Items.GLOW_BERRIES, Items.HONEY_BOTTLE, Items.NETHERITE_SCRAP
    };
    private static final Map<MinecraftServer, Map<UUID, String>> SESSION_MODES = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, String>> SESSION_JOB_IDS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> READ_ONLY_SESSIONS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, UUID>> COURIER_DISPATCH_NPCS = new WeakHashMap<>();

    private JobService() {
    }

    public static synchronized void onServerStarting(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        JobSavedData data = JobSavedData.get(server);
        LumberjackCatalogService.refresh(server);
        JobCatalogService.refresh(server);
        ensureMarketDay(server, data);
    }

    public static synchronized void onServerStopping(MinecraftServer server) {
        if (server != null) {
            SESSION_MODES.remove(server);
            SESSION_JOB_IDS.remove(server);
            READ_ONLY_SESSIONS.remove(server);
            COURIER_DISPATCH_NPCS.remove(server);
            LumberjackCatalogService.clear(server);
            JobCatalogService.clear(server);
        }
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 20L != 0L) {
            return;
        }
        JobSavedData data = JobSavedData.get(server);
        ensureMarketDay(server, data);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JobSavedData.PlayerRecord record = data.player(player.getUUID());
            boolean changed = reconcileNpcRemovalPayout(server, player, record);
            changed |= expireCourierOrders(player, record);
            if (changed) {
                data.setDirty();
                refreshCourierDesk(player, "Courier board updated.");
            }
            sendCourierDeliveryState(player, record);
        }
    }

    public static void refreshCatalog(MinecraftServer server) {
        LumberjackCatalogService.refresh(server);
        JobCatalogService.refresh(server);
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if ("BUYER".equals(sessionMode(server, player.getUUID()))) {
                    openBuyer(player, isReadOnly(server, player.getUUID()), "Job catalog refreshed.",
                            sessionJobId(server, player.getUUID()));
                }
            }
        }
    }

    public static void openMarket(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        setSessionMode(player.getServer(), player.getUUID(), "MARKET");
        clearReadOnly(player.getServer(), player.getUUID());
        sendSnapshot(player, "MARKET", "", false, "", 0, 0L, false);
    }

    public static void openBuyer(ServerPlayer player, boolean readOnly, String notice) {
        String jobId = player == null || player.getServer() == null ? LUMBERJACK_ID
                : activeJobId(player.getServer(), player.getUUID());
        openBuyer(player, readOnly, notice, jobId);
    }

    public static void openBuyer(ServerPlayer player, boolean readOnly, String notice, String requestedJobId) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        setSessionMode(server, player.getUUID(), "BUYER");
        String jobId = JobRegistry.get(requestedJobId) == null ? activeJobId(server, player.getUUID())
                : requestedJobId.trim().toLowerCase(Locale.ROOT);
        setSessionJobId(server, player.getUUID(), jobId.isBlank() ? LUMBERJACK_ID : jobId);
        if (!COURIER_ID.equals(jobId)) {
            clearCourierDispatch(server, player.getUUID());
        }
        if (readOnly) {
            READ_ONLY_SESSIONS.computeIfAbsent(server, ignored -> new HashSet<>()).add(player.getUUID());
        } else {
            clearReadOnly(server, player.getUUID());
        }
        sendSnapshot(player, "BUYER", notice == null ? "" : notice, false, "", 0, 0L, false);
    }

    /** Opens a Courier dispatch desk bound to the NPC the player interacted with. */
    public static void openCourierDispatch(ServerPlayer player, JobNpcEntity dispatcher) {
        if (player == null || player.getServer() == null || dispatcher == null
                || !dispatcher.isCourierDispatcher()) {
            return;
        }
        MinecraftServer server = player.getServer();
        setSessionMode(server, player.getUUID(), "BUYER");
        setSessionJobId(server, player.getUUID(), COURIER_ID);
        clearReadOnly(server, player.getUUID());
        COURIER_DISPATCH_NPCS.computeIfAbsent(server, ignored -> new HashMap<>())
                .put(player.getUUID(), dispatcher.getUUID());
        sendSnapshot(player, "BUYER", "Dispatch board synced with this Courier NPC.", false,
                "", 0, 0L, false);
    }

    /**
     * Called by every job NPC before opening its normal desk. Delivery is deliberately limited to the
     * stack in the hand that performed the interaction. A sealed assigned parcel or the exact manifest
     * item can complete the route; an identical item elsewhere in the inventory cannot consume the click.
     */
    public static boolean tryDeliverCourierParcel(ServerPlayer player, JobNpcEntity destination) {
        return tryDeliverCourierParcel(player, destination, InteractionHand.MAIN_HAND);
    }

    public static boolean tryDeliverCourierParcel(ServerPlayer player, JobNpcEntity destination,
                                                  InteractionHand hand) {
        if (player == null || destination == null || player.getServer() == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (!COURIER_ID.equals(record.activeJob())) {
            return false;
        }
        if (hand == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        int slot = courierHeldSlot(player, hand);
        if (slot < 0) {
            return false;
        }
        ItemStack parcel = courierParcelAt(player, slot);
        boolean sealedParcel = CourierParcelItem.isParcel(parcel);
        CourierOrder order;
        if (sealedParcel) {
            UUID orderId = parseUuid(CourierParcelItem.orderId(parcel));
            if (orderId == null) {
                player.sendSystemMessage(UbsTranslations.literal("This parcel has no valid Courier order."));
                return true;
            }
            order = record.courierOrder(orderId);
            if (order == null || !order.isAccepted()) {
                player.sendSystemMessage(UbsTranslations.literal("This Courier order is no longer active."));
                return true;
            }
        } else {
            order = resolveCourierManifestOrder(record, destination, held);
            if (order == null) {
                return false;
            }
        }
        long now = System.currentTimeMillis();
        if (order.isExpired(now)) {
            order.expire();
            if (sealedParcel) {
                removeCourierParcel(player, slot);
            } else {
                removeCourierParcels(player, order.id());
            }
            data.setDirty();
            player.sendSystemMessage(UbsTranslations.literal("The Courier deadline expired. The parcel was rejected."));
            return true;
        }
        if (order.destinationNpcId() == null || !order.destinationNpcId().equals(destination.getUUID())) {
            player.sendSystemMessage(UbsTranslations.literal("Wrong destination. Deliver this parcel to "
                    + order.destinationName() + "."));
            return true;
        }
        JobSavedData.JobProgress progress = record.progress(COURIER_ID);
        progress.resetForDay(currentDay(server));
        long remainingPayout = Math.max(0L,
                JobProgression.dailyPayoutCapCents(progress.level()) - progress.dailyMoneyCents());
        if (order.rewardCents() > remainingPayout) {
            player.sendSystemMessage(UbsTranslations.literal(
                    "Your daily Courier payout limit is reached. Complete this route another day."));
            return true;
        }

        ItemStack removed = sealedParcel
                ? removeCourierParcel(player, slot)
                : removeCourierManifestItems(player, hand, order.quantity());
        if (removed.isEmpty()) {
            player.sendSystemMessage(UbsTranslations.literal("The delivery items could not be secured. Try again."));
            return true;
        }
        long xpGain = Math.max(10L, Math.min(2_000L, order.rewardCents() / 100L));
        xpGain = Math.max(10L, Math.min(3_000L,
                xpGain * Math.max(50, Math.min(300, order.xpMultiplierPercent())) / 100L));
        xpGain = applyJobXpBonus(xpGain, progress.level());
        ApiTransactionResult payout = UltimateBankingApiProvider.get().depositToPrimary(
                player.getUUID(), BigDecimal.valueOf(order.rewardCents(), 2),
                "COURIER_DELIVERY:" + order.id());
        if (!payout.success()) {
            giveCourierParcelBack(player, removed);
            player.sendSystemMessage(UbsTranslations.literal("Delivery cancelled: " + payout.reason()
                    + ". The parcel was returned."));
            return true;
        }
        if (!sealedParcel) {
            removeCourierParcels(player, order.id());
        }

        order.deliver();
        int nextLevel = levelForXp(saturatingAdd(progress.xp(), xpGain));
        progress.recordDelivery(order.rewardCents(), xpGain, nextLevel);
        progress.recordActivity("deliveries_completed", "npc_routes", 1L);
        progress.recordActivity("courier_payout_cents", "npc_routes", order.rewardCents());
        advanceActivityQuests(progress, COURIER_ID, "deliveries_completed", 1L);
        data.addServerDailyMoney(order.rewardCents());
        data.setDirty();
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(UbsTranslations.literal("Delivery complete: " + order.itemName() + " x"
                + order.quantity() + " delivered to " + order.destinationName() + ". Paid "
                + formatCents(order.rewardCents()) + ". +" + xpGain + " XP."));
        sendCourierDeliveryState(player, record);
        return true;
    }

    private static CourierOrder resolveCourierManifestOrder(JobSavedData.PlayerRecord record,
                                                              JobNpcEntity destination,
                                                              ItemStack held) {
        if (record == null || destination == null || held == null || held.isEmpty()) {
            return null;
        }
        return record.courierOrders().values().stream()
                .filter(CourierOrder::isAccepted)
                .filter(order -> destination.getUUID().equals(order.destinationNpcId()))
                .filter(order -> courierManifestMatches(order, held))
                .min(Comparator.comparingLong(CourierOrder::expiresAtMillis)
                        .thenComparing(order -> order.id().toString()))
                .orElse(null);
    }

    static boolean courierManifestMatches(CourierOrder order, ItemStack held) {
        if (order == null || !order.isAccepted() || held == null || held.isEmpty()
                || held.getCount() < order.quantity()) {
            return false;
        }
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());
        return heldId != null && courierManifestMatches(
                order.status(), order.itemId(), order.quantity(), heldId.toString(), held.getCount());
    }

    static boolean courierManifestMatches(String orderStatus, String orderItemId, int orderQuantity,
                                           String heldItemId, int heldCount) {
        return CourierDeliveryMatcher.matches(orderStatus, orderItemId, orderQuantity,
                heldItemId, heldCount);
    }

    private static ItemStack removeCourierManifestItems(ServerPlayer player, InteractionHand hand,
                                                         int quantity) {
        if (player == null || hand == null || quantity <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getCount() < quantity) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = held.copy();
        removed.setCount(quantity);
        held.shrink(quantity);
        player.getInventory().setChanged();
        return removed;
    }

    public static void handleAction(ServerPlayer player, JobActionPayload payload) {
        if (player == null || payload == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        String action = payload.action() == null ? "" : payload.action().trim().toUpperCase(Locale.ROOT);
        String mode = sessionMode(server, player.getUUID());
        if (mode.isBlank()) {
            mode = "MARKET";
            setSessionMode(server, player.getUUID(), mode);
        }

        try {
            switch (action) {
                case "APPLY" -> apply(player, payload.itemId());
                case "LEAVE" -> leave(player);
                case "CLOSE" -> closeSession(player);
                case "REFRESH" -> sendSnapshot(player, mode, "", false, "", 0, 0L, false);
                case "PREVIEW" -> previewSale(player, payload.itemId(), payload.amount());
                case "PREVIEW_STACK" -> previewSale(player, payload.itemId(), -1);
                case "SELL" -> executeSale(player, payload.itemId(), payload.amount(),
                        payload.expectedQuoteCents(), payload.confirmed());
                case "COURIER_ACCEPT" -> sendSnapshot(player, "BUYER",
                        acceptCourierOrder(player, payload.itemId()), false, "", 0, 0L, false);
                case "COURIER_CANCEL" -> sendSnapshot(player, "BUYER",
                        cancelCourierOrder(player, payload.itemId()), false, "", 0, 0L, false);
                case "CLAIM_ACHIEVEMENT" -> sendSnapshot(player, mode,
                        JobAchievementService.claim(player, payload.itemId()), false, "", 0, 0L, false);
                default -> sendSnapshot(player, mode, "Unknown job action.", false, "", 0, 0L, false);
            }
        } catch (RuntimeException exception) {
            sendSnapshot(player, mode, safeMessage(exception.getMessage()), false, "", 0, 0L, false);
        }
    }

    private static void apply(ServerPlayer player, String requestedJob) {
        String jobId = requestedJob == null || requestedJob.isBlank()
                ? LUMBERJACK_ID : requestedJob.trim().toLowerCase(Locale.ROOT);
        JobDefinition definition = JobRegistry.get(jobId);
        if (definition == null) {
            sendSnapshot(player, "MARKET", "That job is not available.", false, "", 0, 0L, false);
            return;
        }
        JobSavedData data = JobSavedData.get(player.getServer());
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (!record.activeJob().isBlank() && !record.activeJob().equals(jobId)) {
            sendSnapshot(player, "MARKET", "Leave your current job before applying for another one.", false, "", 0, 0L, false);
            return;
        }
        record.setActiveJob(jobId);
        data.setDirty();
        clearReadOnly(player.getServer(), player.getUUID());
        sendSnapshot(player, "MARKET", "Application accepted. You are now a "
                + definition.displayName() + ".", false, "", 0, 0L, false);
    }

    private static void leave(ServerPlayer player) {
        JobSavedData data = JobSavedData.get(player.getServer());
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (record.activeJob().isBlank()) {
            sendSnapshot(player, "MARKET", "You do not have an active job.", false, "", 0, 0L, false);
            return;
        }
        boolean canceledRoutes = COURIER_ID.equals(record.activeJob())
                && cancelActiveCourierOrders(player, record);
        record.setActiveJob("");
        clearCourierDispatch(player.getServer(), player.getUUID());
        data.setDirty();
        sendCourierDeliveryState(player, record);
        sendSnapshot(player, "MARKET", canceledRoutes
                ? "You left the job. Open Courier routes were canceled and your level and XP were preserved."
                : "You left the job. Your level and XP are preserved.", false, "", 0, 0L, false);
    }

    private static void closeSession(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUUID();
        synchronized (SESSION_MODES) {
            Map<UUID, String> sessions = SESSION_MODES.get(server);
            if (sessions != null) {
                sessions.remove(playerId);
            }
        }
        synchronized (SESSION_JOB_IDS) {
            Map<UUID, String> sessions = SESSION_JOB_IDS.get(server);
            if (sessions != null) {
                sessions.remove(playerId);
            }
        }
        clearReadOnly(server, playerId);
        clearCourierDispatch(server, playerId);
    }

    private static void previewSale(ServerPlayer player, String itemId, int requestedAmount) {
        SaleContext context = prepareSale(player, itemId, requestedAmount);
        if (!context.valid()) {
            sendSnapshot(player, "BUYER", context.message(), false, "", 0, 0L, false);
            return;
        }
        int amount = Math.min(context.requestedAmount(), context.maximumSellable());
        if (amount <= 0) {
            sendSnapshot(player, "BUYER", context.capacityMessage(), false, "", 0, 0L, false);
            return;
        }
        long total = multiplyCapped(context.quoteCents(), amount);
        String message = "Preview: sell " + amount + " x " + context.entry().displayName()
                + " for " + formatCents(total) + ". Press Confirm Sale to commit.";
        sendSnapshot(player, "BUYER", message, true, context.entry().itemId().toString(), amount,
                context.quoteCents(), false);
    }

    private static void executeSale(ServerPlayer player, String itemId, int requestedAmount,
                                    long expectedQuoteCents, boolean confirmed) {
        SaleContext context = prepareSale(player, itemId, requestedAmount);
        if (!context.valid()) {
            sendSnapshot(player, "BUYER", context.message(), false, "", 0, 0L, false);
            return;
        }
        if (!confirmed) {
            previewSale(player, itemId, requestedAmount);
            return;
        }
        if (expectedQuoteCents <= 0L || expectedQuoteCents != context.quoteCents()) {
            sendSnapshot(player, "BUYER",
                    "The live quote changed. Review the new quote before confirming again.", true,
                    context.entry().itemId().toString(), Math.min(context.requestedAmount(), context.maximumSellable()),
                    context.quoteCents(), false);
            return;
        }
        int amount = Math.min(context.requestedAmount(), context.maximumSellable());
        if (amount <= 0) {
            sendSnapshot(player, "BUYER", context.capacityMessage(), false, "", 0, 0L, false);
            return;
        }

        ApiJobSaleResult result = commitSale(player, context, amount);
        String message = result.success()
                ? "Sold " + result.quantity() + " x " + context.entry().displayName() + " for "
                + formatCents(result.payout().movePointRight(2).longValue()) + ". +" + result.xpGained() + " XP."
                : result.reason();
        sendSnapshot(player, "BUYER", message, false, "", 0, 0L, false);
    }

    /** Controlled API entry point. It accepts a live server player so all normal sale checks remain active. */
    public static ApiJobSaleResult sellFromApi(ServerPlayer player, String itemId, int requestedAmount) {
        return sellFromApi(player, "", itemId, requestedAmount);
    }

    /** Generic API entry point for a registered job; it does not alter the player's buyer session. */
    public static ApiJobSaleResult sellFromApi(ServerPlayer player, String jobId,
                                               String itemId, int requestedAmount) {
        SaleContext context = prepareSale(player, itemId, requestedAmount, jobId);
        if (!context.valid()) {
            return ApiJobSaleResult.fail(context.message());
        }
        int amount = Math.min(context.requestedAmount(), context.maximumSellable());
        if (amount <= 0) {
            return ApiJobSaleResult.fail(context.capacityMessage());
        }
        return commitSale(player, context, amount);
    }

    private static ApiJobSaleResult commitSale(ServerPlayer player, SaleContext context, int amount) {
        List<InventorySource> sources = collectSources(player, context.entry().itemId());
        int available = sources.stream().mapToInt(InventorySource::available).sum();
        if (available < amount) {
            return ApiJobSaleResult.fail("Your carried goods changed. Review the sale again.");
        }

        List<InventorySource> changed = new ArrayList<>();
        int remaining = amount;
        for (InventorySource source : sources) {
            if (remaining <= 0) break;
            int take = Math.min(remaining, source.available());
            if (take <= 0) continue;
            if (!source.remove(take)) {
                changed.forEach(InventorySource::restore);
                return ApiJobSaleResult.fail("The inventory could not be changed safely; nothing was sold.");
            }
            changed.add(source);
            remaining -= take;
        }
        if (remaining > 0) {
            changed.forEach(InventorySource::restore);
            return ApiJobSaleResult.fail("The inventory changed during the sale; nothing was sold.");
        }

        long totalCents = multiplyCapped(context.quoteCents(), amount);
        ApiTransactionResult payout = UltimateBankingApiProvider.get().depositToPrimary(
                player.getUUID(), BigDecimal.valueOf(totalCents, 2),
                "JOB_SALE:" + context.jobId() + ":" + context.entry().itemId());
        if (!payout.success()) {
            changed.forEach(InventorySource::restore);
            return ApiJobSaleResult.fail("Sale cancelled: " + payout.reason() + ". Your items were returned.");
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        JobSavedData data = JobSavedData.get(player.getServer());
        ensureMarketDay(player.getServer(), data);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        JobSavedData.JobProgress progress = record.progress(context.jobId());
        long units = (long) context.entry().rawUnitsMilli() * amount;
        long baseXpGain = Math.max(1L, (units * XP_PER_RAW_UNIT * context.entry().xpWeightPercent()) / 100_000L);
        long xpGain = applyXpSpotlight(baseXpGain, context.entry(), data);
        xpGain = applyJobXpBonus(xpGain, progress.level());
        int nextLevel = levelForXp(saturatingAdd(progress.xp(), xpGain));
        progress.addSale(totalCents, units, xpGain, nextLevel);
        progress.recordSale(context.entry().family(), context.entry().category(), amount);
        long questDay = JobQuestService.currentRotationDay();
        List<JobQuestService.Definition> dailyQuestDefinitions =
                JobQuestService.forDay(context.jobId(), questDay);
        progress.ensureDailyQuests(questDay, dailyQuestDefinitions);
        for (JobQuestService.Definition definition : dailyQuestDefinitions) {
            long before = progress.dailyQuestProgress(definition.id());
            progress.addDailyQuestProgress(definition.id(),
                    JobQuestService.progressForSale(definition, context.entry().category(), amount, totalCents));
            if (before < definition.target() && progress.dailyQuestProgress(definition.id()) >= definition.target()) {
                progress.recordDailyQuestCompletion();
            }
        }
        long day = currentDay(player.getServer());
        data.addSupply(day, supplyKey(context.jobId(), context.entry().family()), units);
        data.addServerDailyMoney(totalCents);
        data.addServerDailyUnits(units);
        data.setDirty();
        return new ApiJobSaleResult(true, "", context.entry().itemId().toString(), amount,
                BigDecimal.valueOf(totalCents, 2), xpGain);
    }

    /** Compatibility entry point for integrations that already report Lumberjack log breaks. */
    public static void recordRawLogBreak(ServerPlayer player, String family) {
        if (player == null || player.getServer() == null || player.isCreative() || player.isSpectator()
                || family == null || family.isBlank()) {
            return;
        }
        MinecraftServer server = player.getServer();
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (!LUMBERJACK_ID.equals(record.activeJob())) {
            return;
        }
        JobSavedData.JobProgress progress = record.progress(LUMBERJACK_ID);
        progress.recordRawLogBreak(family);
        progress.recordActivity("logs_broken", family, 1L);
        advanceActivityQuests(progress, LUMBERJACK_ID, "logs_broken", 1L);
        data.setDirty();
    }

    /** Records a supported world break for whichever job the player currently holds. */
    public static void recordBlockBreak(ServerPlayer player, net.minecraft.world.level.block.state.BlockState state) {
        if (player == null || player.getServer() == null || player.isCreative() || player.isSpectator()
                || state == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        JobSavedData.PlayerRecord record = JobSavedData.get(server).player(player.getUUID());
        String jobId = record.activeJob();
        if (jobId.isBlank()) {
            return;
        }
        String activityMetric = JobCatalogService.blockActivityMetric(jobId, state);
        if (activityMetric.isBlank()) {
            return;
        }
        String family = materialFamily(state);
        if (LUMBERJACK_ID.equals(jobId)) {
            // Keep the legacy totals and the shared activity/quest path in one place.
            recordRawLogBreak(player, family);
            return;
        }
        recordActivity(player, activityMetric, family, 1L);
    }

    /** Records catches after NeoForge has created the fishing drops. */
    public static void recordFishingCatch(ServerPlayer player, List<ItemStack> drops) {
        if (player == null || drops == null || drops.isEmpty()) return;
        int count = 0;
        String family = "";
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null || JobCatalogService.find(player.getServer(), FISHERMAN_ID,
                    itemId.toString()) == null) continue;
            count = saturatingIntAdd(count, stack.getCount());
            if (family.isBlank()) family = itemFamily(stack);
        }
        if (count <= 0) return;
        recordActivity(player, "fish_caught", family, count);
    }

    /** Records eligible livestock goods from a player-caused passive-animal drop event. */
    public static void recordFarmerAnimalDrops(ServerPlayer player,
                                               net.minecraft.world.entity.Entity source,
                                               List<ItemStack> drops) {
        if (!(source instanceof net.minecraft.world.entity.animal.Animal)
                || player == null || player.getServer() == null || drops == null || drops.isEmpty()) {
            return;
        }
        int count = 0;
        String family = "";
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null || JobCatalogService.find(player.getServer(), FARMER_ID, itemId.toString()) == null) {
                continue;
            }
            count = saturatingIntAdd(count, stack.getCount());
            if (family.isBlank()) family = itemFamily(stack);
        }
        if (count <= 0) return;
        recordActivity(player, "animals_harvested", family, count);
    }

    /** Records player-caused mob drops for the Hunter career without taking ownership of the drops. */
    public static void recordHunterDrops(ServerPlayer player, List<ItemStack> drops) {
        if (player == null || drops == null || drops.isEmpty()) return;
        int count = 0;
        String family = "";
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null || JobCatalogService.find(player.getServer(), HUNTER_ID,
                    itemId.toString()) == null) continue;
            count = saturatingIntAdd(count, stack.getCount());
            if (family.isBlank()) family = itemFamily(stack);
        }
        if (count <= 0) return;
        recordActivity(player, "drops_recovered", family, count);
    }

    /** Records a completed craft for job types whose work is an output rather than a block break. */
    public static void recordCrafted(ServerPlayer player, ItemStack crafted) {
        if (player == null || crafted == null || crafted.isEmpty()) return;
        String jobId = activeJobId(player.getServer(), player.getUUID());
        String metric = JobCatalogService.craftActivityMetric(player.getServer(), jobId, crafted);
        if (!metric.isBlank()) {
            recordActivity(player, metric, itemFamily(crafted), crafted.getCount());
        }
    }

    /** Records a non-sale activity and advances only the active job's daily board. */
    public static void recordActivity(ServerPlayer player, String metric, String family, long amount) {
        if (player == null || player.getServer() == null || player.isCreative() || player.isSpectator()
                || metric == null || metric.isBlank() || amount <= 0L) {
            return;
        }
        JobSavedData data = JobSavedData.get(player.getServer());
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        String jobId = record.activeJob();
        if (jobId.isBlank() || JobRegistry.get(jobId) == null) {
            return;
        }
        JobSavedData.JobProgress progress = record.progress(jobId);
        progress.recordActivity(metric, family, amount);
        advanceActivityQuests(progress, jobId, metric, amount);
        data.setDirty();
    }

    private static void advanceActivityQuests(JobSavedData.JobProgress progress, String jobId,
                                              String metric, long amount) {
        long questDay = JobQuestService.currentRotationDay();
        List<JobQuestService.Definition> definitions = JobQuestService.forDay(jobId, questDay);
        progress.ensureDailyQuests(questDay, definitions);
        for (JobQuestService.Definition definition : definitions) {
            long before = progress.dailyQuestProgress(definition.id());
            progress.addDailyQuestProgress(definition.id(),
                    JobQuestService.progressForActivity(definition, metric, amount));
            if (before < definition.target() && progress.dailyQuestProgress(definition.id()) >= definition.target()) {
                progress.recordDailyQuestCompletion();
            }
        }
    }

    private static String materialFamily(net.minecraft.world.level.block.state.BlockState state) {
        if (state == null || state.getBlock().asItem() == net.minecraft.world.item.Items.AIR) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
        if (id == null) {
            return "";
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        int separator = path.indexOf('_');
        return separator > 0 ? path.substring(0, separator) : path;
    }

    private static String itemFamily(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return "";
        String path = id.getPath().toLowerCase(Locale.ROOT);
        int separator = path.indexOf('_');
        return separator > 0 ? path.substring(0, separator) : path;
    }

    private static SaleContext prepareSale(ServerPlayer player, String itemId, int requestedAmount) {
        return prepareSale(player, itemId, requestedAmount, "");
    }

    private static SaleContext prepareSale(ServerPlayer player, String itemId, int requestedAmount,
                                           String requestedJobId) {
        if (player == null || player.getServer() == null) {
            return SaleContext.invalid("Job service is unavailable.");
        }
        MinecraftServer server = player.getServer();
        if (isReadOnly(server, player.getUUID())) {
            return SaleContext.invalid("Admin preview mode is read-only.");
        }
        JobSavedData data = JobSavedData.get(server);
        ensureMarketDay(server, data);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        record.resetForDay(currentDay(server));
        String jobId = requestedJobId == null ? "" : requestedJobId.trim().toLowerCase(Locale.ROOT);
        if (jobId.isBlank()) {
            jobId = sessionJobId(server, player.getUUID());
        }
        if (jobId.isBlank()) {
            jobId = record.activeJob();
        }
        JobDefinition definition = JobRegistry.get(jobId);
        if (definition == null) {
            return SaleContext.invalid("This job buyer is not configured.");
        }
        if (!jobId.equals(record.activeJob())) {
            return SaleContext.invalid("Only an active " + definition.displayName()
                    + " can sell goods here.");
        }
        if (COURIER_ID.equals(jobId)) {
            return SaleContext.invalid("Courier work uses NPC delivery orders. Open the dispatch board to accept a route.");
        }
        JobSavedData.JobProgress progress = record.progress(jobId);
        if (UltimateBankingApiProvider.get().getPrimaryAccountId(player.getUUID()).isEmpty()) {
            return SaleContext.invalid("Create or select a UBS primary account before selling goods. Your items were not changed.");
        }
        JobCatalogService.CatalogEntry entry = JobCatalogService.find(server, jobId, itemId);
        if (entry == null) {
            return SaleContext.invalid("That item is not a supported " + definition.displayName()
                    + " product.");
        }
        int available = collectSources(player, entry.itemId()).stream()
                .mapToInt(InventorySource::available).sum();
        long quote = JobCatalogService.liveQuoteCents(server, jobId, entry, data, progress, currentDay(server));
        if (quote <= 0L) {
            return SaleContext.invalid("The live quote is unavailable. Try again shortly.");
        }
        int requested = requestedAmount < 0
                ? Math.min(available, maxStackSize(entry))
                : requestedAmount == 0 ? available : Math.min(requestedAmount, available);
        int max = maximumSellable(progress, data, entry, quote);
        String capacityMessage = max <= 0
                ? "A daily payout or normalized-goods limit has been reached. Try again next Minecraft day."
                : "The daily limit allows at most " + max + " of that item right now.";
        return new SaleContext(true, "", capacityMessage, jobId, entry, Math.max(0, requested), max, quote);
    }

    private static int maxStackSize(JobCatalogService.CatalogEntry entry) {
        if (entry == null) return 1;
        ItemStack prototype = new ItemStack(entry.itemId() == null
                ? net.minecraft.world.item.Items.AIR : BuiltInRegistries.ITEM.get(entry.itemId()));
        return Math.max(1, prototype.getMaxStackSize());
    }

    private static int maximumSellable(JobSavedData.JobProgress player,
                                       JobSavedData data,
                                       JobCatalogService.CatalogEntry entry,
                                       long quoteCents) {
        long playerMoney = Math.max(0L, JobProgression.dailyPayoutCapCents(player.level())
                - player.dailyMoneyCents());
        long serverMoney = Math.max(0L, (long) Config.LUMBERJACK_SERVER_DAILY_PAYOUT_CENTS.get()
                - data.serverDailyMoneyCents());
        long playerUnits = Math.max(0L, JobProgression.dailyUnitsCapMilli(player.level())
                - player.dailyUnitsMilli());
        long serverUnits = Math.max(0L, (long) Config.LUMBERJACK_SERVER_DAILY_UNITS.get() * 1000L
                - data.serverDailyUnitsMilli());
        long byMoney = Math.min(playerMoney, serverMoney) / Math.max(1L, quoteCents);
        long byUnits = Math.min(playerUnits, serverUnits) / Math.max(1L, entry.rawUnitsMilli());
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, Math.min(byMoney, byUnits)));
    }

    private static String acceptCourierOrder(ServerPlayer player, String rawOrderId) {
        if (player == null || player.getServer() == null) {
            return "Courier service is unavailable.";
        }
        MinecraftServer server = player.getServer();
        if (isReadOnly(server, player.getUUID())) {
            return "Admin preview mode is read-only.";
        }
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (!COURIER_ID.equals(record.activeJob())) {
            return "Apply for the Courier job before accepting routes.";
        }
        CourierOrder order = resolveCourierOrder(record, rawOrderId);
        if (order == null) {
            return "That Courier order is invalid.";
        }
        UUID dispatcherId = courierDispatchNpcId(server, player.getUUID());
        Entity dispatcherEntity = findEntity(server, dispatcherId);
        if (!(dispatcherEntity instanceof JobNpcEntity dispatcher)
                || !dispatcher.isCourierDispatcher()
                || !player.level().dimension().equals(dispatcher.level().dimension())
                || player.distanceToSqr(dispatcher) > 9.0D * 9.0D) {
            return "Return to a Courier dispatcher before accepting an order.";
        }

        expireCourierOrders(player, record);
        if (!order.isAvailable() || !dispatcher.getUUID().equals(order.originNpcId())) {
            return "That route is no longer available at this dispatcher.";
        }
        long now = System.currentTimeMillis();
        if (order.isExpired(now)) {
            order.expire();
            data.setDirty();
            return "That route has expired.";
        }
        long accepted = record.courierOrders().values().stream()
                .filter(CourierOrder::isAccepted)
                .count();
        if (accepted >= COURIER_MAX_ACTIVE_ORDERS) {
            return "You can carry at most " + COURIER_MAX_ACTIVE_ORDERS + " active Courier routes.";
        }
        if (!hasCourierParcelSpace(player)) {
            return "Free an inventory slot before accepting this parcel.";
        }

        ItemStack parcel = new ItemStack(ModItems.COURIER_PARCEL.get());
        CourierParcelItem.writeAssignment(parcel, order.id().toString(), order.destinationName(),
                order.itemName(), order.quantity());
        if (!player.getInventory().add(parcel)) {
            return "The parcel could not be placed in your inventory.";
        }
        order.accept(now, COURIER_ORDER_LIFETIME_MILLIS);
        data.setDirty();
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        sendCourierDeliveryState(player, record);
        return "Route accepted: collect " + order.itemName() + " x" + order.quantity()
                + " and deliver it to " + order.destinationName() + ".";
    }

    /** Cancels one accepted route owned by the player. Full UUIDs remain accepted for API compatibility. */
    public static String cancelCourierOrder(ServerPlayer player, String rawOrderId) {
        if (player == null || player.getServer() == null) {
            return "Courier service is unavailable.";
        }
        JobSavedData data = JobSavedData.get(player.getServer());
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (!COURIER_ID.equals(record.activeJob())) {
            return "Apply for the Courier job before canceling routes.";
        }
        expireCourierOrders(player, record);
        CourierOrder order = resolveCourierOrder(record, rawOrderId);
        if (order == null || !order.isAccepted()) {
            return "That delivery ID is not one of your active routes.";
        }
        order.cancel();
        removeCourierParcels(player, order.id());
        data.setDirty();
        sendCourierDeliveryState(player, record);
        return "Delivery " + order.shortId() + " canceled. No payout was issued.";
    }

    /** Returns only active accepted route IDs for Brigadier suggestions and external integrations. */
    public static List<String> activeCourierDeliveryIds(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return List.of();
        }
        JobSavedData data = JobSavedData.get(player.getServer());
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (!COURIER_ID.equals(record.activeJob())) {
            return List.of();
        }
        if (expireCourierOrders(player, record)) {
            data.setDirty();
        }
        return record.courierOrders().values().stream()
                .filter(CourierOrder::isAccepted)
                .sorted(Comparator.comparingLong(CourierOrder::expiresAtMillis))
                .map(CourierOrder::shortId)
                .distinct()
                .toList();
    }

    /** Cancels every open Courier offer/parcel when a player leaves the job. */
    private static boolean cancelActiveCourierOrders(ServerPlayer player, JobSavedData.PlayerRecord record) {
        if (record == null) {
            return false;
        }
        boolean changed = false;
        for (CourierOrder order : record.courierOrders().values()) {
            if (!order.isFinished()) {
                order.cancel();
                if (player != null) {
                    removeCourierParcels(player, order.id());
                }
                changed = true;
            }
        }
        return changed;
    }

    /** Called before an operator discards a job NPC. Accepted routes are compensated exactly once. */
    public static void onCourierNpcRemoved(JobNpcEntity npc) {
        if (npc == null || !(npc.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        JobSavedData data = JobSavedData.get(server);
        boolean changed = false;
        Set<UUID> affectedOnlinePlayers = new HashSet<>();
        for (Map.Entry<UUID, JobSavedData.PlayerRecord> entry : data.players().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            JobSavedData.PlayerRecord record = entry.getValue();
            for (CourierOrder order : record.courierOrders().values()) {
                if (order.isFinished() || (!npc.getUUID().equals(order.originNpcId())
                        && !npc.getUUID().equals(order.destinationNpcId()))) {
                    continue;
                }
                boolean wasAccepted = order.isAccepted();
                order.markNpcRemoved();
                if (player != null) {
                    removeCourierParcels(player, order.id());
                }
                if (wasAccepted) {
                    settleNpcRemovalPayout(server, player, entry.getKey(), order);
                }
                if (player != null) {
                    player.sendSystemMessage(UbsTranslations.literal(
                            "Delivery " + order.shortId() + " was closed because a Courier NPC was removed."
                                    + (order.npcRemovalPayoutSettled()
                                    ? " Its promised payout was credited."
                                    : " Its payout will be retried automatically.")));
                    affectedOnlinePlayers.add(player.getUUID());
                }
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
        for (UUID playerId : affectedOnlinePlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                refreshCourierDesk(player, "Courier board updated because a Courier NPC was removed.");
                sendCourierDeliveryState(player, data.player(playerId));
            }
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        if (COURIER_ID.equals(record.activeJob()) && cancelActiveCourierOrders(player, record)) {
            data.setDirty();
        }
        clearCourierDispatch(server, player.getUUID());
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        boolean changed = reconcileNpcRemovalPayout(server, player, record);
        changed |= expireCourierOrders(player, record);
        if (changed) {
            data.setDirty();
        }
        sendCourierDeliveryState(player, record);
    }

    private static CourierOrder resolveCourierOrder(JobSavedData.PlayerRecord record, String rawOrderId) {
        if (record == null || rawOrderId == null || rawOrderId.isBlank()) {
            return null;
        }
        String requested = rawOrderId.trim();
        UUID fullId = parseUuid(requested);
        if (fullId != null) {
            return record.courierOrder(fullId);
        }
        List<CourierOrder> matches = record.courierOrders().values().stream()
                .filter(order -> !order.isFinished())
                .filter(order -> order.shortId().equalsIgnoreCase(requested))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static void sendCourierDeliveryState(ServerPlayer player, JobSavedData.PlayerRecord record) {
        if (player == null || record == null) {
            return;
        }
        List<CourierOrderPayload> deliveries = record.courierOrders().values().stream()
                .filter(CourierOrder::isAccepted)
                .filter(order -> !order.isExpired(System.currentTimeMillis()))
                .sorted(Comparator.comparingLong(CourierOrder::expiresAtMillis))
                .limit(CourierDeliveryStatePayload.MAX_DELIVERIES)
                .map(JobService::courierOrderSnapshot)
                .toList();
        PacketDistributor.sendToPlayer(player, deliveries.isEmpty()
                ? CourierDeliveryStatePayload.inactive()
                : new CourierDeliveryStatePayload(true, deliveries));
    }

    private static void refreshCourierDesk(ServerPlayer player, String notice) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUUID();
        if (!COURIER_ID.equals(sessionJobId(server, playerId))
                || !"BUYER".equals(sessionMode(server, playerId))) {
            return;
        }
        sendSnapshot(player, "BUYER", notice == null ? "" : notice,
                false, "", 0, 0L, false);
    }

    private static CourierOrderPayload courierOrderSnapshot(CourierOrder order) {
        long seconds = Math.max(0L, (order.expiresAtMillis() - System.currentTimeMillis() + 999L) / 1000L);
        return new CourierOrderPayload(
                order.id().toString(), order.shortId(), order.originName(), order.destinationName(),
                order.destinationDimension(), order.destinationX(), order.destinationY(), order.destinationZ(),
                order.itemId(), order.itemName(), order.quantity(), order.rewardCents(), seconds,
                order.status(), order.routeDistanceBlocks(), order.marketValueCents(),
                order.moneyMultiplierPercent(), order.xpMultiplierPercent(), order.contractTier());
    }

    private static boolean reconcileNpcRemovalPayout(MinecraftServer server, ServerPlayer player,
                                                      JobSavedData.PlayerRecord record) {
        if (server == null || record == null) {
            return false;
        }
        boolean changed = false;
        UUID playerId = player == null ? null : player.getUUID();
        for (CourierOrder order : record.courierOrders().values()) {
            if (!CourierOrder.NPC_REMOVED.equals(order.status())) {
                continue;
            }
            if (player != null) {
                removeCourierParcels(player, order.id());
            }
            if (!order.npcRemovalPayoutSettled() && playerId != null) {
                changed |= settleNpcRemovalPayout(server, player, playerId, order);
            }
        }
        return changed;
    }

    private static boolean settleNpcRemovalPayout(MinecraftServer server, ServerPlayer player,
                                                   UUID playerId, CourierOrder order) {
        if (server == null || playerId == null || order == null
                || (!order.isAccepted() && !CourierOrder.NPC_REMOVED.equals(order.status()))) {
            return false;
        }
        if (order.npcRemovalPayoutSettled()) {
            return false;
        }
        ApiTransactionResult payout = UltimateBankingApiProvider.get().depositToPrimary(
                playerId, BigDecimal.valueOf(order.rewardCents(), 2),
                "COURIER_NPC_REMOVED:" + order.id());
        if (!payout.success()) {
            return false;
        }
        order.markNpcRemovalPayoutSettled();
        if (player != null) {
            player.sendSystemMessage(UbsTranslations.literal("Delivery " + order.shortId()
                    + " was canceled by NPC removal. Promised payout credited: "
                    + formatCents(order.rewardCents()) + "."));
        }
        return true;
    }

    private static List<CourierOrderPayload> courierOrderSnapshots(MinecraftServer server,
                                                                    ServerPlayer player,
                                                                    JobSavedData.PlayerRecord record) {
        if (server == null || player == null || record == null) {
            return List.of();
        }
        boolean changed = expireCourierOrders(player, record);
        ensureCourierOrders(server, player, record);
        UUID dispatcherId = courierDispatchNpcId(server, player.getUUID());
        List<CourierOrderPayload> snapshots = record.courierOrders().values().stream()
                .filter(order -> !order.isFinished())
                // Accepted routes belong to the player, not to the dispatcher currently open.
                // Available offers remain scoped to the dispatcher that generated them.
                .filter(order -> order.isAccepted()
                        || (dispatcherId != null && dispatcherId.equals(order.originNpcId())))
                .sorted(Comparator.comparingInt((CourierOrder order) -> order.isAccepted() ? 0 : 1)
                        .thenComparingLong(CourierOrder::expiresAtMillis)
                        .thenComparing(order -> order.id().toString()))
                .limit(JobOpenPayload.MAX_COURIER_ORDERS)
                .map(order -> {
                    long seconds = Math.max(0L, (order.expiresAtMillis()
                            - System.currentTimeMillis() + 999L) / 1000L);
                    return new CourierOrderPayload(
                            order.id().toString(), order.shortId(), order.originName(), order.destinationName(),
                            order.destinationDimension(), order.destinationX(), order.destinationY(),
                            order.destinationZ(), order.itemId(), order.itemName(), order.quantity(),
                            order.rewardCents(), seconds, order.status(), order.routeDistanceBlocks(),
                            order.marketValueCents(), order.moneyMultiplierPercent(),
                            order.xpMultiplierPercent(), order.contractTier());
                })
                .toList();
        if (changed) {
            JobSavedData.get(server).setDirty();
        }
        return snapshots;
    }

    /** Creates a small personal offer board for each placed Courier dispatcher. */
    private static void ensureCourierOrders(MinecraftServer server, ServerPlayer player,
                                             JobSavedData.PlayerRecord record) {
        if (server == null || player == null || record == null) {
            return;
        }
        UUID dispatcherId = courierDispatchNpcId(server, player.getUUID());
        Entity entity = findEntity(server, dispatcherId);
        if (!(entity instanceof JobNpcEntity dispatcher) || !dispatcher.isCourierDispatcher()) {
            return;
        }
        List<JobNpcEntity> destinations = courierNodes(server).stream()
                .filter(node -> !node.getUUID().equals(dispatcher.getUUID()))
                .toList();
        if (destinations.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int existing = (int) record.courierOrders().values().stream()
                .filter(order -> dispatcher.getUUID().equals(order.originNpcId()))
                .filter(order -> !order.isFinished() && !order.isExpired(now))
                .count();
        if (existing >= COURIER_ORDER_TARGET) {
            return;
        }

        Set<String> existingRoutes = new HashSet<>();
        record.courierOrders().values().forEach(order -> {
            if (!order.isFinished() && dispatcher.getUUID().equals(order.originNpcId())) {
                existingRoutes.add(order.destinationNpcId() + ":" + order.itemId());
            }
        });
        Random random = new Random(now ^ player.getUUID().getMostSignificantBits()
                ^ dispatcher.getUUID().getLeastSignificantBits());
        int attempts = 0;
        while (existing < COURIER_ORDER_TARGET && attempts++ < COURIER_ORDER_TARGET * 8) {
            JobNpcEntity destination = destinations.get(random.nextInt(destinations.size()));
            Item item = COURIER_MANIFEST_ITEMS[random.nextInt(COURIER_MANIFEST_ITEMS.length)];
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null) {
                continue;
            }
            String routeKey = destination.getUUID() + ":" + itemId;
            if (!existingRoutes.add(routeKey)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            boolean exotic = item == Items.DIAMOND || item == Items.AMETHYST_SHARD
                    || item == Items.ENDER_PEARL || item == Items.BLAZE_ROD
                    || item == Items.PRISMARINE_SHARD || item == Items.NETHERITE_SCRAP;
            int quantity = exotic ? 1 + random.nextInt(2) : 1 + random.nextInt(4);
            int distance = dispatcher.level().dimension().equals(destination.level().dimension())
                    ? Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                    Math.round(Math.sqrt(dispatcher.distanceToSqr(destination))))) : -1;
            ResourceLocation itemKey = itemId;
            ApiShopPriceStatistics market = ShopMarketPriceService.statistics(
                    server, stack, ApiShopPriceScope.REGULAR);
            long marketValue = market.available() && market.medianPriceCents() > 0L
                    ? market.medianPriceCents() : CourierRewardCalculator.fallbackMarketValueCents(itemKey.toString());
            CourierRewardCalculator.Modifiers modifiers = CourierRewardCalculator.randomModifiers(random);
            CourierRewardCalculator.Quote quote = CourierRewardCalculator.calculate(
                    marketValue, quantity, distance < 0 ? 800 : distance, modifiers);
            var originPos = dispatcher.blockPosition();
            var destinationPos = destination.blockPosition();
            CourierOrder order = CourierOrder.create(
                    dispatcher.getUUID(), destination.getUUID(),
                    dispatcher.level().dimension().location().toString(),
                    destination.level().dimension().location().toString(),
                    courierNodeName(dispatcher), courierNodeName(destination),
                    itemId.toString(), stack.getHoverName().getString(), quantity, quote.rewardCents(),
                    now, now + COURIER_ORDER_LIFETIME_MILLIS, distance,
                    originPos.getX(), originPos.getY(), originPos.getZ(),
                    destinationPos.getX(), destinationPos.getY(), destinationPos.getZ(),
                    quote.marketValueCents(), modifiers.moneyMultiplierPercent(),
                    modifiers.xpMultiplierPercent(), modifiers.tier());
            record.putCourierOrder(order);
            existing++;
        }
        if (attempts > 1) {
            JobSavedData.get(server).setDirty();
        }
    }

    private static List<JobNpcEntity> courierNodes(MinecraftServer server) {
        if (server == null) {
            return List.of();
        }
        List<JobNpcEntity> nodes = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (var level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof JobNpcEntity node && seen.add(node.getUUID())) {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    private static String courierNodeName(JobNpcEntity node) {
        if (node == null) {
            return "Job NPC";
        }
        if (node.hasCustomName() && node.getCustomName() != null
                && !node.getCustomName().getString().isBlank()) {
            return node.getCustomName().getString();
        }
        JobDefinition definition = JobRegistry.get(node.jobId());
        if (definition != null) {
            return definition.buyerName();
        }
        return node.isCourierDispatcher() ? "Courier Dispatcher" : "Job NPC";
    }

    private static UUID courierDispatchNpcId(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return null;
        }
        Map<UUID, UUID> dispatchers = COURIER_DISPATCH_NPCS.get(server);
        return dispatchers == null ? null : dispatchers.get(playerId);
    }

    private static void clearCourierDispatch(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return;
        }
        Map<UUID, UUID> dispatchers = COURIER_DISPATCH_NPCS.get(server);
        if (dispatchers != null) {
            dispatchers.remove(playerId);
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static boolean expireCourierOrders(ServerPlayer player, JobSavedData.PlayerRecord record) {
        if (record == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (CourierOrder order : record.courierOrders().values()) {
            if (!order.isFinished() && order.isExpired(now)) {
                order.expire();
                if (player != null) {
                    removeCourierParcels(player, order.id());
                }
                changed = true;
            }
        }
        return changed;
    }

    private static int courierHeldSlot(ServerPlayer player, InteractionHand hand) {
        if (player == null) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        if (hand == InteractionHand.MAIN_HAND) {
            return inventory.selected >= 0 && inventory.selected < inventory.items.size()
                    ? inventory.selected : -1;
        }
        return inventory.offhand.isEmpty() ? -1 : inventory.items.size();
    }

    private static ItemStack courierParcelAt(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        return slot < inventory.items.size()
                ? inventory.getItem(slot) : inventory.offhand.get(slot - inventory.items.size());
    }

    private static ItemStack removeCourierParcel(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        ItemStack stack = courierParcelAt(player, slot);
        if (!CourierParcelItem.isParcel(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stack.copy();
        removed.setCount(1);
        stack.shrink(1);
        if (slot < inventory.items.size()) {
            inventory.setItem(slot, stack);
        } else {
            inventory.offhand.set(slot - inventory.items.size(), stack);
        }
        inventory.setChanged();
        return removed;
    }

    private static void removeCourierParcels(ServerPlayer player, UUID orderId) {
        if (player == null || orderId == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (orderId.equals(parseUuid(CourierParcelItem.orderId(stack)))) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (int slot = 0; slot < inventory.offhand.size(); slot++) {
            ItemStack stack = inventory.offhand.get(slot);
            if (orderId.equals(parseUuid(CourierParcelItem.orderId(stack)))) {
                inventory.offhand.set(slot, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
    }

    private static boolean hasCourierParcelSpace(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) {
                return true;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void giveCourierParcelBack(ServerPlayer player, ItemStack parcel) {
        if (player == null || parcel == null || parcel.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(parcel.copy())) {
            player.drop(parcel.copy(), false);
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void sendSnapshot(ServerPlayer player, String mode, String notice,
                                     boolean confirmationRequired, String pendingItemId,
                                     int pendingAmount, long pendingQuoteCents, boolean readOnlyOverride) {
        if (player == null || player.getServer() == null) return;
        MinecraftServer server = player.getServer();
        JobSavedData data = JobSavedData.get(server);
        ensureMarketDay(server, data);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        record.resetForDay(currentDay(server));
        String sessionJob = sessionJobId(server, player.getUUID());
        String selectedJobId = "BUYER".equals(mode) && !sessionJob.isBlank()
                ? sessionJob : activeJobId(server, player.getUUID());
        JobDefinition definition = JobRegistry.get(selectedJobId);
        if (definition == null) {
            definition = JobRegistry.get(LUMBERJACK_ID);
            selectedJobId = definition == null ? LUMBERJACK_ID : definition.id();
        }
        JobSavedData.JobProgress selectedProgress = record.progress(selectedJobId);
        long dailyQuestDay = JobQuestService.currentRotationDay();
        List<JobQuestService.Definition> dailyQuestDefinitions = JobQuestService.forDay(selectedJobId, dailyQuestDay);
        selectedProgress.ensureDailyQuests(dailyQuestDay, dailyQuestDefinitions);
        boolean readOnly = readOnlyOverride || isReadOnly(server, player.getUUID());
        boolean active = selectedJobId.equals(record.activeJob());
        Map<ResourceLocation, Integer> owned = active && "BUYER".equals(mode)
                ? countCarriedItems(player, server, selectedJobId) : Map.of();
        List<JobCatalogEntryPayload> entries = new ArrayList<>();
        if ("BUYER".equals(mode) && (active || readOnly)) {
            for (JobCatalogService.CatalogEntry entry : JobCatalogService.catalog(server, selectedJobId)) {
                long quote = JobCatalogService.liveQuoteCents(
                        server, selectedJobId, entry, data, selectedProgress, currentDay(server));
                entries.add(new JobCatalogEntryPayload(
                        entry.itemId().toString(), entry.displayName(), entry.family(), entry.category(), quote,
                        owned.getOrDefault(entry.itemId(), 0), entry.rawUnitsMilli()));
            }
        }
        List<CourierOrderPayload> courierOrders = COURIER_ID.equals(selectedJobId)
                && "BUYER".equals(mode) && active
                ? courierOrderSnapshots(server, player, record) : List.of();
        String snapshotNotice = notice == null ? "" : notice;
        if (COURIER_ID.equals(selectedJobId) && "BUYER".equals(mode) && active
                && courierOrders.isEmpty() && snapshotNotice.isBlank()) {
            snapshotNotice = "No Courier routes are available. Place a second job NPC to create receiving destinations.";
        }
        PacketDistributor.sendToPlayer(player, new JobOpenPayload(
                mode,
                selectedJobId,
                definition == null ? "Lumberjack" : definition.displayName(),
                definition == null ? "" : definition.description(),
                snapshotNotice,
                selectedProgress.level(),
                selectedProgress.xp(),
                xpUntilNextLevel(selectedProgress.xp(), selectedProgress.level()),
                selectedProgress.dailyMoneyCents(),
                JobProgression.dailyPayoutCapCents(selectedProgress.level()),
                selectedProgress.dailyUnitsMilli(),
                JobProgression.dailyUnitsCapMilli(selectedProgress.level()),
                data.moneySpotlightFamily(),
                data.xpSpotlightFamily(),
                data.moneySpotlightBonusPercent(),
                data.xpSpotlightBonusPercent(),
                active,
                readOnly,
                confirmationRequired,
                pendingItemId,
                Math.max(0, pendingAmount),
                Math.max(0L, pendingQuoteCents),
                entries,
                JobRegistry.all().stream()
                        .sorted(Comparator.comparing(JobDefinition::displayName))
                        .map(job -> {
                            JobSavedData.JobProgress progress = record.progress(job.id());
                            return new JobMarketEntryPayload(
                                    job.id(), job.displayName(), job.description(), job.iconItemId(),
                                    progress.level(), progress.xp(),
                                    xpUntilNextLevel(progress.xp(), progress.level()),
                                    job.id().equals(record.activeJob()));
                        })
                        .toList(),
                levelRoadmap(selectedProgress, definition),
                JobAchievementService.snapshot(server, selectedJobId, selectedProgress),
                dailyQuestDefinitions.stream()
                        .map(quest -> {
                            long progress = selectedProgress.dailyQuestProgress(quest.id());
                            return new JobDailyQuestPayload(quest.id(), quest.title(), quest.description(),
                                    Math.min(progress, quest.target()), quest.target(), progress >= quest.target(),
                                    quest.metric() == JobQuestService.Metric.EARNINGS_CENTS);
                        })
                        .toList(),
                JobProgression.rewardsFor(definition).stream()
                        .map(reward -> new JobRewardPayload(reward.level(), reward.title(), reward.description(),
                                reward.effect(), selectedProgress.level() >= reward.level()))
                        .toList(),
                JobQuestService.resetEpochMillis(dailyQuestDay) / 1000L,
                courierOrders
        ));
    }

    private static Map<ResourceLocation, Integer> countCarriedItems(ServerPlayer player, MinecraftServer server,
                                                                    String jobId) {
        Map<ResourceLocation, JobCatalogService.CatalogEntry> byId =
                catalogById(server, jobId);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        Set<IItemHandler> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            countStack(inventory.getItem(slot), 0, byId, counts, visited);
        }
        for (ItemStack stack : inventory.offhand) {
            countStack(stack, 0, byId, counts, visited);
        }
        return Map.copyOf(counts);
    }

    private static void countStack(ItemStack stack, int depth,
                                   Map<ResourceLocation, JobCatalogService.CatalogEntry> byId,
                                   Map<ResourceLocation, Integer> counts,
                                   Set<IItemHandler> visited) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        JobCatalogService.CatalogEntry entry = byId.get(id);
        if (entry != null && isPlain(stack)) {
            counts.merge(id, stack.getCount(), JobService::saturatingIntAdd);
        }
        if (depth >= MAX_NESTED_INVENTORY_DEPTH) return;
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null || !visited.add(handler)) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            countStack(handler.getStackInSlot(slot), depth + 1, byId, counts, visited);
        }
    }

    private static List<InventorySource> collectSources(ServerPlayer player, ResourceLocation itemId) {
        List<InventorySource> sources = new ArrayList<>();
        Set<IItemHandler> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            collectStack(inventory.getItem(slot), itemId, inventory, slot, 0, sources, visited);
        }
        for (int index = 0; index < inventory.offhand.size(); index++) {
            collectStack(inventory.offhand.get(index), itemId, inventory, inventory.items.size() + index,
                    0, sources, visited);
        }
        return sources;
    }

    private static void collectStack(ItemStack stack, ResourceLocation itemId, Inventory inventory, int slot,
                                      int depth, List<InventorySource> sources, Set<IItemHandler> visited) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation currentId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId.equals(currentId) && isPlain(stack)) {
            if (slot < inventory.items.size()) {
                sources.add(InventorySource.forPlayerInventory(inventory, slot, stack));
            } else {
                sources.add(InventorySource.forPlayerOffhand(inventory, slot - inventory.items.size(), stack));
            }
        }
        if (depth >= MAX_NESTED_INVENTORY_DEPTH) return;
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null || !visited.add(handler)) return;
        for (int child = 0; child < handler.getSlots(); child++) {
            ItemStack childStack = handler.getStackInSlot(child);
            if (childStack != null && !childStack.isEmpty()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(childStack.getItem()))
                    && isPlain(childStack)) {
                sources.add(InventorySource.forHandler(handler, child, childStack));
            }
            collectNestedChildren(childStack, itemId, depth + 1, sources, visited);
        }
    }

    private static void collectNestedChildren(ItemStack stack, ResourceLocation itemId, int depth,
                                              List<InventorySource> sources, Set<IItemHandler> visited) {
        if (stack == null || stack.isEmpty() || depth >= MAX_NESTED_INVENTORY_DEPTH) return;
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null || !visited.add(handler)) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack child = handler.getStackInSlot(slot);
            if (child != null && !child.isEmpty()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(child.getItem())) && isPlain(child)) {
                sources.add(InventorySource.forHandler(handler, slot, child));
            }
            collectNestedChildren(child, itemId, depth + 1, sources, visited);
        }
    }

    private static boolean isPlain(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && ItemStackDataCompat.sameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }

    private static Map<ResourceLocation, JobCatalogService.CatalogEntry> catalogById(
            MinecraftServer server, String jobId) {
        Map<ResourceLocation, JobCatalogService.CatalogEntry> map = new HashMap<>();
        for (JobCatalogService.CatalogEntry entry : JobCatalogService.catalog(server, jobId)) {
            map.put(entry.itemId(), entry);
        }
        return map;
    }

    private static void ensureMarketDay(MinecraftServer server, JobSavedData data) {
        long day = currentDay(server);
        boolean dayChanged = data.marketDay() != day;
        List<String> families = JobRegistry.all().stream()
                .flatMap(definition -> JobCatalogService.catalog(server, definition.id()).stream())
                .map(JobCatalogService.CatalogEntry::family)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        boolean currentRotationIsUsable = data.marketDay() == day && (families.isEmpty()
                || (families.contains(data.moneySpotlightFamily())
                && families.contains(data.xpSpotlightFamily())
                && data.moneySpotlightBonusPercent() > 0
                && data.xpSpotlightBonusPercent() > 0));
        if (currentRotationIsUsable) return;
        if (families.isEmpty()) {
            data.setMarketRotation(day, "", "", 0, 0);
        } else {
            Random random = new Random(day * 31_415_927L + 17L);
            int moneyIndex = random.nextInt(families.size());
            String money = families.get(moneyIndex);
            String xp = families.size() == 1 ? money
                    : families.get((moneyIndex + 1 + random.nextInt(families.size() - 1)) % families.size());
            data.setMarketRotation(day, money, xp, 10 + random.nextInt(26), 10 + random.nextInt(26));
        }
        if (dayChanged) {
            data.resetDailyTotals(day);
        }
        data.pruneSupply(day);
        data.setDirty();
    }

    private static long currentDay(MinecraftServer server) {
        return server == null || server.overworld() == null ? 0L
                : JobMarketClock.marketDayFromDayTime(server.overworld().getDayTime());
    }

    private static long applyXpSpotlight(long baseXp, JobCatalogService.CatalogEntry entry,
                                         JobSavedData data) {
        if (baseXp <= 0L || entry == null || data == null
                || !entry.family().equals(data.xpSpotlightFamily())
                || data.xpSpotlightBonusPercent() <= 0) {
            return Math.max(1L, baseXp);
        }
        long bonus = Math.max(0L, baseXp * data.xpSpotlightBonusPercent() / 100L);
        return saturatingAdd(baseXp, bonus);
    }

    private static long applyJobXpBonus(long baseXp, int level) {
        if (baseXp <= 0L) {
            return 1L;
        }
        int bonusPercent = JobProgression.xpBonusPercent(level);
        long bonus = Math.max(0L, baseXp * bonusPercent / 100L);
        return saturatingAdd(baseXp, bonus);
    }

    private static String sessionMode(MinecraftServer server, UUID playerId) {
        synchronized (SESSION_MODES) {
            return SESSION_MODES.getOrDefault(server, Map.of()).getOrDefault(playerId, "");
        }
    }

    private static void setSessionMode(MinecraftServer server, UUID playerId, String mode) {
        if (server == null || playerId == null) return;
        synchronized (SESSION_MODES) {
            SESSION_MODES.computeIfAbsent(server, ignored -> new HashMap<>()).put(playerId, mode);
        }
    }

    private static String sessionJobId(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return "";
        synchronized (SESSION_JOB_IDS) {
            return SESSION_JOB_IDS.getOrDefault(server, Map.of()).getOrDefault(playerId, "");
        }
    }

    private static void setSessionJobId(MinecraftServer server, UUID playerId, String jobId) {
        if (server == null || playerId == null) return;
        String normalized = jobId == null ? "" : jobId.trim().toLowerCase(Locale.ROOT);
        synchronized (SESSION_JOB_IDS) {
            SESSION_JOB_IDS.computeIfAbsent(server, ignored -> new HashMap<>()).put(playerId, normalized);
        }
    }

    private static String activeJobId(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return LUMBERJACK_ID;
        String active = JobSavedData.get(server).player(playerId).activeJob();
        return JobRegistry.get(active) == null ? LUMBERJACK_ID : active;
    }

    private static String supplyKey(String jobId, String family) {
        String normalizedJob = jobId == null ? "" : jobId.trim().toLowerCase(Locale.ROOT);
        String normalizedFamily = family == null ? "" : family.trim().toLowerCase(Locale.ROOT);
        return LUMBERJACK_ID.equals(normalizedJob) ? normalizedFamily : normalizedJob + ":" + normalizedFamily;
    }

    private static boolean isReadOnly(MinecraftServer server, UUID playerId) {
        synchronized (READ_ONLY_SESSIONS) {
            return READ_ONLY_SESSIONS.getOrDefault(server, Set.of()).contains(playerId);
        }
    }

    static boolean isReadOnlySession(MinecraftServer server, UUID playerId) {
        return isReadOnly(server, playerId);
    }

    private static void clearReadOnly(MinecraftServer server, UUID playerId) {
        synchronized (READ_ONLY_SESSIONS) {
            Set<UUID> ids = READ_ONLY_SESSIONS.get(server);
            if (ids != null) ids.remove(playerId);
        }
    }

    public static int levelForXp(long xp) {
        long remaining = Math.max(0L, xp);
        int level = 1;
        for (int next = 1; next < 100; next++) {
            long needed = (long) Config.LUMBERJACK_LEVEL_XP_BASE.get()
                    + (long) (next - 1) * Config.LUMBERJACK_LEVEL_XP_STEP.get();
            if (remaining < needed) break;
            remaining -= needed;
            level = next + 1;
        }
        return level;
    }

    public static long xpUntilNextLevelForApi(long xp, int currentLevel) {
        return xpUntilNextLevel(xp, currentLevel);
    }

    /** Returns the cumulative XP floor for a job level, matching the shop roadmap's node model. */
    public static long requiredXpForLevel(int level) {
        return totalXpForLevel(level);
    }

    private static List<JobLevelRoadmapNodePayload> levelRoadmap(JobSavedData.JobProgress progress,
                                                                  JobDefinition definition) {
        int currentLevel = Math.max(1, Math.min(100, progress == null ? 1 : progress.level()));
        List<JobLevelRoadmapNodePayload> nodes = new ArrayList<>(100);
        for (int level = 1; level <= 100; level++) {
            JobReward reward = level % 5 == 0 ? JobProgression.rewardFor(definition, level) : null;
            List<String> unlocks = reward == null
                    ? List.of()
                    : List.of(reward.title() + ": " + reward.effect());
            String state = level < currentLevel
                    ? "COMPLETED"
                    : level == currentLevel ? "CURRENT" : "LOCKED";
            nodes.add(new JobLevelRoadmapNodePayload(
                    level,
                    requiredXpForLevel(level),
                    JobProgression.payoutBonusPercent(level),
                    JobProgression.xpBonusPercent(level),
                    JobProgression.dailyPayoutCapCents(level),
                    JobProgression.dailyUnitsCapMilli(level),
                    state,
                    unlocks
            ));
        }
        return List.copyOf(nodes);
    }

    private static long xpUntilNextLevel(long xp, int currentLevel) {
        if (currentLevel >= 100) return 0L;
        long total = Math.max(0L, xp);
        long threshold = totalXpForLevel(currentLevel + 1);
        return Math.max(0L, threshold - total);
    }

    private static long totalXpForLevel(int level) {
        long total = 0L;
        int capped = Math.max(1, Math.min(100, level));
        for (int next = 1; next < capped; next++) {
            long needed = (long) Config.LUMBERJACK_LEVEL_XP_BASE.get()
                    + (long) (next - 1) * Config.LUMBERJACK_LEVEL_XP_STEP.get();
            total = saturatingAdd(total, needed);
        }
        return total;
    }

    private static long multiplyCapped(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return Math.max(0L, left);
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static int saturatingIntAdd(int left, int right) {
        return right > Integer.MAX_VALUE - left ? Integer.MAX_VALUE : left + right;
    }

    private static String formatCents(long cents) {
        long safe = Math.max(0L, cents);
        return "$" + (safe / 100L) + "." + String.format(Locale.ROOT, "%02d", safe % 100L);
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "The job request failed." : message;
    }

    private record SaleContext(boolean valid, String message, String capacityMessage,
                               String jobId, JobCatalogService.CatalogEntry entry, int requestedAmount,
                               int maximumSellable, long quoteCents) {
        static SaleContext invalid(String message) {
            return new SaleContext(false, message, message, "", null, 0, 0, 0L);
        }
    }

    private static final class InventorySource {
        private final Inventory inventory;
        private final int inventorySlot;
        private final IItemHandler handler;
        private final int handlerSlot;
        private final ItemStack before;

        private InventorySource(Inventory inventory, int inventorySlot, IItemHandler handler,
                                int handlerSlot, ItemStack before) {
            this.inventory = inventory;
            this.inventorySlot = inventorySlot;
            this.handler = handler;
            this.handlerSlot = handlerSlot;
            this.before = before.copy();
        }

        static InventorySource forPlayerInventory(Inventory inventory, int slot, ItemStack before) {
            return new InventorySource(inventory, slot, null, -1, before);
        }

        static InventorySource forPlayerOffhand(Inventory inventory, int slot, ItemStack before) {
            return new InventorySource(inventory, inventory.items.size() + slot, null, -1, before);
        }

        static InventorySource forHandler(IItemHandler handler, int slot, ItemStack before) {
            return new InventorySource(null, -1, handler, slot, before);
        }

        int available() {
            return inventory != null
                    ? stackAtInventory().getCount()
                    : handler == null || handlerSlot < 0 ? 0 : handler.getStackInSlot(handlerSlot).getCount();
        }

        boolean remove(int amount) {
            int wanted = Math.max(0, amount);
            if (wanted == 0 || available() < wanted) return false;
            if (inventory != null) {
                ItemStack current = stackAtInventory();
                if (!ItemStackDataCompat.sameItemSameComponents(current, before)) return false;
                current.shrink(wanted);
                setInventoryStack(current);
                return true;
            }
            if (handler == null) return false;
            ItemStack removed = handler.extractItem(handlerSlot, wanted, false);
            if (removed.getCount() != wanted) {
                if (handler instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(handlerSlot, before.copy());
                }
                return false;
            }
            return true;
        }

        void restore() {
            if (inventory != null) {
                setInventoryStack(before.copy());
                return;
            }
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(handlerSlot, before.copy());
                return;
            }
            if (handler != null) {
                handler.insertItem(handlerSlot, before.copy(), false);
            }
        }

        private ItemStack stackAtInventory() {
            return inventorySlot < inventory.items.size()
                    ? inventory.getItem(inventorySlot)
                    : inventory.offhand.get(inventorySlot - inventory.items.size());
        }

        private void setInventoryStack(ItemStack stack) {
            if (inventorySlot < inventory.items.size()) {
                inventory.setItem(inventorySlot, stack);
            } else {
                inventory.offhand.set(inventorySlot - inventory.items.size(), stack);
            }
            inventory.setChanged();
        }
    }
}
