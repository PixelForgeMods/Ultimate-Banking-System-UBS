package net.austizz.ultimatebankingsystem.storage;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Vanilla menu slot backed by a shared UBS item inventory. */
public class UbsItemInventorySlot extends Slot {
    private final UbsItemInventory inventory;
    private final int inventorySlot;

    public UbsItemInventorySlot(UbsItemInventory inventory, int slot, int x, int y) {
        super(new UbsItemInventoryContainer(inventory), slot, x, y);
        this.inventory = inventory;
        this.inventorySlot = slot;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return inventory.isItemValid(inventorySlot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return inventory.getSlotLimit(inventorySlot);
    }
}
