package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationOperationResult(
        ApiOrganizationOperationStatus status,
        String code,
        String message,
        String idempotencyKey,
        UUID operationId,
        boolean duplicate,
        long revision,
        Instant completedAt,
        List<UUID> transactionIds,
        List<UUID> affectedAccountIds,
        ApiOrganizationAccountSnapshot account,
        List<ApiOrganizationTransactionSnapshot> transactions
) {
    public ApiOrganizationOperationResult {
        status = status == null ? ApiOrganizationOperationStatus.FAILED : status;
        code = code == null ? "UNKNOWN" : code;
        message = message == null ? "" : message;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        operationId = operationId == null ? UUID.randomUUID() : operationId;
        completedAt = completedAt == null ? Instant.now() : completedAt;
        transactionIds = transactionIds == null ? List.of() : List.copyOf(transactionIds);
        affectedAccountIds = affectedAccountIds == null ? List.of() : List.copyOf(affectedAccountIds);
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public boolean success() {
        return status == ApiOrganizationOperationStatus.SUCCEEDED;
    }

    public ApiOrganizationOperationResult asDuplicate() {
        return new ApiOrganizationOperationResult(status, code, message, idempotencyKey, operationId,
                true, revision, completedAt, transactionIds, affectedAccountIds, account, transactions);
    }
}
