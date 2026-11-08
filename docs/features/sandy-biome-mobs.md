# Feature: Levelled sandy-biome encounters
Status: implementation; live integration verification in progress
Branch: bilal/tusken-mobs

## Player experience
SurvivalWorld deserts, badlands and eroded badlands have Tusken patrols and wildlife instead of naturally spawning vanilla mobs. Beaches and wooded badlands keep their existing spawning. Each encounter rolls a random leader level from 1–5; companions stay within one level, clamped to that range. Difficulty does not depend on the player approaching or distance from spawn.

## Accepted rules
- Tusken patrols: 2–4 raiders, including both ranged and melee variants; 0–2 banthas and 0–2 massiffs, with a hard total of six. Companion selection shares the remaining budget rather than rolling eight mobs independently.
- Separate wildlife encounters: 1–3 banthas, OR 1–3 dewbacks, OR 1–3 massiffs. Species never mix in these wildlife groups. Banthas/dewbacks wander; Tuskens/massiffs are hostile to players.
- Levels increase health and attack damage, not walking speed or firing rate. Names show levels. MythicMobs owns the authoritative level; no parallel persisted level state.
- Ranged Tuskens use the actual WeaponMechanics CyclerRifle with one aimed shot per four seconds, line of sight and a 32-block attack range. Mob shots start at 6 damage at level 1 and reach 8 at level 5. The player rifle retains its legacy damage of 15; these are initial tuning values for playtesting.
- Suppress vanilla NATURAL, CHUNK_GEN, PATROL and REINFORCEMENTS spawning only in the selected SurvivalWorld biomes. Preserve breeding, eggs, spawners and custom spawns. Do not delete existing vanilla mobs or player animals.
- Spawn only on clear, supported surface ground in already loaded chunks, at least 32 blocks from every player. No SpawnWorld, protected landing or other WorldGuard-safe locations. Both hostile targeting and damage respect protection; combat tags keep their existing 15-second behavior.
- Shared cap: at most 12 of these mobs within 96 horizontal blocks of a new member, and 96 in loaded SurvivalWorld chunks. Reserve capacity for the whole group. Overlapping players share the same population, rather than each receiving a separate allowance.
- A bounded attempt runs every 20 seconds, with 25% chance per eligible player and at most eight candidate centers. Patrol, bantha, dewback and massiff encounters are equally likely initially. Each natural member carries a persisted encounter ID; cleanup removes abandoned natural encounters without deaths or loot. Loaded mobs count again after restart or chunk reload.
- No new loot, XP rewards, taming or riding. Vanilla equipment and loot drops are disabled. Drops are the next design pass after spawning/combat: cycler rifle, bantha hide and species-specific materials; rates, quantities and uses remain undecided.

## Ownership and integrations
MythicMobs owns definitions, AI, health, melee damage, levels and mob lifecycle. ModelEngine owns visual models/animations. WeaponMechanics owns shots and the player rifle item. MandoMC owns encounter composition, natural-spawn replacement, population budgets, protected-zone policy and an adapter for level-scaled NPC rifle damage. WorldGuard remains the protection authority.

The legacy ranged mob targeted villagers and fired a DL-18 despite equipping a cycler; both are corrected. Player disguises are replaced by the existing ranged/melee ModelEngine blueprints. Their missing hitboxes are supplied without changing visible artwork. The old pack refers to absent cycler item files, so the new item model reuses the rifle geometry and texture already present in the ranged Tusken model. Client poses still require a visual check. Legacy console-command drops are removed.

## Configuration and deployment
`mobs.yml` holds levels, roster counts, population limits, biome/ground allowlists, placement limits and mob cycler damage. Changes require a restart. Bounds fail startup explicitly. Mythic content is in `server/config/common/mythicmobs`; the five blueprints and provenance are in `server/config/common/modelengine`. Keep these definitions installed before enabling the MandoMC mob feature.

Copy mob definitions to `MythicMobs/mobs/mandomc-sandy.yml`, skills to `MythicMobs/skills/mandomc-sandy.yml`, blueprints to ModelEngine, and the cycler config to WeaponMechanics' `weapons/mandomc/`. Add `cycler_rifle` to the existing MandoMC external item catalog without replacing custom entries. The item browser and `/mmc give cycler_rifle` use the vendor weapon identity.

Regenerate through the pinned ModelEngine, then import its generated resource pack with `scripts/import-mob-models.py PATH`. The importer accepts only the five models plus ModelEngine's internal fire model. It imports namespaced assets and the required atlas directory, preserving vanilla shaders. Build and publish the required MandoMC pack as usual; the upstream JustTimm saber pack remains separate.

## Verification
Five unit tests cover 10,000 patrol rolls, all level bands, no mixed wildlife, group capacity reservations, configuration bounds and mob rifle scaling. Existing Java and resource-pack tests pass. Live checks and client playtest pending.

## Pause note
Next: complete the live integration probe and publish/deploy the combined pack; then playtest density and combat before designing drops.
