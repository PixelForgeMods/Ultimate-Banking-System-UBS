# Job NPC administration

> Place role-specific NPCs and manage appearances.

## Spawn NPCs

`/ubs admin job spawn market` places a Job Market NPC. `/ubs admin job spawn buyer <job-id>` places a role-specific buyer or Courier dispatcher; command suggestions list known roles.

There are dedicated role commands too, such as `/ubs admin job spawn lumberjack` and `/ubs admin job spawn courier`. The UBS Jobs creative tab contains the job NPC items.

## Appearance

`/ubs admin job npc edit` opens the NPC appearance library. Select a role on the left, then apply a saved skin, look up a username or UUID, or add a PNG to the server's `ultimatebankingsystem/npc_skins` folder and refresh. Preview the full model before applying.

![NPC appearance library with role and skin selection](screenshot:ultimatebankingsystem:67?width=2558&height=1438)

Choose default or slim model for the selected role. Appearance is shared by NPCs of that role.

## Reload

`/ubs admin job reload` refreshes job definitions. Check the Job Market and buyer after changing content or installed mods.
