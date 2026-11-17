package net.austizz.ultimatebankingsystem.shelf;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload;
import net.austizz.ultimatebankingsystem.network.ShelfFillOpenPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived, operator-only shelf batch setup. No selected item or position is trusted from the client. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class ShelfFillService {
    private static final int MAX_SHELVES = 512;
    private static final int MAX_ITEMS = 2048;
    private static final long SESSION_MS = 10 * 60 * 1000L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ShelfFillService() {}

    public static void open(ServerPlayer player) {
        if (!player.hasPermissions(3)) return;
        Session session = new Session(player.level().dimension());
        SESSIONS.put(player.getUUID(), session);
        send(player, session, true, false, "Choose how to build your shelf catalog.");
    }

    public static void handle(ServerPlayer player, ShelfFillActionPayload payload) {
        if (!player.hasPermissions(3)) return;
        if ("CANCEL".equals(payload.action())) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.expiresAt < System.currentTimeMillis()
                || !session.dimension.equals(player.level().dimension())) {
            Session fresh = new Session(player.level().dimension());
            SESSIONS.put(player.getUUID(), fresh);
            send(player, fresh, true, false,
                    "Shelf setup expired or the dimension changed. Start again in this world.");
            return;
        }
        session.expiresAt = System.currentTimeMillis() + SESSION_MS;
        switch (payload.action()) {
            case "BEGIN" -> {
                session.priorShelves.clear();
                session.priorShelves.addAll(session.shelves);
                session.selecting = true;
                send(player, session, false, false, "Right-click shelves to toggle them. Sneak + right-click to finish.");
                player.displayClientMessage(Component.literal("Right-click shelves to select. Sneak + right-click to review."), true);
            }
            case "TOGGLE" -> toggle(player, session, new BlockPos(payload.x(), payload.y(), payload.z()));
            case "DONE" -> finish(player, session);
            case "BACK" -> {
                session.selecting = false;
                session.shelves.clear();
                session.shelves.addAll(session.priorShelves);
                send(player, session, false, false, "Shelf selection canceled. Choose shelves again when ready.");
            }
            case "APPLY" -> apply(player, session, payload);
            default -> send(player, session, false, false, "Unknown shelf setup action.");
        }
    }

    public static boolean isSelecting(UUID playerId) {
        Session session = SESSIONS.get(playerId);
        return session != null && session.selecting && session.expiresAt >= System.currentTimeMillis();
    }

    public static void finishSelection(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.selecting) finish(player, session);
    }

    private static void toggle(ServerPlayer player, Session session, BlockPos clicked) {
        if (!session.selecting || player.distanceToSqr(clicked.getX() + 0.5,
                clicked.getY() + 0.5, clicked.getZ() + 0.5) > 64.0) return;
        Level level = player.level();
        if (!level.hasChunkAt(clicked) || !ShelfService.isShelf(level.getBlockState(clicked))) return;
        BlockPos pos = ShelfService.toLowerShelfPos(level, clicked);
        ShelfDisplayBlockEntity display = ShelfService.getDisplayEntity(level, pos);
        if (display == null || !display.isShopMode()) {
            player.displayClientMessage(Component.literal("Select shelves that are in shop mode."), true);
            return;
        }
        if (!session.shelves.remove(pos)) {
            if (session.shelves.size() >= MAX_SHELVES) {
                player.displayClientMessage(Component.literal("Shelf limit reached (512)."), true);
                return;
            }
            session.shelves.add(pos.immutable());
        }
        player.displayClientMessage(Component.literal(session.shelves.size()
                + " shelves selected | Right-click to toggle | Sneak + right-click to review"), true);
    }

    private static void finish(ServerPlayer player, Session session) {
        if (session.shelves.isEmpty()) {
            player.displayClientMessage(Component.literal("Select at least one shelf first."), true);
            return;
        }
        session.selecting = false;
        send(player, session, false, false, "Review items and price before filling empty slots.");
    }

    private static void apply(ServerPlayer player, Session session, ShelfFillActionPayload payload) {
        if (session.selecting || session.shelves.isEmpty()) {
            send(player, session, false, false, "Select shelves before applying.");
            return;
        }
        for (BlockPos pos : session.shelves) {
            if (!player.level().hasChunkAt(pos) || !ShelfService.isShelf(player.level().getBlockState(pos))
                    || ShelfService.getDisplayEntity(player.level(), pos) == null
                    || !ShelfService.getDisplayEntity(player.level(), pos).isShopMode()) {
                send(player, session, false, false,
                        "A selected shelf is missing, unloaded, or no longer in shop mode.");
                return;
            }
        }
        long defaultPrice = payload.defaultPriceCents();
        if (defaultPrice < 0 || defaultPrice > 1_000_000_000_00L) {
            send(player, session, false, false, "Default price must be at most $1,000,000,000.");
            return;
        }
        List<String> ids = new ArrayList<>();
        Set<String> inputIds = new LinkedHashSet<>(payload.itemIds());
        if (inputIds.size() != payload.itemIds().size()) {
            send(player, session, false, false, "The item list contains duplicates.");
            return;
        }
        if (payload.bulk()) {
            String modId = payload.modId();
            if (modId.isBlank() || ResourceLocation.tryParse(modId + ":probe") == null) {
                send(player, session, false, false, "Select a valid mod first.");
                return;
            }
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals(modId)
                        && ShelfDisplayRules.isAllowedDisplayItem(new ItemStack(item))
                        && !inputIds.contains(id.toString())) ids.add(id.toString());
            }
            ids.sort(Comparator.naturalOrder());
        } else {
            if (inputIds.size() > MAX_ITEMS) {
                send(player, session, false, false, "Choose at most 2,048 items in manual mode.");
                return;
            }
            ids.addAll(inputIds);
        }
        if (ids.isEmpty()) {
            send(player, session, false, false, "No eligible items remain in this selection.");
            return;
        }
        Set<String> selectedIds = new LinkedHashSet<>(ids);
        Map<String, Long> prices = new HashMap<>();
        for (ShelfFillActionPayload.PriceOverride override : payload.prices()) {
            if (!selectedIds.contains(override.itemId()) || prices.putIfAbsent(override.itemId(), override.cents()) != null
                    || override.cents() < 1 || override.cents() > 1_000_000_000_00L) {
                send(player, session, false, false, "An individual item price is invalid.");
                return;
            }
        }
        Map<String, ItemStack> inventoryItems = new HashMap<>();
        for (ShelfFillActionPayload.InventoryChoice choice : payload.inventoryChoices()) {
            int slot = choice.slot();
            if (payload.bulk() || slot < 0 || slot >= player.getInventory().getContainerSize()
                    || choice.stack().isEmpty()) {
                send(player, session, false, false, "An inventory selection is invalid.");
                return;
            }
            ItemStack current = player.getInventory().getItem(slot);
            if (!ItemStackDataCompat.sameItemSameComponents(current, choice.stack())) {
                send(player, session, false, false,
                        "An inventory item changed. Select it again before filling shelves.");
                return;
            }
            if (inventoryItems.putIfAbsent(ShelfFillActionPayload.inventoryKey(slot), current.copyWithCount(1)) != null) {
                send(player, session, false, false, "An inventory slot was selected more than once.");
                return;
            }
        }
        List<Slot> empty = collectEmpty(player, session);
        if (empty.isEmpty()) {
            send(player, session, false, false, "The selected shelves have no empty display slots.");
            return;
        }
        if (!payload.force() && ids.size() > empty.size()) {
            send(player, session, false, false, "There are " + ids.size() + " items for " + empty.size()
                    + " slots. Confirm a partial fill to leave the rest unplaced.");
            return;
        }
        int fillCount = Math.min(ids.size(), empty.size());
        List<ItemStack> items = new ArrayList<>(fillCount);
        for (int i = 0; i < fillCount; i++) {
            String raw = ids.get(i);
            ItemStack stack = inventoryItems.get(raw);
            if (stack == null) {
                if (raw.startsWith("@inventory:")) {
                    send(player, session, false, false, "An inventory item must be selected again.");
                    return;
                }
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    send(player, session, false, false, "An item is no longer registered: " + raw);
                    return;
                }
                stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
            if (!ShelfDisplayRules.isAllowedDisplayItem(stack)) {
                send(player, session, false, false, "This item cannot be displayed: " + raw);
                return;
            }
            items.add(stack);
        }
        for (int i = 0; i < fillCount; i++) {
            long price = prices.getOrDefault(ids.get(i), defaultPrice);
            if (price < 1) {
                send(player, session, false, false, "Set a default or individual price for " + ids.get(i) + ".");
                return;
            }
        }
        for (int i = 0; i < fillCount; i++) {
            Slot slot = empty.get(i);
            slot.shelf.setSlot(slot.index, items.get(i), prices.getOrDefault(ids.get(i), defaultPrice), 0);
        }
        int shelfCount = session.shelves.size();
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new ShelfFillOpenPayload(false, false, true,
                shelfCount, empty.size() - fillCount, "Filled " + fillCount + " display slots across "
                + shelfCount + " shelves. " + (ids.size() - fillCount) + " items were left unplaced."
                + " Stock-backed displays still need inventory stock."));
    }

    private static List<Slot> collectEmpty(ServerPlayer player, Session session) {
        List<Slot> empty = new ArrayList<>();
        Level level = player.level();
        for (BlockPos pos : session.shelves) {
            if (!level.hasChunkAt(pos) || !ShelfService.isShelf(level.getBlockState(pos))) continue;
            ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(level, pos);
            if (shelf == null) continue;
            for (int slot = 0; slot < shelf.getSlotCount(); slot++) {
                if (shelf.getDisplayItem(slot).isEmpty()
                        && (shelf.isCreativeShelf() || shelf.getSlotStock(slot) == 0)) {
                    empty.add(new Slot(shelf, slot));
                }
            }
        }
        return empty;
    }

    private static void send(ServerPlayer player, Session session, boolean fresh,
                             boolean complete, String notice) {
        PacketDistributor.sendToPlayer(player, new ShelfFillOpenPayload(fresh, session.selecting,
                complete, session.shelves.size(), collectEmpty(player, session).size(), notice));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    private record Slot(ShelfDisplayBlockEntity shelf, int index) {}

    private static final class Session {
        final ResourceKey<Level> dimension;
        final Set<BlockPos> shelves = new LinkedHashSet<>();
        final Set<BlockPos> priorShelves = new LinkedHashSet<>();
        boolean selecting;
        long expiresAt = System.currentTimeMillis() + SESSION_MS;

        Session(ResourceKey<Level> dimension) { this.dimension = dimension; }
    }
}
