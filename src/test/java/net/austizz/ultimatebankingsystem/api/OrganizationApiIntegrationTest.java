package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationApiIntegrationTest {
    private static final ClassLoader LOADER = NeoForgeTestClassLoader.get();

    @Test
    void createsNamespacedAccountPersistsItAndReplaysItsReceipt() throws Exception {
        seedEconomyConfig();
        Object centralBank = newCentralBank();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        Object api = organizationApi(centralBank);
        Object integration = actorIntegration("opac_nations");
        UUID leader = UUID.randomUUID();

        Object request = accountRequest(
                integration,
                "opac:create:nation-42:v1",
                "opac_nations",
                "nation-42",
                bankId,
                "NATION_TREASURY",
                "Kingdom of Arathia Treasury",
                leader
        );
        Object first = invoke(api, "createAccount", request);
        assertTrue(success(first), resultSummary(first));
        assertFalse(duplicate(first));
        Object firstSnapshot = first.getClass().getMethod("account").invoke(first);
        assertNotNull(firstSnapshot);
        UUID accountId = (UUID) firstSnapshot.getClass().getMethod("accountId").invoke(firstSnapshot);
        assertEquals("NATION_TREASURY", firstSnapshot.getClass().getMethod("accountType").invoke(firstSnapshot));
        assertEquals("Kingdom of Arathia Treasury",
                firstSnapshot.getClass().getMethod("displayName").invoke(firstSnapshot));

        Object replay = invoke(api, "createAccount", request);
        assertTrue(success(replay), resultSummary(replay));
        assertTrue(duplicate(replay));
        assertEquals(accountId,
                replay.getClass().getMethod("account").invoke(replay).getClass()
                        .getMethod("accountId").invoke(replay.getClass().getMethod("account").invoke(replay)));

        Object found = invoke(api, "findAccount", "opac_nations", "nation-42", bankId, integration);
        assertTrue(((Optional<?>) found).isPresent());

        Object reloadedBank = reloadCentralBank(centralBank);
        Object reloadedApi = organizationApi(reloadedBank);
        Object afterRestart = invoke(reloadedApi, "findAccount", "opac_nations", "nation-42", bankId,
                integration);
        assertTrue(((Optional<?>) afterRestart).isPresent());
        Object reloadedSnapshot = ((Optional<?>) afterRestart).orElseThrow();
        assertEquals(accountId, reloadedSnapshot.getClass().getMethod("accountId").invoke(reloadedSnapshot));
        assertEquals("opac_nations", reloadedSnapshot.getClass().getMethod("namespace")
                .invoke(reloadedSnapshot));
    }

    @Test
    void sourceFundsAreIdempotentAndCustomMembersCannotReadWithoutViewPermission() throws Exception {
        seedEconomyConfig();
        Object centralBank = newCentralBank();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        Object api = organizationApi(centralBank);
        Object integration = actorIntegration("guilds");
        UUID owner = UUID.randomUUID();
        Object create = invoke(api, "createAccount", accountRequest(
                integration,
                "guild:create:treasury:v1",
                "guilds",
                "guild-7",
                bankId,
                "GUILD_TREASURY",
                "Guild Seven Treasury",
                owner
        ));
        assertTrue(success(create), resultSummary(create));
        UUID accountId = accountId(create);

        Object moneyRequest = moneyRequest(
                actorIntegration("guilds"),
                "guild:reward:season-1",
                accountId,
                new BigDecimal("42.50"),
                "guild-reward:season-1",
                "REWARD"
        );
        Object deposited = invoke(api, "sourceFunds", moneyRequest);
        assertTrue(success(deposited), resultSummary(deposited));
        Object duplicate = invoke(api, "sourceFunds", moneyRequest);
        assertTrue(success(duplicate), resultSummary(duplicate));
        assertTrue(duplicate(duplicate));

        Object snapshot = ((Optional<?>) invoke(api, "findAccount", "guilds", "guild-7", bankId, integration))
                .orElseThrow();
        assertEquals(new BigDecimal("42.50"), snapshot.getClass().getMethod("balance").invoke(snapshot));
        assertTrue(((Optional<?>) invoke(api, "findTransactionByExternalReference", "guilds",
                "guild-reward:season-1", integration)).isPresent());

        UUID spender = UUID.randomUUID();
        Object accessRequest = accessRequest(
                integration,
                "guild:access:spender:v1",
                accountId,
                spender,
                "CUSTOM",
                Set.of(permission("TRANSFER"))
        );
        Object accessResult = invoke(api, "setMemberAccess", accessRequest);
        assertTrue(success(accessResult), resultSummary(accessResult));
        Object player = actorPlayer(spender);
        assertTrue(((Optional<?>) invoke(api, "getAccount", accountId, player)).isEmpty());
        Object validation = invoke(api, "validateAccess", accountId, player, permission("VIEW_BALANCE"));
        assertFalse((boolean) validation.getClass().getMethod("allowed").invoke(validation));
        Object transferValidation = invoke(api, "validateAccess", accountId, player, permission("TRANSFER"));
        assertTrue((boolean) transferValidation.getClass().getMethod("allowed").invoke(transferValidation));
    }

    @Test
    void integrationCannotReadAnotherNamespace() throws Exception {
        seedEconomyConfig();
        Object centralBank = newCentralBank();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        Object api = organizationApi(centralBank);
        Object owner = actorIntegration("alpha_mod");
        Object otherIntegration = actorIntegration("beta_mod");
        Object result = invoke(api, "createAccount", accountRequest(
                owner,
                "alpha:create:account:v1",
                "alpha_mod",
                "account-1",
                bankId,
                "TREASURY",
                "Alpha Treasury",
                null
        ));
        assertTrue(success(result), resultSummary(result));
        assertTrue(((Optional<?>) invoke(api, "findAccount", "alpha_mod", "account-1", bankId,
                otherIntegration)).isEmpty());
        assertTrue(((java.util.List<?>) invoke(api, "listAccounts", "alpha_mod", bankId,
                otherIntegration)).isEmpty());
    }

    @Test
    void owningIntegrationCanRevokeLegacyOwnerWithoutGivingPlayersTreasuryControl() throws Exception {
        seedEconomyConfig();
        Object centralBank = newCentralBank();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        Object api = organizationApi(centralBank);
        Object integration = actorIntegration("nations");
        UUID founder = UUID.randomUUID();
        Object created = invoke(api, "createAccount", accountRequest(integration, "nation:create:1",
                "nations", "nation-1", bankId, "NATION_TREASURY", "Nation Treasury", founder));
        assertTrue(success(created), resultSummary(created));
        UUID id = accountId(created);

        Object playerRemoval = invoke(api, "removeMember", accessRequest(actorPlayer(founder),
                "nation:remove:player", id, founder, "", Set.of()));
        assertFalse(success(playerRemoval));

        Object integrationRemoval = invoke(api, "removeMember", accessRequest(integration,
                "nation:remove:integration", id, founder, "", Set.of()));
        assertTrue(success(integrationRemoval), resultSummary(integrationRemoval));
        Object snapshot = ((Optional<?>) invoke(api, "getAccount", id, integration)).orElseThrow();
        assertTrue(((java.util.List<?>) snapshot.getClass().getMethod("members").invoke(snapshot)).isEmpty());
        assertTrue(((Optional<?>) invoke(api, "getAccount", id, actorPlayer(founder))).isEmpty());
    }

    @Test
    void organizationUpkeepDebitIsIdempotentAndCannotOverdraw() throws Exception {
        seedEconomyConfig();
        Object centralBank = newCentralBank();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        Object api = organizationApi(centralBank);
        Object integration = actorIntegration("nations");
        Object created = invoke(api, "createAccount", accountRequest(integration, "nation:create:upkeep",
                "nations", "upkeep", bankId, "NATION_TREASURY", "Nation Treasury", null));
        assertTrue(success(created), resultSummary(created));
        UUID id = accountId(created);
        assertTrue(success(invoke(api, "sourceFunds", moneyRequest(integration, "nation:seed",
                id, new BigDecimal("50.00"), "nation:seed", "TEST"))));

        Object due = moneyRequest(integration, "nation:upkeep:day1", id,
                new BigDecimal("12.50"), "nation:upkeep:day1", "NATION_UPKEEP");
        Object first = invoke(api, "sinkFunds", due);
        assertTrue(success(first), resultSummary(first));
        Object replay = invoke(api, "sinkFunds", due);
        assertTrue(success(replay), resultSummary(replay));
        assertTrue(duplicate(replay));
        Object snapshot = ((Optional<?>) invoke(api, "getAccount", id, integration)).orElseThrow();
        assertEquals(new BigDecimal("37.50"), snapshot.getClass().getMethod("balance").invoke(snapshot));

        Object overdraft = invoke(api, "sinkFunds", moneyRequest(integration, "nation:upkeep:day2",
                id, new BigDecimal("100.00"), "nation:upkeep:day2", "NATION_UPKEEP"));
        assertFalse(success(overdraft));
        Object afterFailure = ((Optional<?>) invoke(api, "getAccount", id, integration)).orElseThrow();
        assertEquals(new BigDecimal("37.50"), afterFailure.getClass().getMethod("balance").invoke(afterFailure));
    }

    @Test
    void playerDepositAndIntegrationWithdrawalShareTheOrganizationLedger() throws Exception {
        seedEconomyConfig();
        Object centralBank = newCentralBank();
        UUID bankId = (UUID) centralBank.getClass().getMethod("getBankId").invoke(centralBank);
        Object api = organizationApi(centralBank);
        Object integration = actorIntegration("nations");
        UUID playerId = UUID.randomUUID();
        Class<?> holder = load("net.austizz.ultimatebankingsystem.account.AccountHolder");
        Class<?> types = load("net.austizz.ultimatebankingsystem.accountTypes.AccountTypes");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object checking = Enum.valueOf((Class<? extends Enum>) types, "CheckingAccount");
        Object playerAccount = holder.getConstructor(UUID.class, BigDecimal.class, types,
                        String.class, UUID.class, UUID.class)
                .newInstance(playerId, new BigDecimal("20.00"), checking, "", bankId, UUID.randomUUID());
        centralBank.getClass().getMethod("AddAccount", holder).invoke(centralBank, playerAccount);
        UUID personalId = (UUID) holder.getMethod("getAccountUUID").invoke(playerAccount);

        Object created = invoke(api, "createAccount", accountRequest(integration, "nation:create:money",
                "nations", "money", bankId, "NATION_TREASURY", "Nation Treasury", null));
        assertTrue(success(created), resultSummary(created));
        UUID treasuryId = accountId(created);

        Class<?> operationType = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationType");
        Class<?> actorType = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyActorType");
        Class<?> operationRequest = load("net.austizz.ultimatebankingsystem.api.economy.ApiEconomyOperationRequest");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object transfer = Enum.valueOf((Class<? extends Enum>) operationType, "TRANSFER");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object playerActor = Enum.valueOf((Class<? extends Enum>) actorType, "PLAYER");
        Object deposit = operationRequest.getConstructor(String.class, operationType, actorType,
                        UUID.class, UUID.class, UUID.class, UUID.class, UUID.class,
                        String.class, String.class, String.class, BigDecimal.class, String.class,
                        Map.class, java.util.List.class)
                .newInstance("nation:deposit:1", transfer, playerActor, playerId, personalId, treasuryId,
                        null, null, "", "", "", new BigDecimal("10.00"), "Nation deposit",
                        Map.of("nationId", "money"), java.util.List.of());
        Object economy = economyApi(centralBank);
        Object deposited = invoke(economy, "execute", deposit);
        assertTrue(success(deposited), resultSummary(deposited));
        assertTrue(success(invoke(economy, "execute", deposit)));
        Object snapshot = ((Optional<?>) invoke(api, "getAccount", treasuryId, integration)).orElseThrow();
        assertEquals(new BigDecimal("10.00"), snapshot.getClass().getMethod("balance").invoke(snapshot));

        Class<?> moneyClass = load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationMoneyRequest");
        Object payout = moneyClass.getConstructor(load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor"),
                        String.class, UUID.class, UUID.class, BigDecimal.class,
                        String.class, String.class, String.class)
                .newInstance(integration, "nation:withdraw:1", treasuryId, personalId,
                        new BigDecimal("5.00"), "nation:withdraw:1", "NATION_WITHDRAWAL", "Leader withdrawal");
        Object directPlayerPayout = moneyClass.getConstructor(load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor"),
                        String.class, UUID.class, UUID.class, BigDecimal.class,
                        String.class, String.class, String.class)
                .newInstance(actorPlayer(playerId), "nation:unauthorized:1", treasuryId, personalId,
                        new BigDecimal("5.00"), "nation:unauthorized:1", "NATION_WITHDRAWAL", "Direct withdrawal");
        assertFalse(success(invoke(api, "transfer", directPlayerPayout)));
        assertTrue(success(invoke(api, "transfer", payout)));
        assertTrue(success(invoke(api, "transfer", payout)));
        Object after = ((Optional<?>) invoke(api, "getAccount", treasuryId, integration)).orElseThrow();
        assertEquals(new BigDecimal("5.00"), after.getClass().getMethod("balance").invoke(after));
        assertEquals(new BigDecimal("15.00"), holder.getMethod("getBalance").invoke(playerAccount));
    }

    private static Object newCentralBank() throws Exception {
        return load("net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank")
                .getConstructor().newInstance();
    }

    private static Object organizationApi(Object centralBank) throws Exception {
        Class<?> implementation = load(
                "net.austizz.ultimatebankingsystem.api.organization.UltimateOrganizationApiImpl");
        Class<?> economyApi = load("net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApi");
        Constructor<?> constructor = implementation.getConstructor(Supplier.class, economyApi);
        Supplier<Object> supplier = () -> centralBank;
        return constructor.newInstance(supplier, economyApi(centralBank));
    }

    private static Object economyApi(Object centralBank) throws Exception {
        Class<?> implementation = load(
                "net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApiImpl");
        Constructor<?> constructor = implementation.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        Supplier<Object> supplier = () -> centralBank;
        return constructor.newInstance(supplier);
    }

    private static Object accountRequest(Object actor,
                                         String key,
                                         String namespace,
                                         String externalId,
                                         UUID bankId,
                                         String accountType,
                                         String displayName,
                                         UUID initialOwner) throws Exception {
        Class<?> request = load(
                "net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationAccountRequest");
        return request.getConstructor(
                        load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor"),
                        String.class, String.class, String.class, UUID.class, String.class, String.class,
                        String.class, Map.class, UUID.class, BigDecimal.class, BigDecimal.class)
                .newInstance(actor, key, namespace, externalId, bankId, accountType, displayName,
                        "integration test", Map.of("source", "test"), initialOwner,
                        new BigDecimal("1000.00"), new BigDecimal("5000.00"));
    }

    private static Object moneyRequest(Object actor,
                                       String key,
                                       UUID accountId,
                                       BigDecimal amount,
                                       String externalReference,
                                       String category) throws Exception {
        Class<?> request = load(
                "net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationMoneyRequest");
        return request.getConstructor(
                        load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor"),
                        String.class, UUID.class, UUID.class, BigDecimal.class, String.class, String.class,
                        String.class)
                .newInstance(actor, key, accountId, null, amount, externalReference, category,
                        "integration test reward");
    }

    private static Object accessRequest(Object actor,
                                        String key,
                                        UUID accountId,
                                        UUID targetPlayer,
                                        String role,
                                        Set<Object> permissions) throws Exception {
        Class<?> request = load(
                "net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationAccessRequest");
        return request.getConstructor(
                        load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor"),
                        String.class, UUID.class, UUID.class, String.class, Set.class)
                .newInstance(actor, key, accountId, targetPlayer, role, permissions);
    }

    private static Object actorIntegration(String namespace) throws Exception {
        return load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor")
                .getMethod("integration", String.class).invoke(null, namespace);
    }

    private static Object actorPlayer(UUID playerId) throws Exception {
        return load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationActor")
                .getMethod("player", UUID.class).invoke(null, playerId);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object permission(String name) throws Exception {
        Class<?> type = load("net.austizz.ultimatebankingsystem.api.organization.ApiOrganizationPermission");
        return Enum.valueOf((Class<? extends Enum>) type, name);
    }

    private static Object invoke(Object target, String method, Object... arguments) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            parameterTypes[index] = arguments[index] == null
                    ? Object.class
                    : arguments[index].getClass();
        }
        for (var candidate : target.getClass().getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != arguments.length) {
                continue;
            }
            boolean compatible = true;
            Class<?>[] declared = candidate.getParameterTypes();
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index] != null && !declared[index].isAssignableFrom(arguments[index].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return candidate.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(method);
    }

    private static UUID accountId(Object result) throws Exception {
        Object snapshot = result.getClass().getMethod("account").invoke(result);
        return (UUID) snapshot.getClass().getMethod("accountId").invoke(snapshot);
    }

    private static boolean success(Object result) throws Exception {
        return (boolean) result.getClass().getMethod("success").invoke(result);
    }

    private static boolean duplicate(Object result) throws Exception {
        return (boolean) result.getClass().getMethod("duplicate").invoke(result);
    }

    private static String resultSummary(Object result) throws Exception {
        return result.getClass().getMethod("code").invoke(result) + ": "
                + result.getClass().getMethod("message").invoke(result);
    }

    private static Object reloadCentralBank(Object centralBank) throws Exception {
        Class<?> compoundTag = load("net.minecraft.nbt.CompoundTag");
        Class<?> registries = load("net.minecraft.core.HolderLookup$Provider");
        Object saved = centralBank.getClass().getMethod("save", compoundTag, registries)
                .invoke(centralBank, compoundTag.getConstructor().newInstance(), null);
        return centralBank.getClass().getMethod("load", compoundTag, registries)
                .invoke(null, saved, null);
    }

    private static void seedConfigValue(String fieldName, Object value) throws Exception {
        Object configValue = load("net.austizz.ultimatebankingsystem.Config").getField(fieldName).get(null);
        var cachedValue = configValue.getClass().getSuperclass().getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        cachedValue.set(configValue, value);
    }

    private static void seedEconomyConfig() throws Exception {
        seedConfigValue("DEFAULT_FEDERAL_FUNDS_RATE", 1.0D);
        seedConfigValue("MIN_CUSTOM_BANK_INTEREST_RATE", 0.0D);
        seedConfigValue("MAX_CUSTOM_BANK_INTEREST_RATE", 100.0D);
        seedConfigValue("CREDIT_SCORE_DEFAULT", 500);
        seedConfigValue("ACCOUNT_TRANSACTION_LOG_LIMIT", 100);
        seedConfigValue("GLOBAL_MAX_SINGLE_TRANSACTION", 1_000_000);
        seedConfigValue("GLOBAL_MAX_DAILY_PLAYER_VOLUME", 1_000_000);
        seedConfigValue("BANK_MIN_RESERVE_RATIO", 0.1D);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, LOADER);
    }
}
