package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server-authoritative snapshot used by both the job-market and buyer screens. */
public record JobOpenPayload(
        String mode,
        String jobId,
        String title,
        String description,
        String notice,
        int level,
        long xp,
        long xpToNext,
        long dailyMoneyCents,
        long dailyPayoutCapCents,
        long dailyUnitsMilli,
        long dailyUnitsCapMilli,
        String moneySpotlightFamily,
        String xpSpotlightFamily,
        int moneySpotlightBonusPercent,
        int xpSpotlightBonusPercent,
        boolean active,
        boolean readOnly,
        boolean confirmationRequired,
        String pendingItemId,
        int pendingAmount,
        long pendingQuoteCents,
        List<JobCatalogEntryPayload> catalog,
        List<JobMarketEntryPayload> availableJobs,
        List<JobLevelRoadmapNodePayload> levelRoadmap,
        List<JobAchievementPayload> achievements,
        List<JobDailyQuestPayload> dailyQuests,
        List<JobRewardPayload> rewards,
        long dailyQuestResetEpochSeconds,
        List<CourierOrderPayload> courierOrders
) implements CustomPacketPayload {
    /** Keeps the catalog packet bounded while supporting modded registries. */
    public static final int MAX_CATALOG_ENTRIES = 1024;
    public static final int MAX_AVAILABLE_JOBS = 64;
    public static final int MAX_LEVEL_ROADMAP_NODES = 100;
    public static final int MAX_ACHIEVEMENTS = 512;
    public static final int MAX_DAILY_QUESTS = 3;
    public static final int MAX_REWARDS = 20;
    public static final int MAX_COURIER_ORDERS = 12;

    public static final Type<JobOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "job_open"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);

    public JobOpenPayload {
        mode = mode == null ? "MARKET" : mode;
        jobId = jobId == null ? "" : jobId;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        notice = notice == null ? "" : notice;
        moneySpotlightFamily = moneySpotlightFamily == null ? "" : moneySpotlightFamily;
        xpSpotlightFamily = xpSpotlightFamily == null ? "" : xpSpotlightFamily;
        pendingItemId = pendingItemId == null ? "" : pendingItemId;
        catalog = catalog == null ? List.of() : catalog.stream().limit(MAX_CATALOG_ENTRIES).toList();
        availableJobs = availableJobs == null ? List.of() : availableJobs.stream()
                .limit(MAX_AVAILABLE_JOBS).toList();
        levelRoadmap = levelRoadmap == null ? List.of() : levelRoadmap.stream()
                .limit(MAX_LEVEL_ROADMAP_NODES).toList();
        achievements = achievements == null ? List.of() : achievements.stream()
                .limit(MAX_ACHIEVEMENTS).toList();
        dailyQuests = dailyQuests == null ? List.of() : dailyQuests.stream()
                .limit(MAX_DAILY_QUESTS).toList();
        rewards = rewards == null ? List.of() : rewards.stream().limit(MAX_REWARDS).toList();
        dailyQuestResetEpochSeconds = Math.max(0L, dailyQuestResetEpochSeconds);
        courierOrders = courierOrders == null ? List.of() : courierOrders.stream()
                .limit(MAX_COURIER_ORDERS).toList();
    }

    /** Compatibility constructor for integrations compiled against the pre-quest job snapshot. */
    public JobOpenPayload(
            String mode,
            String jobId,
            String title,
            String description,
            String notice,
            int level,
            long xp,
            long xpToNext,
            long dailyMoneyCents,
            long dailyPayoutCapCents,
            long dailyUnitsMilli,
            long dailyUnitsCapMilli,
            String moneySpotlightFamily,
            String xpSpotlightFamily,
            int moneySpotlightBonusPercent,
            int xpSpotlightBonusPercent,
            boolean active,
            boolean readOnly,
            boolean confirmationRequired,
            String pendingItemId,
            int pendingAmount,
            long pendingQuoteCents,
            List<JobCatalogEntryPayload> catalog,
            List<JobMarketEntryPayload> availableJobs
    ) {
        this(mode, jobId, title, description, notice, level, xp, xpToNext,
                dailyMoneyCents, dailyPayoutCapCents, dailyUnitsMilli, dailyUnitsCapMilli,
                moneySpotlightFamily, xpSpotlightFamily, moneySpotlightBonusPercent,
                xpSpotlightBonusPercent, active, readOnly, confirmationRequired, pendingItemId,
                pendingAmount, pendingQuoteCents, catalog, availableJobs, List.of(), List.of(), List.of(), List.of(), 0L,
                List.of());
    }

    /** Compatibility constructor for snapshots that include quests but predate Courier route data. */
    public JobOpenPayload(
            String mode,
            String jobId,
            String title,
            String description,
            String notice,
            int level,
            long xp,
            long xpToNext,
            long dailyMoneyCents,
            long dailyPayoutCapCents,
            long dailyUnitsMilli,
            long dailyUnitsCapMilli,
            String moneySpotlightFamily,
            String xpSpotlightFamily,
            int moneySpotlightBonusPercent,
            int xpSpotlightBonusPercent,
            boolean active,
            boolean readOnly,
            boolean confirmationRequired,
            String pendingItemId,
            int pendingAmount,
            long pendingQuoteCents,
            List<JobCatalogEntryPayload> catalog,
            List<JobMarketEntryPayload> availableJobs,
            List<JobLevelRoadmapNodePayload> levelRoadmap,
            List<JobAchievementPayload> achievements,
            List<JobDailyQuestPayload> dailyQuests,
            List<JobRewardPayload> rewards,
            long dailyQuestResetEpochSeconds
    ) {
        this(mode, jobId, title, description, notice, level, xp, xpToNext,
                dailyMoneyCents, dailyPayoutCapCents, dailyUnitsMilli, dailyUnitsCapMilli,
                moneySpotlightFamily, xpSpotlightFamily, moneySpotlightBonusPercent,
                xpSpotlightBonusPercent, active, readOnly, confirmationRequired, pendingItemId,
                pendingAmount, pendingQuoteCents, catalog, availableJobs, levelRoadmap,
                achievements, dailyQuests, rewards, dailyQuestResetEpochSeconds, List.of());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, JobOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.mode());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.jobId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.title());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.description());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.notice());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.level());
                        LONG_CODEC.encode(buf, payload.xp());
                        LONG_CODEC.encode(buf, payload.xpToNext());
                        LONG_CODEC.encode(buf, payload.dailyMoneyCents());
                        LONG_CODEC.encode(buf, payload.dailyPayoutCapCents());
                        LONG_CODEC.encode(buf, payload.dailyUnitsMilli());
                        LONG_CODEC.encode(buf, payload.dailyUnitsCapMilli());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.moneySpotlightFamily());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.xpSpotlightFamily());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.moneySpotlightBonusPercent());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.xpSpotlightBonusPercent());
                        ByteBufCodecs.BOOL.encode(buf, payload.active());
                        ByteBufCodecs.BOOL.encode(buf, payload.readOnly());
                        ByteBufCodecs.BOOL.encode(buf, payload.confirmationRequired());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.pendingItemId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.pendingAmount());
                        LONG_CODEC.encode(buf, payload.pendingQuoteCents());
                        JobCatalogEntryPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CATALOG_ENTRIES))
                                .encode(buf, payload.catalog());
                        JobMarketEntryPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_AVAILABLE_JOBS))
                                .encode(buf, payload.availableJobs());
                        JobLevelRoadmapNodePayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LEVEL_ROADMAP_NODES))
                                .encode(buf, payload.levelRoadmap());
                        JobAchievementPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ACHIEVEMENTS))
                                .encode(buf, payload.achievements());
                        JobDailyQuestPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DAILY_QUESTS))
                                .encode(buf, payload.dailyQuests());
                        JobRewardPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_REWARDS))
                                .encode(buf, payload.rewards());
                        LONG_CODEC.encode(buf, payload.dailyQuestResetEpochSeconds());
                        CourierOrderPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_COURIER_ORDERS))
                                .encode(buf, payload.courierOrders());
                    },
                    buf -> new JobOpenPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            LONG_CODEC.decode(buf),
                            JobCatalogEntryPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CATALOG_ENTRIES)).decode(buf),
                            JobMarketEntryPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_AVAILABLE_JOBS)).decode(buf),
                            JobLevelRoadmapNodePayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LEVEL_ROADMAP_NODES)).decode(buf),
                            JobAchievementPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ACHIEVEMENTS)).decode(buf),
                            JobDailyQuestPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DAILY_QUESTS)).decode(buf),
                            JobRewardPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_REWARDS)).decode(buf),
                            LONG_CODEC.decode(buf),
                            CourierOrderPayload.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_COURIER_ORDERS)).decode(buf)
                    )
            );

    @Override
    public Type<JobOpenPayload> type() {
        return TYPE;
    }
}
