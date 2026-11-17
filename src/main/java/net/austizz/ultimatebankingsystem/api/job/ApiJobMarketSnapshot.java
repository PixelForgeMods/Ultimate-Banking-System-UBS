package net.austizz.ultimatebankingsystem.api.job;

import org.jetbrains.annotations.ApiStatus;

/** Global job-market rotation and bounded supply snapshot. */
@ApiStatus.AvailableSince("2.1.2")
public record ApiJobMarketSnapshot(
        long minecraftDay,
        String moneySpotlightFamily,
        String xpSpotlightFamily,
        int moneySpotlightBonusPercent,
        int xpSpotlightBonusPercent,
        long serverDailyPayoutCents,
        long serverDailyNormalizedRawUnitsMilli
) {
    public ApiJobMarketSnapshot {
        minecraftDay = Math.max(0L, minecraftDay);
        moneySpotlightFamily = moneySpotlightFamily == null ? "" : moneySpotlightFamily;
        xpSpotlightFamily = xpSpotlightFamily == null ? "" : xpSpotlightFamily;
        moneySpotlightBonusPercent = Math.max(0, Math.min(35, moneySpotlightBonusPercent));
        xpSpotlightBonusPercent = Math.max(0, Math.min(35, xpSpotlightBonusPercent));
        serverDailyPayoutCents = Math.max(0L, serverDailyPayoutCents);
        serverDailyNormalizedRawUnitsMilli = Math.max(0L, serverDailyNormalizedRawUnitsMilli);
    }
}
