# Proposed build order
Status: revised 2026-11-08; user deferred clan/FactionsUUID work. Remaining ordering is an advisor recommendation. Gameplay rules in [player-experience.md](player-experience.md) remain authoritative; this sequence does not add new rules.

The first friends playtest brings all currently in-scope systems together. Each row is an internal checkpoint that can be split into small commits and paused with a working build. Begin with one content example per system; expand variants after that example works.

| Order | Player outcome | Smallest content and proof |
| --- | --- | --- |
| 1. Arrival and protection | Join SpawnWorld with the pack, reach a safe survival landing and build outside it | Restore SpawnWorld; load one survival world; verify protected boundaries with a non-op account. Use the [first brief](features/arrival-and-protection.md). |
| 2. Combat and loss | Use a blaster and shield-based saber, lose carried gear on death | One blaster, one armor set and one saber with block/reflection/throw/overheat. Start with one working E-11: firing, ammo/reloading, sounds and safe-zone enforcement. Then armor/saber, enchantments, loss and starter kit. Preserve player kill attribution; clan relations and pairwise warpoints await the deferred clan integration. Raids stay disabled. |
| 3. Supplies and progression | Recover from losing everything through ordinary play | One resource sale, basic blaster/ammo shop, basic armor recipe and advanced-equipment recipe. Introduce a second configured item variant to prove parameters work. Event components become earnable in the KOTH/Jabba stages; staff-issued components are development fixtures only. |
| 4. KOTH | Contest a shared objective as a group or solo player | One hill, continuous capture, configured credits/component reward and death/re-entry. Decide reward delivery in its brief. Event PvP updates pairwise scores. |
| 5. Jabba | Enter the scheduled shared dungeon, fight mobs/rivals and take open loot | Recover the existing map and mob configs; prove one room/door/boss/loot path, then restore the agreed room sequence. Validate cleanup, repeat runs and unlimited re-entry while active. |
| 6. Vehicles | Buy transport, pilot it, carry passengers and risk losing it | Speeder first, one aircraft second. Test placer-only pilot, open passenger access, destruction, packing, restart and no duplicate item/entity copies. |
| Later. Raids | Use earned rivalry advantage to breach and loot one rival's base | One approved explosive and limited block set. Test offline defenders, score-drop continuation, preserved ownership and third-party ground-loot-only access. |

At each stage, test the new system against the protections and persisted state already present. New damage paths must pass safe-zone and claim checks before the stage is complete. Add vendors only as a stage needs them, recording exact versions and compatibility evidence.

## Why this sequence
A working arrival makes combat testable now. Clan/base integration is explicitly deferred. Combat establishes loss and kill attribution; acquisition makes that loss recoverable. KOTH exercises the event lifecycle with a smaller content dependency than Jabba. Vehicles then have useful routes, and raids come last because they combine combat, claim permissions, loot and persistent rivalry state.

This order does not defer checking whether a difficult integration is possible. Before building a feature around an uncertain hook—especially claim overrides, projectile reflection or aircraft control—run a small disposable compatibility experiment. Keep that experiment separate from finished gameplay.

## Content boundary
The proposed first integrated build has a small working example of every row, including both events, speeders and transport aircraft. The exact Jabba room count and equipment roster remain open. Resource planets, Force powers, aerial combat and themed replacement swords/bows are deferred.

Initial acquisition stays mixed: basic blasters/ammo and vehicles bought with credits; basic armor, sabers and advanced equipment crafted. Moving all equipment toward crafting is a later progression change. Stable item IDs and independent acquisition definitions let that transition happen without rewriting combat or vehicle ownership.

## Pause rule
Finish each work session with a buildable checkpoint, the relevant verification result and one next action in [status.md](status.md). An unfinished feature can stay on its branch; a pause does not require inventing another project direction.

## Deferred clan milestone
The accepted player-facing name is **Clans**. Creation/joining, claims, clan homes, clan respawn priority and FactionsUUID customization wait until the user returns to them. Do not build a parallel custom membership system. Group-specific KOTH ownership, clan damage rules, pairwise warpoints and raids cannot be considered complete before that integration.

Immediate recommended next slice: one basic armor set, followed by the first shield-based saber (block/reflection/throw/overheat). The E-11 is implemented; finish its remaining client ammo-consumption and combat-tag checks as part of combat validation. Clan/FactionsUUID work remains deferred.
