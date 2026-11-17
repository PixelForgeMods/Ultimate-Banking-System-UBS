package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationAccountRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        String namespace,
        String externalId,
        UUID bankId,
        String accountType,
        String displayName,
        String purpose,
        Map<String, String> metadata,
        UUID initialOwnerPlayerId,
        BigDecimal perTransactionLimit,
        BigDecimal dailySpendingLimit
) {
    public ApiOrganizationAccountRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        namespace = namespace == null ? "" : namespace.trim();
        externalId = externalId == null ? "" : externalId.trim();
        accountType = accountType == null ? "ORGANIZATION" : accountType.trim();
        displayName = displayName == null ? "" : displayName.trim();
        purpose = purpose == null ? "" : purpose.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
