package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationAccountActionRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID accountId,
        String reason
) {
    public ApiOrganizationAccountActionRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        reason = reason == null ? "" : reason.trim();
    }
}
