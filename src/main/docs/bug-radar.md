# Bug Radar (High-Value Checks)

This file tracks consistency issues and hotspots agents should verify first.

## Command and permission mismatches to verify

- Keep command/permission consistency tests green (`PluginYmlConsistencyTest`) after any command changes.
- If adding commands, ensure `plugin.yml` includes metadata and the `permissions:` node is explicitly declared.
- `CommandModule.safe(...)` now logs severe for missing command keys; treat any such startup log as a release blocker.

## Reload safety hotspots

- Modules/services with background tasks or registries should be checked for enable/disable symmetry.
- Any listener registration outside `ListenerRegistrar` is a potential leak risk.
- Preserve repository-backed single source of truth for lottery/bounty runtime state; avoid reintroducing split static + repository writes.
- Parkour/event disable paths must clear active sessions/events so `/mmcreload` does not leave orphaned runtime state.

## Soft dependency hotspots

- Integrations with PlaceholderAPI, Vault, ModelEngine, WeaponMechanics, FancyHolograms should stay optional.
- Use `OptionalPluginSupport` for optional integration checks and fail closed when missing.
- Fail closed and log once when plugin is missing; avoid repetitive runtime spam.

## Config and parse safety hotspots

- Lottery draw day parsing must never throw in scheduler ticks; invalid config should degrade safely and warn.
- JSON repository loads should not crash module enable on malformed data.

## Usage

When starting bugfix work, scan this list before broad searching. If an item is fixed, update/remove it in the same PR.
