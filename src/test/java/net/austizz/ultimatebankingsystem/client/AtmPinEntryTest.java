package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import org.junit.jupiter.api.Test;
import static net.austizz.ultimatebankingsystem.atm.AtmLcdState.Action.*;
import static net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmPinEntryTest {
    @Test void authenticatedPinChangeUsesCreateAndRepeatAndCancelReturnsToMenu() {
        var lcd=new AtmLcdState(); lcd.setStatus(AUTHENTICATED);
        lcd.press(20); assertEquals(SETTINGS,lcd.status()); lcd.press(17); assertEquals(SECURITY,lcd.status()); lcd.press(16); assertEquals(CREATE_PIN,lcd.status()); assertTrue(lcd.creatingPin());
        lcd.press(3); assertEquals(SECURITY,lcd.status()); assertFalse(lcd.creatingPin());
        lcd.press(16); for(int i=0;i<4;i++) lcd.press(0); lcd.press(11);
        assertEquals(REPEAT_PIN,lcd.status());
        for(int i=0;i<4;i++) lcd.press(0);
        assertEquals(SUBMIT_PIN,lcd.press(11));
        assertFalse(lcd.publicSnapshot(0).toString().contains("1111"));
        assertEquals("1111",lcd.takeFirstPin()); assertEquals("1111",lcd.takePin());
        lcd.pinResult(true,""); assertEquals(SECURITY,lcd.status()); assertFalse(lcd.creatingPin());
    }

    private AtmLcdState inserted() {
        var lcd = new AtmLcdState();
        lcd.setStatus(INSERTING);
        lcd.updateCardStatus(INSERTED, 100);
        lcd.updateCardStatus(INSERTED, 2_000_000_100L);
        return lcd;
    }

    @Test
    void waitsTwoSecondsAfterInsertionAndDoesNotRestartThePinPageEachFrame() {
        var lcd = new AtmLcdState();
        lcd.setStatus(INSERTING);
        lcd.updateCardStatus(INSERTED, 100);
        lcd.press(0);
        assertEquals("", lcd.maskedPin());
        lcd.updateCardStatus(INSERTED, 2_000_000_099L);
        assertEquals(INSERTED, lcd.status());
        lcd.updateCardStatus(INSERTED, 2_000_000_100L);
        assertEquals(PIN, lcd.status());
        lcd.press(0);
        lcd.updateCardStatus(INSERTED, 3_000_000_100L);
        assertEquals("•", lcd.maskedPin());
        lcd.updateCardStatus(REMOVING, 4_000_000_100L);
        assertEquals("", lcd.maskedPin());
        lcd.updateCardStatus(INSERTED, 5_000_000_100L);
        assertEquals(INSERTED, lcd.status());
    }

    @Test
    void everyDigitMatchesItsPhysicalKeyAndBlankKeysDoNothing() {
        int[] keys = {13, 0, 1, 2, 4, 5, 6, 8, 9, 10};
        for (int digit = 0; digit <= 9; digit++) {
            var lcd = inserted();
            for (int key : new int[]{12, 14, 15}) lcd.press(key);
            assertEquals("", lcd.maskedPin());
            for (int i = 0; i < 5; i++) lcd.press(keys[digit]);
            assertEquals("••••", lcd.maskedPin());
            assertEquals(String.valueOf(digit).repeat(4), lcd.takePin());
            assertEquals("", lcd.maskedPin());
        }
    }

    @Test
    void enterRequiresFourDigitsClearErasesAndOnlyServerResultAcceptsPin() {
        var lcd = inserted();
        lcd.press(0);
        assertEquals(NONE, lcd.press(11));
        assertEquals(PIN, lcd.status());
        assertTrue(lcd.secondary(0).contains("4 digits"));
        lcd.press(7);
        assertEquals("", lcd.maskedPin());
        for (int key : new int[]{0, 1, 2, 4}) lcd.press(key);
        assertEquals(SUBMIT_PIN, lcd.press(11));
        assertEquals("1234", lcd.takePin());
        assertEquals(VERIFYING_PIN, lcd.status());
        assertEquals(NONE, lcd.press(11));
        lcd.press(0);
        assertEquals("", lcd.maskedPin());
        lcd.pinResult(false, "Incorrect PIN.");
        assertEquals(PIN, lcd.status());
        assertEquals("Incorrect PIN.", lcd.secondary(0));
        for (int key : new int[]{13, 13, 13, 13}) lcd.press(key);
        assertEquals(SUBMIT_PIN, lcd.press(11));
        lcd.takePin();
        lcd.pinResult(true, "");
        assertEquals(AUTHENTICATED, lcd.status());
        lcd.updateCardStatus(INSERTED, 10_000_000_000L);
        assertEquals(AUTHENTICATED, lcd.status());
    }

    @Test
    void cancelWorksDuringPinVerificationAndLateResponsesCannotRestoreExitedSession() {
        var lcd = inserted();
        for (int i = 0; i < 4; i++) lcd.press(0);
        lcd.press(11);
        assertEquals(CANCEL, lcd.press(3));
        lcd.setStatus(REMOVING);
        lcd.pinResult(true, "");
        assertEquals(REMOVING, lcd.status());
        assertEquals("", lcd.maskedPin());
        lcd.setStatus(WELCOME);
        assertEquals(NONE, lcd.press(3));
    }

    @Test
    void livePinPageContainsOnlyMaskedInputAndKeepsDepthLayers() throws Exception {
        var lcd = inserted();
        for (int key : new int[]{0, 1, 2, 4}) lcd.press(key);
        AtmLcdLayoutTest.preview(lcd, "atm-lcd-pin");
        var labels = AtmLcdLayout.labels(lcd, 0);
        assertEquals(4, labels.stream().filter(l -> l.text().equals("•")).count());
        assertFalse(labels.stream().anyMatch(l -> l.text().contains("1234")));
        assertTrue(labels.stream().anyMatch(l -> l.text().equals("Enter your PIN")));
        assertTrue(AtmLcdLayout.PIN_ART.vertices().stream().allMatch(v -> v.z() < AtmLcdLayout.TEXT_LAYER));
    }
}
