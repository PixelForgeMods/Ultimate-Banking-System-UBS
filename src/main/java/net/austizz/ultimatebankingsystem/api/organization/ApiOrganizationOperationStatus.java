package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.2.0")
public enum ApiOrganizationOperationStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
