# Development
## One playable change at a time
1. Describe the player outcome using docs/templates/feature.md.
2. Set its rules and smallest playable content example.
3. Create bilal/<feature> from current main.
4. Implement the necessary system and parameters; adapt useful legacy pieces.
5. Include public-plugin config, validation and docs with the code.
6. Build, exercise it in-server and adjust its feel with the user.
7. Merge the satisfactory feature and start the next from the integrated main.

Recovery branches hold reference material, not ready-to-merge implementations; see recovery.md. Main currently has no gameplay modules.

## Local build
Run ./scripts/build.sh to verify the Maven reactor and produce plugin/target/MandoMC.jar. Select Java 25 using MANDOMC_JAVA_HOME, the local JDK, or JAVA_HOME. Wrapper and dependency downloads stay under ignored .local.

No automatic copy-to-server step exists. Runtime files live in `server/runtime/`, separately from `plugin/` and `resource-pack/`. See [server instructions](../server/README.md). Bind the laptop server to loopback until access is intentionally changed. Stop it before switching deployed code/configs; branches do not preserve runtime world/player state. Use restarts for code/vendor changes, not Bukkit /reload.

Build pack sources with `./scripts/build-resource-pack.py`; output stays in ignored `resource-pack/build/`. Source models, textures and provenance live on main alongside code. Validate model appearance in a matching Minecraft client before calling an asset complete.

## Done means
Build and meaningful tests pass; the player behavior and failure cases work; runtime-dependent integrations are checked in Paper; configs/migrations/docs are included; no generated data, paid binaries, secrets or local paths are committed. Incorporate play feedback before finishing a gameplay feature.

For the scaffold, packaging and metadata checks establish a buildable baseline, not gameplay compatibility.

## Pause and resume
Checkpoint commits on unfinished feature branches are fine. Update docs/status.md with branch, working behavior, verification, unfinished work and ONE next action. Pausing cleanly does not require finishing a whole feature.

## Configuration and updates
Defaults: plugin/src/main/resources. Sanitized server overrides: server/config/common. Local templates: server/config/local. Runtime data and secrets stay ignored.

Track latest stable Minecraft through an upgrade branch and pin the tested Paper/API/JDK/plugin/pack tuple. Back up player/world state before upgrades; older JARs alone cannot safely downgrade worlds.

Replace a public plugin for a concrete benefit with an explicit content/data migration plan.
