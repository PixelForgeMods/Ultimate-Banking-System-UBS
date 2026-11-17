package net.austizz.ultimatebankingsystem.npc.skin;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** The server-managed NPC presentation roles that can receive a shared skin. */
public enum NpcSkinTarget {
    JOB_MARKET("job_market", "Job Market", "textures/entity/bank_teller_male.png", false),
    LUMBERJACK_BUYER("lumberjack_buyer", "Lumberjack Buyer", "textures/entity/cashier.png", false),
    MINER_BUYER("miner_buyer", "Miner Buyer", "textures/entity/cashier.png", false),
    FISHERMAN_BUYER("fisherman_buyer", "Fisherman Buyer", "textures/entity/cashier.png", false),
    FARMER_BUYER("farmer_buyer", "Farmer Buyer", "textures/entity/cashier.png", false),
    HUNTER_BUYER("hunter_buyer", "Hunter Buyer", "textures/entity/cashier.png", false),
    BLACKSMITH_BUYER("blacksmith_buyer", "Blacksmith Buyer", "textures/entity/cashier.png", false),
    COURIER_BUYER("courier_buyer", "Courier Dispatcher", "textures/entity/cashier.png", false),
    CHEF_BUYER("chef_buyer", "Chef Buyer", "textures/entity/cashier.png", false),
    HERBALIST_BUYER("herbalist_buyer", "Herbalist Buyer", "textures/entity/cashier.png", false),
    SALVAGER_BUYER("salvager_buyer", "Salvager Buyer", "textures/entity/cashier.png", false),
    TAILOR_BUYER("tailor_buyer", "Tailor Buyer", "textures/entity/cashier.png", false),
    SHOP_CASHIER("shop_cashier", "Shop Cashier", "textures/entity/cashier.png", false),
    BANK_TELLER_MALE("bank_teller_male", "Bank Teller: Male", "textures/entity/bank_teller_male.png", false),
    BANK_TELLER_FEMALE("bank_teller_female", "Bank Teller: Female", "textures/entity/bank_teller_female.png", true);

    private final String id;
    private final String displayName;
    private final String defaultTexturePath;
    private final boolean defaultSlimModel;

    NpcSkinTarget(String id, String displayName, String defaultTexturePath, boolean defaultSlimModel) {
        this.id = id;
        this.displayName = displayName;
        this.defaultTexturePath = defaultTexturePath;
        this.defaultSlimModel = defaultSlimModel;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ResourceLocation defaultTexture() {
        return ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, defaultTexturePath);
    }

    public boolean defaultSlimModel() {
        return defaultSlimModel;
    }

    public static NpcSkinTarget byId(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (NpcSkinTarget target : values()) {
            if (target.id.equals(normalized)) return target;
        }
        return null;
    }

    public static NpcSkinTarget forJob(String jobId) {
        if (jobId == null) return LUMBERJACK_BUYER;
        return switch (jobId.trim().toLowerCase(Locale.ROOT)) {
            case "miner" -> MINER_BUYER;
            case "fisherman" -> FISHERMAN_BUYER;
            case "farmer" -> FARMER_BUYER;
            case "hunter" -> HUNTER_BUYER;
            case "blacksmith" -> BLACKSMITH_BUYER;
            case "courier" -> COURIER_BUYER;
            case "chef" -> CHEF_BUYER;
            case "herbalist" -> HERBALIST_BUYER;
            case "salvager" -> SALVAGER_BUYER;
            case "tailor" -> TAILOR_BUYER;
            default -> LUMBERJACK_BUYER;
        };
    }
}
