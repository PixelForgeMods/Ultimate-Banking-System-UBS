package net.austizz.ultimatebankingsystem.api.organization.events;

import net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationEventSnapshot;

import java.util.Objects;

/** Server-side organization API event data. */
public final class UbsOrganizationApiEvent {
    private final ApiOrganizationEventSnapshot snapshot;

    public UbsOrganizationApiEvent(ApiOrganizationEventSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public ApiOrganizationEventSnapshot snapshot() {
        return snapshot;
    }
}
