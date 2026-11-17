package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmTerminalGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos pos) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6); return player;
    }
    private static ItemStack card() {
        var card = new ItemStack(ModItems.CREDIT_CARD.get());
        card.set(DataComponents.CUSTOM_NAME, Component.literal("My original card"));
        var tag = new CompoundTag(); tag.putUUID(CreditCardService.TAG_CARD_ID, UUID.randomUUID());
        tag.putString("private_marker", "preserve-this-exactly"); tag.putBoolean("ubs_cc_back_visible", true);
        ItemStackDataCompat.setCustomData(card, tag); return card;
    }
    @GameTest(template = "empty3x3x3", batch = "atm-terminal-motion", timeoutTicks = 320)
    public static void normalAndWithdrawalReturnsKeepCustodyUntilTheHandPickupFinishes(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer mock;
        try { mock = helper.makeMockServerPlayerInLevel(); }
        catch (UnsupportedOperationException exception) {
            mock = level.getServer().getPlayerList().getPlayerByName("test-mock-player");
            if (mock == null) throw exception;
        }
        var owner = mock;
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(owner.connection.getConnection());
        owner.setNoGravity(true); owner.setGameMode(net.minecraft.world.level.GameType.CREATIVE); owner.getAbilities().flying = true;
        owner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        owner.getInventory().clearContent(); var expected = card();
        owner.setItemInHand(InteractionHand.MAIN_HAND, expected.copy());
        level.setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var atm = (AtmBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(AtmTerminalService.claim(owner, atm) && atm.insertCard(owner, InteractionHand.MAIN_HAND, false), "Start physical insertion");
        helper.runAfterDelay(62, () -> {
            helper.assertTrue(atm.cardStage().equals("INSERTED") && owner.getMainHandItem().isEmpty(), "Motor intake ends with card still in custody");
            AtmSessionService.open(owner, pos, ItemStackDataCompat.getCustomData(expected).getUUID(CreditCardService.TAG_CARD_ID), UUID.randomUUID());
            helper.assertFalse(AtmSessionService.expired(owner), "Fresh authentication is not expired");
            helper.assertTrue(AtmSessionService.expired(owner, System.currentTimeMillis()+300_001), "Authentication expires after five minutes");
            atm.expireSession(owner);
            helper.assertTrue(atm.sessionExpired() && atm.cardStage().equals("EJECTING") && atm.lcd().primary(System.nanoTime()).contains("Session expired"), "Expiry announces and starts one guided card return");
            atm.expireSession(owner);
            helper.assertTrue(atm.cardAge(0)==0, "Repeated expiry does not restart the motor");
            helper.runAfterDelay(28, () -> {
                helper.assertTrue(atm.cardStage().equals("REMOVING") && owner.getMainHandItem().isEmpty(), "Card stays in custody while the hand reaches it");
                helper.runAfterDelay(28, () -> {
                    helper.assertTrue(atm.cardStage().equals("EMPTY") && ItemStack.matches(expected, owner.getMainHandItem()), "Normal pickup returns the exact item once");
                    helper.assertTrue(atm.dispenserAction(owner, "VIEW_PING") && atm.insertCard(owner, InteractionHand.MAIN_HAND, false), "Reinsert same card for withdrawal");
                    helper.runAfterDelay(62, () -> {
                        helper.assertTrue(atm.reserveCash(owner, net.austizz.ultimatebankingsystem.item.DollarBills.buildWithdrawPlan(1), 1), "Withdrawal offers inserted card first");
                        helper.runAfterDelay(28, () -> {
                            helper.assertTrue(atm.cardStage().equals("OFFERED") && !atm.visualCard().isEmpty() && owner.getMainHandItem().isEmpty(), "Card protrudes globally while remaining in machine custody");
                            helper.assertTrue(atm.dispenserAction(owner, "CARD_BEGIN"), "Owner takes offered card");
                            atm.dispenserAction(owner, "CARD_TAKEN");
                            helper.assertTrue(owner.getMainHandItem().isEmpty(), "Early completion cannot return or duplicate the item");
                            helper.runAfterDelay(28, () -> {
                                helper.assertTrue(ItemStack.matches(expected, owner.getMainHandItem()) && atm.cardStage().equals("EMPTY"), "Withdrawal pickup returns the original card before cash feeds");
                                helper.assertTrue(atm.phase() == net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.WAIT_CASH, "Cash starts only after the physical pickup");
                                AtmTerminalService.close(owner); atm.dropPendingCash(); level.removeBlock(pos, false);
                                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                                owner.getInventory().clearContent(); level.getServer().getPlayerList().remove(owner);
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        });
    }
    @GameTest(template = "empty3x3x3")
    public static void exclusiveViewHoldsTheRealCardAndReturnsItsExactComponentsOnce(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var atm = (AtmBlockEntity) level.getBlockEntity(pos);
        var owner = player(helper, "atm-owner", pos); var other = player(helper, "atm-other", pos);
        var bank = new CentralBank(); var accountId = UUID.randomUUID(); var original = card();
        var cardId = ItemStackDataCompat.getCustomData(original).getUUID(CreditCardService.TAG_CARD_ID);
        bank.getBankAccounts().put(accountId, new AccountHolder(owner.getUUID(), java.math.BigDecimal.TEN,
                AccountTypes.CheckingAccount, "1234", bank.getBankId(), accountId));
        var issued = new CompoundTag(); issued.putUUID("accountId", accountId); issued.putUUID("bankId", bank.getBankId());
        issued.putString("cardNumber", "4567123456789012"); issued.putString("cvc", "123");
        issued.putString("bankName", "Test Bank"); issued.putString("holderName", "Owner");
        issued.putLong("expiryEpochMillis", System.currentTimeMillis() + 86_400_000);
        bank.getIssuedCreditCards().put(cardId, issued);
        owner.setItemInHand(InteractionHand.OFF_HAND, original);
        try {
            helper.assertTrue(AtmTerminalService.claim(owner, atm), "First player reserves ATM before inserting a card");
            helper.assertFalse(AtmTerminalService.claim(other, atm), "Another camera cannot enter occupied ATM");
            var secondPos = pos.south(); level.setBlock(secondPos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
            helper.assertFalse(AtmTerminalService.claim(owner, (AtmBlockEntity) level.getBlockEntity(secondPos)), "One player cannot reserve several ATMs");
            level.removeBlock(secondPos, false);
            helper.assertFalse(atm.dispenserAction(other, "KEY_16"), "Another player cannot press keys");
            helper.assertFalse(AtmCardInsertionService.insert(other, pos, bank).accepted(), "Another player cannot insert a card");
            var accepted = AtmCardInsertionService.insert(owner, pos, bank);
            helper.assertTrue(accepted.accepted(), "Owner inserts a valid card");
            var expected = accepted.card().copy();
            helper.assertTrue(owner.getOffhandItem().isEmpty(), "The inventory card is taken by the ATM");
            helper.assertTrue(ItemStack.matches(expected, atm.heldCard(owner)), "Machine holds the exact original components");
            helper.assertTrue(atm.heldCard(other).isEmpty(), "Another player cannot read the held card");
            helper.assertFalse(AtmCardInsertionService.insert(owner, pos, bank).accepted(), "Duplicate insertion cannot clone the card");
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
            helper.assertTrue(AtmPinService.verify(owner, pos, cardId, "1234", bank).accepted(), "PIN uses inserted card even when hand contains another item");
            helper.assertFalse(AtmPinService.verify(other, pos, cardId, "1234", bank).accepted(), "Credentials do not bypass occupancy and ownership");
            var update = atm.getUpdateTag(level.registryAccess());
            helper.assertFalse(update.toString().contains("preserve-this-exactly") || update.toString().contains(cardId.toString()), "Public card visual contains no private original data or issued-card identifier");
            var observer = new AtmBlockEntity(pos, atm.getBlockState()); observer.setLevel(level);
            observer.loadWithComponents(update, level.registryAccess());
            helper.assertTrue(observer.cardStage().equals("INSERTING") && !observer.visualCard().isEmpty()
                    && observer.sharedLcd().status() == AtmLcdState.Status.INSERTING, "A late observer receives current LCD and animated card state");
            AtmTerminalService.close(other);
            helper.assertTrue(atm.isViewer(owner), "Closing another player's camera cannot release the owner's lock");
            AtmTerminalService.close(owner); AtmTerminalService.close(owner);
            int count = 0;
            for (var stack : owner.getInventory().items) if (stack.is(ModItems.CREDIT_CARD.get())) {
                helper.assertTrue(ItemStack.matches(expected, stack), "Returned card retains exact metadata and custom name"); count += stack.getCount();
            }
            helper.assertTrue(count == 1 && owner.getOffhandItem().is(Items.STICK), "Return once without overwriting the occupied original slot");
            helper.assertTrue(atm.visualCard().isEmpty() && AtmTerminalService.claim(other, atm), "New user can enter after original owner leaves");
        } finally { AtmTerminalService.close(owner); AtmTerminalService.close(other); level.removeBlock(pos, false); }
        helper.succeed();
    }
    @GameTest(template = "empty3x3x3")
    public static void custodySurvivesSaveLoadAndFullInventoriesWithoutDiscardingTheCard(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1)); var player = player(helper, "atm-recovery", pos);
        var expected = card(); player.setItemInHand(InteractionHand.MAIN_HAND, expected.copy());
        var data = new AtmCardCustody(); var location = GlobalPos.of(helper.getLevel().dimension(), pos);
        helper.assertTrue(data.insert(player, location, player.getInventory().selected), "Take original into custody");
        var restored = AtmCardCustody.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.matches(expected, restored.card(player.getUUID(), location)), "Save/load retains every item component");
        for (int i = 0; i <= 40; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        helper.assertTrue(restored.giveBack(player), "Full inventory still returns card");
        helper.assertFalse(restored.giveBack(player), "Recovery cannot return a second copy");
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(2));
        var cards = drops.stream().filter(e -> e.getItem().is(ModItems.CREDIT_CARD.get())).toList();
        helper.assertTrue(cards.size() == 1 && ItemStack.matches(expected, cards.getFirst().getItem()), "Full inventory drops exactly the original card for its owner");
        cards.forEach(net.minecraft.world.entity.item.ItemEntity::discard); helper.succeed();
    }
    @GameTest(template = "empty3x3x3")
    public static void anOfflineOwnerRecoversTheirCardEvenAfterTheAtmIsGone(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var atm = (AtmBlockEntity) level.getBlockEntity(pos); var owner = player(helper, "atm-offline", pos);
        var expected = card(); owner.setItemInHand(InteractionHand.MAIN_HAND, expected.copy());
        helper.assertTrue(AtmTerminalService.claim(owner, atm) && atm.insertCard(owner, InteractionHand.MAIN_HAND, false), "Card starts in ATM custody");
        // This unregistered test player is offline to the server's player list.
        AtmBlockEntity.tick(level, pos, atm.getBlockState(), atm);
        level.removeBlock(pos, false);
        helper.assertTrue(owner.getMainHandItem().isEmpty(), "Offline return remains safely stored outside the unloaded ATM");
        AtmTerminalService.login(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(owner));
        helper.assertTrue(ItemStack.matches(expected, owner.getMainHandItem()), "Login recovers the exact original card without needing the ATM chunk");
        AtmTerminalService.login(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(owner));
        helper.assertTrue(owner.getMainHandItem().getCount() == 1, "Repeated login cannot duplicate it");
        AtmTerminalService.close(owner); helper.succeed();
    }
}
