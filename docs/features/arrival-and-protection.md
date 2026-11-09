# Feature: Arrival and protection
Status: implemented and deployed locally; client playtest pending
Branch: bilal/arrival-and-protection

## Player experience
A new player joins the recovered Black Spire Outpost, used as SpawnWorld, accepts the required pack, and sees a short configurable introduction. `/tutorial` replays it. `/survival` departs from the outpost after a five-second stationary warmup and lands inside a protected survival area. Movement, damage, teleport, logout, mounting or changing worlds cancels/refuses departure. There is no general return/home travel command in this slice.

The tutorial appears after a successfully loaded resource pack. Its versioned marker lives in the player's persistent data, so reconnects do not repeat it. Existing players retain their login locations; staff can visit SpawnWorld with `/mv tp SpawnWorld`.

## Rules and content
- The operator-supplied BlackSpireOutpost copy is imported as `minecraft:spawnworld`, alias SpawnWorld. The original backup remains untouched. The old Essentials spawn at approximately (-0.25, 93, -16.25), facing -9 degrees, supplied the entrance; the world's old (0, 4, 0) default was unsafe.
- Multiverse supplies first-join placement, world loading, adventure mode in SpawnWorld, survival mode outside, and the current bed/anchor-aware SpawnWorld respawn fallback. Group-home priority is still the next slice.
- WorldGuard denies player edits, PvP, chest access, explosions, destructive mobs and fire in SpawnWorld. Doors/buttons can be used. Players are invulnerable in both safe zones.
- Survival remains the existing disposable `dev_world` (`minecraft:overworld`). Landing is centered near (-135.5, 73, -135.5), within x/z -144..-129 inclusive (chunk -9,-9), y -64..319. Greeting/farewell messages explain the boundary. WorldGuard permits ordinary building and PvP beyond it; other vendors may impose their own rules.
- The public `/survival` route only departs from SpawnWorld. This avoids adding an unrestricted escape teleport before combat tagging exists. General `/spawn`, group-home travel, menus and their combat policy remain a later decision.
- Missing worlds, unsafe footing, unavailable vendors or missing PvP/edit protection prevent arrival travel. Invalid enabled setup blocks logins with an actionable console error. Both arrivals are checked at startup, login and departure.
- Starter equipment, credits, claims, combat tagging and custom damage remain later work. A permanent survival world still needs the agreed generation exclusions.

## Parameters and ownership
MandoMC config schema remains version 1. `arrival.enabled` defaults to false for a clean clone. The local override enables it; `arrival.spawn-world` and `arrival.survival-world` are distinct namespaced IDs, and `arrival.warmup-seconds` must be 1..30. Multiverse/world settings own the actual coordinates. `language/messages.yml` owns the tutorial and route messages. Restart after configuration changes.

WorldGuard owns the regions, not MandoMC. Its API is isolated in `integrations/regions`. Paper serves the required resource pack. No legacy plugin binaries or player data were imported.

## Verification
`./scripts/build.sh` builds the JAR and checks invalid world IDs, identical destinations and invalid warmups. The separate `scripts/build-arrival-probe.sh` produces an ignored, temporary server plugin for real WorldGuard queries and simulated player callbacks. The probe never connects a Minecraft client. It covers safe arrival geometry, boundary permissions, missing worlds, tutorial persistence and departure lifecycle. See `docs/status.md` for the latest run result.

Still requires a real client: map/pack appearance, first-ever join placement, perceived tutorial/warmup behavior, and actual block/PvP interactions with a non-op player. Full custom damage paths must be retested as they land.

## Next action
Join localhost, visit SpawnWorld, and play through `/tutorial` and `/survival`; inspect the commissioned map and test the safe-zone boundary without operator bypass.

Temporary interface note (2026-11-07): `/tutorial` and `/survival` are development-only commands. Their behavior is available for testing; the final onboarding/travel interface is undecided.

## Spawn travel follow-up
`/spawn` now goes to SpawnWorld; temporary `/survival` goes to SurvivalWorld (the existing dev_world under that Multiverse alias). Both use the shared three-second countdown in `travel/teleport.yml`, replacing the earlier five-second, one-way-only departure. Looking around is allowed; changing position cancels. See [command documentation](../commands.md).

Future tutorial direction: `/tutorial` will show short text and take the player to a library building in SpawnWorld with takeable, feature-specific guidebooks. This is deferred; current tutorial text is temporary.
