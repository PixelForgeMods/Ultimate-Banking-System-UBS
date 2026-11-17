package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.*;
import net.austizz.ultimatebankingsystem.atm.AtmLcdState.Action;

class AtmSettingsTest {
    private AtmLcdState settings() {
        var lcd=new AtmLcdState(); lcd.setStatus(AUTHENTICATED); lcd.press(20);
        assertEquals(SETTINGS,lcd.status()); return lcd;
    }
    @Test void navigationAndPhysicalKeysMatchSettingsCategories() throws Exception {
        var lcd=settings();
        assertTrue(AtmLcdLayout.labels(lcd,0).stream().anyMatch(l->l.text().contains("Security")));
        AtmLcdLayoutTest.preview(lcd,"atm-settings");
        lcd.press(17); assertEquals(SECURITY,lcd.status());
        AtmLcdLayoutTest.preview(lcd,"atm-settings-security");
        lcd.press(16); assertEquals(CREATE_PIN,lcd.status());
        lcd.press(3); assertEquals(SECURITY,lcd.status());
        lcd.press(23); assertEquals(SETTINGS,lcd.status());
        lcd.press(23); assertEquals(AUTHENTICATED,lcd.status());
        assertFalse(AtmLcdLayout.labels(lcd,0).stream().anyMatch(l->l.text().contains("Change PIN")));
    }
    @Test void accountInfoCopiesTheAccountIdAndRequestsPrimaryToggle() throws Exception {
        var lcd=settings(); assertEquals(Action.ACCOUNT_INFO,lcd.press(16));
        String id="12345678-1234-1234-1234-123456789abc";
        lcd.actionResult(true,"100","Account: "+id+"\nBank: Test Bank\nType: CheckingAccount\nCreated: 11/08/2026 12:00\nBalance: $100\nPrimary account: No");
        assertEquals(ACCOUNT_INFO,lcd.status()); assertEquals(id,lcd.accountId());
        assertEquals(Action.COPY_ACCOUNT,lcd.press(20));
        AtmLcdLayoutTest.preview(lcd,"atm-settings-info");
        assertEquals(Action.SET_PRIMARY,lcd.press(19));
        lcd.actionResult(true,"100","Account: "+id+"\nPrimary account: Yes");
        var observer=new AtmLcdState(); observer.readPublic(lcd.publicSnapshot(0));
        assertEquals(ACCOUNT_INFO,observer.status()); assertEquals(lcd.details(),observer.details());
    }
    @Test void limitIncreasePromptsForPrivatePinAndResetRequiresConfirmation() throws Exception {
        var lcd=settings(); lcd.press(18); lcd.actionResult(true,"100","Daily remaining: $100");
        lcd.press(19); AtmLcdLayoutTest.preview(lcd,"atm-limit-options");
        lcd.press(16); assertEquals(0,lcd.limitMode());
        lcd.press(1); lcd.press(13); lcd.press(13); lcd.press(11);
        assertEquals(LIMIT_CONFIRM,lcd.status()); AtmLcdLayoutTest.preview(lcd,"atm-limit-confirm");
        assertEquals(Action.SET_LIMIT,lcd.press(19));
        lcd.actionResult(false,"","PIN_REQUIRED"); assertEquals(LIMIT_PIN,lcd.status());
        assertEquals("200",lcd.amount()); assertEquals(0,lcd.requestOption());
        assertEquals(Action.NONE,lcd.press(11));
        for(int i=0;i<4;i++) lcd.press(0);
        assertEquals(Action.SET_LIMIT,lcd.press(11));
        assertFalse(lcd.publicSnapshot(0).toString().contains("1111")); assertEquals("1111",lcd.takePin());
        lcd.actionResult(false,"","Incorrect PIN."); assertEquals(LIMIT_PIN,lcd.status());
        lcd.press(3); assertEquals(LIMIT_CONFIRM,lcd.status());
        lcd.press(23); assertEquals(SET_LIMIT,lcd.status());
        lcd.press(3); assertEquals(LIMIT_OPTIONS,lcd.status());
        lcd.press(19); assertEquals(3,lcd.limitMode()); assertEquals(LIMIT_CONFIRM,lcd.status());
        assertEquals(Action.SET_LIMIT,lcd.press(19));
        lcd.actionResult(true,"100","Daily remaining: $100"); assertEquals(LIMITS,lcd.status());
    }
    @Test void allDurationChoicesAreShownAndSharedWithObservers() throws Exception {
        for(int key=16;key<=18;key++) {
            var lcd=settings();lcd.press(18);lcd.actionResult(true,"100","Daily remaining: $100");
            lcd.press(19);lcd.press(18);lcd.press(0);lcd.press(13);lcd.press(13);lcd.press(11);
            AtmLcdLayoutTest.preview(lcd,"atm-limit-duration");lcd.press(key);
            assertEquals(new int[]{15,60,1440}[key-16],lcd.limitDurationMinutes());
            var observer=new AtmLcdState();observer.readPublic(lcd.publicSnapshot(0));
            assertEquals(lcd.limitMode(),observer.limitMode()); assertEquals(lcd.limitDurationMinutes(),observer.limitDurationMinutes());
            assertEquals(lcd.secondary(0),observer.secondary(0));
        }
    }
    @Test void temporaryLimitUsesTheKeypadAndReturnsToLimits() throws Exception {
        var lcd=settings(); assertEquals(Action.LIMITS,lcd.press(18));
        lcd.actionResult(true,"100","Bank maximum / withdrawal: $500\nPersonal / withdrawal: $300 (active: $300)\nBank daily maximum: $1000\nPersonal daily limit: $600\nTemporary limit: Not active\nTemporary ends: Not active\nWithdrawn today: $100\nDaily remaining: $500\nDaily reset: 11/09/2026 00:00");
        assertEquals(LIMITS,lcd.status());
        AtmLcdLayoutTest.preview(lcd,"atm-settings-limits");
        lcd.press(19); assertEquals(LIMIT_OPTIONS,lcd.status()); lcd.press(18); assertEquals(SET_LIMIT,lcd.status());
        assertEquals(Action.NONE,lcd.press(11));
        lcd.press(1); lcd.press(13); lcd.press(13); assertEquals("200",lcd.amount());
        lcd.press(7); assertEquals("",lcd.amount());
        lcd.press(1); lcd.press(13); lcd.press(13);
        assertEquals(Action.NONE,lcd.press(11)); assertEquals(LIMIT_DURATION,lcd.status()); lcd.press(17); assertEquals(LIMIT_CONFIRM,lcd.status()); assertEquals(Action.SET_LIMIT,lcd.press(19));
        lcd.actionResult(true,"100","Current per withdrawal: $200"); assertEquals(LIMITS,lcd.status());
        lcd.press(19); lcd.press(18); lcd.press(3); assertEquals(LIMIT_OPTIONS,lcd.status()); assertEquals(Action.LIMITS,lcd.press(23)); lcd.actionResult(true,"100","Daily remaining: $1000");
        lcd.press(23); assertEquals(SETTINGS,lcd.status());
    }
}
