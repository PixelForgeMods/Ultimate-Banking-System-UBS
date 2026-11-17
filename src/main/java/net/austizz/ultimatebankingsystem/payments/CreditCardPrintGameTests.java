package net.austizz.ultimatebankingsystem.payments;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.UUID;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class CreditCardPrintGameTests {
    @GameTest(template = "empty3x3x3")
    public static void shiftUseFlipsOnlyTheHeldCardAndPreservesPrintedData(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var item = net.austizz.ultimatebankingsystem.item.ModItems.CREDIT_CARD.get();
        var card = new net.minecraft.world.item.ItemStack(item);
        var tag = new CompoundTag();
        tag.putString(CreditCardService.TAG_CARD_NUMBER, "1234567890123456");
        tag.putUUID(CreditCardService.TAG_ACCOUNT_ID, UUID.randomUUID());
        net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.setCustomData(card, tag);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, card);
        var result = item.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == net.minecraft.world.InteractionResult.PASS, "Ordinary use must not flip");
        player.setShiftKeyDown(true);
        item.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        var flipped = net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(card);
        helper.assertTrue(flipped.getBoolean(net.austizz.ultimatebankingsystem.item.CreditCardItem.TAG_BACK_VISIBLE), "Shift-use shows back");
        flipped.remove(net.austizz.ultimatebankingsystem.item.CreditCardItem.TAG_BACK_VISIBLE);
        helper.assertTrue(flipped.equals(tag), "Flipping must not change printed details or account links");
        item.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(card)
                .getBoolean(net.austizz.ultimatebankingsystem.item.CreditCardItem.TAG_BACK_VISIBLE), "Cooldown prevents duplicate toggles");
        player.getCooldowns().removeCooldown(item);
        item.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertFalse(net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(card)
                .getBoolean(net.austizz.ultimatebankingsystem.item.CreditCardItem.TAG_BACK_VISIBLE), "Next press restores front");
        var offhand = new net.minecraft.world.item.ItemStack(item);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, offhand);
        player.getCooldowns().removeCooldown(item);
        item.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.OFF_HAND);
        helper.assertTrue(net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(offhand)
                .getBoolean(net.austizz.ultimatebankingsystem.item.CreditCardItem.TAG_BACK_VISIBLE), "Offhand card also flips independently");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void sneakingCardAllowsBlocksFirstRefusalButRespectsDeniedInteraction(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.austizz.ultimatebankingsystem.item.ModItems.CREDIT_CARD.get()));
        player.setShiftKeyDown(true);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.NORTH, pos, false);
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
        net.austizz.ultimatebankingsystem.item.CreditCardItem.allowBlockInteraction(event);
        helper.assertTrue(event.getUseBlock().isTrue(), "Interactive blocks must receive sneak-right-click before card flipping");
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        net.austizz.ultimatebankingsystem.item.CreditCardItem.allowBlockInteraction(event);
        helper.assertTrue(event.getUseBlock().isFalse(), "Do not override another mod's explicit interaction denial");
        helper.assertFalse(net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(player.getMainHandItem())
                .getBoolean(net.austizz.ultimatebankingsystem.item.CreditCardItem.TAG_BACK_VISIBLE), "Routing a block click must not itself flip");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void existingCardsAcquireTheirRecordedHolderWithoutChangingLinkage(GameTestHelper helper) {
        var level = helper.getLevel();
        var bank = net.austizz.ultimatebankingsystem.bank.handler.BankManager.getCentralBank(level.getServer());
        var id = UUID.randomUUID();
        var record = new CompoundTag();
        record.putString("holderName", "Original Cardholder");
        bank.getIssuedCreditCards().put(id, record);
        try {
            var stack = new net.minecraft.world.item.ItemStack(net.austizz.ultimatebankingsystem.item.ModItems.CREDIT_CARD.get());
            var tag = new CompoundTag();
            UUID account = UUID.randomUUID();
            tag.putUUID(CreditCardService.TAG_CARD_ID, id);
            tag.putUUID(CreditCardService.TAG_ACCOUNT_ID, account);
            tag.putString(CreditCardService.TAG_CARD_NUMBER, "1234567890123456");
            net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.setCustomData(stack, tag);
            var carrier = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
            carrier.tickCount = 20;
            stack.getItem().inventoryTick(stack, level, carrier, 0, true);
            var updated = net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(stack);
            helper.assertTrue(updated.getString(CreditCardService.TAG_HOLDER_NAME).equals("Original Cardholder"), "Use the issued holder, not the current carrier");
            helper.assertTrue(updated.getUUID(CreditCardService.TAG_ACCOUNT_ID).equals(account)
                    && updated.getUUID(CreditCardService.TAG_CARD_ID).equals(id), "Keep existing card and account links intact");
            helper.assertTrue(updated.getString(CreditCardService.TAG_CARD_NUMBER).equals("1234567890123456"), "Keep the issued card number");
        } finally {
            bank.getIssuedCreditCards().remove(id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void printsOnlyPhysicalCardDetailsFromEachStack(GameTestHelper helper) {
        var tag = new CompoundTag();
        tag.putString(CreditCardService.TAG_BANK_NAME, "Example Bank");
        tag.putString(CreditCardService.TAG_HOLDER_NAME, "Alex Example");
        tag.putString(CreditCardService.TAG_CARD_NUMBER, "1234567890123456");
        tag.putString(CreditCardService.TAG_CVC, "007");
        tag.putLong(CreditCardService.TAG_EXPIRY_AT, Instant.parse("2028-05-15T12:00:00Z").toEpochMilli());
        String account = UUID.randomUUID().toString();
        tag.putString(CreditCardService.TAG_ACCOUNT_ID, account);
        tag.putString("balance", "987654321");
        var original = tag.copy();
        var print = CreditCardPrint.fromTag(tag);
        helper.assertTrue(print.number().equals("1234 5678 9012 3456"), "Card number must be grouped and unmasked");
        helper.assertTrue(print.bank().equals("Example Bank") && print.holder().equals("ALEX EXAMPLE"), "Use card bank and holder");
        helper.assertTrue(print.expiry().equals("05/28") && print.cvc().equals("007"), "Preserve expiry and leading CVC zeros");
        helper.assertFalse(print.toString().contains(account) || print.toString().contains("987654321"), "Never print account data");
        helper.assertTrue(original.equals(tag), "Rendering must not modify the item data");
        tag.putString(CreditCardService.TAG_CARD_NUMBER, "9999888877776666");
        helper.assertTrue(CreditCardPrint.fromTag(tag).number().equals("9999 8888 7777 6666"), "Do not reuse another stack's text");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void blankCardsStayBlankAndLongNamesFitSafely(GameTestHelper helper) {
        var tag = new CompoundTag();
        tag.putString(CreditCardService.TAG_BANK_NAME, "Unissued Bank");
        var blank = CreditCardPrint.fromTag(tag);
        helper.assertTrue(blank.bank().isEmpty() && blank.number().isEmpty() && blank.holder().isEmpty()
                && blank.expiry().isEmpty() && blank.cvc().isEmpty(), "An unissued card must remain blank");
        tag.putString(CreditCardService.TAG_CARD_NUMBER, "1234-5678-9012-3456");
        tag.putString(CreditCardService.TAG_HOLDER_NAME, "§c" + "A".repeat(200) + "\n");
        var print = CreditCardPrint.fromTag(tag);
        helper.assertTrue(print.holder().length() == 64 && !print.holder().contains("§"), "Bound printed text and remove control codes");
        helper.assertTrue(print.expiry().equals("--/--"), "Never invent an expiry date");
        helper.succeed();
    }
}
