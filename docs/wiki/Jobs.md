# UBS Jobs

UBS jobs are server-authoritative career routes for earning digital money from useful Minecraft work. Most jobs follow the Lumberjack contract: a player applies at the Job Market, progress is stored independently for that job, supported activity is recorded without trusting the client, eligible plain goods can be sold at the matching buyer, and the server deposits the payout into the player's UBS primary account. Courier is the intentional exception: it is a dispatch-and-delivery route, not a selling desk.

The shared contract is deliberately small. The catalog is discovered from the loaded item registry, daily cards are deterministic, level progress and achievements are persisted per job, and all sale validation happens again when the player confirms a sale. This keeps the feature compatible with content mods without making UBS depend on any one content mod.

## Player Flow

1. Place a Job Market NPC with `/ubs admin job spawn market`, use the Job Market item in the `UBS: Jobs` creative tab, or open the market with `/ubs job`.
2. Select a job and apply. Application is instant; each job has its own level, XP, activity counters, daily cards, and achievement state.
3. Perform the work shown in the job guide. Creative and spectator activity is ignored.
4. Place the matching buyer with `/ubs admin job spawn buyer <job-id>`.
5. Open the buyer, select a product, review the live quote, and confirm the sale. Courier players instead open a Courier Dispatcher and accept an NPC route.
6. A normal sale removes only matching plain stacks from the player's inventory, offhand, and supported nested item-handler inventories, then deposits the payout and awards job XP. A Courier acceptance issues one sealed, non-stackable parcel; the player carries it to the named destination NPC and interacts with that NPC to complete the route.

A sale needs an UBS primary account. Modified stacks, including named, enchanted, or otherwise component-modified items, are not silently converted. A player can leave a job without losing its saved progress and can apply for another job after leaving the current one.

## Shared Progression

Every job has three daily cards. Lumberjack keeps its established raw-log, finished-product, and earnings cards. Other jobs rotate through activity, sale, and settlement cards with job-specific names. The rotation uses real-world UTC days, resets every 24 hours, and survives a server restart. Existing payout and supply limits continue to use Minecraft days, so operators can tune economic pacing separately from quest cadence.

Every fifth level from 5 through 100 has a milestone card. The implemented banking-themed effects rotate between:

- preferred settlement-rate improvement;
- job certification that improves sale XP;
- additional daily payout reserve;
- additional daily normalized-goods capacity.

The effect is calculated server-side from the player's job progress. Achievement rewards are one-time claims and can contain digital money, job XP, or a persisted random item from a loaded non-vanilla mod. The item roll is persisted before the reward is given, preventing a retry from rerolling the reward.

## Independent Job Analysis

### Lumberjack

**Compatibility:** Raw logs and finished products are admitted through the vanilla `ItemTags` wood tags. Exact vanilla registry identities cover minimal tag environments; modded wood families participate by supplying the standard wood tags. Registry-name substrings do not make an item sellable.

**Daily cards:** Timber Intake, Forest Reserve, Heavy Harvest, Responsible Sourcing, Millwork Order, Joinery Run, Furniture Ledger, Contractor Batch, and earnings targets rotate across raw intake, finished products, and settlement value.

**Level rewards:** Preferred timber settlement rates, supplier certification XP, reserve-window capacity, and intake clearance are presented as bank-supply permissions rather than arbitrary combat power.

**Achievements:** First Cut, wood-family Scout/Steward/Master objectives, Forest Keeper, Timber Empire, production milestones, quest streaks, wholesale partnership, and settlement-value targets reward both breadth and volume.

**Anti-repetition:** Raw harvesting, processed products, material-family diversity, market spotlights, supply pressure, daily cards, and the live shop-price anchor create different reasons to play each day.

**UI/mechanics:** The existing Lumberjack catalog remains the compatibility source, while the shared screen renders its real discovered wood catalog, carried-only view, multipliers, roadmap, daily cards, and achievement claims.

### Miner

**Compatibility:** Ores, raw minerals, refined materials, and stone use vanilla/common item tags, block tags, item components, and exact vanilla registry identities. Modded resources participate through the same tags. An item is never admitted because its registry name contains words such as `bar`, `ore`, or `stone`; tools and armor therefore stay out of Miner.

**Daily cards:** Ore Route, Deep Core, Smelter Intake, Stoneworks Batch, and Ore Settlement mix block work, mineral turn-ins, and money targets.

**Level rewards:** Use extraction-clearance language for payout-rate and normalized-capacity milestones. A server can later add ore-assay vouchers or bank construction permits as non-combat cosmetic/reward items without changing the sale contract.

**Achievements:** First Shift, Steady Hand, Deep Core Clearance, Refinery Partner, Trusted Supplier, and rank achievements reward depth, processed-material supply, and consistent turn-ins.

**Anti-repetition:** Rotate stone, ore, raw mineral, refined bar, market-spotlight, and shop-price opportunities. The classifier separates equipment from resources so the job does not collapse into selling every item with a mineral word in its name.

**UI/mechanics:** The shared catalog is sufficient. The `blocks_mined` activity bridge handles supported block breaks; an ore-processing mod can report additional work through `UltimateBankingApiProvider.jobs().recordActivity(...)` without granting money directly.

### Fisherman

**Compatibility:** Fish are detected through vanilla/common fish tags and exact vanilla raw/cooked identities. Fresh, smoked, and cooked products are separate categories; food mods participate by supplying the appropriate fish tags.

**Daily cards:** Fresh Catch, Longline Shift, Dockside Trade, Cold Storage, and Tide Settlement cover catching, product handling, and settlement.

**Level rewards:** Present cold-chain certification, dock priority, and settlement-rate improvements. A fishing-content mod can add a certificate item or bait permit as an achievement reward without creating a hard dependency.

**Achievements:** First Shift, Steady Hand, Tidekeeper, Cold-Chain Supplier, market breadth, sale volume, quest completion, and rank milestones support both frequent catches and premium product turn-ins.

**Anti-repetition:** Fresh, smoked, and cooked categories create a production choice. Daily cards and family/market spotlights reward changing the catch instead of repeating one fish forever.

**UI/mechanics:** The common catalog and buyer work. NeoForge `ItemFishedEvent` records eligible catch quantities; a mod that adds a separate net, trap, or boat system can call the activity API with `fish_caught` after its server-side result is known.

### Farmer

**Compatibility:** Crop, seed, produce, grain, milk, egg, honey, and animal-product resources use vanilla/common item and block tags, food components, and exact vanilla identities. Passive-animal drops are recognized only when a player caused the event and the resulting item is in the Farmer catalog.

**Daily cards:** Field Rotation, Livestock Roundup, Granary Fill, Market Crates, Pantry Supply, and Harvest Settlement rotate between crops, livestock, sales, and money.

**Level rewards:** Use agricultural-supply clearance, reserve priority, and harvest settlement-rate improvements. Non-combat rewards such as seed vouchers, crate tokens, or bank-market permits fit the banking theme.

**Achievements:** Field Steward and Livestock Keeper are in addition to the shared first shift, supplier, quest, reserve, and rank cards. This gives crop-focused and animal-focused players separate goals.

**Anti-repetition:** The job has two different production loops: field harvest and livestock supply. Seeds, grain, produce, and livestock categories can be affected by different daily spotlights and shop prices.

**UI/mechanics:** The shared buyer UI is enough for sales. Crop breaks use `BlockEvent.BreakEvent`; passive-animal goods use player-attributed `LivingDropsEvent`. Breeding, milking machines, and modded harvesters should report their completed result through the activity API rather than being guessed from client inventory changes.

### Hunter

**Compatibility:** Common mob drops and modded resources use common drop/trophy/hide tags, vanilla arrow/skull tags, and exact vanilla identities. The drop bridge requires player or projectile attribution.

**Daily cards:** Tracking Run, Apex Contract, Trophy Intake, Field Supply, and Hunt Settlement combine field drops, premium trophies, turn-ins, and settlement value.

**Level rewards:** Use trophy-appraisal clearance, field-supply reserve, and preferred rates. Cosmetic display trophies or bank security permits are safer thematic rewards than direct combat bonuses.

**Achievements:** Trailblazer and Trophy Room add fieldwork and sale goals to the common first shift, supplier, quest, reserve, and rank achievements.

**Anti-repetition:** The catalog separates trophies from hides/pelts and ordinary field drops. Projectile attribution lets ranged hunting count while unrelated environmental deaths and uncredited mob drops do not.

**UI/mechanics:** The shared buyer screen works. `LivingDropsEvent` is observed without modifying the drop list; direct attackers and projectile owners are resolved on the server. A hunting mod can report a validated capture or trophy event through `recordActivity`.

### Blacksmith

**Compatibility:** Metal stock and equipment are detected from item tags, the vanilla tool/armor components and tags, and exact vanilla stock identities. Modded equipment participates through the same tags and components without a hard dependency.

**Daily cards:** Forge Shift, Armory Order, Metal Contract, Armory Intake, and Forge Settlement alternate between production outputs, equipment supply, sales, and income.

**Level rewards:** Use forge certification, armory preferred rates, reserve expansion, and normalized intake clearance. Smithing templates, inspection seals, or contract documents can be added as future themed reward items.

**Achievements:** Forge Master and Armory Supplier make production and sales separate objectives, supplemented by the shared first shift, supplier, quest, reserve, and rank cards.

**Anti-repetition:** A player can choose stock sales, equipment crafting, premium equipment, or daily settlement targets. XP is tied to accepted outputs rather than simply holding a tool.

**UI/mechanics:** The shared catalog and confirmation flow are retained. `PlayerEvent.ItemCraftedEvent` records supported output counts as `items_forged`; an anvil or machine integration should call the activity API after its server transaction completes.

### Courier

**Compatibility:** Courier orders are generated by placed UBS job NPCs and do not depend on a shop catalog or a physical commodity. The dispatcher issues a sealed parcel whose manifest records the requested item and quantity. This keeps orders deterministic and safe to validate even when the requested commodity is supplied by a modded server.

**Daily cards:** Route Departure, Express Network, Parcel Manifest, Dispatch Window, and Route Settlement support delivery count, parcel intake, and money goals.

**Level rewards:** Use route-priority certification, express settlement rates, and larger dispatch capacity. Time-limit bonuses should be computed by the courier/route integration, not faked by the buyer UI.

**Achievements:** Route Captain and Manifest Master reward completed dispatches and processed documents separately.

**Anti-repetition:** Each Courier Dispatcher maintains a personal board of up to five available NPC orders. A route has a persistent origin and destination NPC UUID, a manifest, a reward, a distance estimate, and a fifteen-minute real-time expiry. Players can carry at most three accepted routes at once. The same destination/item combination is not duplicated on one board, and expired accepted parcels are removed instead of paying out.

**UI/mechanics:** Courier uses the dedicated Dispatch tab rather than the catalog. The screen always includes every accepted route, even when the player opens a different dispatcher; available offers remain local to the dispatcher that generated them. Each accepted row shows its short ID, item and quantity, destination coordinates, distance, reward, contract tier, money/XP bonuses, and a live countdown. The server checks that the player accepted the route at the interacted dispatcher, is holding either the assigned sealed parcel or the exact manifest item in the clicked hand with enough quantity, is using the correct destination NPC, and is within the daily payout limit before consuming the delivery and depositing the reward. Completion records `deliveries_completed` and Courier XP without incrementing catalog-sale counters, and does not reopen the dispatch screen. A route addon can use `recordActivity` for richer completion metrics, but must keep its deadline and payout server-authoritative.

**Live route HUD:** Accepted routes appear automatically in an in-world Courier panel without reopening the NPC. The panel is limited to three cards, updates once per second from server-authoritative deadlines, shows distance and a top-center bearing/ETA strip, and can be collapsed with `X`. It disappears as soon as the last route is delivered, canceled, expired, or closed because an NPC was removed. When JourneyMap is installed, each same-dimension destination receives a non-persistent labeled waypoint. Xaero Minimap support is best-effort and is isolated behind reflection so it cannot make UBS require Xaero.

**Cancellation and lifecycle:** Use `/delivery cancel <delivery_id>` or `/job cancel <delivery_id>`; both commands suggest only the player's active short IDs. Cancellation removes the matching sealed parcel and never pays the route. Leaving Courier, logging out, or leaving the server closes all open Courier offers and accepted routes. If an origin or destination Courier NPC is removed, the route is closed, its parcel is removed, and an accepted route's promised payout is credited exactly once. The same reconciliation runs after a restart or delayed account availability.

### Chef

**Compatibility:** Food is detected using the item food component, common food tags, and exact vanilla prepared-food identities. Food mods participate through the food component or tags without listing every recipe.

**Daily cards:** Prep Line, Service Rush, Catering Order, Pantry Rotation, and Kitchen Settlement rotate production, catering, ingredient, and income goals.

**Level rewards:** Use kitchen contract certification, catering preferred rates, reserve room, and ingredient intake clearance. Recipe cards, inspection seals, and catering vouchers fit UBS better than generic combat rewards.

**Achievements:** Service Head and Catering Partner distinguish preparing meals from selling ingredients or completed food.

**Anti-repetition:** Ingredients and prepared meals occupy different catalog categories, and the daily board can push a player toward preparation or settlement each day. Food components make the classifier resilient to new edible items.

**UI/mechanics:** The shared buyer and roadmap are sufficient. Crafted food outputs are recorded as `meals_prepared`; campfire, furnace, restaurant, and machine integrations should report their validated output through the activity API.

### Herbalist

**Compatibility:** Flowers, leaves, saplings, botanical resources, potions, and remedies use vanilla/common item tags, potion components, and exact vanilla identities. Modded plants and remedies participate through the same tags or components.

**Daily cards:** Field Samples, Rare Bloom, Apothecary Order, Remedy Stock, and Herbal Settlement alternate gathering, remedy supply, sales, and income.

**Level rewards:** Use apothecary certification, remedy appraisal, reserve expansion, and gathering capacity. Sealed prescription forms, laboratory permits, and bank-inspection kits keep rewards in theme.

**Achievements:** Field Botanist and Apothecary Partner reward gathering and remedy settlement independently.

**Anti-repetition:** Gathering, potion/remedy preparation, and botanical sales are separate routes. The API leaves brewing and alchemy event ownership with the producing mod so UBS does not guess whether a potion was actually completed.

**UI/mechanics:** The common catalog and achievement cards work. Plant block breaks record `plants_gathered`; brewing integrations should report validated brew outputs through `recordActivity` and use the buyer for settlement.

### Salvager

**Compatibility:** Scrap, metal, glass, stone, and reclaimed-material resources use common/vanilla item tags and exact vanilla identities. Registry-name substrings are not used, so unrelated items with words like `barrel`, `barrier`, or `bar` cannot enter Salvager.

**Daily cards:** Scrap Run, Reclamation Shift, Recycling Bin, Second Life, and Scrap Settlement combine field recovery, intake, sales, and income.

**Level rewards:** Use reclamation permits, recycler preferred rates, reserve room, and normalized intake expansion. Salvage manifests, sorting tags, or inspection credits are useful non-combat rewards.

**Achievements:** Reclamation Expert and Second Life Supplier make recovery and resale separate goals.

**Anti-repetition:** Material families, daily supply pressure, and the distinction between recovered blocks and resale create a loop beyond one item spam. A junkyard mod can supply custom recovery events through the API.

**UI/mechanics:** The shared buyer is intentionally enough for the base career. `BlockEvent.BreakEvent` records `scrap_recovered`; a container-search or machine-recycling mod should report only after its server-side result is committed.

### Tailor

**Compatibility:** Wool, carpets, dyes, leather, banners, textiles, and string are detected through vanilla/common tags, dye/item components, and exact vanilla identities. Leather armor is included as leatherwork, while unrelated inventory items are ignored.

**Daily cards:** Pattern Cut, Atelier Order, Textile Intake, Leather Ledger, and Atelier Settlement rotate crafting, textile supply, sales, and income.

**Level rewards:** Use atelier certification, uniform-contract rates, reserve expansion, and textile intake capacity. Pattern books, uniform seals, and approved supplier documents are strong thematic rewards.

**Achievements:** Pattern House and Uniform Contract separate production from settlement and complement the shared career goals.

**Anti-repetition:** Textile inputs, leatherwork, finished wearables, and market spotlights provide different production decisions. A clothing mod can use tags rather than a UBS-specific item list.

**UI/mechanics:** The shared catalog and roadmap are retained. `PlayerEvent.ItemCraftedEvent` records supported outputs as `items_stitched`; loom, sewing-machine, or equipment-mod integrations should report validated outputs through the activity API.

## Mod Compatibility Rules

- Prefer vanilla or common `c:` item tags for a resource family. UBS uses exact vanilla registry identities only as a compatibility fallback; arbitrary registry-name substrings never classify an item.
- Do not give a sellable item custom components when it is intended to be accepted as a plain commodity. Named or enchanted variants are intentionally rejected.
- Report mod-specific activities from the logical server thread with `UltimateBankingApiProvider.jobs().recordActivity(player, metric, family, amount)`.
- Use `getJobDefinitions()` to build a job directory and `getJobQuotes(jobId)` for a live catalog. Never cache a payout value across a sale confirmation.
- Use `sell(player, jobId, itemId, quantity)` for settlement. Do not remove items or deposit money separately in an addon.
- Keep custom route, breeding, brewing, machine, and container logic authoritative in the owning mod. UBS should receive a completed result, not infer one from a client packet.
- Run `/ubs admin job reload` after adding registry content or changing data packs. The catalog is cached per server and refreshed on startup, reload, or a controlled API refresh.

## Server Commands

| Command | Purpose |
| --- | --- |
| `/ubs job` | Open the Job Market screen. |
| `/ubs job status` | Open the same market/status screen. |
| `/ubs job leave` | Leave the active job while keeping its saved progress. |
| `/delivery cancel <delivery_id>` | Cancel one accepted Courier route by its suggested short ID. |
| `/job cancel <delivery_id>` | Alias for Courier route cancellation. |
| `/ubs admin job spawn market` | Place a Job Market NPC at the operator's position. |
| `/ubs admin job spawn buyer <job-id>` | Place the dedicated buyer/dispatcher for any registered job. Courier uses this NPC as its order dispatcher. |
| `/ubs admin job spawn lumberjack` | Compatibility alias for the original Lumberjack buyer. |
| `/ubs admin job spawn miner` through `/ubs admin job spawn tailor` | Place the dedicated buyer NPC for each built-in job. |
| `/ubs admin job test buyer` | Open a read-only buyer/dispatch preview for operators. |
| `/ubs admin job npc edit` | Open the shared NPC skin editor. |
| `/ubs admin job reload` | Rebuild loaded job catalogs and refresh open buyer screens. |

The complete Java surface, records, threading rules, and sale semantics are documented in [Developer API](Developer-API.md).

The `UBS: Jobs` creative tab contains the Job Market NPC and a dedicated role item for Lumberjack, Miner, Fisherman, Farmer, Hunter, Blacksmith, Courier Dispatcher, Chef/Baker, Herbalist/Alchemist, Salvager/Recycler, and Tailor/Leatherworker. Each item places the same persistent entity framework with a distinct synchronized role, job ID, skin target, interaction behavior, and spawn orientation; this avoids duplicated entity implementations without making the roles indistinguishable. Every role appears in `/ubs admin job npc edit`, and the role list is scrollable.

Operators can remove a job NPC with shift-left-click when they have permission level 2. The first click gives a warning; a second shift-left-click by the same operator within five seconds removes the NPC and returns its matching role item.
