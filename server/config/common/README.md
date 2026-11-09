# Shared gameplay configuration
Keep only intentional, sanitized overrides here. Installed server `plugins/` directories and all worlds remain ignored; MandoMC Java source under `plugin/` stays tracked.

## Arrival and protection
- `mandomc.yml`: copy to the runtime MandoMC config while stopped, after importing and protecting both destinations.
- `worldguard-spawn.yml`: WorldGuard regions for SpawnWorld; protects the entire commissioned map.
- `worldguard-landing.yml`: WorldGuard regions for the disposable dev_world landing, x/z -144 through -129 inclusive (one 16×16 chunk), full build height.

The two region files are configuration only; they contain no map assets. On a fresh setup copy each to `WorldGuard/worlds/<world-name>/regions.yml`. If regions already exist, merge the named definitions deliberately rather than replacing the file. See [arrival setup](../../README.md#arrival-and-protection) for current restore instructions. Exact arrivals and aliases belong to MandoMC worlds.yml.

WorldGuard owns static regions. FactionsUUID will own player claims in the next slice. The development landing has greeting/farewell messages at its boundary; it is not a finished shuttle build.

WeaponMechanics `Resource_Pack_Download.Automatically_Send_To_Player` remains false. Paper sends the required GitHub-hosted MandoMC pack. Preserve unrelated vendor configuration when applying overrides.

`teleport.yml` is the shared spawn/survival countdown and effect configuration; copy it to the MandoMC runtime directory while stopped. World aliases now live in worlds.yml.

The temporary survival landing now occupies chunk (-9,-9). Its middle-block arrival is (-135.5, 73, -135.5); restore those coordinates in worlds.yml along with the region template. Both protected regions set weapon-shoot-message to “You can’t shoot in a safe zone.”

## Combat basics
While the server is stopped, copy `combat.yml`, `starter-kit.yml`, `survival-rules.yml` and `teleport.yml` into `plugins/MandoMC/`. New messages use bundled defaults, preserving existing translations. Delete the old teleport `combat-block-seconds` key; only `combat.yml:tag-seconds` owns the timer now.

Apply these vendor settings to the existing files; preserve unrelated settings and world data:
- Paper `config/paper-global.yml`: `misc.enable-nether: false` (also in the tracked template).
- Bukkit `bukkit.yml`: `settings.allow-end: false` (tracked).
- MandoMC worlds.yml lists only enabled existing dimensions; Nether/End stay absent. Preserve all dimension folders. SurvivalRules prevents portal use/creation.
- MandoMC alone grants the first-join kit; Essentials is removed.

SurvivalWorld's native keepInventory game rule remains false. MandoMC additionally forces carried-item loss for a combat logout even if a plugin sets keepInventory on that death. Do not install an additional combat logger or starter-kit plugin. `CombatProbe.jar` is temporary and must be removed before ordinary play.

## Preserve player game modes
The MandoMC world loader does not force game mode on login or world changes. Keep `force-gamemode=false` in server.properties. New players still begin in the server default Survival mode. WorldGuard continues to protect SpawnWorld and the survival landing independently of game mode.

## Hostile attack facing
Deploy mythicmobs/hostile-facing.yml with the existing reviewed mob/skill files and current MandoMC JAR. The shared MandoFaceTarget/MandoFaceTargetNow skills depend on the console-only native/model rotation bridge. Keep existing runtime mandomc-* filenames when updating those definitions; do not leave duplicate mob/skill files under source aliases. See docs/features/hostile-facing.md. No resource-pack regeneration is required.

## Rancor
Deploy rancor.yml to MandoMC, mythicmobs/rancor-mob.yml and rancor-skills.yml to the matching Mythic folders, and the rancor blueprint to ModelEngine. Requires the current plugin/shared hostile-facing skills and published Rancor pack. Legacy combat remains in Mythic; the configured native rubble effect is cosmetic. See docs/features/rancor.md.

## Native utilities
Copy worlds.yml and utilities.yml into plugins/MandoMC while stopped. They replace Multiverse world loading/exact spawns and configure personal homes/teleport requests. Preserve existing world keys and precise spawn coordinates. Messages use bundled defaults. See docs/features/native-utilities.md for permissions and migration. Creative bypasses the public countdown; all other checks remain.

## Event-ending titles
`events.yml:ending-countdown` controls the final-ten-second title and subtitle. `<seconds>` and `<event>` are supported placeholders. Existing idle-only `/event reload` applies presentation updates; implementation changes require the normal restart. Audiences remain event-owned (palace bounds for Jabba, Hoth for KOTH), and KOTH's timeout is included even with the shared scheduler disabled.
