package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.VendingMachineBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.VendingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative stock menu for one vending-machine display slot. The machine storage is
 * a live item handler, so the normal container click protocol handles dragging, split stacks,
 * double-click collection, number-key swaps, and shift-click transfers.
 */
public final class VendingMachineStockMenu extends AbstractContainerMenu {
    public static final int STOCK_SLOT_COUNT = VendingMachineBlockEntity.STORAGE_SLOTS_PER_DISPLAY;
    public static final int PLAYER_SLOT_START = STOCK_SLOT_COUNT;
    public static final int PLAYER_SLOT_COUNT = 36;

    private static final int COLUMNS = 9;
    public static final int SLOT_SIZE = 24;
    public static final int SLOT_GRID_LEFT = 192;
    private static final int INVENTORY_X = SLOT_GRID_LEFT;
    private static final int STOCK_Y = 82;
    private static final int PLAYER_INVENTORY_Y = 182;

    private final VendingMachineBlockEntity machine;
    private final IItemHandler stockHandler;
    private final BlockPos machinePos;
    private final int displaySlot;
    private final ItemStack displayItem;
    private final boolean creative;

    public static VendingMachineStockMenu fromNetwork(int containerId,
                                                       Inventory playerInventory,
                                                       FriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        int displaySlot = data.readVarInt();
        boolean creative = data.readBoolean();
        ItemStack displayItem = data instanceof RegistryFriendlyByteBuf registryData
                ? ItemStack.OPTIONAL_STREAM_CODEC.decode(registryData)
                : ItemStack.EMPTY;
        VendingMachineBlockEntity machine = resolveMachine(playerInventory, pos);
        return new VendingMachineStockMenu(containerId, playerInventory, machine, pos, displaySlot,
                displayItem, creative);
    }

    public VendingMachineStockMenu(int containerId,
                                   Inventory playerInventory,
                                   VendingMachineBlockEntity machine,
                                   int displaySlot) {
        this(containerId,
                playerInventory,
                machine,
                machine == null ? BlockPos.ZERO : machine.getBlockPos(),
                displaySlot,
                machine == null ? ItemStack.EMPTY : machine.getDisplayItem(displaySlot),
                machine != null && machine.isCreativeMachine());
    }

    private VendingMachineStockMenu(int containerId,
                                    Inventory playerInventory,
                                    VendingMachineBlockEntity machine,
                                    BlockPos machinePos,
                                    int displaySlot,
                                    ItemStack displayItem,
                                    boolean creative) {
        super(ModMenus.VENDING_MACHINE_STOCK.get(), containerId);
        this.machine = machine;
        this.machinePos = machinePos == null ? BlockPos.ZERO : machinePos.immutable();
        this.displaySlot = displaySlot;
        this.displayItem = displayItem == null ? ItemStack.EMPTY : displayItem.copyWithCount(1);
        this.creative = creative;
        IItemHandler resolvedHandler = machine == null ? null : machine.getStockHandler(displaySlot);
        this.stockHandler = resolvedHandler == null
                ? new ItemStackHandler(STOCK_SLOT_COUNT)
                : resolvedHandler;

        addStockSlots();
        addPlayerInventorySlots(playerInventory);
    }

    public BlockPos getMachinePos() {
        return machinePos;
    }

    public int getDisplaySlot() {
        return displaySlot;
    }

    public ItemStack getDisplayItem() {
        return displayItem.copy();
    }

    public boolean isCreative() {
        return creative;
    }

    public int getStockSlotCount() {
        return STOCK_SLOT_COUNT;
    }

    public int getStockItemCount() {
        if (creative) {
            return -1;
        }
        int total = 0;
        for (int slot = 0; slot < STOCK_SLOT_COUNT; slot++) {
            total += stockHandler.getStackInSlot(slot).getCount();
        }
        return total;
    }

    private void addStockSlots() {
        for (int slot = 0; slot < STOCK_SLOT_COUNT; slot++) {
            addSlot(new VendingStockSlot(stockHandler, slot,
                    INVENTORY_X + (slot % COLUMNS) * SLOT_SIZE,
                    STOCK_Y + (slot / COLUMNS) * SLOT_SIZE));
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(playerInventory,
                        column + row * COLUMNS + 9,
                        INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE));
            }
        }

        int hotbarY = PLAYER_INVENTORY_Y + (3 * SLOT_SIZE) + 6;
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(playerInventory,
                    column,
                    INVENTORY_X + column * SLOT_SIZE,
                    hotbarY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || (creative && index < STOCK_SLOT_COUNT)) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copied = original.copy();
        boolean moved;
        if (index < STOCK_SLOT_COUNT) {
            moved = moveItemStackTo(original, PLAYER_SLOT_START, slots.size(), true);
        } else {
            moved = !creative && moveItemStackTo(original, 0, STOCK_SLOT_COUNT, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copied;
    }

    @Override
    public boolean stillValid(Player player) {
        if (machine == null || player == null || player.level() == null) {
            return false;
        }
        if (!VendingMachineBlock.isMachineState(player.level().getBlockState(machinePos))) {
            return false;
        }
        if (player.distanceToSqr(machinePos.getX() + 0.5D,
                machinePos.getY() + 0.5D,
                machinePos.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return player.level().isClientSide() || machine.canConfigure(player);
    }

    private static @Nullable VendingMachineBlockEntity resolveMachine(Inventory playerInventory, BlockPos pos) {
        if (playerInventory == null || playerInventory.player == null || pos == null) {
            return null;
        }
        var level = playerInventory.player.level();
        if (level == null) {
            return null;
        }
        BlockPos lower = VendingMachineBlock.lowerPos(pos, level.getBlockState(pos));
        return level.getBlockEntity(lower) instanceof VendingMachineBlockEntity machine ? machine : null;
    }

    private final class VendingStockSlot extends SlotItemHandler {
        private VendingStockSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return !creative
                    && !displayItem.isEmpty()
                    && ItemStack.isSameItemSameComponents(displayItem, stack)
                    && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return !creative && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return displayItem.isEmpty() ? super.getMaxStackSize()
                    : Math.min(super.getMaxStackSize(), displayItem.getMaxStackSize());
        }
    }
}
