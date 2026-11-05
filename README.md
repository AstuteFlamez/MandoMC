# MandoMC
Minecraft Star Wars survival PvP server with player groups and claimed bases, developed locally in small playable slices. The group name and commands are being redesigned; see the player-experience document.

Main contains a minimal Paper plugin foundation. Legacy gameplay is preserved in separate recovery branches and is not enabled.

## Build
Requires Java 25. The script uses MANDOMC_JAVA_HOME, the project-local JDK when present, or JAVA_HOME.
```bash
./scripts/build.sh
```
Output: plugin/target/MandoMC.jar. The wrapper pins Maven. Paper/API versions are pinned in pom.xml and server/paper.lock.json. Building does not copy files to another server or start Minecraft.

## Start here
- [Current state](docs/status.md)
- [Player experience and feature order](docs/player-experience.md)
- [Gameplay architecture](docs/architecture.md)
- [Development workflow](docs/development.md)
- [Style guide](docs/style-guide.md)
- [Legacy feature recovery](docs/recovery.md)
- [Feature brief](docs/templates/feature.md)
- [FigJam board](https://www.figma.com/board/oCcBlAvyGEoYnQzK1pMYM6)

## Layout
```text
plugin/          Maven module producing MandoMC.jar
server/          Selected plugin roles, pinned Paper build, config templates
scripts/         Local development tools
docs/            Player design, architecture, style and resume notes
planning/        Diagram sources and legacy file inventory
.runtime/        Ignored server, worlds, logs and player data
.local/          Ignored tools, dependencies, paid binaries and secrets
```

[MandoPack](https://github.com/AstuteFlamez/MandoPack) remains separate. Future server releases pin its version. Maps and purchased binaries stay outside Git.

The original history is preserved. The full previous main is available at bilal/legacy-main-2026-11-05 and tag legacy/pre-rebuild-2026-11-05.
