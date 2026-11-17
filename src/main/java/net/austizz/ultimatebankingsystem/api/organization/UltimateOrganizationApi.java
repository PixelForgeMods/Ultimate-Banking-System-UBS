package net.austizz.ultimatebankingsystem.api.organization;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.2.0")
public interface UltimateOrganizationApi {
    String getApiVersion();

    Set<ApiOrganizationCapability> getCapabilities();

    boolean supports(ApiOrganizationCapability capability);

    Optional<ApiOrganizationOperationResult> findOperation(String idempotencyKey);

    ApiOrganizationOperationResult cancelOperation(String idempotencyKey,
                                                   ApiOrganizationActor actor,
                                                   String reason);

    ApiOrganizationOperationResult createAccount(ApiOrganizationAccountRequest request);

    Optional<ApiOrganizationAccountSnapshot> getAccount(UUID accountId, ApiOrganizationActor viewer);

    Optional<ApiOrganizationAccountSnapshot> findAccount(String namespace,
                                                         String externalId,
                                                         UUID bankId,
                                                         ApiOrganizationActor viewer);

    List<ApiOrganizationAccountSnapshot> listAccounts(String namespace,
                                                       UUID bankId,
                                                       ApiOrganizationActor viewer);

    ApiOrganizationOperationResult updateAccount(ApiOrganizationAccountUpdateRequest request);

    ApiOrganizationOperationResult closeAccount(ApiOrganizationAccountActionRequest request);

    ApiOrganizationOperationResult setStatus(ApiOrganizationStatusRequest request);

    ApiOrganizationOperationResult setMemberAccess(ApiOrganizationAccessRequest request);

    ApiOrganizationOperationResult removeMember(ApiOrganizationAccessRequest request);

    ApiOrganizationOperationResult transferLeadership(ApiOrganizationLeadershipTransferRequest request);

    ApiOrganizationValidationResult validateAccess(UUID accountId,
                                                   ApiOrganizationActor actor,
                                                   ApiOrganizationPermission permission);

    ApiOrganizationOperationResult transfer(ApiOrganizationMoneyRequest request);

    ApiOrganizationOperationResult sourceFunds(ApiOrganizationMoneyRequest request);

    ApiOrganizationOperationResult sinkFunds(ApiOrganizationMoneyRequest request);

    ApiOrganizationOperationResult setBankStatus(ApiOrganizationBankStatusRequest request);

    Optional<ApiOrganizationTransactionSnapshot> findTransactionByExternalReference(String namespace,
                                                                                      String externalReference,
                                                                                      ApiOrganizationActor viewer);

    List<ApiOrganizationTransactionSnapshot> audit(ApiOrganizationAuditQuery query);

    List<ApiOrganizationEventSnapshot> events(ApiOrganizationEventQuery query);
}
