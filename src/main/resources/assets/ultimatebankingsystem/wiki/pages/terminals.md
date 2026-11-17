# Payment terminals

> Fixed checkout and handheld charges.

## Fixed terminal

Right-click a configured payment terminal to pay. The payer uses an eligible held card or primary account. The display changes from idle to success or denial, so wait for the result before leaving checkout.

![Fixed terminal idle](screenshot:ultimatebankingsystem:23?width=2560&height=1440)

![Fixed terminal approved payment](screenshot:ultimatebankingsystem:24?width=2560&height=1440)

![Fixed terminal denied payment](screenshot:ultimatebankingsystem:25?width=2560&height=1440)

The configured target, amount and account must be valid. A denial does not transfer funds.

When a fixed terminal is **linked to a shop cashier**, configure checkout through the shop/cashier controls. Shift-right-click does not open terminal configuration in that linked state. Standalone terminal configuration may differ.

## Handheld terminal

A handheld payment terminal lets a merchant charge a targeted player. Shift-right-click to set its label, amount and receiving account, then save. Right-click the target player with the item to attempt payment. Confirm the receiving account before taking a payment.

![Handheld terminal item tooltip](screenshot:ultimatebankingsystem:48?width=798&height=282)

![Handheld terminal configuration](screenshot:ultimatebankingsystem:49?width=1126&height=636)

The handheld terminal does not provide redstone outputs; use a fixed terminal for redstone-driven checkout feedback.
