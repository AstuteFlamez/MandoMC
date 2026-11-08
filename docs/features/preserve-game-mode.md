# Feature: Preserve selected player game mode
Status: applied; event verification in progress

## Player experience
Logging in or changing worlds preserves the player's selected game mode. New players still start with the server's Survival default. WorldGuard continues to protect SpawnWorld and the survival landing.

## Cause and change
Paper already had `force-gamemode=false`. Multiverse had `world.enforce-gamemode=true`, and its pinned 5.8.2 player listener invokes the same game-mode/flight handler on both PlayerJoinEvent and PlayerChangedWorldEvent. Applied the supported console command `mv config enforce-gamemode false`; Multiverse reported success and persisted the value immediately. The shared override and fresh-server properties template now record this policy.

No periodic task or competing game-mode listener is introduced. [Multiverse configuration reference](https://mvplugins.org/core/reference/configuration-file/) documents the setting. Existing world default modes remain useful metadata, but no longer override an existing player on entry.

## Verification
Running-server command succeeded and saved `world.enforce-gamemode: false`. Live event checks are included in MobProbe with unregistered native test players; real client reconnect/world-change confirmation remains pending.
