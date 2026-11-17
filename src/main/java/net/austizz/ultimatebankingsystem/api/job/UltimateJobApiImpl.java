package net.austizz.ultimatebankingsystem.api.job;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.api.ApiTransactionResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.job.JobDefinition;
import net.austizz.ultimatebankingsystem.job.JobCatalogService;
import net.austizz.ultimatebankingsystem.job.JobRegistry;
import net.austizz.ultimatebankingsystem.job.JobSavedData;
import net.austizz.ultimatebankingsystem.job.JobService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Default server-backed implementation of {@link UltimateJobApi}. */
public final class UltimateJobApiImpl implements UltimateJobApi {
    private static final String API_VERSION = "2.2.0";

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    public boolean isAvailable() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

    @Override
    public List<String> getRegisteredJobIds() {
        return JobRegistry.all().stream().map(JobDefinition::id).sorted().toList();
    }

    @Override
    public Optional<ApiJobDefinition> getJobDefinition(String jobId) {
        JobDefinition definition = JobRegistry.get(jobId);
        if (definition == null) return Optional.empty();
        return Optional.of(new ApiJobDefinition(
                definition.id(), definition.displayName(), definition.description(), definition.iconItemId(),
                definition.buyerName(), definition.catalogHeading(), definition.activityLabel(),
                definition.baseValueCents()));
    }

    @Override
    public List<ApiJobDefinition> getJobDefinitions() {
        return JobRegistry.all().stream()
                .sorted(java.util.Comparator.comparing(JobDefinition::displayName))
                .map(definition -> new ApiJobDefinition(
                        definition.id(), definition.displayName(), definition.description(), definition.iconItemId(),
                        definition.buyerName(), definition.catalogHeading(), definition.activityLabel(),
                        definition.baseValueCents()))
                .toList();
    }

    @Override
    public Optional<ApiJobSnapshot> getPlayerJob(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || playerId == null) return Optional.empty();
        JobSavedData data = JobSavedData.get(server);
        JobSavedData.PlayerRecord record = data.player(playerId);
        record.resetForDay(currentDay(server));
        JobDefinition definition = JobRegistry.get(record.activeJob());
        JobSavedData.JobProgress progress = record.progress(record.activeJob());
        return Optional.of(new ApiJobSnapshot(
                playerId,
                record.activeJob(),
                definition == null ? record.activeJob() : definition.displayName(),
                progress.level(),
                progress.xp(),
                JobService.xpUntilNextLevelForApi(progress.xp(), progress.level()),
                BigDecimal.valueOf(progress.dailyMoneyCents(), 2),
                progress.dailyUnitsMilli(),
                BigDecimal.valueOf(progress.lifetimeMoneyCents(), 2),
                progress.lifetimeUnitsMilli()
        ));
    }

    @Override
    public List<ApiJobQuote> getLumberjackQuotes() {
        return getJobQuotes(JobService.LUMBERJACK_ID);
    }

    @Override
    public List<ApiJobQuote> getJobQuotes(String jobId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || JobRegistry.get(jobId) == null) return List.of();
        JobSavedData data = JobSavedData.get(server);
        long day = currentDay(server);
        JobSavedData.JobProgress noPlayer = null;
        return JobCatalogService.catalog(server, jobId).stream().map(entry -> new ApiJobQuote(
                entry.itemId().toString(),
                entry.displayName(),
                entry.family(),
                entry.category(),
                entry.configuredQuoteCents(),
                JobCatalogService.liveQuoteCents(server, jobId, entry, data, noPlayer, day),
                entry.rawUnitsMilli(),
                entry.xpWeightPercent(),
                Config.LUMBERJACK_MARKET_SAMPLE_MINIMUM.get()
        )).toList();
    }

    @Override
    public Optional<ApiJobQuote> getLumberjackQuote(String itemId) {
        if (itemId == null || itemId.isBlank()) return Optional.empty();
        String normalized = itemId.trim().toLowerCase(Locale.ROOT);
        return getLumberjackQuotes().stream().filter(quote -> quote.itemId().equals(normalized)).findFirst();
    }

    @Override
    public ApiJobMarketSnapshot getLumberjackMarket() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new ApiJobMarketSnapshot(0L, "", "", 0, 0, 0L, 0L);
        JobSavedData data = JobSavedData.get(server);
        return new ApiJobMarketSnapshot(
                data.marketDay(),
                data.moneySpotlightFamily(),
                data.xpSpotlightFamily(),
                data.moneySpotlightBonusPercent(),
                data.xpSpotlightBonusPercent(),
                data.serverDailyMoneyCents(),
                data.serverDailyUnitsMilli()
        );
    }

    @Override
    public ApiJobMarketSnapshot getJobMarket(String jobId) {
        return getLumberjackMarket();
    }

    @Override
    public int getLevelForXp(long xp) {
        return JobService.levelForXp(xp);
    }

    @Override
    public ApiJobSaleResult sellLumberjack(ServerPlayer player, String itemId, int quantity) {
        return JobService.sellFromApi(player, itemId, quantity);
    }

    @Override
    public ApiJobSaleResult sell(ServerPlayer player, String jobId, String itemId, int quantity) {
        return JobService.sellFromApi(player, jobId, itemId, quantity);
    }

    @Override
    public void recordActivity(ServerPlayer player, String metric, String family, long amount) {
        JobService.recordActivity(player, metric, family, amount);
    }

    private static long currentDay(MinecraftServer server) {
        return server == null || server.overworld() == null ? 0L : server.overworld().getGameTime() / 24000L;
    }
}
