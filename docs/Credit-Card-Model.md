# Credit card model and printed details

The existing `ultimatebankingsystem:credit_card` item now uses the rounded,
textured Blockbench model. Its registry ID, card IDs and account links are
unchanged. The editable blank base is `assets/ultimatebankingsystem/blockbench/credit_card.bbmodel`.
The atlas is `textures/item/credit_card.png` (2048 x 1024), and the static mesh is
`models/item/credit_card_body.json`. The item root uses a custom item renderer
with display transforms for inventory, hands, ground and item frames.

`CreditCardItemRenderer` draws the actual stack's bank name, full grouped card
number, cardholder name and MM/yy expiry on the front, with CVC on the back.
Text is placed directly on the card surface with a subtle embossed shadow and
shrinks to fit long names. It uses the current item data every render, without
cached personalized textures. Unissued cards stay blank. Account IDs, balances,
linked-account labels and card status are not printed on the model.

`CreditCardService.TAG_HOLDER_NAME` stores the holder name from the issued card
record. New issuance and validation copy it to the item. Existing carried cards
backfill the name server-side, once per second until populated, without changing
card identity or account linkage. Online owner profiles provide a visual fallback
for older cards still stored in containers. A stored offline owner's missing name
becomes available when the card is carried or validated.

Validation: `gradlew.bat runGameTestServer -PgameTestNamespaces=ultimatebankingsystem_atm`.
Card tests cover per-stack text, leading CVC zeros, expiry formatting, exclusion
of account data, blank cards, bounded names, and holder-name backfill while
preserving existing card/account IDs.

## Flipping the card

Sneak + use toggles `ubs_cc_back_visible` on the held item and rotates the full
card, including its printed text, 180 degrees about its center. It works for
both hands and retains the side on that item; it never rewrites payment data.
Ordinary right-click does not flip. An eight-tick cooldown prevents immediate
duplicate toggles. Sneak block clicks give the target block first refusal via
NeoForge's normal interaction event, respecting explicit interaction denials,
so containers and other usable blocks continue to work.

A held-card hint uses the configured sneak/use bindings and the shared HUD
layout manager. It sits at least 80 GUI pixels above the bottom and moves up
with vanilla's health/armor/air HUD height, keeping clear of the temporary
selected-item-name line. The hint is hidden with the HUD, during screens and
while other full-screen UBS overlays are active.
