# Common questions

> Quick fixes for the most common stumbling blocks.

## I cannot see a bank or balance

Run `/account info list` and confirm you have an account. Select a primary account with `/account primary set <accountId>`. The balance HUD can be enabled with `/account hud toggle`.

If a custom bank is missing, ask its owner whether it is open and whether its teller and premise are set up.

## I cannot sell to a job buyer

You must have that active job, an eligible primary account and supported goods. Open the buyer's catalog to check the current quote, carried quantity and restrictions.

Component-modified stacks are not accepted by ordinary job sales; check that the item is plain and matches the buyer's catalog.

## A store or delivery is unavailable

Shops can close outside configured hours. A courier route can expire or lose its destination NPC. Check its timer and ID in the active-deliveries view.

Operators should inspect the linked cashier, claim and stockroom setup in the Business Manager PC.
