package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceScope;
import net.austizz.ultimatebankingsystem.api.ApiShopPriceStatistics;
import net.austizz.ultimatebankingsystem.shop.ShopMarketPriceService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Discovers wood products from loaded item tags and keeps a compact quote catalog per server. */
public final class LumberjackCatalogService {
    private static final TagKey<Item> LOGS = ItemTags.LOGS;
    private static final TagKey<Item> PLANKS = ItemTags.PLANKS;
    private static final TagKey<Item> WOODEN_SLABS = ItemTags.WOODEN_SLABS;
    private static final TagKey<Item> WOODEN_STAIRS = ItemTags.WOODEN_STAIRS;
    private static final TagKey<Item> WOODEN_FENCES = ItemTags.WOODEN_FENCES;
    private static final TagKey<Item> FENCE_GATES = ItemTags.FENCE_GATES;
    private static final TagKey<Item> WOODEN_DOORS = ItemTags.WOODEN_DOORS;
    private static final TagKey<Item> WOODEN_TRAPDOORS = ItemTags.WOODEN_TRAPDOORS;
    private static final TagKey<Item> WOODEN_BUTTONS = ItemTags.WOODEN_BUTTONS;
    private static final TagKey<Item> WOODEN_PRESSURE_PLATES = ItemTags.WOODEN_PRESSURE_PLATES;
    private static final TagKey<Item> SIGNS = ItemTags.SIGNS;
    private static final TagKey<Item> HANGING_SIGNS = ItemTags.HANGING_SIGNS;
    private static final TagKey<Item> BOATS = ItemTags.BOATS;
    private static final TagKey<Item> CHEST_BOATS = ItemTags.CHEST_BOATS;
    private static final Set<String> VANILLA_WOOD_FAMILIES = Set.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
            "crimson", "warped", "bamboo");
    private static final List<TagKey<Item>> PRODUCT_TAGS = List.of(
            PLANKS, WOODEN_SLABS, WOODEN_STAIRS, WOODEN_FENCES, FENCE_GATES, WOODEN_DOORS,
            WOODEN_TRAPDOORS, WOODEN_BUTTONS, WOODEN_PRESSURE_PLATES, SIGNS,
            HANGING_SIGNS, BOATS, CHEST_BOATS
    );
    private static final Map<MinecraftServer, List<CatalogEntry>> CATALOGS = new WeakHashMap<>();

    private LumberjackCatalogService() {
    }

    public static synchronized void refresh(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Set<String> woodFamilies = discoverWoodFamilies();
        List<CatalogEntry> entries = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            Category category = classify(new ItemStack(item), id.getPath(), woodFamilies);
            if (category == null) {
                continue;
            }
            String family = familyFor(id.getPath());
            if (family.isBlank()) {
                continue;
            }
            int rawUnitsMilli = category.rawUnitsMilli();
            long configuredQuote = quoteFor(family, category, rawUnitsMilli);
            String displayName = new ItemStack(item).getHoverName().getString();
            entries.add(new CatalogEntry(id, displayName, family, category.id(), rawUnitsMilli,
                    category.xpWeightPercent(), configuredQuote));
        }
        entries.sort(Comparator.comparing(CatalogEntry::family)
                .thenComparing(CatalogEntry::category)
                .thenComparing(entry -> entry.itemId().toString()));
        CATALOGS.put(server, List.copyOf(entries));
    }

    public static synchronized List<CatalogEntry> catalog(MinecraftServer server) {
        if (server == null) {
            return List.of();
        }
        List<CatalogEntry> entries = CATALOGS.get(server);
        if (entries == null) {
            refresh(server);
            entries = CATALOGS.get(server);
        }
        return entries == null ? List.of() : entries;
    }

    public static synchronized void clear(MinecraftServer server) {
        if (server != null) {
            CATALOGS.remove(server);
        }
    }

    public static CatalogEntry find(MinecraftServer server, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId.trim());
        if (id == null) {
            return null;
        }
        for (CatalogEntry entry : catalog(server)) {
            if (entry.itemId().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Returns every discovered raw-material family, including compatible modded wood families. */
    public static List<String> rawWoodFamilies(MinecraftServer server) {
        return catalog(server).stream()
                .filter(entry -> "raw".equals(entry.category()))
                .map(CatalogEntry::family)
                .filter(family -> family != null && !family.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /** Returns a representative raw item for a family so achievement cards can show its real icon. */
    public static String rawItemIdForFamily(MinecraftServer server, String family) {
        if (family == null || family.isBlank()) {
            return "minecraft:oak_log";
        }
        return catalog(server).stream()
                .filter(entry -> "raw".equals(entry.category())
                        && family.equalsIgnoreCase(entry.family()))
                .map(entry -> entry.itemId().toString())
                .findFirst()
                .orElse("minecraft:oak_log");
    }

    /** Resolves a successfully broken raw wood block to the same family used by the sale catalog. */
    public static String rawWoodFamily(BlockState state) {
        if (state == null) {
            return "";
        }
        Item item = state.getBlock().asItem();
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !isRawWood(new ItemStack(item), id.getPath())) {
            return "";
        }
        return familyFor(id.getPath());
    }

    /** Returns the live quote in cents, including market, supply, spotlight, and job modifiers. */
    public static long liveQuoteCents(MinecraftServer server, CatalogEntry entry, JobSavedData data,
                                      JobSavedData.JobProgress player, long gameDay) {
        if (entry == null) {
            return 0L;
        }
        double configured = Math.max(0L, entry.configuredQuoteCents());
        if (configured <= 0.0D) {
            return 0L;
        }

        double marketAnchor = 1.0D;
        Item item = BuiltInRegistries.ITEM.get(entry.itemId());
        if (server != null && item != null) {
            ApiShopPriceStatistics statistics = ShopMarketPriceService.statistics(
                    server, new ItemStack(item), ApiShopPriceScope.REGULAR);
            if (statistics.available() && statistics.sampleCount() >= Config.LUMBERJACK_MARKET_SAMPLE_MINIMUM.get()) {
                double configuredPrice = configured;
                double median = Math.max(0L, statistics.medianPriceCents());
                double ratio = median / configuredPrice;
                marketAnchor = clamp(ratio, 0.50D, 2.00D);
                marketAnchor = (1.0D + marketAnchor) / 2.0D;
            }
        }

        double supply = 1.0D;
        if (data != null) {
            long rolling = data.rollingSupply(gameDay, entry.family());
            double target = Math.max(1.0D, Config.LUMBERJACK_SUPPLY_TARGET_UNITS.get() * 1000.0D);
            supply = clamp(1.0D + ((target - rolling) / target) * 0.25D, 0.75D, 1.25D);
        }

        double levelBonus = 1.0D;
        if (player != null) {
            levelBonus += JobProgression.payoutBonusPercent(player.level()) / 100.0D;
        }

        double spotlight = 1.0D;
        if (data != null && entry.family().equals(data.moneySpotlightFamily())) {
            spotlight += data.moneySpotlightBonusPercent() / 100.0D;
        }

        double quote = configured * marketAnchor * supply * levelBonus * spotlight;
        quote = Math.min(configured * 3.0D, quote);
        if (!Double.isFinite(quote) || quote <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.min(Long.MAX_VALUE, (long) Math.floor(quote)));
    }

    private static long quoteFor(String family, Category category, int rawUnitsMilli) {
        double rarity = familyRarityMultiplier(family);
        double premium = category.premiumPercent() / 100.0D;
        double quote = Config.LUMBERJACK_RAW_LOG_VALUE_CENTS.get() * (rawUnitsMilli / 1000.0D)
                * rarity * (1.0D + premium);
        return Math.max(1L, Math.min(Long.MAX_VALUE, (long) Math.floor(quote)));
    }

    private static Category classify(ItemStack stack, String rawPath) {
        return classify(stack, rawPath, discoverWoodFamilies());
    }

    private static Category classify(ItemStack stack, String rawPath, Set<String> woodFamilies) {
        String path = registryPath(stack, rawPath);
        if (isRawWood(stack, path)) {
            return Category.RAW;
        }
        String family = familyFor(path);
        if (family.isBlank() || woodFamilies == null || !woodFamilies.contains(family)) {
            return null;
        }
        boolean taggedProduct = PRODUCT_TAGS.stream().anyMatch(stack::is);
        Category vanillaProduct = vanillaProductCategory(stack);
        if (!taggedProduct && vanillaProduct == null) {
            return null;
        }
        if (stack.is(CHEST_BOATS)) return Category.CHEST_BOAT;
        if (stack.is(BOATS)) return Category.BOAT;
        if (stack.is(HANGING_SIGNS)) return Category.HANGING_SIGN;
        if (stack.is(SIGNS)) return Category.SIGN;
        if (stack.is(WOODEN_SLABS)) return Category.SLAB;
        if (stack.is(WOODEN_STAIRS)) return Category.STAIRS;
        if (stack.is(FENCE_GATES)) return Category.FENCE_GATE;
        if (stack.is(WOODEN_FENCES)) return Category.FENCE;
        if (stack.is(WOODEN_DOORS)) return Category.DOOR;
        if (stack.is(WOODEN_TRAPDOORS)) return Category.TRAPDOOR;
        if (stack.is(WOODEN_BUTTONS)) return Category.BUTTON;
        if (stack.is(WOODEN_PRESSURE_PLATES)) return Category.PRESSURE_PLATE;
        if (stack.is(PLANKS)) return Category.PLANKS;
        return vanillaProduct;
    }

    /**
     * Builds the accepted product families from actual tree materials, so an item name alone
     * cannot accidentally admit stone or metal building blocks.
     */
    private static Set<String> discoverWoodFamilies() {
        Set<String> families = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            String path = id.getPath();
            if (!isRawWood(new ItemStack(item), path)) {
                continue;
            }
            String family = familyFor(path);
            if (!family.isBlank()) {
                families.add(family);
            }
        }
        return families;
    }

    private static boolean isRawWood(ItemStack stack, String ignoredRawPath) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(LOGS)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "minecraft".equals(id.getNamespace()) && isVanillaRawWoodPath(id.getPath());
    }

    private static String registryPath(ItemStack stack, String fallback) {
        if (stack != null && !stack.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                return id.getPath().toLowerCase(Locale.ROOT);
            }
        }
        return fallback == null ? "" : fallback.toLowerCase(Locale.ROOT);
    }

    private static boolean isVanillaRawWoodPath(String path) {
        for (String family : VANILLA_WOOD_FAMILIES) {
            if (path.equals(family + "_log") || path.equals("stripped_" + family + "_log")
                    || path.equals(family + "_wood") || path.equals("stripped_" + family + "_wood")
                    || path.equals(family + "_stem") || path.equals(family + "_hyphae")
                    || path.equals(family + "_block") || path.equals("stripped_" + family + "_block")) {
                return true;
            }
        }
        return false;
    }

    private static Category vanillaProductCategory(ItemStack stack) {
        ResourceLocation id = stack == null || stack.isEmpty()
                ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return null;
        }
        String path = id.getPath();
        for (String family : VANILLA_WOOD_FAMILIES) {
            if (path.equals(family + "_planks")) return Category.PLANKS;
            if (path.equals(family + "_slab")) return Category.SLAB;
            if (path.equals(family + "_stairs")) return Category.STAIRS;
            if (path.equals(family + "_fence_gate")) return Category.FENCE_GATE;
            if (path.equals(family + "_fence")) return Category.FENCE;
            if (path.equals(family + "_door")) return Category.DOOR;
            if (path.equals(family + "_trapdoor")) return Category.TRAPDOOR;
            if (path.equals(family + "_button")) return Category.BUTTON;
            if (path.equals(family + "_pressure_plate")) return Category.PRESSURE_PLATE;
            if (path.equals(family + "_sign")) return Category.SIGN;
            if (path.equals(family + "_hanging_sign")) return Category.HANGING_SIGN;
            if (path.equals(family + "_boat")) return Category.BOAT;
            if (path.equals(family + "_chest_boat")) return Category.CHEST_BOAT;
        }
        return null;
    }

    private static String familyFor(String rawPath) {
        String path = rawPath == null ? "" : rawPath.toLowerCase(Locale.ROOT);
        path = stripPrefix(path, "stripped_");
        path = stripPrefix(path, "waxed_");
        String[] suffixes = {"_chest_boat", "_hanging_sign", "_pressure_plate", "_trapdoor", "_planks",
                "_stairs", "_fence_gate", "_fence", "_button", "_door", "_slab", "_sign", "_boat",
                "_log", "_wood", "_stem", "_hyphae", "_block"};
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        return path.replaceFirst("^stripped_", "").trim();
    }

    private static String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static double familyRarityMultiplier(String family) {
        return switch (family) {
            case "crimson", "warped", "pale_oak" -> 1.20D;
            case "mangrove", "cherry", "bamboo" -> 1.10D;
            default -> 1.00D;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record CatalogEntry(ResourceLocation itemId, String displayName, String family, String category,
                                int rawUnitsMilli, int xpWeightPercent, long configuredQuoteCents) {
        public CatalogEntry {
            itemId = itemId == null ? ResourceLocation.withDefaultNamespace("air") : itemId;
            displayName = displayName == null || displayName.isBlank() ? itemId.toString() : displayName;
            family = family == null ? "" : family;
            category = category == null ? "" : category;
            rawUnitsMilli = Math.max(1, rawUnitsMilli);
            xpWeightPercent = Math.max(1, xpWeightPercent);
            configuredQuoteCents = Math.max(1L, configuredQuoteCents);
        }
    }

    private enum Category {
        RAW("raw", 1000, 0, 100),
        PLANKS("planks", 250, 5, 100),
        SLAB("slabs", 125, 5, 100),
        STAIRS("stairs", 375, 10, 110),
        FENCE("fences", 125, 8, 105),
        FENCE_GATE("fence gates", 250, 8, 105),
        DOOR("doors", 500, 10, 110),
        TRAPDOOR("trapdoors", 500, 10, 110),
        SIGN("signs", 500, 12, 115),
        HANGING_SIGN("hanging signs", 750, 15, 120),
        BOAT("boats", 1250, 12, 120),
        CHEST_BOAT("chest boats", 1500, 15, 125),
        BUTTON("buttons", 63, 5, 95),
        PRESSURE_PLATE("pressure plates", 500, 8, 105);

        private final String id;
        private final int rawUnitsMilli;
        private final int premiumPercent;
        private final int xpWeightPercent;

        Category(String id, int rawUnitsMilli, int premiumPercent, int xpWeightPercent) {
            this.id = id;
            this.rawUnitsMilli = rawUnitsMilli;
            this.premiumPercent = premiumPercent;
            this.xpWeightPercent = xpWeightPercent;
        }

        public String id() {
            return id;
        }

        public int rawUnitsMilli() {
            return rawUnitsMilli;
        }

        public int premiumPercent() {
            return premiumPercent;
        }

        public int xpWeightPercent() {
            return xpWeightPercent;
        }
    }
}
