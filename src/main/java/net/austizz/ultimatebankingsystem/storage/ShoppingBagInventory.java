package net.austizz.ultimatebankingsystem.storage;

import net.austizz.ultimatebankingsystem.item.ShoppingBagItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ShoppingBagInventory extends ItemStackHandler {
    public static final int SLOT_COUNT = 9;

    public ShoppingBagInventory() {
        super(SLOT_COUNT);
    }

    public static boolean canStore(ItemStack stack) {
        return !(stack.getItem() instanceof ShoppingBagItem);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canStore(stack);
    }
}
