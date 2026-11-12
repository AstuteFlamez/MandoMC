# Feature: Mob base stats
Status: deployed and verified
Branch: bilal/remove-mob-levels

## Player experience
Tusken patrols, Banthas, Dewbacks, Massiffs and Jabba guards have plain names and fixed configured stats. Encounter difficulty no longer rolls randomly or increases with palace room number. Boss mechanics, attacks, drop chances, rarity, herd age/ownership and encounter group sizes remain owned by their existing systems.

## Rules
The pinned Mythic API uses level one internally as the base-stat sentinel. This is hidden from players. Existing caller signatures retain their legacy numeric fields for binary compatibility with the separately deployed animal and dungeon code; the encounter planner stops rolling levels, the spawn boundary ignores supplied levels for these eight types, and the native Mythic spawn event sets their level to one. The rifle adapter applies its configured base damage without consulting mob level. Old level configuration keys are ignored and removed from shipped/current configs.

Configured base health remains Tusken sniper/melee 20, Bantha 50, Dewback 40, Massiff 50, Gamorrean 30 and Quarren/Twilek 25. Native Tusken damage remains 3/4; the sniper CyclerRifle remains base damage 6 before normal weapon/protection processing. Guards retain scripted attacks and zero native melee damage. Massiff retains its fixed scripted bite. Bossk, Boba and Rancor are outside normalization.

## Existing entities and restart
At startup and after entities load, the adapter normalizes only affected types with a non-base Mythic level and strips the known rendered level labels. Mythic's supported setter recalculates its stat registry and persists level one. Final health is min(previous current health, new maximum): an injured mob below the new cap keeps exactly its current health, and health above the new maximum is clamped. No entity is respawned or healed. UUIDs, PDC, inventory, equipment and ownership stay on the same entity. The wrapper preserves native age/age lock, taming owner/tamed state, AI state, combat target and non-level attribute bases (including current movement and model scale) while Mythic reapplies options. Unloaded entities migrate when their chunks subsequently load; no region/entity files are rewritten offline.

## Ownership and integrations
MandoMC owns encounters, group budgets and fixed NPC rifle damage; Mythic owns definitions, persisted identity and stats; ModelEngine retains models, and the existing animal controllers retain age, taming and breeding. Only level labels and LevelModifiers are removed from the latest installed sandy/Jabba definitions. The deployment transform is applied to the latest files, preserving a concurrent change from PIG to HORSE or Tameable options. No resource-pack changes are needed.

## Verification
`./scripts/build.sh --offline` passes 107 tests. The changed encounter tests retain patrol/wildlife counts and cap coverage and now assert base levels and fixed rifle damage. Initial disposable native Paper checks pass 34 assertions. A genuine save/restart run passes 41 assertions: old level-five entities restore with the same UUIDs before migration, injured HP stays seven with max health 20/damage four, baby age/age lock and horse taming owner survive, AI/target/movement/scale remain intact, over-cap Bantha health clamps to 50, and Bossk's 111/800 health stays untouched. All eight fresh definitions use configured health/plain names; direct native Mythic spawning at requested level five is coerced to base stats. The probe is restricted to a disposable levels_test world on loopback port 25578.

## Pause note
Deployed with one ordinary restart on 2026-11-12, ready at 00:00:56 America/Chicago. JAR SHA-256 `bfd76d8a7194447b6907b1dd3fa7f2b1d9f76782f219635e59f8e594b6665c4c`. Four existing classes and bundled spawn config changed, one listener added, 458 unrelated entries remain byte-identical to the immediate deployed baseline. Existing entity regions, Mythic data, JAR and the three affected configs were backed up before startup. The read-only startup audit saw zero affected loaded mobs and zero failures before event restoration. Jabba's Palace / next None was restored from the fresh pre-stop snapshot. All three ensuing real event guards have level-one metadata, plain Quarren Guard names and native max-health base 25. One guard retains an unrelated vanilla `leader_zombie_bonus` modifier; removing vanilla modifiers is outside the level-system change. The audit plugin self-disabled and its own hash-verified JAR was removed, without a second restart. No fake actors or test entities were created on the shared runtime. No startup errors/severe messages appeared. The restart window is released.

Next action: client-check plain mob names during normal play; integrate this source slice before later full builds.
