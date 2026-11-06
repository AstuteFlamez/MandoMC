# Shared gameplay configuration
Place sanitized, intentional server-specific configuration here as each plugin is admitted.

Preserve vendor schemas and stable content IDs. Keep worlds, user/player data, generated caches, logs, database files and secrets out of this directory. Custom plugin defaults belong in plugin/src/main/resources; overrides here describe the development server's intended gameplay.

Public plugins are installed and boot-verified; gameplay integration is pending. Do not copy the old plugins directory wholesale.

The only initial vendor override is WeaponMechanics `Resource_Pack_Download.Automatically_Send_To_Player: false`. Apply that key within its generated config, preserving other vendor defaults. The new MandoMC pack will be served separately once validated.
