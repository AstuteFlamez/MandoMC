# Resume here
Updated: 2026-11-05

## Milestone
Create a clean buildable foundation and preserve every legacy feature in a recovery branch.

## Accepted direction
Laptop development, selective recovery, and the public/custom division in architecture.md. Design player experience, choose playable feature order, build parameterized systems, then configure content. Merge features after validation and satisfaction.

## Implementation
Minimal bootstrap, config schema check and /mandomc status command. Maven reactor/wrapper, Java 25, pinned Paper 26.3 build 242 API. No gameplay modules or third-party plugins enabled. Server templates and lockfile exist; Minecraft is not running.

See docs/recovery.md for the old source. Maps/configs and editable ModelEngine blueprints still need recovery.

## Verification
Maven verify passed and produced MandoMC.jar. Verified packaged descriptor/config and Java 25 bytecode. Documentation links and JSON/XML parsed successfully. No automated gameplay tests exist yet; no in-server test has run. Recovery integrity and publication are checked by the migration operation.

## Next action
Design the first playable session in docs/player-experience.md and choose its feature brief. Runtime bootstrap and integration checks follow that choice.

## Open
Warpoints, raid limits, recovery costs, travel and resource roles. The earlier questionnaire is paused; resume design through concrete player scenarios.
