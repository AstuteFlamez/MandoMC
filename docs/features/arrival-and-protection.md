# Feature: Arrival and protection
Status: design; implementation not started
Branch: proposed bilal/arrival-and-protection

## Player experience
A new player joins the existing SpawnWorld, accepts the required resource pack and sees a short introduction directing them toward survival and a base. Their first survival arrival is a fixed protected landing zone. They can build on eligible survival land outside it.

This checkpoint establishes the place to play. The first-join blaster/tools/food grant is integrated once the real item and weapon definitions exist in the combat slice; it must be present before the friends playtest.

## Rules
- SpawnWorld and the survival landing are safe areas. Players cannot alter those areas or harm each other there. Building belongs to the survival world.
- The pack is required. Verify the actual client behavior when a download is declined or fails.
- The tutorial should be short and replayable. Proposed first presentation: a few configurable messages with a clear survival destination. Presentation remains open.
- The accepted respawn priority is group home, valid bed, SpawnWorld. This slice provides the SpawnWorld destination; the following group slice implements and checks the complete resolver.
- Combat must block voluntary travel. The public travel menu/commands and warmup remain proposals; use staff-assisted transport to exercise destinations until that policy is selected.
- Missing worlds or unsafe configured arrivals must produce actionable errors and prevent the affected travel action. Do not silently send players to an unintended world.
- Persist any first-join/tutorial bookkeeping used by this slice by player UUID. Reconnects and restarts must not repeat a one-time grant later.
- Survival generation exclusions need an explicit list before generating the permanent world. Villages are unwanted; do not interpret that as removing every structure. Use a disposable survival world until this is configured and verified.

Unresolved before finalizing this feature: tutorial format, accepted travel entry points, exact landing locations/bounds, world generation exclusions and recovery source for SpawnWorld and the pack. Group naming belongs to the next slice.

## Smallest playable version
One local Paper server, the recovered SpawnWorld, one disposable survival world, the required pack and two protected arrival areas. A non-op player can join, read the introduction, reach the survival landing and build outside protection. The same player cannot damage or edit the protected areas.

Deferred to later slices: real starter weapons and kit, group claims/home fallback, combat tagging, shops, events, vehicles and raiding. Protection must be retested when each custom damage path is added.

## Ownership and integrations
- Multiverse-Core loads worlds. MandoMC owns destination meaning and first-session behavior.
- WorldGuard/WorldEdit define static protection. MandoMC does not create a second region authority.
- Paper serves the required resource-pack configuration; MandoPack supplies the assets. Verify pack compatibility rather than assuming the old pack works on the pinned client.
- FactionsUUID claims are introduced next and must not be mirrored into WorldGuard.
- No economy balance or weapon identity is created by this slice.

Before enabling vendors, verify their exact releases against the pinned Paper/JDK tuple, document hashes and add only the dependencies this slice uses. Restore maps into ignored local runtime storage from copies; retain the originals.

## Parameters
Candidate configuration contract only. These keys are not implemented, and defaults should use recovered map coordinates rather than invented positions. Restart for these settings initially; reload support is deferred.

| Candidate key | Meaning and unit | Validation/default | Reload or restart |
| --- | --- | --- | --- |
| destinations.spawn.world | Spawn world ID | Existing loaded world; expected SpawnWorld | Restart |
| destinations.spawn.position | Arrival x/y/z and yaw/pitch | Finite values; safe loaded location; coordinates pending | Restart |
| destinations.survival.world | Survival world ID | Existing loaded world; ID pending | Restart |
| destinations.survival.position | Fixed landing x/y/z and yaw/pitch | Finite values; inside verified protected landing | Restart |
| tutorial.messages | Ordered short introduction | Nonempty configurable text; exact copy pending | Restart |

Region bounds, world loading and the pack URL/hash belong in their owning server/plugin configuration. Reference those definitions where needed instead of duplicating them in MandoMC YAML. Include an actual configuration example only after implementing and validating its schema.

## Verification
Not run; this is a brief.

1. Boot with the selected world/protection vendors and pack configuration; inspect dependency/config errors.
2. Join as a fresh non-op account, accept the pack and verify the spawn and introduction. Decline/fail the pack in a separate run and verify required-pack enforcement.
3. Reach the survival landing; attempt block edits, interactions and PvP on both sides of its boundary and in SpawnWorld.
4. Reconnect and restart; confirm worlds, destinations, protection and onboarding state persist.
5. Configure a missing world or invalid destination in an isolated test; verify a clear failure with no unsafe teleport.
6. When group/combat slices land, repeat with the full respawn chain and each real custom damage source.

## Pause note
The repository builds only its existing bootstrap. No gameplay from this brief is implemented or runtime-tested. Next action: locate a copy of SpawnWorld and the usable MandoPack artifact, then inventory their versions and restore them into ignored local runtime storage.
