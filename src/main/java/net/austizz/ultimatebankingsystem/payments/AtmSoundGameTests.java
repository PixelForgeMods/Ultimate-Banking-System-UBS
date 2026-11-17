package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.atm.AtmSoundTiming;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public final class AtmSoundGameTests {
    @GameTest(template = "empty3x3x3", timeoutTicks = 70)
    public static void receiptPrinterPlaysOnceAtItsRotatedSlotAndFinishesWithPaper(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState().setValue(
                net.austizz.ultimatebankingsystem.block.custom.ATMBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        var atm = (AtmBlockEntity) helper.getLevel().getBlockEntity(pos);
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "atm-printer-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        var tag = atm.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putString("atm_phase", "RECEIPT_CHOICE"); tag.putUUID("atm_owner", player.getUUID());
        atm.loadWithComponents(tag, helper.getLevel().registryAccess());
        var events = new java.util.ArrayList<PlayLevelSoundEvent.AtPosition>();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ModSounds.ATM_RECEIPT_PRINT.get()
                    && Math.abs(event.getPosition().x - (pos.getX() + .84375)) < .001) events.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        helper.assertTrue(atm.dispenserAction(player, "RECEIPT_YES"), "Start receipt printing");
        helper.assertFalse(atm.dispenserAction(player, "RECEIPT_YES"), "Repeated presses cannot restart printing or stack audio");
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(events.size() == 1, "Exactly one printer sound throughout the paper feed");
                var event = events.getFirst();
                helper.assertTrue(Math.abs(event.getPosition().y - (pos.getY() + 20.315 / 16)) < .001
                        && Math.abs(event.getPosition().z - (pos.getZ() + .675)) < .001
                        && event.getSource() == SoundSource.BLOCKS && event.getOriginalPitch() == 1,
                        "Printer audio comes from the rotated receipt slot at original pitch");
                helper.assertTrue(atm.phase() == net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.RECEIPT_READY,
                        "Paper finishes its two-second print cycle with the sound");
                tag.putString("atm_phase", "RECEIPT_CHOICE");
                atm.loadWithComponents(tag, helper.getLevel().registryAccess());
                helper.assertTrue(atm.dispenserAction(player, "RECEIPT_NO"), "Decline next receipt");
                helper.assertTrue(events.size() == 1, "Declining a receipt must stay silent");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }
    @GameTest(template = "empty3x3x3", timeoutTicks = 200)
    public static void nineBillsHaveNineFeedSoundsAndCashWaitsForTheLastBill(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var atm = (AtmBlockEntity) helper.getLevel().getBlockEntity(pos);
        var owner = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "atm-cash-sound-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        owner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
        var pulses = new java.util.ArrayList<Long>();
        var prepare = new java.util.ArrayList<Long>();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() != helper.getLevel() || event.getSound() == null
                    || Math.abs(event.getPosition().x - (pos.getX() + .5)) > .001
                    || Math.abs(event.getPosition().z - (pos.getZ() - .02)) > .001) return;
            if (event.getSound().value() == ModSounds.ATM_CASH_DISPENSE.get()) pulses.add(helper.getLevel().getGameTime());
            if (event.getSound().value() == ModSounds.ATM_CASH_PREPARE.get()) prepare.add(helper.getLevel().getGameTime());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        int[] plan = {1, 1, 1, 1, 1, 1, 3, 0, 0, 0, 0, 0};
        helper.assertTrue(atm.reserveCash(owner, plan, 190), "Reserve all nine bills, including mixed denominations");
        helper.runAfterDelay(AtmSoundTiming.cashCycleTicks(9) - 5, () -> {
            helper.assertTrue(atm.phase() == net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.DISPENSING,
                    "Do not finish while the ninth bill is still feeding");
            helper.assertFalse(atm.dispenserAction(owner, "PICK_CASH"), "Cannot collect an incomplete stack");
        });
        helper.runAfterDelay(AtmSoundTiming.cashCycleTicks(9) + 5, () -> {
            try {
                helper.assertTrue(prepare.size() == 1, "One preparation recording per withdrawal");
                helper.assertTrue(pulses.size() == 9, "Exactly one feed sound for every bill, with no seven-bill cap");
                helper.assertTrue(pulses.getFirst() - prepare.getFirst() == AtmSoundTiming.CASH_PREPARE + AtmSoundTiming.SHUTTER_OPEN,
                        "Feed starts only after preparation and opening");
                for (int i = 1; i < pulses.size(); i++)
                    helper.assertTrue(pulses.get(i) - pulses.get(i - 1) == AtmSoundTiming.CASH_BILL_PERIOD,
                            "Each bill sound follows the individual feed cadence");
                helper.assertTrue(atm.phase() == net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.CASH_READY,
                        "Cash is ready after all nine bills finish");
                helper.assertTrue(java.util.Arrays.equals(plan, atm.cashPlan()), "Animation never changes the actual cash plan");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }
    @GameTest(template = "empty3x3x3", timeoutTicks = 120)
    public static void cardMotorPlaysTwoSpatialIntakeCuesAndOneReturnCueWithoutSpam(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, ModBlocks.ATM_MACHINE.get().defaultBlockState(), 3);
        var atm = (AtmBlockEntity) helper.getLevel().getBlockEntity(pos);
        var times = new java.util.ArrayList<Long>();
        var events = new java.util.ArrayList<PlayLevelSoundEvent.AtPosition>();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ModSounds.ATM_CARD_MECHANISM.get()
                    && Math.abs(event.getPosition().x - (pos.getX() + .5)) < .001
                    && Math.abs(event.getPosition().z - (pos.getZ() + .16)) < .001) {
                times.add(helper.getLevel().getGameTime()); events.add(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        atm.startCardIntake(); atm.startCardIntake();
        helper.runAfterDelay(AtmSoundTiming.CARD_INSERT + 5, () -> {
            try {
                helper.assertTrue(times.size() == 2, "Intake and fully-in read each play once, not every tick");
                helper.assertTrue(times.get(1) - times.get(0) == AtmSoundTiming.CARD_MOTOR, "Reading cue waits for intake recording to finish");
                helper.assertTrue(events.stream().allMatch(e -> e.getSource() == SoundSource.BLOCKS
                        && Math.abs(e.getPosition().y - (pos.getY() + 1.27)) < .001 && e.getOriginalPitch() == 1),
                        "Motor sound must come from the physical reader at original pitch");
                var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "atm-audio-test"),
                        net.minecraft.server.level.ClientInformation.createDefault());
                player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - .6);
                helper.assertTrue(atm.claimView(player), "Owner has exclusive ATM access");
                var state = atm.saveWithoutMetadata(helper.getLevel().registryAccess());
                state.putString("atm_card_stage", "INSERTED"); state.putUUID("atm_viewer", player.getUUID());
                atm.loadWithComponents(state, helper.getLevel().registryAccess());
                helper.assertTrue(atm.dispenserAction(player, "CARD_MOTOR"), "Ordinary card removal starts return motor");
                helper.assertFalse(atm.dispenserAction(player, "CARD_MOTOR"), "Repeated messages cannot stack motor sounds");
                helper.assertTrue(times.size() == 3, "One additional cue for card return");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }
}
