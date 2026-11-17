package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.*;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmBorrowedCardGameTests {
    @GameTest(template = "empty3x3x3")
    public static void borrowedCardUsesItsPinAndAccountWithoutUnlockingLegacyAccountRequests(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var atm = (AtmBlockEntity) level.getBlockEntity(pos);
        var borrower = new ServerPlayer(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "atm-borrower"), ClientInformation.createDefault());
        borrower.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        var bank = BankManager.getCentralBank(level.getServer());
        var ownerId = UUID.randomUUID(); var accountId = UUID.randomUUID(); var cardId = UUID.randomUUID();
        var account = new AccountHolder(ownerId, new BigDecimal("100"), AccountTypes.CheckingAccount, "4321", bank.getBankId(), accountId);
        bank.getBankAccounts().put(accountId, account);
        var record = new CompoundTag(); record.putUUID("accountId", accountId); record.putUUID("bankId", bank.getBankId());
        record.putUUID("ownerPlayerId", ownerId); record.putString("cardNumber", "4321123456789012"); record.putString("cvc", "123");
        record.putLong("expiryEpochMillis", System.currentTimeMillis() + 86_400_000);
        bank.getIssuedCreditCards().put(cardId, record);
        var card = new ItemStack(ModItems.CREDIT_CARD.get()); var data = new CompoundTag(); data.putUUID(CreditCardService.TAG_CARD_ID, cardId);
        data.putString("original_marker", "same-borrowed-card"); ItemStackDataCompat.setCustomData(card, data);
        borrower.setItemInHand(InteractionHand.MAIN_HAND, card);
        var response = new AtomicReference<AtmActionResultPayload>();
        var legacyWithdraw = new AtomicReference<WithdrawResponsePayload>();
        var legacyDeposit = new AtomicReference<DepositResponsePayload>();
        try {
            helper.assertTrue(AtmTerminalService.claim(borrower, atm), "Borrower reserves the ATM");
            var inserted = AtmCardInsertionService.insert(borrower, pos, bank);
            helper.assertTrue(inserted.accepted() && borrower.getMainHandItem().isEmpty(), "Another player's valid card enters machine custody");
            var wrong = AtmPinService.authenticate(borrower, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "0000"), bank);
            helper.assertFalse(wrong.accepted(), "Possession cannot bypass the PIN");
            helper.assertFalse(AtmSessionService.authorizes(borrower, accountId, atm), "Wrong PIN grants no account access");
            helper.assertFalse(AtmPinService.verify(borrower,pos,cardId,"9999",true,"9999",bank).accepted(),"Cannot overwrite an existing PIN without authenticating");
            AtmPinService.verify(borrower,pos,cardId,"0000",bank);
            AtmPinService.verify(borrower,pos,cardId,"0000",bank);
            helper.assertFalse(AtmPinService.verify(borrower,pos,cardId,"4321",bank).accepted(),"Third wrong attempt locks even the correct PIN");
            var savedAccount=account.save(new CompoundTag(),level.registryAccess());
            helper.assertTrue(AccountHolder.load(savedAccount,level.registryAccess()).atmPinLockedUntil()==account.atmPinLockedUntil(),"Lockout survives save/load");
            helper.assertTrue(account.atmPinLockedUntil()>System.currentTimeMillis(),"Lockout is account-scoped for sixty seconds");
            account.recordAtmPinAttempt(true,account.atmPinLockedUntil()+1);
            var auth = AtmPinService.authenticate(borrower, new AtmPinRequestPayload(pos, UUID.randomUUID(), cardId, "4321"), bank);
            helper.assertTrue(auth.accepted(), "Correct linked-account PIN authorizes the borrowed card");
            helper.assertFalse(AtmPinService.verify(borrower,pos,cardId,"9876",true,"9875",bank).accepted(),"Repeated PIN must match");
            var changed=AtmPinService.authenticate(borrower,new AtmPinRequestPayload(pos,UUID.randomUUID(),cardId,"9876",true,"9876"),bank);
            helper.assertTrue(changed.accepted() && account.matchesPin("9876") && !account.matchesPin("4321"),"Authenticated change replaces old PIN");
            auth=changed;
            AtmSessionService.execute(borrower,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),1,"SET_PRIMARY",""),bank,response::set);
            helper.assertFalse(response.get().success() || account.isPrimaryAccount(),"Borrowed card cannot change primary-account preference");
            AtmSessionService.execute(borrower,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),2,"SET_LIMIT","200"),bank,response::set);
            helper.assertFalse(response.get().success(),"Borrowed card cannot change owner's withdrawal limit");
            AtmSessionService.execute(borrower, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 3, "BALANCE", ""), bank, response::set);
            helper.assertTrue(response.get().success() && response.get().balance().equals("100"), "Balance comes from the card's account");
            ModPayloads.executeAtmWithdrawal(borrower, new WithdrawRequestPayload(accountId, "5"), legacyWithdraw::set);
            ModPayloads.executeAtmDeposit(borrower, new DepositRequestPayload(accountId, "5"), legacyDeposit::set);
            helper.assertFalse(legacyWithdraw.get().success() || legacyDeposit.get().success(), "Legacy requests without an ATM context retain owner-only authorization");
            borrower.getInventory().add(new ItemStack(DollarBills.getCashItemForDenominationCents(500)));
            AtmSessionService.execute(borrower, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 4, "DEPOSIT", "5"), bank, response::set);
            helper.assertTrue(response.get().success() && account.getBalance().compareTo(new BigDecimal("105")) == 0, "Borrower deposits their cash into the card's account");
            AtmSessionService.execute(borrower, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 5, "CHECK_WITHDRAW", "20"), bank, response::set);
            helper.assertTrue(response.get().success(), "Borrower can check a withdrawal amount");
            AtmSessionService.execute(borrower, new AtmActionRequestPayload(auth.sessionToken(), UUID.randomUUID(), 6, "WITHDRAW", "20"), bank, response::set);
            helper.assertTrue(response.get().success() && account.getBalance().compareTo(new BigDecimal("85")) == 0, "Authenticated borrowed card can withdraw from its linked account");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(atm.cashPlan()) == 2000, "Cash stays in physical dispenser custody");
            // A borrowed card may perform transactions, but cannot alter its owner's preferences.
            helper.assertTrue(account.getPlayerUUID().equals(ownerId), "Using the card never transfers account ownership");
            AtmTerminalService.close(borrower);
            helper.assertTrue(ItemStack.matches(inserted.card(), borrower.getMainHandItem()), "Return the exact borrowed card to its inserter");
            helper.assertFalse(AtmSessionService.authorizes(borrower, accountId, atm), "Returning the card revokes account access");
        } finally {
            AtmTerminalService.close(borrower); borrower.getInventory().clearContent();
            bank.getBankAccounts().remove(accountId); bank.getIssuedCreditCards().remove(cardId);
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
        }
        helper.succeed();
    }
}
