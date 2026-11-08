<p align="center"><img src="docs/assets/server-icon.png" alt="MandoMC server logo" width="128" height="128"></p>

# MandoMC
Minecraft Star Wars survival PvP server with player groups and claimed bases, developed locally in small playable slices. The group name and commands are being redesigned; see the player-experience document.

Main contains the minimal Paper plugin, a pinned local server setup, and the new resource-pack source. Legacy gameplay is preserved in recovery branches and is not enabled.

## Build
Requires Java 25. The script uses MANDOMC_JAVA_HOME, the project-local JDK when present, or JAVA_HOME.
```bash
./scripts/build.sh
```
Output: plugin/target/MandoMC.jar. The wrapper pins Maven. Paper/API versions are pinned in pom.xml and server/paper.lock.json. Building does not copy files to another server or start Minecraft.

## Run locally
Paper 26.1.2 build 74, Java 25. With the local runtime installed, run `./scripts/start-server.sh` or double-click `server/start.command`. Join `localhost` with Minecraft Java **26.1.2**. Type `stop` in the server console to save and shut down. See [local server setup](server/README.md).

Build the pack with `./scripts/build-resource-pack.py` (Python 3). Pack changes on main publish to GitHub Releases automatically; server startup synchronizes the newest compatible hosted build. See [pack sources and recovery](resource-pack/README.md).

## Start here
- [Current state](docs/status.md)
- [Player experience and feature order](docs/player-experience.md)
- [Proposed build order](docs/build-order.md)
- [First feature: arrival and protection](docs/features/arrival-and-protection.md)
- [Gameplay architecture](docs/architecture.md)
- [Development workflow](docs/development.md)
- [Style guide](docs/style-guide.md)
- [Legacy feature recovery](docs/recovery.md)
- [Feature brief](docs/templates/feature.md)
- [FigJam board](https://www.figma.com/board/oCcBlAvyGEoYnQzK1pMYM6)

## Layout
```text
plugin/          Maven module producing MandoMC.jar
server/          Version locks, config templates and start.command
server/runtime/  Ignored runnable server, plugin JARs, worlds and player data
resource-pack/   New pack source, recovery manifest and ignored build output
scripts/         Local development tools
docs/            Player design, architecture, style and resume notes
planning/        Diagram sources and legacy file inventory
.local/          Ignored tools, dependencies, paid binaries and secrets
```

The new pack is maintained in this repository. [MandoPack](https://github.com/AstuteFlamez/MandoPack) is the pinned legacy asset source; recover only the assets needed by each feature. Pack source textures are versioned; generated ZIPs, maps and plugin JARs stay outside Git.

The original history is preserved. The full previous main is available at bilal/legacy-main-2026-11-05 and tag legacy/pre-rebuild-2026-11-05.
