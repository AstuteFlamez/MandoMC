# Bug Radar (High-Value Checks)

This file tracks consistency issues and hotspots agents should verify first.

## Command and permission mismatches to verify

- `ReloadCommand` checks `mmc.reload`, while `plugin.yml` defines `mandomc.admin.reload`.
  - Likely permission mismatch causing denied access for intended admins.
- `plugin.yml` currently has `Drop:` (capital D), while Java wiring uses `"drop"`.
  - Command key lookup is case-sensitive; registration can silently fail.
- `LotteryCommand` checks `mandomc.lottery.admin`; verify that permission exists in `plugin.yml`.

## Reload safety hotspots

- Modules/services with background tasks or registries should be checked for enable/disable symmetry.
- Any listener registration outside `ListenerRegistrar` is a potential leak risk.

## Soft dependency hotspots

- Integrations with PlaceholderAPI, Vault, ModelEngine, WeaponMechanics, FancyHolograms should stay optional.
- Fail closed and log once when plugin is missing; avoid repetitive runtime spam.

## Usage

When starting bugfix work, scan this list before broad searching. If an item is fixed, update/remove it in the same PR.
