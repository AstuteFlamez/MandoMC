# Design the game from the player's perspective
Status: working design, not a finalized feature order.

## Confirmed foundation
Players use Java Minecraft and a required resource pack. They form factions, build on the main survival world, and encounter PvP outside designated safe areas. Death drops carried items, weapons and armor. KOTH and Jabba are proven activities. Offline bases may become vulnerable after warpoints are depleted. Force powers are deferred.

## Confirmed first session and recovery
New players arrive in SpawnWorld, an existing map file the user already has. They receive a short tutorial, with its presentation still undecided. Their first goal is to travel to survival and establish a base. Survival arrivals use a fixed protected landing zone.

A solo player can create a faction and claim a starter base on eligible land outside the spawn safe zone, without a credit requirement. A faction creation cooldown of around three hours was suggested tentatively; neither the cooldown nor its duration is decided. Claim capacity and other later territory rules remain open.

Death loses only what the player carries. Respawn priority is faction home, then a valid bed, then SpawnWorld. If no valid faction home or bed is available, use SpawnWorld. A player with stored faction supplies can re-equip from that stockpile. Voluntary return travel, initial equipment and how a player without supplies recovers still need decisions.

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
- Voluntary travel between SpawnWorld, the survival landing zone and faction homes: commands/menu, physical transport points, or a combination.
- Combat restrictions on travel and the result of disconnecting during combat.
- Initial equipment and whether any starter grant is restricted to first join.

## Configuration-driven systems
Use explicit schemas and stable IDs. YAML declares content and tuning; Java implements behavior, validation, lifecycle and integration.

- Items: ID, presentation/model reference, category, recipe and acquisition.
- Sabers: item reference, damage, stamina costs, block/deflect rules and supported behavior type.
- Vehicles: model reference, movement type, seats, speed/acceleration, fuel, health and supported weapons.
- Events: location, schedule, participant rules, objectives and rewards.
- Resources: location/node type, yields, regeneration and access rules.

Only add parameters when the implemented feature uses them. First prove one item/saber/vehicle and a second configuration variant; generalize the variation we actually need. Avoid inventing a scripting language or a universal effect system.

Validate bounds and cross-references at load time. Errors name the file and key. Editing a price or speed should not require Java changes; adding a new behavior may. Reload only where active-state handling is defined. Schema changes require migrations for saved data and clear compatibility notes.
