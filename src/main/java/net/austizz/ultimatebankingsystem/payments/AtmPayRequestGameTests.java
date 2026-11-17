package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.*;
import net.austizz.ultimatebankingsystem.payrequest.PayRequestManager;
import net.austizz.ultimatebankingsystem.atm.AtmPayRequestPage;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmPayRequestGameTests {
    @GameTest(template="empty3x3x3",batch="atm-pay-requests")
    public static void createsPaysAndDeclinesThroughAuthenticatedAtmWithoutReplay(GameTestHelper h) {
        var level=h.getLevel(); var server=level.getServer();
        ServerPlayer mock;
        try { mock=h.makeMockServerPlayerInLevel(); }
        catch(UnsupportedOperationException failure) { mock=server.getPlayerList().getPlayerByName("test-mock-player"); if(mock==null) throw failure; }
        var payer=mock; net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(payer.connection.getConnection());
        var creator=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"atm-requester"),ClientInformation.createDefault());
        var pos=h.absolutePos(new BlockPos(1,1,1)); level.setBlock(pos,ModBlocks.ATM_MACHINE.get().defaultBlockState(),3);
        var atm=(AtmBlockEntity)level.getBlockEntity(pos); var bank=BankManager.getCentralBank(server);
        var creatorAccount=UUID.randomUUID(); var payerAccount=UUID.randomUUID();
        var receiving=new AccountHolder(creator.getUUID(),new BigDecimal("100.00"),AccountTypes.CheckingAccount,"1234",bank.getBankId(),creatorAccount);
        var paying=new AccountHolder(payer.getUUID(),new BigDecimal("100.00"),AccountTypes.CheckingAccount,"1234",bank.getBankId(),payerAccount);
        bank.getBankAccounts().put(creatorAccount,receiving); bank.getBankAccounts().put(payerAccount,paying);
        var creatorCard=issue(creator,creatorAccount); var payerCard=issue(payer,payerAccount);
        var creatorCardId=id(creatorCard);var payerCardId=id(payerCard);
        var result=new AtomicReference<AtmActionResultPayload>();
        try {
            creator.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.6); payer.setPos(creator.getX(),creator.getY(),creator.getZ());
            creator.getInventory().clearContent(); payer.getInventory().clearContent();
            creator.setItemInHand(InteractionHand.MAIN_HAND,creatorCard);
            h.assertTrue(AtmTerminalService.claim(creator,atm)&&AtmCardInsertionService.insert(creator,pos,bank).accepted(),"Requester inserts their own card");
            var token=AtmPinService.authenticate(creator,new AtmPinRequestPayload(pos,UUID.randomUUID(),creatorCardId,"1234"),bank).sessionToken();
            AtmSessionService.execute(creator,new AtmActionRequestPayload(token,UUID.randomUUID(),1,"PAY_PLAYERS",""),bank,result::set);
            h.assertTrue(result.get().success()&&AtmPayRequestPage.decode(result.get().message()).entries().stream().anyMatch(p->p.id().equals(payer.getUUID())),"Player picker uses online server players");
            var create=new AtmActionRequestPayload(token,UUID.randomUUID(),2,"PAY_SEND","12.34",0,payer.getUUID());
            AtmSessionService.execute(creator,create,bank,result::set); h.assertTrue(result.get().success(),"Create request through the ATM");
            var request=PayRequestManager.getPendingForPayer(payer.getUUID()).stream().filter(p->p.getRequesterUUID().equals(creator.getUUID())).findFirst().orElseThrow();
            h.assertTrue(request.getReceiverAccountUUID().equals(creatorAccount)&&receiving.getBalance().compareTo(new BigDecimal("100"))==0,"Creation targets inserted account but moves no funds");
            AtmSessionService.execute(creator,create,bank,result::set); h.assertFalse(result.get().success(),"Creation packet replay is denied");
            AtmSessionService.execute(creator,new AtmActionRequestPayload(token,UUID.randomUUID(),3,"PAY_SEND","1.001",0,payer.getUUID()),bank,result::set);
            h.assertFalse(result.get().success(),"ATM rejects fractions smaller than a cent");
            AtmTerminalService.close(creator);
            payer.setItemInHand(InteractionHand.MAIN_HAND,payerCard);
            h.assertTrue(AtmTerminalService.claim(payer,atm)&&AtmCardInsertionService.insert(payer,pos,bank).accepted(),"Payer inserts their own card");
            token=AtmPinService.authenticate(payer,new AtmPinRequestPayload(pos,UUID.randomUUID(),payerCardId,"1234"),bank).sessionToken();
            AtmSessionService.execute(payer,new AtmActionRequestPayload(token,UUID.randomUUID(),1,"PAY_LIST",""),bank,result::set);
            h.assertTrue(result.get().success()&&AtmPayRequestPage.decode(result.get().message()).entries().stream().anyMatch(p->p.id().equals(request.getRequestId())),"Inbox contains existing pending request");
            var wrong=PayRequestManager.createRequest(creator.getUUID(),UUID.randomUUID(),creatorAccount,BigDecimal.ONE);
            AtmSessionService.execute(payer,new AtmActionRequestPayload(token,UUID.randomUUID(),2,"PAY_ACCEPT","",0,wrong.getRequestId()),bank,result::set);
            h.assertFalse(result.get().success(),"Cannot pay another player's request");
            paying.freeze("test");
            AtmSessionService.execute(payer,new AtmActionRequestPayload(token,UUID.randomUUID(),3,"PAY_ACCEPT","",0,request.getRequestId()),bank,result::set);
            h.assertFalse(result.get().success()||request.getStatus()!=PayRequestManager.Status.PENDING,"Failed payment leaves request pending"); paying.unfreeze();
            var accept=new AtmActionRequestPayload(token,UUID.randomUUID(),4,"PAY_ACCEPT","",0,request.getRequestId());
            AtmSessionService.execute(payer,accept,bank,result::set); h.assertTrue(result.get().success(),"Confirmed payment succeeds");
            h.assertTrue(paying.getBalance().compareTo(new BigDecimal("87.66"))==0&&receiving.getBalance().compareTo(new BigDecimal("112.34"))==0,"Exact cents move from inserted card account once");
            AtmSessionService.execute(payer,accept,bank,result::set); h.assertFalse(result.get().success(),"Repeated packet cannot pay twice");
            AtmSessionService.execute(payer,new AtmActionRequestPayload(token,UUID.randomUUID(),5,"PAY_ACCEPT","",0,request.getRequestId()),bank,result::set); h.assertFalse(result.get().success(),"New sequence cannot pay accepted request again");
            var declined=PayRequestManager.createRequest(creator.getUUID(),payer.getUUID(),creatorAccount,new BigDecimal("2.50"));
            AtmSessionService.execute(payer,new AtmActionRequestPayload(token,UUID.randomUUID(),6,"PAY_DECLINE","",0,declined.getRequestId()),bank,result::set);
            h.assertTrue(result.get().success()&&declined.getStatus()==PayRequestManager.Status.DECLINED&&paying.getBalance().compareTo(new BigDecimal("87.66"))==0,"Decline moves no money");
            var expired=PayRequestManager.createRequest(creator.getUUID(),payer.getUUID(),creatorAccount,BigDecimal.ONE);
            try {
                var created=expired.getClass().getDeclaredField("createdAtMillis"); created.setAccessible(true); created.setLong(expired,System.currentTimeMillis()-300001);
            } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
            AtmSessionService.execute(payer,new AtmActionRequestPayload(token,UUID.randomUUID(),7,"PAY_ACCEPT","",0,expired.getRequestId()),bank,result::set);
            h.assertFalse(result.get().success(),"Stale LCD selection cannot pay an expired request");
            h.assertTrue(paying.getBalance().compareTo(new BigDecimal("87.66"))==0,"Expired request leaves balance unchanged");
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try { AtmActionRequestPayload.STREAM_CODEC.encode(buf,accept); h.assertTrue(accept.equals(AtmActionRequestPayload.STREAM_CODEC.decode(buf)),"Target and amount roundtrip in bounded ATM packet"); } finally {buf.release();}
        } finally {
            AtmTerminalService.close(creator); AtmTerminalService.close(payer); creator.getInventory().clearContent();payer.getInventory().clearContent();
            bank.getBankAccounts().remove(creatorAccount); bank.getBankAccounts().remove(payerAccount); bank.getIssuedCreditCards().remove(creatorCardId); bank.getIssuedCreditCards().remove(payerCardId);
            for(var request:PayRequestManager.getPendingForPayer(payer.getUUID())) if(request.getRequesterUUID().equals(creator.getUUID())) PayRequestManager.markDeclined(request.getRequestId());
            level.removeBlock(pos,false);server.getPlayerList().remove(payer);
        }
        h.succeed();
    }
    private static UUID id(ItemStack card) {return ItemStackDataCompat.getCustomData(card).getUUID(CreditCardService.TAG_CARD_ID);}
    private static ItemStack issue(ServerPlayer player,UUID account) {
        var bank=BankManager.getCentralBank(player.server); var id=UUID.randomUUID();var record=new CompoundTag();
        record.putUUID("accountId",account);record.putUUID("bankId",bank.getBankId());record.putUUID("ownerPlayerId",player.getUUID());
        record.putString("cardNumber","4321123456789012");record.putString("cvc","123");record.putLong("expiryEpochMillis",System.currentTimeMillis()+86400000);
        bank.getIssuedCreditCards().put(id,record);var card=new ItemStack(ModItems.CREDIT_CARD.get());var tag=new CompoundTag();tag.putUUID(CreditCardService.TAG_CARD_ID,id);ItemStackDataCompat.setCustomData(card,tag);return card;
    }
}
