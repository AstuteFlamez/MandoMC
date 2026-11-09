# Feature: legacy events, Hoth KOTH and public warps
Status: deployed and server-verified; client playtest pending
Branch: bilal/koth-events

## Player experience
Recover the legacy hourly event rotation, public event menu, KOTH boss bar/boundary and public warp menu. `/event` (`/events`) shows current/next event and selection chances; `/warp` (`/warps`, `/travel`) shows exactly Batuu (Black Spire Outpost), Earth and Hoth. Direct warp names and legacy aliases select the same destinations. All travel uses the existing three-second stationary warmup, movement/damage cancellation and combat denial.

## Rules
Recover the supplied Hoth map with the original Hoth arrival (-47,80,-153) and canyon capture point (-148,27,141), radius 8 and height tolerance 4. Validate actual footing/protection before enabling routes. Hoth is protected against edits/explosions; the legacy warzone allows combat and the arrival remains safe. SpawnWorld and the existing survival world retain their data and spawn authority; Earth is a display name, not a replacement survival map.

Capture uses the previously accepted three-minute continuous hold: enemies entering do not reset the holder; clearing all holders resets it. With clan work deferred, each player is a separate contestant. Creative, spectator, vanished, invulnerable and dead players do not capture. Death/logout/world departure remove eligibility. Re-entry after death is unlimited. No custom clan state, warpoints or rewards are introduced. User explicitly chose rewards later; successful capture announces the winner but creates no chest, credits, crate key or item.

## Smallest playable version
One registered KOTH with the legacy hourly schedule in explicit America/Chicago time: start :00, ending warning :50, stop :55, next-event warning :55. Keep one active/queued event, weighted selection and cooldown fallback, admin start/stop/next/reroll/status/list/enable/disable, and idle-only validated config reload. Restart ends a running event without award or catch-up replay. Manual starts have a bounded lifetime. Future event types register with the same lifecycle; Jabba remains deferred.

## Ownership and integrations
MandoMC owns event lifecycle/capture/menus. TeleportService owns every warp timer. WorldService owns loaded worlds/spawns after the native utilities migration. WorldGuard owns map protection and warzone combat. Feature code references the current APIs, rather than merging recovery payloads or legacy plugin binaries. Legacy reference: recovery branches `bilal/legacy-koth`, `bilal/legacy-event-framework`, `bilal/legacy-travel`; source commit 03daf999caf6d8cec6773a6924d2a7b66257a934. World binaries/player state remain ignored and original backups untouched.

## Parameters
`events.yml` owns schedule/timezone/menu text; `events/koth.yml` owns enabled/weight/cooldown, capture-zone bounds, capture/lifetime and feedback. `warps.yml` owns only the three supported destinations and menu text. Reload is refused during an active event and validates a replacement before changing state. Restart is required for code and world imports.

## Verification
`./scripts/build.sh -o` passes all 75 Java tests. The isolated live Paper probe passes 63 assertions for restored world/storage, all three safe arrivals, WorldGuard/WeaponMechanics protection, command ownership, capture continuity/leave/logout/death/winner, excluded game modes/vanish/invulnerability, no reward/chest mutation, event start/stop/queue/enable/disable, atomic invalid reload, active reload denial, bounded timeout, menu transfer cancellation, exactly three worlds, shared pending travel, full countdown, movement/damage/combat denial and non-admin refusal. Hourly boundaries, lag/DST handling, weighted/cooldown fallback, settings bounds and aliases also have unit coverage.

Hoth imported from the supplied legacy `Hoth` directory (not `Hoth4kx4k`). Region/POI/level.dat hashes matched before Paper migration; the ignored import backup records the manifest. Old entity/player/plugin state was omitted. Actual capture-point block is air over ice at the recovered location. Paper migrated the map into the main world container's `dimensions/minecraft/hoth`. Hoth keeps safe world respawn fallback and valid bed respawns; portals/natural mob spawning/time/weather changes are disabled. WorldGuard templates and feature configs are retained under server/config/common.

Temporary probe removed and normal server restarted cleanly. Deployed/build SHA-256 match: `b7fc372ee04897893268137c52fa25780e4f5fa418a0166cf279f00ccade990d`. The existing saved five-second saber delay is now active. These API/world checks do not replace client rendering and authenticated multiplayer capture checks.

## Pause note
Next action: client-check `/warp`, `/events` and a KOTH round using `/event start koth`. Rewards and clan/group ownership remain deferred.

## Galaxy warp presentation follow-up
The user requested a galaxy layout and renamed the spawn destination Batuu. The menu now has six rows (54 slots): Earth at zero-based slot 1 in the upper-left Unknown Regions, Batuu at slot 28 on the western edge, and Hoth at slot 39 southeast of Batuu. Earth’s isolated position is a user-selected server fiction. Batuu represents Black Spire Outpost; the existing SpawnWorld data and legacy aliases remain. `/warp batuu` and tab completion use the new public name.

The original Earth/Hoth textures and the legacy spawn's Naboo texture are recovered byte-for-byte as namespaced item models. `resource-pack/planet-sources.json` records provenance; unrelated vanilla sticks are untouched. The official galaxy map is the layout reference: https://www.starwars.com/star-wars-galaxy-map . No old GUI background is restored because its decorative slots do not match the requested positions. Shared gray-pane menu conventions remain.

Local build and all 75 Java tests pass; 14 pack checks and 67 live Paper assertions pass. All 211 previous pack files are identical; nine model/texture files are added. The user explicitly authorized publication. Pack `resource-pack-26.1.2-1a2e1c028910` is published; the anonymous startup download/cache and server SHA-1 match `cc3e6022cadcfa690b04530a81f32f982af61587`. The verified menu build is deployed, temporary probe removed, and normal server restarted. Client rendering still requires reconnecting to load the new pack.

## Canyon terminology and later build
The event is named KOTH, but its current arena is a canyon. Location-facing messages call it the canyon. The user requested a small KOTH build/platform later in the todo list; this is late pre-release construction work, separate from the current capture-zone implementation. No platform is built or terrain changed in this wording pass.

Event countdown presentation: `/events` and `/event status` use readable Hours/Mins/Seconds (for example `1 Hour 2 Mins 3 Seconds`) through `<time>`. Zero units are omitted; `0 Seconds` is shown at zero. All 76 unit tests pass and the live status output is verified after normal restart.
