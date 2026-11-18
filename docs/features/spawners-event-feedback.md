# Feature: Spawners and gameplay feedback
Status: playable in disposable verification
Branch: bilal/spawners-event-fixes

## Player experience
The former Minerals gray Jawa at Batuu is now Spawners. Stock order is Massiff, Dewback, Bantha, Jawa. Starting unit prices are 100,000 / 150,000 / 200,000 / 300,000 credits, configured in `mobs/jawas.yml`.

Gray-Jawa left-click buys one item. Shift-left-click buys the item's full native stack size, at 64 times the unit price for stackable goods. The inventory stays open on success and failure and its credit display updates after commitment. Existing bundle prices are divided by bundle quantity in integer cents, rounding up; brown component offers retain their configured bundles. All supply delivery continues through durable auction escrow, including insufficient funds, disconnects and full-inventory claims.

Spawner blocks retain their type across chunk unload/restart. A nearby Survival/Adventure player activates one spawn attempt every 30 seconds, with a shared nearby cap of six of the same creature. All four use the existing themed mobs and loot. Suitable ground and clear space are required. Spawners can be placed in unclaimed Wilderness and your own clan’s claims, including Hoth. Hoth’s natural-mob suppression remains in force; only the explicitly authorized spawner creature is permitted. Break with any pickaxe to recover one spawner; no Silk Touch requirement. Other tools cannot destroy it. Creative removal gives no item. Spawn eggs cannot change its type. Ordinary protection cancellations remain authoritative. Each placed cage shows a rotating miniature of its matching existing creature model and a `1x Massiff`/`Dewback`/`Bantha`/`Jawa` hologram. The native pig preview is cleared from items and placed blocks; existing items and loaded spawners migrate automatically. Preview entities have no AI, hitbox, loot or persistence, appear within 32 blocks of a player, and are removed on break, unload or shutdown.

KOTH announces victory globally and delivers a physical key immediately. Full inventories retain delivery for `/crates claim`. Existing virtual keys remain usable, consumed before physical keys. Clan recipient selection remains one random eligible winning member inside the hill.

When Jabba ends, its mobs disappear immediately. Open doors, caches and occupants remain for 300 seconds before extraction and gate/cache reset. Finishing the final room also begins this exit window. Opening animations finish without spawning another wave. New palace starts and event reloads are blocked while extraction is pending. Shutdown and error recovery clean up immediately; the existing journal retains interrupted cleanup.

Player melee and gun kills of themed mobs drop native XP orbs. Small wildlife gives 3 XP, Massiffs/brown Jawas 8, Tuskens 10, palace guards 12, Bossk/Boba 100 and Rancor 200. Values are configurable in `mobs/experience.yml`. Environmental and non-player kills give no custom XP; cleanup despawns never generate death rewards. Vanilla creatures retain their native XP.

Decoration placement/return messages use the saved item's actual display name. The stonecutter shows only the conversions allowed for the exact current saber, with synchronized client/server indexes and preserved vanilla stonecutting. The enchanting-table recipe accepts two Durasteel ingots, one book and four obsidian in the normal layout and appears in `/recipes`.

Coal is added to cave walls in the four resource planets: Tatooine, Hoth, Mandalore and Naboo. Batuu is a protected hub and Ilum is a protected challenge rather than a resource planet. Ordinary coal mining, Fortune, Silk Touch and fuel behavior remain native.

## Ownership and integrations
MandoMC owns spawner block identity, timers and population limits; MythicMobs owns each creature's model/combat/loot. Territory/clans remain the edit authority. Auction escrow owns shop payments/delivery. CrateService owns keys and pending rewards. Jabba owns its delayed cleanup task, gate tickets and journal. LandRestoration owns restoration and applies the separate coal overlay when loading immutable originals.

## Map migration
Run `scripts/add-planet-coal.py` using the pinned map-tools requirements, with `--runtime` and a new `--output` directory. The default is a dry run. `--apply` acquires native world session locks, validates target states against frozen wilderness originals, excludes staff edits, claims, safe zones, structure columns and existing ores, backs up changed region files and writes a separate `data/coal-terrain.yml` overlay. No missing chunks are generated. Original frozen baseline files, journals and scopes remain unchanged. The overlay is required alongside the map changes to retain coal during land restoration. A completed migration is idempotent. Deploy maps/overlay only from a stopped, consistent backup; routine JAR/config upload does not carry them.

## Verification
`./scripts/build.sh` passes 384 Java tests; all 95 tooling/map checks pass. The combined Paper fixture passes 252 assertions, and an actual JVM restart passes 10 more for persisted spawner species and resumed tracking. The palace check verifies a real 6,000-tick scheduled task and invokes its callback on the server thread, rather than waiting five wall-clock minutes. The saber check round-trips the actual recipe packet and compares every permitted option against native selection.

Unit tests cover proportional purchasing, spawner ordering/pricing, physical KOTH config and legacy source guidance, recipe resolution, coal overlay conflict/staff handling, cave depth/structure/ore exclusions and migration idempotence. A disposable Paper fixture covers real crafting, shop transactions, themed spawns, protection/recovery events, global KOTH delivery and palace cleanup.

The private map rehearsal adds 2,125 Hoth, 12,503 Mandalore, 12,053 Naboo and 7,053 Tatooine coal blocks. Region records round-trip before any write; every changed file has a retained prior copy. Live deployment and client playtesting remain pending.

## Next action
Stage the tested release and matching stopped-world coal migration for development playtesting.
