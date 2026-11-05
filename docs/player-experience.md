# Design the game from the player's perspective
Status: working design, not a finalized feature order.

## Confirmed foundation
Players use Java Minecraft and a required resource pack. They form factions, build on the main survival world, and encounter PvP outside designated safe areas. Death drops carried items, weapons and armor. KOTH and Jabba are proven activities. Offline bases may become vulnerable after warpoints are depleted. Force powers are deferred.

## Confirmed first session and recovery
New players arrive in SpawnWorld, an existing map file the user already has. They receive a short tutorial, with its presentation still undecided. Their first goal is to travel to survival and establish a base. Survival arrivals use a fixed protected landing zone.

A solo player can create a faction and claim a starter base on eligible land outside the spawn safe zone, without a credit requirement. A faction creation cooldown of around three hours was suggested tentatively; neither the cooldown nor its duration is decided. Claim capacity and other later territory rules remain open.

Death loses only what the player carries. Respawn priority is faction home, then a valid bed, then SpawnWorld. If no valid faction home or bed is available, use SpawnWorld. A player with stored faction supplies can re-equip from that stockpile. Voluntary return travel and how a player without supplies recovers still need decisions.

Combat blocks voluntary teleporting. Logging out while in combat causes death, with the normal carried-item loss. Combat tag duration, triggering actions and server-initiated disconnect handling still need definition.

The first join grants a basic blaster, vanilla tools and food. Equipment is granted only once, not on subsequent joins or deaths. Exact items, quantities and ammunition remain to be configured. Starting credits are possible but neither their inclusion nor amount is decided.

The first friends playtest should bring the discussed gameplay systems together in a rough, integrated version. The development slices below are incremental building and checking steps toward that target. The precise content depth for each system still needs agreement; force powers remain deferred.

## Proposed everyday loop
Establish a base, gather supplies, prepare equipment, venture out, fight or complete an activity, return with loot, and fund the next expedition. Building gives the faction a home and stockpile. Acquisition and replacement costs still need decisions.

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
| Faction home | Join a faction, claim a base and cooperate without friendly damage | Gives combat and gathering a shared purpose |
| Supplies and progression | Gather, craft and buy a useful replacement loadout | Makes loss and recovery sustainable |
| One event | Compete in KOTH or run Jabba for useful rewards | Adds a repeatable social objective |
| Mobility | Use a speeder, then test aerial transport | Builds on meaningful destinations |
| Raiding | Understand vulnerability and perform a limited explosive breach | Requires stable combat, claims and recovery rules |

This is a starting sequence for discussion. Resource planets, travel details, dungeon contention and warpoint balancing still need decisions.

## Next player decisions
- Review the travel recommendation below; it is not yet accepted.
- Define how players acquire basic replacements after their one-time starting equipment is lost.
- Define combat tagging details and the initial equipment quantities when their feature briefs are written.

## Travel recommendation — awaiting user acceptance
Use free commands and a destination menu for routine travel, with optional themed transport terminals that open the same menu. The proposed first version has:

- `/spawn`: return to SpawnWorld.
- `/travel`: select a public destination; survival always arrives at its fixed protected landing zone. Future planets use their own public arrival points, rather than teleports directly onto objectives.
- `/f home`: return to the faction home, set inside the faction's own eligible claims.
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
