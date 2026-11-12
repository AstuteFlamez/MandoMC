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
- `resource-pack/src/assets/mandomc/textures/item/equipment/boba_flamethrower.png`: original transparent 32×32 RGBA PNG, built-in image_gen, nearest-neighbor reduction. Full prompt/provenance: `resource-pack/boba-equipment-sources.json`.
- `models/equipment/boba_jetpack_attached.json`: fourteen original rocket/nozzle cuboids and UVs, recentered at original torso height; original texture copied unchanged. Reproduce with `scripts/extract-boba-jetpack.py`.
- Item model definitions: `assets/mandomc/items/equipment/`; standalone item/hand models: `assets/mandomc/models/equipment/`. Texture paths use the vanilla-stitched `item/` directory.

## Verification
`./scripts/build.sh` with the scoped `ThrustFuelTest,BobaEquipmentSettingsTest,JabbaDropsSettingsTest` selection passes eleven Java tests. Seven focused pack/model checks pass, including exact extraction/UV reproduction, unchanged source texture bytes, actual 32×32 RGBA transparency and packed model registration. Resource-pack validation/build passes. New classes and the compatible loot extension also compile directly against the currently deployed MandoMC JAR/API.

The dedicated empty localhost Paper 26.1.2-74 runtime with MythicMobs 5.14.0 passes 131 assertions in the scoped equipment probe. It uses real native inventories/CraftPlayers, actual offhand GLIDER eligibility, real ItemDisplays, actual swept flame packets/cow collisions, native armor/team policy, actual Mythic death-event registration and an actual player full-loot death. Fuel timing is an injected deterministic clock. Input events and transform/state checks run server-side; no network client is attached. These checks do not establish client rendering, human flight feel, network movement behavior, audible mix or pack acceptance.

## Coordinated rollout handoff
Do not deploy this checkout's full JAR or full historical pack. Its bootstrap base predates other active features. Apply the small bootstrap additions to the authoritative deployed source, install the new organized resources, merge only the `boba-equipment` message section into `language/messages.yml`, and retain current plugin.yml commands/permissions while adding `mandomc.equipment: default true`. Current ItemCatalog installs definitions from `items/definitions/boba-equipment.yml`; the legacy resource copy exists solely for this base's build/probe compatibility. Only add the two equipment chances to the current Boba loot section; preserve Jawa and every other rule.

The pack staging script `scripts/build-boba-equipment-pack.py BASE.zip OUTPUT.zip` adds only eight equipment assets and refuses conflicting replacements. The staged candidate preserves all 1,157 entries of the inspected deployed pack, adding eight. It has not been published. Rebuild against whatever pack is current in the exclusive release window, and verify unchanged existing bytes again.

No shared server stop, deployment or publication has occurred. Cross-chat coordination was rejected by automatic approval review because explicit human authorization to message the boss chat was missing. One next action: obtain authorization for the boss-chat handoff and an exclusive rollout window, integrate against its authoritative bootstrap, then client-test the matching pack and flight controls.
