# Testing and Validation

## Local commands

- Unit tests: `mvn -q test`
- Build artifact: `mvn package` (jar at `target/MandoMC.jar`)
- Compile-only fallback (avoids local copy-to-server step): `mvn -q -DskipTests compile`

## Change-type validation

- Refactor:
  - Run tests
  - Confirm no command/config behavior changed unintentionally
- Bug fix:
  - Confirm original reproduction case is fixed
  - Check for nearby regressions in same module
- Feature:
  - Verify command paths, permissions, config defaults, and reload behavior

## Automated guardrail tests

- `PluginYmlConsistencyTest`: command key and permission node consistency, including reload permission contract.
- `SchedulerLifecycleStopTest`: verifies new scheduler stop/cancel semantics for lottery and parkour helpers.
- `LinkCommandTest`: validates link command behavior via injected seams without requiring plugin singleton wiring.

## Manual in-server smoke checks

- Plugin enable/disable is clean (no severe errors)
- `/mmcreload` works without leaked tasks/listeners/state
- Changed commands resolve and tab complete
- Changed GUI/event flows still function

## Paper-specific reminders

- Bukkit world/entity/player API usage on main thread only
- Async only for I/O/heavy work; hop back to main thread for server API
