# Feature: Boba Fett equipment drops
Status: LIVE trial deployment at 2026-11-12 14:06:31 America/Chicago; client flight/rendering validation pending.
Branch: bilal/boba-equipment

## Player experience
Boba independently drops his reusable jetpack and wrist flamethrower alongside his existing loot. Right-click a held jetpack or open `/mmc equipment` to equip/remove it. The actual owned item occupies the native offhand; chest armor, enchantments and durability are unchanged. Empty the offhand before equipping and leave an inventory slot free before removing. The menu never stores a second copy.

Double-tap sneak within seven ticks to start/stop thrust. Ground takeoff gives a 0.6 upward launch for the first six ticks, then native horizontal gliding follows the look direction with smoothed velocity steering. Double-sneak avoids changing jump/creative-flight permissions or stealing weapon right-click, reload/drop or hand-swap controls. Creative/spectator flight, mounts, swimming and levitation reject powered activation. Midair resumes enter thrust directly and cannot mint repeat upward launch impulses. Landing stops thrust. Exhaust originates from both extracted nozzles. Exhaustion/voluntary shutdown retains native unpowered glide until landing while the pack remains worn.

Hold the flamethrower in the main hand and right-click for a fixed burst. Both items can operate together. These are independent catalog items and ordinary native inventory ownership; no unlock, virtual item store or combined chestplate exists.

## Required costs and lifecycle
- One exact `COAL` from slots 0–8 buys one real second of powered flight. Multiple hotbar stacks count; charcoal and slots 9 onward do not. First payment precedes takeoff. Purchased nanoseconds are retained in player PDC across toggles, equipment removal, logout and restarts. Paused time is not spent or rounded up.
- Fuel display includes purchased time plus current hotbar coal. At five seconds or less, action bar/chat and a bell warn once; refueling above five seconds rearms it. Empty fuel immediately stops thrust and displays `Jetpack fuel depleted.`
- One exact `FIRE_CHARGE` from normal storage/hotbar (0–35) pays for one accepted burst, including Creative. Cooldown/denied/empty-ammo activations spend nothing. Cooldown survives logout in player PDC.
- Death uses normal native full-loot/keep-inventory policy. Neither equipment service edits death drops. Death, logout, teleport/world change, removal and shutdown remove owned attachments and active bursts/thrust. Native offhand glider data belongs to the jetpack item.

## Ownership and integrations
`ItemCatalog` owns `boba_jetpack` and `boba_flamethrower` persistent identities. `JabbaDrops` remains the single Mythic death-roll writer; equipment rolls append to EE-3/plasma rolls and preserve newer Jawa salvage contracts. Original Boba model, animations, flame skills, sounds and combat are unchanged.

The server's exact 26.1.2 `LivingEntity.canGlideUsing` checks `GLIDER` plus matching `EQUIPPABLE` in every native equipment slot. The jetpack receives offhand components on equip. This enables glide; it does not provide the visible back geometry. A separate nonpersistent `ItemDisplay` uses the extracted model, a torso-center transform and `getBodyYaw()`, with neutral entity yaw/pitch to avoid applying head rotation twice. Standing, sneaking and gliding transforms include armor clearance. Client pose transitions/appearance remain playtest checks.

Flame packets reuse Boba's FLAME/LARGE_SMOKE pairs, sound, 10-block/s travel and nine-block reach. The player adaptation emits ten packets over twenty ticks. Swept native block collision stops each packet, including thin solid obstacles; target contact has a second unobstructed trace. Zone policy is checked along the packet and target path. Real attributed native damage preserves armor, immunity, world PvP, scoreboard friendly fire and protection cancellation. Burn starts only after accepted positive damage and an uncancelled combustion event. Owned environmental burn ticks become attributed native fire damage, retaining native policy and attacker credit. No terrain mutation or ignition is implemented.

## Configurable defaults
Settings: `equipment/boba.yml`; organized runtime overrides: `server/config/common/mandomc/equipment/boba.yml`.

- Drop chances: jetpack 0.10; flamethrower 0.15, under `drops.BobaFett.equipment` in `mobs/drops/jabba.yml`. Existing 0.50 EE-3 and three independent 0.50 plasma-cell rolls remain.
- Jetpack target speed 0.9 blocks/tick (18 blocks/s), steering interpolation 0.28/tick, launch velocity 0.6, six launch ticks, seven-tick double-sneak window. Coal cost/warning threshold are fixed required rules.
- Flame range nine blocks, burst twenty ticks, emission every two ticks, projectile speed 0.5 blocks/tick, contact radius 0.5 blocks, one base health point per target per ten ticks, 100 burn ticks, sixty-tick/three-second cooldown. Native armor/immunity may reduce actual health loss. Packet travel can continue up to eighteen ticks after the last emission.

## Assets
- `resource-pack/src/assets/mandomc/textures/item/equipment/boba_flamethrower.png`: original transparent 32×32 RGBA PNG, original built-in image_gen artwork, refined directly on an editable native 32×32 grid with 15 solid colors plus transparency, a clear fuel tank/feed and a vented burner head. Reproduce with `scripts/render-boba-flamethrower.py`; source: `resource-pack/artwork/boba_flamethrower.pixels.json`. Full prompt/provenance: `resource-pack/boba-equipment-sources.json`.
- `models/equipment/boba_jetpack_attached.json`: fourteen original rocket/nozzle cuboids and UVs, recentered at original torso height; original texture copied unchanged. Reproduce with `scripts/extract-boba-jetpack.py`.
- Item model definitions: `assets/mandomc/items/equipment/`; standalone item/hand models: `assets/mandomc/models/equipment/`. Texture paths use the vanilla-stitched `item/` directory.

## Verification
`./scripts/build.sh` with the scoped `ThrustFuelTest,BobaEquipmentSettingsTest,JabbaDropsSettingsTest` selection passes eleven Java tests. Eight focused pack/model checks pass, including exact extraction/UV reproduction, unchanged source texture bytes, actual 32×32 RGBA transparency and packed model registration. Resource-pack validation/build passes. New classes and the compatible loot extension also compile directly against the currently deployed MandoMC JAR/API.

The dedicated empty localhost Paper 26.1.2-74 runtime with MythicMobs 5.14.0 passes 131 assertions in the scoped equipment probe. It uses real native inventories/CraftPlayers, actual offhand GLIDER eligibility, real ItemDisplays, actual swept flame packets/cow collisions, native armor/team policy, actual Mythic death-event registration and an actual player full-loot death. Fuel timing is an injected deterministic clock. Input events and transform/state checks run server-side; no network client is attached. These checks do not establish client rendering, human flight feel, network movement behavior, audible mix or pack acceptance.

## Live trial deployment
Deployed after explicit coordinator GO on 2026-11-12, normal startup at 14:06:31 America/Chicago. One production restart; full stopped-runtime backup retained privately. Fresh event state was Jabba active / next None and was restored. The temporary read-only audit plugin was removed without another restart. Gear window released.

Live JAR SHA-256: `d0c5f8f9d1c75a71d567e6c8fc0c94b1967f4477b4576803ea347d9b7e5924a3`, an additive exact-JAR overlay of recipe baseline `2fac77b269be20e90fa1f4caad7e03a8cd4e5edcc7d9219b9286fde4003bb152`. Original public APIs, recipe entries, salvage record, redstone boundary and all unowned JAR bytes are retained. The mandatory external redstone agent and launcher are unchanged, including `-javaagent:agents/MandoMC-RedstoneAgent.jar`. Never deploy this historical checkout's full Maven JAR over it.

Published pack: `resource-pack-26.1.2-286525e138bc`; SHA-1 `918a1c7414a452526722296f473ff495afec8524`, SHA-256 `286525e138bc02fe10fc01c70bcf0a4c822f9cc0879b713c0ba380cfaa79e9ab`, 1,165 files. Anonymous downloads match exactly. The merged pack retains current configured 4a7e assets/textures, restores the exact 68 missing active-feature files from prior 2c075, adds four missing Jawa sound events, prepends the missing `$` credit font provider, restores the namespaced trophy font, and adds only eight gear assets. All current font providers/sound events remain unchanged; only the two shared JSON files receive additive merges. Per-file restoration hashes and preservation checks are in `resource-pack/boba-equipment-live-preservation.json`. Reproduce with `scripts/build-boba-preserved-pack.py` and the current owner's explicit upstream model dependency allowlist. Never publish this checkout's historical full source pack directly.

The exact release passed a disposable full-current-runtime startup with the unchanged native agent/launcher and full catalog: 25 assertions exercised registered gear services, real fuel/ammo costs, armor retention and native flame damage. At production startup +102 ticks, a read-only audit passed 36 checks: healthy bootstrap, real registration/identity, all existing loot values, seven gray NPCs, nine decor anchors, both boards, all 2,115 frozen originals, native agent, effective pack URL/hash and restored Jabba/next None. No production fake actors or gameplay tests were run.

Actual connected-client flight displacement/landing, visual back attachment in standing/sneaking/gliding poses, sounds and pack acceptance remain unverified. The server flag/velocity/display-transform tests do not replace this manual client check. Trial: reconnect for the pack; use Survival/Adventure; equip the jetpack in offhand via right-click or `/mmc equipment`; retain your chestplate; put exact coal in hotbar; double-tap sneak. Hold the flamethrower in main hand with Fire Charges in storage/hotbar and right-click in an allowed combat area. Operator catalog IDs are `boba_jetpack` and `boba_flamethrower`.

One next action: user/client trials of powered flight, shutdown/depletion landing, F5 back-model alignment and the approved flame sprite/burst. Do not call client validation complete before that feedback.
