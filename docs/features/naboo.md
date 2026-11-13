# Naboo
Status: playable on the local development server.
Branch: bilal/naboo-dev

## Player experience
`/warp naboo` and Galaxy Map slot 15 reach a recovered section of the legacy
Naboo world. A 500×500 border centered at (-13552,3440) includes a wooded
lakeshore, dense forest, open meadows and rolling slopes. The selected square
has roughly 18% tree canopy and 17% water surface; trees also surround clearings.
The legacy map's original terrain and small shoreline structures are retained.

The warp uses the player's captured island position
(-13341.430043026525,59,3209.6097442130404), yaw 36.899933, pitch -3.6000152.
The whole connected island is Safezone at every height: 100 occupied chunks
following its shoreline, including the island portion north of the play border.
The original island land footprint runs x -13507 through -13299 and z 3033
through 3235; the native protection follows full chunks. Nearby islands, mainland
and the former arrival are Wilderness. There is no Warzone.

The user explicitly kept the existing 500×500 border centered at (-13552,3440):
x -13802 to -13302 and z 3190 to 3690. The border intentionally restricts access
to the northern island portion even though that terrain is also safe. Whole clan
claims must fit inside the border.

## Ownership and migration
WorldService owns the stable `minecraft:naboo` dimension and exact arrival;
WarpService uses protected arrival validation and the shared travel policy.
Native territory/clan services remain the sole protection and ownership authority.
The private importer copies the four original region files intersecting the
selected square, preserving their bytes before Paper's ordinary format upgrade.
Legacy player/entity files and plugin configuration are not imported. Random
ticks and fire spread are disabled to preserve authored scenery, and legacy
command blocks cannot execute. Outside
retained regions, new chunks are void; the 500-block border is the play area.

The original 1023 native Naboo originals remain retained. The island update removes
22 newly safe chunks from active claim preparation and adds a pristine native
original for the former arrival chunk, captured only on the unchanged isolated
terrain. No live terrain is replaced. The new finite scope contains 3117 chunks:
2115 prior-world chunks and 1002 active Naboo wilderness chunks. Every previous
original and capture receipt remains untouched. Maps, originals, journals, backups
and runtime receipts remain private.

## Artwork
The user requested native 32×32 pixels with no downscaling. The original legacy
icon remains retained as an art source. `build-naboo-icon.py` paints a new 32×32
RGBA globe directly: spherical upper-left lighting, curved lower-right shade,
forest/meadow clusters, deep blue water, warm shores, cloud highlights and a
small ivory/green Theed-style landmark. Its detail and landmark treatment follow
the active Hoth/Tatooine icons. No resize operation is used. The new release
changes only the Naboo PNG; all 1180 other active pack entries remain identical.

## Verification and next action
314 gameplay and 4 access tests, 72 Python checks and pack validation pass.
The private island probe verifies exact captured coordinates/camera, safe arrival,
all full-height zone boundaries, the unchanged border, archived originals and
newly claimable former arrival. No existing Naboo claims conflict with the island.
The original deployment passed 2392 native checks; this island update's deployment
verification is in progress. Next action: apply the verified island/pack update to
development and check `/warp naboo` in client.
