package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmBillOptions;
import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import net.austizz.ultimatebankingsystem.item.BillDenominations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmBillOptionsTest {
    @Test void calculatedMixesAreExactDistinctAndAutomaticUsesTheMinimumBillCount() {
        var denoms = BillDenominations.DOLLARS_DESC;
        int[] minimum = new int[1001];
        for (int dollars = 1; dollars <= 1000; dollars++) {
            minimum[dollars] = dollars;
            for (int denom : denoms) if (dollars >= denom) minimum[dollars] = Math.min(minimum[dollars], 1 + minimum[dollars - denom]);
            var options = AtmBillOptions.forAmount(dollars);
            assertEquals(minimum[dollars], options.getFirst().billCount(), "Automatic must use the fewest notes for $" + dollars);
            var seen = new java.util.HashSet<String>();
            for (var option : options) {
                long total = 0;
                for (int i = 0; i < denoms.length; i++) { assertTrue(option.counts()[i] >= 0); total += (long) option.counts()[i] * denoms[i]; }
                assertEquals(dollars, total);
                assertTrue(seen.add(java.util.Arrays.toString(option.counts())));
            }
        }
        assertEquals(1, AtmBillOptions.forAmount(1).size());
        assertTrue(AtmBillOptions.forAmount(0).isEmpty());
        assertTrue(AtmBillOptions.forAmount(-1).isEmpty());
        assertTrue(AtmBillOptions.forAmount(10_000_000).isEmpty());
        assertEquals(7, AtmBillOptions.forAmount(9_999_999).size());
    }
    private static AtmLcdState checkedAmount() {
        var screen = new AtmLcdState(); screen.setStatus(AtmLcdState.Status.AUTHENTICATED);
        screen.press(16); screen.press(1); screen.press(13); screen.press(13);
        assertEquals(AtmLcdState.Action.CHECK_WITHDRAW, screen.press(11));
        screen.actionResult(true, "1000.00", "Amount checked"); return screen;
    }
    @Test void yesNoPagingAndBackKeepTheAmountAndSubmitOnlyTheConfirmedMix() {
        var screen = checkedAmount();
        assertTrue(screen.billChoice()); assertEquals("200", screen.amount());
        assertEquals(AtmLcdState.Action.NONE, screen.press(19)); assertTrue(screen.billOptionsScreen());
        assertEquals(3, screen.billPages());
        screen.press(20); assertEquals(0, screen.billPage());
        screen.press(21); assertEquals(1, screen.billPage());
        screen.press(21); screen.press(21); assertEquals(2, screen.billPage());
        assertEquals(AtmLcdState.Action.NONE, screen.press(17), "Blank option must not withdraw");
        screen.press(23); assertTrue(screen.billChoice()); assertEquals("200", screen.amount());
        screen.press(22); assertEquals(AtmLcdState.Status.WITHDRAW, screen.status()); assertEquals("200", screen.amount());
        screen.press(11); screen.actionResult(true, "1000.00", "Amount checked"); screen.press(19); screen.press(21);
        assertEquals(AtmLcdState.Action.WITHDRAW, screen.press(17)); assertEquals(4, screen.selectedBillOption());
        assertEquals(AtmLcdState.Action.NONE, screen.press(17), "Double press while processing must not submit again");
        screen = checkedAmount();
        assertEquals(AtmLcdState.Action.WITHDRAW, screen.press(23)); assertEquals(-1, screen.selectedBillOption());
        screen = checkedAmount(); screen.press(19); screen.press(3); assertTrue(screen.billChoice());
    }
    @Test void choiceAndEveryOptionsPageRenderWithMatchingObserverState() throws Exception {
        var screen = checkedAmount(); AtmLcdLayoutTest.preview(screen, "atm-bill-choice");
        assertTrue(AtmLcdLayout.labels(screen, 0).stream().anyMatch(l -> l.text().contains("No, automatic")));
        screen.press(19);
        for (int page = 0; page < screen.billPages(); page++) {
            assertEquals(page, screen.billPage());
            var replica = new AtmLcdState(); replica.readPublic(screen.publicSnapshot(0));
            assertEquals(AtmLcdLayout.labels(screen, 0), AtmLcdLayout.labels(replica, 0));
            AtmLcdLayoutTest.preview(screen, "atm-bill-options-" + page);
            var rows = AtmLcdLayout.labels(screen, 0).stream().filter(l -> l.text().startsWith("‹")).toList();
            assertEquals(Math.min(3, screen.billOptions().size() - page * 3), rows.size());
            for (int row = 0; row < rows.size(); row++) assertEquals(262 + row * 126, rows.get(row).baseline());
            screen.press(21);
        }
    }
}
