package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationEventSnapshot(
        UUID eventId,
        ApiOrganizationEventType type,
        Instant occurredAt,
        UUID accountId,
        List<UUID> affectedAccountIds,
        UUID bankId,
        ApiOrganizationActorType actorType,
        UUID actorPlayerId,
        String actorNamespace,
        String externalReference,
        String category,
        String code,
        String message,
        Map<String, String> metadata
) {
    public ApiOrganizationEventSnapshot {
        type = type == null ? ApiOrganizationEventType.TRANSACTION_FAILED : type;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        affectedAccountIds = affectedAccountIds == null ? List.of() : List.copyOf(affectedAccountIds);
        actorType = actorType == null ? ApiOrganizationActorType.PLAYER : actorType;
        actorNamespace = actorNamespace == null ? "" : actorNamespace;
        externalReference = externalReference == null ? "" : externalReference;
        category = category == null ? "" : category;
        code = code == null ? "" : code;
        message = message == null ? "" : message;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
