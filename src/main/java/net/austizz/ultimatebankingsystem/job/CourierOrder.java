package net.austizz.ultimatebankingsystem.job;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Persistent NPC-to-NPC route offered to one Courier player. */
public final class CourierOrder {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DELIVERED = "DELIVERED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELED = "CANCELED";
    public static final String NPC_REMOVED = "NPC_REMOVED";

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_ITEM_ID_LENGTH = 160;
    private static final int MAX_CONTRACT_TIER_LENGTH = 32;

    private final UUID id;
    private final UUID originNpcId;
    private final UUID destinationNpcId;
    private final String originDimension;
    private final String destinationDimension;
    private final String originName;
    private final String destinationName;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long rewardCents;
    private final long createdAtMillis;
    private long expiresAtMillis;
    private final int routeDistanceBlocks;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int destinationX;
    private final int destinationY;
    private final int destinationZ;
    private final long marketValueCents;
    private final int moneyMultiplierPercent;
    private final int xpMultiplierPercent;
    private final String contractTier;
    private String status;
    private long acceptedAtMillis;
    private boolean npcRemovalPayoutSettled;

    public CourierOrder(UUID id, UUID originNpcId, UUID destinationNpcId,
                        String originDimension, String destinationDimension,
                        String originName, String destinationName,
                        String itemId, String itemName, int quantity, long rewardCents,
                        long createdAtMillis, long expiresAtMillis, int routeDistanceBlocks,
                        String status, long acceptedAtMillis) {
        this(id, originNpcId, destinationNpcId, originDimension, destinationDimension,
                originName, destinationName, itemId, itemName, quantity, rewardCents,
                createdAtMillis, expiresAtMillis, routeDistanceBlocks,
                0, 0, 0, 0, 0, 0, 0L, 100, 100, "STANDARD", status,
                acceptedAtMillis, false);
    }

    public CourierOrder(UUID id, UUID originNpcId, UUID destinationNpcId,
                        String originDimension, String destinationDimension,
                        String originName, String destinationName,
                        String itemId, String itemName, int quantity, long rewardCents,
                        long createdAtMillis, long expiresAtMillis, int routeDistanceBlocks,
                        int originX, int originY, int originZ,
                        int destinationX, int destinationY, int destinationZ,
                        long marketValueCents, int moneyMultiplierPercent,
                        int xpMultiplierPercent, String contractTier,
                        String status, long acceptedAtMillis,
                        boolean npcRemovalPayoutSettled) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.originNpcId = originNpcId;
        this.destinationNpcId = destinationNpcId;
        this.originDimension = clean(originDimension, 160);
        this.destinationDimension = clean(destinationDimension, 160);
        this.originName = clean(originName, MAX_NAME_LENGTH);
        this.destinationName = clean(destinationName, MAX_NAME_LENGTH);
        this.itemId = clean(itemId, MAX_ITEM_ID_LENGTH);
        this.itemName = clean(itemName, MAX_NAME_LENGTH);
        this.quantity = Math.max(1, Math.min(64, quantity));
        this.rewardCents = Math.max(1L, Math.min(10_000_000L, rewardCents));
        this.createdAtMillis = Math.max(0L, createdAtMillis);
        this.expiresAtMillis = Math.max(this.createdAtMillis, expiresAtMillis);
        this.routeDistanceBlocks = Math.max(-1, Math.min(30_000_000, routeDistanceBlocks));
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.destinationX = destinationX;
        this.destinationY = destinationY;
        this.destinationZ = destinationZ;
        this.marketValueCents = Math.max(0L, Math.min(10_000_000L, marketValueCents));
        this.moneyMultiplierPercent = clampMultiplier(moneyMultiplierPercent);
        this.xpMultiplierPercent = clampMultiplier(xpMultiplierPercent);
        this.contractTier = cleanTier(contractTier);
        this.status = normalizeStatus(status);
        this.acceptedAtMillis = Math.max(0L, acceptedAtMillis);
        this.npcRemovalPayoutSettled = npcRemovalPayoutSettled;
    }

    public static CourierOrder create(UUID originNpcId, UUID destinationNpcId,
                                       String originDimension, String destinationDimension,
                                       String originName, String destinationName,
                                       String itemId, String itemName, int quantity, long rewardCents,
                                       long createdAtMillis, long expiresAtMillis, int routeDistanceBlocks) {
        return new CourierOrder(UUID.randomUUID(), originNpcId, destinationNpcId,
                originDimension, destinationDimension, originName, destinationName,
                itemId, itemName, quantity, rewardCents, createdAtMillis, expiresAtMillis,
                routeDistanceBlocks, AVAILABLE, 0L);
    }

    public static CourierOrder create(UUID originNpcId, UUID destinationNpcId,
                                      String originDimension, String destinationDimension,
                                      String originName, String destinationName,
                                      String itemId, String itemName, int quantity, long rewardCents,
                                      long createdAtMillis, long expiresAtMillis, int routeDistanceBlocks,
                                      int originX, int originY, int originZ,
                                      int destinationX, int destinationY, int destinationZ,
                                      long marketValueCents, int moneyMultiplierPercent,
                                      int xpMultiplierPercent, String contractTier) {
        return new CourierOrder(UUID.randomUUID(), originNpcId, destinationNpcId,
                originDimension, destinationDimension, originName, destinationName,
                itemId, itemName, quantity, rewardCents, createdAtMillis, expiresAtMillis,
                routeDistanceBlocks, originX, originY, originZ, destinationX, destinationY,
                destinationZ, marketValueCents, moneyMultiplierPercent, xpMultiplierPercent,
                contractTier, AVAILABLE, 0L, false);
    }

    public UUID id() {
        return id;
    }

    /** Short player-facing identifier. It is resolved within the owning player's route list. */
    public String shortId() {
        return shortId(id);
    }

    public static String shortId(UUID id) {
        if (id == null) {
            return "";
        }
        return id.toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    public UUID originNpcId() {
        return originNpcId;
    }

    public UUID destinationNpcId() {
        return destinationNpcId;
    }

    public String originDimension() {
        return originDimension;
    }

    public String destinationDimension() {
        return destinationDimension;
    }

    public String originName() {
        return originName;
    }

    public String destinationName() {
        return destinationName;
    }

    public String itemId() {
        return itemId;
    }

    public String itemName() {
        return itemName;
    }

    public int quantity() {
        return quantity;
    }

    public long rewardCents() {
        return rewardCents;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public int routeDistanceBlocks() {
        return routeDistanceBlocks;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int destinationX() {
        return destinationX;
    }

    public int destinationY() {
        return destinationY;
    }

    public int destinationZ() {
        return destinationZ;
    }

    public long marketValueCents() {
        return marketValueCents;
    }

    public int moneyMultiplierPercent() {
        return moneyMultiplierPercent;
    }

    public int xpMultiplierPercent() {
        return xpMultiplierPercent;
    }

    public String contractTier() {
        return contractTier;
    }

    public String status() {
        return status;
    }

    public long acceptedAtMillis() {
        return acceptedAtMillis;
    }

    public boolean npcRemovalPayoutSettled() {
        return npcRemovalPayoutSettled;
    }

    public boolean isAvailable() {
        return AVAILABLE.equals(status);
    }

    public boolean isAccepted() {
        return ACCEPTED.equals(status);
    }

    public boolean isFinished() {
        return DELIVERED.equals(status) || EXPIRED.equals(status)
                || CANCELED.equals(status) || NPC_REMOVED.equals(status);
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis > 0L && nowMillis >= expiresAtMillis;
    }

    public void accept(long nowMillis) {
        accept(nowMillis, 0L);
    }

    /**
     * Accepts an offer and starts its player-facing completion window.
     * A zero lifetime preserves the legacy offer-only behavior for API callers.
     */
    public void accept(long nowMillis, long completionLifetimeMillis) {
        if (!isAvailable()) {
            return;
        }
        status = ACCEPTED;
        acceptedAtMillis = Math.max(0L, nowMillis);
        long lifetime = Math.max(0L, completionLifetimeMillis);
        if (lifetime > 0L && acceptedAtMillis <= Long.MAX_VALUE - lifetime) {
            expiresAtMillis = acceptedAtMillis + lifetime;
        }
    }

    public void deliver() {
        if (isAccepted()) {
            status = DELIVERED;
        }
    }

    public void expire() {
        if (!isFinished()) {
            status = EXPIRED;
        }
    }

    public void cancel() {
        if (!isFinished()) {
            status = CANCELED;
        }
    }

    public void markNpcRemoved() {
        if (!isFinished()) {
            status = NPC_REMOVED;
        }
    }

    public void markNpcRemovalPayoutSettled() {
        npcRemovalPayoutSettled = true;
    }

    public void save(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        tag.putUUID("id", id);
        if (originNpcId != null) tag.putUUID("origin_npc", originNpcId);
        if (destinationNpcId != null) tag.putUUID("destination_npc", destinationNpcId);
        tag.putString("origin_dimension", originDimension);
        tag.putString("destination_dimension", destinationDimension);
        tag.putString("origin_name", originName);
        tag.putString("destination_name", destinationName);
        tag.putString("item_id", itemId);
        tag.putString("item_name", itemName);
        tag.putInt("quantity", quantity);
        tag.putLong("reward_cents", rewardCents);
        tag.putLong("created_at", createdAtMillis);
        tag.putLong("expires_at", expiresAtMillis);
        tag.putInt("distance", routeDistanceBlocks);
        tag.putInt("origin_x", originX);
        tag.putInt("origin_y", originY);
        tag.putInt("origin_z", originZ);
        tag.putInt("destination_x", destinationX);
        tag.putInt("destination_y", destinationY);
        tag.putInt("destination_z", destinationZ);
        tag.putLong("market_value_cents", marketValueCents);
        tag.putInt("money_multiplier_percent", moneyMultiplierPercent);
        tag.putInt("xp_multiplier_percent", xpMultiplierPercent);
        tag.putString("contract_tier", contractTier);
        tag.putString("status", status);
        tag.putLong("accepted_at", acceptedAtMillis);
        tag.putBoolean("npc_removal_payout_settled", npcRemovalPayoutSettled);
    }

    public static CourierOrder load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("id")) {
            return null;
        }
        UUID origin = tag.hasUUID("origin_npc") ? tag.getUUID("origin_npc") : null;
        UUID destination = tag.hasUUID("destination_npc") ? tag.getUUID("destination_npc") : null;
        return new CourierOrder(
                tag.getUUID("id"), origin, destination,
                tag.getString("origin_dimension"), tag.getString("destination_dimension"),
                tag.getString("origin_name"), tag.getString("destination_name"),
                tag.getString("item_id"), tag.getString("item_name"), tag.getInt("quantity"),
                tag.getLong("reward_cents"), tag.getLong("created_at"), tag.getLong("expires_at"),
                tag.getInt("distance"), tag.getInt("origin_x"), tag.getInt("origin_y"),
                tag.getInt("origin_z"), tag.getInt("destination_x"), tag.getInt("destination_y"),
                tag.getInt("destination_z"), tag.getLong("market_value_cents"),
                tag.contains("money_multiplier_percent") ? tag.getInt("money_multiplier_percent") : 100,
                tag.contains("xp_multiplier_percent") ? tag.getInt("xp_multiplier_percent") : 100,
                tag.getString("contract_tier"), tag.getString("status"), tag.getLong("accepted_at"),
                tag.getBoolean("npc_removal_payout_settled"));
    }

    private static String normalizeStatus(String value) {
        if (value == null) return AVAILABLE;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case AVAILABLE, ACCEPTED, DELIVERED, EXPIRED, CANCELED, NPC_REMOVED -> normalized;
            default -> AVAILABLE;
        };
    }

    private static int clampMultiplier(int value) {
        return Math.max(50, Math.min(300, value <= 0 ? 100 : value));
    }

    private static String cleanTier(String value) {
        if (value == null || value.isBlank()) {
            return "STANDARD";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim().toUpperCase(java.util.Locale.ROOT);
        return cleaned.length() <= MAX_CONTRACT_TIER_LENGTH
                ? cleaned : cleaned.substring(0, MAX_CONTRACT_TIER_LENGTH);
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
