package net.austizz.ultimatebankingsystem.storage;

import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/**
 * NBT-compatible item storage.
 *
 * <p>The serialized shape intentionally matches the established ItemStackHandler
 * format ({@code Size} plus {@code Items} entries with {@code Slot}) so existing
 * placed blocks and item data continue loading unchanged.</p>
 */
public final class UbsItemInventory {
    private static final String ITEMS_KEY = "Items";
    private static final String SIZE_KEY = "Size";
    private static final String SLOT_KEY = "Slot";

    private final List<ItemStack> stacks;
    private final IntUnaryOperator slotLimit;
    private final BiPredicate<Integer, ItemStack> itemValidator;
    private final IntConsumer changeListener;

    public UbsItemInventory(int slots) {
        this(slots, ignored -> 64, (ignored, stack) -> true, ignored -> {
        });
    }

    public UbsItemInventory(int slots, IntUnaryOperator slotLimit,
                            BiPredicate<Integer, ItemStack> itemValidator,
                            IntConsumer changeListener) {
        if (slots < 0) {
            throw new IllegalArgumentException("slots must not be negative");
        }
        this.slotLimit = Objects.requireNonNull(slotLimit, "slotLimit");
        this.itemValidator = Objects.requireNonNull(itemValidator, "itemValidator");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
        this.stacks = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            stacks.add(ItemStack.EMPTY);
        }
    }

    public int getSlots() {
        return stacks.size();
    }

    public ItemStack getStackInSlot(int slot) {
        return isSlotValid(slot) ? stacks.get(slot) : ItemStack.EMPTY;
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (!isSlotValid(slot)) {
            return;
        }
        stacks.set(slot, stack == null ? ItemStack.EMPTY : stack);
        changed(slot);
    }

    public int getSlotLimit(int slot) {
        return isSlotValid(slot) ? Math.max(1, slotLimit.applyAsInt(slot)) : 0;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return isSlotValid(slot) && stack != null && !stack.isEmpty() && itemValidator.test(slot, stack);
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        ItemStack existing = stacks.get(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStackDataCompat.sameItemSameComponents(existing, stack)) {
                return stack;
            }
            limit = Math.min(limit, existing.getMaxStackSize());
        }
        int accepted = Math.min(Math.max(0, limit - existing.getCount()), stack.getCount());
        if (accepted <= 0) {
            return stack;
        }
        if (!simulate) {
            if (existing.isEmpty()) {
                ItemStack inserted = stack.copy();
                inserted.setCount(accepted);
                stacks.set(slot, inserted);
            } else {
                existing.grow(accepted);
            }
            changed(slot);
        }
        if (accepted == stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        return remainder;
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isSlotValid(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int extractedCount = Math.min(amount, existing.getCount());
        ItemStack extracted = existing.copy();
        extracted.setCount(extractedCount);
        if (!simulate) {
            existing.shrink(extractedCount);
            if (existing.isEmpty()) {
                stacks.set(slot, ItemStack.EMPTY);
            }
            changed(slot);
        }
        return extracted;
    }

    public CompoundTag serializeNbt(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt(SIZE_KEY, getSlots());
        ListTag items = new ListTag();
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CompoundTag stackTag = ItemStackDataCompat.saveStack(stack, registries);
            if (stackTag.isEmpty()) {
                continue;
            }
            stackTag.putInt(SLOT_KEY, slot);
            items.add(stackTag);
        }
        root.put(ITEMS_KEY, items);
        return root;
    }

    public CompoundTag serializeNbt() {
        return serializeNbt(ItemStackDataCompat.DEFAULT_REGISTRIES);
    }

    public void deserializeNbt(CompoundTag root, HolderLookup.Provider registries) {
        for (int slot = 0; slot < getSlots(); slot++) {
            stacks.set(slot, ItemStack.EMPTY);
        }
        if (root == null || !root.contains(ITEMS_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag items = root.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag stackTag = items.getCompound(index);
            int slot = stackTag.getInt(SLOT_KEY);
            if (!isSlotValid(slot)) {
                continue;
            }
            ItemStack stack = ItemStackDataCompat.parseStack(stackTag, registries);
            if (!stack.isEmpty()) {
                stacks.set(slot, stack);
            }
        }
    }

    public void deserializeNbt(CompoundTag root) {
        deserializeNbt(root, ItemStackDataCompat.DEFAULT_REGISTRIES);
    }

    private boolean isSlotValid(int slot) {
        return slot >= 0 && slot < stacks.size();
    }

    private void changed(int slot) {
        changeListener.accept(slot);
    }
}
