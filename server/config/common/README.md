# Shared gameplay configuration
Keep only intentional, sanitized overrides here. Installed server `plugins/` directories and all worlds remain ignored; MandoMC Java source under `plugin/` stays tracked.

## Arrival and protection
- `mandomc.yml`: copy to the runtime MandoMC config while stopped, after importing and protecting both destinations.
- `worldguard-spawn.yml`: WorldGuard regions for SpawnWorld; protects the entire commissioned map.
- `worldguard-landing.yml`: WorldGuard regions for the disposable dev_world landing, x/z -176 through -112 inclusive, full build height.

The two region files are configuration only; they contain no map assets. On a fresh setup copy each to `WorldGuard/worlds/<world-name>/regions.yml`. If regions already exist, merge the named definitions deliberately rather than replacing the file. See [arrival setup](../../README.md#arrival-and-protection) for import and Multiverse settings. Spawn coordinates belong to Multiverse/world spawn settings; MandoMC resolves those rather than maintaining another coordinate copy.

WorldGuard owns static regions. FactionsUUID will own player claims in the next slice. The development landing has greeting/farewell messages at its boundary; it is not a finished shuttle build.

WeaponMechanics `Resource_Pack_Download.Automatically_Send_To_Player` remains false. Paper sends the required GitHub-hosted MandoMC pack. Preserve unrelated vendor configuration when applying overrides.

`teleport.yml` is the shared spawn/survival countdown and effect configuration; copy it to the MandoMC runtime directory while stopped. Restore the survival alias with `mv modify dev_world set alias SurvivalWorld`.
