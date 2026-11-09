# Feature: legacy vehicles and temporary explosions
Status: deployed; client follow-up playtest pending
Branch: bilal/vehicles-and-config

## Player experience
Place a speederbike, X-wing, TIE fighter or U-wing item by right-clicking open terrain. Right-click the vehicle to choose a seat. Only its placer can pilot. The U-wing's existing secondary seat is Gunner and is open to everyone; only that role may fire held blasters. Actual passenger seats will be added later. Menus omit seat roles absent from the vehicle. Sneak/right-click to pack a landed, stopped, empty vehicle after combat has ended. Its remaining health is preserved. Destruction permanently loses the vehicle and causes one attributed explosion.

Speeders use forward/back and strafe controls and the legacy native one-block step height, with jump available separately. Aircraft use the recovered arcade controls: W engages continuous thrust; S brakes; a second W press boosts; looking steers; jump climbs; sneak descends and dismounts on the ground. Built-in aircraft cannons remain deferred. There are no fuel requirements, fuel items, refilling, skin menus, skin permissions or model variants in this port.

## Rules
One deployed vehicle per placer. Packing requires inventory room, a stationary empty vehicle on the ground, and both player and vehicle out of combat. Damage starts a 15-second vehicle combat window. A packed item carries an authoritative identity and token; stale copies cannot recreate deployed or destroyed vehicles. Placing a legitimately transferred packed item assigns the new placer as pilot authority.

Vehicle health is native living-entity health. ModelEngine supplies the scaled legacy hitbox and mounts; WeaponMechanics supplies its normal swept projectile collision and damage calculation. No second custom damage deduction runs. Protected regions deny damage; riders cannot shoot their own mount. Death ejects riders, clears the entity/model/index, clears native mob drops, records permanent destruction, then creates the explosion. Logout/death/world change releases a rider. Vehicles remain vulnerable when their owner is offline; shutdown saves deployed state and removes temporary entities, then the next startup reconstructs them once.

Every native entity/block explosion and WeaponMechanics projectile explosion uses a four-block sphere, at most 64 eligible blocks, and a 60-second real-time restoration deadline. Other plugins' cancellations and region protection remain authoritative. Blocks drop no items. Containers/tile data, bedrock/unbreakable blocks, portals, TNT/anchors and multipart blocks are preserved. Temporary holes reject placement, fluids and piston intrusion; nearby physics is held so supporting-block removal does not destroy adjacent belongings. Overlapping blasts keep the first snapshot/deadline. A global pending cap bounds work. Journals are written before destruction; startup resumes pending restoration, and clean shutdown restores it immediately. Occupied cells retry after the player or living mount leaves, avoiding suffocation. New administrator/plugin replacements win over restoration.

## Smallest playable version
All four recovered default models with original textures, sounds and inventory icons. Staff can issue fixtures with `/mandomc give <player> <speederbike|xwing|tiefighter|uwing>` while shops remain deferred. No fuel, skins, built-in vehicle cannons, purchases or repair mechanic is introduced. The U-wing gunner uses ordinary held blasters; pilot and passenger roles cannot fire them.

## Ownership and integrations
MandoMC owns identity, placement, seats, motion parameters and the block-restoration journal. ModelEngine R4.1.1 is a provided API dependency and owns rendering/mount transport. WeaponMechanics 4.3.1 owns projectile collision and native entity damage; the adapter disables its separate block damage/regeneration and dispatches shared protected block destruction. WorldGuard retains effective static-region policy, with no operator bypass. Claim/warpoint raids remain deferred.

## Parameters
Vehicle files live in `vehicles/`, each selecting one fixed model by stable item ID. Health/speed/scale, acceleration/braking, pitch/roll, boost duration/cooldown, sound, passenger seats and gunner seats are validated at startup. Existing legacy balance values are starting values for playtesting. `vehicles/settings.yml` bounds concurrent deployment (32 by default), vehicle combat (15 seconds) and the stationary horizontal-speed threshold (0.05 blocks/tick). All changes require restart. Existing U-wing configs migrate `seatone` from `passenger-seats` to `gunner-seats`, preserving other values. Virtual mount membership prevents one player from occupying multiple seats; completed dismounts clear any residual vendor seat entries.

`worlds/explosions.yml`: `radius: 4`, `max-blocks: 64`, `restore-seconds: 60`, `pending-limit: 4096`. The user accepted the radius/cap; all values are bounded at load. `data/vehicles.yml` and `data/explosion-restoration.yml` are runtime state, not configuration or Git assets.

## Verification
`./scripts/build.sh` runs the movement, migration and blast-bound tests plus existing regression tests. `scripts/build-vehicle-probe.sh` builds the disposable Paper probe, covering real mounted models, native/WeaponMechanics damage, ownership, packing, death explosions, protected/cancelled damage, block caps and one-minute restoration, plus a second boot for persistence. Run only in an isolated copy; the probe stops Paper after each phase.

Verified on an isolated Paper 26.1.2 server with ModelEngine R4.1.1 and WeaponMechanics 4.3.1: 205 vehicle/blast assertions, five restart assertions, 168 existing native-utility assertions and 54 arrival/WorldGuard assertions (432 total). Coverage includes a real scheduled E-11 projectile hitting the scaled native vehicle bounds exactly once, mounted surface/climb motion, driver/passenger ownership, packed health and duplicate prevention, exactly one death explosion, vendor blast routing, cancelled/protected blasts, the 64-block cap and complete regeneration after a real minute. Restart reconstructs one deployed identity with its saved health. The test server is stopped and temporary probes removed after verification.

The combined build passes 119 Java tests and 17 pack/tooling tests. The combined server also passes 205 vehicle/blast checks, five restart checks and 62 Jabba dungeon checks. The initial 496-file combined pack was published and deployed at 16:16 local on 2026-11-09, SHA-1 `e1607fa4e27326d34b43b1b9a3290dbcf429c43e`.

`scripts/build-vehicle-polish-probe.sh` builds the focused follow-up probe for real one-block climbing, hidden absent seats, gunner-only WeaponMechanics pre-shoot gating, duplicate seat prevention, old stale-seat cleanup, packing and identity-preserving badge refresh. All 38 focused live assertions pass. The restored original VEHICLE badge uses its legacy glyph and bitmap; existing packed items retain health and identity when their badge refreshes.

Client rendering, steering feel, gunner alignment and multiplayer latency require a matching Minecraft client playtest.

## Combined deployment
The deployment branch includes integrated Jabba/Tatooine and the deployed Rancor close-follow tuning. ModelEngine Premium R4.1.1 is required: the free edition's 12-model import cap cannot load the complete 15-model custom roster plus internal fire. The owner-supplied binary is pinned in the plugin lock and remains private. Deployment retains stopped-runtime backups and uses a clean restart. Runtime state and worlds must be preserved when deploying follow-ups; never restore a pre-playtest world merely to update code.

Follow-up deployment: source `b27b0f1`, published `resource-pack-26.1.2-b27b0f118ade` (497 files, SHA-1 `d3f5cb6557242c65cc45078e182604055957a3f8`). Restart completed at 16:36:05 local on 2026-11-09 with no startup errors. The tested JAR is installed, KOTH restored, expected seat/badge config migrations applied, and existing vehicle identity/health state preserved.
