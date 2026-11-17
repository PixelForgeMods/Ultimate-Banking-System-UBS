package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationMemberSnapshot(
        UUID playerId,
        String role,
        Set<ApiOrganizationPermission> permissions,
        Instant grantedAt
) {
    public ApiOrganizationMemberSnapshot {
        role = role == null ? "" : role;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
