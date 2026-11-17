package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ApiStatus.AvailableSince("2.2.0")
public record ApiOrganizationTransactionSnapshot(
        UUID transactionId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        Instant occurredAt,
        String namespace,
        String externalReference,
        String category,
        String description,
        ApiOrganizationOperationStatus status,
        String operationCode
) {
    public ApiOrganizationTransactionSnapshot {
        amount = amount == null ? BigDecimal.ZERO : amount;
        namespace = namespace == null ? "" : namespace;
        externalReference = externalReference == null ? "" : externalReference;
        category = category == null ? "" : category;
        description = description == null ? "" : description;
        status = status == null ? ApiOrganizationOperationStatus.SUCCEEDED : status;
        operationCode = operationCode == null ? "" : operationCode;
    }
}
