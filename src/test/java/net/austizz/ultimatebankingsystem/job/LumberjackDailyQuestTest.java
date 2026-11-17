package net.austizz.ultimatebankingsystem.job;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumberjackDailyQuestTest {
    @Test
    void eachRotationContainsThreeDistinctQuestCards() {
        List<LumberjackDailyQuest.Definition> quests = LumberjackDailyQuest.forDay(42L);

        assertEquals(3, quests.size());
        assertEquals(3, new HashSet<>(quests.stream().map(LumberjackDailyQuest.Definition::id).toList()).size());
        assertTrue(quests.stream().anyMatch(quest -> quest.metric() == LumberjackDailyQuest.Metric.RAW_ITEMS));
        assertTrue(quests.stream().anyMatch(quest -> quest.metric() == LumberjackDailyQuest.Metric.PRODUCT_ITEMS));
        assertTrue(quests.stream().anyMatch(quest -> quest.metric() == LumberjackDailyQuest.Metric.EARNINGS_CENTS));
    }

    @Test
    void saleProgressOnlyAdvancesTheMatchingQuestMetric() {
        List<LumberjackDailyQuest.Definition> quests = LumberjackDailyQuest.forDay(0L);
        LumberjackDailyQuest.Definition raw = quests.stream()
                .filter(quest -> quest.metric() == LumberjackDailyQuest.Metric.RAW_ITEMS)
                .findFirst().orElseThrow();
        LumberjackDailyQuest.Definition products = quests.stream()
                .filter(quest -> quest.metric() == LumberjackDailyQuest.Metric.PRODUCT_ITEMS)
                .findFirst().orElseThrow();
        LumberjackDailyQuest.Definition earnings = quests.stream()
                .filter(quest -> quest.metric() == LumberjackDailyQuest.Metric.EARNINGS_CENTS)
                .findFirst().orElseThrow();

        assertEquals(24L, raw.progressForSale("raw", 24, 100L));
        assertEquals(0L, raw.progressForSale("planks", 24, 100L));
        assertEquals(24L, products.progressForSale("planks", 24, 100L));
        assertEquals(0L, products.progressForSale("raw", 24, 100L));
        assertEquals(100L, earnings.progressForSale("raw", 1, 100L));
        assertFalse(earnings.progressForSale("raw", 1, 0L) > 0L);
    }
}
