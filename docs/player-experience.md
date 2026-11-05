# Design the game from the player's perspective
Status: working design, not a finalized feature order.

## Confirmed foundation
Players use Java Minecraft and a required resource pack. They form player groups, build on the main survival world, and encounter PvP outside designated safe areas. Death drops carried items, weapons and armor. KOTH and Jabba are proven activities. Offline bases may become vulnerable after warpoints are depleted. Force powers and resource planets are deferred.

## Player-facing group identity
The user wants a name other than Factions and a replacement for the `/f` player command. The final name and command are undecided. Use "player group" as a neutral planning placeholder. Commands, help, menus, messages and tutorial language must use the chosen identity consistently. FactionsUUID remains the selected underlying integration; its technical name is not the server's player-facing identity.

Advisor shortlist, not accepted names:
- Clans: `/clan`, with `/cl` as a proposed short alias. Recommended for the MandoMC identity and territorial groups; [Clan Wren](https://www.starwars.com/databank/clan-wren) provides a direct Mandalorian precedent.
- Crews: `/crew`. A more informal group identity that also fits Star Wars adventurers, such as the [Ghost crew](https://www.starwars.com/databank/Hera-Syndulla).
- Syndicates: `/syndicate`, with `/syn` as a proposed short alias. Strong underworld flavor, exemplified by the [Pyke Syndicate](https://www.starwars.com/news/who-are-the-pykes), but implies a criminal identity for every group.

These are naming recommendations, not evidence of current Minecraft player preferences. Verify command ownership and aliases when implementing the selected integration.

## Confirmed first session and recovery
New players arrive in SpawnWorld, an existing map file the user already has. They receive a short tutorial, with its presentation still undecided. Their first goal is to travel to survival and establish a base. Survival arrivals use a fixed protected landing zone.

A solo player can create a group and claim a starter base on eligible land outside the spawn safe zone, without a credit requirement. A group creation cooldown of around three hours was suggested tentatively; neither the cooldown nor its duration is decided. Claim capacity and other later territory rules remain open.

Death loses only what the player carries. Respawn priority is group home, then a valid bed, then SpawnWorld. If no valid group home or bed is available, use SpawnWorld. A player with stored group supplies can re-equip from that stockpile. Otherwise they repeat the same acquisition process used to obtain the gear originally; there is no separate replacement mechanic. Voluntary return travel and specific recipes/prices remain open.

Combat blocks voluntary teleporting. Logging out while in combat causes death, with the normal carried-item loss. Combat tag duration, triggering actions and server-initiated disconnect handling still need definition.

The first join grants a basic blaster, vanilla tools and food. Equipment is granted only once, not on subsequent joins or deaths. Exact items, quantities and ammunition remain to be configured. Starting credits are possible but neither their inclusion nor amount is decided.

The first friends playtest should bring the in-scope gameplay systems together in a rough, integrated version. The development slices below are incremental building and checking steps toward that target. The precise content depth for each system still needs agreement; force powers and resource planets are excluded for now.

## Proposed everyday loop
Establish a base, gather supplies, prepare equipment, venture out, fight or complete an activity, return with loot, and fund the next expedition. Building gives the group a home and stockpile. Credits come from a mixture of resource selling and activity rewards. Specific equipment variants, recipes and prices still need decisions.

Resource planets are deferred. The initial supply loop must work with ordinary survival resources and in-scope activities. Revisit special resource planets only when the user brings them back into scope.

## Design order
1. Describe the experience: what the player sees, chooses, risks, earns and does next.
2. Set rules and failure cases: death, disconnect, restart, abuse, safe zones and other players.
3. Choose the smallest playable version and the dependencies it needs.
4. Define the parameters used by that version.
5. Implement the system, then add and balance content through configuration.
6. Play it before expanding the feature.

Use [the feature brief](templates/feature.md). Record unresolved gameplay choices as unresolved; infrastructure work must not silently decide them.

## Proposed development slices
| Slice | Player can… | Why it comes here |
| --- | --- | --- |
| Arrival and protection | Join, accept the pack, understand the safe starting area | Establishes a place to test everything |
| Basic combat | Obtain one blaster and armor set, fight, die and re-equip | Tests the smallest full-loot loop |
| Group home | Join a group, claim a base and cooperate without friendly damage | Gives combat and gathering a shared purpose |
| Supplies and progression | Gather, craft and buy a useful replacement loadout | Makes loss and recovery sustainable |
| One event | Compete in KOTH or run Jabba for useful rewards | Adds a repeatable social objective |
| Mobility | Use a speeder, then test aerial transport | Builds on meaningful destinations |
| Raiding | Understand vulnerability and perform a limited explosive breach | Requires stable combat, claims and recovery rules |

This is a starting sequence for discussion. Travel details, event schedules/rewards and warpoint balancing still need decisions. Resource planets are outside the current scope.

## Next player decisions
- Define how a group recovers warpoints after losses.
- Define what attackers can do to a vulnerable base and what remains protected.
- Then settle KOTH reward delivery and remaining event configuration details.

Group naming and the travel recommendation remain open. Combat tagging details and initial equipment quantities can be settled when their feature briefs are written.

## Accepted acquisition loop
Sell ordinary survival resources to buy basic blasters and ammunition; craft basic armor from survival materials; earn credits and rare components from KOTH/Jabba; use those components to craft lightsabers and advanced equipment.

The user accepted this loop on 2026-11-05. It also supplies the normal route for replacing lost equipment. Specific recipes, equipment variants, prices, drop rates and grind duration remain open. Resource planets are not required.

## Saber baseline and equipment power
The user uses shield-based models for lightsabers, taking advantage of shield blocking. Their earlier implementation included reflection and saber throwing. Recover that approach as the starting point; a new timed-parry requirement has not been accepted. Separate Force powers remain deferred.

The intended progression has a large power difference between basic and top-tier gear. Exact damage, protection, availability and time-to-kill targets are undecided. This is a deliberate power progression, not a mostly-sidegrade equipment model.

Keep a configurable guard/overheat limit so sustained fire can temporarily overwhelm saber defense. Ordinary Minecraft swords, bows, crossbows and shields are removed from the intended gameplay. Recognized custom sabers remain allowed despite using the shield material. Star Wars-themed swords and bows may be added later; they are not in the current scope.

Vanilla enchantments remain part of progression. Applicable enchantments on each custom item, wear, and repair behavior still need verification and explicit configuration. The user has not chosen a separate repair system. Avoid accidentally making the guard limit disappear through an item's enchantment or repair behavior; separating guard state from permanent item wear is a recommendation to evaluate during implementation.

Source inspection of `bilal/legacy-lightsabers` confirms:
- Saber recognition requires `SHIELD` material and a saber tag; saber definitions include models, recipes and combat values.
- [The deflection listener](https://github.com/AstuteFlamez/MandoMC/blob/bilal/legacy-lightsabers/recovery/lightsabers/src/main/java/net/mandomc/gameplay/lightsaber/listener/SaberDeflectListener.java) redirects projectiles toward the blocking player's facing direction. The listener itself has no timed-parry or incoming-angle check.
- [The throw listener](https://github.com/AstuteFlamez/MandoMC/blob/bilal/legacy-lightsabers/recovery/lightsabers/src/main/java/net/mandomc/gameplay/lightsaber/listener/SaberThrowListener.java) launches on sneak plus left-click, removes the held saber, and implements a returning throw with configurable damage/cooldown and cleanup paths. Some old item lore describes different controls, so choose one control scheme when porting.
- [The stamina manager](https://github.com/AstuteFlamez/MandoMC/blob/bilal/legacy-lightsabers/recovery/lightsabers/src/main/java/net/mandomc/gameplay/lightsaber/SaberStaminaManager.java) consumes shield durability on deflection, then resets it and temporarily disables shielding on overheat. The user has accepted retaining a configurable guard limit; the old values and durability implementation are reference material.

These are source observations, not new-server runtime verification. Porting must validate current weapon hooks, reflected-hit attribution, directional blocking, region/group protection, and item ownership across death or disconnect. Legacy values are reference material, not accepted balance settings.

## Accepted event structure
Jabba is one shared dungeon with PvP competition, opened through a server-wide schedule. Chest and boss loot can be taken or stolen by anyone; it is not reserved to a contributing player or group. The legacy source contains a room/keycard progression, bosses and randomized loot chests to recover selectively. Current maps and MythicMobs definitions still need restoration; this inspection does not establish runtime compatibility.

KOTH capture progress and victory belong to a player group; an unaffiliated solo player can compete as their own contestant. Players who die during either event may re-equip and return as often as they can while it remains active. Normal full-loot death and respawn priority apply.

The user delegated the KOTH rules to a conventional model. Selected baseline: continuous capture, adapted to groups. The first eligible contestant to take control owns the timer. It continues while at least one member remains alive and eligible inside; it resets when that contestant is entirely cleared out. Enemy presence alone does not pause or reset the timer: opponents must kill or push the holders out. Each fresh capture starts from zero, with no accumulated score. Start with a configurable three-minute timer. KOTH reward delivery and ownership are still open; group victory does not by itself decide who receives physical loot.

This differs from the recovered `KothActiveTask`, which resets immediately when multiple contestants occupy the zone. Change that rule deliberately during porting. Published precedents checked 2026-11-05: [AxKoth capture mode](https://docs.artillex-studios.com/axkoth-capture.html) uses uninterrupted occupancy and a three-minute default; [Netherite's KOTH guide](https://netherite.gg/wiki/lifesteal/combat/koth) describes losing capture on leaving, death or disconnect. The group adaptation and starting duration are advisor-selected under the user's delegation; these examples do not establish a measured majority of servers.

## Event losses and base vulnerability
The user confirmed that group members' PvP deaths at KOTH and Jabba lower the group's warpoints. Event losses therefore affect both carried equipment and the survival base's vulnerability, including after the group logs off. The earlier rule allowing offline raiding when warpoints are depleted still applies. Loss amounts, recovery, other death causes and the exact breach permissions remain undecided.

## Travel recommendation — awaiting user acceptance
Use free commands and a destination menu for routine travel, with optional themed transport terminals that open the same menu. The proposed first version has:

- `/spawn`: return to SpawnWorld.
- `/travel`: select a public destination; survival always arrives at its fixed protected landing zone. Future planets use their own public arrival points, rather than teleports directly onto objectives.
- Group-home command: return to the home set inside the group's own eligible claims. The command root follows the naming decision above; `/f` is not the proposed player-facing root.
- A five-second stationary warmup, cancelled by movement or damage. All entry points enforce the same combat restrictions. Five seconds is a suggested initial tuning value, not an accepted rule.
- No death-return `/back` and no network of arbitrary personal homes initially. Exploration, journeys from landing zones to activities, and reaching other bases provide routes for walking, speeders and aircraft. Teleporting to other players remains a separate open choice.

A shuttle terminal in SpawnWorld can teach first-time travel while returning players use commands. Routine travel should be free initially; vehicle fuel and other economy costs are separate decisions. Combat tag duration remains open; a 30-second PvP tag is a candidate for playtesting, not an established requirement.

Research checked 2026-11-05: [Minecadia's published faction guide](https://wiki.minecadia.com/index.php?title=Faction_Commands) documents `/f home` and menu-based faction warps (page last edited March 2023). [Complex's October 2026 guide](https://www.mc-complex.com/forums/vanilla/wiki-page/56/factions-commands-full-f-command-list-2026/) documents faction-home travel with a warmup and restricted activity areas. [PirateCraft's getting-started guide](https://piratemc.com/guides/getting-started/) combines ships with free public warps, portals and home teleports; the guide contains older version references. These are examples of published designs, not live-server tests or a player-preference survey.

Advisor prediction: this audience will probably favor convenient routine travel with Star Wars presentation over mandatory walks to transport points for every trip. Limited destinations preserve reasons to explore and use vehicles, though easy home travel reduces transport risk. Validate that tradeoff in playtests instead of claiming a measured majority preference.

## Configuration-driven systems
Use explicit schemas and stable IDs. YAML declares content and tuning; Java implements behavior, validation, lifecycle and integration.

- Items: ID, presentation/model reference, category, recipe and acquisition.
- Sabers: item reference, damage, stamina costs, block/deflect rules and supported behavior type.
- Vehicles: model reference, movement type, seats, speed/acceleration, fuel, health and supported weapons.
- Events: location, schedule, participant rules, objectives and rewards.
- Resources: location/node type, yields, regeneration and access rules.

Only add parameters when the implemented feature uses them. First prove one item/saber/vehicle and a second configuration variant; generalize the variation we actually need. Avoid inventing a scripting language or a universal effect system.

Validate bounds and cross-references at load time. Errors name the file and key. Editing a price or speed should not require Java changes; adding a new behavior may. Reload only where active-state handling is defined. Schema changes require migrations for saved data and clear compatibility notes.
