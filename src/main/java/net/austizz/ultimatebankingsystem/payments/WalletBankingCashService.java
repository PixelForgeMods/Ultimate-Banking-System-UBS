package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.WalletData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class WalletBankingCashService {
    private WalletBankingCashService() {
    }

    public static CashStorage resolve(ServerPlayer player) {
        ItemStack heldWallet = WalletData.findHeldWallet(player);
        boolean walletHeld = !heldWallet.isEmpty();
        if (walletHeld) {
            WalletData.ensureOwner(heldWallet, player);
        }
        CashStoragePreference preference = CashStoragePreference.select(
                walletHeld,
                walletHeld && WalletData.isOwner(heldWallet, player)
        );
        return new CashStorage(
                preference == CashStoragePreference.WALLET ? heldWallet : ItemStack.EMPTY,
                preference
        );
    }

    private static java.util.List<ItemStack> depositWallets(ServerPlayer player) {
        var wallets = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (WalletData.isWallet(stack) && WalletData.isOwner(stack, player)) wallets.add(stack);
        }
        return wallets;
    }
    public static int[] depositAvailableCounts(ServerPlayer player) {
        var counts = DollarBills.getAvailableTenderAsCashCounts(player);
        for (var wallet : depositWallets(player)) {
            var stored = WalletData.getCashCounts(wallet);
            for (int i = 0; i < counts.length; i++) counts[i] = (int)Math.min(Integer.MAX_VALUE, (long)counts[i] + stored[i]);
        }
        return counts;
    }
    public static boolean removeDepositBills(ServerPlayer player, int[] plan) {
        if (plan.length != DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length || java.util.Arrays.stream(plan).anyMatch(n -> n < 0)) return false;
        var stock = depositAvailableCounts(player);
        for (int i = 0; i < plan.length; i++) if (plan[i] > stock[i]) return false;
        var remaining = plan.clone();
        for (var wallet : depositWallets(player)) {
            var counts = WalletData.getCashCounts(wallet); boolean changed = false;
            for (int i = 0; i < remaining.length; i++) {
                int take = Math.min(counts[i],remaining[i]);counts[i] -= take;remaining[i] -= take;changed |= take > 0;
            }
            if (changed) { WalletData.ensureOwner(wallet,player);WalletData.setCashCounts(wallet,counts); }
        }
        DollarBills.removeTender(player,remaining);
        CashStorage.sync(player);
        return true;
    }

    public record CashStorage(ItemStack wallet, CashStoragePreference preference) {
        public CashStorage {
            wallet = wallet == null ? ItemStack.EMPTY : wallet;
            preference = preference == null ? CashStoragePreference.CARRIED_CASH : preference;
        }

        public boolean walletBacked() {
            return preference == CashStoragePreference.WALLET;
        }

        public String label() {
            return walletBacked() ? "wallet cash" : "carried cash";
        }

        public int[] availableCounts(ServerPlayer player) {
            return walletBacked()
                    ? WalletData.getCashCounts(wallet)
                    : DollarBills.getAvailableTenderAsCashCounts(player);
        }

        public boolean canAdd(int[] plan) {
            return !walletBacked() || WalletData.canAddCashPlan(wallet, plan);
        }

        public boolean remove(ServerPlayer player, int[] plan) {
            if (walletBacked()) {
                if (!WalletData.removeCashPlan(wallet, plan)) {
                    return false;
                }
                sync(player);
                return true;
            }
            DollarBills.removeTender(player, plan);
            return true;
        }

        public boolean add(ServerPlayer player, int[] plan) {
            if (walletBacked()) {
                if (!WalletData.canAddCashPlan(wallet, plan)) {
                    return false;
                }
                WalletData.addCashPlan(wallet, plan);
                sync(player);
                return true;
            }
            DollarBills.giveCash(player, plan);
            return true;
        }

        private static void sync(ServerPlayer player) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
