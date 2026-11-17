package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationEventQuery(
        ApiOrganizationActor actor,
        String namespace,
        UUID accountId,
        ApiOrganizationEventType type,
        Instant since,
        Instant until,
        int limit
) {
    public ApiOrganizationEventQuery {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        namespace = namespace == null ? "" : namespace.trim();
        limit = Math.max(1, Math.min(10_000, limit <= 0 ? 100 : limit));
    }
}
