# Bank and heist administration

> Approvals, premises and heist recovery.

## Bank oversight

Bank applications and premises are administered under `/ubs admin`. Player-bank creation and approval rules are set in common config. Use the Bank Owner PC for day-to-day owner policy and physical premise setup.

## Heist controls

`/ubs admin heist list` lists sessions. `/ubs admin heist inspect <sessionId>` shows details. `/ubs admin heist abort <sessionId>` ends an active session; `recover <sessionId>` resolves a stuck one.

Cooldown clear commands are under `/ubs admin heist clearcooldown`. Use these only after checking the reason a target is unavailable.
