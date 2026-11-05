# Resume here
Updated: 2026-11-05

## Milestone
Complete: clean buildable foundation and 16 legacy feature recovery branches, plus the full old-main snapshot.

## Accepted direction
Laptop development, selective recovery, and the public/custom division in architecture.md. Design player experience, choose playable feature order, build parameterized systems, then configure content. Merge features after validation and satisfaction.

## Implementation
Minimal bootstrap, config schema check and /mandomc status command. Maven reactor/wrapper, Java 25, pinned Paper 26.3 build 242 API. No gameplay modules or third-party plugins enabled. Server templates and lockfile exist; Minecraft is not running.

See docs/recovery.md for the old source. Maps/configs and editable ModelEngine blueprints still need recovery.

## Verification
Maven verify passed and produced MandoMC.jar. Verified packaged descriptor/config and Java 25 bytecode. Documentation links and JSON/XML parsed successfully. All 294 legacy feature files match their original Git blobs exactly across the 16 recovery branches; 13 obsolete documentation/rule files remain in the full snapshot. The split leaves the main working tree clean. No automated gameplay tests exist yet; no in-server test has run.

## Next action
Design the first playable session in docs/player-experience.md and choose its feature brief. Runtime bootstrap and integration checks follow that choice.

## Open
Warpoints, raid limits, recovery costs, travel and resource roles. The earlier questionnaire is paused; resume design through concrete player scenarios.
