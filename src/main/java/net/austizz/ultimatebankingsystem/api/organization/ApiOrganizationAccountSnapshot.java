package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationAccountSnapshot(
        UUID accountId,
        ApiOrganizationOwnerType ownerType,
        String namespace,
        String externalId,
        String displayName,
        String accountType,
        String purpose,
        UUID bankId,
        String bankName,
        String bankStatus,
        ApiOrganizationAccountStatus status,
        BigDecimal balance,
        boolean balanceVisible,
        BigDecimal perTransactionLimit,
        BigDecimal dailySpendingLimit,
        BigDecimal dailySpent,
        Map<String, String> metadata,
        List<ApiOrganizationMemberSnapshot> members,
        Set<ApiOrganizationPermission> viewerPermissions,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
    public ApiOrganizationAccountSnapshot {
        ownerType = ownerType == null ? ApiOrganizationOwnerType.EXTERNAL : ownerType;
        namespace = namespace == null ? "" : namespace;
        externalId = externalId == null ? "" : externalId;
        displayName = displayName == null ? "" : displayName;
        accountType = accountType == null ? "" : accountType;
        purpose = purpose == null ? "" : purpose;
        bankName = bankName == null ? "" : bankName;
        bankStatus = bankStatus == null ? "" : bankStatus;
        status = status == null ? ApiOrganizationAccountStatus.OPEN : status;
        balance = balance == null ? BigDecimal.ZERO : balance;
        perTransactionLimit = perTransactionLimit == null ? BigDecimal.ZERO : perTransactionLimit;
        dailySpendingLimit = dailySpendingLimit == null ? BigDecimal.ZERO : dailySpendingLimit;
        dailySpent = dailySpent == null ? BigDecimal.ZERO : dailySpent;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        members = members == null ? List.of() : List.copyOf(members);
        viewerPermissions = viewerPermissions == null ? Set.of() : Set.copyOf(viewerPermissions);
        createdBy = createdBy == null ? "" : createdBy;
    }
}
