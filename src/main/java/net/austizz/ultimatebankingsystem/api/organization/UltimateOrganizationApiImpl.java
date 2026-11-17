package net.austizz.ultimatebankingsystem.api.organization;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;
import net.austizz.ultimatebankingsystem.api.economy.ApiEconomyActorType;
import net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationRequest;
import net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationResult;
import net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationType;
import net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApi;
import net.austizz.ultimatebankingsystem.api.organization.events.OrganizationApiEvent;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * General organization-account adapter. Organization state is kept separately from the
 * legacy player access-role fields so new integrations cannot change the behaviour of
 * existing personal accounts.
 */
@ApiStatus.Internal
public final class UltimateOrganizationApiImpl implements UltimateOrganizationApi {
    private static final String API_VERSION = "2.2.0";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,160}");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._:@/-]{1,160}");
    private static final Pattern ACCOUNT_TYPE = Pattern.compile("[A-Z][A-Z0-9._:-]{0,63}");
    private static final UUID EXTERNAL_SYSTEM_ACCOUNT = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:economy-api-system".getBytes(StandardCharsets.UTF_8));
    private static final int MAX_METADATA_ENTRIES = 64;
    private static final int MAX_METADATA_KEY = 64;
    private static final int MAX_METADATA_VALUE = 512;
    private static final int MAX_OPERATION_HISTORY = 50_000;
    private static final int MAX_TRANSACTION_HISTORY = 100_000;
    private static final int MAX_EVENT_HISTORY = 100_000;
    private static final BigDecimal NO_LIMIT = BigDecimal.ZERO.setScale(2);
    private static final Set<ApiOrganizationCapability> CAPABILITIES = Set.copyOf(
            EnumSet.allOf(ApiOrganizationCapability.class));

    private final Supplier<CentralBank> centralBankSupplier;
    private final UltimateEconomyApi economyApi;

    public UltimateOrganizationApiImpl() {
        this(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server == null ? null : BankManager.getCentralBank(server);
        }, UltimateBankingApiProvider.economy());
    }

    public UltimateOrganizationApiImpl(Supplier<CentralBank> centralBankSupplier,
                                       UltimateEconomyApi economyApi) {
        this.centralBankSupplier = java.util.Objects.requireNonNull(centralBankSupplier,
                "centralBankSupplier");
        this.economyApi = java.util.Objects.requireNonNull(economyApi, "economyApi");
    }

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    public Set<ApiOrganizationCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean supports(ApiOrganizationCapability capability) {
        return capability != null && CAPABILITIES.contains(capability);
    }

    @Override
    public synchronized Optional<ApiOrganizationOperationResult> findOperation(String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        CentralBank centralBank = resolveCentralBank();
        if (key.isEmpty() || centralBank == null) {
            return Optional.empty();
        }
        CompoundTag stored = centralBank.getOrganizationOperations().get(key);
        return Optional.ofNullable(stored == null ? null : readOperationResult(centralBank, stored));
    }

    @Override
    public synchronized ApiOrganizationOperationResult cancelOperation(String idempotencyKey,
                                                                        ApiOrganizationActor actor,
                                                                        String reason) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        CentralBank centralBank = resolveCentralBank();
        if (key.isEmpty()) {
            return failure("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency key must be 8-160 safe ASCII characters", key, centralBank);
        }
        if (centralBank == null) {
            return failure("SERVER_UNAVAILABLE", "Bank data is unavailable", key, null);
        }
        CompoundTag stored = centralBank.getOrganizationOperations().get(key);
        if (stored == null) {
            return failure("OPERATION_NOT_FOUND", "No pending organization operation was found", key,
                    centralBank);
        }
        ApiOrganizationOperationStatus status = operationStatus(stored);
        if (status != ApiOrganizationOperationStatus.PENDING) {
            return failure("OPERATION_ALREADY_FINAL",
                    "The organization operation already has a final result", key, centralBank);
        }
        if (!actorMatchesPending(stored, actor)) {
            return failure("ACCESS_DENIED", "Actor cannot cancel this organization operation", key,
                    centralBank);
        }
        if (stored.contains("executionStartedAt") && stored.getLong("executionStartedAt") > 0L) {
            return failure("OPERATION_IN_FLIGHT",
                    "Operation execution has started; retry the original request to reconcile it",
                    key, centralBank);
        }
        String message = normalizeDescription(reason);
        ApiOrganizationOperationResult result = new ApiOrganizationOperationResult(
                ApiOrganizationOperationStatus.CANCELLED,
                "OPERATION_CANCELLED",
                message.isBlank() ? "Organization operation cancelled" : message,
                key,
                operationId(key),
                false,
                centralBank.getEconomyRevision(),
                Instant.now(),
                List.of(),
                readUuidList(stored.getList("affectedAccountIds", Tag.TAG_COMPOUND)),
                readAccountSnapshot(centralBank, stored.hasUUID("accountId") ? stored.getUUID("accountId") : null,
                        actor),
                List.of()
        );
        ApiOrganizationOperationType operationType = parseOperationType(stored.getString("operationType"));
        recordEvent(centralBank,
                isMoneyOperation(operationType)
                        ? ApiOrganizationEventType.TRANSACTION_CANCELLED
                        : ApiOrganizationEventType.OPERATION_CANCELLED,
                stored.hasUUID("accountId") ? stored.getUUID("accountId") : null,
                stored.hasUUID("bankId") ? stored.getUUID("bankId") : null,
                actor,
                stored.getString("externalReference"),
                stored.getString("category"),
                result.code(),
                result.message(),
                key,
                Map.of());
        centralBank.getOrganizationOperations().put(key, writeOperation(result, stored.getString("requestFingerprint"),
                operationType, stored));
        BankManager.markDirty();
        return result;
    }

    @Override
    public synchronized ApiOrganizationOperationResult createAccount(ApiOrganizationAccountRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Account request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.CREATE_ACCOUNT,
                fingerprint("CREATE_ACCOUNT", request.actor(), request.namespace(), request.externalId(),
                        request.bankId(), request.accountType(), request.displayName(), request.purpose(),
                        request.metadata(), request.initialOwnerPlayerId(), request.perTransactionLimit(),
                        request.dailySpendingLimit()), request.actor(), null, request.bankId(), "", "",
                (centralBank, key) -> createAccount0(centralBank, key, request));
    }

    @Override
    public synchronized Optional<ApiOrganizationAccountSnapshot> getAccount(UUID accountId,
                                                                              ApiOrganizationActor viewer) {
        CentralBank centralBank = resolveCentralBank();
        CompoundTag record = organizationRecord(centralBank, accountId);
        return record == null || !canViewAccount(record, viewer)
                ? Optional.empty()
                : Optional.of(snapshot(centralBank, record, viewer));
    }

    @Override
    public synchronized Optional<ApiOrganizationAccountSnapshot> findAccount(String namespace,
                                                                               String externalId,
                                                                               UUID bankId,
                                                                               ApiOrganizationActor viewer) {
        String normalizedNamespace = normalizeNamespace(namespace);
        String normalizedExternalId = normalizeExternalId(externalId);
        if (normalizedNamespace.isEmpty() || normalizedExternalId.isEmpty()) {
            return Optional.empty();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return Optional.empty();
        }
        for (CompoundTag record : organizationRecords(centralBank)) {
            if (normalizedNamespace.equals(record.getString("namespace"))
                    && normalizedExternalId.equals(record.getString("externalId"))
                    && (bankId == null || bankId.equals(record.getUUID("bankId")))
                    && canViewAccount(record, viewer)) {
                return Optional.of(snapshot(centralBank, record, viewer));
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<ApiOrganizationAccountSnapshot> listAccounts(String namespace,
                                                                            UUID bankId,
                                                                            ApiOrganizationActor viewer) {
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null) {
            return List.of();
        }
        String normalizedNamespace = normalizeNamespace(namespace);
        if (viewer != null && viewer.type() == ApiOrganizationActorType.INTEGRATION) {
            if (viewer.integrationNamespace().isBlank()) {
                return List.of();
            }
            if (normalizedNamespace.isBlank()) {
                normalizedNamespace = viewer.integrationNamespace();
            } else if (!normalizedNamespace.equals(viewer.integrationNamespace())) {
                return List.of();
            }
        }
        String requestedNamespace = normalizedNamespace;
        return organizationRecords(centralBank).stream()
                .filter(record -> requestedNamespace.isBlank()
                        || requestedNamespace.equals(record.getString("namespace")))
                .filter(record -> bankId == null || bankId.equals(record.getUUID("bankId")))
                .filter(record -> canViewAccount(record, viewer))
                .map(record -> snapshot(centralBank, record, viewer))
                .sorted(Comparator.comparing(ApiOrganizationAccountSnapshot::displayName)
                        .thenComparing(ApiOrganizationAccountSnapshot::accountId))
                .toList();
    }

    @Override
    public synchronized ApiOrganizationOperationResult updateAccount(ApiOrganizationAccountUpdateRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Account update request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.UPDATE_ACCOUNT,
                fingerprint("UPDATE_ACCOUNT", request.actor(), request.accountId(), request.displayName(),
                        request.accountType(), request.purpose(), request.metadata(),
                        request.perTransactionLimit(), request.dailySpendingLimit()), request.actor(),
                request.accountId(), null, "", "",
                (centralBank, key) -> updateAccount0(centralBank, key, request));
    }

    @Override
    public synchronized ApiOrganizationOperationResult closeAccount(ApiOrganizationAccountActionRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Account close request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.CLOSE_ACCOUNT,
                fingerprint("CLOSE_ACCOUNT", request.actor(), request.accountId(), request.reason()),
                request.actor(), request.accountId(), null, "", "",
                (centralBank, key) -> closeAccount0(centralBank, key, request));
    }

    @Override
    public synchronized ApiOrganizationOperationResult setStatus(ApiOrganizationStatusRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Account status request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.SET_STATUS,
                fingerprint("SET_STATUS", request.actor(), request.accountId(), request.status(), request.reason()),
                request.actor(), request.accountId(), null, "", "",
                (centralBank, key) -> setStatus0(centralBank, key, request));
    }

    @Override
    public synchronized ApiOrganizationOperationResult setMemberAccess(ApiOrganizationAccessRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Access request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.SET_MEMBER_ACCESS,
                fingerprint("SET_MEMBER_ACCESS", request.actor(), request.accountId(), request.targetPlayerId(),
                        request.role(), request.permissions()), request.actor(), request.accountId(), null, "", "",
                (centralBank, key) -> setMemberAccess0(centralBank, key, request));
    }

    @Override
    public synchronized ApiOrganizationOperationResult removeMember(ApiOrganizationAccessRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Member removal request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.REMOVE_MEMBER,
                fingerprint("REMOVE_MEMBER", request.actor(), request.accountId(), request.targetPlayerId()),
                request.actor(), request.accountId(), null, "", "",
                (centralBank, key) -> removeMember0(centralBank, key, request));
    }

    @Override
    public synchronized ApiOrganizationOperationResult transferLeadership(
            ApiOrganizationLeadershipTransferRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Leadership transfer request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.TRANSFER_LEADERSHIP,
                fingerprint("TRANSFER_LEADERSHIP", request.actor(), request.accountId(), request.newLeaderPlayerId(),
                        request.demotedRole()), request.actor(), request.accountId(), null, "", "",
                (centralBank, key) -> transferLeadership0(centralBank, key, request));
    }

    @Override
    public synchronized ApiOrganizationValidationResult validateAccess(UUID accountId,
                                                                         ApiOrganizationActor actor,
                                                                         ApiOrganizationPermission permission) {
        CentralBank centralBank = resolveCentralBank();
        CompoundTag record = organizationRecord(centralBank, accountId);
        Set<ApiOrganizationPermission> permissions = record == null
                ? Set.of()
                : permissionsFor(record, actor);
        boolean allowed = record != null && permission != null && permissions.contains(permission)
                && accountStatus(record) != ApiOrganizationAccountStatus.CLOSED;
        return new ApiOrganizationValidationResult(
                allowed,
                allowed ? "ALLOWED" : record == null ? "ACCOUNT_NOT_FOUND" : "ACCESS_DENIED",
                allowed ? "Permission granted" : "Actor does not have the requested permission",
                accountId,
                permissions,
                Instant.now());
    }

    @Override
    public synchronized ApiOrganizationOperationResult transfer(ApiOrganizationMoneyRequest request) {
        return moneyRequest(request, ApiOrganizationOperationType.TRANSFER);
    }

    @Override
    public synchronized ApiOrganizationOperationResult sourceFunds(ApiOrganizationMoneyRequest request) {
        return moneyRequest(request, ApiOrganizationOperationType.SOURCE_FUNDS);
    }

    @Override
    public synchronized ApiOrganizationOperationResult sinkFunds(ApiOrganizationMoneyRequest request) {
        return moneyRequest(request, ApiOrganizationOperationType.SINK_FUNDS);
    }

    @Override
    public synchronized ApiOrganizationOperationResult setBankStatus(ApiOrganizationBankStatusRequest request) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Bank status request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), ApiOrganizationOperationType.SET_BANK_STATUS,
                fingerprint("SET_BANK_STATUS", request.actor(), request.bankId(), request.status(), request.reason()),
                request.actor(), null, request.bankId(), "", "",
                (centralBank, key) -> setBankStatus0(centralBank, key, request));
    }

    @Override
    public synchronized Optional<ApiOrganizationTransactionSnapshot> findTransactionByExternalReference(
            String namespace, String externalReference, ApiOrganizationActor viewer) {
        String normalizedNamespace = normalizeNamespace(namespace);
        String normalizedReference = normalizeReference(externalReference);
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null || normalizedNamespace.isEmpty() || normalizedReference.isEmpty()) {
            return Optional.empty();
        }
        return centralBank.getOrganizationTransactions().values().stream()
                .map(this::transactionSnapshot)
                .filter(java.util.Objects::nonNull)
                .filter(transaction -> normalizedReference.equals(transaction.externalReference()))
                .filter(transaction -> normalizedNamespace.equals(transaction.namespace()))
                .filter(transaction -> transactionVisible(centralBank, transaction, viewer))
                .findFirst();
    }

    @Override
    public synchronized List<ApiOrganizationTransactionSnapshot> audit(ApiOrganizationAuditQuery query) {
        if (query == null) {
            return List.of();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null || !queryAllowed(query.actor(), query.namespace())) {
            return List.of();
        }
        String namespace = normalizedQueryNamespace(query.actor(), query.namespace());
        String externalId = normalizeExternalId(query.externalId());
        String reference = normalizeReference(query.externalReference());
        String category = normalizeCategory(query.category());
        return centralBank.getOrganizationTransactions().values().stream()
                .map(this::transactionSnapshot)
                .filter(java.util.Objects::nonNull)
                .filter(transaction -> query.includeFailed()
                        || transaction.status() == ApiOrganizationOperationStatus.SUCCEEDED)
                .filter(transaction -> namespace.isBlank()
                        || transactionMatchesNamespace(centralBank, transaction, namespace))
                .filter(transaction -> externalId.isBlank() || transactionBelongsToExternalId(centralBank,
                        transaction, externalId))
                .filter(transaction -> query.accountId() == null
                        || query.accountId().equals(transaction.senderAccountId())
                        || query.accountId().equals(transaction.receiverAccountId()))
                .filter(transaction -> reference.isBlank()
                        || reference.equals(transaction.externalReference()))
                .filter(transaction -> category.isBlank() || category.equals(transaction.category()))
                .filter(transaction -> query.actorPlayerId() == null
                        || query.actorPlayerId().equals(transactionActorPlayer(centralBank, transaction)))
                .filter(transaction -> inTimeRange(transaction.occurredAt(), query.since(), query.until()))
                .filter(transaction -> transactionVisible(centralBank, transaction, query.actor()))
                .sorted(Comparator.comparing(ApiOrganizationTransactionSnapshot::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(query.limit())
                .toList();
    }

    @Override
    public synchronized List<ApiOrganizationEventSnapshot> events(ApiOrganizationEventQuery query) {
        if (query == null) {
            return List.of();
        }
        CentralBank centralBank = resolveCentralBank();
        if (centralBank == null || !queryAllowed(query.actor(), query.namespace())) {
            return List.of();
        }
        String namespace = normalizedQueryNamespace(query.actor(), query.namespace());
        return centralBank.getOrganizationEvents().values().stream()
                .map(this::eventSnapshot)
                .filter(java.util.Objects::nonNull)
                .filter(event -> eventMatchesNamespace(centralBank, event, namespace))
                .filter(event -> query.accountId() == null || query.accountId().equals(event.accountId()))
                .filter(event -> query.type() == null || query.type() == event.type())
                .filter(event -> inTimeRange(event.occurredAt(), query.since(), query.until()))
                .filter(event -> eventVisible(centralBank, event, query.actor()))
                .sorted(Comparator.comparing(ApiOrganizationEventSnapshot::occurredAt).reversed())
                .limit(query.limit())
                .toList();
    }

    private ApiOrganizationOperationResult moneyRequest(ApiOrganizationMoneyRequest request,
                                                        ApiOrganizationOperationType type) {
        if (request == null) {
            return failure("INVALID_REQUEST", "Money request is required", "", resolveCentralBank());
        }
        return executeMutation(request.idempotencyKey(), type,
                fingerprint(type.name(), request.actor(), request.accountId(), request.counterpartyAccountId(),
                        request.amount(), request.externalReference(), request.category(), request.description()),
                request.actor(), request.accountId(), null, request.externalReference(), request.category(),
                (centralBank, key) -> money0(centralBank, key, request, type));
    }

    private ApiOrganizationOperationResult createAccount0(CentralBank centralBank,
                                                          String key,
                                                          ApiOrganizationAccountRequest request) {
        String namespace = normalizeNamespace(request.namespace());
        String externalId = normalizeExternalId(request.externalId());
        String accountType = normalizeAccountType(request.accountType());
        String displayName = normalizeLabel(request.displayName());
        String purpose = normalizeLabel(request.purpose());
        if (namespace.isEmpty() || externalId.isEmpty() || accountType.isEmpty()
                || request.bankId() == null || centralBank.getBank(request.bankId()) == null) {
            return failure("INVALID_ACCOUNT", "Namespace, external id, account type, and an existing bank are required",
                    key, centralBank);
        }
        if (!canCreateNamespace(request.actor(), namespace)) {
            return failure("ACCESS_DENIED", "Only the owning integration or official system can create this account",
                    key, centralBank);
        }
        BigDecimal perTransactionLimit = normalizeLimit(request.perTransactionLimit());
        BigDecimal dailySpendingLimit = normalizeLimit(request.dailySpendingLimit());
        if (perTransactionLimit == null || dailySpendingLimit == null) {
            return failure("INVALID_LIMIT", "Organization limits must be zero or positive values with two decimals",
                    key, centralBank);
        }
        Map<String, String> metadata;
        try {
            metadata = normalizeMetadata(request.metadata());
        } catch (IllegalArgumentException exception) {
            return failure("INVALID_METADATA", exception.getMessage(), key, centralBank);
        }
        String canonicalPrincipal = namespace + ":" + externalId;
        UUID accountId = organizationAccountId(namespace, externalId, request.bankId());
        CompoundTag existingRecord = organizationRecord(centralBank, accountId);
        AccountHolder existingAccount = account(centralBank, accountId);
        Bank bank = centralBank.getBank(request.bankId());
        if (existingRecord != null) {
            if (namespace.equals(existingRecord.getString("namespace"))
                    && externalId.equals(existingRecord.getString("externalId"))
                    && request.bankId().equals(existingRecord.getUUID("bankId"))) {
                return existingAccount == null
                        ? failure("ACCOUNT_STORAGE_INCONSISTENT",
                        "The organization registry exists but its bank account is missing", key, centralBank)
                        : success("ACCOUNT_EXISTS", "Organization account already exists", key, centralBank,
                        request.actor(), accountId, List.of(), List.of(accountId));
            }
            return failure("ACCOUNT_ID_CONFLICT", "The deterministic organization account id is already in use",
                    key, centralBank);
        }

        AccountHolder created;
        boolean recoveringUnderlyingAccount = existingAccount != null;
        if (recoveringUnderlyingAccount) {
            boolean matchesIdentity = existingAccount.isInstitutional()
                    && canonicalPrincipal.equals(existingAccount.getPrincipalId())
                    && request.bankId().equals(existingAccount.getBankId());
            if (!matchesIdentity) {
                return failure("ACCOUNT_ID_CONFLICT", "The deterministic organization account id is already in use",
                        key, centralBank);
            }
            created = existingAccount;
        } else {
            created = AccountHolder.createInstitutional(canonicalPrincipal, BigDecimal.ZERO,
                    AccountTypes.CheckingAccount, request.bankId(), accountId);
        }
        created.setAccountAccessType("ORGANIZATION");
        created.setBusinessLabel(displayName.isBlank() ? canonicalPrincipal : displayName);
        if (recoveringUnderlyingAccount) {
            created.unfreeze();
        } else if (!bank.AddAccount(created)) {
            return failure("ACCOUNT_CREATE_FAILED", "Organization account could not be added to the bank",
                    key, centralBank);
        }

        CompoundTag record = new CompoundTag();
        record.putUUID("accountId", accountId);
        record.putString("namespace", namespace);
        record.putString("externalId", externalId);
        record.putUUID("bankId", request.bankId());
        record.putString("accountType", accountType);
        record.putString("displayName", displayName.isBlank() ? canonicalPrincipal : displayName);
        record.putString("purpose", purpose);
        record.putString("status", ApiOrganizationAccountStatus.OPEN.name());
        record.putString("perTransactionLimit", perTransactionLimit.toPlainString());
        record.putString("dailySpendingLimit", dailySpendingLimit.toPlainString());
        record.putString("dailySpent", NO_LIMIT.toPlainString());
        record.putLong("dailySpentDay", currentEpochDay());
        record.putString("createdBy", actorLabel(request.actor()));
        long now = System.currentTimeMillis();
        record.putLong("createdAt", now);
        record.putLong("updatedAt", now);
        record.put("metadata", writeStringMap(metadata));
        record.put("members", new ListTag());
        if (request.initialOwnerPlayerId() != null) {
            putMember(record, request.initialOwnerPlayerId(), ApiOrganizationRole.OWNER.name(),
                    ApiOrganizationRole.OWNER.defaultPermissions(), now);
        }
        centralBank.getOrganizationAccounts().put(accountId, record);
        recordEvent(centralBank, ApiOrganizationEventType.ACCOUNT_CREATED, accountId, request.bankId(),
                request.actor(), "", "", "ACCOUNT_CREATED", "Organization account created", key, metadata);
        BankManager.markDirty();
        return success("ACCOUNT_CREATED", "Organization account created", key, centralBank, request.actor(),
                accountId, List.of(), List.of(accountId));
    }

    private ApiOrganizationOperationResult updateAccount0(CentralBank centralBank,
                                                          String key,
                                                          ApiOrganizationAccountUpdateRequest request) {
        CompoundTag existing = organizationRecord(centralBank, request.accountId());
        if (existing == null) {
            return failure("ACCOUNT_NOT_FOUND", "Organization account was not found", key, centralBank);
        }
        if (!canManageAccount(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Actor cannot manage this organization account", key, centralBank);
        }
        if (accountStatus(existing) == ApiOrganizationAccountStatus.CLOSED) {
            return failure("ACCOUNT_CLOSED", "Closed organization accounts cannot be updated", key, centralBank);
        }

        String displayName = normalizeLabel(request.displayName());
        String accountType = normalizeAccountType(request.accountType());
        String purpose = normalizeLabel(request.purpose());
        if (!displayName.isBlank()) {
            existing.putString("displayName", displayName);
            AccountHolder account = account(centralBank, request.accountId());
            if (account != null) {
                account.setBusinessLabel(displayName);
            }
        }
        if (!accountType.isBlank()) {
            existing.putString("accountType", accountType);
        }
        if (!purpose.isBlank()) {
            existing.putString("purpose", purpose);
        }
        try {
            if (!request.metadata().isEmpty()) {
                Map<String, String> merged = new LinkedHashMap<>(readStringMap(existing.getList("metadata",
                        Tag.TAG_COMPOUND)));
                merged.putAll(normalizeMetadata(request.metadata()));
                existing.put("metadata", writeStringMap(merged));
            }
        } catch (IllegalArgumentException exception) {
            return failure("INVALID_METADATA", exception.getMessage(), key, centralBank);
        }
        if (request.perTransactionLimit() != null) {
            BigDecimal value = normalizeLimit(request.perTransactionLimit());
            if (value == null) {
                return failure("INVALID_LIMIT", "Per-transaction limit must be zero or positive with two decimals",
                        key, centralBank);
            }
            existing.putString("perTransactionLimit", value.toPlainString());
        }
        if (request.dailySpendingLimit() != null) {
            BigDecimal value = normalizeLimit(request.dailySpendingLimit());
            if (value == null) {
                return failure("INVALID_LIMIT", "Daily spending limit must be zero or positive with two decimals",
                        key, centralBank);
            }
            existing.putString("dailySpendingLimit", value.toPlainString());
        }
        existing.putLong("updatedAt", System.currentTimeMillis());
        centralBank.getOrganizationAccounts().put(request.accountId(), existing);
        recordEvent(centralBank, ApiOrganizationEventType.ACCOUNT_UPDATED, request.accountId(),
                existing.getUUID("bankId"), request.actor(), "", "", "ACCOUNT_UPDATED",
                "Organization account updated", key, Map.of());
        BankManager.markDirty();
        return success("ACCOUNT_UPDATED", "Organization account updated", key, centralBank, request.actor(),
                request.accountId(), List.of(), List.of(request.accountId()));
    }

    private ApiOrganizationOperationResult closeAccount0(CentralBank centralBank,
                                                         String key,
                                                         ApiOrganizationAccountActionRequest request) {
        CompoundTag existing = organizationRecord(centralBank, request.accountId());
        AccountHolder account = account(centralBank, request.accountId());
        if (existing == null || account == null) {
            return failure("ACCOUNT_NOT_FOUND", "Organization account was not found", key, centralBank);
        }
        if (!canManageAccount(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Actor cannot close this organization account", key, centralBank);
        }
        if (accountStatus(existing) == ApiOrganizationAccountStatus.CLOSED) {
            return success("ACCOUNT_ALREADY_CLOSED", "Organization account is already closed", key, centralBank,
                    request.actor(), request.accountId(), List.of(), List.of(request.accountId()));
        }
        if (account.getBalance().signum() != 0) {
            return failure("BALANCE_REMAINS",
                    "Organization account must have a zero balance before it can be closed", key, centralBank);
        }
        existing.putString("status", ApiOrganizationAccountStatus.CLOSED.name());
        existing.putLong("closedAt", System.currentTimeMillis());
        existing.putLong("updatedAt", System.currentTimeMillis());
        account.freeze(request.reason().isBlank() ? "Organization account closed" : request.reason());
        centralBank.getOrganizationAccounts().put(request.accountId(), existing);
        recordEvent(centralBank, ApiOrganizationEventType.ACCOUNT_CLOSED, request.accountId(),
                existing.getUUID("bankId"), request.actor(), "", "", "ACCOUNT_CLOSED",
                request.reason().isBlank() ? "Organization account closed" : request.reason(), key, Map.of());
        BankManager.markDirty();
        return success("ACCOUNT_CLOSED", "Organization account closed", key, centralBank, request.actor(),
                request.accountId(), List.of(), List.of(request.accountId()));
    }

    private ApiOrganizationOperationResult setStatus0(CentralBank centralBank,
                                                       String key,
                                                       ApiOrganizationStatusRequest request) {
        CompoundTag existing = organizationRecord(centralBank, request.accountId());
        AccountHolder account = account(centralBank, request.accountId());
        if (existing == null || account == null) {
            return failure("ACCOUNT_NOT_FOUND", "Organization account was not found", key, centralBank);
        }
        if (!canManageAccount(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Actor cannot change this organization account status", key, centralBank);
        }
        if (accountStatus(existing) == ApiOrganizationAccountStatus.CLOSED) {
            return failure("ACCOUNT_CLOSED", "Closed organization accounts cannot be reopened", key, centralBank);
        }
        if (request.status() == ApiOrganizationAccountStatus.CLOSED) {
            return failure("USE_CLOSE_ACCOUNT", "Use closeAccount for the terminal closed state", key, centralBank);
        }
        existing.putString("status", request.status().name());
        existing.putLong("updatedAt", System.currentTimeMillis());
        if (request.status() == ApiOrganizationAccountStatus.FROZEN) {
            account.freeze(request.reason());
        } else {
            account.unfreeze();
        }
        centralBank.getOrganizationAccounts().put(request.accountId(), existing);
        ApiOrganizationEventType eventType = request.status() == ApiOrganizationAccountStatus.FROZEN
                ? ApiOrganizationEventType.ACCOUNT_FROZEN : ApiOrganizationEventType.ACCOUNT_REOPENED;
        recordEvent(centralBank, eventType, request.accountId(), existing.getUUID("bankId"), request.actor(),
                "", "", request.status().name(),
                request.reason().isBlank() ? "Organization account status changed" : request.reason(), key, Map.of());
        BankManager.markDirty();
        return success("ACCOUNT_STATUS_UPDATED", "Organization account status changed", key, centralBank,
                request.actor(), request.accountId(), List.of(), List.of(request.accountId()));
    }

    private ApiOrganizationOperationResult setMemberAccess0(CentralBank centralBank,
                                                             String key,
                                                             ApiOrganizationAccessRequest request) {
        CompoundTag existing = organizationRecord(centralBank, request.accountId());
        if (existing == null) {
            return failure("ACCOUNT_NOT_FOUND", "Organization account was not found", key, centralBank);
        }
        if (!canManageMembers(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Actor cannot manage organization members", key, centralBank);
        }
        if (accountStatus(existing) == ApiOrganizationAccountStatus.CLOSED) {
            return failure("ACCOUNT_CLOSED", "Closed organization accounts cannot change access", key, centralBank);
        }
        if (request.targetPlayerId() == null) {
            return failure("PLAYER_REQUIRED", "A target player is required", key, centralBank);
        }
        String role = normalizeRole(request.role());
        if (role.isBlank() || "NONE".equals(role)) {
            return failure("USE_REMOVE_MEMBER", "Use removeMember to revoke access", key, centralBank);
        }
        if ("OWNER".equals(role)) {
            return failure("USE_LEADERSHIP_TRANSFER", "Use transferLeadership to assign the owner role", key,
                    centralBank);
        }
        Set<ApiOrganizationPermission> permissions = effectivePermissions(role, request.permissions());
        putMember(existing, request.targetPlayerId(), role, permissions, System.currentTimeMillis());
        existing.putLong("updatedAt", System.currentTimeMillis());
        centralBank.getOrganizationAccounts().put(request.accountId(), existing);
        recordEvent(centralBank, ApiOrganizationEventType.ACCESS_CHANGED, request.accountId(),
                existing.getUUID("bankId"), request.actor(), "", "", "ACCESS_UPDATED",
                "Organization member access updated", key,
                Map.of("playerId", request.targetPlayerId().toString(), "role", role));
        BankManager.markDirty();
        return success("ACCESS_UPDATED", "Organization member access updated", key, centralBank, request.actor(),
                request.accountId(), List.of(), List.of(request.accountId()));
    }

    private ApiOrganizationOperationResult removeMember0(CentralBank centralBank,
                                                          String key,
                                                          ApiOrganizationAccessRequest request) {
        CompoundTag existing = organizationRecord(centralBank, request.accountId());
        if (existing == null) {
            return failure("ACCOUNT_NOT_FOUND", "Organization account was not found", key, centralBank);
        }
        if (!canManageMembers(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Actor cannot manage organization members", key, centralBank);
        }
        if (accountStatus(existing) == ApiOrganizationAccountStatus.CLOSED) {
            return failure("ACCOUNT_CLOSED", "Closed organization accounts cannot change access", key, centralBank);
        }
        if (request.targetPlayerId() == null) {
            return failure("PLAYER_REQUIRED", "A target player is required", key, centralBank);
        }
        MemberData member = member(existing, request.targetPlayerId());
        if (member == null) {
            return success("MEMBER_ABSENT", "Player did not have organization access", key, centralBank,
                    request.actor(), request.accountId(), List.of(), List.of(request.accountId()));
        }
        // Integration-owned accounts may delegate all access to the owning mod's permission system.
        if ("OWNER".equals(member.role()) && (request.actor() == null
                || request.actor().type() != ApiOrganizationActorType.INTEGRATION
                || !request.actor().integrationNamespace().equals(existing.getString("namespace")))) {
            return failure("TRANSFER_LEADERSHIP_FIRST", "Transfer leadership before removing the current owner",
                    key, centralBank);
        }
        removeMember(existing, request.targetPlayerId());
        existing.putLong("updatedAt", System.currentTimeMillis());
        centralBank.getOrganizationAccounts().put(request.accountId(), existing);
        recordEvent(centralBank, ApiOrganizationEventType.ACCESS_CHANGED, request.accountId(),
                existing.getUUID("bankId"), request.actor(), "", "", "ACCESS_REVOKED",
                "Organization member access revoked", key,
                Map.of("playerId", request.targetPlayerId().toString()));
        BankManager.markDirty();
        return success("ACCESS_REVOKED", "Organization member access revoked", key, centralBank, request.actor(),
                request.accountId(), List.of(), List.of(request.accountId()));
    }

    private ApiOrganizationOperationResult transferLeadership0(CentralBank centralBank,
                                                               String key,
                                                               ApiOrganizationLeadershipTransferRequest request) {
        CompoundTag existing = organizationRecord(centralBank, request.accountId());
        if (existing == null) {
            return failure("ACCOUNT_NOT_FOUND", "Organization account was not found", key, centralBank);
        }
        if (!canManageMembers(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Actor cannot transfer organization leadership", key, centralBank);
        }
        if (!canTransferLeadership(existing, request.actor())) {
            return failure("ACCESS_DENIED", "Only the current owner or owning integration can transfer leadership",
                    key, centralBank);
        }
        if (request.newLeaderPlayerId() == null) {
            return failure("PLAYER_REQUIRED", "A new leader is required", key, centralBank);
        }
        if (accountStatus(existing) == ApiOrganizationAccountStatus.CLOSED) {
            return failure("ACCOUNT_CLOSED", "Closed organization accounts cannot transfer leadership", key,
                    centralBank);
        }
        String demotedRole = normalizeRole(request.demotedRole());
        if (demotedRole.isBlank() || "NONE".equals(demotedRole) || "OWNER".equals(demotedRole)) {
            return failure("INVALID_DEMOTION_ROLE", "The previous leader needs a non-owner member role", key,
                    centralBank);
        }
        long now = System.currentTimeMillis();
        for (MemberData member : members(existing)) {
            if ("OWNER".equals(member.role())) {
                putMember(existing, member.playerId(), demotedRole, effectivePermissions(demotedRole, Set.of()),
                        member.grantedAt().toEpochMilli());
            }
        }
        putMember(existing, request.newLeaderPlayerId(), ApiOrganizationRole.OWNER.name(),
                ApiOrganizationRole.OWNER.defaultPermissions(), now);
        existing.putLong("updatedAt", now);
        centralBank.getOrganizationAccounts().put(request.accountId(), existing);
        recordEvent(centralBank, ApiOrganizationEventType.LEADERSHIP_TRANSFERRED, request.accountId(),
                existing.getUUID("bankId"), request.actor(), "", "", "LEADERSHIP_TRANSFERRED",
                "Organization leadership transferred", key,
                Map.of("newLeader", request.newLeaderPlayerId().toString()));
        BankManager.markDirty();
        return success("LEADERSHIP_TRANSFERRED", "Organization leadership transferred", key, centralBank,
                request.actor(), request.accountId(), List.of(), List.of(request.accountId()));
    }

    private ApiOrganizationOperationResult money0(CentralBank centralBank,
                                                  String key,
                                                  ApiOrganizationMoneyRequest request,
                                                  ApiOrganizationOperationType type) {
        CompoundTag senderRecord = organizationRecord(centralBank, request.accountId());
        AccountHolder sender = account(centralBank, request.accountId());
        if (senderRecord == null || sender == null) {
            return failure("ACCOUNT_NOT_FOUND", "Source organization account was not found", key, centralBank);
        }
        if (!canUseMoneyOperation(senderRecord, request.actor(), type)) {
            return failure("ACCESS_DENIED", "Actor cannot perform this organization money operation", key,
                    centralBank);
        }
        ApiOrganizationAccountStatus senderStatus = accountStatus(senderRecord);
        if (senderStatus != ApiOrganizationAccountStatus.OPEN) {
            return failure("ACCOUNT_UNAVAILABLE", "Source organization account is not open", key, centralBank);
        }
        if (!bankAcceptsTransactions(centralBank, sender.getBankId())) {
            return failure("BANK_UNAVAILABLE", "Source organization bank is unavailable", key, centralBank);
        }

        AccountHolder receiver = null;
        CompoundTag receiverRecord = null;
        if (type == ApiOrganizationOperationType.TRANSFER) {
            if (request.counterpartyAccountId() == null) {
                return failure("RECEIVER_REQUIRED", "Transfer destination account is required", key, centralBank);
            }
            receiver = account(centralBank, request.counterpartyAccountId());
            if (receiver == null) {
                return failure("RECEIVER_NOT_FOUND", "Transfer destination account was not found", key, centralBank);
            }
            receiverRecord = organizationRecord(centralBank, request.counterpartyAccountId());
            if (receiverRecord != null && accountStatus(receiverRecord) != ApiOrganizationAccountStatus.OPEN) {
                return failure("RECEIVER_UNAVAILABLE", "Transfer destination organization account is not open",
                        key, centralBank);
            }
            if (!bankAcceptsTransactions(centralBank, receiver.getBankId())) {
                return failure("BANK_UNAVAILABLE", "Transfer destination bank is unavailable", key, centralBank);
            }
            if (request.accountId().equals(request.counterpartyAccountId())) {
                return failure("SELF_TRANSFER", "An organization account cannot transfer to itself", key, centralBank);
            }
        }

        BigDecimal amount = normalizeMoney(request.amount());
        String externalReference = normalizeReference(request.externalReference());
        String category = normalizeCategory(request.category());
        String description = normalizeDescription(request.description());
        if (amount == null || amount.signum() <= 0) {
            return failure("INVALID_AMOUNT", "Amount must be positive and have at most two decimals", key, centralBank);
        }
        if (externalReference.isBlank()) {
            return failure("EXTERNAL_REFERENCE_REQUIRED", "A unique external transaction reference is required",
                    key, centralBank);
        }
        if (category.isBlank()) {
            return failure("INVALID_CATEGORY", "Transaction category is invalid", key, centralBank);
        }

        CompoundTag existingReference = findTransactionTag(centralBank,
                senderRecord.getString("namespace"), externalReference);
        if (existingReference != null) {
            if (!key.equals(existingReference.getString("idempotencyKey"))) {
                return failure("EXTERNAL_REFERENCE_EXISTS",
                        "External transaction reference has already been used by this namespace", key,
                        centralBank);
            }
            ApiOrganizationTransactionSnapshot recovered = transactionSnapshot(existingReference);
            if (recovered != null) {
                if (recovered.status() != ApiOrganizationOperationStatus.SUCCEEDED) {
                    return failure(recovered.operationCode(), recovered.description(), key, centralBank);
                }
                return successWithTransactions("TRANSACTION_ALREADY_APPLIED", "Transaction was already applied",
                        key, centralBank, request.actor(), request.accountId(),
                        List.of(recovered.transactionId()), affectedAccountIds(request.accountId(),
                                request.counterpartyAccountId()), List.of(recovered));
            }
        }

        BigDecimal dailyBefore = dailySpent(senderRecord);
        BigDecimal perTransactionLimit = readMoney(senderRecord.getString("perTransactionLimit"));
        BigDecimal dailyLimit = readMoney(senderRecord.getString("dailySpendingLimit"));
        if (perTransactionLimit.signum() > 0 && amount.compareTo(perTransactionLimit) > 0) {
            return failure("PER_TRANSACTION_LIMIT", "Organization transaction limit exceeded", key, centralBank);
        }
        if (dailyLimit.signum() > 0 && dailyBefore.add(amount).compareTo(dailyLimit) > 0) {
            return failure("DAILY_SPENDING_LIMIT", "Organization daily spending limit exceeded", key, centralBank);
        }

        ApiEconomyOperationType ledgerType = type == ApiOrganizationOperationType.TRANSFER
                ? ApiEconomyOperationType.TRANSFER
                : type == ApiOrganizationOperationType.SOURCE_FUNDS
                ? ApiEconomyOperationType.ADMIN_DEPOSIT
                : ApiEconomyOperationType.ADMIN_WITHDRAW;
        UUID receiverId = receiver == null ? null : receiver.getAccountUUID();
        String ledgerKey = ledgerIdempotencyKey(senderRecord.getString("namespace"), key);
        ApiEconomyOperationRequest ledgerRequest = new ApiEconomyOperationRequest(
                ledgerKey,
                ledgerType,
                ApiEconomyActorType.OFFICIAL_SYSTEM,
                null,
                request.accountId(),
                receiverId,
                sender.getBankId(),
                null,
                "",
                "",
                "",
                amount,
                externalReference,
                Map.of("organizationNamespace", senderRecord.getString("namespace"),
                        "organizationCategory", category),
                List.of());
        ApiEconomyOperationResult ledgerResult = economyApi.execute(ledgerRequest);
        if (ledgerResult == null || !ledgerResult.success()) {
            String code = ledgerResult == null ? "LEDGER_UNAVAILABLE" : ledgerResult.code();
            String message = ledgerResult == null ? "The UBS ledger did not return a result" : ledgerResult.message();
            recordFailedTransaction(centralBank, request, key, amount, code, message, receiverId,
                    senderRecord, receiverRecord);
            return failure(code, message, key, centralBank);
        }

        List<UUID> transactionIds = ledgerResult.transactionIds();
        List<ApiOrganizationTransactionSnapshot> transactions = recordSuccessfulTransactions(
                centralBank, request, key, amount, transactionIds, senderRecord, receiverRecord, type, description);
        if (type != ApiOrganizationOperationType.SOURCE_FUNDS) {
            incrementDailySpent(senderRecord, amount);
            centralBank.getOrganizationAccounts().put(request.accountId(), senderRecord);
        }
        ApiOrganizationEventType eventType = ApiOrganizationEventType.TRANSACTION_SUCCEEDED;
        List<UUID> affected = affectedAccountIds(request.accountId(), receiverId);
        recordEvent(centralBank, eventType, request.accountId(), sender.getBankId(), request.actor(),
                externalReference, category, "TRANSACTION_SUCCEEDED", "Organization transaction completed", key,
                Map.of("operation", type.name()), affected);
        BankManager.markDirty();
        return successWithTransactions("TRANSACTION_COMPLETED", "Organization transaction completed", key,
                centralBank, request.actor(), request.accountId(), transactionIds, affected, transactions);
    }

    private ApiOrganizationOperationResult setBankStatus0(CentralBank centralBank,
                                                           String key,
                                                           ApiOrganizationBankStatusRequest request) {
        if (request.actor() == null || request.actor().type() != ApiOrganizationActorType.OFFICIAL_SYSTEM) {
            return failure("ACCESS_DENIED", "Bank status changes require official-system authority", key,
                    centralBank);
        }
        Bank bank = request.bankId() == null ? null : centralBank.getBank(request.bankId());
        String status = normalizeBankStatus(request.status());
        if (bank == null || status.isBlank()) {
            return failure("INVALID_BANK_STATUS", "An existing bank and a valid status are required", key,
                    centralBank);
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String previous = metadata.getString("status");
        metadata.putString("status", status);
        ApiOrganizationEventType eventType = switch (status) {
            case "SUSPENDED" -> ApiOrganizationEventType.BANK_SUSPENDED;
            case "LOCKDOWN" -> ApiOrganizationEventType.BANK_LOCKDOWN;
            case "REVOKED" -> ApiOrganizationEventType.BANK_REVOKED;
            case "ACTIVE" -> ApiOrganizationEventType.BANK_REOPENED;
            default -> ApiOrganizationEventType.BANK_STATUS_CHANGED;
        };
        recordEvent(centralBank, eventType, null, bank.getBankId(), request.actor(), "", "",
                "BANK_STATUS_CHANGED", request.reason().isBlank() ? "Bank status changed" : request.reason(), key,
                Map.of("previousStatus", previous == null ? "" : previous, "status", status));
        BankManager.markDirty();
        return success("BANK_STATUS_UPDATED", "Bank status updated", key, centralBank, request.actor(), null,
                List.of(), List.of());
    }

    private synchronized ApiOrganizationOperationResult executeMutation(String rawKey,
                                                                         ApiOrganizationOperationType type,
                                                                         String fingerprint,
                                                                         ApiOrganizationActor actor,
                                                                         UUID accountId,
                                                                         UUID bankId,
                                                                         String externalReference,
                                                                         String category,
                                                                         OperationAction action) {
        String key = normalizeIdempotencyKey(rawKey);
        CentralBank centralBank = resolveCentralBank();
        if (key.isEmpty()) {
            return failure("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency key must be 8-160 safe ASCII characters", key, centralBank);
        }
        if (centralBank == null) {
            return failure("SERVER_UNAVAILABLE", "Bank data is unavailable", key, null);
        }

        CompoundTag existing = centralBank.getOrganizationOperations().get(key);
        if (existing != null) {
            String storedFingerprint = existing.getString("requestFingerprint");
            if (!fingerprint.equals(storedFingerprint)) {
                return failure("IDEMPOTENCY_CONFLICT",
                        "Idempotency key was already used for a different organization operation", key,
                        centralBank);
            }
            if (operationStatus(existing) == ApiOrganizationOperationStatus.PENDING
                    && !actorMatchesPending(existing, actor)) {
                return failure("ACCESS_DENIED",
                        "Only the actor that created a pending organization operation may resume it", key,
                        centralBank);
            }
            if (operationStatus(existing) != ApiOrganizationOperationStatus.PENDING) {
                ApiOrganizationOperationResult replay = readOperationResult(centralBank, existing);
                return replay == null
                        ? failure("CORRUPT_OPERATION_RECEIPT", "Stored organization operation is invalid", key,
                        centralBank)
                        : replay.asDuplicate();
            }
        } else {
            CompoundTag pending = writePending(key, type, fingerprint, actor, accountId, bankId,
                    externalReference, category);
            if (accountId != null) {
                pending.put("affectedAccountIds", uuidList(List.of(accountId)));
            }
            centralBank.getOrganizationOperations().put(key, pending);
            BankManager.markDirty();
        }

        CompoundTag executing = centralBank.getOrganizationOperations().get(key);
        if (executing != null && operationStatus(executing) == ApiOrganizationOperationStatus.PENDING
                && !executing.contains("executionStartedAt")) {
            executing.putLong("executionStartedAt", System.currentTimeMillis());
            centralBank.getOrganizationOperations().put(key, executing);
            BankManager.markDirty();
        }

        ApiOrganizationOperationResult result;
        try {
            result = action.apply(centralBank, key);
        } catch (RuntimeException exception) {
            UltimateBankingSystem.LOGGER.error("UBS organization operation {} failed", key, exception);
            result = failure("OPERATION_ERROR", "Organization operation could not be completed", key,
                    centralBank);
        }
        if (result == null) {
            result = failure("OPERATION_ERROR", "Organization operation returned no result", key, centralBank);
        }
        if (!result.success()) {
            ApiOrganizationEventType eventType = isMoneyOperation(type)
                    ? result.status() == ApiOrganizationOperationStatus.CANCELLED
                    ? ApiOrganizationEventType.TRANSACTION_CANCELLED
                    : ApiOrganizationEventType.TRANSACTION_FAILED
                    : result.status() == ApiOrganizationOperationStatus.CANCELLED
                    ? ApiOrganizationEventType.OPERATION_CANCELLED
                    : ApiOrganizationEventType.OPERATION_FAILED;
            recordEvent(centralBank, eventType, accountId, bankId, actor, externalReference, category,
                    result.code(), result.message(), key, Map.of("operation", type.name()));
        }
        centralBank.getOrganizationOperations().put(key,
                writeOperation(result, fingerprint, type,
                        centralBank.getOrganizationOperations().get(key)));
        pruneOrganizationHistory(centralBank);
        BankManager.markDirty();
        return result;
    }

    private CompoundTag writePending(String key,
                                     ApiOrganizationOperationType type,
                                     String fingerprint,
                                     ApiOrganizationActor actor,
                                     UUID accountId,
                                     UUID bankId,
                                     String externalReference,
                                     String category) {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", ApiOrganizationOperationStatus.PENDING.name());
        tag.putString("code", "PENDING");
        tag.putString("message", "Organization operation is in progress");
        tag.putString("idempotencyKey", key);
        tag.putUUID("operationId", operationId(key));
        tag.putString("requestFingerprint", fingerprint);
        tag.putString("operationType", type.name());
        tag.putLong("createdAt", System.currentTimeMillis());
        tag.putLong("completedAt", System.currentTimeMillis());
        if (accountId != null) tag.putUUID("accountId", accountId);
        if (bankId != null) tag.putUUID("bankId", bankId);
        tag.putString("externalReference", normalizeReference(externalReference));
        tag.putString("category", normalizeCategory(category));
        writeActor(tag, actor);
        return tag;
    }

    private CompoundTag writeOperation(ApiOrganizationOperationResult result,
                                       String fingerprint,
                                       ApiOrganizationOperationType type,
                                       CompoundTag pending) {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", result.status().name());
        tag.putString("code", result.code());
        tag.putString("message", result.message());
        tag.putString("idempotencyKey", result.idempotencyKey());
        tag.putUUID("operationId", result.operationId());
        tag.putLong("revision", result.revision());
        tag.putLong("completedAt", result.completedAt().toEpochMilli());
        tag.putString("requestFingerprint", fingerprint == null ? "" : fingerprint);
        tag.putString("operationType", type == null ? "" : type.name());
        tag.put("transactionIds", uuidList(result.transactionIds()));
        tag.put("affectedAccountIds", uuidList(result.affectedAccountIds()));
        if (result.account() != null) {
            tag.putUUID("accountId", result.account().accountId());
        } else if (pending != null && pending.hasUUID("accountId")) {
            tag.putUUID("accountId", pending.getUUID("accountId"));
        }
        if (pending != null) {
            copyUuidIfPresent(pending, tag, "bankId");
            tag.putString("externalReference", pending.getString("externalReference"));
            tag.putString("category", pending.getString("category"));
            copyActor(pending, tag);
        }
        return tag;
    }

    private ApiOrganizationOperationResult readOperationResult(CentralBank centralBank, CompoundTag tag) {
        if (tag == null || !tag.hasUUID("operationId")) {
            return null;
        }
        ApiOrganizationOperationStatus status = operationStatus(tag);
        ApiOrganizationActor actor = readActor(tag);
        ApiOrganizationOperationResult result = new ApiOrganizationOperationResult(
                status,
                tag.getString("code"),
                tag.getString("message"),
                tag.getString("idempotencyKey"),
                tag.getUUID("operationId"),
                false,
                tag.getLong("revision"),
                tag.contains("completedAt") ? Instant.ofEpochMilli(tag.getLong("completedAt")) : Instant.now(),
                readUuidList(tag.getList("transactionIds", Tag.TAG_COMPOUND)),
                readUuidList(tag.getList("affectedAccountIds", Tag.TAG_COMPOUND)),
                tag.hasUUID("accountId")
                        ? readAccountSnapshot(centralBank, tag.getUUID("accountId"), actor)
                        : null,
                readTransactionSnapshots(centralBank,
                        readUuidList(tag.getList("transactionIds", Tag.TAG_COMPOUND)))
        );
        return result;
    }

    private void pruneOrganizationHistory(CentralBank centralBank) {
        pruneStringTagMap(centralBank.getOrganizationOperations(), MAX_OPERATION_HISTORY, "completedAt");
        pruneUuidTagMap(centralBank.getOrganizationTransactions(), MAX_TRANSACTION_HISTORY, "occurredAt");
        pruneUuidTagMap(centralBank.getOrganizationEvents(), MAX_EVENT_HISTORY, "occurredAt");
    }

    private List<UUID> affectedAccountIds(UUID first, UUID second) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (first != null) {
            ids.add(first);
        }
        if (second != null) {
            ids.add(second);
        }
        return List.copyOf(ids);
    }

    private static void pruneStringTagMap(Map<String, CompoundTag> map, int maxSize, String timestampKey) {
        if (map == null || map.size() <= maxSize) {
            return;
        }
        int excess = map.size() - maxSize;
        map.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getLong(timestampKey)))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(map::remove);
    }

    private static void pruneUuidTagMap(Map<UUID, CompoundTag> map, int maxSize, String timestampKey) {
        if (map == null || map.size() <= maxSize) {
            return;
        }
        int excess = map.size() - maxSize;
        map.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getLong(timestampKey)))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(map::remove);
    }

    private ApiOrganizationOperationResult success(String code,
                                                   String message,
                                                   String key,
                                                   CentralBank centralBank,
                                                   ApiOrganizationActor actor,
                                                   UUID accountId,
                                                   List<UUID> transactionIds,
                                                   List<UUID> affectedAccountIds) {
        return successWithTransactions(code, message, key, centralBank, actor, accountId, transactionIds,
                affectedAccountIds, readTransactionSnapshots(centralBank, transactionIds));
    }

    private ApiOrganizationOperationResult successWithTransactions(String code,
                                                                   String message,
                                                                   String key,
                                                                   CentralBank centralBank,
                                                                   ApiOrganizationActor actor,
                                                                   UUID accountId,
                                                                   List<UUID> transactionIds,
                                                                   List<UUID> affectedAccountIds,
                                                                   List<ApiOrganizationTransactionSnapshot> transactions) {
        long revision = centralBank == null ? 0L : centralBank.advanceEconomyRevision();
        return new ApiOrganizationOperationResult(
                ApiOrganizationOperationStatus.SUCCEEDED,
                code,
                message,
                key,
                operationId(key),
                false,
                revision,
                Instant.now(),
                transactionIds,
                affectedAccountIds,
                accountId == null || centralBank == null
                        ? null
                        : readAccountSnapshot(centralBank, accountId, actor),
                transactions);
    }

    private ApiOrganizationOperationResult failure(String code,
                                                   String message,
                                                   String key,
                                                   CentralBank centralBank) {
        return new ApiOrganizationOperationResult(
                ApiOrganizationOperationStatus.FAILED,
                code,
                message,
                key == null ? "" : key,
                operationId(key == null ? "invalid" : key),
                false,
                centralBank == null ? 0L : centralBank.getEconomyRevision(),
                Instant.now(),
                List.of(),
                List.of(),
                null,
                List.of());
    }

    @FunctionalInterface
    private interface OperationAction {
        ApiOrganizationOperationResult apply(CentralBank centralBank, String key);
    }

    private record MemberData(UUID playerId,
                              String role,
                              Set<ApiOrganizationPermission> permissions,
                              Instant grantedAt) {
        private MemberData {
            role = role == null ? "" : role;
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            grantedAt = grantedAt == null ? Instant.EPOCH : grantedAt;
        }
    }

    private CentralBank resolveCentralBank() {
        try {
            return centralBankSupplier.get();
        } catch (RuntimeException exception) {
            UltimateBankingSystem.LOGGER.debug("Unable to resolve the central bank for the organization API",
                    exception);
            return null;
        }
    }

    private AccountHolder account(CentralBank centralBank, UUID accountId) {
        return centralBank == null || accountId == null
                ? null
                : centralBank.SearchForAccountByAccountId(accountId);
    }

    private CompoundTag organizationRecord(CentralBank centralBank, UUID accountId) {
        return centralBank == null || accountId == null
                ? null
                : centralBank.getOrganizationAccounts().get(accountId);
    }

    private List<CompoundTag> organizationRecords(CentralBank centralBank) {
        if (centralBank == null) {
            return List.of();
        }
        return centralBank.getOrganizationAccounts().values().stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private UUID organizationAccountId(String namespace, String externalId, UUID bankId) {
        return deterministicId("organization-account", namespace + ":" + externalId + ":" + bankId);
    }

    private UUID operationId(String key) {
        return deterministicId("organization-operation", key == null ? "invalid" : key);
    }

    private UUID deterministicId(String purpose, String value) {
        return UUID.nameUUIDFromBytes((purpose + ":" + value).getBytes(StandardCharsets.UTF_8));
    }

    private String ledgerIdempotencyKey(String namespace, String key) {
        return "org-ledger-" + digest(namespace + ":" + key).substring(0, 48);
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return IDEMPOTENCY_KEY.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeNamespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return NAMESPACE.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeExternalId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        return TOKEN.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeAccountType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ACCOUNT_TYPE.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String normalizeReference(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ACCOUNT_TYPE.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private String normalizeRole(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ACCOUNT_TYPE.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeBankStatus(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "SUSPENDED", "LOCKDOWN", "REVOKED" -> normalized;
            default -> "";
        };
    }

    private BigDecimal normalizeLimit(BigDecimal value) {
        if (value == null) {
            return NO_LIMIT;
        }
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            return normalized.signum() < 0 ? null : normalized;
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal readMoney(String value) {
        if (value == null || value.isBlank()) {
            return NO_LIMIT;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException exception) {
            return NO_LIMIT;
        }
    }

    private BigDecimal displayMoney(BigDecimal value) {
        return value == null ? NO_LIMIT : value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private long currentEpochDay() {
        return Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
    }

    private String actorLabel(ApiOrganizationActor actor) {
        if (actor == null) {
            return "unknown";
        }
        return switch (actor.type()) {
            case PLAYER -> actor.playerId() == null ? "player:unknown" : "player:" + actor.playerId();
            case INTEGRATION -> "integration:" + actor.integrationNamespace();
            case OFFICIAL_SYSTEM -> "official-system";
        };
    }

    private boolean canCreateNamespace(ApiOrganizationActor actor, String namespace) {
        if (actor == null) {
            return false;
        }
        return actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                && namespace.equals(actor.integrationNamespace());
    }

    private boolean canViewAccount(CompoundTag record, ApiOrganizationActor actor) {
        return record != null && (actor != null && (
                actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                        && actor.integrationNamespace().equals(record.getString("namespace"))
                || actor.type() == ApiOrganizationActorType.PLAYER
                        && (permissionsFor(record, actor).contains(ApiOrganizationPermission.VIEW_BALANCE)
                        || permissionsFor(record, actor).contains(ApiOrganizationPermission.VIEW_TRANSACTIONS))
        ));
    }

    private boolean canManageAccount(CompoundTag record, ApiOrganizationActor actor) {
        return actor != null && (actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                && actor.integrationNamespace().equals(record.getString("namespace"))
                || permissionsFor(record, actor).contains(ApiOrganizationPermission.MANAGE_ACCOUNT));
    }

    private boolean canManageMembers(CompoundTag record, ApiOrganizationActor actor) {
        return actor != null && (actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                && actor.integrationNamespace().equals(record.getString("namespace"))
                || permissionsFor(record, actor).contains(ApiOrganizationPermission.MANAGE_MEMBERS));
    }

    private boolean canTransferLeadership(CompoundTag record, ApiOrganizationActor actor) {
        if (actor == null) {
            return false;
        }
        if (actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                && actor.integrationNamespace().equals(record.getString("namespace"))) {
            return true;
        }
        MemberData member = member(record, actor.playerId());
        return actor.type() == ApiOrganizationActorType.PLAYER
                && member != null
                && "OWNER".equals(member.role());
    }

    private boolean canUseMoneyOperation(CompoundTag record,
                                          ApiOrganizationActor actor,
                                          ApiOrganizationOperationType operationType) {
        if (record == null || actor == null) {
            return false;
        }
        if (actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                && actor.integrationNamespace().equals(record.getString("namespace"))) {
            return true;
        }
        ApiOrganizationPermission required = switch (operationType) {
            case SOURCE_FUNDS -> ApiOrganizationPermission.DEPOSIT;
            case SINK_FUNDS -> ApiOrganizationPermission.WITHDRAW;
            default -> ApiOrganizationPermission.TRANSFER;
        };
        return permissionsFor(record, actor).contains(required);
    }

    private Set<ApiOrganizationPermission> permissionsFor(CompoundTag record, ApiOrganizationActor actor) {
        if (record == null || actor == null) {
            return Set.of();
        }
        if (actor.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || actor.type() == ApiOrganizationActorType.INTEGRATION
                && actor.integrationNamespace().equals(record.getString("namespace"))) {
            return Set.copyOf(EnumSet.allOf(ApiOrganizationPermission.class));
        }
        if (actor.type() != ApiOrganizationActorType.PLAYER || actor.playerId() == null) {
            return Set.of();
        }
        MemberData member = member(record, actor.playerId());
        return member == null ? Set.of() : member.permissions();
    }

    private Set<ApiOrganizationPermission> effectivePermissions(String role,
                                                                Set<ApiOrganizationPermission> requested) {
        LinkedHashSet<ApiOrganizationPermission> result = new LinkedHashSet<>();
        ApiOrganizationRole parsed = ApiOrganizationRole.parse(role);
        if (parsed != ApiOrganizationRole.CUSTOM) {
            result.addAll(parsed.defaultPermissions());
        }
        if (requested != null) {
            requested.stream().filter(java.util.Objects::nonNull).forEach(result::add);
        }
        return Set.copyOf(result);
    }

    private List<MemberData> members(CompoundTag record) {
        if (record == null) {
            return List.of();
        }
        List<MemberData> result = new ArrayList<>();
        ListTag list = record.getList("members", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            if (!tag.hasUUID("playerId")) {
                continue;
            }
            Set<ApiOrganizationPermission> permissions = new LinkedHashSet<>();
            ListTag permissionTags = tag.getList("permissions", Tag.TAG_STRING);
            for (int permissionIndex = 0; permissionIndex < permissionTags.size(); permissionIndex++) {
                try {
                    permissions.add(ApiOrganizationPermission.valueOf(permissionTags.getString(permissionIndex)));
                } catch (IllegalArgumentException ignored) {
                    // Ignore permissions removed by a newer API version.
                }
            }
            long grantedAt = tag.contains("grantedAt") ? tag.getLong("grantedAt") : 0L;
            result.add(new MemberData(tag.getUUID("playerId"), tag.getString("role"), permissions,
                    Instant.ofEpochMilli(grantedAt)));
        }
        return List.copyOf(result);
    }

    private MemberData member(CompoundTag record, UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return members(record).stream()
                .filter(candidate -> playerId.equals(candidate.playerId()))
                .findFirst()
                .orElse(null);
    }

    private void putMember(CompoundTag record,
                           UUID playerId,
                           String role,
                           Set<ApiOrganizationPermission> permissions,
                           long grantedAt) {
        if (record == null || playerId == null) {
            return;
        }
        ListTag replacement = new ListTag();
        for (MemberData existing : members(record)) {
            if (!playerId.equals(existing.playerId())) {
                replacement.add(writeMember(existing));
            }
        }
        replacement.add(writeMember(new MemberData(playerId, role, permissions, Instant.ofEpochMilli(grantedAt))));
        record.put("members", replacement);
    }

    private void removeMember(CompoundTag record, UUID playerId) {
        if (record == null || playerId == null) {
            return;
        }
        ListTag replacement = new ListTag();
        for (MemberData existing : members(record)) {
            if (!playerId.equals(existing.playerId())) {
                replacement.add(writeMember(existing));
            }
        }
        record.put("members", replacement);
    }

    private CompoundTag writeMember(MemberData member) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("playerId", member.playerId());
        tag.putString("role", member.role());
        tag.put("permissions", stringList(member.permissions().stream()
                .map(Enum::name).sorted().toList()));
        tag.putLong("grantedAt", member.grantedAt().toEpochMilli());
        return tag;
    }

    private ApiOrganizationAccountStatus accountStatus(CompoundTag record) {
        if (record == null) {
            return ApiOrganizationAccountStatus.CLOSED;
        }
        try {
            return ApiOrganizationAccountStatus.valueOf(record.getString("status"));
        } catch (IllegalArgumentException exception) {
            return ApiOrganizationAccountStatus.OPEN;
        }
    }

    private ApiOrganizationAccountSnapshot snapshot(CentralBank centralBank,
                                                     CompoundTag record,
                                                     ApiOrganizationActor viewer) {
        UUID accountId = record.hasUUID("accountId") ? record.getUUID("accountId") : null;
        UUID bankId = record.hasUUID("bankId") ? record.getUUID("bankId") : null;
        Bank bank = centralBank == null || bankId == null ? null : centralBank.getBank(bankId);
        Set<ApiOrganizationPermission> viewerPermissions = permissionsFor(record, viewer);
        boolean balanceVisible = viewerPermissions.contains(ApiOrganizationPermission.VIEW_BALANCE)
                || viewer != null && (viewer.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || viewer.type() == ApiOrganizationActorType.INTEGRATION);
        boolean limitsVisible = balanceVisible || viewerPermissions.contains(ApiOrganizationPermission.MANAGE_LIMITS);
        boolean showMembers = viewerPermissions.contains(ApiOrganizationPermission.MANAGE_MEMBERS)
                || viewer != null && viewer.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM
                || viewer != null && viewer.type() == ApiOrganizationActorType.INTEGRATION
                && viewer.integrationNamespace().equals(record.getString("namespace"));
        List<ApiOrganizationMemberSnapshot> memberSnapshots = showMembers
                ? members(record).stream().map(member -> new ApiOrganizationMemberSnapshot(
                member.playerId(), member.role(), member.permissions(), member.grantedAt())).toList()
                : List.of();
        AccountHolder account = account(centralBank, accountId);
        return new ApiOrganizationAccountSnapshot(
                accountId,
                ApiOrganizationOwnerType.EXTERNAL,
                record.getString("namespace"),
                record.getString("externalId"),
                record.getString("displayName"),
                record.getString("accountType"),
                record.getString("purpose"),
                bankId,
                bank == null ? "" : bank.getBankName(),
                bankStatus(centralBank, bank),
                accountStatus(record),
                account == null || !balanceVisible ? NO_LIMIT : displayMoney(account.getBalance()),
                balanceVisible && account != null,
                limitsVisible ? readMoney(record.getString("perTransactionLimit")) : NO_LIMIT,
                limitsVisible ? readMoney(record.getString("dailySpendingLimit")) : NO_LIMIT,
                limitsVisible ? dailySpent(record) : NO_LIMIT,
                readStringMap(record.getList("metadata", Tag.TAG_COMPOUND)),
                memberSnapshots,
                viewerPermissions,
                record.getString("createdBy"),
                instant(record, "createdAt"),
                instant(record, "updatedAt"),
                instant(record, "closedAt")
        );
    }

    private ApiOrganizationAccountSnapshot readAccountSnapshot(CentralBank centralBank,
                                                                UUID accountId,
                                                                ApiOrganizationActor viewer) {
        CompoundTag record = organizationRecord(centralBank, accountId);
        return record == null || !canViewAccount(record, viewer) ? null : snapshot(centralBank, record, viewer);
    }

    private Instant instant(CompoundTag tag, String key) {
        return tag != null && tag.contains(key) && tag.getLong(key) > 0L
                ? Instant.ofEpochMilli(tag.getLong(key))
                : null;
    }

    private String bankStatus(CentralBank centralBank, Bank bank) {
        if (centralBank == null || bank == null) {
            return "MISSING";
        }
        String status = centralBank.getOrCreateBankMetadata(bank.getBankId()).getString("status");
        return status == null || status.isBlank() ? "ACTIVE" : status.toUpperCase(Locale.ROOT);
    }

    private boolean bankAcceptsTransactions(CentralBank centralBank, UUID bankId) {
        Bank bank = centralBank == null || bankId == null ? null : centralBank.getBank(bankId);
        return bank != null && !Set.of("SUSPENDED", "LOCKDOWN", "REVOKED").contains(bankStatus(centralBank, bank));
    }

    private BigDecimal dailySpent(CompoundTag record) {
        if (record == null) {
            return NO_LIMIT;
        }
        long today = currentEpochDay();
        if (record.getLong("dailySpentDay") != today) {
            record.putLong("dailySpentDay", today);
            record.putString("dailySpent", NO_LIMIT.toPlainString());
            return NO_LIMIT;
        }
        return readMoney(record.getString("dailySpent"));
    }

    private void incrementDailySpent(CompoundTag record, BigDecimal amount) {
        BigDecimal updated = dailySpent(record).add(amount).setScale(2, RoundingMode.HALF_EVEN);
        record.putLong("dailySpentDay", currentEpochDay());
        record.putString("dailySpent", updated.toPlainString());
    }

    private CompoundTag findTransactionTag(CentralBank centralBank, String namespace, String reference) {
        if (centralBank == null) {
            return null;
        }
        return centralBank.getOrganizationTransactions().values().stream()
                .filter(tag -> tag != null
                        && normalizeNamespace(namespace).equals(tag.getString("namespace"))
                        && normalizeReference(reference).equals(tag.getString("externalReference")))
                .findFirst()
                .orElse(null);
    }

    private List<ApiOrganizationTransactionSnapshot> recordSuccessfulTransactions(
            CentralBank centralBank,
            ApiOrganizationMoneyRequest request,
            String key,
            BigDecimal amount,
            List<UUID> transactionIds,
            CompoundTag senderRecord,
            CompoundTag receiverRecord,
            ApiOrganizationOperationType type,
            String description) {
        UUID receiverId = request.counterpartyAccountId();
        if (type == ApiOrganizationOperationType.SOURCE_FUNDS) {
            receiverId = request.accountId();
        }
        UUID senderId = request.accountId();
        if (type == ApiOrganizationOperationType.SOURCE_FUNDS) {
            senderId = EXTERNAL_SYSTEM_ACCOUNT;
        }
        if (type == ApiOrganizationOperationType.SINK_FUNDS) {
            receiverId = EXTERNAL_SYSTEM_ACCOUNT;
        }
        List<UUID> ids = transactionIds == null || transactionIds.isEmpty()
                ? List.of(deterministicId("organization-transaction", key))
                : transactionIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<ApiOrganizationTransactionSnapshot> result = new ArrayList<>();
        for (UUID transactionId : ids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("transactionId", transactionId);
            if (senderId != null) tag.putUUID("senderAccountId", senderId);
            if (receiverId != null) tag.putUUID("receiverAccountId", receiverId);
            tag.putString("amount", amount.toPlainString());
            tag.putLong("occurredAt", System.currentTimeMillis());
            tag.putString("namespace", senderRecord.getString("namespace"));
            tag.putString("externalReference", normalizeReference(request.externalReference()));
            tag.putString("category", normalizeCategory(request.category()));
            tag.putString("description", normalizeDescription(description));
            tag.putString("status", ApiOrganizationOperationStatus.SUCCEEDED.name());
            tag.putString("operationCode", type.name());
            tag.putString("idempotencyKey", key);
            writeActor(tag, request.actor());
            centralBank.getOrganizationTransactions().put(transactionId, tag);
            result.add(transactionSnapshot(tag));
        }
        return List.copyOf(result);
    }

    private void recordFailedTransaction(CentralBank centralBank,
                                         ApiOrganizationMoneyRequest request,
                                         String key,
                                         BigDecimal amount,
                                         String code,
                                         String message,
                                         UUID receiverId,
                                         CompoundTag senderRecord,
                                         CompoundTag receiverRecord) {
        UUID transactionId = deterministicId("organization-failed-transaction", key);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("transactionId", transactionId);
        tag.putUUID("senderAccountId", request.accountId());
        if (receiverId != null) tag.putUUID("receiverAccountId", receiverId);
        tag.putString("amount", amount.toPlainString());
        tag.putLong("occurredAt", System.currentTimeMillis());
        tag.putString("namespace", senderRecord.getString("namespace"));
        tag.putString("externalReference", normalizeReference(request.externalReference()));
        tag.putString("category", normalizeCategory(request.category()));
        tag.putString("description", normalizeDescription(message));
        tag.putString("status", ApiOrganizationOperationStatus.FAILED.name());
        tag.putString("operationCode", code);
        tag.putString("idempotencyKey", key);
        writeActor(tag, request.actor());
        centralBank.getOrganizationTransactions().put(transactionId, tag);
    }

    private void recordEvent(CentralBank centralBank,
                             ApiOrganizationEventType type,
                             UUID accountId,
                             UUID bankId,
                             ApiOrganizationActor actor,
                             String externalReference,
                             String category,
                             String code,
                             String message,
                             String operationKey,
                             Map<String, String> metadata) {
        recordEvent(centralBank, type, accountId, bankId, actor, externalReference, category, code, message,
                operationKey, metadata, accountId == null ? List.of() : List.of(accountId));
    }

    private void recordEvent(CentralBank centralBank,
                             ApiOrganizationEventType type,
                             UUID accountId,
                             UUID bankId,
                             ApiOrganizationActor actor,
                             String externalReference,
                             String category,
                             String code,
                             String message,
                             String operationKey,
                             Map<String, String> metadata,
                             List<UUID> affectedAccountIds) {
        if (centralBank == null || type == null) {
            return;
        }
        String stableKey = operationKey == null || operationKey.isBlank()
                ? UUID.randomUUID().toString()
                : operationKey;
        UUID eventId = deterministicId("organization-event",
                type.name() + ":" + accountId + ":" + bankId + ":" + stableKey);
        if (centralBank.getOrganizationEvents().containsKey(eventId)) {
            return;
        }
        Map<String, String> safeMetadata;
        try {
            safeMetadata = normalizeMetadata(metadata);
        } catch (IllegalArgumentException exception) {
            safeMetadata = Map.of();
        }
        CompoundTag tag = new CompoundTag();
        tag.putUUID("eventId", eventId);
        tag.putString("type", type.name());
        tag.putLong("occurredAt", System.currentTimeMillis());
        if (accountId != null) tag.putUUID("accountId", accountId);
        if (bankId != null) tag.putUUID("bankId", bankId);
        tag.putString("externalReference", normalizeReference(externalReference));
        tag.putString("category", normalizeCategory(category));
        tag.putString("code", normalizeLabel(code));
        tag.putString("message", normalizeDescription(message));
        tag.put("metadata", writeStringMap(safeMetadata));
        tag.put("affectedAccountIds", uuidList(affectedAccountIds));
        writeActor(tag, actor);
        centralBank.getOrganizationEvents().put(eventId, tag);
        NeoForge.EVENT_BUS.post(new OrganizationApiEvent(eventSnapshot(tag)));
    }

    private ApiOrganizationEventSnapshot eventSnapshot(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("eventId")) {
            return null;
        }
        ApiOrganizationEventType type;
        try {
            type = ApiOrganizationEventType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException exception) {
            type = ApiOrganizationEventType.OPERATION_FAILED;
        }
        return new ApiOrganizationEventSnapshot(
                tag.getUUID("eventId"),
                type,
                tag.contains("occurredAt") ? Instant.ofEpochMilli(tag.getLong("occurredAt")) : Instant.EPOCH,
                tag.hasUUID("accountId") ? tag.getUUID("accountId") : null,
                readUuidList(tag.getList("affectedAccountIds", Tag.TAG_COMPOUND)),
                tag.hasUUID("bankId") ? tag.getUUID("bankId") : null,
                readActor(tag).type(),
                tag.hasUUID("actorPlayerId") ? tag.getUUID("actorPlayerId") : null,
                tag.getString("actorNamespace"),
                tag.getString("externalReference"),
                tag.getString("category"),
                tag.getString("code"),
                tag.getString("message"),
                readStringMap(tag.getList("metadata", Tag.TAG_COMPOUND)));
    }

    private void copyUuidIfPresent(CompoundTag source, CompoundTag target, String key) {
        if (source != null && target != null && source.hasUUID(key)) {
            target.putUUID(key, source.getUUID(key));
        }
    }

    private ApiOrganizationTransactionSnapshot transactionSnapshot(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("transactionId")) {
            return null;
        }
        ApiOrganizationOperationStatus status;
        try {
            status = ApiOrganizationOperationStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException exception) {
            status = ApiOrganizationOperationStatus.FAILED;
        }
        return new ApiOrganizationTransactionSnapshot(
                tag.getUUID("transactionId"),
                tag.hasUUID("senderAccountId") ? tag.getUUID("senderAccountId") : null,
                tag.hasUUID("receiverAccountId") ? tag.getUUID("receiverAccountId") : null,
                readMoney(tag.getString("amount")),
                tag.contains("occurredAt") ? Instant.ofEpochMilli(tag.getLong("occurredAt")) : Instant.EPOCH,
                tag.getString("namespace"),
                tag.getString("externalReference"),
                tag.getString("category"),
                tag.getString("description"),
                status,
                tag.getString("operationCode"));
    }

    private List<ApiOrganizationTransactionSnapshot> readTransactionSnapshots(CentralBank centralBank,
                                                                                List<UUID> transactionIds) {
        if (centralBank == null || transactionIds == null) {
            return List.of();
        }
        return transactionIds.stream()
                .map(centralBank.getOrganizationTransactions()::get)
                .map(this::transactionSnapshot)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private UUID transactionActorPlayer(CentralBank centralBank,
                                        ApiOrganizationTransactionSnapshot transaction) {
        if (centralBank == null || transaction == null) {
            return null;
        }
        CompoundTag tag = centralBank.getOrganizationTransactions().get(transaction.transactionId());
        return tag != null && tag.hasUUID("actorPlayerId") ? tag.getUUID("actorPlayerId") : null;
    }

    private boolean transactionBelongsToExternalId(CentralBank centralBank,
                                                    ApiOrganizationTransactionSnapshot transaction,
                                                    String externalId) {
        if (centralBank == null || transaction == null) {
            return false;
        }
        return organizationRecord(centralBank, transaction.senderAccountId()) != null
                && externalId.equals(organizationRecord(centralBank, transaction.senderAccountId())
                .getString("externalId"))
                || organizationRecord(centralBank, transaction.receiverAccountId()) != null
                && externalId.equals(organizationRecord(centralBank, transaction.receiverAccountId())
                .getString("externalId"));
    }

    private boolean transactionVisible(CentralBank centralBank,
                                       ApiOrganizationTransactionSnapshot transaction,
                                       ApiOrganizationActor viewer) {
        if (transaction == null || viewer == null) {
            return false;
        }
        if (viewer.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM) {
            return true;
        }
        if (viewer.type() == ApiOrganizationActorType.INTEGRATION) {
            return transactionMatchesNamespace(centralBank, transaction, viewer.integrationNamespace());
        }
        return (organizationRecord(centralBank, transaction.senderAccountId()) != null
                && permissionsFor(organizationRecord(centralBank, transaction.senderAccountId()), viewer)
                .contains(ApiOrganizationPermission.VIEW_TRANSACTIONS))
                || (organizationRecord(centralBank, transaction.receiverAccountId()) != null
                && permissionsFor(organizationRecord(centralBank, transaction.receiverAccountId()), viewer)
                .contains(ApiOrganizationPermission.VIEW_TRANSACTIONS));
    }

    private boolean eventVisible(CentralBank centralBank,
                                 ApiOrganizationEventSnapshot event,
                                 ApiOrganizationActor viewer) {
        if (event == null || viewer == null) {
            return false;
        }
        if (viewer.type() == ApiOrganizationActorType.OFFICIAL_SYSTEM) {
            return true;
        }
        if (viewer.type() == ApiOrganizationActorType.INTEGRATION) {
            return eventMatchesNamespace(centralBank, event, viewer.integrationNamespace());
        }
        CompoundTag record = organizationRecord(centralBank, event.accountId());
        if (record != null && permissionsFor(record, viewer)
                .contains(ApiOrganizationPermission.VIEW_TRANSACTIONS)) {
            return true;
        }
        if (event.affectedAccountIds().stream()
                .map(accountId -> organizationRecord(centralBank, accountId))
                .filter(java.util.Objects::nonNull)
                .anyMatch(candidate -> permissionsFor(candidate, viewer)
                        .contains(ApiOrganizationPermission.VIEW_TRANSACTIONS))) {
            return true;
        }
        return isBankStatusEvent(event) && event.bankId() != null && organizationRecords(centralBank).stream()
                .filter(candidate -> event.bankId().equals(candidate.getUUID("bankId")))
                .anyMatch(candidate -> permissionsFor(candidate, viewer)
                        .contains(ApiOrganizationPermission.VIEW_TRANSACTIONS));
    }

    private boolean transactionMatchesNamespace(CentralBank centralBank,
                                                ApiOrganizationTransactionSnapshot transaction,
                                                String namespace) {
        if (transaction == null || namespace == null || namespace.isBlank()) {
            return false;
        }
        if (namespace.equals(transaction.namespace())) {
            return true;
        }
        CompoundTag sender = organizationRecord(centralBank, transaction.senderAccountId());
        CompoundTag receiver = organizationRecord(centralBank, transaction.receiverAccountId());
        return sender != null && namespace.equals(sender.getString("namespace"))
                || receiver != null && namespace.equals(receiver.getString("namespace"));
    }

    private boolean eventMatchesNamespace(CentralBank centralBank,
                                           ApiOrganizationEventSnapshot event,
                                           String namespace) {
        if (event == null || namespace == null || namespace.isBlank()) {
            return namespace != null && namespace.isBlank();
        }
        if (namespace.equals(event.actorNamespace())) {
            return true;
        }
        CompoundTag record = organizationRecord(centralBank, event.accountId());
        if (record != null && namespace.equals(record.getString("namespace"))) {
            return true;
        }
        if (event.affectedAccountIds().stream()
                .map(accountId -> organizationRecord(centralBank, accountId))
                .filter(java.util.Objects::nonNull)
                .anyMatch(candidate -> namespace.equals(candidate.getString("namespace")))) {
            return true;
        }
        return isBankStatusEvent(event) && event.bankId() != null && organizationRecords(centralBank).stream()
                .anyMatch(candidate -> event.bankId().equals(candidate.getUUID("bankId"))
                        && namespace.equals(candidate.getString("namespace")));
    }

    private boolean isBankStatusEvent(ApiOrganizationEventSnapshot event) {
        return event != null && event.type() != null
                && switch (event.type()) {
            case BANK_SUSPENDED, BANK_LOCKDOWN, BANK_REOPENED, BANK_REVOKED, BANK_STATUS_CHANGED -> true;
            default -> false;
        };
    }

    private boolean queryAllowed(ApiOrganizationActor actor, String requestedNamespace) {
        if (actor == null) {
            return false;
        }
        String namespace = normalizeNamespace(requestedNamespace);
        return actor.type() != ApiOrganizationActorType.INTEGRATION
                || namespace.isBlank() || namespace.equals(actor.integrationNamespace());
    }

    private String normalizedQueryNamespace(ApiOrganizationActor actor, String requestedNamespace) {
        String namespace = normalizeNamespace(requestedNamespace);
        if (actor != null && actor.type() == ApiOrganizationActorType.INTEGRATION && namespace.isBlank()) {
            return actor.integrationNamespace();
        }
        return namespace;
    }

    private boolean inTimeRange(Instant value, Instant since, Instant until) {
        return value != null
                && (since == null || !value.isBefore(since))
                && (until == null || !value.isAfter(until));
    }

    private boolean isMoneyOperation(ApiOrganizationOperationType type) {
        return type == ApiOrganizationOperationType.TRANSFER
                || type == ApiOrganizationOperationType.SOURCE_FUNDS
                || type == ApiOrganizationOperationType.SINK_FUNDS;
    }

    private ApiOrganizationOperationStatus operationStatus(CompoundTag tag) {
        if (tag == null) {
            return ApiOrganizationOperationStatus.FAILED;
        }
        try {
            return ApiOrganizationOperationStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException exception) {
            return ApiOrganizationOperationStatus.FAILED;
        }
    }

    private ApiOrganizationOperationType parseOperationType(String value) {
        try {
            return ApiOrganizationOperationType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ApiOrganizationOperationType.UPDATE_ACCOUNT;
        }
    }

    private void writeActor(CompoundTag tag, ApiOrganizationActor actor) {
        ApiOrganizationActor normalized = actor == null ? ApiOrganizationActor.player(null) : actor;
        tag.putString("actorType", normalized.type().name());
        if (normalized.playerId() != null) {
            tag.putUUID("actorPlayerId", normalized.playerId());
        }
        tag.putString("actorNamespace", normalized.integrationNamespace());
    }

    private void copyActor(CompoundTag source, CompoundTag target) {
        target.putString("actorType", source.getString("actorType"));
        target.putString("actorNamespace", source.getString("actorNamespace"));
        if (source.hasUUID("actorPlayerId")) {
            target.putUUID("actorPlayerId", source.getUUID("actorPlayerId"));
        }
    }

    private ApiOrganizationActor readActor(CompoundTag tag) {
        ApiOrganizationActorType type;
        try {
            type = ApiOrganizationActorType.valueOf(tag.getString("actorType"));
        } catch (IllegalArgumentException exception) {
            type = ApiOrganizationActorType.PLAYER;
        }
        return new ApiOrganizationActor(type,
                tag.hasUUID("actorPlayerId") ? tag.getUUID("actorPlayerId") : null,
                tag.getString("actorNamespace"));
    }

    private boolean actorMatchesPending(CompoundTag pending, ApiOrganizationActor actor) {
        if (pending == null || actor == null) {
            return false;
        }
        ApiOrganizationActor stored = readActor(pending);
        return stored.type() == actor.type()
                && java.util.Objects.equals(stored.playerId(), actor.playerId())
                && stored.integrationNamespace().equals(actor.integrationNamespace());
    }

    private String fingerprint(String operation, Object... values) {
        StringBuilder canonical = new StringBuilder(operation == null ? "" : operation);
        for (Object value : values) {
            canonical.append('\n').append(canonicalValue(value));
        }
        return digest(canonical.toString());
    }

    private String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, entryValue) -> entries.add(canonicalValue(key) + "=" + canonicalValue(entryValue)));
            entries.sort(String::compareTo);
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Set<?> set) {
            List<String> entries = set.stream().map(this::canonicalValue).sorted().toList();
            return "[" + String.join(",", entries) + "]";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte byteValue : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", byteValue));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private ListTag uuidList(List<UUID> values) {
        ListTag list = new ListTag();
        if (values == null) {
            return list;
        }
        values.stream().filter(java.util.Objects::nonNull).distinct().forEach(value -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", value);
            list.add(tag);
        });
        return list;
    }

    private List<UUID> readUuidList(ListTag list) {
        if (list == null) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            if (tag.hasUUID("id")) {
                result.add(tag.getUUID("id"));
            }
        }
        return List.copyOf(result);
    }

    private ListTag stringList(List<String> values) {
        ListTag list = new ListTag();
        if (values != null) {
            values.stream().filter(java.util.Objects::nonNull).forEach(value -> list.add(net.minecraft.nbt.StringTag.valueOf(value)));
        }
        return list;
    }

    private ListTag writeStringMap(Map<String, String> values) {
        ListTag list = new ListTag();
        if (values == null) {
            return list;
        }
        values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("key", entry.getKey());
                    tag.putString("value", entry.getValue());
                    list.add(tag);
                });
        return list;
    }

    private Map<String, String> readStringMap(ListTag list) {
        if (list == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            String key = tag.getString("key");
            if (!key.isBlank()) {
                result.put(key, tag.getString("value"));
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, String> normalizeMetadata(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (values.size() > MAX_METADATA_ENTRIES) {
            throw new IllegalArgumentException("Metadata may contain at most " + MAX_METADATA_ENTRIES + " entries");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Metadata keys and values cannot be null");
            }
            String key = entry.getKey().replace('\n', ' ').replace('\r', ' ').trim();
            String value = entry.getValue().replace('\n', ' ').replace('\r', ' ').trim();
            if (key.isBlank() || key.length() > MAX_METADATA_KEY || value.length() > MAX_METADATA_VALUE) {
                throw new IllegalArgumentException("Metadata keys or values exceed the supported length");
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
    }
}
