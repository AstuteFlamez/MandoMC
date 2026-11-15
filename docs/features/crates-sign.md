# Feature: Crates building sign
Status: implemented and native-tested; development installation pending
Branch: bilal/crates-sign

## Player experience
Staff obtain a Crates Sign using `/mmc give crates_sign` or `/mmc give <player> crates_sign <quantity>` (the existing `mandomc.items.give` permission). Right-click a block face to place it. The clicked face determines the outward direction on walls; floor and ceiling placement stay upright and face the player. The placement cell is the middle of the three-block span. Break any part to recover the original item.

## Rules
The sign is three blocks wide and one block tall, with three full collision blocks behind one thin visual. All three cells must be clear, loaded, inside the world border and editable by the placer. Existing native territory, clan and Bukkit block-event protection applies independently to each cell for both placement and recovery. No crafting recipe, mob drop, event reward or shop supply is registered. The admin give browser is part of `/mmc give`.

One item is consumed per placement, including Creative placement. Returning a placed sign preserves its metadata and yields one item. If inventory is full, recovery waits safely in the existing custody journal. Explosions and pistons cannot move individual parts. If terrain restoration removes a part, the remaining owned parts are removed and the item is returned to the owner; unrelated replacement blocks are preserved. Unloaded parts are cleaned when their chunks load.

## Smallest playable version
One `crates_sign` item using the supplied six-element Java Blockbench model and embedded 128×128 texture. There is no text editor or crate-opening interaction.

## Ownership and integrations
`ItemCatalog` owns the item identity. The existing `RancorTrophies` service owns placement, visual lifecycle, protection and inventory receipts. `TrophyJournal` schema 3 persists the width and reserves all occupied cells; schemas 1 and 2 load as single-block decorations without changing skull/helmet custody. Paper renders one item display with the NONE transform; no ModelEngine blueprint is needed for this static model.

## Parameters
- Item: `crates_sign`; model: `mandomc:decor/crates_sign`; carrier: Paper.
- The supplied model geometry is retained at full size (48×16 model units); UV coordinates are normalized from 128-pixel coordinates to Java model coordinates.
- Item lore lives in `items/definitions/crates-sign.yml`; feedback lives in `trophies/settings.yml` under `sign-text`.
- Deployment requires the matching plugin JAR, default item definition, feedback and resource pack, followed by a normal server restart and client pack reload.

## Verification
Run `./scripts/build.sh`, the Python tooling suite, and `scripts/build-resource-pack.py`. `CratesSignProbe` exercises actual `/mmc give`, native block interactions on all six faces, every break position, chunk-border footprints, denied outer-cell placement/break, obstruction, piston/explosion protection, full-inventory return, and an actual second JVM startup. It is guarded to isolated localhost port 25593.

## Pause note
The reactor passes 333 Java tests and the tooling suite passes 77 checks. The isolated Paper 26.1.2 fixture passes 132 native lifecycle assertions and 20 assertions after an actual JVM restart. A final combined-build run preserves the latest deployed tutorial changes. The three new pack files leave all 1,181 existing entries byte-identical. Client appearance remains a separate check. Next action: install the tested build and pack on development, then check the sign visually outside the crates building.
