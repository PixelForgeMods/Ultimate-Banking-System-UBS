package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationAccessRequest(
        ApiOrganizationActor actor,
        String idempotencyKey,
        UUID accountId,
        UUID targetPlayerId,
        String role,
        Set<ApiOrganizationPermission> permissions
) {
    public ApiOrganizationAccessRequest {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        role = role == null ? "VIEWER" : role.trim();
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
