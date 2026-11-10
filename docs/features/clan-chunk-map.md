# Feature: Native clan chunk map
Status: deployed; client boundary review pending
Branch: bilal/clan-chunk-map

## Player experience
`/c map` displays all 1,024 chunks intersecting Mandalore's 500×500 block map, with aliases `/clan map` and `/cl map`. North is up, west left. Hover cells for zone/chunk/block coordinates; yellow + marks the viewer. The full grid is chat output and may require scrolling.

## Rules
Green safezone covers authored surface cities, ruin terraces, isolated ruin walls and the arrival camp/causeway/gateway. Red warzone covers the carved central cave system and both approaches; all other chunks are wilderness. Safezone wins if a full-height chunk contains both a surface build and caves. Underground furnishings remain part of the cave warzone. The outer row/column chunks extend six blocks beyond the map border. Chunk coordinates use floor division, including negatives.

This first command is visualization only. Existing WorldGuard protection remains unchanged; chunk labels grant no claim/edit/combat permissions. FactionsUUID is removed from catalog, lock and runtime, with its local JAR/data archived for rollback. No native membership or claim migration is implemented yet.

## Smallest playable version
One immutable Mandalore atlas, read once at startup. Unknown worlds, console, bad syntax and denied permissions receive clear messages. No per-command disk access, terrain scan, chunk loading, tasks or persistent player state.

## Ownership and integrations
MandoMC owns the atlas and command. WorldGuard remains protection authority. CommandBindings registers the executor and reclaims bare command labels. A narrowly verified runtime JAR overlay preserves concurrent deployed content byte-for-byte.

## Parameters
`territory/maps.yml` schema-version 1 stores the world key, title, explicit chunk bounds and north-to-south rows (S/W/.). Maximum 64×64 chunks; row count/width and symbols are validated. Configuration and language/territory.yml require restart. The generator accepts private authored scene/blocks files as arguments and emits the sanitized atlas; private map payloads stay untracked.

## Verification
170 Java tests pass, including complete grid coverage, negative/edge coordinates, landmark classifications and malformed configuration. 2,102 focused native Paper assertions pass against the deployed JAR: command ownership/aliases/default permission, every cell/hover, marker and negative coordinates, unchanged loaded-chunk count, unmapped/outside/usage cases, completion and console dispatch. Normal server restarted without the checker or FactionsUUID; no startup errors. Runtime overlay preserves every unrelated entry from the exact deployed baseline.

## Pause note
Next action: verify `/warp mandalore` then `/c map` in a real client and review the projected boundaries before implementing zone enforcement or clan state.
