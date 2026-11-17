package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion;
import net.austizz.ultimatebankingsystem.atm.AtmSoundTiming;
import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase;
import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmDispenseMotionTest {
    @Test void everyBillFeedsSeparatelyIncludingBillsBeyondTheOldSevenLayerLimit() {
        int[] plan = {1, 1, 1, 1, 1, 1, 3, 0, 0, 0, 0, 0};
        int count = AtmDispenseMotion.billCount(plan);
        assertEquals(9, count);
        for (int bill = 0; bill < count; bill++) {
            double start = bill * AtmSoundTiming.CASH_BILL_PERIOD;
            assertEquals(bill + 1, AtmDispenseMotion.billsStarted(Phase.DISPENSING, start, count));
            assertEquals(0, AtmDispenseMotion.cash(Phase.DISPENSING, start, bill));
            assertEquals(.5, AtmDispenseMotion.cash(Phase.DISPENSING, start + 4, bill), .00001);
            assertEquals(1, AtmDispenseMotion.cash(Phase.DISPENSING, start + 8, bill));
            if (bill > 0) assertEquals(1, AtmDispenseMotion.cash(Phase.DISPENSING, start, bill - 1));
            if (bill + 1 < count) assertEquals(0, AtmDispenseMotion.cash(Phase.DISPENSING, start + 8, bill + 1));
        }
        assertEquals(count, AtmDispenseMotion.billsStarted(Phase.CASH_READY, 0, count));
        assertEquals(0, AtmDispenseMotion.billsStarted(Phase.OPENING, 0, count));
        assertEquals(90, AtmSoundTiming.cashFeedTicks(count));
        assertEquals(10, AtmSoundTiming.cashFeedTicks(1));
        assertEquals(0, AtmSoundTiming.cashFeedTicks(0));
    }
    @Test void cardReaderPulseIsGentleAndReceiptChoiceExpiresAfterTenSeconds() {
        for (int tick = 0; tick < 96; tick++) {
            double brightness = AtmDispenseMotion.readerBrightness(Phase.TAKE_CARD, tick);
            assertTrue(brightness >= .55 && brightness <= 1);
            assertTrue(Math.abs(brightness - AtmDispenseMotion.readerBrightness(Phase.TAKE_CARD, tick + 1)) < .03);
            var color = AtmDispenseMotion.readerLight(Phase.TAKE_CARD, tick);
            assertTrue(color.green() > color.red() * 2 && color.green() > color.blue() * 2,
                    "Reader must pulse green, never white or gray");
        }
        assertEquals(1, AtmDispenseMotion.readerBrightness(Phase.CASH_READY, 24));
        assertTrue(AtmDispenseMotion.guideLight(false).green() < AtmDispenseMotion.guideLight(true).green() / 2);
        assertEquals(0, AtmDispenseMotion.receipt(Phase.PRINTING, 10), "Print inside before feeding out the completed receipt");
        assertEquals(1, AtmDispenseMotion.receipt(Phase.PRINTING, 40));
        assertFalse(AtmDispenseMotion.receiptTimedOut(199));
        assertTrue(AtmDispenseMotion.receiptTimedOut(200));
        var lcd = new AtmLcdState();
        lcd.showDispenser(Phase.RECEIPT_CHOICE, 100);
        assertEquals(5, lcd.receiptSeconds());
    }

    @Test void receiptTipRestsOnTheExportedKeyboardDeckInsteadOfPassingThroughIt() throws Exception {
        var assets = java.nio.file.Path.of("src/main/resources/assets/ultimatebankingsystem");
        var hardware = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(assets.resolve("atm_dispenser.json"))).getAsJsonObject();
        var slot = hardware.getAsJsonArray("receipt");
        double angle = Math.toRadians(AtmDispenseMotion.RECEIPT_TILT);
        double tipY = slot.get(1).getAsDouble() - 16 * AtmDispenseMotion.RECEIPT_LENGTH * Math.cos(angle);
        double tipZ = slot.get(2).getAsDouble() - .1 - 16 * AtmDispenseMotion.RECEIPT_LENGTH * Math.sin(angle);
        var model = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(assets.resolve("models/block/atm_machine_body.json"))).getAsJsonObject();
        for (var element : model.getAsJsonArray("elements")) {
            var deck = element.getAsJsonObject();
            if (!deck.get("name").getAsString().equals("Sloped metal keyboard deck")) continue;
            var rotation = deck.getAsJsonObject("rotation");
            var origin = rotation.getAsJsonArray("origin");
            double a = Math.toRadians(rotation.get("angle").getAsDouble());
            double y = deck.getAsJsonArray("to").get(1).getAsDouble() - origin.get(1).getAsDouble();
            double z = (tipZ - origin.get(2).getAsDouble() - y * Math.sin(a)) / Math.cos(a);
            double deckY = origin.get(1).getAsDouble() + y * Math.cos(a) - z * Math.sin(a);
            assertTrue(tipY >= deckY && tipY - deckY < .1, "Paper must rest just above the real deck surface");
            return;
        }
        fail("Missing keyboard deck geometry");
    }
    @Test void dispensedBillCenterStopsAtTheSlotSoHalfRemainsInside() {
        assertTrue(AtmDispenseMotion.cashDepth(Phase.DISPENSING, 0, 0) > 0);
        assertEquals(0, AtmDispenseMotion.cashDepth(Phase.DISPENSING, AtmSoundTiming.CASH_FEED, 0));
        assertEquals(0, AtmDispenseMotion.cashDepth(Phase.CASH_READY, 100_000, 0));
        for (int tick = 0; tick <= AtmSoundTiming.CASH_FEED; tick++) {
            assertTrue(AtmDispenseMotion.cashDepth(Phase.DISPENSING, tick, 0) >= 0,
                    "Do not push the bill center beyond the cash slot");
        }
    }
    @Test void closeCameraPickupReachesThenLiftsIntoViewBeforePuttingCashAway() {
        var start = AtmDispenseMotion.pickup(0, false);
        var grasp = AtmDispenseMotion.pickup(10, false);
        var lifted = AtmDispenseMotion.pickup(18, false);
        var end = AtmDispenseMotion.pickup(26, false);
        assertEquals(0, start.reach());
        assertEquals(1, grasp.reach());
        assertEquals(0, grasp.y(), "Cash must stay in the slot until the hand reaches it");
        assertTrue(lifted.y() > .3, "Lift the cash into the original close camera framing");
        assertTrue(lifted.z() > 0, "Keep raised cash ahead of the close camera near plane");
        assertTrue(end.y() < 0 && end.z() < -.3, "Finish by tucking cash below the view");
        assertTrue(AtmDispenseMotion.pickup(18, true).y() < lifted.y(), "The higher receipt slot needs a shorter lift");
    }
    @Test void shutterOpensBeforeCashMovesAndOnlyClosesAfterPickup() {
        assertEquals(0, AtmDispenseMotion.shutter(Phase.WAIT_CASH, 40));
        assertEquals(0, AtmDispenseMotion.cash(Phase.OPENING, 25, 0));
        assertEquals(1, AtmDispenseMotion.shutter(Phase.OPENING, 25));
        assertEquals(1, AtmDispenseMotion.shutter(Phase.DISPENSING, 0));
        assertEquals(0, AtmDispenseMotion.cash(Phase.DISPENSING, 0, 0));
        assertEquals(1, AtmDispenseMotion.cash(Phase.DISPENSING, AtmSoundTiming.CASH_FEED, 0));
        assertEquals(1, AtmDispenseMotion.cash(Phase.CASH_READY, 1_000_000, 0));
        assertEquals(1, AtmDispenseMotion.shutter(Phase.PICKING_CASH, 26));
        assertEquals(0, AtmDispenseMotion.shutter(Phase.CLOSING, 25));
        double previous = -1;
        for (int i = 0; i <= 100; i++) {
            double value = AtmDispenseMotion.cash(Phase.DISPENSING, i * AtmSoundTiming.CASH_FEED / 100.0, 0);
            assertTrue(value >= previous && value <= 1); previous = value;
        }
    }

    @Test void withdrawalAndReceiptPromptsAreLiveAndChoiceKeysDoNotRestartBanking() throws Exception {
        var lcd = new AtmLcdState();
        lcd.showDispenser(Phase.TAKE_CARD);
        assertEquals("Take your card", lcd.title());
        assertEquals(AtmLcdState.Action.NONE, lcd.press(11));
        lcd.showDispenser(Phase.CASH_READY);
        assertEquals("Take your cash", lcd.title());
        assertEquals(AtmLcdState.Action.PICK_CASH, lcd.press(23), "Close camera must still allow cash collection through the physical screen key");
        AtmLcdLayoutTest.preview(lcd, "atm-take-cash");
        lcd.showDispenser(Phase.RECEIPT_CHOICE);
        assertEquals(AtmLcdState.Action.RECEIPT_YES, lcd.press(19));
        assertEquals(AtmLcdState.Action.RECEIPT_NO, lcd.press(23));
        AtmLcdLayoutTest.preview(lcd, "atm-receipt-choice");
        lcd.showDispenser(Phase.RECEIPT_READY);
        assertEquals("Take your receipt", lcd.title());
        assertEquals(1, AtmDispenseMotion.receipt(Phase.RECEIPT_READY, 100_000));
        lcd.showDispenser(Phase.IDLE);
        assertEquals(AtmLcdState.Status.WELCOME, lcd.status());
    }
}
