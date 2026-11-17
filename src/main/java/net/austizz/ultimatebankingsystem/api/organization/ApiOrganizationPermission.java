package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.2.0")
public enum ApiOrganizationPermission {
    VIEW_BALANCE,
    VIEW_TRANSACTIONS,
    DEPOSIT,
    WITHDRAW,
    TRANSFER,
    MANAGE_MEMBERS,
    MANAGE_ACCOUNT,
    MANAGE_LIMITS
}
