# Feature: Jabba generic guards
Status: published and deployed; client playtest pending
Branch: bilal/jabba-generic-mobs

## Player experience
Fight the recovered Gamorrean, Quarren and Twi'lek guards individually before tuning them and building the dungeon encounters. Gamorreans use their original weighted light/heavy/spin attacks. Quarren fire the SE-14C and Twi'leks fire the A280 with their original animations and burst timings. Their weapons are usable player items in `/mmc items` and `/mmc give`.

## Rules
Preserve the supplied legacy models, textures, keyframes, stats, voices and weapon tuning as the starting point. The player Gamorrean Axe uses native `IRON_AXE` damage, attack speed, durability and repair behavior, without the obsolete Mythic item's Sharpness/Unbreakable overrides. Mythic owns mob levels: base health 30/25/25 with +3 per level; armor 8/4/4. The scripted axe hits remain 6/10/12 damage with a ten-tick wind-up and light/heavy/spin weights 0.5/0.3/0.2. Player axe attributes do not replace these scripted attacks.

Each ranged animation emits the original six shots: pairs scheduled at ticks 10–11, 14–15 and 18–19 (the pinned scheduler can deliver these one or two ticks later). Movement is planted for the full 46-tick shooting animation, then native AI automatically resumes. Each guard independently rolls a 2.5–4.5-second cooldown; a five-tick eligibility check starts the next burst. A separate 5–35-tick spawn/load wait prevents groups spawned together sharing their first firing beat. Twi'leks randomly choose between their two clips. Shot damage comes from the original player weapon config (SE-14C base 9 with distance falloff; A280 base 3); no Tusken-specific NPC damage scaling is applied.

Retain 25% single-axe loot for Gamorreans and 20% single-weapon loot for ranged guards. Each ranged death also rolls three independent 50% chances for one Standard Plasma Cell. Mythic's death list owns the final item drop; removal/despawn does not roll loot. Drop items use the current catalog/vendor factories. No console commands or duplicate Mythic weapon drop declarations remain.

## Smallest playable version
Only `GamorreanGuard`, `QuarrenGuard`, `TwilekGuard`, their assets and weapons. Admin test commands: `/mm mobs spawn GamorreanGuard`, `/mm mobs spawn QuarrenGuard`, `/mm mobs spawn TwilekGuard`. Items: `/mmc give gamorrean_axe`, `/mmc give se14c`, `/mmc give a280`.

No natural spawns or automatic dungeon placement. Bosses, map import, doors/keys, event activation, chest loot and dungeon balancing remain later passes, as requested. Encounter levels and room distribution remain open.

## Ownership and integrations
MythicMobs owns targeting, animations/skill requests, mob stats and death lifecycle. ModelEngine owns the recovered visual models. WeaponMechanics owns the two guns and projectiles. MandoMC supplies native axe identity and validated catalog-backed death loot. The existing combat tag, saber and WorldGuard paths continue to handle attributed damage.

Compatibility changes: explicit player AI; no random zombie equipment, item pickup, jockeys or transformation; attach models on both spawn and load; retain native zombie collision with `h=false` because the original rigs contain no model hitbox. Attack clips play once instead of holding their last pose indefinitely; original keyframes and Override flags remain unchanged. Delayed hits recheck life, range and sight. Correct the legacy unsupported `damage{d=...}` shorthand to `damage{a=...}` so the written 6/10/12 values apply instead of silently falling back to 1; verified against the pinned runtime and [official damage schema](https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/damage). Preserve the old vertical lift with explicit `velocity{mode=SET;y=0.3}` (numeric `m=2` was an invalid mode and fell back to SET). Missing legacy scope/light-reload sound names map to the pack's original pistol/rifle zoom and reload samples. No geometry, item display poses or art has been redesigned.

## Parameters
| Configuration | Meaning/default | Reload |
| --- | --- | --- |
| `jabba-mobs.yml: enabled` | Enables only the death-loot adapter, true | Restart |
| `drops.<mob>.weapon`, `weapon-chance` | Catalog weapon; 0.25 axe / 0.20 blaster | Restart |
| `drops.<mob>.ammo`, `ammo-chance`, `ammo-rolls` | Catalog cell; 0.5 and 3 for ranged guards, 0 rolls for axe guard | Restart |
| Mythic `Health`, `Armor`, `LevelModifiers`, `Skills` | Original mob baseline described above | Mythic config reload; use fresh mobs |
| WeaponMechanics configs | Original SE-14C / A280 tuning | Vendor reload; fresh items |

Drop probabilities must be finite 0–1; ammo rolls must be integral 0–64; all three guard rules and referenced catalog IDs are required. New external item registrations are added only when absent, preserving existing local overrides.

## Deployment
Copy `server/config/common/mythicmobs/jabba-mobs.yml` and `jabba-skills.yml` into separate Mythic `mobs/mandomc-jabba.yml` and `skills/mandomc-jabba.yml` files. Copy the three guard blueprints into ModelEngine and the two gun configs into WeaponMechanics `weapons/mandomc`. The plugin adds missing `items/jabba.yml`, `jabba-mobs.yml` and external catalog entries on startup.

Generate through ModelEngine R4.1.1, then run `scripts/import-mob-models.py` on its generated pack. The importer validates against all tracked blueprint IDs; unknown extra models are rejected. Build/publish the combined required pack using the existing release path. Asset provenance is in `resource-pack/jabba-sources.json` and `server/config/common/modelengine/sources.json`; the supplied legacy files are untouched.

## Verification
Plugin build currently passes 79 tests, including independent legacy loot rolls, exact 0/1 chances and rejection of invalid settings. `scripts/build-jabba-probe.sh` builds the temporary live fixture for native axe equivalence, vendor gun generation, attached models/collision, level scaling, loot rolls, real damage/combat attribution and burst timing. All 91 live assertions pass, including original level/base-stat behavior, all three attached models and native collision, native iron-axe attributes, vendor gun generation, 3,000 guard death-roll samples, actual ranged damage/combat tags and safe-zone denial, delayed six-damage slash and range cancellation, all three six-shot ranged clips, planted/restored movement and dead-target cancellation. Timing checks allow the observed one-to-two-tick vendor scheduler tolerance. All 14 pack/tooling checks pass. The deterministic pack has 294 files and SHA-1 `01c65419d774e7326b36bbeaecb3189bfc0b7dee`; all previous mob assets and 218 of 220 previous pack files are byte-identical, with only additive font/sound registrations changed. The isolated pack branch is `bilal/jabba-pack` at `b04bf65559e32ab038fb15963216e255fb8337f4`. The user explicitly approved publication and ongoing resource-pack updates. Published `resource-pack-26.1.2-b04bf65559e3`; anonymous download matches the tested checksum and server startup selects this release. Rendered appearance, sound levels, gun poses and combat feel require a player check.

## Pause note
Next action: reconnect for the published pack and client-check the three guards before adjusting balance or starting bosses.

## Ranged combat variation (2026-11-09)
Quarren and Twi'lek guards independently roll their attack cooldown on every burst rather than sharing the old 50-tick firing timer. Burst keyframes and six-shot timing remain intact. Mythic's named `JabbaAiming` stun owns the 46-tick planted pose and automatically restores AI even when a target disappears; combat movement cannot run while that aura is active.

Native navigation approaches to within 14 blocks at 1.15x walking speed, backs away from players closer than seven blocks, and chooses nearby surface positions 3–5 blocks away while fighting a target at 7–18 blocks. Repositioning is limited to once per 1.25 seconds. Losing combat clears the tactical path and leaves ordinary wandering. These are navigation destinations, not teleports or terrain changes. Normal walk playback is preserved. No new pack or Java deployment is needed for the Mythic content update. Use fresh guards after reload/restart for new AI goals.

Live cadence/navigation verification uses `scripts/build-jabba-ai-probe.sh`; remove the temporary probe after testing. All 79 Java tests and 71 live AI assertions pass. Eight simultaneously spawned guards showed different first-burst times and varied repeated intervals within the 2.5–4.5-second cooldown plus five-tick polling tolerance. Both types navigated between attacks, stayed planted through the firing pose, restored AI automatically, resumed repositioning and exited combat on target loss. The fixture maintains its non-player targets to exercise hostile-only guard targeting without involving player inventories. Fixture blocks/entities were restored and the temporary plugin removed. Client combat feel remains to be tested.

## Guard damage correction (2026-11-09)
User reported Quarren instant death and Twilek seven-heart bursts in full netherite. Both guards now use dedicated NPC weapon profiles (`QuarrenSE14C`, `TwilekA280`) with base damage 1 and armor wear 1. All three ranged attack clips emit three single shots, aim at the torso and use spread 2. Original player weapon IDs, stats, catalog entries and death drops remain unchanged. Existing AI, cooldown variation, model clips and movement recovery are preserved.

Deployment: install `se14c_guard.yml` and `a280_guard.yml` in WeaponMechanics `weapons/mandomc`, then apply the revised Jabba skill file. Wait for WeaponMechanics reload to finish re-registering its Mythic hook before reloading Mythic. Runtime/source files were compared and matched. Current global damage multiplier cap is 300%, so a three-shot base-1 burst has a maximum configured budget of nine health, below a full 20-health player even before armor. This is configuration validation, not a new live armor-hit probe. Next action: playtest freshly summoned guards in netherite.

### Guard damage retune (2026-11-09)
The base-1 nerf proved too weak in the user's netherite playtest. Both NPC-only profiles are now base 3.5, retaining three single torso-aimed shots, spread 2, armor wear 1 and the existing varied cooldown/movement. Nominal unenchanted netherite body-shot budget is 3 × 3.5 × (1 − 20 × 0.03) = 4.2 health / 2.1 hearts if all three shots connect; this is a tuning target from the current additive damage modifiers, not a new live-hit measurement. WeaponMechanics loaded all 32 weapon definitions and its Mythic hook; Mythic was reloaded after that hook completed. Source/runtime files match. Player guns are unchanged. Next action: client-check a full burst in netherite and refine toward this middle ground.

### Guard damage balance reversal (2026-11-09)
User clarified Quarren felt harder-hitting and requested reversing the damage balance. Lowered only the Quarren NPC profile from 3.5 to 2.5 base damage and raised only the Twilek NPC profile from 3.5 to 4.5. Three-shot body aiming, cadence, movement, armor wear and player guns are unchanged. Updated source/runtime files match; WeaponMechanics accepted all 32 weapons and re-established its Mythic hook before the dependent Mythic reload. No restart or source JAR replacement. Next action: client-check the reversed guard damage balance in the same netherite gear.
