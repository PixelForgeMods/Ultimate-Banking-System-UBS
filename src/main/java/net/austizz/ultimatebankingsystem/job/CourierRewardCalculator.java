package net.austizz.ultimatebankingsystem.job;

import java.util.Locale;
import java.util.Random;

/** Deterministic, bounded pricing rules for one NPC-to-NPC Courier route. */
public final class CourierRewardCalculator {
    private static final long MAX_REWARD_CENTS = 10_000_000L;

    private CourierRewardCalculator() {
    }

    public record Modifiers(int moneyMultiplierPercent, int xpMultiplierPercent, String tier) {
        public Modifiers {
            moneyMultiplierPercent = clampMultiplier(moneyMultiplierPercent);
            xpMultiplierPercent = clampMultiplier(xpMultiplierPercent);
            tier = cleanTier(tier);
        }

        public static Modifiers standard() {
            return new Modifiers(100, 100, "STANDARD");
        }
    }

    public record Quote(long marketValueCents, long rewardCents, Modifiers modifiers) {
        public Quote {
            marketValueCents = Math.max(0L, marketValueCents);
            rewardCents = Math.max(1L, Math.min(MAX_REWARD_CENTS, rewardCents));
            modifiers = modifiers == null ? Modifiers.standard() : modifiers;
        }
    }

    /**
     * Generates a small set of weighted contract modifiers. The random source is server-side only.
     */
    public static Modifiers randomModifiers(Random random) {
        if (random == null) {
            return Modifiers.standard();
        }
        int roll = random.nextInt(100);
        if (roll < 8) {
            return new Modifiers(150, 135, "BLACK LABEL");
        }
        if (roll < 20) {
            return new Modifiers(125, 120, "PRIORITY");
        }
        if (roll < 35) {
            return new Modifiers(110, 115, "EXPRESS");
        }
        return Modifiers.standard();
    }

    /**
     * Uses the median market value when available, then adds a dispatch fee and distance pay.
     * The result is capped before it can enter saved data or an economy transaction.
     */
    public static Quote calculate(long marketValueCents, int quantity, int distanceBlocks,
                                  Modifiers modifiers) {
        long unitValue = Math.max(1L, Math.min(10_000_000L, marketValueCents));
        long units = Math.max(1L, Math.min(64L, quantity));
        long distance = Math.max(0L, Math.min(30_000_000L, distanceBlocks));
        Modifiers safeModifiers = modifiers == null ? Modifiers.standard() : modifiers;
        long cargoValue = saturatingMultiply(unitValue, units);
        long distanceFee = Math.min(MAX_REWARD_CENTS, saturatingMultiply(distance, 8L));
        long base = saturatingAdd(600L, saturatingAdd(cargoValue, distanceFee));
        long reward = saturatingMultiply(base, safeModifiers.moneyMultiplierPercent()) / 100L;
        return new Quote(unitValue, Math.max(1L, Math.min(MAX_REWARD_CENTS, reward)), safeModifiers);
    }

    public static long fallbackMarketValueCents(String itemId) {
        String normalized = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        String path = separator < 0 ? normalized : normalized.substring(separator + 1);
        if (path.isBlank()) {
            return 100L;
        }
        return switch (path) {
            case "netherite_scrap", "netherite_ingot", "netherite_block", "ancient_debris",
                    "dragon_egg", "dragon_breath", "elytra" -> 1_800L;
            case "diamond", "diamond_block", "diamond_ore", "deepslate_diamond_ore",
                    "emerald", "emerald_block", "emerald_ore", "deepslate_emerald_ore",
                    "amethyst_shard", "amethyst_block", "budding_amethyst" -> 850L;
            case "ender_pearl", "ender_eye", "end_crystal", "blaze_rod", "blaze_powder",
                    "phantom_membrane" -> 600L;
            case "gold_ingot", "gold_nugget", "gold_block", "gold_ore", "deepslate_gold_ore",
                    "nether_gold_ore", "iron_ingot", "iron_nugget", "iron_block", "iron_ore",
                    "deepslate_iron_ore", "redstone", "redstone_block", "redstone_ore",
                    "deepslate_redstone_ore" -> 300L;
            case "bread", "leather", "honey_bottle", "honeycomb" -> 160L;
            default -> 100L;
        };
    }

    public static long xpFor(Quote quote) {
        if (quote == null) {
            return 10L;
        }
        long base = Math.max(10L, Math.min(2_000L, quote.rewardCents() / 100L));
        return Math.max(10L, Math.min(3_000L,
                saturatingMultiply(base, quote.modifiers().xpMultiplierPercent()) / 100L));
    }

    private static int clampMultiplier(int value) {
        return Math.max(50, Math.min(300, value <= 0 ? 100 : value));
    }

    private static String cleanTier(String value) {
        if (value == null || value.isBlank()) return "STANDARD";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim().toUpperCase(Locale.ROOT);
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return Math.max(0L, left);
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
