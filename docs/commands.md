# Commands and shared teleport behavior

## Command catalogue
| Command | Permission | Destination/behavior | Status |
| --- | --- | --- | --- |
| `/spawn` | `mandomc.travel.spawn` | SpawnWorld, `minecraft:spawnworld` | Current travel command |
| `/survival` | `mandomc.travel.survival` | SurvivalWorld, `minecraft:overworld` | Temporary entry point |
| `/tutorial` | `mandomc.arrival` | Replay current introductory text | Temporary implementation |
| `/mandomc give [player] [item]` | `mandomc.items.give` (op) | Give custom items or open recipient browser | Implemented |
| `/mandomc items reload` | `mandomc.items.reload` (op) | Validate and atomically reload item catalog | Implemented |
| `/mandomc` | `mandomc.admin.status` | Validate arrivals/status | Operator diagnostic |

SurvivalWorld is the Multiverse alias for the existing dev_world dimension. No terrain was regenerated or renamed on disk. SpawnWorld remains the recovered outpost. Commands resolve each world's configured spawn through ArrivalDestinations, preserving one coordinate authority.

## Add a command
1. Declare its name, usage, description and permission in `plugin.yml`. Give player-facing features their own permission; no blanket admin bypass of travel timing.
2. Register it in its feature's command class. `ArrivalCommands` is the first example. `bind` handles no-argument commands and returns usage for unexpected arguments. For a command with arguments/subcommands, use a dedicated CommandExecutor/TabCompleter in that feature, rather than extending a central command-name switch.
3. Keep parsing and permission decisions in the command handler. Put behavior in an injected feature service; do not put more gameplay logic into bootstrap.
4. For teleporting, pass the shared `TeleportService` a player and a destination supplier. The supplier must validate world, location and route policy, and is called at request and completion. A future home route must recheck membership/claim ownership at both points. Never start another warmup scheduler inside a command, GUI, terminal or tutorial.
5. Add meaningful route/failure tests, document its permission and update this catalogue. Command aliases must resolve to the same service. Check command ownership in Paper when vendors register similar names.

## Teleport contract
`TeleportService` owns one request per player across all routes and one main-thread tick task for all requests, combat expiry and arrival effects. Duplicate requests are refused without resetting the current timer. Disabled plugins cancel tasks and clear active titles; no requests survive restart.

Default countdown: three seconds (60 server ticks). At request, display 3; after one second, 2; then 1; teleport after the full delay. Tick lag can lengthen wall-clock duration, never shorten the required stationary period. Camera yaw and pitch are ignored. Walking, jumping, falling, knockback or accumulated position change beyond 0.001 blocks cancels, with a message explicitly saying the player moved. Damage, external teleport, death, logout, mounting, passengers and combat also stop/refuse travel. Position is compared against the original position, so tiny incremental movements cannot evade cancellation.

The destination is validated again before teleporting. An unsafe/missing destination or a cancelled Bukkit teleport never produces success titles or arrival effects. Successful arrival shows a short aqua title, a teleport sound and a bounded double-helix END_ROD spiral (two particles per tick; 30 ticks by default). The spiral stops if the player leaves, dies, changes worlds or moves far away. Countdown sounds are private; arrival sound/particles are visible nearby.

Configuration: `teleport.yml` owns schema version, warmup seconds (1..30), provisional PvP travel-block duration (1..300 seconds), effect toggle, volume (0..2), namespaced sound keys and spiral duration (1..100 ticks). `messages.yml` owns MiniMessage titles, subtitles and cancellation text; `<seconds>` is an unparsed placeholder. Bundled message defaults fill new keys without overwriting operator translations. Restart after changes. The former `arrival.warmup-seconds` key is removed/ignored; there is only one timer source now. A sanitized deployable example is in `server/config/common/teleport.yml`.

## Combat boundary
A provisional 30-second tag blocks voluntary MandoMC travel for both participants after uncancelled damaging Bukkit PvP hits, including player-fired projectiles. Reconnects retain the expiring in-memory tag. Environmental damage cancels a pending teleport but does not create a PvP tag. Future WeaponMechanics/custom-damage adapters must call `tagCombat(player)` for attributed hits that bypass Bukkit damage events. The full combat feature will own tagging and logout penalties; this travel module does not implement death-on-logout or promise coverage of future custom damage sources. Staff/vendor teleport commands are separate and require their own permission audit before public play.

## Tutorial direction — deferred
The eventual `/tutorial` will give short text and transport the player to a library building in SpawnWorld. Players can take specific guidebooks for individual features there. Implement the library destination, books and distribution much later; the current replayable text remains a temporary development aid. Reuse TeleportService for the future library trip. No library coordinates or book content have been invented.

## Design references
PirateCraft documents movement during the stationary period as a way to cancel teleports: https://wiki.piratemc.com/Commands . EssentialsSMP documents warmups for spawn and movement cancellation: https://wiki.chunklabs.net/essentials-smp/mechanics/teleportation.html . These are published precedents, not claims that their current visuals were tested. The colored countdown, sound progression and spiral are MandoMC's chosen presentation.

## Custom item administration
`/mmc` aliases `/mandomc`. `/mmc give` opens the category browser for yourself; `/mmc give <player>` opens it for an online recipient. `/mmc give <item>` gives yourself one; `/mmc give <player> <item>` gives that player one and works from console. With one argument, a registered item ID takes precedence over a matching player name. Vanilla/Essentials `/give` is unchanged.

The browser has All items and category pages, 45 entries per page, previous/next/back controls and a visible recipient. Click gives one; shift-click gives the item's actual maximum stack (one for robes, 64 for plasma cells). Inventory capacity is checked before delivery; insufficient space refuses the whole grant. Permissions and recipient availability are rechecked on every click. Menu previews never become granted items.

Root subcommands register a handler, permission and completer with `RootCommand`; feature code owns behavior. Add ordinary custom items in `plugins/MandoMC/items/*.yml`, then `/mmc items reload`. Invalid reloads preserve the previous catalog; successful reloads close old browsers. Vendor items are explicitly listed in `items.yml`, generated by their vendor API. See [Desert Robes](features/desert-robes.md).
