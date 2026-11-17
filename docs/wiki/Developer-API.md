# Developer API

UBS exposes server-side Java interfaces for NeoForge `1.21.1`. Version `2.2.0` is the current API release and includes the authoritative economy module plus the general organization-account API: namespaced external ownership, shared access, fine-grained permissions, lifecycle controls, durable operation receipts, audit queries, persisted events, bank-status events, limits, and atomic money movement. The earlier finance, cash, notification, market-price, bank, shop, heist, and discovery interfaces remain available.

## Entry Points

```java
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

var finance = UltimateBankingApiProvider.get();
var server = UltimateBankingApiProvider.server();
var banks = UltimateBankingApiProvider.banks();
var shops = UltimateBankingApiProvider.shops();
var heists = UltimateBankingApiProvider.heists();
var economy = UltimateBankingApiProvider.economy();
var organizations = UltimateBankingApiProvider.organizations();
```

`finance.getApiVersion()`, `economy.getApiVersion()`, and `organizations.getApiVersion()` return `2.2.0`.

## Contract and Threading

- Read models are records whose collection fields use defensive immutable copies.
- API methods return empty/failed results when UBS world data is unavailable; they do not expose mutable `Bank`, `CompoundTag`, or internal service objects.
- Mutations must run on the logical Minecraft server thread.
- Mutations reuse UBS authorization and validation. An addon does not bypass ownership, role, cooldown, capacity, setup, or heist rules.
- Operations that require an acting player may require that player to be online.
- `getTargets()` performs live heist/world eligibility scans and therefore returns no targets when called off the server thread.
- Monetary values use `BigDecimal`, dollars, or cents as stated by the method/record. Economy mutation amounts must be positive dollar values with no more than two decimal places. Snapshot amounts are normalized to two decimal places with half-even rounding for legacy data.
- The API is an in-process Java surface. UBS does not ship an HTTP/WebSocket admin server.
- `OFFICIAL_SYSTEM` is a trusted in-process authority intended for the appointed official-server adapter. Do not expose it directly to browser input, commands, or an untrusted addon.

## Result Types

- `ApiResult`: simple finance result and resulting balance.
- `ApiTransactionResult`: success/reason, transaction ID, source/destination IDs, amount, resulting balances, and reference.
- `ApiManagementResult`: `success` plus a human-readable message for bank/shop/heist actions.
- `ApiCashResult`: physical cash inventory operation result.
- `ApiItemResult`: issued paper instrument and reference ID.
- `ApiNotificationResult` and `ApiAlertResult`: UI-delivery outcomes.
- `ApiEconomyOperationResult`: durable authoritative receipt with status/code, stable operation ID, duplicate flag, economy revision, transaction IDs, affected accounts, and optional escrow state.

## Authoritative Economy Module (2.2.0)

`UltimateBankingApiProvider.economy()` is the integration seam for remote projections and commands. Its interface deliberately has four methods:

```java
String getApiVersion();
ApiEconomySnapshot snapshot(ApiEconomySnapshotRequest request);
Optional<ApiEconomyOperationResult> findOperation(String idempotencyKey);
ApiEconomyOperationResult execute(ApiEconomyOperationRequest request);
```

All calls belong on the logical server thread. The module owns validation, authorization, atomic balance movement, limits, deterministic transaction IDs, operation receipts, and persistence. Network code should translate a verified command into one request, execute it once, and serialize the returned result; it must not reproduce banking rules.

### Reconciliation snapshots

- `ApiEconomySnapshotRequest.forPlayer(playerId)` returns accounts visible to that player, their effective role/capabilities, retained statements for the preceding year, and escrow summaries.
- `ApiEconomySnapshotRequest.reconciliation()` returns every account, access grant, retained transaction, and escrow for a trusted official adapter.
- `revision` is monotonic for economy mutations. A projection that observes a gap must request reconciliation rather than inventing the missing state.
- `PLAYER` principals represent human-owned accounts. `INSTITUTION` principals represent durable non-player owners such as `nation:<id>` and `escrow:<id>`.
- An accessible institutional account is not part of a player's owned wealth. Use `principalType`, `principalId`, and `playerId`; do not infer ownership from the grants map.
- Account grants use `VIEW`, `DEPOSIT`, `WITHDRAW`, and `MANAGE`. `OWNER` is reserved for the principal of a personal account.

Retained transaction statements are bounded by `AccountTransactionLogLimit` (default `10,000`, configurable up to `50,000`) and the requested limit. The snapshot's time filter cannot recover entries already pruned by server retention.

### Idempotent operations

Every `execute` request requires a stable 8–160 character idempotency key containing only safe ASCII characters. Replaying the same key and exact payload returns the original receipt with `duplicate=true` and performs no second mutation. Reusing a key with different content fails with `IDEMPOTENCY_CONFLICT`. Completed receipts survive server restarts; keep command keys stable across network retries.

Supported operation types:

- `TRANSFER`, `TRANSFER_TO_PRIMARY`, `SET_PRIMARY_ACCOUNT`
- `PROVISION_INSTITUTION_ACCOUNT`, `SET_ACCESS_ROLE`, `SET_ACCOUNT_FROZEN`
- `ADMIN_DEPOSIT`, `ADMIN_WITHDRAW`
- `CREATE_ESCROW`, `FUND_ESCROW`, `RELEASE_ESCROW`, `REFUND_ESCROW`

`PLAYER` transfers enforce the acting player's current account grant. Personal transfers are limited to `$10,000.00` each and `$25,000.00` over a rolling 24 hours. Institutional nation payouts derive officer/leader limits from the current `WITHDRAW` or `MANAGE` grant. Primary-account selection only accepts a player-owned personal account.

Provisioning and account freezing, administrative adjustments, and escrow lifecycle operations require `OFFICIAL_SYSTEM`. Administrative adjustments require an exact account, positive amount, and non-empty audit reason. Approval separation belongs in the official workflow before that trusted request reaches UBS.

### Institutional account example

```java
var request = new ApiEconomyOperationRequest(
        "nation:provision:" + nationId,
        ApiEconomyOperationType.PROVISION_INSTITUTION_ACCOUNT,
        ApiEconomyActorType.OFFICIAL_SYSTEM,
        null,
        null,
        null,
        bankId,
        founderPlayerId,
        "nation:" + nationId,
        "",
        ApiAccountRole.MANAGE.name(),
        BigDecimal.ZERO,
        "Provision nation treasury",
        Map.of("label", nationName + " Treasury"),
        List.of()
);

ApiEconomyOperationResult receipt = economy.execute(request);
if (!receipt.success()) {
    // Keep the website command failed/pending; never update a projected balance locally.
}
```

### Matched escrow

Create an escrow in a real bank, then fund it with exactly two `ApiEconomyTransferLeg` entries of equal value. Each side may contribute at most `$25,000.00`. Funding prevalidates the complete movement before changing any balance. A release must consume the complete holding balance; a refund returns the recorded contributions. Terminal escrow operations are idempotent through their command keys.

## Organization Accounts API

`UltimateBankingApiProvider.organizations()` is the general integration surface for durable accounts owned by something other than a player: nations, guilds, companies, factions, towns, automated services, or another mod's entity. It is intentionally not tied to a particular external mod.

### Identity model

An organization account is identified by the tuple `(namespace, externalId, bankId)`.

| Field | Meaning | Example |
| --- | --- | --- |
| `namespace` | Lowercase integration ownership namespace. It prevents two mods from colliding. | `opac_nations` |
| `externalId` | Stable identifier assigned by the integration. It may be a UUID or another safe external ID. | `nation-42` |
| `bankId` | UBS bank holding the account. | `centralBank.getBankId()` |
| `accountType` | Integration-defined uppercase type label. | `NATION_TREASURY` |
| `displayName` | Player-facing label. | `Kingdom of Arathia Treasury` |
| `purpose` | Short reason for the account. | `government treasury` |
| `metadata` | Small string map for integration identifiers and display data. | `nationId=42` |

UBS derives a stable account UUID from the namespace, external ID, and bank. Do not generate a new account on every startup and do not use the display name as identity. The external ID must remain stable for the lifetime of the external organization.

### Actors and trust boundary

Use `ApiOrganizationActor.integration("your_mod_namespace")` for normal integration calls. The integration may manage accounts in its own namespace, and it cannot read or alter another integration's accounts. Use `ApiOrganizationActor.player(playerId)` for a human operating through member access. `ApiOrganizationActor.officialSystem()` is reserved for trusted UBS-owned server adapters and bank-status/system adjustments; an addon should not expose it to client or web input.

```java
var integration = ApiOrganizationActor.integration("opac_nations");
var leader = ApiOrganizationActor.player(leaderUuid);
```

### Account lifecycle and access

Account status is `OPEN`, `FROZEN`, or `CLOSED`. Frozen accounts remain visible to authorized readers but reject money operations. Closing is terminal and requires a zero balance; leadership or membership changes cannot reopen a closed account. Use `setStatus` for an operational freeze/reopen and `closeAccount` for permanent closure.

Built-in roles grant these default permissions:

| Role | Default permissions |
| --- | --- |
| `OWNER` | View balance, view transactions, deposit, withdraw, transfer, manage members, manage account, manage limits |
| `ADMINISTRATOR` | Same management permissions as owner, without ownership semantics |
| `TREASURER` | View balance, view transactions, deposit, withdraw, transfer |
| `SPENDER` | View balance, view transactions, transfer |
| `DEPOSITOR` | View balance, view transactions, deposit |
| `VIEWER` | View balance, view transactions |
| `CUSTOM` | Only explicitly supplied permissions |

`setMemberAccess` replaces a player's complete organization grant. `removeMember` revokes it. `transferLeadership` changes the owner role in place and demotes the previous owner to the requested non-owner role, so a leadership change never requires recreating the treasury or losing its balance.

The individual permissions are `VIEW_BALANCE`, `VIEW_TRANSACTIONS`, `DEPOSIT`, `WITHDRAW`, `TRANSFER`, `MANAGE_MEMBERS`, `MANAGE_ACCOUNT`, and `MANAGE_LIMITS`. Use `validateAccess` for UI decisions, but treat the mutation result as authoritative because access can change between a screen opening and a button press.

### Create an account

```java
var request = new ApiOrganizationAccountRequest(
        integration,
        "opac:create-account:nation-42:v1",
        "opac_nations",
        "nation-42",
        bankId,
        "NATION_TREASURY",
        "Kingdom of Arathia Treasury",
        "government treasury",
        Map.of("nationId", "42", "createdBy", leaderUuid.toString()),
        leaderUuid,
        new BigDecimal("100000.00"),
        new BigDecimal("1000000.00")
);

ApiOrganizationOperationResult receipt = organizations.createAccount(request);
if (!receipt.success()) {
    // Keep the external setup pending and surface receipt.code()/message().
}
UUID treasuryId = receipt.account().accountId();
```

`initialOwnerPlayerId` is optional. An account can be entirely integration-managed, or the integration can grant human access after creation. `perTransactionLimit` and `dailySpendingLimit` use dollars with at most two decimal places; zero means no limit. Metadata is bounded to keep world saves and audit records predictable.

### Read and manage accounts

| Method | Use |
| --- | --- |
| `getAccount(accountId, viewer)` | Read one account snapshot if the viewer is authorized. |
| `findAccount(namespace, externalId, bankId, viewer)` | Resolve a stable external identity without storing UBS internals. |
| `listAccounts(namespace, bankId, viewer)` | Enumerate visible accounts, restricted to an integration's own namespace. |
| `updateAccount(request)` | Change display name, custom type, purpose, metadata, or limits. |
| `setStatus(request)` | Freeze or reopen an account. |
| `closeAccount(request)` | Permanently close a zero-balance account. |
| `setMemberAccess(request)` | Add or replace a player's role and explicit permissions. |
| `removeMember(request)` | Remove a player's access. |
| `transferLeadership(request)` | Assign a new owner and safely demote the previous owner. |
| `validateAccess(accountId, actor, permission)` | Validate one permission for a UI or command before submitting a mutation. |

Snapshots include owner type, namespace, external ID, display name, custom type, purpose, bank identity/status, account status, balance, limits, daily spend, metadata, authorized member details when permitted, and timestamps. `balanceVisible()` distinguishes a real zero balance from a balance redacted for a member who has transaction visibility without balance visibility. They contain immutable copies and must be treated as read models.

### Money operations

All money requests require a unique idempotency key, a positive exact amount, a namespace-unique external reference, a category, and an optional description. Categories are integration-defined uppercase labels such as `TAX`, `UPKEEP`, `GOVERNMENT_SPENDING`, `SALARY`, or `NATION_TRANSFER`.

| Method | Meaning | Required member permission |
| --- | --- | --- |
| `transfer(request)` | Atomically transfer from the organization account to a UBS account, including another organization account. | `TRANSFER` |
| `sourceFunds(request)` | Mint/source an explicit external reward, grant, reimbursement, or adjustment into the organization account. | `DEPOSIT` |
| `sinkFunds(request)` | Remove an explicit fee, upkeep charge, tax settlement, or administrative adjustment from the organization account. | `WITHDRAW` |

The owning integration and official system may perform operations in their own account namespace. Player actors are checked against the account grant. The underlying UBS ledger validates both sides before a transfer, so a failed destination or insufficient source cannot leave a one-sided movement.

```java
var transfer = organizations.transfer(new ApiOrganizationMoneyRequest(
        leader,
        "opac:upkeep:nation-42:day-20500",
        treasuryId,
        recipientAccountId,
        new BigDecimal("125.00"),
        "opac-nation-42-upkeep-day-20500",
        "UPKEEP",
        "Daily territory upkeep"
));
```

`findTransactionByExternalReference(namespace, reference, viewer)` is the preflight check for scheduled work. If it returns a transaction, do not submit the same business action again. A repeated idempotency key with the same payload returns the original receipt with `duplicate=true`; reusing it with a different payload returns `IDEMPOTENCY_CONFLICT`.

### Operation receipts and cancellation

Every mutating request returns `ApiOrganizationOperationResult` with a stable operation ID, status (`PENDING`, `SUCCEEDED`, `FAILED`, or `CANCELLED`), code, message, revision, affected accounts, transaction IDs, and snapshots where applicable. `findOperation(idempotencyKey)` allows an integration to reconcile after a timeout or restart. `cancelOperation` is available only before a persisted operation has started execution and must be called by the actor that created it. Once execution has started, retry the original request so UBS can reconcile the journal and underlying ledger instead of cancelling a potentially-applied mutation. A final receipt is never silently replaced.

Operation records, organization transactions, and events are persisted in the Central Bank saved data and pruned with bounded histories. This gives retry safety across normal server restarts after the world save. It is not a substitute for a remote database transaction: an external integration should keep its business operation pending until it has received and persisted the UBS receipt.

### Audit queries

`audit(ApiOrganizationAuditQuery)` returns immutable transaction snapshots filtered by namespace, external ID, account, external reference, category, acting player, time range, and success/failure status. Player viewers only receive transactions for accounts on which they have `VIEW_TRANSACTIONS`; an integration viewer is restricted to its own namespace; the official system can reconcile all organization transactions.

`events(ApiOrganizationEventQuery)` returns persisted lifecycle/security events filtered by namespace, account, event type, and time range. Events include account creation/update/closure, freezes/reopens, access changes, leadership transfers, successful/failed/cancelled transactions, operation failures, and bank suspension, lockdown, reopening, or revocation. Each event exposes the actor type, player or integration identity, primary account, and every affected account, so a receiving organization can reconcile a cross-bank transfer without guessing from the source event.

For live server-thread reactions, subscribe to `OrganizationApiEvent` on `NeoForge.EVENT_BUS`. Its `snapshot()` is the same immutable event shape returned by the query API; do not retain or mutate internal NBT.

### Bank status controls

`setBankStatus` is an official-system operation for server-controlled `ACTIVE`, `SUSPENDED`, `LOCKDOWN`, or `REVOKED` transitions. Integration-owned accounts automatically reject money operations while their bank is unavailable, and the transition creates a persisted bank-status event. Normal integrations should observe status through account snapshots or events rather than changing it directly.

### Capability negotiation

Do not infer support from the mod version alone. Call:

```java
if (organizations.supports(ApiOrganizationCapability.IDEMPOTENT_TRANSACTIONS)
        && organizations.supports(ApiOrganizationCapability.ATOMIC_TRANSFERS)) {
    // Enable the integration's scheduled treasury workflow.
}
```

`getCapabilities()` currently advertises `ORGANIZATION_ACCOUNTS`, `NAMESPACED_EXTERNAL_OWNERS`, `CUSTOM_ACCOUNT_TYPES`, `SHARED_ACCESS`, `FINE_GRAINED_PERMISSIONS`, `ACCOUNT_LIFECYCLE`, `LEADERSHIP_TRANSFER`, `IDEMPOTENT_TRANSACTIONS`, `UNIQUE_EXTERNAL_REFERENCES`, `CANCELLABLE_OPERATIONS`, `SOURCE_AND_SINK_OPERATIONS`, `ATOMIC_TRANSFERS`, `ORGANIZATION_LIMITS`, `AUDIT_QUERIES`, `PERSISTED_EVENTS`, and `BANK_STATUS_EVENTS`. An integration can disable only the affected feature when running against a future UBS build with a reduced capability set.

## Jobs API (2.2.0)

`UltimateBankingApiProvider.jobs()` exposes the server-authoritative career system. Jobs use the same core contract as Lumberjack: a player applies once, keeps progress separately for each job, performs supported world activities, turns eligible plain items in at that job's buyer, and receives a validated digital payout plus job XP. The catalog, quote, inventory removal, primary-account deposit, daily limits, quest progress, and achievement rewards are all revalidated on the logical server thread.

```java
var jobs = UltimateBankingApiProvider.jobs();
if (jobs.isAvailable()) {
    for (String jobId : jobs.getRegisteredJobIds()) {
        System.out.println(jobs.getJobQuotes(jobId));
    }
}
```

### Built-in careers

| ID | Career | World activity recorded | Buyer/catalog focus |
| --- | --- | --- | --- |
| `lumberjack` | Lumberjack | Non-creative raw wood breaks | Raw wood plus wood products from loaded wood families |
| `miner` | Miner | Supported stone, ore, and mineral block breaks | Stone, ores, raw minerals, refined bars, and gems |
| `fisherman` | Fisherman | Fishing drops from `ItemFishedEvent` | Fresh, smoked, and cooked fish products |
| `farmer` | Farmer | Crop breaks and eligible passive-animal goods | Produce, seeds, grain, livestock goods, and farm supplies |
| `hunter` | Hunter | Player-caused living-entity drops, including projectile kills | Field drops, hides, pelts, and trophies |
| `blacksmith` | Blacksmith | Supported crafted equipment and metal outputs | Metal stock, tools, weapons, armor, and smithing goods |
| `courier` | Courier | Completed NPC parcel routes | Dispatcher-issued parcels delivered to the named destination NPC |
| `chef` | Chef | Supported crafted food outputs | Ingredients and prepared meals |
| `herbalist` | Herbalist | Supported plant and botanical block breaks | Botanicals, remedies, potions, and alchemy supplies |
| `salvager` | Salvager | Supported salvage-material block breaks | Metal salvage and reclaimed materials |
| `tailor` | Tailor | Supported crafted textile and leather outputs | Textiles, leatherwork, and wearable goods |

The catalog is built from the loaded item registry when the server starts or an operator runs `/ubs admin job reload`. Vanilla tags, common `c:` tags, and stable item-path conventions are used, so a compatible mod can contribute resources without a compile-time dependency on that mod. A resource may be present in a catalog without being accepted in a sale if it has non-default components; this prevents named, enchanted, or otherwise modified items from being converted accidentally.

### API surface

| Method | Returns or action | When to use it |
| --- | --- | --- |
| `getApiVersion()` | Job API version | Capability/version logging and compatibility checks |
| `isAvailable()` | Whether a server is currently attached | Guard calls made during client setup or server shutdown |
| `getRegisteredJobIds()` | Sorted built-in/registered job IDs | Build a job picker without hardcoding the list |
| `getJobDefinition(jobId)` | Optional `ApiJobDefinition` metadata | Read the display name, description, icon, buyer, catalog heading, activity label, and base value |
| `getJobDefinitions()` | Immutable metadata list | Populate a complete external career directory in one call |
| `getPlayerJob(playerId)` | Optional `ApiJobSnapshot` | Read the player's active job, level, XP, daily/lifetime earnings and units |
| `getJobQuotes(jobId)` | Live `ApiJobQuote` list | Populate a buyer, sell menu, scoreboard, or external job board |
| `getJobQuote(jobId, itemId)` | One live quote | Validate/display one item before requesting a sale |
| `getLumberjackQuotes()` / `getLumberjackQuote(itemId)` | Compatibility aliases for Lumberjack | Keep older Lumberjack integrations source-compatible |
| `getLumberjackMarket()` / `getJobMarket(jobId)` | Compatibility/generic market rotation snapshot | Read the current spotlight and server daily totals |
| `getLevelForXp(xp)` | Level from cumulative job XP | Render an external progression bar consistently with UBS |
| `sell(player, jobId, itemId, quantity)` | `ApiJobSaleResult` | Submit a player-context sale; the player must be online and active in that job |
| `sellLumberjack(player, itemId, quantity)` | Lumberjack compatibility sale | Keep an older integration working without a job ID |
| `recordActivity(player, metric, family, amount)` | Records a non-sale activity | Let a route, farming, brewing, machine, or custom-mod event report work without granting money directly |

`ApiJobQuote` contains the item ID, localized display name captured by the server, material family, category, configured base quote, current live quote in cents, normalized-unit weight, XP weight, and the configured minimum number of shop samples. `ApiJobSaleResult.payout()` is a dollar `BigDecimal`; quote and payout values are never client-authoritative.

### Sale and inventory rules

`sell` is intentionally narrower than a generic inventory API. The server checks the active job, the requested job catalog, a UBS primary account, a positive quantity, the current quote, per-player and server daily payout/unit limits, and the exact item/components immediately before removing anything. It can collect plain matching stacks from the player inventory, offhand, and nested item-handler inventories such as backpacks up to a bounded depth of four. The item is removed only after a matching snapshot is confirmed; if the deposit fails, the removed stacks are restored.

Do not call `depositToPrimary` yourself for a job sale and do not award XP from a client packet. Use `sell` so the item removal and UBS payout stay in the same server-controlled workflow. A successful sale updates the job's daily/lifetime statistics, supply pressure, daily quest progress, and activity-specific counters.

### Activity bridge

UBS listens for normal NeoForge events and records activity without taking ownership of drops or outputs:

| Career | Event bridge | Counter |
| --- | --- | --- |
| Lumberjack | `BlockEvent.BreakEvent` | `logs_broken` |
| Miner | `BlockEvent.BreakEvent` | `blocks_mined` |
| Fisherman | `ItemFishedEvent` | `fish_caught` |
| Farmer | `BlockEvent.BreakEvent` and passive-animal drops | `crops_harvested`, `animals_harvested` |
| Hunter | `LivingDropsEvent` with player/projectile attribution | `drops_recovered` |
| Blacksmith | `PlayerEvent.ItemCraftedEvent` | `items_forged` |
| Courier | Successful NPC parcel delivery | `deliveries_completed` |
| Chef | `PlayerEvent.ItemCraftedEvent` | `meals_prepared` |
| Herbalist | `BlockEvent.BreakEvent` | `plants_gathered` |
| Salvager | `BlockEvent.BreakEvent` | `scrap_recovered` |
| Tailor | `PlayerEvent.ItemCraftedEvent` | `items_stitched` |

The activity bridge ignores creative and spectator players. Mod integrations that implement a richer mechanic can report the supported counter through `recordActivity`; this records progress and quests but never pays the player by itself. The built-in job achievement and daily-quest boards only advance when their activity metric matches the submitted counter.

### Daily quests, milestones, and achievements

Each job receives three deterministic daily cards. Lumberjack retains its established raw/product/earnings rotation; the other careers rotate through job-specific activity, sale, and settlement templates. The reset is every 24 real-world hours at the UTC day boundary, survives restarts, and is returned to clients as `dailyQuestResetEpochSeconds`. Minecraft time is used separately for the existing payout/supply caps.

Every job has a shared five-level milestone track at levels `5, 10, ..., 100`. Rewards improve the verified job payout rate, sale XP certification, daily payout room, or normalized intake capacity. Job-specific achievement cards add fieldwork, trade, contract, exploration, career, and job-themed objectives. Rewards are server-created and may contain digital money, job XP, or a persisted random item from a loaded non-vanilla mod. Claiming is one-time and requires the player's active job; the client supplies only the achievement ID.

### In-game commands and screens

Players can use `/ubs job` or interact with a Job Market NPC. Operators with permission level 2 can use `/ubs admin job spawn market`, `/ubs admin job spawn buyer <job-id>`, `/ubs admin job spawn lumberjack`, `/ubs admin job test buyer`, `/ubs admin job npc edit`, and `/ubs admin job reload`. The Courier buyer is a Dispatcher: its bounded `courierOrders` snapshot contains generated NPC routes, and accepting one issues a server-assigned parcel. Delivery is completed by interacting with the destination job NPC, not by calling `sell`. The shared screen uses job metadata and server payloads rather than Lumberjack-specific labels: job list, catalog, carried-only filtering, live quotes, daily cards, milestone roadmap, achievements, and guide content are all driven by the selected job ID. Packet lists are bounded so a large modded registry cannot exceed the custom-payload element limit.

For a custom job integration, use an existing registered job ID only when its catalog and activity semantics match the built-in classifier. Otherwise, keep the external catalog in the integration and use `recordActivity` for the shared progress/quest bridge; never rely on client-side item names or a client-supplied payout.

## Finance API

`UltimateBankingApiProvider.get()` exposes:

### Money operations

```java
ApiResult getBalance(UUID accountId);
ApiTransactionResult deposit(UUID accountId, BigDecimal amount, String reference);
ApiTransactionResult withdraw(UUID accountId, BigDecimal amount, String reference);
ApiTransactionResult transfer(UUID from, UUID to, BigDecimal amount, String reference);
ApiTransactionResult depositToPrimary(UUID playerId, BigDecimal amount, String reference);
ApiTransactionResult withdrawFromPrimary(UUID playerId, BigDecimal amount, String reference);
ApiTransactionResult transferFromPrimary(UUID playerId, UUID to, BigDecimal amount, String reference);
ApiTransactionResult transferToPrimary(UUID from, UUID playerId, BigDecimal amount, String reference);
```

Whole-dollar `long` overloads and legacy `ApiResult` deposit/withdraw/transfer overloads remain available.

### Validation

- `accountExists`, `bankExists`, `shopExists`
- `accountCanSend`, `accountCanReceive`
- `primaryAccountCanSend`, `primaryAccountCanReceive`
- `validateAccountCanSend`, `validateAccountCanReceive`, `validateAccountCanInteract`
- `accountBelongsToBank`, `accountIsFrozen`, `accountIsPrimary`
- `bankAcceptsTransactions`

Call validation immediately before an operation only as UX assistance; the operation still performs authoritative validation.

### Account and bank snapshots

- `getAccountSnapshot`, `getPrimaryAccountSnapshot`
- `getPlayerAccounts`, `getPlayerAccountIds`, `getBankAccounts`
- `getBankSnapshot`, `getBanks`
- `getTransactionSnapshot`, `getAccountTransactions`, `getPlayerTransactions`
- `setPrimaryAccount`

Account transaction lists are bounded by the requested limit and by the server's retained history (`AccountTransactionLogLimit`, default `10,000`).

### Ownership and aggregate helpers

- `playerOwnsAccount`, `playerOwnsBank`, `playerOwnsAnyBank`
- `getPlayerOwnedBanks`
- `playerOwnsShop`, `playerOwnsAnyShop`, `getPlayerOwnedShopIds`
- `playerHasAnyAccount`, `playerHasPrimaryAccount`
- `playerHasAvailableAccount`, `playerHasAvailablePrimaryAccount`, `playerHasFrozenAccount`
- `getPlayerTotalBalance`, `getPlayerPrimaryBalance`, `getPlayerAccountCount`
- `getBankTotalDeposits`, `getBankReserve`, `getBankStatus`

### Merchant and instruments

- `shopPurchase` with payer, merchant, shop name, and reference
- `issueBankNote`
- `issueCheque`
- `giveDollarBills`, `takeDollarBills`
- `giveCoins`, `takeCoins`
- supported denomination, stack-creation, count, and cash-on-hand helpers

Bill denominations are dollars: `1, 2, 5, 10, 20, 50, 100`. Coin denominations are cents: `1, 5, 10, 25, 50`.

## Notification API

New integrations should use `sendNotification(playerId, request)`.

```java
var request = ApiNotificationRequest.transaction("Payment settled")
        .id("auction:settlement:" + orderId)
        .channel("auction_house")
        .source("Ultimate Auction System")
        .title("Settlement complete")
        .detail("The seller account was credited.")
        .priority(ApiNotificationPriority.NORMAL)
        .durationMs(5500)
        .build();

ApiNotificationResult result = finance.sendNotification(playerId, request);
```

Types: `SUCCESS`, `ERROR`, `WARNING`, `INFO`, `TRANSACTION`, `SECURITY`, `MESSAGE`, `PROGRESS`, `SYSTEM`.

Priorities: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`.

Placements: `AUTO`, `TOP_RIGHT`, `TOP_CENTER`, `BOTTOM_RIGHT`.

Use a stable request ID and `replaceExisting=true` to update progress/state in place. Use `dismissNotification`, `clearNotificationChannel`, or `clearNotifications` for cleanup. Offline targets are not silently queued.

The original `sendUiAlert` overloads and `ApiAlertTone` remain binary-compatible for older addons.

## Market Price API

```java
ApiShopPriceStatistics stats = finance.getItemShopPriceStatistics(
        stack,
        ApiShopPriceScope.REGULAR
);
```

Scopes:

- `REGULAR`: registered, setup-complete, currently open shops; creative displays excluded
- `INCLUDE_ALL`: every indexed shop display, including creative/unregistered displays
- `ALL_SHELVES_EXCLUDE_CREATIVE`: every indexed non-creative display
- `CREATIVE_ONLY`: creative displays only

The result exposes availability, item ID, sample count, and median/average/minimum/maximum price in cents. Convenience methods exist for every scope.

## General Server API

`UltimateBankingApiProvider.server()` provides:

- `isAvailable`
- `getSnapshot`
- `getAvailableFeatures`, `isFeatureAvailable`
- `getOnlinePlayerIds`
- `getPlayerPortfolio`

`ApiServerSnapshot` contains API version, online-player count, bank/account/shop counts, active-heist count, and feature flags.

Feature flags: `BANKING`, `SHOPS`, `HEISTS`, `SMARTPHONE`, `SAFETY_DEPOSIT_BOXES`, `RFID_ACCESS`, `PHYSICAL_CURRENCY`, `WALLET`, `OWNER_PC`, `INSTITUTIONAL_ECONOMY`, `IDEMPOTENT_OPERATIONS`, `MONETARY_ESCROW`, `ORGANIZATION_ACCOUNTS`, `ORGANIZATION_AUDIT`, `ORGANIZATION_EVENTS`.

`ApiPlayerPortfolioSnapshot` contains account count, primary account, total balance, owned/accessed bank IDs, owned/accessed shop IDs, and current heist session ID.

## Bank Management API

`UltimateBankingApiProvider.banks()` provides:

### Reads

- `getBanks`, `getBank`, `findBank`
- `getOwnedBanks`, `getAccessibleBanks`
- `playerOwnsBank`, `playerOwnsAnyBank`
- `playerCanAccessBank`
- `playerCanManageSafeArea`, `playerCanAccessProtectedSafeArea`
- `isBankUnderAttack`
- `getStaffing`
- `getSafeDepositSetup`

`ApiBankManagementSnapshot` includes identity/owner/status, central-bank flag, account count, deposits, reserve requirements/ratio, outstanding loans, lendable amount, rate, premise/safe/vault/readiness counts, staff/teller counts, and attack state.

`ApiBankStaffingSnapshot` contains immutable employee and teller records. Employees include role, salary, online state, and Safe Access. Tellers include entity identity, variant, position, active state, and bank-bound state.

`ApiSafeDepositSetupSnapshot` contains enabled/readiness counts, human-readable blockers, and premises. Each `ApiBankPremiseSnapshot` provides bounds, exit/facing, access mode, safe/vault counts, and ready-vault count.

### Mutations

```java
ApiManagementResult setEmployeeSafeAccess(
        UUID actorId, UUID bankId, UUID employeeId, boolean allowed);

ApiManagementResult setInterestRate(
        UUID actorId, UUID bankId, double annualPercent);
```

The actor must have the same permission the in-game management action requires.

## Shop Management API

`UltimateBankingApiProvider.shops()` provides:

### Discovery and ownership

- `getShops`, `getShop`, `findShop`
- `getOwnedShops`, `getAccessibleShops`
- `shopExists`, `playerOwnsShop`, `playerOwnsAnyShop`
- `getPlayerRole`, `playerCanManageShop`, `playerCanBuildInShop`

### State and limits

- `isShopSetupComplete`
- `isShopCurrentlyOpen`
- `getMaximumShopsPerOwner`
- `getSupportedShopTypes`
- `getSupportedParticipantRoles`

`ApiShopManagementSnapshot` includes owner/name/type/display type, level/revenue/next target, used/capacity claim volume, plot and stockroom counts, setup/open state, display/cashier/order-pallet limits, and participants. Each participant includes role plus computed management/build permissions.

### Mutations

```java
createShop(ownerId, name, type);
renameShop(ownerId, shopId, newName);
setShopType(ownerId, shopId, type);
setOpeningHours(ownerId, shopId, schedule);
setParticipantRole(ownerId, shopId, playerId, role);
removeParticipant(ownerId, shopId, playerId);
deleteShop(ownerId, shopId, confirmationName);
```

The schedule accepts the same payload as the Owner PC, for example `ALL|09:00|21:00` or `MON|9:00 AM|5:30 PM`. Roles are the values returned by `getSupportedParticipantRoles`.

## Heist API

`UltimateBankingApiProvider.heists()` provides:

### State

- `getSessions`, `getActiveSessions`, `getSession`, `getPlayerSession`
- `getTargets`, `getTarget`
- `isPlayerInHeist`, `isPlayerInActiveHeist`, `isBankUnderAttack`
- player/bank cooldown and victim-protection remaining milliseconds
- effective maximum crew size, countdown ticks, and duration ticks

`ApiHeistSessionSnapshot` includes phase/timestamps/deadline, target bounds/exit, loot/alarm state, members, active drills/hacks, completed hacks, breached targets, and cancel votes.

`ApiHeistTargetSnapshot` includes premise bounds/exit, owner-PC/vault-door positions, eligibility/blockers, physical-loot source count, and bank cooldown.

### Planning actions

```java
createPlanningSession(playerId);
invite(leaderId, playerName);
respondToInvite(playerId, accepted);
leave(playerId);
selectTarget(leaderId, bankId, premiseId);
setReady(playerId, ready);
startCountdown(leaderId);
cancelCountdown(leaderId);
abandon(playerId);
```

Actors must be online and calls must run on the server thread.

### Heist extension points

- Listen for `HeistLifecycleEvent` stages: `STARTED`, `ALARMED`, `SUCCEEDED`, `FAILED`.
- Register `HeistDoorAdapter` with `HeistDoorAdapterRegistry` to breach/restore modded doors.
- Register `HeistLootValueProvider` with `HeistLootValueRegistry` to value custom items.
- Unregister adapters/providers when your integration unloads where applicable.

Registry implementations isolate provider exceptions so one addon does not break the heist loop.

## Geometry Records

`ApiBlockPosition` stores dimension and integer coordinates.

`ApiBlockBounds` normalizes min/max coordinates and exposes `volume()` and `contains(position)`. Use these API records instead of depending on internal claim classes.

## Placeholders

UBS resolves placeholders live from the current server state. Values are not cached, so account balances, ownership, account status, and shop/bank counts reflect the result of the most recent completed server-side operation.

The public resolver is available through `UltimateBankingApiProvider.get()`:

```java
String value = UltimateBankingApiProvider.get().resolvePlaceholder(player.getUUID(), "%ubs_primary_balance_raw%");
String text = UltimateBankingApiProvider.get().resolvePlaceholders(player.getUUID(),
        "Balance: %ubs_primary_balance% | Bank: {ubs_primary_bank_name}");
```

The resolver accepts both PAPI-style `%ubs_<key>%` and NeoEssentials-style `{ubs_<key>}` syntax. Existing `ubs_player_*` and bank UUID placeholder names remain supported. Unknown placeholders resolve to an empty value through the direct API and remain unchanged when embedded in text.

### Canonical player placeholders

| Placeholder | Meaning |
|---|---|
| `{ubs_balance}` / `{ubs_balance_raw}` | Primary account balance, formatted or plain decimal |
| `{ubs_total_balance}` / `{ubs_total_balance_raw}` | Sum of all player accounts |
| `{ubs_primary_account_id}` | Primary account UUID |
| `{ubs_primary_account_type}` | Display label for the primary account type |
| `{ubs_primary_bank_id}` / `{ubs_primary_bank_name}` | Primary account's bank |
| `{ubs_account_count}` | Number of accounts owned by the player |
| `{ubs_has_account}` / `{ubs_has_primary_account}` | `true` or `false` |
| `{ubs_cash_balance}` / `{ubs_cash_balance_raw}` | Total UBS physical tender currently carried by the online player, including loose bills, coins, money stacks, cash stored in wallets, and tender inside backpacks or other containers exposing the standard NeoForge item-handler capability |
| `{ubs_account_status}` | Primary account status, or `NONE` |
| `{ubs_owned_bank_count}` / `{ubs_owns_bank}` | Bank ownership summary |
| `{ubs_owned_shop_count}` / `{ubs_owns_shop}` | Shop ownership summary |
| `{ubs_player_name}` | Current online player name |

`raw` values are decimal strings suitable for sorting or machine processing. Formatted money values use the configured UBS currency symbol and display abbreviation.

### Server placeholders

`{ubs_server_bank_count}` and `{ubs_server_shop_count}` expose the current number of registered banks and shops. These are available only when the UBS server data is loaded.

### NeoEssentials

When NeoEssentials is installed, UBS detects its placeholder API at server start and registers the canonical keys as individual live providers. This avoids a hard dependency and keeps UBS loadable without NeoEssentials. NeoEssentials consumers can therefore use `{ubs_primary_balance}`, `{ubs_primary_account_id}`, and the other canonical keys directly.

#### NeoEssentials leaderboards

When NeoEssentials is installed, UBS also registers these live boards through `com.zerog.neoessentials.leaderboard.LeaderboardAPI`:

| Board ID | Ranking |
| --- | --- |
| `ubs_player_balance` | Total player account balances |
| `ubs_player_accounts` | Number of player accounts |
| `ubs_player_businesses` | Owned shops plus owned custom banks |
| `ubs_shop_revenue` | Best shop revenue per shop operator |
| `ubs_shop_level` | Highest shop level per shop operator |
| `ubs_shop_claims` | Total claimed shop regions per shop operator |
| `ubs_bank_deposits` | Customer deposits per bank operator |
| `ubs_bank_accounts` | Customer account count per bank operator |
| `ubs_bank_reserves` | Declared reserves per bank operator |

The boards use NeoEssentials' standard `{leaderboard_<board_id>:<rank>:name}` and `{leaderboard_<board_id>:<rank>:value}` placeholders and are available to `/leaderboard`, holograms, tablists, and other NeoEssentials consumers. Values are read from UBS data when NeoEssentials refreshes its leaderboard cache; UBS does not create demo rows or placeholder values.

NeoEssentials currently accepts only `UUID -> Number` leaderboard providers and resolves names from player profiles. Consequently, shop and bank boards are operator-backed: a player with multiple shops or banks appears once, with shop revenue/level/claims using the best or aggregate operator metric described above. This preserves correct player names and compatibility with the current NeoEssentials API. A future NeoEssentials named-entity provider can expose individual shop and bank names without changing the UBS metric definitions.

Example tablist lines:

```text
Top player: {leaderboard_ubs_player_balance:1:name} {leaderboard_ubs_player_balance:1:value}
Top shop operator: {leaderboard_ubs_shop_revenue:1:name} {leaderboard_ubs_shop_revenue:1:value}
Top bank operator: {leaderboard_ubs_bank_deposits:1:name} {leaderboard_ubs_bank_deposits:1:value}
```

The registrations are optional and reflection-based, so UBS remains loadable when NeoEssentials is absent. They are installed again on each server start, matching NeoEssentials' in-memory registration lifecycle.

### PAPI compatibility

The canonical names deliberately follow the PlaceholderAPI expansion convention: `%ubs_primary_balance%`, `%ubs_balance_raw%`, and so on. A pure NeoForge server cannot load Bukkit's PlaceholderAPI expansion classes, so UBS does not bundle or shade PAPI. Hybrid servers or a PAPI bridge should delegate the expansion's `onRequest` parameter to `net.austizz.ultimatebankingsystem.api.placeholder.PapiPlaceholderBridge.resolve(UUID, String)`; text consumers can delegate to `resolveText`. This keeps PAPI optional while preserving the standard `%ubs_<key>%` contract.

### Full legacy list

`getSupportedPlaceholders()` returns both the legacy `%ubs_player_*%`/bank UUID forms and the canonical brace forms. Check this list instead of hardcoding a version-specific set.

## Compatibility

- Check `getApiVersion()` before using a surface introduced after your minimum version.
- Declare UBS as a required or optional NeoForge dependency; never shade UBS into your jar.
- Keep optional integration classes isolated until `ModList.get().isLoaded("ultimatebankingsystem")` is true.
- Do not retain internal UBS objects or mutate NBT directly. Use IDs, snapshots, and API methods.
