# Local Paper server
The installed development server runs from `server/runtime/`; reviewed shareable configuration is tracked, while worlds, installed plugins and private/generated state are ignored. Plugin source is in `plugin/`; pack source is in `resource-pack/`.

## Start and join
From the repository root:
```bash
./scripts/server-control.py start
./scripts/server-control.py status
./scripts/server-control.py command 'mandomc'
./scripts/server-control.py restart
./scripts/server-control.py stop
```
Startup is detached from the calling shell and waits for Paper's ready line. Start is idempotent; stop saves and exits cleanly, and restart completes both steps without asking for input. The controller verifies process identity and the runtime directory before signalling anything. It refuses an unrelated listener on port 25565 and never sends a forced kill. Runtime control files and console logs stay under ignored `.local/`.

On macOS, double-click `server/start.command` for one persistent Terminal console view. Commands go to Paper; `start`, `stop`, `restart` and `status` are handled by the controller. `exit` closes only the console view; the server keeps running. Agent-controlled restarts appear in that same view, with no Enter prompt. A visible Terminal window is optional for startup. This does not configure login/boot autostart or a crash-restart loop.

Join `localhost` using **Minecraft Java 26.1.2**. The server binds to `127.0.0.1:25565` with online-mode enabled. It loads SpawnWorld and SurvivalWorld; Nether and End are disabled. Java 25 is selected by `scripts/java-env.sh`; Python 3 synchronizes the hosted resource pack and controls the process. Default maximum heap is 4 GiB; set `MANDOMC_MAX_MEMORY=6G` before start to override it.

`./scripts/start-server.sh` remains the low-level foreground entry point for isolated integration probes. Do not run it alongside the managed server. Full code changes require stop → deploy → start, never Bukkit reload. Read `.local/server-console.log` or `server/runtime/logs/latest.log` for output.

## Installed baseline
Paper 26.1.2 build 74 is pinned in `paper.lock.json`. Newer Minecraft/Paper 26.3 exists; the chosen version is the newest documented common baseline we verified with the selected gameplay stack, including [WeaponMechanics 4.3.1](https://github.com/WeaponMechanics/WeaponMechanics/releases/tag/v4.3.1).

Exact official download URLs, versions and locally verified hashes are in `plugins.lock.json`. `verifiedPublishedHashes` distinguishes hashes checked against publisher metadata from locally recorded hashes alone.

| Plugin | Installed version |
| --- | --- |
| WeaponMechanics / MechanicsCore | 4.3.1 / 4.3.1 |
| PacketEvents | 2.14.0 |
| ModelEngine | R4.1.1, official free edition |
| MythicMobs | 5.14.0 official Modrinth release |
| FactionsUUID | 4.6.0 official public Maven artifact |
| WorldEdit / WorldGuard | 7.4.6 / 7.0.18 |
| Vault (inactive economy bridge) | 1.7.3-b131 |
| MandoMC | 0.1.0-SNAPSHOT, built locally |

FactionsUUID's [official Maven repository](https://factions.support/developers/) publishes a complete runnable shadowJar; 4.6.0 was its latest published artifact at setup time and declares API 26.1.2. Newer source tags exist, but were not substituted for this boot-tested binary. MythicMobs' published 5.14.0 artifact internally reports `5.14.0-SNAPSHOT-local`; its downloaded bytes matched the official release hashes. ModelEngine is the free edition; premium capabilities and gameplay limits have not been evaluated.

Historical baseline: all 12 plugins enabled together. Verified `/mandomc`, console plugin list, FactionsUUID list, the EssentialsX economy bridge through Vault, clean stop/restart and a Minecraft protocol status request. The GitHub pack publication also passed, its ZIP downloaded anonymously with matching hashes, and Paper restarted with required-pack delivery configured. The user confirmed a successful client join before the arrival work. The new arrival flow and pack/map appearance still need a client playtest.

Expected startup notices: newer Minecraft/Paper is available; ModelEngine has no MineSkin API key for optional skin generation; Java reports a third-party Unsafe deprecation. These did not prevent startup.

## What is ready
SpawnWorld, a protected survival landing, a replayable tutorial and shared `/spawn` and `/survival` travel are deployed locally. Group branding, raids, events and vehicles still need implementation. E-11, Desert Robes, the accepted Vader throw, first-join kit and combat basics are implemented; see the feature briefs. Default FactionsUUID rules and commands are temporary vendor behavior. This remains a local development build.

EssentialsX and Multiverse-Core are removed. MandoMC supplies the selected administration, personal travel and world-loading features. Credits are deferred; Vault has no economy provider. Removed plugin userdata/balances remain archived for rollback.

WeaponMechanics' `Resource_Pack_Download.Automatically_Send_To_Player` is set to false in its generated runtime config. Keep this intentional override when restoring configs; the new MandoMC pack will own presentation. The [MandoMC pack](../resource-pack/README.md) is published through GitHub Releases and applied through server.properties. Startup synchronizes its URL, SHA-1, stable UUID and required-pack prompt. Minecraft downloads the pack directly from GitHub when a player accepts it. Wait for the publishing workflow, then restart Paper to adopt updated assets; reconnect to apply them.

## Change or restore content
Stop the server, back up `server/runtime/`, then restore one plugin config or map at a time. Never copy old JARs over the pinned stack. Check migrations and restart logs after each restoration. World upgrades cannot be undone by merely swapping server JARs.

To deploy a new MandoMC build while stopped:
```bash
./scripts/build.sh
./scripts/server-control.py stop
cp plugin/target/MandoMC.jar server/runtime/plugins/MandoMC.jar
./scripts/server-control.py start
```

The runtime itself is not a Git backup. A clean clone needs Java 25, the Paper artifact saved as `server/runtime/paper.jar`, and each locked vendor artifact in `server/runtime/plugins/`. Build/copy MandoMC, copy the local server.properties template on initial setup, and accept Minecraft's EULA before starting. Downloaded plugin dependencies and defaults are generated on the first boot. Do not overwrite an existing server.properties or world with setup templates.

## Arrival and protection
The supplied BlackSpireOutpost map is imported as SpawnWorld (`minecraft:spawnworld`). Its whole world is protected. New players arrive there; existing players retain their saved login location. As an operator, use `/mvtp SpawnWorld` to visit, `/tutorial` for the introduction, then `/survival` and stand still for three seconds. `/spawn` returns to SpawnWorld. Looking around is allowed; walking/jumping cancels. Group home travel remains future work.

The survival landing is in `dev_world`, around (-135.5, 73, -135.5). Protection covers x/z -144 through -129 inclusive (one 16×16 chunk) and the full build height. You see a message when leaving protection; the terrain is still a disposable development world. Operator accounts normally bypass WorldGuard: test with a non-op account, or use `/rg bypass` deliberately for your own test. `/mandomc` checks both destinations and their protection.

For a clean local restore:
1. Stop Paper and back up the entire runtime/world container and WorldGuard/MandoMC configuration. Restore the already migrated `dev_world/dimensions` and player data from a verified Paper backup. Do not feed raw legacy archives to WorldService: it deliberately refuses missing dimension data instead of generating terrain.
2. Apply `config/common/mandomc/worlds/worlds.yml` and `config/common/mandomc/admin/utilities.yml` at the same relative paths under `plugins/MandoMC/`. Preserve stable dimension keys, exact coordinates/facing and aliases. The four integrated dimensions are overworld, devworld, spawnworld and hoth; Nether/End remain disabled.
3. Restore the existing WorldGuard regions; never overwrite unrelated regions. Deploy `config/common/mandomc/config.yml` as MandoMC `config.yml`, with `spawn-protection=0` so WorldGuard owns static protection.
4. Preserve native map gamerules (`advance_time=false`, `advance_weather=false`, `spawn_mobs=false`, `random_tick_speed=0`, `fire_spread_radius_around_player=0` on the adventure maps). Survival retains its normal rules. MandoMC preserves beds and uses Batuu as the fallback for deaths; first joins go to Batuu and returning players keep their position/game mode.
5. Build/deploy MandoMC and restart. `/mandomc` must report both arrivals ready. Missing enabled worlds or unsafe/unprotected arrival configuration blocks logins until repaired. `/mvtp` accepts world names and old aliases.

Maps, installed plugins and player state are excluded from Git; reviewed configuration is tracked. The original logo is versioned at docs/assets/server-icon.png and copied into the runtime on startup. Full legacy archive migration is a separate explicit operation; restore the existing migrated dimensions for routine recovery.

## Repeatable arrival checks
Run `./scripts/build.sh`, then `./scripts/build-arrival-probe.sh`. With the server stopped, temporarily copy `.local/arrival-probe/ArrivalProbe.jar` to `server/runtime/plugins/ArrivalProbe.jar`. Start the local server and look for `ARRIVAL PROBE PASSED` in the log. The probe uses real WorldGuard/world data and non-op player proxies; it is not a Minecraft client or complete gameplay test. Stop Paper, remove that test JAR, and restart before ordinary play. Do not deploy the probe to a shared server.

The paid/commissioned assets are not a Git backup. Keep their originals and runtime backups separately. The tracked top-level `plugin/` directory is MandoMC source, distinct from ignored server-side `plugins/` installations.

Temporary interface note (2026-11-07): `/tutorial` and `/survival` are development-only commands. Their behavior is available for testing; the final onboarding/travel interface is undecided.

See [runtime configuration policy](runtime/README.md) and [world sources](../WORLD_SOURCES.txt).

## Spawn travel follow-up
`/spawn` now goes to SpawnWorld; temporary `/survival` goes to SurvivalWorld (the existing dev_world under that MandoMC world alias). Both use the shared three-second countdown in `travel/teleport.yml`, replacing the earlier five-second, one-way-only departure. Looking around is allowed; changing position cancels. See [command documentation](../docs/commands.md).

Future tutorial direction: `/tutorial` will show short text and take the player to a library building in SpawnWorld with takeable, feature-specific guidebooks. This is deferred; current tutorial text is temporary.

### Reuse the console tab
The managed console view stays open across automated restarts. Use `scripts/server-control.py` for all ordinary agent starts/stops; never ask the user to press Enter. The former restart-request marker/manual loop is retired. An old console tab running that earlier loop can be closed; new launches use the controller above.

## Recovered Hoth and event travel
Hoth is loaded as `minecraft:hoth`; Paper stores it under the main world's `dimensions/minecraft/hoth`. Back up that directory and WorldGuard/MandoMC configuration together. Its exact arrival and Hoth alias are in `worlds/worlds.yml`. The native map gamerules and WorldGuard warzone/safe arrival remain intact. The legacy archive remains untouched.

The deployable `events/schedule.yml`, `events/koth.yml` and `travel/warps.yml` defaults are in server/config/common/mandomc/. `/warp` reaches only SpawnWorld, Earth and Hoth; `/events` shows hourly KOTH. `/event start koth` starts a manual round. Rewards remain disabled. `./scripts/build-koth-probe.sh` builds the isolated integration probe; install only while stopped, remove after controlled verification, and use normal restarts for code changes. See docs/features/koth-and-warps.md for lifecycle and verification limits.

## Native utility deployment
Deploy MandoMC plus common/mandomc/worlds/worlds.yml and common/mandomc/admin/utilities.yml while stopped. Back up world/player data and vendor configuration, then move EssentialsX and Multiverse-Core JARs outside plugins/. Keep old userdata/balances for rollback. The replacement does not require resource-pack changes. Use normal restart, never Bukkit reload.

See [native utilities](../docs/features/native-utilities.md) for commands and permissions. Public trips are instant in Creative; other modes keep the shared three-second warmup. Consent, destination safety and combat checks apply in either case.
