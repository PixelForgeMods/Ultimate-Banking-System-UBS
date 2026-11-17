# Courier deliveries

> Accept a route, carry its sealed parcel and hand it to the named NPC.

## Dispatch

Courier is a delivery role, not a selling catalog. Open the Courier Dispatcher to compare orders. Each row shows a destination, parcel contents, reward, route distance and time limit. Accepting one issues a sealed parcel and starts its timer.

![Courier Dispatch board with available routes](screenshot:ultimatebankingsystem:64?width=2090&height=1251)

## While in transit

The active-routes HUD shows the next destination, direction, distance and time remaining. It can list several accepted routes at once. The courier menu also shows active orders if the HUD is crowded.

![Active courier routes in the HUD](screenshot:ultimatebankingsystem:65?width=2558&height=1253)

Keep the issued parcel. Its tooltip names the recipient and manifest; carrying a similar item from your inventory is not the same as carrying that sealed order.

![Sealed courier parcel and its recipient](screenshot:ultimatebankingsystem:66?width=888&height=162)

## Deliver or cancel

Reach the named destination NPC and interact while holding the matching parcel. Without it, the NPC opens its normal menu. Deliver before the timer expires to earn the displayed payout.

Cancel in the courier menu or use `/delivery cancel <delivery_id>`; `/job cancel <delivery_id>` is also supported. Command suggestions show active IDs. Leaving Courier cancels open routes, and expired routes no longer pay on ordinary completion.
