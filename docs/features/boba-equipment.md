# Feature: Boba Fett equipment drops
Status: implementation and isolated Paper/Mythic verification; coordinated deployment and client playtest pending.
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

## Coordinated rollout handoff
READY and queued after the recipe-source owner. No shared stop, deployment or publication has occurred. The boss reads the READY handoff directly; no cross-chat reply is required. Await an assigned gear window.

Do not deploy this checkout's full Maven JAR or historical full pack. `scripts/build-boba-equipment-overlay.py` builds a scoped release from the exact live JAR with an explicit expected SHA-256. It compiles only the equipment family and the compatible Jabba loot extension against that live API. A JDK 25 Class-File transform adds exactly three calls to the existing bootstrap: install the item definition before catalog reload, initialize before Jabba settings load, and close at plugin disable. Original public APIs, the existing salvage record, recipe release and every unowned entry are retained. The transform is packaging tooling only; no runtime dependency is added. The helper installs only missing equipment files/chance keys, preserving operator settings and all existing loot.

The staged release is rebased on the recipe JAR `2fac77b269be20e90fa1f4caad7e03a8cd4e5edcc7d9219b9286fde4003bb152`, following redstone JAR `2fb70b25b406df3d619b32d03b19f38426e1afe971d6a7ba9c65679f9d2241f7`. Redstone's required external agent and launcher must remain installed: retain `-javaagent:agents/MandoMC-RedstoneAgent.jar`. This feature never replaces them. Verify the live JAR/pack/agent/launcher again in the assigned window and rebuild if any baseline changed.

The pack staging script `scripts/build-boba-equipment-pack.py BASE.zip OUTPUT.zip` adds only eight equipment assets and refuses conflicting replacements. The current candidate preserves all 1,157 entries of deployed pack `2c075adc803b28496eb6a7d5f8164a6c7cc85c87c0cb7ed83ceb4139459e8f54`, adding eight. Target versioned publication is `resource-pack-26.1.2-8f339d33a829`; candidate SHA-1 `cf998f55231cda3079db14118b912b1d6e468c73`. It has not been published.

Fast fresh release checks pass: JDK class verification, exact insertion-anchor counts, original public API retention, unchanged unowned bytes, recipe/redstone preservation, all existing loot values/Jawa salvage retained, and parsing of settings/items/messages/permission/new rolls. The original 131 isolated native assertions cover the gameplay implementation. The new exact-JAR bootstrap helper still needs its narrow startup smoke check in the assigned gear window; no new full matrix has been run. Connected-client pack appearance, attachment alignment and flight feel remain unverified.

Exact local artifact paths, hashes and owned entries are recorded in `.local/boba-release/READY.json` and the adjacent candidate reports. One next action: receive the gear window after the recipe owner's release, recheck CAS baselines, run the narrow bootstrap smoke and complete the coordinated gear/pack rollout.
