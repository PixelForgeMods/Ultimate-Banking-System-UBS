package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.*;

class AtmDepositTrayTest {
    @Test void acceptedTotalIsLargeAndRemainsVisibleWhileBillsAreInserted() throws Exception {
        var lcd=new AtmLcdState();
        for(var phase:new net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase[]{DEPOSIT_FEEDING,DEPOSIT_INSERTING}) {
            lcd.showDispenser(phase); lcd.showDepositDetails(12500,new int[12]);
            assertEquals("Accepted so far: $125.00",lcd.primary(0));
            var total=AtmLcdLayout.labels(lcd,0).stream().filter(l->l.text().equals(lcd.primary(0))).findFirst().orElseThrow();
            assertEquals(52,total.size()); assertEquals(AtmLcdLayout.Typeface.BOLD,total.typeface());
        }
        AtmLcdLayoutTest.preview(lcd,"atm-deposit-large-total");
    }
    @Test void clicksAccumulateOneOrAStackAndNeverSelectMoreThanAvailable() {
        var tray = new AtmDepositTray(); tray.stock(new int[]{100,0,3,0,0,0,1});
        assertFalse(tray.take(1,false));
        assertTrue(tray.take(0,false)); assertEquals(1,tray.count());
        assertTrue(tray.take(0,false)); assertEquals(2,tray.count());
        assertTrue(tray.take(0,true)); assertEquals(66,tray.count());
        assertTrue(tray.take(0,true)); assertEquals(100,tray.count());
        assertFalse(tray.take(0,false)); assertTrue(tray.take(2,true)); assertEquals(103,tray.count());
        assertFalse(tray.submit(false)); assertFalse(tray.pending);
        assertTrue(tray.submit(true)); assertTrue(tray.pending);
        assertFalse(tray.submit(true)); assertFalse(tray.take(6,false));
        tray.stock(new int[]{0,0,0,0,0,0,1}); assertEquals(103,tray.count(),"Pending selection survives stock arriving before its acknowledgement");
        tray.result(true,""); assertEquals(0,tray.count()); assertFalse(tray.pending);
    }
    @Test void rejectedDropsAndClearingDoNotChangeAvailableCashAndFadeIsBounded() {
        var tray = new AtmDepositTray(); tray.stock(new int[]{0,0,5,0,0,0,0});tray.take(2,true);tray.submit(true);
        tray.result(false,"Try again");assertEquals(5,tray.count());assertEquals(5,tray.available[2]);
        tray.clear(); assertEquals(0,tray.count()); assertEquals(5,tray.available[2]);
        for(int i=0;i<60;i++)tray.fade(true,1.0/60);assertTrue(tray.opacity>.99 && tray.opacity<=1);
        for(int i=0;i<60;i++)tray.fade(false,1.0/60);assertTrue(tray.opacity<.01 && tray.opacity>=0);
        assertEquals(0,AtmDepositTray.hit(20,AtmDepositTray.top(240)+1,427,240));
        assertEquals(6,AtmDepositTray.hit(20,AtmDepositTray.top(240)+AtmDepositTray.rowHeight(240)*6+1,427,240));
        assertEquals(-1,AtmDepositTray.hit(120,100,427,240));
    }
    @Test void feederStaysOpenAcrossDropsAndClosesOnlyForCountingOrCompletion() {
        assertEquals(1,AtmDispenseMotion.shutter(DEPOSIT_OPENING,25));
        for(var phase : new AtmDispenseMotion.Phase[]{DEPOSIT_FEEDING,DEPOSIT_INSERTING,DEPOSIT_RETURNING,DEPOSIT_RETURN_READY,DEPOSIT_PICKUP})
            for(int age=0;age<200;age++)assertEquals(1,AtmDispenseMotion.shutter(phase,age));
        assertEquals(1,AtmDispenseMotion.shutter(DEPOSIT_COUNTING,0));assertEquals(0,AtmDispenseMotion.shutter(DEPOSIT_COUNTING,25));
        assertEquals(0,AtmDepositMotion.feed(10));assertEquals(1,AtmDepositMotion.feed(28));
        assertTrue(AtmDepositMotion.depth(0)<0);assertEquals(.17,AtmDepositMotion.depth(40),1e-9);
        assertEquals(0,AtmDepositMotion.withdraw(28));assertEquals(1,AtmDepositMotion.withdraw(40));
    }
    @Test void confirmationUsesTheNormalCameraAndBackRestoresTheFeederView() {
        assertTrue(AtmCameraMotion.feederCamera(DEPOSIT_OPENING));
        assertTrue(AtmCameraMotion.feederCamera(DEPOSIT_FEEDING));
        assertTrue(AtmCameraMotion.feederCamera(DEPOSIT_INSERTING));
        assertFalse(AtmCameraMotion.feederCamera(DEPOSIT_COUNTING));
        assertFalse(AtmCameraMotion.feederCamera(DEPOSIT_REVIEW), "Confirm screen must use the original LCD camera position");
        assertTrue(AtmCameraMotion.feederCamera(DEPOSIT_RETURN_OPENING));
        assertTrue(AtmCameraMotion.feederCamera(DEPOSIT_RETURN_READY));
        assertFalse(AtmCameraMotion.feederCamera(DEPOSIT_CLOSING));
        assertFalse(AtmCameraMotion.feederCamera(IDLE));
    }
    @Test void depositViewKeepsTheFeederInFrontAndLooksAtItWithoutCursorChasing() {
        for (int[] facing : new int[][]{{0,-1},{1,0},{0,1},{-1,0}}) {
            var docked=AtmCameraMotion.screen(0,0,0,facing[0],facing[1]);
            var base=AtmCameraMotion.deposit(docked,facing[0],facing[1]);
            double x=.5+facing[0]*(.5+.53/16), z=.5+facing[1]*(.5+.53/16);
            var focused=AtmCameraMotion.lookAt(base,x,.975,z);
            assertTrue(focused.pitch()>35&&focused.pitch()<60,"Feeder should be comfortably below the LCD, not beneath the camera");
            assertEquals(base.x(),focused.x());assertEquals(base.z(),focused.z());
            assertEquals(.30,Math.hypot(base.x()-docked.x(),base.z()-docked.z()),1e-9);
        }
    }
    @Test void localControlsResumeTheSharedBankMenuAfterHardwareDepositFinishes() {
        var local = new AtmLcdState(); local.setStatus(AtmLcdState.Status.AUTHENTICATED);
        assertEquals(AtmLcdState.Action.OPEN_DEPOSIT, local.press(17)); local.showDispenser(DEPOSIT_REVIEW);
        var shared = new AtmLcdState(); shared.setStatus(AtmLcdState.Status.PROCESSING); shared.actionResult(true,"130.00","Deposit complete");
        local.readPublic(shared.publicSnapshot(0)); local.showDispenser(IDLE);
        local.press(23); assertEquals(AtmLcdState.Status.AUTHENTICATED,local.status());
        local.press(16);local.press(1);local.press(13);
        assertEquals(AtmLcdState.Action.CHECK_WITHDRAW,local.press(11),"A deposit must not leave the owner's local controls stuck on the welcome screen");
    }
    @Test void lcdShowsContinueDetectedDenominationsAndConfirmation() throws Exception {
        var lcd = new AtmLcdState();lcd.showDispenser(DEPOSIT_FEEDING);lcd.showDepositDetails(3500,new int[]{0,0,1,1,1,0,0});
        assertTrue(lcd.primary(0).contains("35.00"));
        AtmLcdLayoutTest.preview(lcd,"atm-deposit-feeding");
        assertTrue(AtmLcdLayout.labels(lcd,0).stream().anyMatch(l->l.text().contains("Continue")));
        lcd.showDispenser(DEPOSIT_REVIEW);lcd.showDepositDetails(18800,new int[]{1,1,1,1,1,1,1});
        assertTrue(lcd.primary(0).contains("188.00"));
        assertEquals(7,lcd.secondary(0).split("\n").length);
        AtmLcdLayoutTest.preview(lcd,"atm-deposit-review");
        assertTrue(AtmLcdLayout.labels(lcd,0).stream().anyMatch(l->l.text().contains("Confirm deposit")));
        assertTrue(AtmLcdLayout.labels(lcd,0).stream().anyMatch(l->l.text().contains("Back to feeding")&&l.baseline()==512));
    }
}
