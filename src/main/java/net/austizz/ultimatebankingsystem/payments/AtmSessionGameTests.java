package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.network.*;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmSessionGameTests {
    @GameTest(template = "empty3x3x3")
    public static void createsPinThenRunsAuthorizedCashMenuWithoutReplay(GameTestHelper helper) {
        verifyAtmSession(helper,ModBlocks.ATM_MACHINE.get());
    }
    public static void verifyAtmSession(GameTestHelper helper,net.minecraft.world.level.block.Block block) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "atm-menu-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        var bank = BankManager.getCentralBank(helper.getLevel().getServer());
        UUID accountId = UUID.randomUUID(), otherAccountId = UUID.randomUUID(), cardId = UUID.randomUUID();
        var account = new AccountHolder(player.getUUID(), new BigDecimal("100"), AccountTypes.CheckingAccount, "", bank.getBankId(), accountId);
        bank.getBankAccounts().put(accountId, account);
        var record = new CompoundTag();
        record.putUUID("accountId", accountId); record.putUUID("bankId", bank.getBankId());
        record.putString("cardNumber", "1234567890123456"); record.putString("cvc", "123");
        record.putLong("expiryEpochMillis", System.currentTimeMillis() + 86_400_000L);
        bank.getIssuedCreditCards().put(cardId, record);
        var card = new ItemStack(ModItems.CREDIT_CARD.get());
        var tag = new CompoundTag(); tag.putUUID(CreditCardService.TAG_CARD_ID, cardId);
        ItemStackDataCompat.setCustomData(card, tag);
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        var response = new AtomicReference<AtmActionResultPayload>();
        try {
            helper.assertTrue(AtmCardInsertionService.validate(player, pos, bank).needsPin(), "Missing PIN must trigger setup at insertion");
            var machine = (net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(AtmTerminalService.claim(player, machine), "Acquire exclusive ATM view");
            helper.assertTrue(AtmCardInsertionService.insert(player, pos, bank).accepted(), "Insert actual card before authenticating");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "Authentication works with the card held by the ATM");
            var mismatch = AtmPinService.authenticate(player, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "0123", true, "1234"), bank);
            helper.assertFalse(mismatch.accepted() || account.hasPin(), "Mismatched PINs must never save");
            var auth = AtmPinService.authenticate(player, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "0123", true, "0123"), bank);
            helper.assertTrue(auth.accepted() && account.matchesPin("0123"), "Matching setup saves PIN with leading zero");
            helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).needsPin(), "Reinsertion must request existing PIN");
            AtmSessionService.close(player);
            helper.assertFalse(AtmPinService.verify(player, pos, cardId, "9999", true, "9999", bank).accepted(), "Setup cannot overwrite an existing PIN without authentication");
            auth = AtmPinService.authenticate(player, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "0123"), bank);
            helper.assertTrue(account.matchesPin("0123"), "Existing PIN remains unchanged");
            AtmSessionService.execute(player, new AtmActionRequestPayload(UUID.randomUUID(), UUID.randomUUID(), 1, "WITHDRAW", "20"), bank, response::set);
            helper.assertFalse(response.get().success(), "Unauthenticated cash requests must fail");
            var withdraw = new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 1, "BALANCE", "");
            AtmSessionService.execute(player, withdraw, bank, response::set);
            helper.assertTrue(response.get().success(), "Authorized balance: " + response.get().message());
            var legacy = new AtomicReference<WithdrawResponsePayload>();
            ModPayloads.executeAtmWithdrawal(player, new WithdrawRequestPayload(accountId, "20"), legacy::set);
            helper.assertTrue(legacy.get().success(), "Legacy cash handler must still work");
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("80")) == 0, "Withdrawal debits once");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player)) == 2000, "Withdrawal creates real cash");
            helper.assertTrue(DollarBills.getAvailableCashCounts(player)[2] == 1, "Automatic withdrawal uses one twenty instead of several smaller bills");
            AtmSessionService.execute(player, withdraw, bank, response::set);
            helper.assertFalse(response.get().success(), "Replaying withdrawal cannot spend twice");
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("80")) == 0, "Replay preserves balance");
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 2, "DEPOSIT", "20"), bank, response::set);
            helper.assertTrue(response.get().success(), "Authorized deposit: " + response.get().message());
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("100")) == 0, "Deposit credits matching cash");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player)) == 0, "Deposit consumes real cash");
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 3, "BALANCE", ""), bank, response::set);
            helper.assertTrue(response.get().success() && new BigDecimal(response.get().balance()).compareTo(new BigDecimal("100")) == 0, "Balance comes from authoritative account");
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 4, "HISTORY", ""), bank, response::set);
            helper.assertTrue(response.get().success() && response.get().message().contains("ATM Cash"), "History includes completed cash operations");
            account.freeze("Test freeze");
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 5, "CHECK_WITHDRAW", "20"), bank, response::set);
            helper.assertFalse(response.get().success(), "Existing frozen-account protection remains active");
            account.unfreeze();
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 6, "DEPOSIT", "-20"), bank, response::set);
            helper.assertFalse(response.get().success(), "Negative amount is rejected without mutation");
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 7, "CHECK_WITHDRAW", "9999999"), bank, response::set);
            helper.assertFalse(response.get().success(), "Existing withdrawal limits remain active");
            AtmSessionService.execute(player,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),8,"ACCOUNT_INFO",""),bank,response::set);
            helper.assertTrue(response.get().success() && response.get().message().contains(accountId.toString()),"Authenticated settings show linked account details");
            AtmSessionService.execute(player,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),9,"SET_PRIMARY",""),bank,response::set);
            helper.assertTrue(response.get().success() && account.isPrimaryAccount(),"Settings mark account primary through central bank");
            AtmSessionService.execute(player,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),10,"SET_LIMIT","0"),bank,response::set);
            helper.assertFalse(response.get().success(),"Zero temporary limit is rejected");
            AtmSessionService.execute(player,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),11,"SET_LIMIT","200"),bank,response::set);
            helper.assertTrue(response.get().success() && account.getEffectiveWithdrawalLimit(player.server.overworld().getGameTime()).intValueExact()==200,"Temporary limit uses existing account rules");
            AtmSessionService.execute(player,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),12,"SET_LIMIT","300",2,new UUID(0,0),60,"0123"),bank,response::set);
            helper.assertTrue(response.get().success(),"A later settings request can replace temporary limit");
            record.putBoolean("blocked", true);
            AtmSessionService.execute(player, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 13, "BALANCE", ""), bank, response::set);
            helper.assertFalse(response.get().success(), "Card blocked after authentication invalidates session");
            record.putBoolean("blocked", false);
            var second = AtmPinService.authenticate(player, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "0123"), bank);
            helper.assertTrue(second.accepted(), "Existing PIN opens a new session");
            bank.getBankAccounts().put(otherAccountId, new AccountHolder(player.getUUID(), BigDecimal.ZERO, AccountTypes.CheckingAccount, "9999", bank.getBankId(), otherAccountId));
            record.putUUID("accountId", otherAccountId);
            AtmSessionService.execute(player, new AtmActionRequestPayload(second.sessionToken(), UUID.randomUUID(), 1, "BALANCE", ""), bank, response::set);
            helper.assertFalse(response.get().success(), "Session cannot follow a card relinked to another account");
            record.putUUID("accountId", accountId);
            var third = AtmPinService.authenticate(player, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "0123"), bank);
            var dispenser = (net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity) helper.getLevel().getBlockEntity(pos);
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 1, "WITHDRAW", "20"), bank, response::set);
            helper.assertFalse(response.get().success(), "Cannot skip amount verification");
            var dailyBefore = account.getDailyWithdrawnAmount();
            int transactionsBefore = account.getTransactions().size();
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 2, "CHECK_WITHDRAW", "20"), bank, response::set);
            helper.assertTrue(response.get().success() && dispenser.canReserve(), "Amount check must not start dispensing");
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("100")) == 0
                    && dailyBefore.compareTo(account.getDailyWithdrawnAmount()) == 0 && transactionsBefore == account.getTransactions().size(), "Preview cannot debit, spend limits or record a transaction");
            AtmSessionService.cancelWithdrawal(player);
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 3, "WITHDRAW", "20"), bank, response::set);
            helper.assertFalse(response.get().success(), "Going back to edit invalidates prior approval");
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 4, "CHECK_WITHDRAW", "20"), bank, response::set);
            helper.assertTrue(response.get().success(), "Amount can be checked again");
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 5, "WITHDRAW", "20", 999), bank, response::set);
            helper.assertFalse(response.get().success() || !dispenser.canReserve(), "Forged option cannot reserve cash");
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 6, "WITHDRAW", "21", 1), bank, response::set);
            helper.assertFalse(response.get().success(), "Selection is bound to the verified amount");
            var malformed = new java.util.concurrent.atomic.AtomicReference<WithdrawResponsePayload>();
            ModPayloads.executeAtmWithdrawal(player, new WithdrawRequestPayload(accountId, "20"), malformed::set, dispenser,
                    new int[]{0, 0, 0, -2, 0, 0, 0}, false);
            helper.assertFalse(malformed.get().success(), "Negative bill counts cannot debit money");
            ModPayloads.executeAtmWithdrawal(player, new WithdrawRequestPayload(accountId, "20"), malformed::set, dispenser,
                    new int[]{0, 0, 0, 1, 0, 0, 0}, false);
            helper.assertFalse(malformed.get().success(), "Incorrect bill total cannot debit money");
            account.freeze("Frozen while choosing bills");
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 7, "WITHDRAW", "20", 1), bank, response::set);
            helper.assertFalse(response.get().success(), "Final confirmation rechecks account policy after preview");
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("100")) == 0 && dailyBefore.compareTo(account.getDailyWithdrawnAmount()) == 0, "Rejected selection leaves balance and limits unchanged");
            account.unfreeze();
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 8, "CHECK_WITHDRAW", "20"), bank, response::set);
            helper.assertTrue(response.get().success(), "Recheck succeeds after unfreezing");
            var physical = new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 9, "WITHDRAW", "20", 1);
            AtmSessionService.execute(player, physical, bank, response::set);
            helper.assertTrue(response.get().success(), "Physical withdrawal is accepted");
            helper.assertTrue(dispenser.cashPlan()[3] == 2 && dispenser.cashPlan()[2] == 0, "Selected $10 mix must dispense two tens rather than an automatic twenty");
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("80")) == 0, "Physical withdrawal debits once");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(dispenser.cashPlan()) == 2000, "Debited cash is held in the ATM");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player)) == 0, "Cash is not instantly added to inventory");
            AtmSessionService.execute(player, physical, bank, response::set);
            helper.assertFalse(response.get().success(), "Physical withdrawal replay is rejected");
            helper.assertTrue(account.getBalance().compareTo(new BigDecimal("80")) == 0, "Replay does not debit again");
            var saved = dispenser.saveWithoutMetadata(helper.getLevel().registryAccess());
            var restored = new net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity(pos, dispenser.getBlockState());
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertTrue(java.util.Arrays.equals(dispenser.cashPlan(), restored.cashPlan()), "Pending money survives world save/load");
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 0, "END", ""), bank, response::set);
            AtmSessionService.execute(player, new AtmActionRequestPayload(third.sessionToken(), UUID.randomUUID(), 1, "BALANCE", ""), bank, response::set);
            helper.assertFalse(response.get().success(), "Returning card revokes authentication");
        } finally {
            AtmTerminalService.close(player);
            bank.getBankAccounts().remove(accountId);
            bank.getBankAccounts().remove(otherAccountId);
            bank.getIssuedCreditCards().remove(cardId);
            player.getInventory().clearContent();
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }
}
