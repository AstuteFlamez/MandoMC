# Local Paper server
The installed development server runs from `server/runtime/`, outside Git. Plugin source is in `plugin/`; pack source is in `resource-pack/`.

## Start and join
From the repository root:
```bash
./scripts/start-server.sh
```
On macOS, double-click `server/start.command` for a Terminal console. Join `localhost` using **Minecraft Java 26.1.2**. The server binds to `127.0.0.1:25565` with online-mode enabled. It has a fresh `dev_world`, 20 slots, and a 4 GiB maximum heap. Override the heap with `MANDOMC_MAX_MEMORY=6G` if needed. Java 25 is selected by `scripts/java-env.sh`. Python 3 is also required: startup synchronizes the hosted resource pack before launching Paper.

Type `stop` in the server console to save and exit. Use full restarts for plugin changes. Do not run two copies against the same runtime.

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

All 12 plugins enabled together. Verified `/mandomc`, console plugin list, FactionsUUID list, the EssentialsX economy bridge through Vault, clean stop/restart and a Minecraft protocol status request. The GitHub pack publication also passed, its ZIP downloaded anonymously with matching hashes, and Paper restarted with required-pack delivery configured. No authenticated client join, pack rendering or multiplayer gameplay test has been performed yet.

Expected startup notices: newer Minecraft/Paper is available; ModelEngine has no MineSkin API key for optional skin generation; Java reports a third-party Unsafe deprecation. These did not prevent startup.

## What is ready
This is a development boot with fresh vendor defaults, not the designed MandoMC gameplay. SpawnWorld, tutorials, custom weapons, group branding, pairwise raids, events and vehicles still need implementation/configuration. Default FactionsUUID rules and commands are temporary vendor behavior. WorldGuard is loaded but no MandoMC safe zones have been created. Do not treat this runtime as a public playtest build.

EssentialsX is the credit balance provider. No EssentialsChat/Spawn/Protect modules were installed. Other plugins' kits, travel, selling and raid defaults must be restricted when their feature slices are implemented.

WeaponMechanics' `Resource_Pack_Download.Automatically_Send_To_Player` is set to false in its generated runtime config. Keep this intentional override when restoring configs; the new MandoMC pack will own presentation. The [MandoMC pack](../resource-pack/README.md) is published through GitHub Releases and applied through server.properties. Startup synchronizes its URL, SHA-1, stable UUID and required-pack prompt. Minecraft downloads the pack directly from GitHub when a player accepts it. Wait for the publishing workflow, then restart Paper to adopt updated assets; reconnect to apply them.

## Change or restore content
Stop the server, back up `server/runtime/`, then restore one plugin config or map at a time. Never copy old JARs over the pinned stack. Check migrations and restart logs after each restoration. World upgrades cannot be undone by merely swapping server JARs.

To deploy a new MandoMC build while stopped:
```bash
./scripts/build.sh
cp plugin/target/MandoMC.jar server/runtime/plugins/MandoMC.jar
./scripts/start-server.sh
```

The runtime itself is not a Git backup. A clean clone needs Java 25, the Paper artifact saved as `server/runtime/paper.jar`, and each locked vendor artifact in `server/runtime/plugins/`. Build/copy MandoMC, copy the local server.properties template on initial setup, and accept Minecraft's EULA before starting. Downloaded plugin dependencies and defaults are generated on the first boot. Do not overwrite an existing server.properties or world with setup templates.
