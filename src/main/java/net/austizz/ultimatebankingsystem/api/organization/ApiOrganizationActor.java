package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationActor(
        ApiOrganizationActorType type,
        UUID playerId,
        String integrationNamespace
) {
    public ApiOrganizationActor {
        type = type == null ? ApiOrganizationActorType.PLAYER : type;
        integrationNamespace = integrationNamespace == null
                ? ""
                : integrationNamespace.trim().toLowerCase(Locale.ROOT);
    }

    public static ApiOrganizationActor player(UUID playerId) {
        return new ApiOrganizationActor(ApiOrganizationActorType.PLAYER, playerId, "");
    }

    public static ApiOrganizationActor integration(String namespace) {
        return new ApiOrganizationActor(ApiOrganizationActorType.INTEGRATION, null, namespace);
    }

    public static ApiOrganizationActor officialSystem() {
        return new ApiOrganizationActor(ApiOrganizationActorType.OFFICIAL_SYSTEM, null, "");
    }
}
