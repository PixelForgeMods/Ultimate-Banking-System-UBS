package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationBankStatusRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID bankId,
        String status,
        String reason
) {
    public ApiOrganizationBankStatusRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        status = status == null ? "ACTIVE" : status.trim();
        reason = reason == null ? "" : reason.trim();
    }
}
