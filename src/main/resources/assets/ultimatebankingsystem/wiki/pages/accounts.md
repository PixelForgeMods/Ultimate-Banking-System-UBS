# Accounts and balances

> Open an account, choose a default and follow your transactions.

## Open an account

`/account open` creates a Central Bank checking account. You can also visit the Central Bank teller and use its account-opening option. Other banks may offer different terms; inspect those at their own teller before opening an account.

![Central Bank teller account-opening screen](screenshot:ultimatebankingsystem:02?width=1730&height=1047)

## Find and choose accounts

`/account info list` shows your accounts and their IDs. `/account info` shows the primary account, and `/account balance` checks its balance. Use `/account primary set <accountId>` to choose the default account for supported payments and payouts. Copy the ID from the list; account names alone may not be unique.

![Account list and primary-account details](screenshot:ultimatebankingsystem:01?width=997&height=540)

## Check transactions

`/account transaction list <accountId>` lists activity for one account. Open an entry with `/account transaction <transactionId>` for its details. You can also review history at an ATM. A transaction ID identifies an entry, not an account.

![Transaction list returned in chat](screenshot:ultimatebankingsystem:04?width=1032&height=558)

![One transaction expanded in chat](screenshot:ultimatebankingsystem:05?width=997&height=233)

`/account help` lists the remaining account commands, including shared and business-account actions.
