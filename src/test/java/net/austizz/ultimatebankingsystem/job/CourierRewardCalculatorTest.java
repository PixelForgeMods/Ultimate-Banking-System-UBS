package net.austizz.ultimatebankingsystem.job;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierRewardCalculatorTest {
    @Test
    void quoteIncludesCargoDistanceAndContractMultiplier() {
        CourierRewardCalculator.Quote quote = CourierRewardCalculator.calculate(
                500L, 2, 100, new CourierRewardCalculator.Modifiers(125, 120, "priority"));

        assertEquals(500L, quote.marketValueCents());
        assertEquals(3_000L, quote.rewardCents());
        assertEquals("PRIORITY", quote.modifiers().tier());
    }

    @Test
    void quoteClampsUnsafeInputsBeforeCalculating() {
        CourierRewardCalculator.Quote quote = CourierRewardCalculator.calculate(
                Long.MAX_VALUE, 500, Integer.MAX_VALUE,
                new CourierRewardCalculator.Modifiers(999, 1, "unsafe\nlabel"));

        assertEquals(10_000_000L, quote.marketValueCents());
        assertEquals(10_000_000L, quote.rewardCents());
        assertEquals(300, quote.modifiers().moneyMultiplierPercent());
        assertEquals(50, quote.modifiers().xpMultiplierPercent());
        assertEquals("UNSAFE LABEL", quote.modifiers().tier());
    }

    @Test
    void xpIsBoundedAndUsesContractXpMultiplier() {
        CourierRewardCalculator.Quote quote = CourierRewardCalculator.calculate(
                2_000L, 4, 0, new CourierRewardCalculator.Modifiers(100, 150, "black label"));

        assertEquals(129L, CourierRewardCalculator.xpFor(quote));
    }

    @Test
    void fallbackPricingUsesExactItemPathsInsteadOfNameSubstrings() {
        assertEquals(300L, CourierRewardCalculator.fallbackMarketValueCents("minecraft:iron_ingot"));
        assertEquals(850L, CourierRewardCalculator.fallbackMarketValueCents("minecraft:diamond"));
        assertEquals(100L, CourierRewardCalculator.fallbackMarketValueCents("minecraft:barrel"));
        assertEquals(100L, CourierRewardCalculator.fallbackMarketValueCents("minecraft:bar_of_chocolate"));
        assertEquals(100L, CourierRewardCalculator.fallbackMarketValueCents("minecraft:barrier"));
        assertEquals(100L, CourierRewardCalculator.fallbackMarketValueCents("example:ironwood_bar"));
    }

    @Test
    void acceptingAnOfferStartsAFreshCompletionWindow() throws Exception {
        Class<?> orderType = Class.forName(
                "net.austizz.ultimatebankingsystem.job.CourierOrder",
                true, net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader.get());
        Method create = orderType.getMethod("create",
                UUID.class, UUID.class, String.class, String.class, String.class, String.class,
                String.class, String.class, int.class, long.class, long.class, long.class, int.class,
                int.class, int.class, int.class, int.class, int.class, int.class, long.class,
                int.class, int.class, String.class);
        Object order = create.invoke(null,
                UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", "minecraft:overworld",
                "Dispatcher", "Destination", "minecraft:paper", "Paper", 1, 1_000L,
                1_000L, 2_000L, 100, 0, 64, 0, 100, 64, 0, 500L, 100, 100, "STANDARD");

        orderType.getMethod("accept", long.class, long.class).invoke(order, 5_000L, 900_000L);

        assertEquals(905_000L, orderType.getMethod("expiresAtMillis").invoke(order));
        assertEquals(true, orderType.getMethod("isAccepted").invoke(order));
    }

}
