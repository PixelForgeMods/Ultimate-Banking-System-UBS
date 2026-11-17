package net.austizz.ultimatebankingsystem.job;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent job progress, market rotation, and bounded supply history. */
public final class JobSavedData extends SavedData {
    public static final String DATA_NAME = "ultimate_banking_system_jobs";
    private static final int HISTORY_DAYS = 7;
    private static final int MAX_FAMILY_ENTRIES = 256;
    private static final int MAX_ACHIEVEMENT_STATES = 512;
    private static final int MAX_COURIER_ORDERS_PER_PLAYER = 64;

    private final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    private final Map<Long, Map<String, Long>> dailyFamilyUnits = new LinkedHashMap<>();
    private long marketDay = Long.MIN_VALUE;
    private String moneySpotlightFamily = "";
    private String xpSpotlightFamily = "";
    private int moneySpotlightBonusPercent;
    private int xpSpotlightBonusPercent;
    private long serverDailyMoneyCents;
    private long serverDailyUnitsMilli;

    public static JobSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            throw new IllegalArgumentException("A running server is required.");
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<JobSavedData> factory() {
        return new SavedData.Factory<>(JobSavedData::new, JobSavedData::load, null);
    }

    public PlayerRecord player(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new PlayerRecord());
    }

    public Map<UUID, PlayerRecord> players() {
        return Map.copyOf(players);
    }

    public long marketDay() {
        return marketDay;
    }

    public void setMarketRotation(long day, String moneyFamily, String xpFamily,
                                  int moneyBonusPercent, int xpBonusPercent) {
        marketDay = day;
        moneySpotlightFamily = safeFamily(moneyFamily);
        xpSpotlightFamily = safeFamily(xpFamily);
        moneySpotlightBonusPercent = clampBonus(moneyBonusPercent);
        xpSpotlightBonusPercent = clampBonus(xpBonusPercent);
        setDirty();
    }

    public String moneySpotlightFamily() {
        return moneySpotlightFamily;
    }

    public String xpSpotlightFamily() {
        return xpSpotlightFamily;
    }

    public int moneySpotlightBonusPercent() {
        return moneySpotlightBonusPercent;
    }

    public int xpSpotlightBonusPercent() {
        return xpSpotlightBonusPercent;
    }

    public long serverDailyMoneyCents() {
        return serverDailyMoneyCents;
    }

    public long serverDailyUnitsMilli() {
        return serverDailyUnitsMilli;
    }

    public void resetDailyTotals(long day) {
        for (PlayerRecord player : players.values()) {
            player.resetForDay(day);
        }
        serverDailyMoneyCents = 0L;
        serverDailyUnitsMilli = 0L;
        setDirty();
    }

    public void addServerDailyMoney(long cents) {
        serverDailyMoneyCents = saturatingAdd(serverDailyMoneyCents, Math.max(0L, cents));
        setDirty();
    }

    public void addServerDailyUnits(long unitsMilli) {
        serverDailyUnitsMilli = saturatingAdd(serverDailyUnitsMilli, Math.max(0L, unitsMilli));
        setDirty();
    }

    public void addSupply(long day, String family, long unitsMilli) {
        if (family == null || family.isBlank() || unitsMilli <= 0L) {
            return;
        }
        Map<String, Long> bucket = dailyFamilyUnits.computeIfAbsent(day, ignored -> new LinkedHashMap<>());
        bucket.merge(safeFamily(family), unitsMilli, JobSavedData::saturatingAdd);
        while (bucket.size() > MAX_FAMILY_ENTRIES) {
            String first = bucket.keySet().iterator().next();
            bucket.remove(first);
        }
        pruneSupply(day);
        setDirty();
    }

    public long rollingSupply(long currentDay, String family) {
        String key = safeFamily(family);
        if (key.isBlank()) {
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<Long, Map<String, Long>> entry : dailyFamilyUnits.entrySet()) {
            if (entry.getKey() >= currentDay - HISTORY_DAYS + 1L
                    && entry.getKey() <= currentDay) {
                total = saturatingAdd(total, entry.getValue().getOrDefault(key, 0L));
            }
        }
        return total;
    }

    public void pruneSupply(long currentDay) {
        dailyFamilyUnits.entrySet().removeIf(entry -> entry.getKey() < currentDay - HISTORY_DAYS + 1L);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("market_day", marketDay);
        tag.putString("money_spotlight", moneySpotlightFamily);
        tag.putString("xp_spotlight", xpSpotlightFamily);
        tag.putInt("money_bonus", moneySpotlightBonusPercent);
        tag.putInt("xp_bonus", xpSpotlightBonusPercent);
        tag.putLong("server_daily_money", serverDailyMoneyCents);
        tag.putLong("server_daily_units", serverDailyUnitsMilli);

        ListTag playerList = new ListTag();
        players.forEach((id, record) -> {
            CompoundTag entry = record.save();
            entry.putUUID("player", id);
            playerList.add(entry);
        });
        tag.put("players", playerList);

        ListTag supplyList = new ListTag();
        dailyFamilyUnits.forEach((day, families) -> {
            CompoundTag dayTag = new CompoundTag();
            dayTag.putLong("day", day);
            ListTag familyList = new ListTag();
            families.forEach((family, units) -> {
                CompoundTag familyTag = new CompoundTag();
                familyTag.putString("family", family);
                familyTag.putLong("units", units);
                familyList.add(familyTag);
            });
            dayTag.put("families", familyList);
            supplyList.add(dayTag);
        });
        tag.put("supply", supplyList);
        return tag;
    }

    private static JobSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        JobSavedData data = new JobSavedData();
        data.marketDay = tag.contains("market_day") ? tag.getLong("market_day") : Long.MIN_VALUE;
        data.moneySpotlightFamily = safeFamily(tag.getString("money_spotlight"));
        data.xpSpotlightFamily = safeFamily(tag.getString("xp_spotlight"));
        data.moneySpotlightBonusPercent = clampBonus(tag.getInt("money_bonus"));
        data.xpSpotlightBonusPercent = clampBonus(tag.getInt("xp_bonus"));
        data.serverDailyMoneyCents = Math.max(0L, tag.getLong("server_daily_money"));
        data.serverDailyUnitsMilli = Math.max(0L, tag.getLong("server_daily_units"));

        ListTag playerList = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag entry = playerList.getCompound(i);
            if (entry.hasUUID("player")) {
                data.players.put(entry.getUUID("player"), PlayerRecord.load(entry));
            }
        }

        ListTag supplyList = tag.getList("supply", Tag.TAG_COMPOUND);
        for (int i = 0; i < supplyList.size(); i++) {
            CompoundTag dayTag = supplyList.getCompound(i);
            long day = dayTag.getLong("day");
            Map<String, Long> families = new LinkedHashMap<>();
            ListTag familyList = dayTag.getList("families", Tag.TAG_COMPOUND);
            for (int j = 0; j < familyList.size(); j++) {
                CompoundTag familyTag = familyList.getCompound(j);
                String family = safeFamily(familyTag.getString("family"));
                if (!family.isBlank()) {
                    families.put(family, Math.max(0L, familyTag.getLong("units")));
                }
            }
            if (!families.isEmpty()) {
                data.dailyFamilyUnits.put(day, families);
            }
        }
        return data;
    }

    private static int clampBonus(int value) {
        return Math.max(0, Math.min(35, value));
    }

    private static String safeFamily(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public static final class PlayerRecord {
        private String activeJob = "";
        private final Map<String, JobProgress> progressByJob = new LinkedHashMap<>();
        private final Map<UUID, CourierOrder> courierOrders = new LinkedHashMap<>();

        public String activeJob() {
            return activeJob;
        }

        public void setActiveJob(String activeJob) {
            this.activeJob = activeJob == null ? "" : activeJob.trim().toLowerCase(java.util.Locale.ROOT);
        }

        /** Returns progress isolated to one job; inactive jobs start at level one without being persisted. */
        public JobProgress progress(String jobId) {
            String normalized = normalizeJobId(jobId);
            if (normalized.isBlank()) {
                return new JobProgress();
            }
            return progressByJob.computeIfAbsent(normalized, ignored -> new JobProgress());
        }

        public Map<String, JobProgress> progressByJob() {
            return Map.copyOf(progressByJob);
        }

        public Map<UUID, CourierOrder> courierOrders() {
            return Map.copyOf(courierOrders);
        }

        public CourierOrder courierOrder(UUID orderId) {
            return orderId == null ? null : courierOrders.get(orderId);
        }

        public void putCourierOrder(CourierOrder order) {
            if (order == null) {
                return;
            }
            courierOrders.put(order.id(), order);
            while (courierOrders.size() > MAX_COURIER_ORDERS_PER_PLAYER) {
                courierOrders.remove(courierOrders.keySet().iterator().next());
            }
        }

        public void removeCourierOrder(UUID orderId) {
            if (orderId != null) {
                courierOrders.remove(orderId);
            }
        }

        public void resetForDay(long day) {
            progressByJob.values().forEach(progress -> progress.resetForDay(day));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("job", activeJob);
            ListTag progressList = new ListTag();
            progressByJob.forEach((jobId, progress) -> {
                CompoundTag entry = progress.save();
                entry.putString("id", jobId);
                progressList.add(entry);
            });
            tag.put("job_progress", progressList);

            // Keep an active-job mirror for older UBS builds that only understand one progress record.
            JobProgress activeProgress = progressByJob.get(activeJob);
            if (activeProgress != null) {
                activeProgress.writeLegacy(tag);
            }

            ListTag courierOrderList = new ListTag();
            courierOrders.values().forEach(order -> {
                CompoundTag orderTag = new CompoundTag();
                order.save(orderTag);
                courierOrderList.add(orderTag);
            });
            tag.put("courier_orders", courierOrderList);
            return tag;
        }

        private static PlayerRecord load(CompoundTag tag) {
            PlayerRecord record = new PlayerRecord();
            record.activeJob = normalizeJobId(tag.getString("job"));
            ListTag progressList = tag.getList("job_progress", Tag.TAG_COMPOUND);
            for (int i = 0; i < progressList.size(); i++) {
                CompoundTag entry = progressList.getCompound(i);
                String jobId = normalizeJobId(entry.getString("id"));
                if (!jobId.isBlank()) {
                    record.progressByJob.put(jobId, JobProgress.load(entry));
                }
            }
            if (record.progressByJob.isEmpty() && !record.activeJob.isBlank()) {
                record.progressByJob.put(record.activeJob, JobProgress.load(tag));
            }
            ListTag courierOrderList = tag.getList("courier_orders", Tag.TAG_COMPOUND);
            for (int i = 0; i < courierOrderList.size() && i < MAX_COURIER_ORDERS_PER_PLAYER; i++) {
                CourierOrder order = CourierOrder.load(courierOrderList.getCompound(i));
                if (order != null) {
                    record.courierOrders.put(order.id(), order);
                }
            }
            return record;
        }

        private static String normalizeJobId(String value) {
            return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Saved payout and level state for one job. */
    public static final class JobProgress {
        private int level = 1;
        private long xp;
        private long dailyMoneyCents;
        private long dailyUnitsMilli;
        private long lastDay = Long.MIN_VALUE;
        private long lifetimeMoneyCents;
        private long lifetimeUnitsMilli;
        private long lifetimeSaleCount;
        private long lifetimeItemCount;
        private long lifetimeFinishedProductUnits;
        private long lifetimeRawLogsBroken;
        private long lifetimeDeliveryCount;
        private long completedDailyQuestCount;
        private long largestSaleCents;
        private final Map<String, Long> familyLogsBroken = new LinkedHashMap<>();
        private final Map<String, Long> familySales = new LinkedHashMap<>();
        private final Map<String, Long> activityMetrics = new LinkedHashMap<>();
        private final Map<String, Long> activityFamilies = new LinkedHashMap<>();
        private long dailyQuestRotationDay = Long.MIN_VALUE;
        private final Map<String, Long> dailyQuestProgressById = new LinkedHashMap<>();
        private final Map<String, AchievementState> achievementStates = new LinkedHashMap<>();

        public int level() {
            return level;
        }

        public long xp() {
            return xp;
        }

        public long dailyMoneyCents() {
            return dailyMoneyCents;
        }

        public long dailyUnitsMilli() {
            return dailyUnitsMilli;
        }

        public long lifetimeMoneyCents() {
            return lifetimeMoneyCents;
        }

        public long lifetimeUnitsMilli() {
            return lifetimeUnitsMilli;
        }

        public long lifetimeSaleCount() {
            return lifetimeSaleCount;
        }

        public long lifetimeItemCount() {
            return lifetimeItemCount;
        }

        public long lifetimeFinishedProductUnits() {
            return lifetimeFinishedProductUnits;
        }

        public long lifetimeRawLogsBroken() {
            return lifetimeRawLogsBroken;
        }

        public long lifetimeDeliveryCount() {
            return lifetimeDeliveryCount;
        }

        public long logsBrokenForFamily(String family) {
            return familyLogsBroken.getOrDefault(normalizeMetricKey(family), 0L);
        }

        public long salesForFamily(String family) {
            return familySales.getOrDefault(normalizeMetricKey(family), 0L);
        }

        public long completedDailyQuestCount() {
            return completedDailyQuestCount;
        }

        public long largestSaleCents() {
            return largestSaleCents;
        }

        public long distinctWoodFamilies() {
            Set<String> families = new LinkedHashSet<>(familyLogsBroken.keySet());
            families.addAll(familySales.keySet());
            families.removeIf(String::isBlank);
            return families.size();
        }

        /** Distinct material families recorded by any job-specific activity or sale. */
        public long distinctFamilies() {
            Set<String> families = new LinkedHashSet<>(familyLogsBroken.keySet());
            families.addAll(familySales.keySet());
            families.addAll(activityFamilies.keySet());
            families.removeIf(String::isBlank);
            return families.size();
        }

        public long activityMetric(String metric) {
            return activityMetrics.getOrDefault(normalizeMetricKey(metric), 0L);
        }

        public Map<String, Long> activityMetrics() {
            return Map.copyOf(activityMetrics);
        }

        public Map<String, AchievementState> achievementStates() {
            return Map.copyOf(achievementStates);
        }

        /** Returns bounded, server-created claim state for one achievement. */
        public AchievementState achievementState(String achievementId) {
            String id = normalizeMetricKey(achievementId);
            if (id.isBlank()) {
                return null;
            }
            AchievementState existing = achievementStates.get(id);
            if (existing != null || achievementStates.size() >= MAX_ACHIEVEMENT_STATES) {
                return existing;
            }
            AchievementState created = new AchievementState();
            achievementStates.put(id, created);
            return created;
        }

        public long dailyQuestRotationDay() {
            return dailyQuestRotationDay;
        }

        public Map<String, Long> dailyQuestProgressById() {
            return Map.copyOf(dailyQuestProgressById);
        }

        /** Creates the current rotation and discards obsolete quest ids without trusting the client. */
        public void ensureDailyQuests(long rotationDay, List<? extends JobQuestDefinition> definitions) {
            if (dailyQuestRotationDay != rotationDay) {
                dailyQuestRotationDay = rotationDay;
                dailyQuestProgressById.clear();
            }
            Set<String> currentIds = new LinkedHashSet<>();
            if (definitions != null) {
                for (JobQuestDefinition definition : definitions) {
                    if (definition == null || definition.id().isBlank()) {
                        continue;
                    }
                    currentIds.add(definition.id());
                    dailyQuestProgressById.putIfAbsent(definition.id(), 0L);
                }
            }
            dailyQuestProgressById.keySet().removeIf(id -> !currentIds.contains(id));
        }

        public long dailyQuestProgress(String questId) {
            if (questId == null || questId.isBlank()) {
                return 0L;
            }
            return Math.max(0L, dailyQuestProgressById.getOrDefault(questId, 0L));
        }

        public void addDailyQuestProgress(String questId, long amount) {
            if (questId == null || questId.isBlank() || amount <= 0L) {
                return;
            }
            dailyQuestProgressById.put(questId,
                    saturatingAdd(dailyQuestProgressById.getOrDefault(questId, 0L), amount));
        }

        public void recordDailyQuestCompletion() {
            completedDailyQuestCount = saturatingAdd(completedDailyQuestCount, 1L);
        }

        public void resetForDay(long day) {
            if (lastDay == day) {
                return;
            }
            lastDay = day;
            dailyMoneyCents = 0L;
            dailyUnitsMilli = 0L;
        }

        public void addSale(long moneyCents, long unitsMilli, long xpGain, int newLevel) {
            dailyMoneyCents = saturatingAdd(dailyMoneyCents, Math.max(0L, moneyCents));
            dailyUnitsMilli = saturatingAdd(dailyUnitsMilli, Math.max(0L, unitsMilli));
            lifetimeMoneyCents = saturatingAdd(lifetimeMoneyCents, Math.max(0L, moneyCents));
            lifetimeUnitsMilli = saturatingAdd(lifetimeUnitsMilli, Math.max(0L, unitsMilli));
            lifetimeSaleCount = saturatingAdd(lifetimeSaleCount, 1L);
            largestSaleCents = Math.max(largestSaleCents, Math.max(0L, moneyCents));
            xp = saturatingAdd(xp, Math.max(0L, xpGain));
            level = Math.max(1, Math.min(100, newLevel));
        }

        /** Adds achievement XP without falsely inflating sales, supply, or daily limits. */
        public void addCareerXp(long xpGain, int newLevel) {
            xp = saturatingAdd(xp, Math.max(0L, xpGain));
            level = Math.max(1, Math.min(100, newLevel));
        }

        /** Records one completed Courier route without counting it as a catalog sale. */
        public void recordDelivery(long moneyCents, long xpGain, int newLevel) {
            long payout = Math.max(0L, moneyCents);
            dailyMoneyCents = saturatingAdd(dailyMoneyCents, payout);
            lifetimeMoneyCents = saturatingAdd(lifetimeMoneyCents, payout);
            lifetimeDeliveryCount = saturatingAdd(lifetimeDeliveryCount, 1L);
            largestSaleCents = Math.max(largestSaleCents, payout);
            xp = saturatingAdd(xp, Math.max(0L, xpGain));
            level = Math.max(1, Math.min(100, newLevel));
        }

        /** Records accepted sale metadata after the sale transaction has succeeded. */
        public void recordSale(String family, String category, long itemCount) {
            long count = Math.max(0L, itemCount);
            if (count <= 0L) {
                return;
            }
            lifetimeItemCount = saturatingAdd(lifetimeItemCount, count);
            String familyKey = normalizeMetricKey(family);
            if (!familyKey.isBlank()) {
                familySales.merge(familyKey, count, JobSavedData::saturatingAdd);
                trimMetricMap(familySales);
            }
            if (category != null && !"raw".equalsIgnoreCase(category.trim())) {
                lifetimeFinishedProductUnits = saturatingAdd(lifetimeFinishedProductUnits, count);
            }
        }

        /** Records one successful non-creative raw wood block break. */
        public void recordRawLogBreak(String family) {
            String familyKey = normalizeMetricKey(family);
            if (familyKey.isBlank()) {
                return;
            }
            lifetimeRawLogsBroken = saturatingAdd(lifetimeRawLogsBroken, 1L);
            familyLogsBroken.merge(familyKey, 1L, JobSavedData::saturatingAdd);
            trimMetricMap(familyLogsBroken);
        }

        /** Records a world activity without changing payout totals or daily sale limits. */
        public void recordActivity(String metric, String family, long amount) {
            String metricKey = normalizeMetricKey(metric);
            long count = Math.max(0L, amount);
            if (metricKey.isBlank() || count <= 0L) {
                return;
            }
            activityMetrics.merge(metricKey, count, JobSavedData::saturatingAdd);
            trimMetricMap(activityMetrics);
            String familyKey = normalizeMetricKey(family);
            if (!familyKey.isBlank()) {
                activityFamilies.merge(familyKey, count, JobSavedData::saturatingAdd);
                trimMetricMap(activityFamilies);
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            writeLegacy(tag);
            return tag;
        }

        private void writeLegacy(CompoundTag tag) {
            tag.putInt("level", level);
            tag.putLong("xp", xp);
            tag.putLong("daily_money", dailyMoneyCents);
            tag.putLong("daily_units", dailyUnitsMilli);
            tag.putLong("last_day", lastDay);
            tag.putLong("lifetime_money", lifetimeMoneyCents);
            tag.putLong("lifetime_units", lifetimeUnitsMilli);
            tag.putLong("lifetime_sales", lifetimeSaleCount);
            tag.putLong("lifetime_items", lifetimeItemCount);
            tag.putLong("lifetime_products", lifetimeFinishedProductUnits);
            tag.putLong("lifetime_raw_logs", lifetimeRawLogsBroken);
            tag.putLong("lifetime_deliveries", lifetimeDeliveryCount);
            tag.putLong("completed_quests", completedDailyQuestCount);
            tag.putLong("largest_sale", largestSaleCents);
            writeMetricMap(tag, "family_logs", "logs", familyLogsBroken);
            writeMetricMap(tag, "family_sales", "sales", familySales);
            writeMetricMap(tag, "activity_metrics", "amount", activityMetrics);
            writeMetricMap(tag, "activity_families", "amount", activityFamilies);
            tag.putLong("daily_quest_day", dailyQuestRotationDay);
            ListTag questList = new ListTag();
            dailyQuestProgressById.forEach((id, progress) -> {
                CompoundTag quest = new CompoundTag();
                quest.putString("id", id);
                quest.putLong("progress", Math.max(0L, progress));
                questList.add(quest);
            });
            tag.put("daily_quests", questList);

            ListTag achievementList = new ListTag();
            achievementStates.forEach((id, state) -> {
                CompoundTag achievement = new CompoundTag();
                achievement.putString("id", id);
                achievement.putBoolean("claimed", state.claimed());
                achievement.putString("reward_item", state.rewardItemId());
                achievement.putInt("reward_count", state.rewardItemCount());
                achievementList.add(achievement);
            });
            tag.put("achievements", achievementList);
        }

        private static JobProgress load(CompoundTag tag) {
            JobProgress progress = new JobProgress();
            progress.level = Math.max(1, Math.min(100, tag.getInt("level")));
            progress.xp = Math.max(0L, tag.getLong("xp"));
            progress.dailyMoneyCents = Math.max(0L, tag.getLong("daily_money"));
            progress.dailyUnitsMilli = Math.max(0L, tag.getLong("daily_units"));
            progress.lastDay = tag.contains("last_day") ? tag.getLong("last_day") : Long.MIN_VALUE;
            progress.lifetimeMoneyCents = Math.max(0L, tag.getLong("lifetime_money"));
            progress.lifetimeUnitsMilli = Math.max(0L, tag.getLong("lifetime_units"));
            progress.lifetimeSaleCount = Math.max(0L, tag.getLong("lifetime_sales"));
            progress.lifetimeItemCount = Math.max(0L, tag.getLong("lifetime_items"));
            progress.lifetimeFinishedProductUnits = Math.max(0L, tag.getLong("lifetime_products"));
            progress.lifetimeRawLogsBroken = Math.max(0L, tag.getLong("lifetime_raw_logs"));
            progress.lifetimeDeliveryCount = Math.max(0L, tag.getLong("lifetime_deliveries"));
            progress.completedDailyQuestCount = Math.max(0L, tag.getLong("completed_quests"));
            progress.largestSaleCents = Math.max(0L, tag.getLong("largest_sale"));
            readMetricMap(tag, "family_logs", "logs", progress.familyLogsBroken);
            readMetricMap(tag, "family_sales", "sales", progress.familySales);
            readMetricMap(tag, "activity_metrics", "amount", progress.activityMetrics);
            readMetricMap(tag, "activity_families", "amount", progress.activityFamilies);
            progress.dailyQuestRotationDay = tag.contains("daily_quest_day")
                    ? tag.getLong("daily_quest_day") : Long.MIN_VALUE;
            ListTag questList = tag.getList("daily_quests", Tag.TAG_COMPOUND);
            for (int i = 0; i < questList.size(); i++) {
                CompoundTag quest = questList.getCompound(i);
                String id = quest.getString("id").trim().toLowerCase(java.util.Locale.ROOT);
                if (!id.isBlank()) {
                    progress.dailyQuestProgressById.put(id, Math.max(0L, quest.getLong("progress")));
                }
            }
            ListTag achievementList = tag.getList("achievements", Tag.TAG_COMPOUND);
            for (int i = 0; i < achievementList.size() && i < MAX_ACHIEVEMENT_STATES; i++) {
                CompoundTag achievement = achievementList.getCompound(i);
                String id = normalizeMetricKey(achievement.getString("id"));
                if (id.isBlank()) {
                    continue;
                }
                AchievementState state = new AchievementState();
                state.claimed = achievement.getBoolean("claimed");
                state.rewardItemId = normalizeRewardItemId(achievement.getString("reward_item"));
                state.rewardItemCount = Math.max(0, Math.min(64, achievement.getInt("reward_count")));
                progress.achievementStates.put(id, state);
            }
            return progress;
        }

        private static void writeMetricMap(CompoundTag parent, String listKey, String valueKey,
                                           Map<String, Long> values) {
            ListTag list = new ListTag();
            values.forEach((key, value) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("key", key);
                entry.putLong(valueKey, Math.max(0L, value));
                list.add(entry);
            });
            parent.put(listKey, list);
        }

        private static void readMetricMap(CompoundTag parent, String listKey, String valueKey,
                                          Map<String, Long> destination) {
            ListTag list = parent.getList(listKey, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && destination.size() < MAX_FAMILY_ENTRIES; i++) {
                CompoundTag entry = list.getCompound(i);
                String key = normalizeMetricKey(entry.getString("key"));
                if (!key.isBlank()) {
                    destination.put(key, Math.max(0L, entry.getLong(valueKey)));
                }
            }
        }

        private static void trimMetricMap(Map<String, Long> values) {
            while (values.size() > MAX_FAMILY_ENTRIES) {
                values.remove(values.keySet().iterator().next());
            }
        }

        private static String normalizeMetricKey(String value) {
            return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        }

        private static String normalizeRewardItemId(String value) {
            return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Persisted claim marker and the rolled item for random rewards. */
    public static final class AchievementState {
        private boolean claimed;
        private String rewardItemId = "";
        private int rewardItemCount;

        public boolean claimed() {
            return claimed;
        }

        public String rewardItemId() {
            return rewardItemId;
        }

        public int rewardItemCount() {
            return rewardItemCount;
        }

        public void setRewardItem(String itemId, int count) {
            rewardItemId = itemId == null ? "" : itemId.trim().toLowerCase(java.util.Locale.ROOT);
            rewardItemCount = Math.max(0, Math.min(64, count));
        }

        public void markClaimed() {
            claimed = true;
        }
    }
}
