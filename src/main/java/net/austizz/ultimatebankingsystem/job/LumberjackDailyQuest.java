package net.austizz.ultimatebankingsystem.job;

import java.util.List;
import java.util.Locale;

/** Deterministic, real-world daily quest rotation for the Lumberjack job. */
public final class LumberjackDailyQuest {
    private static final List<QuestTemplate> RAW_QUESTS = List.of(
            new QuestTemplate("timber_intake", "Timber Intake", "Sell %d raw logs to the Lumberjack Buyer.", 32L, Metric.RAW_ITEMS),
            new QuestTemplate("forest_reserve", "Forest Reserve", "Sell %d raw logs and keep the reserve ledger supplied.", 48L, Metric.RAW_ITEMS),
            new QuestTemplate("heavy_harvest", "Heavy Harvest", "Sell %d raw logs during today's settlement window.", 64L, Metric.RAW_ITEMS),
            new QuestTemplate("responsible_sourcing", "Responsible Sourcing", "Sell %d raw logs for the bank's sustainable intake report.", 80L, Metric.RAW_ITEMS)
    );
    private static final List<QuestTemplate> PRODUCT_QUESTS = List.of(
            new QuestTemplate("millwork_order", "Millwork Order", "Sell %d finished wood products to close the millwork order.", 48L, Metric.PRODUCT_ITEMS),
            new QuestTemplate("joinery_run", "Joinery Run", "Sell %d crafted wood products through the job desk.", 64L, Metric.PRODUCT_ITEMS),
            new QuestTemplate("furniture_ledger", "Furniture Ledger", "Sell %d finished products for the commercial goods ledger.", 80L, Metric.PRODUCT_ITEMS),
            new QuestTemplate("contractor_batch", "Contractor Batch", "Sell %d finished wood products in one productive shift.", 96L, Metric.PRODUCT_ITEMS)
    );
    private static final List<QuestTemplate> EARNINGS_QUESTS = List.of(
            new QuestTemplate("close_the_ledger", "Close the Ledger", "Earn $%s from Lumberjack sales before the daily reset.", 2_500L, Metric.EARNINGS_CENTS),
            new QuestTemplate("settlement_target", "Settlement Target", "Earn $%s in verified wood-sale deposits.", 5_000L, Metric.EARNINGS_CENTS),
            new QuestTemplate("reserve_builder", "Reserve Builder", "Earn $%s for the bank's daily reserve report.", 7_500L, Metric.EARNINGS_CENTS),
            new QuestTemplate("strong_quarter", "Strong Quarter", "Earn $%s in Lumberjack settlement value today.", 10_000L, Metric.EARNINGS_CENTS)
    );

    private LumberjackDailyQuest() {
    }

    public static long currentRotationDay() {
        return DailyQuestClock.rotationDay(System.currentTimeMillis());
    }

    public static long resetEpochMillis(long rotationDay) {
        return DailyQuestClock.nextResetEpochMillis(rotationDay);
    }

    public static List<Definition> forDay(long rotationDay) {
        int index = Math.floorMod(rotationDay, RAW_QUESTS.size());
        QuestTemplate raw = RAW_QUESTS.get(index);
        QuestTemplate products = PRODUCT_QUESTS.get((index + 1) % PRODUCT_QUESTS.size());
        QuestTemplate earnings = EARNINGS_QUESTS.get((index + 2) % EARNINGS_QUESTS.size());
        return List.of(raw.definition(), products.definition(), earnings.definition());
    }

    public record Definition(String id, String title, String description, long target, Metric metric)
            implements JobQuestDefinition {
        public Definition {
            id = normalize(id);
            title = title == null ? "Daily quest" : title.trim();
            description = description == null ? "Complete sales through the Lumberjack Buyer." : description.trim();
            target = Math.max(1L, target);
            metric = metric == null ? Metric.PRODUCT_ITEMS : metric;
        }

        public long progressForSale(String category, int itemCount, long earningsCents) {
            long safeItems = Math.max(0L, itemCount);
            long safeEarnings = Math.max(0L, earningsCents);
            return switch (metric) {
                case RAW_ITEMS -> "raw".equalsIgnoreCase(category) ? safeItems : 0L;
                case PRODUCT_ITEMS -> "raw".equalsIgnoreCase(category) ? 0L : safeItems;
                case EARNINGS_CENTS -> safeEarnings;
            };
        }
    }

    public enum Metric {
        RAW_ITEMS,
        PRODUCT_ITEMS,
        EARNINGS_CENTS
    }

    private record QuestTemplate(String id, String title, String description, long target, Metric metric) {
        private Definition definition() {
            String formattedDescription = metric == Metric.EARNINGS_CENTS
                    ? String.format(Locale.ROOT, description, formatDollars(target))
                    : String.format(Locale.ROOT, description, target);
            return new Definition(id, title, formattedDescription, target, metric);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String formatDollars(long cents) {
        return String.format(Locale.ROOT, "%,d.%02d", cents / 100L, cents % 100L);
    }
}
