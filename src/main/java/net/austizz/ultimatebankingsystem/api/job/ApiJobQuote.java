package net.austizz.ultimatebankingsystem.api.job;

import org.jetbrains.annotations.ApiStatus;

/** A live registered-job catalog quote. Monetary values are cents. */
@ApiStatus.AvailableSince("2.2.0")
public record ApiJobQuote(
        String itemId,
        String displayName,
        String family,
        String category,
        long configuredQuoteCents,
        long liveQuoteCents,
        int normalizedRawUnitsMilli,
        int xpWeightPercent,
        int minimumShopSamples
) {
    public ApiJobQuote {
        itemId = itemId == null ? "" : itemId;
        displayName = displayName == null ? "" : displayName;
        family = family == null ? "" : family;
        category = category == null ? "" : category;
        configuredQuoteCents = Math.max(0L, configuredQuoteCents);
        liveQuoteCents = Math.max(0L, liveQuoteCents);
        normalizedRawUnitsMilli = Math.max(1, normalizedRawUnitsMilli);
        xpWeightPercent = Math.max(1, xpWeightPercent);
        minimumShopSamples = Math.max(1, minimumShopSamples);
    }
}
