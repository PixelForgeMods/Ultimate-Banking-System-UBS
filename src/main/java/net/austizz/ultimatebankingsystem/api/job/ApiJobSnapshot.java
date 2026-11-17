package net.austizz.ultimatebankingsystem.api.job;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Read-only progress snapshot for one player's active UBS job. */
@ApiStatus.AvailableSince("2.1.2")
public record ApiJobSnapshot(
        UUID playerId,
        String activeJobId,
        String activeJobName,
        int level,
        long xp,
        long xpToNextLevel,
        BigDecimal dailyEarnings,
        long dailyNormalizedRawUnitsMilli,
        BigDecimal lifetimeEarnings,
        long lifetimeNormalizedRawUnitsMilli
) {
    public ApiJobSnapshot {
        activeJobId = activeJobId == null ? "" : activeJobId;
        activeJobName = activeJobName == null ? "" : activeJobName;
        level = Math.max(1, Math.min(100, level));
        xp = Math.max(0L, xp);
        xpToNextLevel = Math.max(0L, xpToNextLevel);
        dailyEarnings = dailyEarnings == null ? BigDecimal.ZERO : dailyEarnings.max(BigDecimal.ZERO);
        dailyNormalizedRawUnitsMilli = Math.max(0L, dailyNormalizedRawUnitsMilli);
        lifetimeEarnings = lifetimeEarnings == null ? BigDecimal.ZERO : lifetimeEarnings.max(BigDecimal.ZERO);
        lifetimeNormalizedRawUnitsMilli = Math.max(0L, lifetimeNormalizedRawUnitsMilli);
    }
}
