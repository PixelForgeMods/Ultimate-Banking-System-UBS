package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationAuditQuery(
        ApiOrganizationActor actor,
        String namespace,
        String externalId,
        UUID accountId,
        String externalReference,
        String category,
        UUID actorPlayerId,
        Instant since,
        Instant until,
        int limit,
        boolean includeFailed
) {
    public ApiOrganizationAuditQuery {
        actor = actor == null ? new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, null, "") : actor;
        namespace = namespace == null ? "" : namespace.trim();
        externalId = externalId == null ? "" : externalId.trim();
        externalReference = externalReference == null ? "" : externalReference.trim();
        category = category == null ? "" : category.trim();
        limit = Math.max(1, Math.min(10_000, limit <= 0 ? 100 : limit));
    }
}
