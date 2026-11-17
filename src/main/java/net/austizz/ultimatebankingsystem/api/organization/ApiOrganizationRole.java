package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Set;

@ApiStatus.AvailableSince("2.2.0")
public enum ApiOrganizationRole {
    NONE(Set.of()),
    VIEWER(Set.of(ApiOrganizationPermission.VIEW_BALANCE,
            ApiOrganizationPermission.VIEW_TRANSACTIONS)),
    DEPOSITOR(Set.of(ApiOrganizationPermission.VIEW_BALANCE,
            ApiOrganizationPermission.VIEW_TRANSACTIONS,
            ApiOrganizationPermission.DEPOSIT)),
    SPENDER(Set.of(ApiOrganizationPermission.VIEW_BALANCE,
            ApiOrganizationPermission.VIEW_TRANSACTIONS,
            ApiOrganizationPermission.TRANSFER)),
    TREASURER(Set.of(ApiOrganizationPermission.VIEW_BALANCE,
            ApiOrganizationPermission.VIEW_TRANSACTIONS,
            ApiOrganizationPermission.DEPOSIT,
            ApiOrganizationPermission.WITHDRAW,
            ApiOrganizationPermission.TRANSFER)),
    ADMINISTRATOR(Set.of(ApiOrganizationPermission.VIEW_BALANCE,
            ApiOrganizationPermission.VIEW_TRANSACTIONS,
            ApiOrganizationPermission.DEPOSIT,
            ApiOrganizationPermission.WITHDRAW,
            ApiOrganizationPermission.TRANSFER,
            ApiOrganizationPermission.MANAGE_MEMBERS,
            ApiOrganizationPermission.MANAGE_ACCOUNT,
            ApiOrganizationPermission.MANAGE_LIMITS)),
    OWNER(Set.of(ApiOrganizationPermission.VIEW_BALANCE,
            ApiOrganizationPermission.VIEW_TRANSACTIONS,
            ApiOrganizationPermission.DEPOSIT,
            ApiOrganizationPermission.WITHDRAW,
            ApiOrganizationPermission.TRANSFER,
            ApiOrganizationPermission.MANAGE_MEMBERS,
            ApiOrganizationPermission.MANAGE_ACCOUNT,
            ApiOrganizationPermission.MANAGE_LIMITS)),
    CUSTOM(Set.of());

    private final Set<ApiOrganizationPermission> defaultPermissions;

    ApiOrganizationRole(Set<ApiOrganizationPermission> defaultPermissions) {
        this.defaultPermissions = Set.copyOf(defaultPermissions);
    }

    public Set<ApiOrganizationPermission> defaultPermissions() {
        return defaultPermissions;
    }

    public static ApiOrganizationRole parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CUSTOM;
        }
    }
}
