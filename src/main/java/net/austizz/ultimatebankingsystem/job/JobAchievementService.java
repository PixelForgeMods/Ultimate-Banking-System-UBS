package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.api.ApiTransactionResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.JobAchievementPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Server-authoritative job achievements, progress snapshots, and one-time rewards. */
public final class JobAchievementService {
    private static final String LUMBERJACK = JobService.LUMBERJACK_ID;
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final int MAX_RANDOM_ITEM_COUNT = 3;

    private JobAchievementService() {
    }

    public static List<JobAchievementPayload> snapshot(MinecraftServer server,
                                                        JobSavedData.JobProgress progress) {
        return snapshot(server, LUMBERJACK, progress);
    }

    /** Builds a job achievement snapshot when no server-specific catalog data is needed. */
    public static List<JobAchievementPayload> snapshot(String jobId,
                                                        JobSavedData.JobProgress progress) {
        return snapshot(null, jobId, progress);
    }

    public static List<JobAchievementPayload> snapshot(MinecraftServer server, String jobId,
                                                        JobSavedData.JobProgress progress) {
        if (progress == null) {
            return List.of();
        }
        return definitionsForJob(server, jobId).stream()
                .map(definition -> toPayload(definition, progress))
                .toList();
    }

    /** Claims one completed achievement. The client supplies only the id; all reward data is server-owned. */
    public static String claim(ServerPlayer player, String achievementId) {
        if (player == null || player.getServer() == null) {
            return "The achievement desk is unavailable.";
        }
        MinecraftServer server = player.getServer();
        if (JobService.isReadOnlySession(server, player.getUUID())) {
            return "Admin preview mode is read-only.";
        }
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(player.getUUID());
        String jobId = record.activeJob();
        JobDefinition job = JobRegistry.get(jobId);
        if (job == null) {
            return "Choose a job before claiming job achievements.";
        }
        AchievementDefinition definition = findDefinition(server, jobId, achievementId);
        if (definition == null) {
            return "That achievement is not available.";
        }
        JobSavedData.JobProgress progress = record.progress(jobId);
        long current = metricProgress(definition, progress);
        if (current < definition.target()) {
            return "That achievement is not complete yet.";
        }

        JobSavedData.AchievementState state = progress.achievementState(definition.id());
        if (state == null) {
            return "The achievement ledger is full. Ask an operator to archive old achievement records.";
        }
        if (state.claimed()) {
            return "That achievement has already been claimed.";
        }

        RolledItem rolledItem = resolveRewardItem(definition.reward(), state);
        if (definition.reward().randomModItem() && rolledItem == null) {
            return "No eligible modded item reward is available on this server.";
        }

        Reward reward = definition.reward();
        if (reward.moneyCents() > 0L) {
            ApiTransactionResult payout = UltimateBankingApiProvider.get().depositToPrimary(
                    player.getUUID(), BigDecimal.valueOf(reward.moneyCents(), 2),
                    "JOB_ACHIEVEMENT:" + player.getUUID() + ":" + definition.id());
            if (!payout.success()) {
                return "Reward not claimed: " + payout.reason() + ". Create a UBS primary account first.";
            }
        }

        if (reward.xp() > 0L) {
            long newXp = saturatingAdd(progress.xp(), reward.xp());
            progress.addCareerXp(reward.xp(), JobService.levelForXp(newXp));
        }
        if (rolledItem != null) {
            ItemStack stack = new ItemStack(rolledItem.item(), rolledItem.count());
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
            player.getInventory().setChanged();
        }

        state.markClaimed();
        data.setDirty();
        return "Claimed " + definition.title() + " reward: " + rewardSummary(reward, rolledItem) + ".";
    }

    private static JobAchievementPayload toPayload(AchievementDefinition definition,
                                                   JobSavedData.JobProgress progress) {
        long current = metricProgress(definition, progress);
        JobSavedData.AchievementState state = progress.achievementStates().get(definition.id());
        RolledItem rolledItem = state == null ? null : persistedRewardItem(state);
        return new JobAchievementPayload(
                definition.id(),
                definition.title(),
                definition.description(),
                definition.category(),
                definition.unit(),
                Math.min(current, definition.target()),
                definition.target(),
                current >= definition.target(),
                state != null && state.claimed(),
                rewardSummary(definition.reward(), rolledItem),
                definition.iconItemId()
        );
    }

    private static List<AchievementDefinition> definitions(MinecraftServer server) {
        List<AchievementDefinition> definitions = new ArrayList<>();
        add(definitions, "lumberjack.first_cut", "First Cut",
                "Break your first supported raw wood block.", "HARVEST", "logs", 1,
                "minecraft:oak_log", Metric.TOTAL_LOGS, "", reward(2_500L, 25L, false, 0));
        add(definitions, "lumberjack.grove_runner", "Grove Runner",
                "Break 25 raw logs and establish your first reliable timber route.", "HARVEST", "logs", 25,
                "minecraft:oak_log", Metric.TOTAL_LOGS, "", reward(25_000L, 100L, false, 0));
        add(definitions, "lumberjack.forest_keeper", "Forest Keeper",
                "Break 250 raw logs while supplying the bank's lumber desk.", "HARVEST", "logs", 250,
                "minecraft:oak_log", Metric.TOTAL_LOGS, "", reward(100_000L, 300L, false, 0));
        add(definitions, "lumberjack.timber_empire", "Timber Empire",
                "Break 1,000 raw logs. Your forestry network is now a serious operation.", "HARVEST", "logs", 1_000,
                "minecraft:golden_axe", Metric.TOTAL_LOGS, "", reward(500_000L, 750L, true, 1));

        add(definitions, "lumberjack.quest_apprentice", "Quest Apprentice",
                "Complete three rotating Lumberjack daily quests.", "CONTRACTS", "quests", 3,
                "minecraft:book", Metric.DAILY_QUESTS, "", reward(25_000L, 100L, false, 0));
        add(definitions, "lumberjack.quest_regular", "Quest Regular",
                "Complete 15 daily quests for the Lumberjack desk.", "CONTRACTS", "quests", 15,
                "minecraft:book", Metric.DAILY_QUESTS, "", reward(100_000L, 350L, false, 0));
        add(definitions, "lumberjack.quest_master", "Quest Master",
                "Complete 50 daily quests. The bank can rely on your consistency.", "CONTRACTS", "quests", 50,
                "minecraft:enchanted_book", Metric.DAILY_QUESTS, "", reward(500_000L, 1_000L, true, 1));

        add(definitions, "lumberjack.first_settlement", "First Settlement",
                "Complete your first accepted sale at the Lumberjack Buyer.", "TRADE", "sales", 1,
                "minecraft:emerald", Metric.SALES, "", reward(10_000L, 75L, false, 0));
        add(definitions, "lumberjack.route_builder", "Route Builder",
                "Complete 50 accepted sales across the wood catalog.", "TRADE", "sales", 50,
                "minecraft:map", Metric.SALES, "", reward(100_000L, 300L, false, 0));
        add(definitions, "lumberjack.wholesale_partner", "Wholesale Partner",
                "Complete 250 accepted sales and become a trusted supplier.", "TRADE", "sales", 250,
                "minecraft:emerald_block", Metric.SALES, "", reward(1_000_000L, 1_000L, true, 1));

        add(definitions, "lumberjack.craftsman", "Certified Craftsman",
                "Sell 100 finished wood products, not only raw logs.", "PRODUCTION", "products", 100,
                "minecraft:crafting_table", Metric.FINISHED_PRODUCTS, "", reward(50_000L, 200L, false, 0));
        add(definitions, "lumberjack.production_line", "Production Line",
                "Sell 1,000 finished wood products through the bank desk.", "PRODUCTION", "products", 1_000,
                "minecraft:stonecutter", Metric.FINISHED_PRODUCTS, "", reward(250_000L, 750L, false, 0));

        add(definitions, "lumberjack.family_traveler", "Family Traveler",
                "Work with three different wood families.", "EXPLORATION", "families", 3,
                "minecraft:compass", Metric.DISTINCT_FAMILIES, "", reward(75_000L, 150L, false, 0));
        add(definitions, "lumberjack.forest_network", "Forest Network",
                "Work with eight different wood families and diversify your supply chain.", "EXPLORATION", "families", 8,
                "minecraft:lodestone", Metric.DISTINCT_FAMILIES, "", reward(250_000L, 750L, true, 1));

        add(definitions, "lumberjack.good_day", "Good Day at the Desk",
                "Complete a single accepted sale worth at least $100.", "MILESTONES", "$", 10_000L,
                "minecraft:gold_ingot", Metric.LARGEST_SALE, "", reward(50_000L, 100L, false, 0));
        add(definitions, "lumberjack.big_haul", "Big Haul",
                "Complete a single accepted sale worth at least $1,000.", "MILESTONES", "$", 100_000L,
                "minecraft:diamond", Metric.LARGEST_SALE, "", reward(250_000L, 500L, true, 1));
        add(definitions, "lumberjack.reserve_builder", "Reserve Builder",
                "Earn $1,000 in lifetime Lumberjack settlements.", "MILESTONES", "$", 100_000L,
                "minecraft:iron_block", Metric.LIFETIME_MONEY, "", reward(100_000L, 300L, false, 0));
        add(definitions, "lumberjack.market_mogul", "Market Mogul",
                "Earn $10,000 in lifetime Lumberjack settlements.", "MILESTONES", "$", 1_000_000L,
                "minecraft:diamond_block", Metric.LIFETIME_MONEY, "", reward(1_000_000L, 1_500L, true, 2));

        add(definitions, "lumberjack.rank_25", "Rank 25 Clearance",
                "Reach Lumberjack level 25.", "CAREER", "level", 25,
                "minecraft:iron_axe", Metric.LEVEL, "", reward(100_000L, 500L, false, 0));
        add(definitions, "lumberjack.rank_50", "Rank 50 Clearance",
                "Reach Lumberjack level 50.", "CAREER", "level", 50,
                "minecraft:diamond_axe", Metric.LEVEL, "", reward(500_000L, 1_000L, true, 1));
        add(definitions, "lumberjack.rank_100", "Rank 100 Clearance",
                "Reach the maximum Lumberjack level.", "CAREER", "level", 100,
                "minecraft:netherite_axe", Metric.LEVEL, "", reward(2_500_000L, 5_000L, true, 2));

        for (String family : LumberjackCatalogService.rawWoodFamilies(server)) {
            String safeFamily = safeId(family);
            String displayFamily = displayFamily(family);
            String icon = LumberjackCatalogService.rawItemIdForFamily(server, family);
            add(definitions, "lumberjack.family." + safeFamily + ".scout",
                    displayFamily + " Scout",
                    "Break 25 " + displayFamily + " logs.", "WOOD FAMILIES", "logs", 25,
                    icon, Metric.FAMILY_LOGS, family, reward(7_500L, 50L, false, 0));
            add(definitions, "lumberjack.family." + safeFamily + ".steward",
                    displayFamily + " Steward",
                    "Break 100 " + displayFamily + " logs.", "WOOD FAMILIES", "logs", 100,
                    icon, Metric.FAMILY_LOGS, family, reward(50_000L, 175L, false, 0));
            add(definitions, "lumberjack.family." + safeFamily + ".master",
                    displayFamily + " Master",
                    "Break 500 " + displayFamily + " logs.", "WOOD FAMILIES", "logs", 500,
                    icon, Metric.FAMILY_LOGS, family, reward(250_000L, 600L, true, 1));
        }
        return definitions.stream()
                .sorted(Comparator.comparing(AchievementDefinition::category)
                        .thenComparing(AchievementDefinition::target)
                        .thenComparing(AchievementDefinition::id))
                .toList();
    }

    private static List<AchievementDefinition> definitionsForJob(MinecraftServer server, String jobId) {
        String normalized = jobId == null ? "" : jobId.trim().toLowerCase(Locale.ROOT);
        return LUMBERJACK.equals(normalized) ? definitions(server) : genericDefinitions(normalized);
    }

    private static List<AchievementDefinition> genericDefinitions(String jobId) {
        JobDefinition job = JobRegistry.get(jobId);
        if (job == null) {
            return List.of();
        }
        String name = job.displayName();
        String metric = activityMetric(jobId);
        List<AchievementDefinition> definitions = new ArrayList<>();
        add(definitions, jobId + ".first_shift", "First Shift",
                "Complete your first recorded " + job.activityLabel() + ".", "FIELDWORK", "activities", 1,
                job.iconItemId(), Metric.ACTIVITY, metric, reward(5_000L, 25L, false, 0));
        add(definitions, jobId + ".steady_hand", "Steady Hand",
                "Complete 50 recorded " + job.activityLabel() + " for the UBS network.", "FIELDWORK", "activities", 50,
                job.iconItemId(), Metric.ACTIVITY, metric, reward(25_000L, 150L, false, 0));
        add(definitions, jobId + ".first_settlement", "First Settlement",
                "Complete your first accepted sale at the " + job.buyerName() + ".", "TRADE", "sales", 1,
                job.iconItemId(), Metric.SALES, "", reward(10_000L, 75L, false, 0));
        add(definitions, jobId + ".trusted_supplier", "Trusted Supplier",
                "Complete 50 accepted sales through the " + job.buyerName() + ".", "TRADE", "sales", 50,
                job.iconItemId(), Metric.SALES, "", reward(100_000L, 300L, false, 0));
        add(definitions, jobId + ".network_builder", "Network Builder",
                "Work with three different material families in the " + name + " supply network.", "EXPLORATION", "families", 3,
                job.iconItemId(), Metric.DISTINCT_FAMILIES, "", reward(75_000L, 200L, false, 0));
        add(definitions, jobId + ".quest_regular", "Quest Regular",
                "Complete 15 rotating daily quests for the " + name + " desk.", "CONTRACTS", "quests", 15,
                "minecraft:book", Metric.DAILY_QUESTS, "", reward(100_000L, 350L, false, 0));
        add(definitions, jobId + ".reserve_builder", "Reserve Builder",
                "Earn $1,000 in lifetime " + name + " settlements.", "MILESTONES", "$", 100_000L,
                "minecraft:iron_block", Metric.LIFETIME_MONEY, "", reward(100_000L, 300L, false, 0));
        add(definitions, jobId + ".rank_25", name + " Rank 25",
                "Reach " + name + " level 25.", "CAREER", "level", 25,
                job.iconItemId(), Metric.LEVEL, "", reward(100_000L, 500L, false, 0));
        add(definitions, jobId + ".rank_50", name + " Rank 50",
                "Reach " + name + " level 50.", "CAREER", "level", 50,
                job.iconItemId(), Metric.LEVEL, "", reward(500_000L, 1_000L, true, 1));
        add(definitions, jobId + ".rank_100", name + " Rank 100",
                "Reach the maximum " + name + " level.", "CAREER", "level", 100,
                job.iconItemId(), Metric.LEVEL, "", reward(2_500_000L, 5_000L, true, 2));
        addDistinctiveAchievements(definitions, jobId, job);
        return definitions.stream()
                .sorted(Comparator.comparing(AchievementDefinition::category)
                        .thenComparing(AchievementDefinition::target)
                        .thenComparing(AchievementDefinition::id))
                .toList();
    }

    private static void addDistinctiveAchievements(List<AchievementDefinition> definitions,
                                                    String jobId, JobDefinition job) {
        String icon = job.iconItemId();
        switch (jobId) {
            case JobService.MINER_ID -> {
                add(definitions, "miner.deep_core", "Deep Core Clearance",
                        "Mine 250 supported stone, ore, or refined mineral blocks.", "FIELDWORK", "blocks", 250,
                        icon, Metric.ACTIVITY, "blocks_mined", reward(125_000L, 450L, false, 0));
                add(definitions, "miner.refinery_partner", "Refinery Partner",
                        "Sell 100 mineral goods through the bank's extraction desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.FISHERMAN_ID -> {
                add(definitions, "fisherman.tidekeeper", "Tidekeeper",
                        "Record 250 catches for the dockside supply ledger.", "FIELDWORK", "fish", 250,
                        icon, Metric.ACTIVITY, "fish_caught", reward(125_000L, 450L, false, 0));
                add(definitions, "fisherman.cold_chain", "Cold-Chain Supplier",
                        "Sell 100 fresh, smoked, or cooked catches through the bank desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.FARMER_ID -> {
                add(definitions, "farmer.field_steward", "Field Steward",
                        "Harvest 500 supported crops for the farm ledger.", "FIELDWORK", "crops", 500,
                        icon, Metric.ACTIVITY, "crops_harvested", reward(125_000L, 450L, false, 0));
                add(definitions, "farmer.livestock_keeper", "Livestock Keeper",
                        "Recover 100 eligible livestock goods from passive animals.", "FIELDWORK", "livestock", 100,
                        icon, Metric.ACTIVITY, "animals_harvested", reward(200_000L, 550L, false, 0));
            }
            case JobService.HUNTER_ID -> {
                add(definitions, "hunter.trailblazer", "Trailblazer",
                        "Recover 250 eligible field drops from player-caused hunts.", "FIELDWORK", "drops", 250,
                        icon, Metric.ACTIVITY, "drops_recovered", reward(125_000L, 450L, false, 0));
                add(definitions, "hunter.trophy_room", "Trophy Room",
                        "Sell 100 hides, pelts, trophies, or field drops through the bank desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.BLACKSMITH_ID -> {
                add(definitions, "blacksmith.forge_master", "Forge Master",
                        "Craft 100 supported tools, weapons, armor, or metal outputs.", "FIELDWORK", "outputs", 100,
                        icon, Metric.ACTIVITY, "items_forged", reward(150_000L, 500L, false, 0));
                add(definitions, "blacksmith.armory_supplier", "Armory Supplier",
                        "Sell 100 metal-stock or equipment items through the armory desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(225_000L, 600L, false, 0));
            }
            case JobService.COURIER_ID -> {
                add(definitions, "courier.route_captain", "Route Captain",
                        "Complete 25 courier turn-ins through the dispatch desk.", "FIELDWORK", "deliveries", 25,
                        icon, Metric.ACTIVITY, "deliveries_completed", reward(125_000L, 450L, false, 0));
                add(definitions, "courier.manifest_master", "Manifest Master",
                        "Sell 100 route documents or parcel supplies through the courier desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.CHEF_ID -> {
                add(definitions, "chef.service_head", "Service Head",
                        "Prepare 100 supported meals for the kitchen ledger.", "FIELDWORK", "meals", 100,
                        icon, Metric.ACTIVITY, "meals_prepared", reward(125_000L, 450L, false, 0));
                add(definitions, "chef.catering_partner", "Catering Partner",
                        "Sell 100 ingredients or prepared foods through the kitchen desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.HERBALIST_ID -> {
                add(definitions, "herbalist.field_botanist", "Field Botanist",
                        "Gather 250 supported plants for the apothecary ledger.", "FIELDWORK", "plants", 250,
                        icon, Metric.ACTIVITY, "plants_gathered", reward(125_000L, 450L, false, 0));
                add(definitions, "herbalist.apothecary_partner", "Apothecary Partner",
                        "Sell 100 botanicals, remedies, or potions through the apothecary desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.SALVAGER_ID -> {
                add(definitions, "salvager.reclamation_expert", "Reclamation Expert",
                        "Recover 250 supported salvage materials from the world.", "FIELDWORK", "scrap", 250,
                        icon, Metric.ACTIVITY, "scrap_recovered", reward(125_000L, 450L, false, 0));
                add(definitions, "salvager.second_life", "Second Life Supplier",
                        "Sell 100 reclaimed materials through the recycler desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            case JobService.TAILOR_ID -> {
                add(definitions, "tailor.pattern_house", "Pattern House",
                        "Craft 100 supported textile or leather outputs.", "FIELDWORK", "outputs", 100,
                        icon, Metric.ACTIVITY, "items_stitched", reward(125_000L, 450L, false, 0));
                add(definitions, "tailor.uniform_contract", "Uniform Contract",
                        "Sell 100 textile or leatherwork items through the atelier desk.", "TRADE", "items", 100,
                        icon, Metric.SALES, "", reward(200_000L, 550L, false, 0));
            }
            default -> {
            }
        }
    }

    private static String activityMetric(String jobId) {
        return switch (jobId) {
            case JobService.MINER_ID -> "blocks_mined";
            case JobService.FISHERMAN_ID -> "fish_caught";
            case JobService.FARMER_ID -> "crops_harvested";
            case JobService.HUNTER_ID -> "drops_recovered";
            case JobService.BLACKSMITH_ID -> "items_forged";
            case JobService.COURIER_ID -> "deliveries_completed";
            case JobService.CHEF_ID -> "meals_prepared";
            case JobService.HERBALIST_ID -> "plants_gathered";
            case JobService.SALVAGER_ID -> "scrap_recovered";
            case JobService.TAILOR_ID -> "items_stitched";
            default -> "job_activity";
        };
    }

    private static void add(List<AchievementDefinition> definitions, String id, String title,
                             String description, String category, String unit, long target,
                             String iconItemId, Metric metric, String family, Reward reward) {
        definitions.add(new AchievementDefinition(id, title, description, category, unit,
                Math.max(1L, target), iconItemId, metric, family, reward));
    }

    private static AchievementDefinition findDefinition(MinecraftServer server, String id) {
        return findDefinition(server, LUMBERJACK, id);
    }

    private static AchievementDefinition findDefinition(MinecraftServer server, String jobId, String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return definitionsForJob(server, jobId).stream()
                .filter(definition -> definition.id().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static long metricProgress(AchievementDefinition definition, JobSavedData.JobProgress progress) {
        return switch (definition.metric()) {
            case TOTAL_LOGS -> progress.lifetimeRawLogsBroken();
            case FAMILY_LOGS -> progress.logsBrokenForFamily(definition.family());
            case DAILY_QUESTS -> progress.completedDailyQuestCount();
            case SALES -> progress.lifetimeSaleCount();
            case FINISHED_PRODUCTS -> progress.lifetimeFinishedProductUnits();
            case DISTINCT_FAMILIES -> progress.distinctFamilies();
            case LARGEST_SALE -> progress.largestSaleCents();
            case LIFETIME_MONEY -> progress.lifetimeMoneyCents();
            case LEVEL -> progress.level();
            case ACTIVITY -> progress.activityMetric(definition.family());
        };
    }

    private static Reward reward(long moneyCents, long xp, boolean randomModItem, int itemCount) {
        return new Reward(Math.max(0L, moneyCents), Math.max(0L, xp), randomModItem,
                Math.max(1, Math.min(MAX_RANDOM_ITEM_COUNT, itemCount)));
    }

    private static RolledItem resolveRewardItem(Reward reward, JobSavedData.AchievementState state) {
        if (!reward.randomModItem()) {
            return null;
        }
        RolledItem persisted = persistedRewardItem(state);
        if (persisted != null) {
            return persisted;
        }
        ResourceLocation selected = randomModItem();
        if (selected == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(selected);
        int count = Math.min(reward.randomItemCount(), Math.max(1, new ItemStack(item).getMaxStackSize()));
        state.setRewardItem(selected.toString(), count);
        return new RolledItem(item, count);
    }

    private static RolledItem persistedRewardItem(JobSavedData.AchievementState state) {
        if (state == null || state.rewardItemId().isBlank() || state.rewardItemCount() <= 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(state.rewardItemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new RolledItem(item, Math.min(state.rewardItemCount(), new ItemStack(item).getMaxStackSize()));
    }

    private static ResourceLocation randomModItem() {
        List<ResourceLocation> candidates = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || MINECRAFT_NAMESPACE.equals(id.getNamespace())
                    || UltimateBankingSystem.MODID.equals(id.getNamespace())
                    || item == Items.AIR || new ItemStack(item).getMaxStackSize() <= 0) {
                continue;
            }
            candidates.add(id);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private static String rewardSummary(Reward reward, RolledItem rolledItem) {
        List<String> parts = new ArrayList<>();
        if (reward.moneyCents() > 0L) {
            parts.add(formatCents(reward.moneyCents()) + " digital money");
        }
        if (reward.xp() > 0L) {
            parts.add("+" + reward.xp() + " job XP");
        }
        if (reward.randomModItem()) {
            parts.add(rolledItem == null
                    ? reward.randomItemCount() + "x random loaded-mod item"
                    : rolledItem.count() + "x " + new ItemStack(rolledItem.item()).getHoverName().getString());
        }
        return String.join("  |  ", parts);
    }

    private static String formatCents(long cents) {
        long safe = Math.max(0L, cents);
        return String.format(Locale.ROOT, "$%,d.%02d", safe / 100L, safe % 100L);
    }

    private static String displayFamily(String family) {
        if (family == null || family.isBlank()) {
            return "Wood";
        }
        String normalized = family.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String safeId(String value) {
        if (value == null || value.isBlank()) {
            return "wood";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private enum Metric {
        TOTAL_LOGS,
        FAMILY_LOGS,
        DAILY_QUESTS,
        SALES,
        FINISHED_PRODUCTS,
        DISTINCT_FAMILIES,
        LARGEST_SALE,
        LIFETIME_MONEY,
        LEVEL,
        ACTIVITY
    }

    private record Reward(long moneyCents, long xp, boolean randomModItem, int randomItemCount) {
    }

    private record RolledItem(Item item, int count) {
    }

    private record AchievementDefinition(String id, String title, String description,
                                         String category, String unit, long target,
                                         String iconItemId, Metric metric, String family,
                                         Reward reward) {
        private AchievementDefinition {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            title = title == null ? "Achievement" : title.trim();
            description = description == null ? "" : description.trim();
            category = category == null ? "GENERAL" : category.trim();
            unit = unit == null ? "" : unit.trim();
            iconItemId = iconItemId == null || iconItemId.isBlank() ? "minecraft:oak_log" : iconItemId;
            family = family == null ? "" : family.trim().toLowerCase(Locale.ROOT);
        }
    }
}
