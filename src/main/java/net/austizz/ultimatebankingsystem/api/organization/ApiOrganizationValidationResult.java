package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationValidationResult(
        boolean allowed,
        String code,
        String message,
        UUID accountId,
        Set<ApiOrganizationPermission> permissions,
        Instant evaluatedAt
) {
    public ApiOrganizationValidationResult {
        code = code == null ? "" : code;
        message = message == null ? "" : message;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }
}
