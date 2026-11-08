# Shared runtime configuration
Tracked: Bukkit, command aliases, Spigot, permissions, and Paper world defaults. These reviewed files contain no player data or credentials.

`server.properties.template` and `config/paper-global.yml.template` are sanitized examples. Their live counterparts stay ignored because they can hold RCON/proxy secrets. Copy templates only during initial setup, preserving existing local settings. Do not put credentials into tracked files or templates.

Worlds (regardless of folder name), installed `plugins/` folders, JARs, logs, caches, libraries, player/operator/ban/whitelist data, local history, downloaded packs and other generated files remain ignored by default. Explicitly allowlist new shareable config files after review. MandoMC's top-level `plugin/` source remains tracked.

Intentional plugin configuration is stored outside installed plugin folders in `server/config/common/`; never force-add runtime plugins. Map acquisition and replacement notes are in the root `WORLD_SOURCES.txt`. `/tutorial` and `/survival` are temporary development commands, pending the final onboarding and travel interface.
