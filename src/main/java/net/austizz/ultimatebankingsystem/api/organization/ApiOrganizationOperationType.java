package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("2.2.0")
public enum ApiOrganizationOperationType {
    CREATE_ACCOUNT,
    UPDATE_ACCOUNT,
    CLOSE_ACCOUNT,
    SET_STATUS,
    SET_MEMBER_ACCESS,
    REMOVE_MEMBER,
    TRANSFER_LEADERSHIP,
    TRANSFER,
    SOURCE_FUNDS,
    SINK_FUNDS,
    SET_BANK_STATUS
}
