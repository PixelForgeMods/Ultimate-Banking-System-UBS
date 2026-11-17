# ATM model and LCD

The NCR SelfServ 22 is one placed `ultimatebankingsystem:atm_machine` block,
rendering the complete 32-unit-high cabinet. It uses only `facing`, has a
full-height narrow collision shape, and checks for headroom when placed.
There is no upper block, half property, or paired destruction logic.
Remove and replace legacy two-block ATM placements when updating an existing world.

## Source and export

- Editable source: `src/main/resources/assets/ultimatebankingsystem/blockbench/atm_machine.bbmodel`.
- Preserve the component groups and the single embedded 2048 x 2048 texture.
- Run `python tools/export_atm.py` after updating that source.
- The exporter replaces `models/block/atm_machine.json`, `models/item/atm_machine.json`,
  `models/block/atm_machine_body.json` and `textures/block/atm_machine.png`
  in the same assets directory. The obsolete `atm_glow.png` is removed.
- The item inherits the complete cabinet model including its buttons. All four facing variants use that model.
- Export checks: `python -m unittest discover -s tools -p test_export_atm.py`.
- Placement, collision and destruction check:
  `gradlew.bat runGameTestServer -PgameTestNamespaces=ultimatebankingsystem_atm`.

## Live LCD welcome UI

The placed ATM draws its welcome screen with world-space geometry and TrueType
text in `AtmLcdLayout` and `AtmLcdRenderer`. The atlas artwork below is the design
reference and inventory preview, not the world UI background. The exporter removes
the static LCD north face and writes its bounds to `atm_lcd.json`.

The bottom left/right physical screen buttons select English/Spanish. Card
validation, insertion, removal and temporary errors update the text on the LCD.
Two seconds after insertion completes and “Card accepted” appears, the LCD switches
to a four-digit masked PIN page. Physical keys 0–9 enter digits; CLEAR erases the
whole input; ENTER submits only four digits; CANCEL returns the card and exits the
camera. Existing button travel, indicators and beep remain active.

PIN checks run on the server against the issued card's linked account. Each check
revalidates the card held in ATM custody, the active operator, ATM range and obstruction. Requests are
limited to one per second and responses are matched to their request/session;
late responses after removal are ignored. Input is cleared on submission/removal,
and PIN request string representations redact both PIN entries. Accounts without
a PIN enter Create PIN then Repeat PIN. The server requires matching entries and
refuses to overwrite an existing account PIN through setup. The PIN belongs to the
linked account, so its other issued cards use the same PIN.

Successful sign-in or PIN setup opens the LCD main menu. The four left physical
screen buttons select withdrawal, deposit, balance and recent transactions; the
bottom right button returns the card. Withdrawal/deposit amounts use the physical
number keys in whole dollars, CLEAR erases, ENTER confirms, and CANCEL or the
bottom right key returns to the main menu. The result page shows the outcome and
balance. Recent transactions lists the latest four entries.

Menu actions require a server-issued, five-minute token bound to player, card,
account, ATM and dimension. Requests have increasing sequence numbers to prevent
cash-operation replays. Each action rechecks the card and physical ATM access;
returning the card, logout and server shutdown revoke the session. Withdrawal and
deposit reuse the original cash handlers, including frozen-account checks,
withdrawal policies, rollback and transaction history. Deposits come from the
existing wallet/inventory storage. Withdrawals debit once into persistent ATM
custody; cash enters a collector's wallet/inventory only after pickup finishes.
Timeout messages advise checking
balance/history before retrying; money operations are never automatically retried.

## Withdrawal bill choice

ENTER on a withdrawal amount first sends `CHECK_WITHDRAW`, reusing the cash handler's
card/session authorization, balance, frozen-account and withdrawal-policy checks without debiting,
spending daily limits, recording a transaction or reserving cash. The shared LCD then
asks whether to choose bills. No (bottom-right key) confirms the automatic minimum-note
plan. Yes (bottom-left key) opens calculated, exact-value denomination mixes.

The options prefer each supported largest denomination ($100, $50, $20, $10, $5,
$2, $1), remove duplicate mixes and show the breakdown and bill count. Three mixes
appear per page, selected with the top three left keys. The top two right keys page
back/forward; bottom-right Back returns to the Yes/No question. Change amount (third
right key on that question), or keypad CANCEL, returns to amount entry without
clearing the amount. New checks and page state are shared with observers.

The server binds the check to the authenticated session and amount. Confirmation
sends only an option index (-1 for automatic); the server recalculates the bill plan
and repeats all withdrawal checks before debiting. Forged indices, changed amounts,
negative counts and incorrect totals cannot spend money. Editing/backing to the
main menu invalidates amount approval. Confirmed plans enter existing ATM cash
custody, so bill-by-bill animation and sound follow the selected denominations.

## Physical withdrawal and receipt sequence

The return motor runs for 26 server ticks (1.3 seconds), then the LCD asks for card removal and stays in the
ATM camera after the existing card-removal animation. Its completion is acknowledged
to the server. After a further 40 ticks, the cash shutter slides upward and backward
into the housing over 25 ticks. Each banknote then feeds individually over eight
ticks (0.4 seconds), followed by a two-tick gap. Feed duration and sound pulses
follow the actual bill count, including mixed denominations and more than seven bills. The camera
stays at its original LCD position and distance. Downward cursor look is limited
to 30 degrees, reaching that limit at 85% of screen height with slow vertical easing.
Bills stop centered on the cash slot,
half inside the machine, with straight aligned layers and no decorative rotation.

During the final 12 ticks of the return motor, the credit card eases partway out and stays
visible without a hand until clicked. Pickup begins at that same protruding position.
The reader's physical LED collar is a separate rendered model; its brightness gently
cycles between the original green at 100% and a darker 55% tone over 48 ticks while
waiting for card removal, then returns to steady illumination. The dynamic renderer
supplies explicit green RGB; its white atlas swatch has no second baked color tint.
The lower card guide is dark green when empty and uses the original bright green
while the card physically occupies the reader. No halo or floating ring geometry
is added.

The exported shutter panel and eleven ribs are removed from the static placed
model and rendered separately; inventory previews retain the complete cabinet.
`atm_dispenser.json` derives cash/receipt anchors from the grouped source model.
Cash uses the existing denomination artwork for every requested bill. Each bill
starts completely behind the outlet, slides onto the previous bills, and remains
in the stack. Layer spacing compresses for larger stacks to fit the physical outlet;
no bills are omitted. Cash is collectible only after the last feed finishes.

Clicking the cash, or the bottom-right physical key beside “Take cash”, starts a reach, grasp, lift into the close camera view and tuck-away
animation using the player's skin. Cash remains in ATM custody until the server completes the 26-tick pickup.
Only one collector can reserve a pickup; interrupted or failed pickups leave the
cash available. Pending cash and dispenser state survive save/load. Leaving the
ATM never grants cash automatically, and another nearby player may collect it.
An abandoned view returns its card to the owner or retains it in persistent recovery
custody until login; the server proceeds to dispensing rather than locking the machine.
Destroying the ATM drops its remaining cash exactly once.

Only an empty cash slot closes. After 25 closing ticks, the collector is offered
a receipt (left bottom screen key Yes, right bottom key No). The choice counts down
from ten seconds and defaults to No after 200 server ticks. Yes prints a paper
strip over 40 ticks at the receipt printer, showing withdrawal amount only. The
complete receipt is preprinted during the first 10 ticks, then fed out over the
remaining 30 ticks. Paper
is 0.12 blocks long, angles outward at 44 degrees, and its tip rests just above the
actual keyboard deck. Excess blank paper is trimmed from the printed surface.
Ink is baked into the full paper surface before rollout. The finished sheet moves
through the printer edge using a cropped texture window; ink moves with the paper
rather than appearing as stationary lines or floating glyphs. Printed surfaces are
cached by amount, with a maximum of sixteen textures retained.
Clicking
it plays a pickup and gives one named paper receipt after 22 ticks. No, or completed
receipt collection, returns to the welcome screen and regular player camera.
Printed receipts remain until collected; abandoned receipt-choice prompts clear.

Cursor normalization handles zero-sized/minimized windows and invalid coordinates;
camera smoothing also recovers from a non-finite prior sample. This prevents NaN
rotations from reaching the detached camera and its audio listener.
Fonts are bundled Liberation Sans, with their OFL license in `META-INF/licenses`.

Overlapping artwork uses separate depth layers spaced 0.0005 blocks apart, with
text ahead of all shapes. Do not flatten those layers: coplanar card, panel and
button primitives flicker under shader depth precision. The artwork remains
depth-tested against the cabinet and world.

- Group: `NCR SelfServ 22 / 03 LCD and 8 function keys / LCD Screen and Bezel`.
- Cube name: `LCD welcome screen`.
- North-facing display surface: x=5..11, y=22.12..26.32, z=1.955 (model units).
- Cube depth ends at z=2.02; the display has no cube rotation.
- Atlas: `ultimatebankingsystem:textures/block/atm_machine.png`, 2048 x 2048.
- Pixel rectangle: x=0..1024, y=256..1024, end exclusive; 1024 x 768 pixels.
- Minecraft face UV: `[0, 2, 8, 8]`; normalized texture UV: `[0, 0.125, 0.5, 0.5]`.
- Facing is applied around the block center by the blockstate model rotation.

Only the north face uses the LCD rectangle. Keep the surrounding atlas intact
when changing the screen so the keypad legends and cabinet materials are preserved.

## ATM camera cursor

`AtmCameraController` draws a 16 GUI-pixel cursor from `AtmCursorShape`
after arrival. The idle shape has one ring; the action shape has two rings.
All vertices are pure white; alpha-only edge fringes provide smoothing without
dark outlines. Rings are 1.1..1.5 GUI pixels thick and dots are 2.7/3.3 pixels
in diameter for idle/hover respectively. The operating system cursor, Minecraft HUD and hand remain
hidden. Escape hides the circle and starts the return transition.

Cursor position adds smoothly eased yaw/pitch offsets, limited to 1.25 degrees
horizontal, 0.85 degrees upward, and 30 degrees downward. Downward motion grows
quadratically toward the bottom edge to reveal the full keypad while keeping
motion around the LCD center subtle. It never translates the camera into the
cabinet or moves the player. Smoothing uses elapsed real time rather than frame count.
Vertical cursor following responds four times more slowly than horizontal following,
so revealing the keypad is a gentle movement rather than a quick tilt.

## Lighting

Lighting is baked onto the existing opaque model faces. The root model inherits
`models/block/atm_machine_body.json`; there are no translucent halo or spotlight
sheets. This avoids rectangular artifacts in shader packs that treat translucent
block geometry as glass or water, and keeps light attached to the hardware.

The reader collar and card guide use full-bright level 15 block and sky face
lighting so their physical surfaces stay luminous under shader exposure. The collar
samples the lamp's white atlas swatch with a `ff55ff77` green tint instead of the
dark painted-green swatch; this raises actual LED luminance without adding any
overlay geometry or changing the LCD. The eight LCD button rings use
level 13 on their fronts and 10 on their sides; centers and sockets remain dark.
The physical lamp lens uses level 15. Only the keypad's top faces receive a
back-to-front falloff of 13, 13, 12, 12 across the four rows; the recessed border
uses level 11, the metal deck top level 9 and the controls fascia front level 8.
This creates subtle surface light spill below the display while preserving
key legends, without overlaying a circular glow on them.

The LCD north face uses level 14 block light, no artificial sky light, and a
neutral `ffb0b0b0` vertex tint. This caps its white point while preserving the
high-resolution artwork and relative text contrast. It reduces the input to
shader bloom rather than changing the user's shader settings. Shader packs
still control final exposure, bloom, and tone mapping, so no universal bloom
threshold is guaranteed. Only the reader and lamp use full-bright sky light;
the LCD, keys and LCD button rings use zero artificial sky light.

The block emits ordinary Minecraft light at level 5. Face lighting does not add
colored world illumination or dynamic spotlight shadows. No external shader or
lighting mod is required. The lamp remains grouped under `04 Controls / Keypad
Lamp`; LCD atlas coordinates above are unchanged.

## Physical button presses

`atm_machine_body.json` retains the complete inventory model. The placed block
uses `atm_machine_static.json`; all 16 keypad keys and the eight LCD button
caps/centers are separate `block/atm/buttons/0..23.json` models rendered by the
ATM block entity. Sockets remain static. The exporter writes `atm_controls.json`
from the same source bounds and rotations, so click targets follow model edits.
All button parts preserve their label UVs and lighting.

In the docked ATM camera, left-clicking a key depresses it 0.08 model units into
its own surface and returns it over 240 ms. Keypad movement follows its -22.5
degree deck tilt; LCD buttons move into the cabinet. Cursor picking uses the
actual camera orientation, rendered FOV and the ATM facing. Presses during the
camera transition or return are ignored. The server mirrors accepted keys and indicator presses to nearby players. Banking
actions still require the authenticated, occupied ATM session.

`atm_keypress.ogg` is a single 150 ms beep sampled from the user-provided
`alex_jauk-atm-keypad-beep-481754.mp3` (0.205..0.355 seconds), converted to mono
Ogg Vorbis at 44.1 kHz with 2 ms edge fades. All sixteen keypad keys and eight
LCD buttons use this same recording. The separate synthesized keypad tones and
per-key sound registrations have been removed. The sound plays only when a
valid button accepts a press. Existing ATM placements gain the new block entity
on chunk load; no replacement is required.

Each LCD button's `screen guide` line has separate opaque on/off models in
`block/atm/indicators/16..23_on/off.json`. The renderer lights only the matching
line in white during the button's 240 ms press/return animation. The line stays
fixed on the bezel while the button cap moves. Off uses the original material;
on samples the white lamp swatch at full brightness. No translucent overlays
are used, and the inventory model retains the original unlit guides.

The ATM cursor switches to the double-ring action shape over any of the
24 pressable controls and returns to the idle single-ring shape elsewhere.
Hover and clicks share the same camera-ray picking method, including FOV,
ATM facing and clamped cursor coordinates. Hover only selects an image; it
never depresses a button, plays a sound or lights a press indicator. Both
shapes retain the same 16 GUI-pixel bounds and center. The older PNG concepts
are retained as references and are no longer sampled by the cursor renderer.

## Card reader insertion and removal

Physical ATMs accept another player's valid issued card. The linked account's PIN
is still required, and blocked/expired cards, frozen accounts, withdrawal limits,
exclusive operator access and replay checks remain enforced. Balance, deposits and
withdrawals target the inserted card's account; using it does not transfer account
ownership. Shared cash handlers permit this only through an authenticated session
bound to the actual ATM and custody card. Legacy/non-ATM requests retain owner-only
checks. The exact card is returned to its inserter, including interruption recovery.

Click the reader in the docked ATM view to insert a held credit card. The reader
uses `atm_reader.json`, exported from the actual reader bounds and insertion
slit's front-center, for both cursor picking and animation placement. Its hit
identifier is 24; the existing keypad/LCD controls remain 0..23.

The client requests server validation with an ATM position and unique request
ID. The server checks the ATM, range, line of sight, both held stacks and the
existing `CreditCardService` rules (issued card, account existence,
blocked/expired status and nonempty card number). Blank/empty/non-card items
are rejected. The server removes one actual card from the accepted hand into
persistent custody before returning a render-only snapshot. Duplicate requests cannot
take another card, and late responses cannot reopen an abandoned camera session.

A valid insertion takes 3 seconds (alignment, motor intake and reading). The camera gently focuses the reader while
the player's skinned hand brings the actual card to the mouth, slides it in and
withdraws. The card is scaled to physical card proportions and rotated short
edge first, with its face upward. Hand motion stops before entering the cabinet;
the final card travel is hidden by the existing opaque reader geometry. The
camera returns toward the LCD as the hand withdraws. Both handedness settings
and offhand cards are supported.

The return motor keeps the normal LCD camera view. Only hand pickup focuses the
reader, so Escape does not play the focus-and-return camera movement twice.

Click the reader again to run a 1.35-second removal. Escape queues removal first
if the card is inserted or being inserted, then returns the camera to the
player. Pending validation can be canceled immediately. A missing ATM, changed
world, death, or interrupted screen clears the visual session safely. Validation
timeouts reset after ten seconds. The original item and all its components are stored
in server-only `ubs_atm_cards` SavedData. Normal ejection returns it after the motor
and hand pickup finish. Exit, disconnect, death and block removal release custody;
offline owners recover their exact card at login, without requiring the ATM chunk.
The original inventory slot is preferred; an occupied slot falls back to inventory,
or an owner-targeted item drop when full. No replacement card is minted.

A server-granted, heartbeat-maintained view lock covers the entire camera session,
including the welcome screen before insertion. Competing camera, keypad, card and
cash/receipt requests are rejected while occupied. Abandoned cash remains collectible
after the owner leaves. Locks clear on exit, distance/dimension changes, death,
disconnect or heartbeat expiry, and one player can reserve only one ATM.

All clients tracking the block receive its LCD state and card animation stage/start
tick, including observers arriving mid-animation. Welcome/language, PIN setup and
verification, menus, amounts and results use the same server display snapshot as the
cash/receipt screens. Only PIN length is sent: neither PIN entry is serialized.
The card visual includes printed card fields, without its issued-card identifier,
account linkage or arbitrary private custom data. Nearby players see insertion,
partial ejection and hand pickup, while the player's world body stays in place.

Checks: `python -m unittest discover -s tools -p test_export_atm.py`, focused
`AtmCardMotionTest`/camera/cursor unit tests, and the ATM GameTest namespace.
Coverage includes animation phases and interrupts, physical slot alignment for
all facings, reader hit tests, server validation/rejection cases, and packet
identity/card-snapshot round trips.


## ATM mechanism recordings

The supplied Alex Jauk recordings are converted to mono 44.1 kHz Ogg Vorbis:
`atm_card_mechanism.ogg` is 1.3 seconds. The cash recording is split into
`atm_cash_prepare.ogg` (3.25 seconds, source 0.35..3.60) for preparation and opening,
and `atm_cash_dispense.ogg` (0.4 seconds, source 4.35..4.75) for each individual
bill feed. Short edge fades prevent clicks. Original MP3s are unchanged.

Card insertion aligns the hand for eight ticks, feeds the card for one 26-tick
motor cycle, then reads the fully inserted card during a second cycle. The LCD
waits for both cycles to finish. Ordinary card return also runs the motor before
the hand collects it; withdrawal returns the card before allowing cash preparation.
The shared durations are defined in `AtmSoundTiming`.

Sounds broadcast once per card stage, once for cash preparation and once per bill
from the physical reader or cash outlet using
the Blocks category, original pitch and volume 0.7, with a 12-block attenuation
distance. Duplicate return messages are rate-limited. Cash collection is enabled
only after the complete dispensing cycle. Nearby players hear these sounds too.


The receipt printer uses the supplied `freesound_community-receipt-printing-82897 (1).mp3`.
`atm_receipt_print.ogg` combines source 2.70..4.20 seconds of short printing pulses
with source 8.70..9.20 seconds of the louder finishing buzz. The complete edit is
two seconds, matching PRINTING and ending as the receipt becomes ready.
`atm_receipt_tear.ogg` contains source 9.84..10.20 seconds and plays once at pickup
tick 9, when the reaching hand grips and pulls the paper. Clicking alone, printing,
waiting for collection, declining, and repeated pickup requests do not play a tear.
A collector who leaves before reaching the paper cancels pickup without tearing it.
Both edits use mono 44.1 kHz Ogg Vorbis, a 180 Hz high-pass and 7 kHz low-pass filter,
8 ms edge fades and separate gain adjustments preserving the louder finishing buzz.
Sounds come from the rotated receipt slot in the Blocks category with 12-block
attenuation. The supplied MP3 is unchanged; the earlier procedural printer is replaced.


## Physical deposit and floating bill tray

Deposit on the LCD opens the existing shuttered cash port as a combined feeder.
A minimalist floating rail fades in at the left of the ATM view, showing the seven
bill denominations, their artwork and combined available quantities from loose cash
and owned wallets anywhere in the player's inventory or offhand. Foreign-owned wallets
are excluded. Feeding deducts owned-wallet bills first, then loose cash, without
changing the storage preference used by withdrawals or legacy banking. Zero availability dims the artwork and quantity to gray.
Left click adds one bill to the cursor; right click adds up to 64 of that denomination.
Repeated clicks accumulate a mixed selection, bounded by the actual available stock.
Dropping away from the feeder cancels the local selection, without moving real cash;
Escape first clears a dragged selection. Availability is sent only to the operator.

A release over the open feeder sends the selected denominations with the authenticated
session token and its monotonic request sequence. The server checks custody, account,
stock and exact nonnegative bill counts, removes the selected notes once, and only then
starts a shared two-second hand/stack insertion. The feeder remains fully open during
DEPOSIT_FEEDING and DEPOSIT_INSERTING, including all waits between batches. The tray
stays visible but disabled while a batch is being accepted. The camera eases back 0.3
blocks in deposit mode; selecting cash gently focuses the feeder and holds that view
steady while dragging, so the drop target does not chase the cursor.

Continue (bottom-right LCD key, or clicking the actual LCD surface) closes the feeder
and counts for 65 ticks. The tray fades out. The shared LCD then shows every detected
denomination and the total that will be credited. Counting and review return smoothly
to the original LCD camera distance. Back to feeding (third right key) reopens the
feeder, preserves all pending bills and restores the feeder camera for more drops. Confirm (bottom-left key) rechecks
the account and credits exactly once, records ATM Cash Deposit and returns to the
normal result/main-menu flow. Return cash (bottom-right or CANCEL) opens the feeder,
slides the uncredited notes out and waits for physical pickup. Return remains available
when account authorization expires or the account is frozen. Only the depositor can
collect these returned bills. The feeder closes after pickup, not between feed batches.

Pending notes live in server-only `ubs_atm_deposits` SavedData, bound to operator, ATM
and target account. They cannot be withdrawn, credited twice or claimed by another
operator. Confirmed money is removed from custody; cancellation refunds its exact
denominations once. Exiting, disconnecting, dying or breaking the ATM refunds pending
cash; an offline operator recovers it on login even without loading the ATM chunk.
Cash never credits before confirmation, and coins are excluded from the floating tray.
