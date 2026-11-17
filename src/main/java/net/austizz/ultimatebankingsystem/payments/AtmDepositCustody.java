package net.austizz.ultimatebankingsystem.payments;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import java.util.HashMap;
import java.util.UUID;

/** Uncredited cash has one persistent custodian, even if the ATM chunk disappears. */
public final class AtmDepositCustody extends SavedData {
    private record Entry(GlobalPos atm, UUID account, int[] bills) {}
    private final HashMap<UUID, Entry> entries = new HashMap<>();
    public static AtmDepositCustody get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AtmDepositCustody::new, AtmDepositCustody::load, null), "ubs_atm_deposits");
    }
    public boolean open(ServerPlayer player, GlobalPos atm, UUID account) {
        if (entries.containsKey(player.getUUID())) return false;
        entries.put(player.getUUID(), new Entry(atm, account, new int[12])); setDirty(); return true;
    }
    public int[] bills(UUID owner, GlobalPos atm) {
        var entry = entries.get(owner); return entry != null && entry.atm.equals(atm) ? entry.bills.clone() : new int[12];
    }
    public boolean contains(UUID owner, GlobalPos atm) { var e = entries.get(owner); return e != null && e.atm.equals(atm); }
    public boolean feed(ServerPlayer player, GlobalPos atm, int[] plan) {
        var e = entries.get(player.getUUID());
        if (e == null || !e.atm.equals(atm) || plan.length != 7 || java.util.Arrays.stream(plan).anyMatch(n -> n < 0)) return false;
        long amount = DollarBills.totalCashValueCentsLong(plan);
        if (amount <= 0 || amount + DollarBills.totalCashValueCentsLong(e.bills) > Integer.MAX_VALUE) return false;
        if (!WalletBankingCashService.removeDepositBills(player, java.util.Arrays.copyOf(plan, 12))) return false;
        for (int i = 0; i < 7; i++) e.bills[i] += plan[i];
        setDirty(); return true;
    }
    public boolean confirm(ServerPlayer player, GlobalPos atm, net.austizz.ultimatebankingsystem.account.AccountHolder account) {
        var e = entries.get(player.getUUID());
        if (e == null || !e.atm.equals(atm) || !e.account.equals(account.getAccountUUID()) || account.isFrozen()) return false;
        long cents = DollarBills.totalCashValueCentsLong(e.bills);
        if (cents <= 0) return false;
        var amount = java.math.BigDecimal.valueOf(cents, 2);
        if (!account.AddBalance(amount)) return false;
        entries.remove(player.getUUID()); setDirty();
        account.addTransaction(new net.austizz.ultimatebankingsystem.account.transaction.UserTransaction(
                net.austizz.ultimatebankingsystem.network.ModPayloads.ATM_TERMINAL_ID, account.getAccountUUID(), amount,
                java.time.LocalDateTime.now(), "ATM Cash Deposit"));
        return true;
    }
    public boolean refund(ServerPlayer player) {
        var e = entries.remove(player.getUUID());
        if (e == null) return false;
        setDirty(); var storage = WalletBankingCashService.resolve(player);
        if (!storage.add(player, e.bills)) DollarBills.giveCash(player, e.bills);
        return true;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        entries.forEach((owner,e) -> { var value = new CompoundTag(); value.putUUID("owner", owner); value.putUUID("account", e.account);
            value.putString("dimension", e.atm.dimension().location().toString()); value.putLong("pos", e.atm.pos().asLong());
            value.putIntArray("bills", e.bills); list.add(value); }); tag.put("deposits", list); return tag;
    }
    public static AtmDepositCustody load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new AtmDepositCustody();
        for (var value : tag.getList("deposits", Tag.TAG_COMPOUND)) {
            var e = (CompoundTag) value; var id = net.minecraft.resources.ResourceLocation.tryParse(e.getString("dimension"));
            if (!e.hasUUID("owner") || !e.hasUUID("account") || id == null) continue;
            int[] bills = java.util.Arrays.copyOf(e.getIntArray("bills"), 12);
            if (java.util.Arrays.stream(bills).anyMatch(n -> n < 0) || DollarBills.totalCashValueCentsLong(bills) > Integer.MAX_VALUE) continue;
            var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
            data.entries.put(e.getUUID("owner"), new Entry(GlobalPos.of(dimension, net.minecraft.core.BlockPos.of(e.getLong("pos"))), e.getUUID("account"), bills));
        }
        return data;
    }
}
