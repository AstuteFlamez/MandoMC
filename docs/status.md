# Resume here
Updated: 2026-11-05

## Milestone
Current: first-session gameplay design on bilal/player-session-design. Complete: clean buildable foundation and 16 legacy feature recovery branches, plus the full old-main snapshot.

## Accepted direction
Laptop development, selective recovery, and the public/custom division in architecture.md. Design player experience, choose playable feature order, build parameterized systems, then configure content. Merge features after validation and satisfaction.

New arrivals start in the existing SpawnWorld map, receive a short tutorial, and go to survival to establish a base. Death loses carried equipment; faction stockpiles support re-equipping. The first friends playtest should combine the discussed systems in a rough integrated version; individual development slices build toward it.

Survival has a fixed protected landing zone. Solo players can create a faction and claim a starter base outside protected spawn without a credit requirement. Respawn priority: faction home, valid bed, SpawnWorld. A possible three-hour faction creation cooldown remains tentative.

Combat blocks voluntary teleports; logging out in combat causes death. First join grants a basic blaster, vanilla tools and food once. Starting credits remain undecided.

Research-backed travel recommendation is recorded in docs/player-experience.md as a proposal, not accepted policy: free commands/menu plus optional themed terminals, fixed public arrivals, faction home, and a proposed five-second warmup. Published server guides support this as a comparable design; player preference remains a prediction to test.

## Implementation
Minimal bootstrap, config schema check and /mandomc status command. Maven reactor/wrapper, Java 25, pinned Paper 26.3 build 242 API. No gameplay modules or third-party plugins enabled. Server templates and lockfile exist; Minecraft is not running.

See docs/recovery.md for the old source. Maps/configs and editable ModelEngine blueprints still need recovery.

## Verification
Maven verify passed and produced MandoMC.jar. Verified packaged descriptor/config and Java 25 bytecode. Documentation links and JSON/XML parsed successfully. All 294 legacy feature files match their original Git blobs exactly across the 16 recovery branches; 13 obsolete documentation/rule files remain in the full snapshot. The split leaves the main working tree clean. No automated gameplay tests exist yet; no in-server test has run.

## Next action
Review the travel recommendation with the user, then define basic equipment acquisition and replacement.

## Open
Tutorial format, kit quantities/ammo, starting credits, travel, combat tag details, faction creation cooldown, claim capacity, warpoints, raid limits, recovery costs, resource roles, and content depth for the integrated playtest. Continue design through concrete player scenarios; no gameplay code changed in this step.
