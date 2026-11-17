package net.austizz.ultimatebankingsystem.payments;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.UUID;

/** The sole inventory copy of an inserted card, also recoverable when its chunk is unloaded. */
public final class AtmCardCustody extends SavedData {
    private record Card(GlobalPos atm, int slot, ItemStack stack) {}
    private final HashMap<UUID, Card> cards = new HashMap<>();
    public static AtmCardCustody get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AtmCardCustody::new, AtmCardCustody::load, null), "ubs_atm_cards");
    }
    public ItemStack card(UUID owner, GlobalPos atm) {
        var card = cards.get(owner);
        return card != null && card.atm.equals(atm) ? card.stack.copy() : ItemStack.EMPTY;
    }
    public boolean insert(ServerPlayer player, GlobalPos atm, int slot) {
        var held = player.getInventory().getItem(slot);
        if (cards.containsKey(player.getUUID()) || !held.is(net.austizz.ultimatebankingsystem.item.ModItems.CREDIT_CARD.get())) return false;
        cards.put(player.getUUID(), new Card(atm, slot, held.split(1)));
        setDirty(); player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        return true;
    }
    public boolean giveBack(ServerPlayer player) {
        var card = cards.remove(player.getUUID());
        if (card == null) return false;
        setDirty();
        var stack = card.stack;
        if (player.getInventory().getItem(card.slot).isEmpty()) player.getInventory().setItem(card.slot, stack);
        else if (!player.getInventory().add(stack)) {
            var drop = player.drop(stack, false);
            if (drop != null) { drop.setTarget(player.getUUID()); drop.setNoPickUpDelay(); }
        }
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        return true;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        cards.forEach((owner, card) -> {
            var entry = new CompoundTag(); entry.putUUID("owner", owner);
            entry.putString("dimension", card.atm.dimension().location().toString());
            entry.putLong("pos", card.atm.pos().asLong()); entry.putInt("slot", card.slot);
            entry.put("card", card.stack.save(registries)); list.add(entry);
        });
        tag.put("cards", list); return tag;
    }
    public static AtmCardCustody load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new AtmCardCustody();
        for (var value : tag.getList("cards", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (!entry.hasUUID("owner")) continue;
            var id = net.minecraft.resources.ResourceLocation.tryParse(entry.getString("dimension"));
            if (id == null) continue;
            var stack = ItemStack.parseOptional(registries, entry.getCompound("card"));
            if (stack.isEmpty()) continue;
            var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
            data.cards.put(entry.getUUID("owner"), new Card(GlobalPos.of(dimension, net.minecraft.core.BlockPos.of(entry.getLong("pos"))),
                    Math.clamp(entry.getInt("slot"), 0, 40), stack));
        }
        return data;
    }
}
