package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationMoneyRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID accountId,
        UUID counterpartyAccountId,
        BigDecimal amount,
        String externalReference,
        String category,
        String description
) {
    public ApiOrganizationMoneyRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        amount = amount == null ? BigDecimal.ZERO : amount;
        externalReference = externalReference == null ? "" : externalReference.trim();
        category = category == null ? "TRANSFER" : category.trim();
        description = description == null ? "" : description.trim();
    }
}
