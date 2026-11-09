# Feature: Native worlds, administration and personal travel
Status: playable
Integration: main; native utilities extracted separately from the active dungeon checkout.

## Player experience
Operators retain the selected Essentials/Multiverse commands directly in MandoMC. Players can request consent-based teleports, save up to three named homes and return to their previous teleport/death location. Public trips retain shared travel checks. Creative mode skips the countdown; other modes wait three seconds.

## Rules
- `/flyspeed <0-10> [player]`; `/speed [fly|walk] <0-10> [player]`. One means the normal native speed, zero stops movement in that mode. `/fly [on|off] [player]` toggles flight.
- `/gm <0|1|2|3|survival|creative|adventure|spectator> [player]`; `/gm0`–`/gm3`, `/gms`, `/gmc`, `/gma`, `/gmsp` are shortcuts.
- `/heal [player]` restores maximum health/food and clears fire, freezing and potion effects. `/feed [player]` restores food.
- `/god [on|off] [player]` and `/vanish [on|off] [player]` persist in native player data. Vanish hides entity/tab visibility from ordinary players, suppresses join/quit announcements and marks the player ineligible for KOTH. `mandomc.admin.vanish.see` lets staff see hidden players. It does not suppress the aggregate server player count.
- `/invsee <player>` exposes all 41 live inventory slots (36 storage, boots/leggings/chest/helmet, offhand); `/enderchest [player]` (`/ec`, `/echest`) exposes the online player's ender chest. Left-click swaps from the authoritative inventory; ordinary lower-inventory left/right clicks work. Shift-click, hotbar transfers and drag edits are denied. Death/logout or permission loss closes the view. Offline inventories are out of scope.
- `/time [set|add|query] <value> [world]`; `/weather <clear|rain|thunder> [seconds] [world]`. World weather locks are respected. Console must specify a world.
- `/tp <player>`, `/tp <player> <destination-player>`, `/tp [player] <x> <y> <z> [yaw pitch]`, `/tppos` with the coordinate form, `/tphere <player>`, `/tpall [destination-player]`, `/mvtp [player] <world>` are immediate operator tools. Relative `~` coordinates work; entity selectors/local `^` coordinates are outside this interface. Normal Bukkit cancellation, world borders, world height, mounts and combat still apply.
- Operator command nodes are `mandomc.admin.<command>`, with all game-mode variants using `mandomc.admin.gamemode`. Targeting others additionally requires `mandomc.admin.others`. Defaults are op. Existing vanilla/vendor namespaced commands remain available; bare requested labels belong to MandoMC.
- `/tpa <player>` asks to visit; `/tpahere <player>` asks them to visit you. `/tpaccept [player]`, `/tpdeny [player]`, `/tpacancel` manage consent. Multiple incoming requests require an explicit sender on acceptance/denial. Requests expire after 60 seconds; a sender has one outstanding request and a five-second send cooldown. Each recipient accepts at most ten pending requests. Death/logout cancels requests.
- Acceptance consumes the request exactly once and uses shared travel: Creative travellers move immediately; other modes wait three seconds. Both players must be online, living, visible to each other, unmounted and out of combat; destination and permissions are checked again at completion. `/tpcancel` cancels the travelling player's countdown.
- `/sethome [name]`, `/home [name]`, `/delhome [name]`, `/homes`. Omitted names use `home`. Names are case-insensitive, 1–24 letters/numbers/underscores/hyphens. Updating a name does not consume another slot. `mandomc.travel.homes.unlimited` exempts the default three-home limit (op).
- `/back` returns to the last successful command/plugin teleport departure or death location; successful returns update the reverse trip. Cancelled teleports, ender pearls and combat movement corrections do not replace history. Unsafe death/home destinations are refused; this grants no safe-zone, item-recovery or protection bypass.
- Public permission nodes are `mandomc.travel.<command>` and default true. Saved homes and back locations use stable dimension keys and exact coordinates/facing in native player persistent data. Clean shutdown/restart preserves them. Pending consent/countdowns never survive restart. Changing into Creative during a pending countdown completes it on the next travel tick.
- Beds remain the first valid respawn choice; otherwise deaths return to Batuu. New players start at Batuu; returning players keep their saved location and game mode. Nether/End stay disabled.

## Smallest playable version
All existing worlds and all three public warps load without either removed plugin; an ordinary player can set a home and accept another player's request, then return with `/back`. Admin commands work through their bare aliases.

Credits, shops and a new economy provider remain deferred by the user. Vault stays installed as an inactive bridge. Clans/claims remain deferred. No new planet content or resource-pack changes.

## Ownership and integrations
WorldService reads `worlds.yml`, loads existing migrated Paper dimensions by stable key and refuses to generate a replacement when dimension data is missing. It owns aliases, precise arrival coordinates, PvP/difficulty and weather locks. Existing native world rules and terrain remain intact. WorldGuard still owns region protection.

TeleportService remains the sole public countdown owner. PersonalTravel stores home/back data; TeleportRequests stores only ephemeral consent. AdminService owns operator actions and visibility; normal Paper events remain authoritative. CommandBindings explicitly selects MandoMC's bare labels where WorldEdit/vanilla register duplicates.

Migration: copy the four integrated Multiverse entries into `worlds.yml`, including exact spawn position/facing, aliases and flags. Keep disabled dimensions absent. Stop the server, back up world/player data and plugin settings, replace MandoMC and move the two vendor JARs out of the plugin search path. Retain their configuration/userdata for rollback; existing Essentials balances stay archived and inactive. No existing Essentials homes were present at inspection. New MandoMC return history and god/vanish markers start with this feature. Old vendor implementation is removed from source and plugin lock/catalog.

## Parameters
| Key | Meaning | Default/range | Application |
| --- | --- | --- | --- |
| utilities.yml `homes-limit` | Personal homes, excluding explicit unlimited permission | 3 / 1–100 | Restart |
| `request-expiry-seconds` | Consent lifetime | 60 / 5–300 | Restart |
| `request-cooldown-seconds` | Sender spam guard | 5 / 1–60 | Restart |
| `max-incoming-requests` | Recipient pending cap | 10 / 1–100 | Restart |
| worlds.yml `worlds.<id>.key` | Existing Paper dimension | Stable namespaced key | Restart |
| `aliases`, `spawn`, `weather`, `pvp`, `difficulty` | Name resolution, exact arrival and world flags | Migrated server configuration | Restart |

Both configuration files use schema version 1. Messages fall back to bundled `messages.yml:utilities`, preserving local translations. Personal persisted keys carry version 1.

## Verification
The integrated main build passes 81 Java tests, including input bounds, nonfinite coordinates/speeds, game-mode aliases and location serialization. `./scripts/build-utilities-probe.sh` builds the real Paper verification harness. The same integrated JAR passes 168 focused utility assertions and 54 arrival/WorldGuard regression assertions in an isolated Paper copy. All four configured worlds load with both removed vendors absent and no startup errors. The tests preserve exact Hoth arrival/facing; Tatooine is excluded with Jabba. Native PDC serialization verifies saved homes/history; no human client rendering test is claimed. The earlier five-world development version had 99 Java tests and 170 utility assertions, including excluded dungeon-related coverage. See docs/status.md for integration and deployment boundaries.

API references: [Paper WorldCreator](https://jd.papermc.io/paper/26.1.2/org/bukkit/WorldCreator.html), [async initial spawn event](https://jd.papermc.io/paper/26.1.2/io/papermc/paper/event/player/AsyncPlayerSpawnLocationEvent.html), [command map](https://jd.papermc.io/paper/26.1.2/org/bukkit/command/CommandMap.html).

## Pause note
Deployed with a full runtime backup and clean normal restart. Next action: reconnect and client-check Creative instant travel plus personal home/death-return commands.
