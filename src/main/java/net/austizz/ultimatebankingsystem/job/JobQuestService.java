package net.austizz.ultimatebankingsystem.job;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic real-world daily rotations for every registered job.
 * The rotation is calculated from UTC epoch days, so a server restart cannot reroll a player's board.
 */
public final class JobQuestService {
    private JobQuestService() {
    }

    public static long currentRotationDay() {
        return DailyQuestClock.rotationDay(System.currentTimeMillis());
    }

    public static long resetEpochMillis(long rotationDay) {
        return DailyQuestClock.nextResetEpochMillis(rotationDay);
    }

    public static List<Definition> forDay(String jobId, long rotationDay) {
        String id = normalize(jobId);
        if (JobService.LUMBERJACK_ID.equals(id)) {
            return LumberjackDailyQuest.forDay(rotationDay).stream()
                    .map(quest -> new Definition(quest.id(), quest.title(), quest.description(), quest.target(),
                            quest.metric() == LumberjackDailyQuest.Metric.EARNINGS_CENTS
                                    ? Metric.EARNINGS_CENTS : Metric.SALE_ITEMS,
                            "", ""))
                    .toList();
        }
        List<Template> templates = templatesFor(id);
        int offset = Math.floorMod(rotationDay, templates.size());
        return List.of(
                templates.get(offset % templates.size()).toDefinition(),
                templates.get((offset + 1) % templates.size()).toDefinition(),
                templates.get((offset + 2) % templates.size()).toDefinition()
        );
    }

    public static long progressForSale(Definition definition, String category, int itemCount, long earningsCents) {
        if (definition == null) {
            return 0L;
        }
        long items = Math.max(0L, itemCount);
        return switch (definition.metric()) {
            case SALE_ITEMS -> items;
            case CATEGORY_ITEMS -> definition.category().isBlank()
                    || definition.category().equalsIgnoreCase(category) ? items : 0L;
            case EARNINGS_CENTS -> Math.max(0L, earningsCents);
            case ACTIVITY -> 0L;
        };
    }

    public static long progressForActivity(Definition definition, String activityMetric, long amount) {
        if (definition == null || definition.metric() != Metric.ACTIVITY
                || !definition.activityMetric().equalsIgnoreCase(activityMetric)) {
            return 0L;
        }
        return Math.max(0L, amount);
    }

    private static List<Template> templatesFor(String jobId) {
        return switch (jobId) {
            case JobService.MINER_ID -> List.of(
                    activity("ore_route", "Ore Route", "Mine %d blocks and keep the extraction ledger moving.", 48, "blocks_mined"),
                    activity("deep_core", "Deep Core", "Mine %d blocks below the surface for a priority shipment.", 96, "blocks_mined"),
                    sale("smelter_intake", "Smelter Intake", "Sell %d mineral items to the Miner Buyer.", 32),
                    sale("stoneworks_batch", "Stoneworks Batch", "Sell %d stone or metal items through the bank desk.", 64),
                    earnings("ore_settlement", "Ore Settlement", "Earn $%s in verified mining settlements.", 5_000)
            );
            case JobService.FISHERMAN_ID -> List.of(
                    activity("fresh_catch", "Fresh Catch", "Collect %d fish items for the dock ledger.", 24, "fish_caught"),
                    activity("longline", "Longline Shift", "Collect %d fish items without missing the tide window.", 64, "fish_caught"),
                    sale("dockside_trade", "Dockside Trade", "Sell %d catches to the Fisherman Buyer.", 32),
                    sale("cold_storage", "Cold Storage", "Sell %d fish products before the next tide.", 64),
                    earnings("tide_settlement", "Tide Settlement", "Earn $%s from verified dock sales.", 4_000)
            );
            case JobService.FARMER_ID -> List.of(
                    activity("field_rotation", "Field Rotation", "Harvest %d crops for the farm ledger.", 64, "crops_harvested"),
                    activity("livestock_roundup", "Livestock Roundup", "Recover %d eligible livestock goods for the farm ledger.", 32, "animals_harvested"),
                    activity("granary_fill", "Granary Fill", "Harvest %d crops across a productive shift.", 128, "crops_harvested"),
                    sale("market_crates", "Market Crates", "Sell %d farm goods to the Farmer Buyer.", 48),
                    sale("pantry_supply", "Pantry Supply", "Sell %d food or seed items through the desk.", 96),
                    earnings("harvest_settlement", "Harvest Settlement", "Earn $%s in verified farm settlements.", 4_000)
            );
            case JobService.HUNTER_ID -> List.of(
                    activity("tracking_run", "Tracking Run", "Recover %d eligible drops from successful hunts.", 32, "drops_recovered"),
                    activity("apex_contract", "Apex Contract", "Recover %d eligible drops for the security supply chain.", 96, "drops_recovered"),
                    sale("trophy_intake", "Trophy Intake", "Sell %d hunting goods to the Hunter Buyer.", 24),
                    sale("field_supply", "Field Supply", "Sell %d recovered drops through the desk.", 64),
                    earnings("hunt_settlement", "Hunt Settlement", "Earn $%s from verified hunting goods.", 5_000)
            );
            case JobService.BLACKSMITH_ID -> List.of(
                    activity("forge_shift", "Forge Shift", "Craft or deliver %d smithing outputs.", 16, "items_forged"),
                    activity("armory_order", "Armory Order", "Craft %d tools, weapons, or armor for a bank order.", 32, "items_forged"),
                    sale("metal_contract", "Metal Contract", "Sell %d metal goods to the Blacksmith Buyer.", 24),
                    sale("armory_intake", "Armory Intake", "Sell %d finished equipment items.", 48),
                    earnings("forge_settlement", "Forge Settlement", "Earn $%s in verified forge settlements.", 7_500)
            );
            case JobService.COURIER_ID -> List.of(
                    activity("route_departure", "Route Departure", "Complete %d courier deliveries before the route closes.", 3, "deliveries_completed"),
                    activity("express_network", "Express Network", "Complete %d deliveries across the courier network.", 8, "deliveries_completed"),
                    sale("parcel_manifest", "Parcel Manifest", "Turn in %d sealed parcel supplies to the Courier Desk.", 12),
                    sale("dispatch_window", "Dispatch Window", "Process %d courier materials during today's dispatch.", 24),
                    earnings("route_settlement", "Route Settlement", "Earn $%s from completed courier work.", 3_000)
            );
            case JobService.CHEF_ID -> List.of(
                    activity("prep_line", "Prep Line", "Craft %d meals for the kitchen ledger.", 32, "meals_prepared"),
                    activity("service_rush", "Service Rush", "Craft %d meals during a high-volume service.", 96, "meals_prepared"),
                    sale("catering_order", "Catering Order", "Sell %d prepared foods to the Chef Buyer.", 48),
                    sale("pantry_rotation", "Pantry Rotation", "Sell %d edible goods through the desk.", 96),
                    earnings("kitchen_settlement", "Kitchen Settlement", "Earn $%s from verified kitchen orders.", 5_000)
            );
            case JobService.HERBALIST_ID -> List.of(
                    activity("field_samples", "Field Samples", "Gather %d plants for the apothecary ledger.", 48, "plants_gathered"),
                    activity("rare_bloom", "Rare Bloom", "Gather %d plants across the wild supply route.", 128, "plants_gathered"),
                    sale("apothecary_order", "Apothecary Order", "Sell %d botanical items to the Herbalist Buyer.", 32),
                    sale("remedy_stock", "Remedy Stock", "Sell %d ingredients or potions through the desk.", 64),
                    earnings("herbal_settlement", "Herbal Settlement", "Earn $%s from verified apothecary goods.", 4_500)
            );
            case JobService.SALVAGER_ID -> List.of(
                    activity("scrap_run", "Scrap Run", "Recover %d salvageable blocks from old sites.", 64, "scrap_recovered"),
                    activity("reclamation_shift", "Reclamation Shift", "Recover %d pieces of salvage for the recycler ledger.", 160, "scrap_recovered"),
                    sale("recycling_bin", "Recycling Bin", "Sell %d salvage items to the Recycler Buyer.", 48),
                    sale("second_life", "Second Life", "Sell %d reclaimed materials through the desk.", 96),
                    earnings("scrap_settlement", "Scrap Settlement", "Earn $%s from verified reclamation work.", 3_500)
            );
            case JobService.TAILOR_ID -> List.of(
                    activity("pattern_cut", "Pattern Cut", "Craft %d textile or leather outputs.", 24, "items_stitched"),
                    activity("atelier_order", "Atelier Order", "Craft %d wearable goods for a priority order.", 64, "items_stitched"),
                    sale("textile_intake", "Textile Intake", "Sell %d textile goods to the Tailor Buyer.", 32),
                    sale("leather_ledger", "Leather Ledger", "Sell %d textile or leather items through the desk.", 64),
                    earnings("atelier_settlement", "Atelier Settlement", "Earn $%s from verified atelier orders.", 4_500)
            );
            default -> List.of(
                    activity("daily_shift", "Daily Shift", "Complete %d job activities.", 32, "job_activity"),
                    sale("daily_intake", "Daily Intake", "Sell %d supported goods through the job desk.", 32),
                    earnings("daily_settlement", "Daily Settlement", "Earn $%s from verified job work.", 3_000)
            );
        };
    }

    private static Template activity(String id, String title, String description, long target, String metric) {
        return new Template(id, title, description, target, Metric.ACTIVITY, metric, "");
    }

    private static Template sale(String id, String title, String description, long target) {
        return new Template(id, title, description, target, Metric.SALE_ITEMS, "", "");
    }

    private static Template earnings(String id, String title, String description, long targetDollars) {
        return new Template(id, title, description, targetDollars * 100L, Metric.EARNINGS_CENTS, "", "");
    }

    public enum Metric {
        ACTIVITY,
        SALE_ITEMS,
        CATEGORY_ITEMS,
        EARNINGS_CENTS
    }

    public record Definition(String id, String title, String description, long target, Metric metric,
                             String activityMetric, String category) implements JobQuestDefinition {
        public Definition {
            id = normalize(id);
            title = title == null || title.isBlank() ? "Daily quest" : title.trim();
            description = description == null ? "Complete today's job assignment." : description.trim();
            target = Math.max(1L, target);
            metric = metric == null ? Metric.SALE_ITEMS : metric;
            activityMetric = activityMetric == null ? "" : activityMetric.trim().toLowerCase(Locale.ROOT);
            category = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        }
    }

    private record Template(String id, String title, String description, long target, Metric metric,
                            String activityMetric, String category) {
        private Definition toDefinition() {
            String rendered = metric == Metric.EARNINGS_CENTS
                    ? String.format(Locale.ROOT, description, formatDollars(target))
                    : String.format(Locale.ROOT, description, target);
            return new Definition(id, title, rendered, target, metric, activityMetric, category);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String formatDollars(long cents) {
        return String.format(Locale.ROOT, "%,d.%02d", cents / 100L, cents % 100L);
    }
}
