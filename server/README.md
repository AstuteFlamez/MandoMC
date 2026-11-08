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
Paper 26.1.2 build 74 is pinned in `paper.lock.json`. Newer Minecraft/Paper 26.3 exists; the chosen version is the newest documented common baseline we verified with the selected gameplay stack, including [WeaponMechanics 4.3.1](https://github.com/WeaponMechanics/WeaponMechanics/releases/tag/v4.3.1) and [EssentialsX 2.22.0](https://github.com/EssentialsX/Essentials/releases/tag/2.22.0).

Exact official download URLs, versions and locally verified hashes are in `plugins.lock.json`. `verifiedPublishedHashes` distinguishes hashes checked against publisher metadata from locally recorded hashes alone.

| Plugin | Installed version |
| --- | --- |
| WeaponMechanics / MechanicsCore | 4.3.1 / 4.3.1 |
| PacketEvents | 2.14.0 |
| ModelEngine | R4.1.1, official free edition |
| MythicMobs | 5.14.0 official Modrinth release |
| FactionsUUID | 4.6.0 official public Maven artifact |
| WorldEdit / WorldGuard | 7.4.6 / 7.0.18 |
| Multiverse-Core | 5.8.2 |
| EssentialsX Core / Vault | 2.22.0 / 1.7.3-b131 |
| MandoMC | 0.1.0-SNAPSHOT, built locally |

FactionsUUID's [official Maven repository](https://factions.support/developers/) publishes a complete runnable shadowJar; 4.6.0 was its latest published artifact at setup time and declares API 26.1.2. Newer source tags exist, but were not substituted for this boot-tested binary. MythicMobs' published 5.14.0 artifact internally reports `5.14.0-SNAPSHOT-local`; its downloaded bytes matched the official release hashes. ModelEngine is the free edition; premium capabilities and gameplay limits have not been evaluated.

All 12 plugins enabled together. Verified `/mandomc`, console plugin list, FactionsUUID list, the EssentialsX economy bridge through Vault, clean stop/restart and a Minecraft protocol status request. The GitHub pack publication also passed, its ZIP downloaded anonymously with matching hashes, and Paper restarted with required-pack delivery configured. The user confirmed a successful client join before the arrival work. The new arrival flow and pack/map appearance still need a client playtest.

Expected startup notices: newer Minecraft/Paper is available; ModelEngine has no MineSkin API key for optional skin generation; Java reports a third-party Unsafe deprecation. These did not prevent startup.

## What is ready
SpawnWorld, a protected survival landing, a replayable tutorial and shared `/spawn` and `/survival` travel are deployed locally. Group branding, raids, events and vehicles still need implementation. E-11, Desert Robes, the accepted Vader throw, first-join kit and combat basics are implemented; see the feature briefs. Default FactionsUUID rules and commands are temporary vendor behavior. This remains a local development build.

EssentialsX is the credit balance provider. No EssentialsChat/Spawn/Protect modules were installed. Other plugins' kits, travel, selling and raid defaults must be restricted when their feature slices are implemented.

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
The supplied BlackSpireOutpost map is imported as SpawnWorld (`minecraft:spawnworld`). Its whole world is protected. New players arrive there; existing players retain their saved login location. As an operator, use `/mv tp SpawnWorld` to visit, `/tutorial` for the introduction, then `/survival` and stand still for three seconds. `/spawn` returns to SpawnWorld. Looking around is allowed; walking/jumping cancels. Group home travel remains future work.

The survival landing is in `dev_world`, around (-135.5, 73, -135.5). Protection covers x/z -144 through -129 inclusive (one 16×16 chunk) and the full build height. You see a message when leaving protection; the terrain is still a disposable development world. Operator accounts normally bypass WorldGuard: test with a non-op account, or use `/rg bypass` deliberately for your own test. `/mandomc` checks both destinations and their protection.

For a clean local restore:
1. Stop Paper and back up the whole runtime. Copy the supplied map into `server/runtime/SpawnWorld`; omit old playerdata, stats, advancements, session.lock, uid.dat and old Paper overrides. Preserve the original backup.
2. Start with MandoMC arrival disabled. Run `mv import SpawnWorld normal`, then `mv modify SpawnWorld set alias SpawnWorld` and `mv setspawn SpawnWorld:-0.25,93,-16.25,-9,-0.9`. The pinned command's final fields are yaw,pitch.
3. Set SpawnWorld properties with `mv modify SpawnWorld set <property> <value>`: `gamemode adventure`, `pvp false`, `hunger false`, `allow-weather false`, `portal-form none`. Set `mv config first-spawn-override true` and `mv config first-spawn-location SpawnWorld`. Leave forced every-login destination disabled.
4. For SpawnWorld, set the namespaced gamerules `advance_time=false`, `advance_weather=false`, `spawn_mobs=false`, `random_tick_speed=0` and `fire_spread_radius_around_player=0` using `execute in minecraft:spawnworld run minecraft:gamerule <key> <value>`. Survival retains normal daylight and random ticks. Set `mv setspawn dev_world:-143.5,73,-143.5,0,0`. Set `respawn-world SpawnWorld` on dev_world, dev_world_nether and dev_world_the_end; retain bed/anchor respawn. Group-home priority is not implemented yet.
5. Stop Paper. Apply the two [WorldGuard templates](config/common/README.md) to their respective world's regions files and the MandoMC override to its config. Set `spawn-protection=0` so WorldGuard owns static protection. Preserve unrelated existing regions/configs.
6. Build/deploy MandoMC and restart. `/mandomc` must report both arrivals ready. A bad enabled arrival configuration blocks logins until repaired.

Paper 26.1.2 migrates the imported region data into `server/runtime/dev_world/dimensions/minecraft/spawnworld/`. Back up the entire runtime/world container and Multiverse/WorldGuard configuration, not just the leftover top-level SpawnWorld folder. Maps, generated/private configuration, installed plugins and player state are excluded from Git; reviewed shared configuration is tracked. The original logo is versioned at `docs/assets/server-icon.png` and copied into the runtime on startup.

## Repeatable arrival checks
Run `./scripts/build.sh`, then `./scripts/build-arrival-probe.sh`. With the server stopped, temporarily copy `.local/arrival-probe/ArrivalProbe.jar` to `server/runtime/plugins/ArrivalProbe.jar`. Start the local server and look for `ARRIVAL PROBE PASSED` in the log. The probe uses real WorldGuard/world data and non-op player proxies; it is not a Minecraft client or complete gameplay test. Stop Paper, remove that test JAR, and restart before ordinary play. Do not deploy the probe to a shared server.

The paid/commissioned assets are not a Git backup. Keep their originals and runtime backups separately. The tracked top-level `plugin/` directory is MandoMC source, distinct from ignored server-side `plugins/` installations.

Temporary interface note (2026-11-07): `/tutorial` and `/survival` are development-only commands. Their behavior is available for testing; the final onboarding/travel interface is undecided.

See [runtime configuration policy](runtime/README.md) and [world sources](../WORLD_SOURCES.txt).

## Spawn travel follow-up
`/spawn` now goes to SpawnWorld; temporary `/survival` goes to SurvivalWorld (the existing dev_world under that Multiverse alias). Both use the shared three-second countdown in `teleport.yml`, replacing the earlier five-second, one-way-only departure. Looking around is allowed; changing position cancels. See [command documentation](../docs/commands.md).

Future tutorial direction: `/tutorial` will show short text and take the player to a library building in SpawnWorld with takeable, feature-specific guidebooks. This is deferred; current tutorial text is temporary.

### Reuse the console tab
The managed console view stays open across automated restarts. Use `scripts/server-control.py` for all ordinary agent starts/stops; never ask the user to press Enter. The former restart-request marker/manual loop is retired. An old console tab running that earlier loop can be closed; new launches use the controller above.
