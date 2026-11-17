package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Persistent state for an independent, eight-product vending machine. */
public final class VendingMachineBlockEntity extends BlockEntity implements ShelfDisplayBlockEntity {
    public static final int DISPLAY_SLOT_COUNT = 8;
    public static final int DISPLAY_SLOTS_PER_SHELF = 2;
    public static final int SHELF_COUNT = 4;
    public static final int STORAGE_SLOTS_PER_DISPLAY = 27;
    public static final int CASH_SLOT_COUNT = 27;
    public static final long DEFAULT_PRICE_DOLLARS = 1L;
    public static final long MAX_PRICE_DOLLARS = 1_000_000L;

    private static final String DISPLAY_PREFIX = "display_";
    private static final String PRICE_PREFIX = "price_";
    private static final String STOCK_PREFIX = "stock_";
    private static final String VISIBLE_STOCK_PREFIX = "visible_stock_";
    private static final String CASH_STORAGE_KEY = "cash_storage";

    private final ItemStack[] displayItems = new ItemStack[DISPLAY_SLOT_COUNT];
    private final long[] pricesDollars = new long[DISPLAY_SLOT_COUNT];
    private final int[] syncedStockCounts = new int[DISPLAY_SLOT_COUNT];
    private final ItemStackHandler[] stockHandlers = new ItemStackHandler[DISPLAY_SLOT_COUNT];
    private final ItemStackHandler cashHandler;

    private UUID ownerUuid;
    private String ownerName = "";
    private UUID settlementAccountId;
    private long totalSalesDollars;

    private UUID shopId;

    public VendingMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.VENDING_MACHINE.get(), pos, blockState);
        Arrays.fill(displayItems, ItemStack.EMPTY);
        Arrays.fill(pricesDollars, DEFAULT_PRICE_DOLLARS);
        for (int slot = 0; slot < DISPLAY_SLOT_COUNT; slot++) {
            final int displaySlot = slot;
            stockHandlers[slot] = new ItemStackHandler(STORAGE_SLOTS_PER_DISPLAY) {
                @Override
                public boolean isItemValid(int storageSlot, ItemStack stack) {
                    ItemStack display = displayItems[displaySlot];
                    return !display.isEmpty() && !stack.isEmpty()
                            && ItemStack.isSameItemSameComponents(display, stack);
                }

                @Override
                protected void onContentsChanged(int storageSlot) {
                    markUpdated();
                }
            };
        }
        cashHandler = new ItemStackHandler(CASH_SLOT_COUNT) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !stack.isEmpty() && DollarBills.isCashTenderItem(stack.getItem());
            }

            @Override
            protected void onContentsChanged(int slot) {
                markUpdated();
            }
        };
    }

    @Override
    public int getSlotCount() {
        return DISPLAY_SLOT_COUNT;
    }

    @Override
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public UUID getSettlementAccountId() {
        return settlementAccountId;
    }

    public long getTotalSalesDollars() {
        return totalSalesDollars;
    }

    public boolean isCreativeMachine() {
        return getBlockState().is(ModBlocks.CREATIVE_VENDING_MACHINE.get());
    }

    @Override
    public long getSlotPrice(int slot) {
        return getPriceDollars(slot) * 100L;
    }

    @Override
    public int getSlotStock(int slot) {
        return getStockCount(slot);
    }

    @Override
    public void setSlot(int slot, ItemStack item, long priceCents, int stockCount) {
        if (!validSlot(slot)) return;
        setDisplayItem(slot, item);
        setPriceDollars(slot, priceCents / 100L);
        // Stock handling is complex, but for this interface we'll try to sync if possible.
        // Usually, the shop setup doesn't use setSlot for complex machines.
    }

    @Override
    public void setPriceOnly(int slot, long priceCents) {
        setPriceDollars(slot, priceCents / 100L);
    }

    @Override
    public void clearSlot(int slot) {
        clearDisplayItem(slot);
    }

    @Override
    public void setStockOnly(int slot, int stockCount) {
        // Not easily supported via this interface for vending machines
    }

    @Override
    public boolean consumeOneStock(int slot) {
        ItemStack stack = takeOneForSale(slot);
        return stack != null && !stack.isEmpty();
    }

    @Override
    public void addStock(int slot, int amount) {
        // Not easily supported via this interface for vending machines
    }

    @Override
    public List<ItemStack> extractDisplayItemsForDrop() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < DISPLAY_SLOT_COUNT; i++) {
            ItemStack s = getDisplayItem(i);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }

    @Override
    public ItemDisplayTransform getSlotTransform(int slot) {
        return null;
    }

    @Override
    public void setSlotTransform(int slot, float offsetX, float offsetY, float offsetZ, float rotationX, float rotationY, float rotationZ, float scaleX, float scaleY, float scaleZ) {
    }

    public boolean canConfigure(Player player) {
        if (player == null) {
            return false;
        }
        return player.hasPermissions(3) || ownerUuid == null || ownerUuid.equals(player.getUUID());
    }

    @Override
    public UUID getShopId() {
        return shopId;
    }

    @Override
    public void setShopId(UUID shopId) {
        this.shopId = shopId;
        markUpdated();
    }

    @Override
    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "" : ownerName.trim();
        markUpdated();
    }

    @Override
    public boolean isCreativeShelf() {
        return isCreativeMachine();
    }

    @Override
    public boolean isShopMode() {
        return true;
    }

    @Override
    public void setShopMode(boolean shopMode) {
        // Vending machine is always in shop mode
    }

    @Override
    public ItemStack getDisplayItem(int slot) {
        return validSlot(slot) ? displayItems[slot].copy() : ItemStack.EMPTY;
    }

    public long getPriceDollars(int slot) {
        return validSlot(slot) ? pricesDollars[slot] : DEFAULT_PRICE_DOLLARS;
    }

    public int getStockCount(int slot) {
        if (!validSlot(slot) || displayItems[slot].isEmpty()) {
            return 0;
        }
        if (isCreativeMachine()) {
            return Integer.MAX_VALUE;
        }
        int total = 0;
        for (int storageSlot = 0; storageSlot < STORAGE_SLOTS_PER_DISPLAY; storageSlot++) {
            int count = stockHandlers[slot].getStackInSlot(storageSlot).getCount();
            if (Integer.MAX_VALUE - total < count) {
                total = Integer.MAX_VALUE;
                break;
            }
            total += count;
        }
        if (level != null && level.isClientSide() && syncedStockCounts[slot] >= 0) {
            return syncedStockCounts[slot];
        }
        return total;
    }

    public int getCashOccupiedSlots() {
        int occupied = 0;
        for (int slot = 0; slot < CASH_SLOT_COUNT; slot++) {
            if (!cashHandler.getStackInSlot(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    public int getCashValueCents() {
        long total = 0L;
        for (int slot = 0; slot < CASH_SLOT_COUNT; slot++) {
            ItemStack stack = cashHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                total += (long) DollarBills.cashCentsForItem(stack.getItem()) * stack.getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public IItemHandler getCashHandler() {
        return cashHandler;
    }

    public boolean setDisplayItem(int slot, ItemStack stack) {
        if (!validSlot(slot) || stack == null || stack.isEmpty()) {
            return false;
        }
        ItemStack old = displayItems[slot];
        if (!old.isEmpty() && !ItemStack.isSameItemSameComponents(old, stack) && getStoredStockCount(slot) > 0) {
            return false;
        }
        displayItems[slot] = stack.copyWithCount(1);
        if (pricesDollars[slot] <= 0L) {
            pricesDollars[slot] = DEFAULT_PRICE_DOLLARS;
        }
        markUpdated();
        return true;
    }

    public boolean clearDisplayItem(int slot) {
        if (!validSlot(slot) || getStoredStockCount(slot) > 0) {
            return false;
        }
        displayItems[slot] = ItemStack.EMPTY;
        pricesDollars[slot] = DEFAULT_PRICE_DOLLARS;
        markUpdated();
        return true;
    }

    public boolean setPriceDollars(int slot, long priceDollars) {
        if (!validSlot(slot) || priceDollars <= 0L || priceDollars > MAX_PRICE_DOLLARS) {
            return false;
        }
        pricesDollars[slot] = priceDollars;
        markUpdated();
        return true;
    }

    public boolean setSettlementAccountId(@Nullable UUID accountId) {
        settlementAccountId = accountId;
        markUpdated();
        return true;
    }

    public int restockFromHeldItem(Player player, int slot) {
        if (player == null || !validSlot(slot) || isCreativeMachine()) {
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || displayItems[slot].isEmpty()
                || !ItemStack.isSameItemSameComponents(displayItems[slot], held)) {
            return 0;
        }
        ItemStack requested = held.copy();
        ItemStack remainder = insertIntoStorage(slot, requested);
        int inserted = requested.getCount() - remainder.getCount();
        if (inserted > 0) {
            held.shrink(inserted);
            player.getInventory().setChanged();
            markUpdated();
        }
        return inserted;
    }

    /**
     * Configures a display slot from a visible player-inventory slot and moves every matching
     * stack from the player's main inventory into the machine's stock storage.
     *
     * @return the number of items moved, or {@code -1} when the request is invalid or blocked
     */
    public int setDisplayItemFromInventory(Player player, int slot, int inventorySlot) {
        if (player == null || !validSlot(slot) || isCreativeMachine()
                || inventorySlot < 0 || inventorySlot >= player.getInventory().items.size()) {
            return -1;
        }

        ItemStack source = player.getInventory().getItem(inventorySlot);
        if (source.isEmpty()) {
            return 0;
        }

        ItemStack previous = displayItems[slot].copy();
        if (!setDisplayItem(slot, source)) {
            return -1;
        }

        int moved = 0;
        for (int playerSlot = 0; playerSlot < player.getInventory().items.size(); playerSlot++) {
            ItemStack inventoryStack = player.getInventory().getItem(playerSlot);
            if (inventoryStack.isEmpty() || !ItemStack.isSameItemSameComponents(source, inventoryStack)) {
                continue;
            }

            int requested = inventoryStack.getCount();
            ItemStack remainder = insertIntoStorage(slot, inventoryStack.copy());
            int inserted = requested - remainder.getCount();
            if (inserted > 0) {
                inventoryStack.shrink(inserted);
                moved += inserted;
            }
        }

        if (moved > 0) {
            player.getInventory().setChanged();
            markUpdated();
        } else {
            // Do not change the configured display when the stock storage cannot accept the inventory.
            if (previous.isEmpty()) {
                clearDisplayItem(slot);
            } else {
                displayItems[slot] = previous;
                markUpdated();
            }
        }
        return moved;
    }

    public int getStoredStockCount(int slot) {
        if (!validSlot(slot)) {
            return 0;
        }
        int total = 0;
        for (int storageSlot = 0; storageSlot < STORAGE_SLOTS_PER_DISPLAY; storageSlot++) {
            int count = stockHandlers[slot].getStackInSlot(storageSlot).getCount();
            if (Integer.MAX_VALUE - total < count) {
                return Integer.MAX_VALUE;
            }
            total += count;
        }
        return total;
    }

    public List<ItemStack> getStockSnapshot(int slot) {
        List<ItemStack> snapshot = new ArrayList<>(STORAGE_SLOTS_PER_DISPLAY);
        if (!validSlot(slot)) {
            return snapshot;
        }
        for (int storageSlot = 0; storageSlot < STORAGE_SLOTS_PER_DISPLAY; storageSlot++) {
            snapshot.add(stockHandlers[slot].getStackInSlot(storageSlot).copy());
        }
        return snapshot;
    }

    /**
     * Returns the live storage handler for a display compartment. Container menus use this
     * handler so vanilla click, drag, split-stack, and shift-click operations stay server
     * authoritative while preserving every component on the moved stacks.
     */
    @Nullable
    public IItemHandler getStockHandler(int slot) {
        return validSlot(slot) ? stockHandlers[slot] : null;
    }

    public ItemStack takeStockStack(int displaySlot, int storageSlot) {
        if (!validSlot(displaySlot) || storageSlot < 0 || storageSlot >= STORAGE_SLOTS_PER_DISPLAY
                || isCreativeMachine()) {
            return ItemStack.EMPTY;
        }
        ItemStack stored = stockHandlers[displaySlot].getStackInSlot(storageSlot);
        if (stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = stockHandlers[displaySlot].extractItem(storageSlot, stored.getCount(), false);
        if (!extracted.isEmpty()) {
            markUpdated();
        }
        return extracted;
    }

    public int insertStock(int displaySlot, ItemStack stack) {
        if (!validSlot(displaySlot) || stack == null || stack.isEmpty() || isCreativeMachine()) {
            return 0;
        }
        int requested = stack.getCount();
        ItemStack remainder = insertIntoStorage(displaySlot, stack.copy());
        int inserted = requested - remainder.getCount();
        if (inserted > 0) {
            markUpdated();
        }
        return inserted;
    }

    public ItemStack takeOneForSale(int slot) {
        if (!validSlot(slot) || isCreativeMachine()) {
            return getDisplayItem(slot).copyWithCount(1);
        }
        for (int storageSlot = 0; storageSlot < STORAGE_SLOTS_PER_DISPLAY; storageSlot++) {
            ItemStack extracted = stockHandlers[slot].extractItem(storageSlot, 1, false);
            if (!extracted.isEmpty()) {
                markUpdated();
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public void returnStock(int slot, ItemStack stack) {
        if (!validSlot(slot) || stack == null || stack.isEmpty() || isCreativeMachine()) {
            return;
        }
        ItemStack remainder = insertIntoStorage(slot, stack.copy());
        if (!remainder.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.7D,
                    worldPosition.getZ() + 0.5D, remainder);
        }
        markUpdated();
    }

    public boolean canAcceptCashPlan(int[] plan) {
        if (isCreativeMachine()) {
            return true;
        }
        if (plan == null || plan.length > DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length) {
            return false;
        }
        int emptySlots = 0;
        for (int slot = 0; slot < CASH_SLOT_COUNT; slot++) {
            if (cashHandler.getStackInSlot(slot).isEmpty()) {
                emptySlots++;
            }
        }

        for (int index = 0; index < plan.length; index++) {
            int remaining = plan[index];
            if (remaining < 0) {
                return false;
            }
            Item item = DollarBills.getCashItemForDenominationCents(DollarBills.cashDenominationCentsForIndex(index));
            if (remaining == 0) {
                continue;
            }
            if (item == null) {
                return false;
            }

            ItemStack template = item.getDefaultInstance();
            int stackLimit = template.getMaxStackSize();
            for (int slot = 0; slot < CASH_SLOT_COUNT && remaining > 0; slot++) {
                ItemStack existing = cashHandler.getStackInSlot(slot);
                int max = Math.min(cashHandler.getSlotLimit(slot), stackLimit);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, template)
                        && existing.getCount() < max) {
                    remaining -= Math.min(remaining, max - existing.getCount());
                }
            }

            int slotsNeeded = (int) ((remaining + (long) stackLimit - 1L) / stackLimit);
            if (slotsNeeded > emptySlots) {
                return false;
            }
            emptySlots -= slotsNeeded;
        }
        return true;
    }

    public boolean acceptCashPlan(int[] plan) {
        if (isCreativeMachine()) {
            return true;
        }
        if (!canAcceptCashPlan(plan)) {
            return false;
        }
        ItemStack[] before = new ItemStack[CASH_SLOT_COUNT];
        for (int slot = 0; slot < CASH_SLOT_COUNT; slot++) {
            before[slot] = cashHandler.getStackInSlot(slot).copy();
        }
        for (int index = 0; index < plan.length; index++) {
            int remaining = plan[index];
            Item item = DollarBills.getCashItemForDenominationCents(DollarBills.cashDenominationCentsForIndex(index));
            if (remaining == 0 || item == null) {
                continue;
            }
            while (remaining > 0) {
                int chunk = Math.min(remaining, item.getDefaultInstance().getMaxStackSize());
                ItemStack remainder = insertIntoCash(new ItemStack(item, chunk));
                if (!remainder.isEmpty()) {
                    for (int slot = 0; slot < CASH_SLOT_COUNT; slot++) {
                        cashHandler.setStackInSlot(slot, before[slot]);
                    }
                    return false;
                }
                remaining -= chunk;
            }
        }
        markUpdated();
        return true;
    }

    public List<ItemStack> collectCash() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < CASH_SLOT_COUNT; slot++) {
            ItemStack stack = cashHandler.extractItem(slot, cashHandler.getStackInSlot(slot).getCount(), false);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        markUpdated();
        return result;
    }

    public List<ItemStack> collectStock(int slot) {
        List<ItemStack> result = new ArrayList<>();
        if (!validSlot(slot) || isCreativeMachine()) {
            return result;
        }
        for (int storageSlot = 0; storageSlot < STORAGE_SLOTS_PER_DISPLAY; storageSlot++) {
            ItemStack stack = stockHandlers[slot].extractItem(storageSlot,
                    stockHandlers[slot].getStackInSlot(storageSlot).getCount(), false);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        markUpdated();
        return result;
    }

    public void addSale(long amountDollars) {
        if (amountDollars <= 0L) {
            return;
        }
        totalSalesDollars = safeAdd(totalSalesDollars, amountDollars);
        markUpdated();
    }

    public void dropContents(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < DISPLAY_SLOT_COUNT; slot++) {
            ItemStack configured = displayItems[slot];
            if (!configured.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D, configured.copy());
            }
            for (ItemStack stack : collectStock(slot)) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D, stack);
            }
        }
        for (ItemStack stack : collectCash()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D, stack);
        }
    }

    private ItemStack insertIntoStorage(int slot, ItemStack stack) {
        ItemStack remainder = stack;
        for (int storageSlot = 0; storageSlot < STORAGE_SLOTS_PER_DISPLAY && !remainder.isEmpty(); storageSlot++) {
            remainder = stockHandlers[slot].insertItem(storageSlot, remainder, false);
        }
        return remainder;
    }

    private ItemStack insertIntoCash(ItemStack stack) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < CASH_SLOT_COUNT && !remainder.isEmpty(); slot++) {
            remainder = cashHandler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < DISPLAY_SLOT_COUNT;
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
        tag.putString("owner_name", getOwnerName());
        if (settlementAccountId != null) {
            tag.putUUID("settlement_account_uuid", settlementAccountId);
        }
        tag.putLong("total_sales_dollars", totalSalesDollars);
        for (int slot = 0; slot < DISPLAY_SLOT_COUNT; slot++) {
            if (!displayItems[slot].isEmpty()) {
                tag.put(DISPLAY_PREFIX + slot, displayItems[slot].save(registries));
            }
            tag.putLong(PRICE_PREFIX + slot, pricesDollars[slot]);
            tag.put(STOCK_PREFIX + slot, stockHandlers[slot].serializeNBT(registries));
        }
        tag.put(CASH_STORAGE_KEY, cashHandler.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerUuid = tag.hasUUID("owner_uuid") ? tag.getUUID("owner_uuid") : null;
        ownerName = tag.getString("owner_name");
        settlementAccountId = tag.hasUUID("settlement_account_uuid") ? tag.getUUID("settlement_account_uuid") : null;
        totalSalesDollars = Math.max(0L, tag.getLong("total_sales_dollars"));
        for (int slot = 0; slot < DISPLAY_SLOT_COUNT; slot++) {
            displayItems[slot] = tag.contains(DISPLAY_PREFIX + slot)
                    ? ItemStack.parseOptional(registries, tag.getCompound(DISPLAY_PREFIX + slot))
                    : ItemStack.EMPTY;
            pricesDollars[slot] = clampPrice(tag.getLong(PRICE_PREFIX + slot));
            if (tag.contains(STOCK_PREFIX + slot)) {
                CompoundTag stock = tag.getCompound(STOCK_PREFIX + slot).copy();
                stock.putInt("Size", STORAGE_SLOTS_PER_DISPLAY);
                stockHandlers[slot].deserializeNBT(registries, stock);
            }
            if (tag.contains(VISIBLE_STOCK_PREFIX + slot)) {
                syncedStockCounts[slot] = Math.max(0, tag.getInt(VISIBLE_STOCK_PREFIX + slot));
            }
        }
        if (tag.contains(CASH_STORAGE_KEY)) {
            CompoundTag cash = tag.getCompound(CASH_STORAGE_KEY).copy();
            cash.putInt("Size", CASH_SLOT_COUNT);
            cashHandler.deserializeNBT(registries, cash);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (int slot = 0; slot < DISPLAY_SLOT_COUNT; slot++) {
            if (!displayItems[slot].isEmpty()) {
                tag.put(DISPLAY_PREFIX + slot, displayItems[slot].save(registries));
            }
            tag.putLong(PRICE_PREFIX + slot, pricesDollars[slot]);
            tag.putInt(VISIBLE_STOCK_PREFIX + slot, isCreativeMachine() ? Integer.MAX_VALUE : getStoredStockCount(slot));
        }
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static long clampPrice(long value) {
        return value <= 0L ? DEFAULT_PRICE_DOLLARS : Math.min(MAX_PRICE_DOLLARS, value);
    }

    public AABB getRenderBoundingBox() {
        // The Blockbench mesh is two blocks tall and slightly wider/deeper than its base block.
        return new AABB(worldPosition).expandTowards(0.0D, 1.0D, 0.0D).inflate(1.0D, 0.1D, 1.0D);
    }
}
