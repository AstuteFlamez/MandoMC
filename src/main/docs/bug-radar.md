# Bug Radar (High-Value Checks)

This file tracks consistency issues and hotspots agents should verify first.

## Command and permission mismatches to verify

- Keep command/permission consistency tests green (`PluginYmlConsistencyTest`) after any command changes.
- If adding commands, ensure `plugin.yml` includes metadata and the `permissions:` node is explicitly declared.
- `CommandModule.safe(...)` now logs severe for missing command keys; treat any such startup log as a release blocker.

## Reload safety hotspots

- Modules/services with background tasks or registries should be checked for enable/disable symmetry.
- Any listener registration outside `ListenerRegistrar` is a potential leak risk.
- Continue reducing static mutable storage usage in lottery/bounty/parkour paths where repository/service seams already exist.

## Soft dependency hotspots

- Integrations with PlaceholderAPI, Vault, ModelEngine, WeaponMechanics, FancyHolograms should stay optional.
- Fail closed and log once when plugin is missing; avoid repetitive runtime spam.

## Usage

When starting bugfix work, scan this list before broad searching. If an item is fixed, update/remove it in the same PR.
