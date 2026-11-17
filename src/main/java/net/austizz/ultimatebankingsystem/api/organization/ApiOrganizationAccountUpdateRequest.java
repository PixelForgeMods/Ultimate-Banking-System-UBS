package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationAccountUpdateRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID accountId,
        String displayName,
        String accountType,
        String purpose,
        Map<String, String> metadata,
        BigDecimal perTransactionLimit,
        BigDecimal dailySpendingLimit
) {
    public ApiOrganizationAccountUpdateRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        displayName = displayName == null ? "" : displayName.trim();
        accountType = accountType == null ? "" : accountType.trim();
        purpose = purpose == null ? "" : purpose.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
