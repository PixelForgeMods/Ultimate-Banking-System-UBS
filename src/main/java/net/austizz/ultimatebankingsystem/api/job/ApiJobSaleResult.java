package net.austizz.ultimatebankingsystem.api.job;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;

/** Result of a controlled job sale initiated with a live server player context. */
@ApiStatus.AvailableSince("2.2.0")
public record ApiJobSaleResult(
        boolean success,
        String reason,
        String itemId,
        int quantity,
        BigDecimal payout,
        long xpGained
) {
    public ApiJobSaleResult {
        reason = reason == null ? "" : reason;
        itemId = itemId == null ? "" : itemId;
        quantity = Math.max(0, quantity);
        payout = payout == null ? BigDecimal.ZERO : payout.max(BigDecimal.ZERO);
        xpGained = Math.max(0L, xpGained);
    }

    public static ApiJobSaleResult fail(String reason) {
        return new ApiJobSaleResult(false, reason, "", 0, BigDecimal.ZERO, 0L);
    }
}
