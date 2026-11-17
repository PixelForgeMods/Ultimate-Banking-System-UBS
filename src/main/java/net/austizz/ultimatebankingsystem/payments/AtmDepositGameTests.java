package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.item.*;
import net.austizz.ultimatebankingsystem.network.*;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.*;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmDepositGameTests {
    @GameTest(template="empty3x3x3", batch="atm-deposit-flow", timeoutTicks=760)
    public static void feedsMultipleBatchesCountsConfirmsOnceAndPhysicallyReturnsCancelledBills(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        ServerPlayer mock;
        try { mock=h.makeMockServerPlayerInLevel(); }
        catch(UnsupportedOperationException exception) { mock=level.getServer().getPlayerList().getPlayerByName("test-mock-player");if(mock==null)throw exception; }
        final var player=mock;
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(player.connection.getConnection());
        player.setNoGravity(true);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.6);player.getInventory().clearContent();
        level.setBlock(pos,ModBlocks.ATM_MACHINE.get().defaultBlockState(),3);var atm=(AtmBlockEntity)level.getBlockEntity(pos);
        var bank=BankManager.getCentralBank(level.getServer());UUID accountId=UUID.randomUUID(),cardId=UUID.randomUUID();
        var account=new AccountHolder(player.getUUID(),new BigDecimal("100"),AccountTypes.CheckingAccount,"1234",bank.getBankId(),accountId);
        bank.getBankAccounts().put(accountId,account);var issued=new CompoundTag();issued.putUUID("accountId",accountId);issued.putUUID("bankId",bank.getBankId());
        issued.putString("cardNumber","1234567890123456");issued.putString("cvc","123");issued.putLong("expiryEpochMillis",System.currentTimeMillis()+86_400_000);
        bank.getIssuedCreditCards().put(cardId,issued);var card=new ItemStack(ModItems.CREDIT_CARD.get());var tag=new CompoundTag();tag.putUUID(CreditCardService.TAG_CARD_ID,cardId);ItemStackDataCompat.setCustomData(card,tag);
        player.setItemInHand(InteractionHand.OFF_HAND,card);
        DollarBills.giveCash(player,new int[]{0,0,2,0,3,0,0,0,4,0,0,0});
        h.assertTrue(AtmTerminalService.claim(player,atm)&&AtmCardInsertionService.insert(player,pos,bank).accepted(),"Insert valid card");
        class Run {
            UUID token;int sequence;AtmDepositFeedPayload first;
            final AtomicReference<AtmActionResultPayload> result=new AtomicReference<>();
            void at(int tick,Runnable action){h.runAfterDelay(tick,()->{atm.dispenserAction(player,"VIEW_PING");action.run();});}
            boolean action(String action){AtmSessionService.execute(player,new AtmActionRequestPayload(token,UUID.randomUUID(),++sequence,action,""),bank,result::set);return result.get().success();}
            boolean feed(int...plan){var request=new AtmDepositFeedPayload(token,UUID.randomUUID(),++sequence,Arrays.stream(plan).boxed().toList());AtmSessionService.executeFeed(player,request,bank,result::set);if(result.get().success())first=request;return result.get().success();}
            long carried(){return DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player));}
        }
        var run=new Run();
        run.at(62,()->{var auth=AtmPinService.authenticate(player,new AtmPinRequestPayload(pos,UUID.randomUUID(),cardId,"1234"),bank);run.token=auth.sessionToken();h.assertTrue(auth.accepted()&&run.action("OPEN_DEPOSIT"),"Open deposit with authenticated card");});
        run.at(89,()->{
            h.assertTrue(atm.phase()==DEPOSIT_FEEDING,"Feeder opens before accepting cash");
            h.assertFalse(run.feed(0,0,-1,0,0,0,0),"Reject negative counts");h.assertFalse(run.feed(0,0,99,0,0,0,0),"Reject bills not owned by player");
            h.assertTrue(run.carried()==5600&&account.getBalance().intValueExact()==100,"Rejected drops do not change money");
            h.assertTrue(run.feed(0,0,1,0,1,0,0),"Accept exact 20 and 5 selection");
            h.assertTrue(run.carried()==3100&&atm.deposit().cents()==2500,"Take cash into custody before animation, without credit");
        });
        run.at(92,()->{AtmSessionService.executeFeed(player,run.first,bank,run.result::set);h.assertFalse(run.result.get().success(),"Replayed drop cannot take money again");h.assertTrue(atm.phase()==DEPOSIT_INSERTING,"Confirmed drop starts insertion animation");});
        run.at(131,()->{h.assertTrue(atm.phase()==DEPOSIT_FEEDING&&net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.shutter(atm.phase(),0)==1,"Feeder stays open between drops");h.assertTrue(run.feed(0,0,0,0,1,0,0),"Add a second batch");});
        run.at(174,()->{h.assertTrue(run.action("DEPOSIT_COUNT")&&atm.phase()==DEPOSIT_COUNTING,"LCD Continue starts counting");h.assertTrue(account.getBalance().intValueExact()==100,"Counting does not credit account");});
        run.at(241,()->{h.assertTrue(atm.phase()==DEPOSIT_REVIEW&&atm.deposit().cents()==3000,"Review shows exact detected total");h.assertTrue(atm.dispenserAction(player,"DEPOSIT_BACK")&&atm.phase()==DEPOSIT_OPENING,"Back reopens the feeder without refunding or crediting");});
        run.at(270,()->{h.assertTrue(atm.phase()==DEPOSIT_FEEDING&&atm.deposit().cents()==3000&&run.carried()==2600&&account.getBalance().intValueExact()==100,"Returning to feeding preserves pending bills and balance");h.assertTrue(run.action("DEPOSIT_COUNT"),"Continue recounts after going back");});
        run.at(337,()->{h.assertTrue(atm.phase()==DEPOSIT_REVIEW&&run.action("DEPOSIT_CONFIRM"),"Explicit confirmation credits deposit");h.assertTrue(account.getBalance().intValueExact()==130&&run.carried()==2600,"Credit only inserted bills, leave coins and unselected notes");h.assertFalse(run.action("DEPOSIT_CONFIRM"),"Cannot confirm twice");});
        run.at(345,()->{h.assertTrue(atm.phase()==RECEIPT_CHOICE && atm.receiptDetails().contains("CASH DEPOSIT") && atm.receiptDetails().contains("$30.00"),"Confirmed deposit offers a detailed receipt");h.assertTrue(atm.dispenserAction(player,"RECEIPT_YES"),"Print deposit receipt");});
        run.at(388,()->{h.assertTrue(atm.phase()==RECEIPT_READY,"Deposit receipt physically printed");h.assertTrue(atm.dispenserAction(player,"PICK_RECEIPT"),"Collect deposit receipt");});
        run.at(414,()->{h.assertTrue(atm.phase()==IDLE&&atm.lcd().status()==net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.AUTHENTICATED,"Receipt returns to authenticated menu");h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.PAPER)==1,"One deposit receipt delivered");});
        run.at(420,()->h.assertTrue(run.action("OPEN_DEPOSIT"),"Begin another deposit"));
        run.at(447,()->h.assertTrue(run.feed(0,0,1,0,0,0,0),"Insert cash to cancel later"));
        run.at(490,()->h.assertTrue(run.action("DEPOSIT_COUNT"),"Count the cancellation batch"));
        run.at(558,()->{account.freeze("Return must remain possible");h.assertFalse(run.action("DEPOSIT_CONFIRM"),"Frozen account cannot receive a deposit");AtmSessionService.close(player);h.assertTrue(atm.dispenserAction(player,"RETURN_DEPOSIT"),"Own cash can be returned even after authentication expires");});
        run.at(616,()->{h.assertTrue(atm.phase()==DEPOSIT_RETURN_READY&&run.carried()==600,"Cancelled bills protrude before they are collected");h.assertTrue(atm.dispenserAction(player,"PICK_DEPOSIT"),"Physically collect returned bills");h.assertFalse(atm.dispenserAction(player,"PICK_DEPOSIT"),"Repeated pickup cannot duplicate refund");});
        run.at(671,()->{
            h.assertTrue(atm.phase()==IDLE&&run.carried()==2600&&account.getBalance().intValueExact()==130,"Cancelled cash is returned once, without account credit");
            h.assertTrue(account.getTransactions().values().stream().filter(tx->tx.getTransactionDescription().equals("ATM Cash Deposit")).count()==1,"One confirmed deposit records one transaction");
            AtmTerminalService.close(player);player.getInventory().clearContent();bank.getBankAccounts().remove(accountId);bank.getIssuedCreditCards().remove(cardId);level.removeBlock(pos,false);level.getServer().getPlayerList().remove(player);h.succeed();
        });
    }
    @GameTest(template="empty3x3x3")
    public static void exitingOrBreakingTheAtmReturnsPendingBillsWithoutCreditOrDuplication(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        var player=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"deposit-interrupt"),ClientInformation.createDefault());
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.6);
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ModItems.CREDIT_CARD.get()));
        DollarBills.giveCash(player,new int[]{0,0,2,0,0,0,0,0,0,0,0,0});
        for(int round=0;round<2;round++) {
            level.setBlock(pos,ModBlocks.ATM_MACHINE.get().defaultBlockState(),3);var atm=(AtmBlockEntity)level.getBlockEntity(pos);
            h.assertTrue(AtmTerminalService.claim(player,atm)&&atm.insertCard(player,InteractionHand.OFF_HAND,false),"Start interrupted deposit fixture");
            h.assertTrue(atm.deposit().open(player,UUID.randomUUID()),"Open pending deposit custody");atm.depositPhase(DEPOSIT_FEEDING);
            h.assertTrue(atm.deposit().feed(player,new int[]{0,0,1,0,0,0,0}),"Take one actual twenty into custody");
            h.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player))==2000,"Only uninserted cash remains carried");
            if(round==1)level.removeBlock(pos,false);
            AtmTerminalService.close(player);AtmTerminalService.close(player);
            h.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player))==4000,"Interruption returns cash exactly once");
            h.assertTrue(player.getOffhandItem().is(ModItems.CREDIT_CARD.get()),"Interruption also returns the original card");
            h.assertFalse(AtmDepositCustody.get(level.getServer()).refund(player),"Recovery entry is consumed once");
            level.removeBlock(pos,false);
        }
        player.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3")
    public static void depositedBillsCombineInventoryWalletsAndLooseCashWithoutUsingForeignWallets(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        var player=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"wallet-deposit"),ClientInformation.createDefault());
        var other=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"foreign-wallet-owner"),ClientInformation.createDefault());
        var wallet=new ItemStack(ModItems.WALLET.get());WalletData.ensureOwner(wallet,player);
        WalletData.addCashPlan(wallet,new int[]{0,0,3,0,0,0,0,0,4,0,0,0});player.getInventory().setItem(9,wallet);
        var second=new ItemStack(ModItems.WALLET.get());WalletData.ensureOwner(second,player);
        WalletData.addCashPlan(second,new int[]{0,0,1,0,0,0,0,0,0,0,0,0});player.getInventory().setItem(10,second);
        var foreign=new ItemStack(ModItems.WALLET.get());WalletData.ensureOwner(foreign,other);
        WalletData.addCashPlan(foreign,new int[]{0,0,99,0,0,0,0,0,0,0,0,0});player.getInventory().setItem(11,foreign);
        DollarBills.giveCash(player,new int[]{0,0,2,0,0,0,0,0,0,0,0,0});
        var data=new AtmDepositCustody();var location=GlobalPos.of(level.dimension(),pos);data.open(player,location,UUID.randomUUID());
        h.assertTrue(data.feed(player,location,new int[]{0,0,5,0,0,0,0}),"Bills in inventory wallets and loose cash must be depositable together");
        h.assertTrue(WalletData.getCashCounts(wallet)[2]==0&&WalletData.getCashCounts(second)[2]==0,"Selected bills are actually removed from owned wallets");
        h.assertTrue(WalletData.getCashCounts(wallet)[8]==4&&WalletData.getCashCounts(foreign)[2]==99,"Coins and another player's wallet stay untouched");
        h.assertTrue(DollarBills.getAvailableCashCounts(player)[2]==1,"Only the remaining selected bill is taken from loose cash");
        h.assertFalse(data.feed(player,location,new int[]{0,0,2,0,0,0,0}),"Cannot count the foreign wallet or spend inserted bills twice");
        h.assertTrue(data.bills(player.getUUID(),location)[2]==5,"Failed feed cannot change pending custody");
        data.refund(player);h.succeed();
    }
    @GameTest(template="empty3x3x3")
    public static void pendingCashSurvivesSaveLoadAndRefundsTheExactDenominationsOnce(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        var player=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"deposit-recovery"),ClientInformation.createDefault());
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.6);
        var wallet=new ItemStack(ModItems.WALLET.get());WalletData.ensureOwner(wallet,player);player.setItemInHand(InteractionHand.MAIN_HAND,wallet);
        WalletData.addCashPlan(wallet,new int[]{0,0,3,0,2,0,0,0,4,0,0,0});
        var data=new AtmDepositCustody();var location=GlobalPos.of(level.dimension(),pos);
        h.assertTrue(data.open(player,location,UUID.randomUUID())&&data.feed(player,location,new int[]{0,0,2,0,1,0,0}),"Take selected bills from owned wallet");
        var restored=AtmDepositCustody.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        h.assertTrue(restored.bills(player.getUUID(),location)[2]==2&&restored.bills(player.getUUID(),location)[4]==1,"Pending denominations survive restart");
        h.assertTrue(restored.refund(player),"Recover pending cash");h.assertFalse(restored.refund(player),"Recovery cannot duplicate cash");
        var bills=WalletData.getCashCounts(wallet);h.assertTrue(bills[2]==3&&bills[4]==2&&bills[8]==4,"Return exact notes and leave coins unchanged");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try {
            var packet=new AtmDepositFeedPayload(UUID.randomUUID(),UUID.randomUUID(),7,List.of(1,2,3,4,5,6,7));AtmDepositFeedPayload.STREAM_CODEC.encode(buffer,packet);h.assertTrue(packet.equals(AtmDepositFeedPayload.STREAM_CODEC.decode(buffer)),"Feed packet preserves sequence and denominations");
            buffer.clear();var stock=new AtmDepositStockPayload(pos,List.of(7,6,5,4,3,2,1));AtmDepositStockPayload.STREAM_CODEC.encode(buffer,stock);h.assertTrue(stock.equals(AtmDepositStockPayload.STREAM_CODEC.decode(buffer)),"Availability is delivered privately to the active tray");
        }finally{buffer.release();}
        h.succeed();
    }
}
