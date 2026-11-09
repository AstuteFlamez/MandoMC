# Feature: legacy Boba Fett recovery
Status: legacy baseline restored for player testing; boss refinement and dungeon placement deferred.
Branch: bilal/jabba-generic-mobs

## Player experience
Fight the original Boba Fett model with its recovered idle/walk, dodge, burst, jetpack jump, flamethrower and death clips. Original voice lines and jetpack/flamethrower sounds are included. The purpose is to test the old encounter inside the actual Jabba room before changing its AI or balance.

## Recovered rules
- Original 500 health, eight armor, zero contact damage, 0.28 movement speed, 0.9 knockback resistance, fall immunity, Hutts faction and red boss bar.
- Every 100 ticks in combat, randomly choose the original ground burst, jetpack jump shot or flamethrower. Taking damage invokes the original left/right dodge. Voices retain their 150-tick, 75% timer.
- Ground burst keeps five releases at ticks 9, 13, 17, 21 and 25. Each release retains the legacy repeat=1 paired-shot setting. Jump keeps its 0.6 upward impulse, short slow-falling effect and delayed shot. Flame keeps its original nine-block projectile, repeated small damage/ignition and temporary cosmetic fire blockmask.
- EE-3 player weapon: three-round burst, three ticks between burst shots, 24-round magazine, 65-tick reload, three base damage and original distance falloff/recoil. Item catalog ID `ee3`; `/mmc give ee3`.
- Actual death rolls one EE-3 at 50%, plus three independent 50% single Standard Plasma Cell rolls. The shared catalog-backed Mythic loot pipeline replaces old op console drop commands; no duplicate vendor weapon drop.
- Manual summon: `/mm mobs spawn BobaFett`. No scheduled encounter, automatic placement or Boba AI redesign is included.

## Compatibility adaptations
Use the current EE3 weapon ID/projectile/skin schema, namespaced model/sound references and existing reload/scope samples where legacy references were missing. Omit the unavailable WeaponMechanics cosmetics extension. Prefix all legacy skill IDs with Boba_ to avoid later boss collisions. Use supported 70to100 blockmask duration syntax. The model retains original geometry, textures, keyframes and Override flags; attack clips finish instead of holding, native zombie collision is retained, and models reattach on load. Suppress random equipment/pickup, transformations and jockeys.

## Assets and delivery
Provenance: resource-pack/boba-sources.json and server/config/common/modelengine/sources.json. Generated with pinned ModelEngine R4.1.1; the pack includes the original EE-3 base/scope/sprint models, Boba rig assets, seven sound files and original Tatooine planet pixels. Existing pack assets and sound mappings are preserved. Published pack: resource-pack-26.1.2-b81c58441b03, SHA-1 677a03e713f44870ac87bf40a3160645036d15c0 (367 files, 890606 bytes).

## Verification
All 86 Java tests and 14 pack/tooling tests pass. The isolated Paper test passes 43 assertions: precise palace coordinates/yaw/pitch and safe standing space, combat allowed at arrival, real EE-3 catalog item, original health/armor/model/animations, 1,000 actual Mythic death-event samples with original drop bounds and odds, ten ground-burst/two jump projectiles, physical jump/dodge movement and flame ignition. The fixture enables native physics, removes navigation goals and restores its temporary sky platform; it does not place bosses or edit the dungeon. Startup anonymously downloaded and verified the published pack. No runtime skill/sound errors in the passing run.

## Playtest boundary
The player selects what to change after testing this baseline in the actual dungeon room. Client rendering and room-based behavior remain player checks. Do not infer approval to redesign Boba from this import.

## Shared facing follow-up (2026-11-09)
Boba now turns toward his current target for six ticks before any attack clip, then keeps native/model facing aligned through the original wind-up and launch sequence. Shot/flame cues retain their original animation offsets. Removed the old flame-tick instruction that turned Boba toward his own projectile origins. The 43-check Boba regression and shared 92-check hostile-facing probe pass. See hostile-facing.md for the shared behavior applied to all current hostile types.
