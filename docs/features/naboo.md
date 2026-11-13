# Naboo
Status: isolated verification; development deployment pending.
Branch: bilal/naboo-dev

## Player experience
`/warp naboo` and Galaxy Map slot 15 reach a recovered section of the legacy
Naboo world. A 500×500 border centered at (-13552,3440) includes a wooded
lakeshore, dense forest, open meadows and rolling slopes. The selected square
has roughly 18% tree canopy and 17% water surface; trees also surround clearings.
The legacy map's original terrain and small shoreline structures are retained.

Arrival is (-13550.5,101,3359.5), facing north (yaw 180, pitch 0).
Only chunk (-847,209), x -13552 through -13537 and z 3344 through 3359,
is Safezone at every height. Every other location is Wilderness; there is no
Warzone. Normal native clan, combat, building and restoration rules apply.
The world border runs x -13802 to -13302 and z 3190 to 3690. A whole clan claim
must fit inside the border; ordinary building can reach its partial edge chunks.

## Ownership and migration
WorldService owns the stable `minecraft:naboo` dimension and exact arrival;
WarpService uses protected arrival validation and the shared travel policy.
Native territory/clan services remain the sole protection and ownership authority.
The private importer copies the four original region files intersecting the
selected square, preserving their bytes before Paper's ordinary format upgrade.
Legacy player/entity files and plugin configuration are not imported. Outside
retained regions, new chunks are void; the 500-block border is the play area.

All 1023 non-safe chunks intersecting the square receive native pristine terrain
originals before development play. The isolated preparation uses a temporary
544-block border so partial edge chunks can be backed up; final play uses exactly
500 blocks. A new finite snapshot scope preserves all prior originals and adds
Naboo. The existing 8192-chunk limit and restoration behavior remain unchanged.
Maps, originals, journals, backups and runtime receipts remain private.

## Artwork
The user requested native 32×32 pixels with no downscaling. The original 32×32
legacy Naboo asset is retained as an art source. `build-naboo-icon.py` edits that
same grid directly: restrained green/blue colors, stepped upper-left highlights,
lower-right shading and small cloud accents. No resize operation is used.
The publication overlays only the three new Naboo texture/model entries onto
the current verified development pack, preserving all existing entries.

## Verification and next action
The reactor, icon contract, pack builder and native Paper probe verify rules,
arrival safety, menu/completion, border size, existing warps and durable originals.
Complete the isolated snapshot capture and native checks, then apply the backed-up
Naboo-only changes to the local development server.
