# Feature: Mandalore natural caves
Status: deployed for review
Updated: 2026-11-10

## Player experience
Six connected, broad organic caverns and six walkable surface descents extend the existing 500×500 Mandalore map. Overlapping chamber lobes, flared floor-to-roof columns, attached multi-segment dripstone and an outer passage ring provide large spaces and connected exploration. Plain deepslate dominates, with coherent tuff patches and basalt accents. Newly exposed old cobbled/stone layers use deepslate. Shroomlight pockets and rooted lantern niches provide 59 additional fixtures.

Entrances: West Rift (-213,83,-65), Southwest Vault (-220,70,175), East Cathedral (218,82,-154), Glassfall Cavern (220,78,153), Northern Hollow (84,68,-232), Southern Basin (-73,65,233). Use `/warp mandalore` for the existing arrival.

## Preservation and ownership
The snapshot comes from a clean shutdown of the actual current native world, retaining the prior lantern-bracket fixes, crossing, signs and furnishing. The complete event mine, northern arrival/descent and southern approach are protected. All 16 Beskar hosts, 136,096 existing built blocks and 34 block entities are preserved. No additional Beskar nodes or regeneration behavior are added. Existing final territory/event decisions remain with their current owners.

A conflict-checked delta changes 736,565 voxels in 303 chunks. Installation compares every target against the stopped live world, retains current untouched section data and block entities, and preserves all other chunks/regions. The complete prior dimension is backed up. No main-plugin, pack, public warp or event configuration changes are made. No new third-party assets are used.

## Verification
Three-dimensional walking checks connect all six entrances and rooms, with 7,452 reachable standing positions. Existing event/arrival volumes are byte-identical to the clean snapshot. The actual native delta round-trips 7,272 sections, including 757 changed sections. Native verification checks all edits, every retained built block, floors/headroom, all 34 block entities and the 59 new fixtures: 961,846 assertions pass. Own checker removed; normal startup is clean, and all 59 fixtures retain saved block light 15 after restart. Main JAR, pack and protected public configs remain unchanged.

## Pause note
The public Mandalore warp reaches the existing world with the added caves. Private source, baseline, deltas, backups and verification logs remain outside Git. Next action: review the new cavern network in game, starting at Northern Hollow near 84,68,-232.
