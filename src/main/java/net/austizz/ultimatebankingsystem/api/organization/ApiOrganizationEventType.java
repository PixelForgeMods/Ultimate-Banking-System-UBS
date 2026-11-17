package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.2.0")
public enum ApiOrganizationEventType {
    ACCOUNT_CREATED,
    ACCOUNT_UPDATED,
    ACCOUNT_CLOSED,
    ACCOUNT_FROZEN,
    ACCOUNT_REOPENED,
    ACCESS_CHANGED,
    LEADERSHIP_TRANSFERRED,
    TRANSACTION_SUCCEEDED,
    TRANSACTION_FAILED,
    TRANSACTION_CANCELLED,
    OPERATION_FAILED,
    OPERATION_CANCELLED,
    BANK_SUSPENDED,
    BANK_LOCKDOWN,
    BANK_REOPENED,
    BANK_REVOKED,
    BANK_STATUS_CHANGED
}
