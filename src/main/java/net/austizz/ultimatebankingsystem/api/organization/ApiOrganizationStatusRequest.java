package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationStatusRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID accountId,
        ApiOrganizationAccountStatus status,
        String reason
) {
    public ApiOrganizationStatusRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        status = status == null ? ApiOrganizationAccountStatus.OPEN : status;
        reason = reason == null ? "" : reason.trim();
    }
}
