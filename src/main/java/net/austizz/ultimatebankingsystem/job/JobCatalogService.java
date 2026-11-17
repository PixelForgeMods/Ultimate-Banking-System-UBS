package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceScope;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceStatistics;
import net.austizz.ultimatebankingsystem.shop.ShopMarketPriceService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shared item catalog seam for job buyers. The classifier is intentionally registry-driven:
 * loaded mods contribute through standard item/block tags, while vanilla compatibility fallbacks
 * use exact registry identities. Registry-name substrings never decide whether an item is sellable.
 */
public final class JobCatalogService {
    private static final Map<MinecraftServer, Map<String, List<CatalogEntry>>> CATALOGS = new WeakHashMap<>();
    private static final Map<MinecraftServer, MarketStatisticsCache> MARKET_STATISTICS = new WeakHashMap<>();
    private static final TagKey<Item> COMMON_ORES = commonTag("ores");
    private static final TagKey<Item> COMMON_INGOTS = commonTag("ingots");
    private static final TagKey<Item> COMMON_GEMS = commonTag("gems");
    private static final TagKey<Item> COMMON_CROPS = commonTag("crops");
    private static final TagKey<Item> COMMON_SEEDS = commonTag("seeds");
    private static final TagKey<Item> COMMON_WOOL = commonTag("wool");
    private static final TagKey<Item> COMMON_CARPETS = commonTag("carpets");
    private static final TagKey<Item> COMMON_DYES = commonTag("dyes");
    private static final TagKey<Item> COMMON_FISHES = commonTag("fishes");
    private static final TagKey<Item> COMMON_FLOWERS = commonTag("flowers");
    private static final TagKey<Item> COMMON_LEAVES = commonTag("leaves");
    private static final TagKey<Item> COMMON_SAPLINGS = commonTag("saplings");
    private static final TagKey<Item> COMMON_FOODS = commonTag("foods");
    private static final TagKey<Item> COMMON_RAW_MATERIALS = commonTag("raw_materials");
    private static final TagKey<Item> COMMON_RAW_ORES = commonTag("raw_ores");
    private static final TagKey<Item> COMMON_STONES = commonTag("stones");
    private static final TagKey<Item> COMMON_STONE = commonTag("stone");
    private static final TagKey<Item> COMMON_RAW_FISHES = commonTag("raw_fishes");
    private static final TagKey<Item> COMMON_COOKED_FISHES = commonTag("cooked_fishes");
    private static final TagKey<Item> COMMON_SMOKED_FISHES = commonTag("smoked_fishes");
    private static final TagKey<Item> COMMON_LIVESTOCK = commonTag("livestock");
    private static final TagKey<Item> COMMON_MEAT = commonTag("meat");
    private static final TagKey<Item> COMMON_PRODUCE = commonTag("produce");
    private static final TagKey<Item> COMMON_MOB_DROPS = commonTag("mob_drops");
    private static final TagKey<Item> COMMON_TROPHIES = commonTag("trophies");
    private static final TagKey<Item> COMMON_HIDES = commonTag("hides");
    private static final TagKey<Item> COMMON_METAL_STOCK = commonTag("metal_stock");
    private static final TagKey<Item> COMMON_PREPARED_FOODS = commonTag("prepared_foods");
    private static final TagKey<Item> COMMON_BOTANICALS = commonTag("botanicals");
    private static final TagKey<Item> COMMON_REMEDIES = commonTag("remedies");
    private static final TagKey<Item> COMMON_SALVAGE = commonTag("salvage_materials");
    private static final TagKey<Item> COMMON_SCRAP = commonTag("scrap");
    private static final TagKey<Item> COMMON_TEXTILES = commonTag("textiles");
    private static final TagKey<Item> COMMON_LEATHER = commonTag("leather");
    private static final TagKey<Item> COMMON_STRINGS = commonTag("strings");

    private static final Set<String> MINER_ORE_ITEMS = Set.of(
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "emerald_ore", "deepslate_emerald_ore",
            "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris");
    private static final Set<String> MINER_RAW_ITEMS = Set.of("raw_iron", "raw_copper", "raw_gold");
    private static final Set<String> MINER_REFINED_ITEMS = Set.of(
            "iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot", "iron_nugget", "gold_nugget",
            "diamond", "emerald", "lapis_lazuli", "redstone", "quartz", "amethyst_shard", "netherite_scrap");
    private static final Set<String> MINER_STONE_ITEMS = Set.of(
            "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite",
            "polished_andesite", "deepslate", "cobbled_deepslate", "polished_deepslate", "calcite", "tuff",
            "dripstone_block", "pointed_dripstone", "cobblestone", "mossy_cobblestone", "gravel", "flint",
            "sand", "red_sand", "sandstone", "red_sandstone", "netherrack", "soul_sand", "soul_soil",
            "basalt", "polished_basalt", "smooth_basalt", "blackstone", "end_stone", "obsidian",
            "crying_obsidian", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
            "chiseled_stone_bricks", "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles",
            "cracked_deepslate_tiles", "chiseled_deepslate", "bricks", "nether_bricks", "quartz_block",
            "quartz_bricks", "prismarine", "prismarine_bricks", "dark_prismarine", "purpur_block",
            "purpur_pillar", "clay", "packed_mud", "mud_bricks");
    private static final Set<String> RAW_FISH_ITEMS = Set.of("cod", "salmon", "pufferfish", "tropical_fish");
    private static final Set<String> COOKED_FISH_ITEMS = Set.of("cooked_cod", "cooked_salmon");
    private static final Set<String> FARM_SEED_ITEMS = Set.of(
            "wheat_seeds", "beetroot_seeds", "pumpkin_seeds", "melon_seeds", "torchflower_seeds", "pitcher_pod");
    private static final Set<String> FARM_GRAIN_ITEMS = Set.of("wheat", "hay_block");
    private static final Set<String> FARM_PRODUCE_ITEMS = Set.of(
            "carrot", "potato", "beetroot", "melon", "melon_slice", "pumpkin", "cocoa_beans", "sugar_cane",
            "cactus", "sweet_berries", "glow_berries", "apple", "chorus_fruit");
    private static final Set<String> FARM_LIVESTOCK_ITEMS = Set.of(
            "milk_bucket", "egg", "honey_bottle", "beef", "cooked_beef", "porkchop", "cooked_porkchop",
            "chicken", "cooked_chicken", "mutton", "cooked_mutton", "rabbit", "cooked_rabbit");
    private static final Set<String> HUNTER_TROPHY_ITEMS = Set.of(
            "bone", "arrow", "turtle_scute", "armadillo_scute", "shulker_shell", "nautilus_shell", "goat_horn",
            "prismarine_shard", "phantom_membrane", "ghast_tear", "ender_pearl", "blaze_rod", "magma_cream");
    private static final Set<String> HUNTER_HIDE_ITEMS = Set.of("leather", "rabbit_hide", "phantom_membrane");
    private static final Set<String> HUNTER_DROP_ITEMS = Set.of(
            "rotten_flesh", "string", "gunpowder", "spider_eye", "slime_ball", "feather", "ink_sac");
    private static final Set<String> BLACKSMITH_STOCK_ITEMS = Set.of(
            "iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot", "iron_nugget", "gold_nugget",
            "raw_iron", "raw_copper", "raw_gold", "netherite_scrap", "diamond", "emerald", "lapis_lazuli",
            "redstone", "quartz", "amethyst_shard", "iron_bars", "chain", "bucket", "water_bucket",
            "lava_bucket", "powder_snow_bucket", "anvil", "chipped_anvil", "damaged_anvil");
    private static final Set<String> PREPARED_FOOD_ITEMS = Set.of(
            "bread", "cookie", "cake", "pumpkin_pie", "mushroom_stew", "rabbit_stew", "beetroot_soup",
            "suspicious_stew", "cooked_beef", "cooked_chicken", "cooked_mutton", "cooked_porkchop",
            "cooked_rabbit", "cooked_cod", "cooked_salmon", "baked_potato", "dried_kelp", "golden_apple",
            "enchanted_golden_apple");
    private static final Set<String> HERBAL_REMEDY_ITEMS = Set.of(
            "potion", "splash_potion", "lingering_potion", "glass_bottle", "nether_wart");
    private static final Set<String> HERBAL_BOTANICAL_ITEMS = Set.of(
            "brown_mushroom", "red_mushroom", "crimson_fungus", "warped_fungus", "crimson_roots", "warped_roots",
            "nether_sprouts", "weeping_vines", "twisting_vines", "sugar", "spider_eye", "glow_berries",
            "kelp", "seagrass", "vine", "moss_block", "moss_carpet", "hanging_roots", "big_dripleaf",
            "small_dripleaf");
    private static final Set<String> SALVAGE_METAL_ITEMS = Set.of(
            "netherite_scrap", "iron_nugget", "gold_nugget", "chain", "rail", "powered_rail", "detector_rail",
            "activator_rail", "iron_bars", "copper_ingot", "iron_ingot", "gold_ingot");
    private static final Set<String> SALVAGE_MATERIAL_ITEMS = Set.of(
            "flint", "gravel", "sand", "red_sand", "glass", "glass_pane", "brick", "bricks", "nether_brick",
            "nether_bricks", "stone", "cobblestone", "mossy_cobblestone", "blackstone", "netherrack", "basalt",
            "calcite", "tuff", "deepslate", "cobbled_deepslate", "stone_bricks", "terracotta");
    private static final Set<String> TAILOR_LEATHER_ITEMS = Set.of("leather", "rabbit_hide", "string");

    private JobCatalogService() {
    }

    private static TagKey<Item> commonTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    public static synchronized void refresh(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Map<String, List<CatalogEntry>> catalogs = new HashMap<>();
        for (JobDefinition definition : JobRegistry.all()) {
            List<CatalogEntry> entries = new ArrayList<>();
            if (JobService.LUMBERJACK_ID.equals(definition.id())) {
                for (LumberjackCatalogService.CatalogEntry entry : LumberjackCatalogService.catalog(server)) {
                    entries.add(new CatalogEntry(entry.itemId(), entry.displayName(), entry.family(),
                            entry.category(), entry.rawUnitsMilli(), entry.xpWeightPercent(),
                            entry.configuredQuoteCents()));
                }
            } else {
                for (Item item : BuiltInRegistries.ITEM) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    if (id == null) {
                        continue;
                    }
                    CatalogEntry entry = classify(definition, item, id);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            entries.sort(Comparator.comparing(CatalogEntry::category)
                    .thenComparing(CatalogEntry::displayName)
                    .thenComparing(entry -> entry.itemId().toString()));
            catalogs.put(definition.id(), List.copyOf(entries));
        }
        CATALOGS.put(server, Map.copyOf(catalogs));
    }

    public static synchronized List<CatalogEntry> catalog(MinecraftServer server, String jobId) {
        if (server == null) {
            return List.of();
        }
        Map<String, List<CatalogEntry>> catalogs = CATALOGS.get(server);
        if (catalogs == null) {
            refresh(server);
            catalogs = CATALOGS.get(server);
        }
        return catalogs == null ? List.of() : catalogs.getOrDefault(normalize(jobId), List.of());
    }

    public static CatalogEntry find(MinecraftServer server, String jobId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId.trim());
        if (id == null) {
            return null;
        }
        return catalog(server, jobId).stream()
                .filter(entry -> entry.itemId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static synchronized void clear(MinecraftServer server) {
        if (server != null) {
            CATALOGS.remove(server);
            MARKET_STATISTICS.remove(server);
        }
    }

    private static ApiShopPriceStatistics regularMarketStatistics(MinecraftServer server, Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (server == null || itemId == null) {
            return ApiShopPriceStatistics.unavailable(itemId == null ? "" : itemId.toString(),
                    ApiShopPriceScope.REGULAR);
        }
        long gameTime = server.overworld() == null ? Long.MIN_VALUE : server.overworld().getGameTime();
        synchronized (MARKET_STATISTICS) {
            MarketStatisticsCache cache = MARKET_STATISTICS.computeIfAbsent(server,
                    ignored -> new MarketStatisticsCache());
            if (cache.gameTime != gameTime) {
                cache.gameTime = gameTime;
                cache.values.clear();
            }
            return cache.values.computeIfAbsent(itemId,
                    ignored -> ShopMarketPriceService.statistics(server, new ItemStack(item),
                            ApiShopPriceScope.REGULAR));
        }
    }

    public static long liveQuoteCents(MinecraftServer server, String jobId, CatalogEntry entry,
                                      JobSavedData data, JobSavedData.JobProgress player, long gameDay) {
        if (entry == null || entry.configuredQuoteCents() <= 0L) {
            return 0L;
        }
        if (JobService.LUMBERJACK_ID.equals(normalize(jobId))) {
            LumberjackCatalogService.CatalogEntry lumberjackEntry = new LumberjackCatalogService.CatalogEntry(
                    entry.itemId(), entry.displayName(), entry.family(), entry.category(),
                    entry.rawUnitsMilli(), entry.xpWeightPercent(), entry.configuredQuoteCents());
            return LumberjackCatalogService.liveQuoteCents(server, lumberjackEntry, data, player, gameDay);
        }

        double marketAnchor = 1.0D;
        Item item = BuiltInRegistries.ITEM.get(entry.itemId());
        if (server != null && item != null) {
            ApiShopPriceStatistics statistics = regularMarketStatistics(server, item);
            if (statistics.available() && statistics.sampleCount() >= Config.LUMBERJACK_MARKET_SAMPLE_MINIMUM.get()) {
                double ratio = Math.max(0L, statistics.medianPriceCents())
                        / (double) Math.max(1L, entry.configuredQuoteCents());
                marketAnchor = (1.0D + clamp(ratio, 0.50D, 2.00D)) / 2.0D;
            }
        }

        double levelBonus = player == null ? 1.0D
                : 1.0D + JobProgression.payoutBonusPercent(player.level()) / 100.0D;
        double spotlight = data != null && entry.family().equalsIgnoreCase(data.moneySpotlightFamily())
                ? 1.0D + data.moneySpotlightBonusPercent() / 100.0D : 1.0D;
        double supply = 1.0D;
        if (data != null) {
            long rolling = data.rollingSupply(gameDay, supplyKey(jobId, entry.family()));
            double target = Math.max(1.0D, 25_000.0D);
            supply = clamp(1.0D + ((target - rolling) / target) * 0.25D, 0.75D, 1.25D);
        }
        double quote = entry.configuredQuoteCents() * marketAnchor * levelBonus * supply * spotlight;
        if (!Double.isFinite(quote) || quote <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.min(Long.MAX_VALUE, (long) Math.floor(quote)));
    }

    /** Returns the activity counter used by the world-action event bridge for a job. */
    public static String blockActivityMetric(String jobId, BlockState state) {
        if (state == null) {
            return "";
        }
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty()) {
            return "";
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        String id = normalize(jobId);
        if (JobService.LUMBERJACK_ID.equals(id)) {
            return LumberjackCatalogService.rawWoodFamily(state).isBlank() ? "" : "logs_broken";
        }
        return switch (id) {
            case JobService.MINER_ID -> isMinerMaterial(stack, path) ? "blocks_mined" : "";
            case JobService.FARMER_ID -> isFarmMaterial(stack, path) ? "crops_harvested" : "";
            case JobService.HERBALIST_ID -> isHerbalMaterial(stack, path) ? "plants_gathered" : "";
            case JobService.SALVAGER_ID -> isSalvageMaterial(stack) ? "scrap_recovered" : "";
            default -> "";
        };
    }

    /** Returns the career counter associated with a crafted output for the active job. */
    public static String craftActivityMetric(MinecraftServer server, String jobId, ItemStack crafted) {
        if (server == null || crafted == null || crafted.isEmpty()) return "";
        String id = normalize(jobId);
        if (!List.of(JobService.BLACKSMITH_ID, JobService.CHEF_ID, JobService.TAILOR_ID).contains(id)) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(crafted.getItem());
        if (itemId == null || find(server, id, itemId.toString()) == null) return "";
        return switch (id) {
            case JobService.BLACKSMITH_ID -> "items_forged";
            case JobService.CHEF_ID -> "meals_prepared";
            case JobService.TAILOR_ID -> "items_stitched";
            default -> "";
        };
    }

    private static CatalogEntry classify(JobDefinition definition, Item item, ResourceLocation id) {
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(item);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String category = switch (definition.id()) {
            case JobService.MINER_ID -> minerCategory(stack, path);
            case JobService.FISHERMAN_ID -> fishermanCategory(stack, path);
            case JobService.FARMER_ID -> farmerCategory(stack, path);
            case JobService.HUNTER_ID -> hunterCategory(stack, path);
            case JobService.BLACKSMITH_ID -> blacksmithCategory(stack, path);
            case JobService.COURIER_ID -> courierCategory(path);
            case JobService.CHEF_ID -> chefCategory(stack, path);
            case JobService.HERBALIST_ID -> herbalistCategory(stack, path);
            case JobService.SALVAGER_ID -> salvagerCategory(stack, path);
            case JobService.TAILOR_ID -> tailorCategory(stack, path);
            default -> null;
        };
        if (category == null) {
            return null;
        }
        int units = unitsFor(category);
        int xpWeight = xpWeightFor(category);
        long quote = Math.max(1L, (long) Math.floor(definition.baseValueCents()
                * (units / 1000.0D) * categoryMultiplier(category)));
        return new CatalogEntry(id, stack.getHoverName().getString(), familyFor(path), category,
                units, xpWeight, quote);
    }

    private static String minerCategory(ItemStack stack, String ignoredPath) {
        if (!isMinerMaterial(stack)) return null;
        return isMinerRefined(stack) ? "refined bars" : isMinerOre(stack) ? "ores" : "stone";
    }

    private static String fishermanCategory(ItemStack stack, String ignoredPath) {
        if (!isFish(stack)) return null;
        if (stack.is(COMMON_SMOKED_FISHES)) return "smoked catch";
        return isCookedFish(stack) ? "cooked catch" : "fresh catch";
    }

    private static String farmerCategory(ItemStack stack, String ignoredPath) {
        if (!isFarmMaterial(stack)) return null;
        if (isFarmSeed(stack)) return "seeds";
        if (isFarmGrain(stack)) return "grain";
        if (isFarmLivestock(stack)) return "livestock";
        return "produce";
    }

    private static String hunterCategory(ItemStack stack, String ignoredPath) {
        if (!isHunterDrop(stack)) return null;
        return isHunterTrophy(stack) ? "trophies"
                : isHunterHide(stack) ? "hides and pelts" : "field drops";
    }

    private static String blacksmithCategory(ItemStack stack, String ignoredPath) {
        if (!isBlacksmithMaterial(stack)) return null;
        return isBlacksmithEquipment(stack) ? "equipment" : "metal stock";
    }

    private static String courierCategory(String path) {
        // Courier inventory is issued by the dispatcher and delivered to NPCs; it is not a sell desk.
        return null;
    }

    private static String chefCategory(ItemStack stack, String ignoredPath) {
        if (!isFood(stack)) {
            return null;
        }
        return isPreparedFood(stack) ? "prepared meals" : "ingredients";
    }

    private static String herbalistCategory(ItemStack stack, String ignoredPath) {
        if (!isHerbalMaterial(stack)) {
            return null;
        }
        return isHerbalRemedy(stack) ? "remedies" : "botanicals";
    }

    private static String salvagerCategory(ItemStack stack, String ignoredPath) {
        if (!isSalvageMaterial(stack)) return null;
        return isMetalSalvage(stack) ? "metal salvage" : "reclaimed material";
    }

    private static String tailorCategory(ItemStack stack, String ignoredPath) {
        if (!isTailorMaterial(stack)) {
            return null;
        }
        return isTailorLeatherwork(stack) ? "leatherwork" : "textiles";
    }

    private static boolean isMinerMaterial(ItemStack stack, String ignoredPath) {
        return isMinerMaterial(stack);
    }

    private static boolean isMinerMaterial(ItemStack stack) {
        return isMinerOre(stack) || isMinerRefined(stack) || isMinerStone(stack);
    }

    private static boolean isMinerOre(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(ItemTags.COAL_ORES) || stack.is(ItemTags.IRON_ORES)
                || stack.is(ItemTags.COPPER_ORES) || stack.is(ItemTags.GOLD_ORES)
                || stack.is(ItemTags.REDSTONE_ORES) || stack.is(ItemTags.EMERALD_ORES)
                || stack.is(ItemTags.LAPIS_ORES) || stack.is(ItemTags.DIAMOND_ORES)
                || stack.is(COMMON_ORES) || stack.is(COMMON_RAW_MATERIALS) || stack.is(COMMON_RAW_ORES)
                || isExactVanillaItem(stack, MINER_ORE_ITEMS) || isExactVanillaItem(stack, MINER_RAW_ITEMS));
    }

    private static boolean isMinerRefined(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_INGOTS) || stack.is(COMMON_GEMS)
                || isExactVanillaItem(stack, MINER_REFINED_ITEMS));
    }

    private static boolean isMinerStone(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_BRICKS)
                || stack.is(ItemTags.SAND) || stack.is(COMMON_STONES) || stack.is(COMMON_STONE)
                || blockHasTag(stack, BlockTags.BASE_STONE_OVERWORLD)
                || blockHasTag(stack, BlockTags.BASE_STONE_NETHER)
                || isExactVanillaItem(stack, MINER_STONE_ITEMS));
    }

    private static boolean isFarmMaterial(ItemStack stack, String ignoredPath) {
        return isFarmMaterial(stack);
    }

    private static boolean isFarmMaterial(ItemStack stack) {
        return isFarmSeed(stack) || isFarmGrain(stack) || isFarmLivestock(stack) || isFarmProduce(stack);
    }

    private static boolean isFarmSeed(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_SEEDS) || stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
                || isExactVanillaItem(stack, FARM_SEED_ITEMS));
    }

    private static boolean isFarmGrain(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_CROPS) || blockHasTag(stack, BlockTags.CROPS)
                || isExactVanillaItem(stack, FARM_GRAIN_ITEMS));
    }

    private static boolean isFarmLivestock(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_LIVESTOCK) || stack.is(COMMON_MEAT)
                || isExactVanillaItem(stack, FARM_LIVESTOCK_ITEMS));
    }

    private static boolean isFarmProduce(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_PRODUCE) || isExactVanillaItem(stack, FARM_PRODUCE_ITEMS));
    }

    private static boolean isFish(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(ItemTags.FISHES) || stack.is(COMMON_FISHES)
                || stack.is(COMMON_RAW_FISHES) || stack.is(COMMON_COOKED_FISHES)
                || stack.is(COMMON_SMOKED_FISHES) || isExactVanillaItem(stack, RAW_FISH_ITEMS)
                || isExactVanillaItem(stack, COOKED_FISH_ITEMS));
    }

    private static boolean isCookedFish(ItemStack stack) {
        return stack.is(COMMON_COOKED_FISHES) || isExactVanillaItem(stack, COOKED_FISH_ITEMS);
    }

    private static boolean isHunterDrop(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_MOB_DROPS) || stack.is(COMMON_TROPHIES) || stack.is(COMMON_HIDES)
                || stack.is(ItemTags.ARROWS) || stack.is(ItemTags.SKULLS)
                || isExactVanillaItem(stack, HUNTER_DROP_ITEMS)
                || isExactVanillaItem(stack, HUNTER_TROPHY_ITEMS)
                || isExactVanillaItem(stack, HUNTER_HIDE_ITEMS));
    }

    private static boolean isHunterTrophy(ItemStack stack) {
        return stack.is(COMMON_TROPHIES) || stack.is(ItemTags.ARROWS) || stack.is(ItemTags.SKULLS)
                || isExactVanillaItem(stack, HUNTER_TROPHY_ITEMS);
    }

    private static boolean isHunterHide(ItemStack stack) {
        return stack.is(COMMON_HIDES) || isExactVanillaItem(stack, HUNTER_HIDE_ITEMS);
    }

    private static boolean isBlacksmithMaterial(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (isBlacksmithEquipment(stack) || stack.is(COMMON_METAL_STOCK)
                || stack.is(COMMON_INGOTS) || stack.is(COMMON_GEMS) || stack.is(COMMON_ORES)
                || stack.is(ItemTags.RAILS) || stack.is(ItemTags.ANVIL)
                || isExactVanillaItem(stack, BLACKSMITH_STOCK_ITEMS));
    }

    private static boolean isBlacksmithEquipment(ItemStack stack) {
        return stack.get(DataComponents.TOOL) != null
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.ARMOR_ENCHANTABLE) || stack.is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.is(ItemTags.BOW_ENCHANTABLE) || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
                || stack.is(ItemTags.TRIDENT_ENCHANTABLE) || stack.is(ItemTags.MACE_ENCHANTABLE);
    }

    private static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.get(DataComponents.FOOD) != null || stack.is(COMMON_FOODS));
    }

    private static boolean isPreparedFood(ItemStack stack) {
        return stack.is(COMMON_PREPARED_FOODS) || isExactVanillaItem(stack, PREPARED_FOOD_ITEMS);
    }

    private static boolean isHerbalRemedy(ItemStack stack) {
        return stack.get(DataComponents.POTION_CONTENTS) != null || stack.is(COMMON_REMEDIES)
                || isExactVanillaItem(stack, HERBAL_REMEDY_ITEMS);
    }

    private static boolean isMetalSalvage(ItemStack stack) {
        return stack.is(COMMON_SCRAP) || stack.is(ItemTags.RAILS) || stack.is(ItemTags.ANVIL)
                || stack.is(COMMON_INGOTS) || stack.is(COMMON_ORES) || stack.is(COMMON_GEMS)
                || isExactVanillaItem(stack, SALVAGE_METAL_ITEMS);
    }

    private static boolean isTailorMaterial(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof DyeItem || stack.is(ItemTags.WOOL)
                || stack.is(COMMON_WOOL) || stack.is(ItemTags.WOOL_CARPETS) || stack.is(COMMON_CARPETS)
                || stack.is(ItemTags.BANNERS) || stack.is(COMMON_DYES)
                || stack.is(ItemTags.DYEABLE) || stack.is(COMMON_TEXTILES) || stack.is(COMMON_LEATHER)
                || stack.is(COMMON_STRINGS) || isExactVanillaItem(stack, TAILOR_LEATHER_ITEMS));
    }

    private static boolean isTailorLeatherwork(ItemStack stack) {
        return stack.is(COMMON_LEATHER) || stack.is(ItemTags.DYEABLE)
                || isExactVanillaItem(stack, TAILOR_LEATHER_ITEMS);
    }

    private static boolean blockHasTag(ItemStack stack, TagKey<Block> tag) {
        if (stack == null || stack.isEmpty() || tag == null || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock().defaultBlockState().is(tag);
    }

    private static boolean isExactVanillaItem(ItemStack stack, Set<String> paths) {
        if (stack == null || stack.isEmpty() || paths == null || paths.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "minecraft".equals(id.getNamespace()) && paths.contains(id.getPath());
    }

    private static boolean isHerbalMaterial(ItemStack stack, String ignoredPath) {
        return isHerbalMaterial(stack);
    }

    private static boolean isHerbalMaterial(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(ItemTags.FLOWERS) || stack.is(COMMON_FLOWERS)
                || stack.is(ItemTags.LEAVES) || stack.is(COMMON_LEAVES)
                || stack.is(ItemTags.SAPLINGS) || stack.is(COMMON_SAPLINGS)
                || stack.is(COMMON_BOTANICALS) || isExactVanillaItem(stack, HERBAL_BOTANICAL_ITEMS)
                || stack.get(DataComponents.POTION_CONTENTS) != null
                || stack.is(COMMON_REMEDIES) || isExactVanillaItem(stack, HERBAL_REMEDY_ITEMS));
    }

    private static boolean isSalvageMaterial(ItemStack stack, String ignoredPath) {
        return isSalvageMaterial(stack);
    }

    private static boolean isSalvageMaterial(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(COMMON_SALVAGE) || stack.is(COMMON_SCRAP)
                || stack.is(ItemTags.RAILS) || stack.is(ItemTags.SAND)
                || stack.is(ItemTags.SMELTS_TO_GLASS) || stack.is(ItemTags.STONE_CRAFTING_MATERIALS)
                || stack.is(ItemTags.STONE_BRICKS) || stack.is(ItemTags.ANVIL)
                || stack.is(COMMON_INGOTS) || stack.is(COMMON_ORES)
                || stack.is(COMMON_GEMS) || isExactVanillaItem(stack, SALVAGE_MATERIAL_ITEMS)
                || isExactVanillaItem(stack, SALVAGE_METAL_ITEMS));
    }

    private static int unitsFor(String category) {
        return switch (category) {
            case "fresh catch", "ingredients", "produce", "livestock", "botanicals", "parcel supplies",
                    "reclaimed material" -> 500;
            case "smoked catch", "cooked catch", "prepared meals", "route documents", "textiles" -> 750;
            case "seeds", "grain", "field drops", "hides and pelts", "trophies", "metal salvage", "metal stock" -> 1000;
            case "equipment", "leatherwork", "remedies", "ores" -> 1500;
            case "refined bars" -> 1750;
            default -> 1000;
        };
    }

    private static int xpWeightFor(String category) {
        return switch (category) {
            case "equipment", "remedies", "prepared meals", "leatherwork", "refined bars" -> 125;
            case "ores", "trophies", "route documents", "smoked catch" -> 115;
            default -> 100;
        };
    }

    private static double categoryMultiplier(String category) {
        return switch (category) {
            case "equipment", "remedies", "prepared meals", "leatherwork", "refined bars" -> 1.25D;
            case "trophies", "route documents", "ores", "smoked catch" -> 1.15D;
            case "cooked catch", "textiles", "grain", "livestock" -> 1.10D;
            default -> 1.0D;
        };
    }

    private static String familyFor(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('_');
        if (separator <= 0) {
            return normalized.isBlank() ? "general" : normalized;
        }
        return normalized.substring(0, separator);
    }

    private static String supplyKey(String jobId, String family) {
        return normalize(jobId) + ":" + (family == null ? "" : family.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class MarketStatisticsCache {
        private long gameTime = Long.MIN_VALUE;
        private final Map<ResourceLocation, ApiShopPriceStatistics> values = new HashMap<>();
    }

    public record CatalogEntry(ResourceLocation itemId, String displayName, String family, String category,
                               int rawUnitsMilli, int xpWeightPercent, long configuredQuoteCents) {
        public CatalogEntry {
            itemId = itemId == null ? ResourceLocation.withDefaultNamespace("air") : itemId;
            displayName = displayName == null || displayName.isBlank() ? itemId.toString() : displayName;
            family = family == null ? "general" : family;
            category = category == null ? "general" : category;
            rawUnitsMilli = Math.max(1, rawUnitsMilli);
            xpWeightPercent = Math.max(1, xpWeightPercent);
            configuredQuoteCents = Math.max(1L, configuredQuoteCents);
        }
    }
}
