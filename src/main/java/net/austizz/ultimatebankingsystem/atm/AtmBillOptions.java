package net.austizz.ultimatebankingsystem.atm;

import net.austizz.ultimatebankingsystem.item.BillDenominations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Distinct exact-value mixes, preferring each supported largest denomination. */
public final class AtmBillOptions {
    public record Option(int largest, int[] counts) {
        public int billCount() { return Arrays.stream(counts).sum(); }
        public String breakdown() {
            var parts = new ArrayList<String>();
            for (int i = 0; i < counts.length; i++) if (counts[i] > 0)
                parts.add(counts[i] + " × $" + BillDenominations.DOLLARS_DESC[i]);
            return String.join(" + ", parts);
        }
    }
    private AtmBillOptions() {}
    public static List<Option> forAmount(int dollars) {
        if (dollars <= 0 || dollars > 9_999_999) return List.of();
        var options = new ArrayList<Option>();
        var denoms = BillDenominations.DOLLARS_DESC;
        for (int first = 0; first < denoms.length; first++) {
            int remaining = dollars;
            var counts = new int[denoms.length];
            for (int i = first; i < denoms.length; i++) { counts[i] = remaining / denoms[i]; remaining %= denoms[i]; }
            if (options.stream().anyMatch(option -> Arrays.equals(option.counts, counts))) continue;
            int largest = 0;
            for (int i = first; i < denoms.length; i++) if (counts[i] > 0) { largest = denoms[i]; break; }
            options.add(new Option(largest, counts));
        }
        return List.copyOf(options);
    }
}
