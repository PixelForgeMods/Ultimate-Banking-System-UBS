package net.austizz.ultimatebankingsystem.api.organization.events;

import net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationEventSnapshot;
import net.neoforged.bus.api.Event;

/**
 * Live server-side notification for integrations. The immutable snapshot is also persisted
 * by UBS, so integrations that were offline can reconcile through UltimateOrganizationApi.events.
 */
public final class OrganizationApiEvent extends Event {
    private final ApiOrganizationEventSnapshot snapshot;

    public OrganizationApiEvent(ApiOrganizationEventSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public ApiOrganizationEventSnapshot snapshot() {
        return snapshot;
    }
}
