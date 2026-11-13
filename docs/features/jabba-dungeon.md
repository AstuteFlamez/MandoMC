# Jabba's Palace first playable operation
Status: deployed; nine-room map walkthrough and loot/balance refinement remain playtests.

## Confirmed flow
All 22 configured supply caches appear when the event starts. The entrance blast door opens and room one's enemies spawn. Only the final owned enemy's death opens the next room; opening caches never changes progression. Bossk, Boba Fett and Rancor occupy rooms 4, 7 and 9. Killing Rancor completes the operation, but gates and remaining loot stay available until the scheduled event end. End/stop removes leftover caches and owned mobs, returns palace occupants outside, and restores the gates closed.

Caches retain the approved legacy one-click behavior: grant one to three rarity-weighted item rolls, then disappear. Remaining caches are removed at event end. Rolls are prepared once at spawn, so repeat clicks/offhand actions cannot reroll or duplicate loot. Inventory overflow drops at the player for shared pickup. Only configured live operation caches are intercepted; unrelated chests do not become loot sources.

The user superseded the earlier kill → spawn room caches → loot every cache → open next room proposal. Do not reintroduce loot-gated progression or keycards.

## Recovered content
Nine gate definitions (legacy IDs 15–23), nine rooms, 51 total enemies and all 22 original chest coordinates. Guard rooms keep three random legacy guards at each spawn and room-based guard levels. Bosses use their currently playtested definitions at level one. Chest positions are grouped by palace floor/route in configuration, but all spawn together; room group membership does not gate loot or progression.

The original seven JABBA-tagged components are restored: Fusion Coil Stabilizer, Plasma Capacitor Array, Precision Crafted Lens, Alloy Plating, Energy Conduits, Transparisteel Shard and Wiring Coil. Original IDs/rarities/artwork and per-item rarity weights are preserved: common 60, uncommon 25, rare 10, with one to three rolls per chest. No recipes are added by this slice.

## Messages and travel
Room messages go only to players inside the configured palace/entrance bounds, rather than the whole server. The exact unlock line is `Room <room> unlocked » blast doors opening`. Cache deployment is announced at start, not after a room kill. The final operation message says the palace remains open until the event ends. Event start/schedule announcements retain the existing server-wide event behavior.

Jabba is registered in the existing hourly event scheduler and `/events` GUI at original weight 20/cooldown two cycles. `/event start jabba`, `/event stop`, `/event next jabba` and enable/disable use the existing event authority. Current/next event clicks route Jabba to `/warp tatooine`; KOTH still routes to Hoth.

## Native blast doors
MandoMC animates actual legacy gate block states with temporary BlockDisplays, preserves collision while opening, then parks real panel blocks wherever the destination is free. Existing walls are not overwritten. Opening/closing/reset and animation cancellation require no BigDoors JAR, API, commands or database at runtime.

Private gate artwork is map data and remains excluded from Git at `plugins/MandoMC/jabba/doors.yml`. `scripts/import-jabba-doors.py LEGACY_ROOT OUTPUT_FILE` recovers the original panel block states from the supplied Tatooine region files and BigDoors SQLite metadata. The entrance gate was saved open: the database engine Y recovers its closed origin and original three-block upward movement. The legacy source is never edited. Shared configuration contains coordinates/movement only, not map artwork.

## Ownership, resets and recovery
Mythic owns mob AI/level and persistent entity lifecycle. MandoMC owns the single operation state, UUID-based kill accounting, cache claims, doors and cleanup. Foreign/duplicate deaths cannot advance a room. Operation mobs are tagged with their run identity and use NEVER despawn; palace chunks remain ticketed during the run. Unexpected disappearance aborts/reset the operation rather than awarding a room clear.

An atomic, private closed-state checkpoint is written before world edits and updated for spawned entity UUIDs. It includes original gate block states, clear/parking cells and every stash location. It is retained and replayed idempotently on startup/start so a crash during movement cannot strand barriers/open doors. Stale tagged mobs are removed when loaded. Stop/reset cancels animations and callbacks, clears caches/mobs and closes gates; a stopped opening cannot spawn a late wave. Native objective blocks resist breaking, placement, piston displacement and explosion edits. General Tatooine/Hoth WorldGuard setup remains deferred per user instruction.

## Assets, deployment and checks
Resource pack `resource-pack-26.1.2-5ce4bfd2dde0`, SHA-1 `3ffb9eb5e6115aac5067b3958e791720330596d5`: 425 files, 1,064,624 bytes. All previous 404 files are byte-identical. The only additions are the seven original loot component models/textures/dispatch files; no gate texture or BigDoors dependency is needed. Provenance is in resource-pack/jabba-loot-sources.json.

102 Java tests and 14 pack tests pass. The updated 62-check isolated live probe exercises all-caches-at-start, real legacy panel parsing, actual animated/physical gate movement, last-owned-kill progression with unopened caches, independent one-click/offhand/duplicate claims, correct boss spawning, final-room open state, end cleanup, interrupted-run recovery and animation cancellation. Palace coordinates are read-validated without destructive automated room completion. Full route/door presentation and item balance are player playtests.

Before scheduled closure, palace/entrance occupants receive the shared final-ten-second title countdown and event-name subtitle. Outside Tatooine players and distant Tatooine players are excluded; completing the rooms still leaves the operation open until its scheduled end.

## Cache interaction protection repair

Protected palace containers previously cancelled the native Bukkit interaction before the event's
`ignoreCancelled` loot listener ran. The active event now registers an `EventCaches` owner. Territory
protection permits only that owner's registered, still-present cache, main-hand right-click and
non-Spectator claimant. Other protection denials remain authoritative. Unregistered and decorative
containers retain their protection; coordinates, room progression and the migration transform are unchanged.

Rewards are staged into a copy of the player's storage inventory before committing. An incomplete fit
leaves inventory, chest and the original rolled reward unchanged. Delivery failures roll back inventory;
no overflow drops are created. Successful claims remove ownership and the chest once. Event cleanup and
restart recovery retain their existing journal/reset ownership.

Build with `scripts/build.sh`. The disposable `scripts/build-jabba-cache-probe.sh` native integration probe
must run only in a private copy of the current runtime/map. It uses `ServerPlayerGameMode.useItemOn`,
including the actual Bukkit listener chain, rather than directly invoking the loot handler. Run with
`-Dmandomc.cache.reproduce=true` to reproduce against the previous runtime JAR; normal mode verifies the
fix, exact rewards, cancellation boundaries, capacity/failure retry, duplicates, recovery and reset cells.
Never install this probe on the shared player server: it starts/stops events and consumes private caches.
