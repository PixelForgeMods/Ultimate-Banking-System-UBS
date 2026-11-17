package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmLcdSharingTest {
    private static void sameDisplay(AtmLcdState source) {
        long now = System.nanoTime();
        var publicState = source.publicSnapshot(now);
        assertFalse(publicState.toString().contains("1234"), "Neither PIN entry may be sent to observers");
        var observer = new AtmLcdState(); observer.readPublic(publicState);
        assertEquals(source.status(), observer.status());
        assertEquals(source.maskedPin(), observer.maskedPin());
        assertEquals(AtmLcdLayout.labels(source, now), AtmLcdLayout.labels(observer, now));
    }
    @Test void observersSeeTheSameSetupPinMenuAmountsAndResultsWithoutReceivingEitherPin() {
        var screen = new AtmLcdState(); sameDisplay(screen);
        screen.prepareCard(true); screen.setStatus(AtmLcdState.Status.CREATE_PIN);
        for (int key : new int[]{0, 1, 2, 4}) { screen.press(key); sameDisplay(screen); }
        screen.press(11); sameDisplay(screen);
        for (int key : new int[]{0, 1, 2, 4}) { screen.press(key); sameDisplay(screen); }
        screen.press(11); sameDisplay(screen);
        screen.pinResult(true, ""); sameDisplay(screen);
        screen.press(16); sameDisplay(screen);
        screen.press(1); screen.press(13); sameDisplay(screen);
        screen.press(11); sameDisplay(screen);
        screen.actionResult(true, "100.00", "Amount checked"); sameDisplay(screen);
        screen.press(19); sameDisplay(screen);
        screen.press(21); sameDisplay(screen);
        screen.press(23); sameDisplay(screen);
        screen.press(23); sameDisplay(screen);
        screen.actionResult(true, "80.00", "Preparing your cash"); sameDisplay(screen);
        screen.press(23); sameDisplay(screen);
        screen.press(18); sameDisplay(screen);
        screen.actionResult(true, "80.00", "Current balance"); sameDisplay(screen);
    }
}
