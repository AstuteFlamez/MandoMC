# Commands and shared teleport behavior

## Native administration and personal travel
Essentials and Multiverse are replaced by MandoMC. See [native utilities](features/native-utilities.md) for syntax, limits, permissions and migration.

| Commands | Access | Behavior |
| --- | --- | --- |
| `/flyspeed`, `/fly`, `/speed` | Operator | Flight and movement speed |
| `/gm`, `/gm0`–`/gm3`, `/gms`, `/gmc`, `/gma`, `/gmsp` | Operator | All four game modes |
| `/heal`, `/feed`, `/god`, `/vanish` | Operator | Health, hunger and moderation |
| `/invsee`, `/enderchest` (`/ec`) | Operator | Live online inventory inspection/editing |
| `/time`, `/weather` | Operator | World time/weather; weather locks remain |
| `/tp`, `/tppos`, `/tphere`, `/tpall`, `/mvtp` | Operator | Immediate direct teleports; normal combat/cancellation still apply |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel` | Everyone | Expiring player consent, then shared warmup |
| `/home`, `/homes`, `/sethome`, `/delhome` | Everyone | Three persistent named homes by default |
| `/back`, `/tpcancel` | Everyone | Previous teleport/death return; cancel own countdown |

Operator nodes use `mandomc.admin.<command>` (`gamemode` for all mode aliases); targeting others needs `mandomc.admin.others`. Public nodes use `mandomc.travel.<command>`. Public homes, requests and return trips use instant travel in Creative and a three-second warmup in other modes and revalidate the destination. Admin direct commands use their explicit immediate path.

## Command catalogue
| Command | Permission | Destination/behavior | Status |
| --- | --- | --- | --- |
| `/warp [batuu|earth|hoth|tatooine]` | `mandomc.travel.warp` | Galaxy menu/direct travel; aliases `/warps`, `/travel` | Implemented |
| `/event` | `mandomc.event` | Current/next event and selection chances; alias `/events` | Implemented |
| `/event start koth`, `stop`, `forceend`, `next koth`, `reroll`, `status`, `list`, `reload`, `enable koth`, `disable koth` | `mandomc.event.admin` (op) | Manage shared event lifecycle; reload/enable/disable only while idle | Implemented |
| `/spawn` | `mandomc.travel.spawn` | SpawnWorld, `minecraft:spawnworld` | Current travel command |
| `/survival` | `mandomc.travel.survival` | SurvivalWorld, `minecraft:overworld` | Temporary entry point |
| `/tutorial` | `mandomc.arrival` | Replay current introductory text | Temporary implementation |
| `/mandomc give [player] [item]` | `mandomc.items.give` (op) | Give custom items or open recipient browser | Implemented |
| `/mandomc items reload` | `mandomc.items.reload` (op) | Validate and atomically reload item catalog | Implemented |
| `/mandomc` | `mandomc.admin.status` | Validate arrivals/status | Operator diagnostic |

SurvivalWorld is the MandoMC world alias for the existing dev_world dimension. No terrain was regenerated or renamed on disk. SpawnWorld remains the recovered outpost. Commands resolve exact arrivals from MandoMC worlds.yml through ArrivalDestinations, preserving one coordinate authority.

## Add a command
1. Declare its name, usage, description and permission in `plugin.yml`. Give player-facing features their own permission; only Creative skips the public countdown.
2. Register it in its feature's command class. `ArrivalCommands` is the first example. `bind` handles no-argument commands and returns usage for unexpected arguments. For a command with arguments/subcommands, use a dedicated CommandExecutor/TabCompleter in that feature, rather than extending a central command-name switch.
3. Keep parsing and permission decisions in the command handler. Put behavior in an injected feature service; do not put more gameplay logic into bootstrap.
4. For teleporting, pass the shared `TeleportService` a player and a destination supplier. The supplier must validate world, location and route policy, and is called at request and completion. A future home route must recheck membership/claim ownership at both points. Never start another warmup scheduler inside a command, GUI, terminal or tutorial.
5. Add meaningful route/failure tests, document its permission and update this catalogue. Command aliases must resolve to the same service. Check command ownership in Paper when vendors register similar names.

## Teleport contract
`TeleportService` owns one request per player across all routes and one main-thread tick task for all requests and arrival effects. Duplicate requests are refused without resetting the current timer. Disabled plugins cancel tasks and clear active titles; no requests survive restart.

Creative travellers teleport immediately after normal validation and consent; switching into Creative during a pending trip completes it on the next tick. Other modes use a three-second countdown (60 server ticks). At request, display 3; after one second, 2; then 1; teleport after the full delay. Tick lag can lengthen wall-clock duration, never shorten the required stationary period. Camera yaw and pitch are ignored. Walking, jumping, falling, knockback or accumulated position change beyond 0.001 blocks cancels, with a message explicitly saying the player moved. Damage, external teleport, death, logout, mounting, passengers and combat also stop/refuse travel. Position is compared against the original position, so tiny incremental movements cannot evade cancellation.

The destination is validated again before teleporting. An unsafe/missing destination or a cancelled Bukkit teleport never produces success titles or arrival effects. Successful arrival shows a short aqua title, a teleport sound and a bounded double-helix END_ROD spiral (two particles per tick; 30 ticks by default). The spiral stops if the player leaves, dies, changes worlds or moves far away. Countdown sounds are private; arrival sound/particles are visible nearby.

Configuration: `travel/teleport.yml` owns schema version, warmup seconds (1..30), effect toggle, volume (0..2), namespaced sound keys and spiral duration (1..100 ticks). `language/messages.yml` owns MiniMessage titles, subtitles and cancellation text; `<seconds>` is an unparsed placeholder. Bundled message defaults fill new keys without overwriting operator translations. Restart after changes. The former `arrival.warmup-seconds` key is removed/ignored; there is only one timer source now. A sanitized deployable example is in `server/config/common/mandomc/travel/teleport.yml`.

## Combat boundary
CombatService owns one 15-second mob/PvP tag shared by travel, sidebar and logout death. Uncancelled positive-damage attributed hits refresh it for involved players, including WeaponMechanics E-11. The sidebar counts down with one decimal. Environmental damage cancels travel without tagging. External voluntary Bukkit teleports are also denied while tagged. Tagged logout causes death and full carried-item drops; clean server shutdowns are exempt. `travel/teleport.yml` no longer owns `combat-block-seconds`; delete that old key and use `combat/combat.yml:tag-seconds`. Future damage paths that bypass Bukkit events must integrate with CombatService. See [combat basics](features/combat-basics.md).

## Tutorial direction — deferred
The eventual `/tutorial` will give short text and transport the player to a library building in SpawnWorld. Players can take specific guidebooks for individual features there. Implement the library destination, books and distribution much later; the current replayable text remains a temporary development aid. Reuse TeleportService for the future library trip. No library coordinates or book content have been invented.

## Design references
PirateCraft documents movement during the stationary period as a way to cancel teleports: https://wiki.piratemc.com/Commands . EssentialsSMP documents warmups for spawn and movement cancellation: https://wiki.chunklabs.net/essentials-smp/mechanics/teleportation.html . These are published precedents, not claims that their current visuals were tested. The colored countdown, sound progression and spiral are MandoMC's chosen presentation.

## Custom item administration
`/mmc` aliases `/mandomc`. `/mmc give` opens the category browser for yourself; `/mmc give <player>` opens it for an online recipient. `/mmc give <item>` gives yourself one; `/mmc give <player> <item>` gives that player one and works from console. With one argument, a registered item ID takes precedence over a matching player name. Vanilla `/give` is unchanged.

The browser has All items and category pages, 45 entries per page, previous/next/back controls and a visible recipient. Click gives one; shift-click gives the item's actual maximum stack (one for robes, 64 for plasma cells). Inventory capacity is checked before delivery; insufficient space refuses the whole grant. Permissions and recipient availability are rechecked on every click. Menu previews never become granted items.

Root subcommands register a handler, permission and completer with `RootCommand`; feature code owns behavior. Add ordinary custom items in `plugins/MandoMC/items/definitions/*.yml`, then `/mmc items reload`. Invalid reloads preserve the previous catalog; successful reloads close old browsers. Vendor items are explicitly listed in `items/catalog.yml`, generated by their vendor API. See [Desert Robes](features/desert-robes.md).

Vader's first saber is available as `/mmc give vader` (same give permission), or under Weapons. Main-hand left click attacks, right click blocks/reflects, sneak + left click throws. See [Vader's Lightsaber](features/vader-lightsaber.md).

Cycler rifle: `/mmc give cycler_rifle`, also in Weapons. Right click fires; left click scopes; the configured drop key reloads. Mob definitions: `TuskenRaiderSniper`, `TuskenRaiderMelee`, `Bantha`, `Dewback`, `Massiff`; use MythicMobs administrator commands for isolated model/combat checks. Natural spawns are limited to selected sandy SurvivalWorld biomes. Drops are not implemented yet.

## Legacy KOTH/events and current-world warps
`/warp` has exactly Batuu / Black Spire Outpost (SpawnWorld), Earth (the existing survival world) recovered Hoth and Tatooine. The legacy direct aliases `blackspireoutpost`, `spawnworld`, `survival` and `survivalworld` resolve to the same protected world-spawn routes. GUI and event-menu travel delegates to TeleportService, including its combat and movement restrictions. Tatooine is a combat-enabled dungeon test destination; its landing must still have safe standing space. Ilum, Alderaan and other legacy worlds remain unavailable.

`/events` retains current/next/chances, with only KOTH registered. The legacy hourly schedule uses America/Chicago: :50 end warning, :55 forced end/next warning, :00 start. No missed event is replayed on startup. `/event start koth` begins an immediate round; only one event can run. KOTH is currently solo, with a three-minute continuous capture and no rewards. Enemies entering do not reset a holder who remains on the hill. Clans and rewards wait for their own decisions. See [KOTH and warps](features/koth-and-warps.md).

`/event reload` validates replacement event configuration and refuses while active; invalid data preserves the working definitions. This is a feature-level config reload, not Bukkit/plugin reload and does not reload saber settings. Warp presentation and code changes still require restart.

The galaxy warp menu uses the recovered custom planet icons. Earth is isolated in the upper-left Unknown Regions by the user’s choice, Batuu sits west, and Hoth sits southeast of Batuu. `/warp batuu` is the public spawn destination; `/warp spawn` and the old world-name aliases still work.

Jabba is now a registered scheduled event: `/event start jabba` starts the shared palace operation and `/event stop` resets it. Its current/next GUI entries travel to Tatooine. All caches spawn at start and only room kills unlock gates. See [Jabba operation](features/jabba-dungeon.md).

## Identity and credits
- `/sb` or `/scoreboard`: toggle the full sidebar for this session (`mandomc.hud`).
- `/balance [player]`, `/bal`, `/money`: view credits (`mandomc.credits.balance`).
- `/pay <player|UUID> <amount>`: transfer credits (`mandomc.credits.pay`).
- `/balancetop`, `/baltop`: ten largest balances (`mandomc.credits.top`).
- `/credits give|take|set <player|UUID> <amount>`: operator/MandoStudios balance adjustment (`mandomc.credits.admin`).
- `/mmc rank list|get <player|UUID>|set|add|remove <player|UUID> <rank>`: operator/MandoStudios rank management (`mandomc.ranks.manage`).

Rank IDs: `mandostudios`, `mod`, `builder`, `creator`, `mandalore`, `marshal`, `warrior`, `default`. UUIDs support offline changes; names must already be known to the server. Monetary values use at most two decimals, with no self-payment or overdrafts. Fresh credit balances are zero.

Native auction house: `/ah` (`/auctionhouse`, `/auction`) browses player listings; `/ah sell <price> [amount]` lists the held quantity for a total stack price; `/ah selling` manages your listings; `/ah collect` reclaims items; `/ah search <name>` filters item or seller names. Price/newest sorting is in the menu. Normal players have `mandomc.auction`; Creative, Spectator and combat-tagged actions are denied. Eight listings, 48 hours, no fees/tax, permanent claims. No new plugin or currency.

Quantity update: `/mmc give <item> [quantity]` and `/mmc give <player> <item> [quantity]` accept a trailing whole number from 1 to 2304. Default remains one. Console uses the target form. Factory-generated items split into valid stacks; insufficient storage refuses the whole grant. Existing permission/browser behavior remains.
