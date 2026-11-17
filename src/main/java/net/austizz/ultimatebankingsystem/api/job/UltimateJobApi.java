package net.austizz.ultimatebankingsystem.api.job;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Public read API for the job system and the controlled player-context sale entry point. */
@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.2.0")
public interface UltimateJobApi {
    String getApiVersion();

    boolean isAvailable();

    List<String> getRegisteredJobIds();

    /** Returns immutable metadata for one registered job. */
    default Optional<ApiJobDefinition> getJobDefinition(String jobId) {
        return Optional.empty();
    }

    /** Returns immutable metadata for every registered job. */
    default List<ApiJobDefinition> getJobDefinitions() {
        return getRegisteredJobIds().stream()
                .map(this::getJobDefinition)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableList());
    }

    Optional<ApiJobSnapshot> getPlayerJob(UUID playerId);

    List<ApiJobQuote> getLumberjackQuotes();

    Optional<ApiJobQuote> getLumberjackQuote(String itemId);

    ApiJobMarketSnapshot getLumberjackMarket();

    /** Returns the current shared market rotation; the job ID is accepted for generic integrations. */
    default ApiJobMarketSnapshot getJobMarket(String jobId) {
        return getLumberjackMarket();
    }

    /** Returns the live catalog for any registered job. */
    default List<ApiJobQuote> getJobQuotes(String jobId) {
        return List.of();
    }

    /** Finds one live quote in a registered job catalog. */
    default Optional<ApiJobQuote> getJobQuote(String jobId, String itemId) {
        return getJobQuotes(jobId).stream()
                .filter(quote -> quote.itemId().equalsIgnoreCase(itemId == null ? "" : itemId.trim()))
                .findFirst();
    }

    int getLevelForXp(long xp);

    /**
     * Sells carried plain wood for the supplied live player. The server revalidates the quote,
     * active job, account, inventory, and daily limits before committing anything.
     */
    ApiJobSaleResult sellLumberjack(ServerPlayer player, String itemId, int quantity);

    /** Sells carried plain goods for the requested registered job. */
    default ApiJobSaleResult sell(ServerPlayer player, String jobId, String itemId, int quantity) {
        return ApiJobSaleResult.fail("Generic job sales are unavailable on this UBS build.");
    }

    /** Records a supported non-sale activity for a player's active job. */
    default void recordActivity(ServerPlayer player, String metric, String family, long amount) {
    }
}
