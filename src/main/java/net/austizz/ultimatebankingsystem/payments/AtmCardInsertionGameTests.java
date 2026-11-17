package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.AtmCardRequestPayload;
import net.austizz.ultimatebankingsystem.network.AtmCardResultPayload;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import io.netty.buffer.Unpooled;
import java.math.BigDecimal;
import java.util.UUID;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmCardInsertionGameTests {
    @GameTest(template = "empty3x3x3")
    public static void validatesHeldAndBorrowedCardsAndRejectsBlankBlockedExpiredAndRemoteCards(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "atm-insert-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        var bank = new CentralBank();
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Empty hands must be rejected");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Non-card items must be rejected");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREDIT_CARD.get()));
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Blank cards must be rejected");
        UUID accountId = UUID.randomUUID(), cardId = UUID.randomUUID();
        var account = new AccountHolder(player.getUUID(), BigDecimal.ZERO, AccountTypes.CheckingAccount, "1234", bank.getBankId(), accountId);
        bank.getBankAccounts().put(accountId, account);
        var record = new CompoundTag();
        record.putUUID("accountId", accountId); record.putUUID("bankId", bank.getBankId());
        record.putString("bankName", "Test Bank"); record.putString("cardNumber", "1234567890123456");
        record.putString("holderName", "Test Holder"); record.putString("cvc", "123");
        record.putLong("expiryEpochMillis", System.currentTimeMillis() + 86_400_000L);
        bank.getIssuedCreditCards().put(cardId, record);
        var card = new ItemStack(ModItems.CREDIT_CARD.get());
        var tag = new CompoundTag(); tag.putUUID(CreditCardService.TAG_CARD_ID, cardId);
        ItemStackDataCompat.setCustomData(card, tag);
        player.setItemInHand(InteractionHand.OFF_HAND, card);
        var result = AtmCardInsertionService.validate(player, pos, bank);
        helper.assertTrue(result.accepted() && result.hand() == InteractionHand.OFF_HAND, "Valid offhand card must work even with blank main-hand card");
        helper.assertTrue(player.getOffhandItem() == card && card.getCount() == 1 && result.card() != card,
                "Animation gets a snapshot without removing or duplicating inventory items");
        var machine = (net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(AtmTerminalService.claim(player, machine), "Reserve ATM camera before insertion");
        helper.assertTrue(AtmCardInsertionService.insert(player, pos, bank).accepted(), "Move the validated card into the machine");
        helper.assertTrue(player.getOffhandItem().isEmpty(), "Inserted card is no longer in inventory");
        helper.assertTrue(AtmPinService.verify(player, pos, cardId, "1234", bank).accepted(), "Correct account PIN must work");
        helper.assertFalse(AtmPinService.verify(player, pos, cardId, "0000", bank).accepted(), "Incorrect PIN must fail");
        for (String invalid : new String[]{"", "123", "12345", "abcd", "１２３４"}) {
            helper.assertFalse(AtmPinService.verify(player, pos, cardId, invalid, bank).accepted(), "Only four ASCII digits are valid");
        }
        helper.assertFalse(AtmPinService.verify(player, pos, UUID.randomUUID(), "1234", bank).accepted(), "Cannot substitute another card");
        bank.getBankAccounts().put(accountId, new AccountHolder(player.getUUID(), BigDecimal.ZERO,
                AccountTypes.CheckingAccount, "", bank.getBankId(), accountId));
        helper.assertFalse(AtmPinService.verify(player, pos, cardId, "1234", bank).accepted(), "Missing PIN cannot authenticate");
        bank.getBankAccounts().put(accountId, account);
        record.putBoolean("blocked", true);
        helper.assertFalse(AtmPinService.verify(player, pos, cardId, "1234", bank).accepted(), "Blocked inserted cards must fail PIN verification");
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Blocked card must be rejected");
        record.putBoolean("blocked", false);
        record.putLong("expiryEpochMillis", System.currentTimeMillis() - 1000);
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Expired card must be rejected");
        record.putLong("expiryEpochMillis", System.currentTimeMillis() + 86_400_000L);
        bank.getBankAccounts().put(accountId, new AccountHolder(UUID.randomUUID(), BigDecimal.ZERO,
                AccountTypes.CheckingAccount, "1234", bank.getBankId(), accountId));
        helper.assertTrue(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Another player's valid card can be inserted");
        helper.assertTrue(AtmPinService.verify(player, pos, cardId, "1234", bank).accepted(), "Borrowed card still requires its account's PIN");
        bank.getBankAccounts().put(accountId, account);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 3);
        level.setBlock(pos.north(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Wall must prevent remote reader activation");
        level.removeBlock(pos.north(), false);
        player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        helper.assertFalse(AtmCardInsertionService.validate(player, pos, bank).accepted(), "Out-of-range ATM must be rejected");
        AtmTerminalService.close(player);
        level.removeBlock(pos, false);
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void animationPacketsPreserveRequestIdentityAndValidatedCardSnapshot(GameTestHelper helper) {
        var pos = new BlockPos(3, 64, -2);
        var requestId = UUID.randomUUID();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var request = new AtmCardRequestPayload(pos, requestId);
            AtmCardRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertTrue(request.equals(AtmCardRequestPayload.STREAM_CODEC.decode(buffer)), "Request identity must round trip");
            buffer.clear();
            var view = new net.austizz.ultimatebankingsystem.network.AtmViewResultPayload(pos, true);
            net.austizz.ultimatebankingsystem.network.AtmViewResultPayload.STREAM_CODEC.encode(buffer, view);
            helper.assertTrue(view.equals(net.austizz.ultimatebankingsystem.network.AtmViewResultPayload.STREAM_CODEC.decode(buffer)), "Camera grants stay bound to the requested ATM");
            buffer.clear();
            var card = new ItemStack(ModItems.CREDIT_CARD.get());
            var tag = new CompoundTag(); tag.putUUID(CreditCardService.TAG_CARD_ID, UUID.randomUUID());
            ItemStackDataCompat.setCustomData(card, tag);
            var result = new AtmCardResultPayload(pos, requestId, true, InteractionHand.OFF_HAND, card, "Card accepted.", true);
            AtmCardResultPayload.STREAM_CODEC.encode(buffer, result);
            var decoded = AtmCardResultPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.atm().equals(pos) && decoded.requestId().equals(requestId)
                    && decoded.hand() == InteractionHand.OFF_HAND && decoded.accepted(), "Do not apply results to the wrong session or hand");
            helper.assertTrue(ItemStack.matches(card, decoded.card()), "Preserve card snapshot");
            helper.assertTrue(decoded.needsPin(), "Missing PIN setup flag must survive the packet");
            buffer.clear();
            var pinRequest = new net.austizz.ultimatebankingsystem.network.AtmPinRequestPayload(pos, requestId, UUID.randomUUID(), "0123", true, "0123");
            net.austizz.ultimatebankingsystem.network.AtmPinRequestPayload.STREAM_CODEC.encode(buffer, pinRequest);
            helper.assertTrue(pinRequest.equals(net.austizz.ultimatebankingsystem.network.AtmPinRequestPayload.STREAM_CODEC.decode(buffer)),
                    "PIN packet preserves leading zero and card/request identity");
            helper.assertFalse(pinRequest.toString().contains("0123"), "Do not log plaintext PINs");
            buffer.clear();
            var pinResult = new net.austizz.ultimatebankingsystem.network.AtmPinResultPayload(pos, requestId, true, "", UUID.randomUUID());
            net.austizz.ultimatebankingsystem.network.AtmPinResultPayload.STREAM_CODEC.encode(buffer, pinResult);
            helper.assertTrue(pinResult.equals(net.austizz.ultimatebankingsystem.network.AtmPinResultPayload.STREAM_CODEC.decode(buffer)),
                    "PIN response remains bound to the correct request and ATM");
            buffer.clear();
            var action = new net.austizz.ultimatebankingsystem.network.AtmActionRequestPayload(pinResult.sessionToken(), requestId, 1, "WITHDRAW", "20", 2);
            net.austizz.ultimatebankingsystem.network.AtmActionRequestPayload.STREAM_CODEC.encode(buffer, action);
            helper.assertTrue(action.equals(net.austizz.ultimatebankingsystem.network.AtmActionRequestPayload.STREAM_CODEC.decode(buffer)), "Menu action preserves authorization and sequence");
            buffer.clear();
            var actionResult = new net.austizz.ultimatebankingsystem.network.AtmActionResultPayload(requestId, true, "80.00", "Cash dispensed.");
            net.austizz.ultimatebankingsystem.network.AtmActionResultPayload.STREAM_CODEC.encode(buffer, actionResult);
            helper.assertTrue(actionResult.equals(net.austizz.ultimatebankingsystem.network.AtmActionResultPayload.STREAM_CODEC.decode(buffer)), "Menu result preserves balance and request identity");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
