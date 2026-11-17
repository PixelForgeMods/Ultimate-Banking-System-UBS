# Configuration and limits

> What operators can tune and how time is interpreted.

## Economy policy

The UBS common config controls transaction limits, withdrawal limits, interest, credit, bank creation, shop behavior, job payouts and heist pacing. Check the generated config file for the exact key names and accepted ranges for this build.

AccountTransactionLogLimit caps saved account history. The current default is 10,000; reducing it lowers saved data volume but shortens account statements.

## Time

Shop opening hours use the server timezone for enforcement. The shop UI can display those hours in the client's timezone without changing them.

Job daily quests follow real-world UTC days. Some other limits advance by Minecraft days. Avoid relying on `/time add` to test real-world resets.
