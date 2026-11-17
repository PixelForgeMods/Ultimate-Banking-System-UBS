package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationLeadershipTransferRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID accountId,
        UUID newLeaderPlayerId,
        String demotedRole
) {
    public ApiOrganizationLeadershipTransferRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        demotedRole = demotedRole == null || demotedRole.isBlank() ? "ADMINISTRATOR" : demotedRole.trim();
    }
}
