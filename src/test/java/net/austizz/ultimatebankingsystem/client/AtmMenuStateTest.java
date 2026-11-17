package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import org.junit.jupiter.api.Test;
import static net.austizz.ultimatebankingsystem.atm.AtmLcdState.Action.*;
import static net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmMenuStateTest {
    private void digits(AtmLcdState lcd) { for (int key : new int[]{13, 0, 1, 2}) lcd.press(key); }
    private AtmLcdState setup() {
        var lcd = new AtmLcdState();
        lcd.prepareCard(true);
        lcd.setStatus(INSERTING);
        lcd.updateCardStatus(INSERTED, 0);
        lcd.updateCardStatus(INSERTED, 2_000_000_000L);
        return lcd;
    }

    @Test void setupRequiresMatchingRepeatAndServerAcceptance() {
        var lcd = setup();
        assertEquals(CREATE_PIN, lcd.status());
        digits(lcd);
        assertEquals(NONE, lcd.press(11));
        assertEquals(REPEAT_PIN, lcd.status());
        lcd.press(0); lcd.press(0); lcd.press(0); lcd.press(0);
        assertEquals(NONE, lcd.press(11));
        assertEquals(CREATE_PIN, lcd.status());
        assertTrue(lcd.secondary(0).contains("do not match"));
        digits(lcd); lcd.press(11);
        digits(lcd);
        assertEquals(SUBMIT_PIN, lcd.press(11));
        assertEquals("0123", lcd.takePin());
        assertEquals("0123", lcd.takeFirstPin());
        lcd.pinResult(true, "");
        assertEquals(AUTHENTICATED, lcd.status());
        assertTrue(lcd.bankScreen());
        assertEquals(CANCEL, lcd.press(23));
    }

    @Test void clearRepeatDoesNotDestroyFirstEntryAndRemovalClearsBoth() {
        var lcd = setup();
        digits(lcd); lcd.press(11); digits(lcd); lcd.press(7);
        assertEquals("", lcd.maskedPin());
        digits(lcd);
        assertEquals(SUBMIT_PIN, lcd.press(11));
        lcd.setStatus(REMOVING);
        assertEquals("", lcd.takePin());
        assertEquals("", lcd.takeFirstPin());
        lcd.pinResult(true, "");
        assertEquals(REMOVING, lcd.status());
    }

    @Test void menuKeysAmountsAndBackNeverSubmitTwice() {
        var lcd = new AtmLcdState(); lcd.setStatus(AUTHENTICATED);
        assertEquals(NONE, lcd.press(16));
        assertEquals(AtmLcdState.Status.WITHDRAW, lcd.status());
        lcd.press(1); lcd.press(13);
        assertEquals("20", lcd.amount());
        assertEquals(CHECK_WITHDRAW, lcd.press(11));
        assertEquals(PROCESSING, lcd.status());
        assertEquals(NONE, lcd.press(11));
        lcd.actionResult(true, "100.00", "Amount checked.");
        assertEquals(BILL_CHOICE, lcd.status());
        assertEquals(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Action.WITHDRAW, lcd.press(23));
        assertEquals(-1, lcd.selectedBillOption());
        assertEquals(PROCESSING, lcd.status());
        lcd.actionResult(true, "80.00", "Cash dispensed.");
        assertEquals(RESULT, lcd.status());
        lcd.press(23);
        assertEquals(AUTHENTICATED, lcd.status());
        assertEquals(OPEN_DEPOSIT, lcd.press(17));
        assertEquals(PROCESSING, lcd.status());
        assertEquals(NONE, lcd.press(11));
        lcd.actionResult(true, "80.00", "Feeder opened"); lcd.press(23);
        assertEquals(BALANCE, lcd.press(18));
        lcd.actionResult(true, "80.00", "Balance"); lcd.press(23);
        assertEquals(HISTORY, lcd.press(19));
        lcd.setStatus(REMOVING);
        lcd.actionResult(true, "100", "Late response");
        assertEquals(REMOVING, lcd.status());
    }

    @Test void previewsContainLiveMenuAndAmountControls() throws Exception {
        var lcd = setup();
        AtmLcdLayoutTest.preview(lcd, "atm-create-pin");
        digits(lcd); lcd.press(11);
        AtmLcdLayoutTest.preview(lcd, "atm-repeat-pin");
        lcd.setStatus(AUTHENTICATED);
        AtmLcdLayoutTest.preview(lcd, "atm-main-menu");
        assertTrue(AtmLcdLayout.labels(lcd, 0).stream().anyMatch(l -> l.text().contains("Withdraw cash")));
        lcd.press(16); lcd.press(1); lcd.press(13);
        AtmLcdLayoutTest.preview(lcd, "atm-withdraw");
        assertTrue(AtmLcdLayout.labels(lcd, 0).stream().anyMatch(l -> l.text().equals("$ 20")));
    }

    @Test void mainMenuLabelsAlignWithExportedPhysicalScreenKeys() throws Exception {
        var lcd = new AtmLcdState(); lcd.setStatus(AUTHENTICATED);
        var labels = AtmLcdLayout.labels(lcd, 0).stream().filter(l -> l.text().startsWith("‹")).toList();
        var assets = java.nio.file.Path.of("src/main/resources/assets/ultimatebankingsystem");
        var controls = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(assets.resolve("atm_controls.json"))).getAsJsonArray();
        var screen = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(assets.resolve("atm_lcd.json"))).getAsJsonObject();
        double top = screen.getAsJsonArray("to").get(1).getAsDouble();
        double height = top - screen.getAsJsonArray("from").get(1).getAsDouble();
        assertEquals(4, labels.size());
        for (int i = 0; i < 4; i++) {
            var button = controls.get(16 + i).getAsJsonObject();
            double center = (button.getAsJsonArray("from").get(1).getAsDouble() + button.getAsJsonArray("to").get(1).getAsDouble()) / 2;
            double pixel = (top - center) / height * 768;
            assertEquals(pixel, labels.get(i).baseline() - 11, 2, "Option must line up with its physical key");
        }
    }
}
