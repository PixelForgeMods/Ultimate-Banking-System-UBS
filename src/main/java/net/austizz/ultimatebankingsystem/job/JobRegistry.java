package net.austizz.ultimatebankingsystem.job;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small generic registry so future jobs do not need to change the persistence model. */
public final class JobRegistry {
    private static final Map<String, JobDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(new JobDefinition(
                "lumberjack",
                "Lumberjack",
                "Turn harvested wood and finished wood products into UBS account income.",
                "minecraft:iron_axe"
        ));
        register(new JobDefinition(
                "miner", "Miner", "Extract stone and ore for the bank's construction and reserve desks.",
                "minecraft:iron_pickaxe", "Mining", "Miner Catalog", "mining activity", 650));
        register(new JobDefinition(
                "fisherman", "Fisherman", "Bring sustainable catches to the bank's dockside settlement desk.",
                "minecraft:fishing_rod", "Fisherman", "Fisherman Catalog", "fishing activity", 600));
        register(new JobDefinition(
                "farmer", "Farmer", "Grow, harvest, and deliver reliable food and crop supplies.",
                "minecraft:iron_hoe", "Farm", "Farmer Catalog", "farming activity", 550));
        register(new JobDefinition(
                "hunter", "Hunter", "Recover field supplies and trophies from successful expeditions.",
                "minecraft:bow", "Hunter", "Hunter Catalog", "hunting activity", 700));
        register(new JobDefinition(
                "blacksmith", "Blacksmith", "Forge tools, equipment, and metal stock for the bank network.",
                "minecraft:anvil", "Blacksmith", "Blacksmith Catalog", "forging activity", 900));
        register(new JobDefinition(
                "courier", "Courier", "Move documents and parcels through the UBS delivery network.",
                "minecraft:chest", "Courier Dispatcher", "Dispatch Board", "delivery activity", 500));
        register(new JobDefinition(
                "chef", "Chef", "Prepare valuable meals and dependable provisions for bank events.",
                "minecraft:crafting_table", "Chef", "Chef Catalog", "kitchen activity", 650));
        register(new JobDefinition(
                "herbalist", "Herbalist", "Gather botanicals and remedies for the apothecary ledger.",
                "minecraft:brewing_stand", "Herbalist", "Herbalist Catalog", "herbal activity", 600));
        register(new JobDefinition(
                "salvager", "Salvager", "Recover useful materials from abandoned and damaged sites.",
                "minecraft:brush", "Recycler", "Salvager Catalog", "salvage activity", 450));
        register(new JobDefinition(
                "tailor", "Tailor", "Craft textiles and leatherwork for the UBS uniform and trade desk.",
                "minecraft:leather", "Tailor", "Tailor Catalog", "tailoring activity", 700));
    }

    private JobRegistry() {
    }

    public static synchronized JobDefinition register(JobDefinition definition) {
        if (definition == null || definition.id().isBlank()) {
            throw new IllegalArgumentException("A job definition with an id is required.");
        }
        JobDefinition previous = DEFINITIONS.putIfAbsent(definition.id(), definition);
        return previous == null ? definition : previous;
    }

    public static synchronized JobDefinition get(String id) {
        return DEFINITIONS.get(normalize(id));
    }

    public static synchronized Collection<JobDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
