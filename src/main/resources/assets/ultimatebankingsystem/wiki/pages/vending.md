# Vending machines

> Eight product windows with per-slot price and stock.

## Owner setup

Open a machine you own to select a product slot. Set its display item from your inventory, enter a whole-dollar price and save it. The item data is copied to the display; the physical items still need to be stocked separately. Choose the settlement account before selling.

![Stocked vending machine in the world](screenshot:ultimatebankingsystem:50?width=722&height=812)

![Vending owner controls and product windows](screenshot:ultimatebankingsystem:51?width=1967&height=1151)

**Show Stock** opens storage for the selected slot. It accepts only items matching the displayed product and its components. Add items there to make the stock count available to buyers; use the cash-vault control to collect any physical cash held by the machine.

![Physical stock for one vending slot](screenshot:ultimatebankingsystem:52?width=1202&height=660)

## Buying

Customers see each stocked product, its price and a payment selector. Choose a product and confirm the payment rail; unavailable stock cannot be sold. The stock change and payment result are handled by the server.

![Vending customer purchase screen](screenshot:ultimatebankingsystem:53?width=1970&height=1152)

## Creative variant

Creative vending machines can display unlimited stock; their proceeds are voided. Operators can give regular or creative variants with `/ubs admin vending give`.
