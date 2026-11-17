package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.*;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmWithdrawalLimitsGameTests {
    @GameTest(template="empty3x3x3",batch="atm-personal-limits")
    public static void personalLimitsRespectBankCeilingsRequirePinAndPreserveDailyUsage(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var bank=BankManager.getCentralBank(server);
        var pos=h.absolutePos(new BlockPos(1,1,1));level.setBlock(pos,ModBlocks.ATM_MACHINE.get().defaultBlockState(),3);
        var atm=(AtmBlockEntity)level.getBlockEntity(pos);
        var player=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"atm-limits"),ClientInformation.createDefault());
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.6);
        var accountId=UUID.randomUUID();var cardId=UUID.randomUUID();
        var account=new AccountHolder(player.getUUID(),new BigDecimal("10000"),AccountTypes.CheckingAccount,"1234",bank.getBankId(),accountId);
        bank.getBankAccounts().put(accountId,account);
        var record=new CompoundTag();record.putUUID("accountId",accountId);record.putUUID("bankId",bank.getBankId());record.putUUID("ownerPlayerId",player.getUUID());
        record.putString("cardNumber","4321123456789012");record.putString("cvc","123");record.putLong("expiryEpochMillis",System.currentTimeMillis()+86400000);bank.getIssuedCreditCards().put(cardId,record);
        var card=new ItemStack(ModItems.CREDIT_CARD.get());var data=new CompoundTag();data.putUUID(CreditCardService.TAG_CARD_ID,cardId);ItemStackDataCompat.setCustomData(card,data);player.setItemInHand(InteractionHand.MAIN_HAND,card);
        var metadata=bank.getOrCreateBankMetadata(bank.getBankId());var original=metadata.copy();
        metadata.putString("limitSingle","300");metadata.putString("limitDailyPlayer","500");metadata.putString("limitDailyBank","10000");
        try {
            h.assertTrue(AtmTerminalService.claim(player,atm)&&AtmCardInsertionService.insert(player,pos,bank).accepted(),"Insert owner's card");
            var auth=AtmPinService.authenticate(player,new AtmPinRequestPayload(pos,UUID.randomUUID(),cardId,"1234"),bank);
            h.assertTrue(auth.accepted(),"Authenticate before editing limits");
            var policy=ModPayloads.buildAtmWithdrawalPolicyInput(bank,bank.getBank(account.getBankId()),account,BigDecimal.ZERO,server.overworld().getGameTime());
            var max=account.getConfiguredWithdrawalLimit().min(policy.bankSingleLimit());
            var maxDaily=account.getConfiguredDailyWithdrawalLimit().min(policy.bankPlayerDailyLimit()).min(policy.bankDailyLimit());
            var low=max.divide(new BigDecimal("3"),0,RoundingMode.DOWN).max(BigDecimal.ONE);
            var lowDaily=maxDaily.divide(new BigDecimal("2"),0,RoundingMode.DOWN).max(BigDecimal.ONE);
            class Run {
                int sequence;
                AtmActionResultPayload change(int mode,String amount,int duration,String pin) {
                    var result=new AtomicReference<AtmActionResultPayload>();
                    AtmSessionService.execute(player,new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),++sequence,"SET_LIMIT",amount,mode,new UUID(0,0),duration,pin),bank,result::set);
                    return result.get();
                }
            }
            var run=new Run();
            h.assertFalse(run.change(0,max.add(BigDecimal.ONE).toPlainString(),60,"1234").success(),"No PIN bypasses bank maximum");
            h.assertTrue(run.change(0,low.toPlainString(),60,"").success(),"Lower personal cap needs no PIN");
            var withdrawal=new AtomicReference<WithdrawResponsePayload>();
            ModPayloads.executeAtmWithdrawal(player,new WithdrawRequestPayload(accountId,low.add(BigDecimal.ONE).toPlainString()),withdrawal::set,atm,null,true);
            h.assertFalse(withdrawal.get().success(),"Withdrawal preview enforces permanent personal cap");
            ModPayloads.executeAtmWithdrawal(player,new WithdrawRequestPayload(accountId,low.add(BigDecimal.ONE).toPlainString()),withdrawal::set);
            h.assertFalse(withdrawal.get().success(),"Legacy withdrawal path enforces the same personal cap");
            h.assertTrue(run.change(0,low.add(BigDecimal.ONE).toPlainString(),60,"").message().equals("PIN_REQUIRED"),"Increasing a cap requires a fresh PIN");
            h.assertFalse(run.change(0,low.add(BigDecimal.ONE).toPlainString(),60,"0000").success(),"Wrong PIN cannot raise cap");
            h.assertTrue(account.getPersonalWithdrawalLimit().compareTo(low)==0,"Failed PIN never mutates limits");
            h.assertTrue(run.change(0,low.add(BigDecimal.ONE).toPlainString(),60,"1234").success(),"Correct PIN authorizes increase");
            h.assertTrue(run.change(1,lowDaily.toPlainString(),60,"").success(),"Daily cap can be lowered");
            account.registerDailyWithdrawal(new BigDecimal("20"));var used=account.getDailyWithdrawnAmount();
            h.assertTrue(account.getRemainingDailyWithdrawalLimit().compareTo(lowDaily.subtract(used).max(BigDecimal.ZERO))==0,"Daily remaining is calculated, not editable");
            h.assertFalse(run.change(2,max.toPlainString(),2,"1234").success(),"Forged duration is rejected");
            long before=System.currentTimeMillis();h.assertTrue(run.change(2,max.toPlainString(),15,"1234").success(),"Temporary increase accepts a selected duration after PIN");
            long expiry=account.getTemporaryWithdrawalLimitExpiresAtEpochMillis(server.overworld().getGameTime());
            h.assertTrue(expiry>=before+900000&&expiry<=System.currentTimeMillis()+900000,"Selected fifteen-minute duration is honored");
            var saved=account.save(new CompoundTag(),level.registryAccess());var loaded=AccountHolder.load(saved,level.registryAccess());
            h.assertTrue(loaded.getPersonalWithdrawalLimit().compareTo(low.add(BigDecimal.ONE))==0&&loaded.getEffectiveDailyWithdrawalLimit().compareTo(lowDaily)==0,"Personal caps survive save/load");
            h.assertTrue(loaded.getDailyWithdrawnAmount().compareTo(used)==0&&loaded.getTemporaryWithdrawalLimitExpiresAtEpochMillis(server.overworld().getGameTime())==expiry,"Usage and temporary expiry survive save/load");
            saved.remove("personalWithdrawalLimit");saved.remove("personalDailyWithdrawalLimit");saved.remove("temporaryWithdrawalLimit");
            var legacy=AccountHolder.load(saved,level.registryAccess());h.assertTrue(legacy.getPersonalWithdrawalLimit().compareTo(legacy.getConfiguredWithdrawalLimit())==0,"Old saves keep bank defaults");
            h.assertTrue(run.change(3,"",60,"").message().equals("PIN_REQUIRED"),"Reset cannot silently raise lower caps");
            h.assertTrue(run.change(3,"",60,"1234").success(),"PIN-confirmed reset restores defaults");
            h.assertTrue(account.getTemporaryWithdrawalLimitIfActive(server.overworld().getGameTime())==null&&account.getDailyWithdrawnAmount().compareTo(used)==0,"Reset clears temporary cap without resetting daily usage");
            h.assertTrue(account.getPersonalWithdrawalLimit().compareTo(account.getConfiguredWithdrawalLimit())==0&&account.getEffectiveDailyWithdrawalLimit().compareTo(account.getConfiguredDailyWithdrawalLimit())==0,"Reset restores both defaults");
            h.assertFalse(account.setTemporaryWithdrawalLimit(account.getConfiguredWithdrawalLimit().add(BigDecimal.ONE),server.overworld().getGameTime()),"Legacy temporary setter cannot exceed server ceiling");
            account.setPersonalWithdrawalLimit(low);account.setTemporaryWithdrawalLimitUntil(max,server.overworld().getGameTime(),System.currentTimeMillis()+60000);
            h.assertTrue(account.getEffectiveWithdrawalLimit(server.overworld().getGameTime()+1201).compareTo(low)==0,"Expired temporary cap falls back to permanent personal cap");
            h.assertFalse(run.change(1,maxDaily.add(BigDecimal.ONE).toPlainString(),60,"1234").success(),"Daily cap cannot exceed bank maximum");
            account.setTemporaryWithdrawalLimitUntil(low,server.overworld().getGameTime(),System.currentTimeMillis()+60000);
            h.assertTrue(run.change(0,max.toPlainString(),60,"").message().equals("PIN_REQUIRED"),"Ending a lower temporary cap cannot silently raise the active limit");
            h.assertTrue(run.change(0,low.toPlainString(),60,"").success()&&account.getTemporaryWithdrawalLimitIfActive(server.overworld().getGameTime())==null,"Permanent cap takes effect immediately and clears temporary cap");
            h.assertTrue(account.readOnlySnapshot(server.overworld().getGameTime()).dailyUsed().compareTo(used)==0,"Read-only snapshot preserves today's usage");
            var packet=new AtmActionRequestPayload(auth.sessionToken(),UUID.randomUUID(),1,"SET_LIMIT","100",2,new UUID(0,0),1440,"1234");
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try {AtmActionRequestPayload.STREAM_CODEC.encode(buf,packet);h.assertTrue(packet.equals(AtmActionRequestPayload.STREAM_CODEC.decode(buf)),"Duration and private PIN roundtrip in bounded packet");}finally{buf.release();}
            h.assertFalse(packet.toString().contains("1234"),"Request logging redacts the PIN");
        } finally {
            AtmTerminalService.close(player);player.getInventory().clearContent();bank.getBankAccounts().remove(accountId);bank.getIssuedCreditCards().remove(cardId);level.removeBlock(pos,false);
            for(String key:new String[]{"limitSingle","limitDailyPlayer","limitDailyBank"}) {metadata.remove(key);if(original.contains(key)) metadata.put(key,original.get(key).copy());}
        }
        h.succeed();
    }
}
