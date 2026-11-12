# Proposed build order
Status: revised 2026-11-09; current acquisition is crafting-first for all equipment; NPC equipment shops and resource-selling shops are removed from the roadmap; clan/FactionsUUID work remains deferred. Remaining ordering is an advisor recommendation. Gameplay rules in [player-experience.md](player-experience.md) remain authoritative; this sequence does not add new rules.

The first friends playtest brings all currently in-scope systems together. Each row is an internal checkpoint that can be split into small commits and paused with a working build. Begin with one content example per system; expand variants after that example works.

| Order | Player outcome | Smallest content and proof |
| --- | --- | --- |
| 1. Arrival and protection | Join SpawnWorld with the pack, reach a safe survival landing and build outside it | Restore SpawnWorld; load one survival world; verify protected boundaries with a non-op account. Use the [first brief](features/arrival-and-protection.md). |
| 2. Combat and loss | Use a blaster and shield-based saber, lose carried gear on death | One blaster, one armor set and one saber with block/reflection/throw/overheat. Start with one working E-11: firing, ammo/reloading, sounds and safe-zone enforcement. Then armor/saber, survival restrictions, repair, loss and starter kit; sabers accepted as complete November 10, with throws requiring/spending 51 of 100 charge. Preserve player kill attribution; clan relations and pairwise warpoints await the deferred clan integration. Raids stay disabled. |
| 3. KOTH | Contest a shared objective as a group or solo player | One hill, continuous capture, configured credits/component reward and death/re-entry. Decide reward delivery in its brief. Event PvP pairwise scoring waits for the deferred clan integration. |
| 4. Jabba | Enter the scheduled shared dungeon, fight mobs/rivals and take open loot | Recover the existing map and mob configs; prove one room/door/boss/loot path, then restore the agreed room sequence. Validate cleanup, repeat runs and unlimited re-entry while active. |
| 5. Vehicles | Craft transport, pilot it, carry passengers and risk losing it | Speeder first, one aircraft second. Test placer-only pilot, open passenger access, destruction, packing, restart and no duplicate item/entity copies. |
| 6. Crafting and progression | Recover from losing everything through ordinary play | Basic armor recipe and advanced-equipment recipe. Implement ordinary survival acquisition and recipes for all equipment, including vehicles, blasters and ammunition. Introduce a second configured item variant to prove parameters work. Event components become earnable in the KOTH/Jabba stages; staff-issued components are development fixtures only. |
| 7. Remaining equipment recipes | Craft blasters and ammunition through approved progression | Design and approve remaining blaster/ammo recipes, then verify genuine vendor identities and costs. Finished lightsaber recipes remain explicitly deferred. |
| Later. Raids | Use earned rivalry advantage to breach and loot one rival's base | One approved explosive and limited block set. Test offline defenders, score-drop continuation, preserved ownership and third-party ground-loot-only access. |

At each stage, test the new system against the protections and persisted state already present. New damage paths must pass safe-zone and claim checks before the stage is complete. Add vendors only as a stage needs them, recording exact versions and compatibility evidence.

## Why this sequence
A working arrival makes combat testable now. Clan/base integration is explicitly deferred. Combat establishes loss and kill attribution. KOTH is the user-selected next milestone and exercises the event lifecycle with a smaller content dependency than Jabba. Vehicles then have useful routes. Crafting/progression follows vehicles; development checks may use staff-issued fixtures until their approved crafting routes are complete. Raids come last because they combine combat, claim permissions, loot and persistent rivalry state.

This order does not defer checking whether a difficult integration is possible. Before building a feature around an uncertain hook—especially claim overrides, projectile reflection or aircraft control—run a small disposable compatibility experiment. Keep that experiment separate from finished gameplay.

## Content boundary
The proposed first integrated build has a small working example of every row, including both events, speeders and transport aircraft. The exact Jabba room count and equipment roster remain open. Resource planets, Force powers, aerial combat and themed replacement swords/bows are deferred.

All equipment should have crafting recipes, including blasters, ammunition and vehicles. No NPC/server equipment shops or resource-selling shop milestone is planned. Existing starter grants and event loot remain unless separately changed. Credits, balances and payment commands remain; a future player-to-player auction house replaces shops as the trading system. Finished lightsaber recipes remain deferred, with their ingredients/costs undecided. Stable item identities keep acquisition independent of combat/ownership.

## Pause rule
Finish each work session with a buildable checkpoint, the relevant verification result and one next action in [status.md](status.md). An unfinished feature can stay on its branch; a pause does not require inventing another project direction.

## Deferred clan milestone
The accepted player-facing name is **Clans**. Creation/joining, claims, clan homes, clan respawn priority and FactionsUUID customization wait until the user returns to them. Do not build a parallel custom membership system. Group-specific KOTH ownership, clan damage rules, pairwise warpoints and raids cannot be considered complete before that integration.

Desert Robes and the admin browser are implemented and the corrected icons confirmed. The Vader throw was accepted on 2026-11-08. Current slice: [combat basics](features/combat-basics.md) — restrictions, native repairs, 15-second mob/PvP tag and decimal sidebar, logout death/drops, and a first-join kit including 64 E-11 ammo. WeaponMechanics owns ammo consumption.

Game-mode reset fixed: Multiverse enforcement is disabled; live login/world-change event checks preserve Creative and Spectator.

Current content: [levelled sandy-biome encounters](features/sandy-biome-mobs.md) are implemented and server-verified: ranged/melee Tuskens, cycler rifle, separate wildlife groups, random levels 1–5, shared spawn budgets and vanilla natural-spawn replacement. Next playtest models, density and combat; then define drops (cycler rifle, bantha hide, species materials). Sabers are accepted as complete; the November 10 throw tuning requires/spends 51 of 100 charge. Clan/FactionsUUID work remains deferred.

## Late pre-release animation polish
User-requested, deferred until the end of the todo list: fix skirt animations on **both ranged and melee Tuskens** before release. Do not change those keyframes during the current aiming, shot-timing and locomotion fixes.

## Current KOTH checkpoint
The user-selected next slice is now implemented: legacy event rotation/menu/admin lifecycle, three-minute solo KOTH on recovered Hoth, and a warp menu limited to Black Spire Outpost, Earth and Hoth. Rewards are explicitly deferred. Unit and live server checks pass; client playtest comes next. Jabba remains the next feature after this check; clan ownership/warpoints remain deferred.

## Late pre-release KOTH build
User-requested later task: add a small build/platform for KOTH inside its Hoth canyon. Keep the event name KOTH. Design placement and shape when this task is reached; preserve the protected map and validate the capture zone afterward.

## Appended work queue (2026-11-09)
Continue the current [ordered progression queue](features/progression-crafting.md) before later work. New requests append; corrections update their original item.

- [ ] Finish and deploy the approved progression/recipe slice after verification and client review.
- [ ] Design, approve and implement remaining blaster recipes and concrete ammunition recipes. Do not invent ingredient costs.
- [ ] Plan and implement a player-to-player auction house, replacing shops. Fees, listing limits/duration, rules, implementation/plugin and currency details remain undecided. Preserve existing credits/ranks/payments.
- [ ] Apply the subsequently requested vanilla desert-themed natural-spawn blacklist while preserving custom encounters; see the progression queue for scope and checks.

Final-world ore-generation exclusions and finished-lightsaber recipe deferral remain durable unchecked requirements in the progression brief. Current maps must not be regenerated or stripped to satisfy the future generation requirement.
