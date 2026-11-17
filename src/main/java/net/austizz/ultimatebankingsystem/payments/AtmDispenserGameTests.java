package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmDispenserGameTests {
    @GameTest(template = "empty3x3x3", timeoutTicks = 450)
    public static void abandonedCashCanBeCollectedOnceByAnotherPlayerThenReceiptPrints(GameTestHelper helper) {
        ServerPlayer passerby;
        try { passerby = helper.makeMockServerPlayerInLevel(); }
        catch (UnsupportedOperationException exception) {
            passerby = helper.getLevel().getServer().getPlayerList().getPlayerByName("test-mock-player");
            if (passerby == null) throw exception;
        }
        var player = passerby;
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(player.connection.getConnection());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setNoGravity(true);
        player.getAbilities().flying = true;
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        player.getInventory().clearContent();
        var absentOwner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "absent-atm-owner"), net.minecraft.server.level.ClientInformation.createDefault());
        var atm = (AtmBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(atm.reserveCash(absentOwner, DollarBills.buildWithdrawPlan(27), 27), "Reserve exact bills in ATM");
        helper.assertFalse(atm.reserveCash(absentOwner, DollarBills.buildWithdrawPlan(20), 20), "Second withdrawal cannot overwrite pending cash");
        helper.assertFalse(atm.dispenserAction(player, "PICK_CASH"), "Cash cannot be collected before dispensing");
        var probePos = pos.south();
        helper.getLevel().setBlock(probePos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var probe = (AtmBlockEntity) helper.getLevel().getBlockEntity(probePos);
        helper.assertTrue(AtmTerminalService.claim(player, probe), "Reserve the view while returning its card");
        helper.assertTrue(probe.reserveCash(player, DollarBills.buildWithdrawPlan(1), 1), "Card timing fixture reserves cash");
        helper.assertTrue(probe.dispenserAction(player, "CARD_BEGIN"), "Owner begins card removal");
        helper.assertTrue(probe.dispenserAction(player, "CARD_TAKEN"), "Early real-time completion is queued during slow server ticks");
        helper.assertTrue(probe.phase() == Phase.CARD_REMOVING, "Completion cannot skip minimum card-return time");
        helper.runAfterDelay(5, () -> helper.assertTrue(probe.phase() == Phase.CARD_REMOVING, "Do not dispense before card-return timing completes"));
        helper.runAfterDelay(27, () -> {
            helper.assertTrue(probe.phase() == Phase.WAIT_CASH, "Queued card completion advances without requiring another client message");
            probe.dropPendingCash();
            probe.dropPendingCash();
            helper.getLevel().removeBlock(probePos, false);
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(probePos).inflate(.5));
            long cents = drops.stream().mapToLong(e -> DollarBills.physicalTenderCents(e.getItem().getItem()) * e.getItem().getCount()).sum();
            helper.assertTrue(cents == 100, "Breaking or repeated drop handling releases pending money exactly once");
            drops.forEach(net.minecraft.world.entity.item.ItemEntity::discard);
        });
        helper.runAfterDelay(net.austizz.ultimatebankingsystem.atm.AtmSoundTiming.cashCycleTicks(net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.billCount(DollarBills.buildWithdrawPlan(27))) + 15, () -> {
            helper.assertTrue(atm.phase() == Phase.CASH_READY, "Abandoned cash must dispense and remain ready");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(atm.cashPlan()) == 2700, "Pending cash stays in the machine");
            helper.assertTrue(atm.dispenserAction(player, "PICK_CASH"), "Different player can collect abandoned cash");
            helper.assertFalse(atm.dispenserAction(player, "PICK_CASH"), "Concurrent pickup is rejected");
            helper.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player)) == 0, "Do not grant before pickup animation finishes");
            helper.runAfterDelay(28, () -> {
                helper.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player)) == 2700, "Collector receives exact requested cash once");
                helper.assertTrue(DollarBills.totalCashValueCentsLong(atm.cashPlan()) == 0, "Collected cash is removed from ATM custody");
                helper.assertTrue(atm.phase() == Phase.CLOSING, "Only empty cash slot closes");
                helper.runAfterDelay(27, () -> {
                    helper.assertTrue(atm.phase() == Phase.RECEIPT_CHOICE, "Offer receipt after shutter closes");
                    helper.assertFalse(atm.dispenserAction(absentOwner, "RECEIPT_YES"), "Another player cannot answer collector's receipt prompt");
                    helper.assertTrue(atm.dispenserAction(player, "RECEIPT_YES"), "Collector can request receipt");
                    helper.runAfterDelay(42, () -> {
                        helper.assertTrue(atm.phase() == Phase.RECEIPT_READY, "Printed receipt remains available");
                        var tearTimes = new java.util.ArrayList<Long>();
                        long pickupStarted = helper.getLevel().getGameTime();
                        java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition> tearListener = event -> {
                            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                                    && event.getSound().value() == net.austizz.ultimatebankingsystem.sound.ModSounds.ATM_RECEIPT_TEAR.get()
                                    && Math.abs(event.getPosition().x - (pos.getX() + .675)) < .001
                                    && Math.abs(event.getPosition().z - (pos.getZ() + .15625)) < .001)
                                tearTimes.add(helper.getLevel().getGameTime());
                        };
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(tearListener);
                        helper.assertTrue(atm.dispenserAction(player, "PICK_RECEIPT"), "Receipt can be taken");
                        helper.assertTrue(tearTimes.isEmpty(), "Do not tear the receipt before the hand reaches it");
                        helper.runAfterDelay(7, () -> helper.assertTrue(tearTimes.isEmpty(), "The reach is silent until the hand grips the paper"));
                        helper.assertFalse(atm.dispenserAction(player, "PICK_RECEIPT"), "Receipt pickup cannot duplicate");
                        helper.runAfterDelay(24, () -> {
                            try {
                                helper.assertTrue(tearTimes.size() == 1 && tearTimes.getFirst() - pickupStarted
                                        == net.austizz.ultimatebankingsystem.atm.AtmSoundTiming.RECEIPT_TEAR,
                                        "Play exactly one tearing sound when the hand pulls the paper");
                            } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(tearListener); }
                            helper.assertTrue(atm.phase() == Phase.IDLE, "ATM returns to welcome after receipt collection");
                            helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.PAPER) == 1, "Exactly one receipt item is delivered");
                            helper.assertTrue(DollarBills.totalCashValueCentsLong(DollarBills.getAvailableCashCounts(player)) == 2700, "Receipt never awards more money");
                            var saved = atm.saveWithoutMetadata(helper.getLevel().registryAccess());
                            saved.putString("atm_phase", "RECEIPT_CHOICE");
                            saved.putLong("atm_started", helper.getLevel().getGameTime() - 199);
                            atm.loadWithComponents(saved, helper.getLevel().registryAccess());
                            helper.runAfterDelay(2, () -> {
                                helper.assertTrue(atm.phase() == Phase.IDLE, "Unanswered receipt choice defaults to No after ten seconds");
                                helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.PAPER) == 1,
                                        "Timeout must not print an additional receipt");
                                player.getInventory().clearContent();
                                helper.getLevel().getServer().getPlayerList().remove(player);
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        });
    }
}
