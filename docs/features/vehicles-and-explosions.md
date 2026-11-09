# Feature: legacy vehicles and temporary explosions
Status: server integration verified; client playtest pending
Branch: bilal/vehicles-and-config

## Player experience
Place a speederbike, X-wing, TIE fighter or U-wing item by right-clicking open terrain. Right-click the vehicle to choose a seat. Only its placer can pilot; the U-wing's passenger seat is open to everyone. Sneak/right-click to pack a landed, stopped, empty vehicle after combat has ended. Its remaining health is preserved. Destruction permanently loses the vehicle and causes one attributed explosion.

Speeders use forward/back and strafe controls, with jump over small obstacles. Aircraft use the recovered arcade controls: W engages continuous thrust; S brakes; a second W press boosts; looking steers; jump climbs; sneak descends and dismounts on the ground. Aircraft remain transport-only. There are no fuel requirements, fuel items, refilling, skin menus, skin permissions or model variants in this port.

## Rules
One deployed vehicle per placer. Packing requires inventory room, a stationary empty vehicle on the ground, and both player and vehicle out of combat. Damage starts a 15-second vehicle combat window. A packed item carries an authoritative identity and token; stale copies cannot recreate deployed or destroyed vehicles. Placing a legitimately transferred packed item assigns the new placer as pilot authority.

Vehicle health is native living-entity health. ModelEngine supplies the scaled legacy hitbox and mounts; WeaponMechanics supplies its normal swept projectile collision and damage calculation. No second custom damage deduction runs. Protected regions deny damage; riders cannot shoot their own mount. Death ejects riders, clears the entity/model/index, clears native mob drops, records permanent destruction, then creates the explosion. Logout/death/world change releases a rider. Vehicles remain vulnerable when their owner is offline; shutdown saves deployed state and removes temporary entities, then the next startup reconstructs them once.

Every native entity/block explosion and WeaponMechanics projectile explosion uses a four-block sphere, at most 64 eligible blocks, and a 60-second real-time restoration deadline. Other plugins' cancellations and region protection remain authoritative. Blocks drop no items. Containers/tile data, bedrock/unbreakable blocks, portals, TNT/anchors and multipart blocks are preserved. Temporary holes reject placement, fluids and piston intrusion; nearby physics is held so supporting-block removal does not destroy adjacent belongings. Overlapping blasts keep the first snapshot/deadline. A global pending cap bounds work. Journals are written before destruction; startup resumes pending restoration, and clean shutdown restores it immediately. Occupied cells retry after the player or living mount leaves, avoiding suffocation. New administrator/plugin replacements win over restoration.

## Smallest playable version
All four recovered default models with original textures, sounds and inventory icons. Staff can issue fixtures with `/mandomc give <player> <speederbike|xwing|tiefighter|uwing>` while shops remain deferred. No fuel, skins, weapon firing, purchases or repair mechanic is introduced.

## Ownership and integrations
MandoMC owns identity, placement, seats, motion parameters and the block-restoration journal. ModelEngine R4.1.1 is a provided API dependency and owns rendering/mount transport. WeaponMechanics 4.3.1 owns projectile collision and native entity damage; the adapter disables its separate block damage/regeneration and dispatches shared protected block destruction. WorldGuard retains effective static-region policy, with no operator bypass. Claim/warpoint raids remain deferred.

## Parameters
Vehicle files live in `vehicles/`, each selecting one fixed model by stable item ID. Health/speed/scale, acceleration/braking, pitch/roll, boost duration/cooldown, sound and passenger seats are validated at startup. Existing legacy balance values are starting values for playtesting. `vehicles/settings.yml` bounds concurrent deployment (32 by default), vehicle combat (15 seconds) and the stationary horizontal-speed threshold (0.05 blocks/tick). All changes require restart.

`worlds/explosions.yml`: `radius: 4`, `max-blocks: 64`, `restore-seconds: 60`, `pending-limit: 4096`. The user accepted the radius/cap; all values are bounded at load. `data/vehicles.yml` and `data/explosion-restoration.yml` are runtime state, not configuration or Git assets.

## Verification
`./scripts/build.sh` runs the movement, migration and blast-bound tests plus existing regression tests. `scripts/build-vehicle-probe.sh` builds the disposable Paper probe, covering real mounted models, native/WeaponMechanics damage, ownership, packing, death explosions, protected/cancelled damage, block caps and one-minute restoration, plus a second boot for persistence. Run only in an isolated copy; the probe stops Paper after each phase.

Verified on an isolated Paper 26.1.2 server with ModelEngine R4.1.1 and WeaponMechanics 4.3.1: 205 vehicle/blast assertions, five restart assertions, 168 existing native-utility assertions and 54 arrival/WorldGuard assertions (432 total). Coverage includes a real scheduled E-11 projectile hitting the scaled native vehicle bounds exactly once, mounted surface/climb motion, driver/passenger ownership, packed health and duplicate prevention, exactly one death explosion, vendor blast routing, cancelled/protected blasts, the 64-block cap and complete regeneration after a real minute. Restart reconstructs one deployed identity with its saved health. The test server is stopped and temporary probes removed after verification.

The build passes 93 Java tests and 17 pack/tooling tests. The 291-file pack builds with SHA-1 `b05385a39ffc666ea8f21de57451d558219978b9`; generated vehicle textures, original icons and engine sounds resolve. This isolated branch is based on integrated main. The active Jabba checkout, development server and published pack remain unchanged; combine the assets with that work before deployment.

Client rendering, steering feel, passenger alignment and multiplayer latency require a matching Minecraft client playtest.
