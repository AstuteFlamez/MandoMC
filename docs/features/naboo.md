# Naboo
Status: playable on development; final normal startup verified.
Branch: bilal/naboo-dev

## Player experience
`/warp naboo` and Galaxy Map slot 15 reach the user's captured island position:
(-13341.430043026525,59,3209.6097442130404), yaw 36.899933, pitch -3.6000152.
The original legacy Naboo world supplies the forested lakeshore, meadows, hills
and buildings. The user explicitly kept the existing 500×500 border centered
at (-13552,3440), x -13802 to -13302 and z 3190 to 3690.

The whole connected island occupies 100 Safezone chunks at every height. Its
original land footprint spans x -13507 through -13299 and z 3033 through 3235.
The northern portion remains safe beyond the unchanged border, but the border
still prevents access there. The building district, courtyards, docks and immediate
approaches are 100 Warzone chunks: x -13760 through -13601 and z 3200 through
3359. PvP is allowed there; building, containers, doors and other scenery
interactions are protected. The rest is Wilderness, including the former arrival.
Creative staff with the existing edit permission retain maintenance access.

## Caves and ores
The current underground terrain uses actual native Minecraft cave geometry from
1,024 privately generated chunks (seed 2026111303, pinned Paper 26.1.2).
Only the cave void shapes are adapted, shifted vertically to respect legacy
Naboo's Y=0 bedrock. No source ores, entities, structures, loot or biomes are
imported. The 1,636,976-cell excavated volume includes winding spaghetti/noodle
tunnels, broad cheese caverns, native pillars, ledges, sloping passages, drops
and dead ends. There are 175 meaningful connected air systems; the two largest
contain 1,108,616 and 192,284 cells. Floors span 120 elevations instead of shared
artificial levels. Vertical passages and separate systems retain normal Minecraft
mining/climbing exploration; every ledge is not promised to connect on foot.

Twelve mossy grottos feature moss, small azaleas and supported hanging roots;
six dripstone regions have supported stalactites/stalagmites. Eighteen shallow
sumps are enclosed at their waterline with solid floors. Native stone, andesite,
tuff and pale calcite form coherent geological patches. There are 450 supported
glow-lichen lights. This adds scenery, not mobs, loot, magical materials or new
mineral families. Existing server spawning and combat rules remain authoritative.

The previous mineral budget is preserved exactly: 2,635 new Agrinium hosts,
2,095 Durasteel hosts and 2,294 Electrum hosts, now arranged in 978 veins.
Existing native mappings use iron, diamond and redstone ore for those metals;
current drops, pickaxe rules and active pack textures apply. Every original
ore count is retained.

Caves remain inside the exact play border. The island and building columns,
plus structural foundation margins, are not excavated. Bedrock and at least the
top twelve original terrain layers are preserved. There are no added surface
openings: players mine into this network as on Hoth.

## Building recolor
17,116 existing blocks receive compatible material replacements: pale sandstone
and quartz masonry, cream trim, and weathered green copper roof pieces. Orange acacia becomes pale birch; jungle timber becomes warmer spruce;
terracotta becomes cream masonry or green accents. Glazed pieces retain their
facing, using white glazed ceramic. Windows, doors, block entities and every
block position remain intact. Every original property string is retained, including stair facing/half/
shape, slab type/waterlogging and pillar axis. Native shape comparison verifies
all 104 changed state pairs have identical collision geometry.

The vanilla geometry revision replaces 3,801,315 cells in 801 chunks relative
to the prior organic caves. Its 78,741,504-cell section round trip preserves
unrelated blocks, block entities, biomes and heightmaps. Fresh before-state
checks prevent replacing concurrent player edits. Saved player positions are
checked before replacing old cave space; nobody is forcibly relocated.

## Ownership and protection
WorldService owns the existing `minecraft:naboo` dimension and precise arrival;
WarpService keeps protected arrival validation and the shared travel policy.
TerritoryPolicy and ClanService remain the only zone/claim authorities.
The optional `locked-interaction-worlds` setting contains Naboo. It locks
interactive blocks in its protected zones, while Wilderness and other planets
keep their current behavior. Existing event-cache authorization remains intact.
Normal solid walls do not gain an interaction cancellation path.

## Terrain originals and rollback
The active scope contains 3,017 chunks: 2,115 previous-world chunks and 902
Naboo Wilderness chunks. The former safe arrival now has a pristine original.
New originals derive from the existing native originals by exact sparse
terrain/recolor deltas, retaining every untouched pristine cell. Before each
terrain version, prior originals, metadata, scopes and journals are archived
with matching bytes. The first cave rollout versions 545 originals and adds
the former arrival original; the organic follow-up versions 702 existing
originals, and the native-geometry revision versions 801 existing originals. All unchanged active originals stay byte-identical. The current
scope is wilderness-naboo-vanilla-20261113, complete at 3017/3017. Restoration
therefore retains the native caves and palette instead of restoring an older
terrain version. Maps, archives, full stopped-runtime backups and receipts stay
private.

The disposable integration fixture NabooCaveReference generates the bounded
reference on a private server only; extract-naboo-cave-reference.py exports it.
Pass that private directory to generate-naboo-caves.py using --reference-dir;
retain the reference.npy/reference.json files in the validation work directory.
The source tools also include extract-naboo-terrain.py, generate-naboo-caves.py,
validate-naboo-caves.py, recolor-naboo-builds.py, migrate-naboo-originals.py and
apply-naboo-terrain.py under scripts/. Numerical dependencies are listed in
scripts/map_tools/requirements.txt. Use private stopped copies and new work
folders. Regenerate originals only as an explicit terrain-version migration;
never refresh them from player edits.

## Artwork
The Naboo globe is painted directly at native 32×32 RGBA, with no resizing:
spherical upper-left lighting, curved lower-right shading, forest/meadow pixels,
deep blue water, warm shores, clouds and a small ivory/green Theed-style landmark.
Its detail follows active Hoth/Tatooine icons. The improved PNG was explicitly
approved and published. A concurrent newer main pack initially omitted all three
Naboo files, causing the missing-model cube. The repair retains all 1178 newer-main
entries byte-for-byte and adds the three already-approved Naboo files. Anonymous
downloads match their hashes. Development uses the verified resource-packs/pinned.json
selection so an unrelated main release cannot drop these local-feature assets
on restart. Remove or update that explicit pin when intentionally selecting a
new pack after source integration.

## Clan-map border view
`/c map` projects the actual native WorldBorder, while the zone policy retains
protection beyond that view. Naboo renders chunk X -863..-832 and Z 199..230,
32 rows of 32 cells intersecting its 500×500 area. The caption states the block
size and edge hovers show clipped inside-border ranges. The 78 safe island
chunks north of the border remain protected but are not drawn as playable land.
Whole-world-safe hubs keep their nearby map behavior; unbounded worlds retain
existing map handling. Claim eligibility still requires a whole chunk inside
the world border.

## Verification and next action
316 gameplay and 4 access tests pass, alongside 74 Python checks and pack
validation. The initial zone/interaction rollout passed 48,104 live assertions.
The native cave revision passes 3,806,771 terrain, standing, decoration support,
pool containment, lighting and unchanged building geometry checks both privately
and on dev, including every changed cell, all 193 dry standing anchors, all native
lights, and identical collision geometry/properties for 104 material-state pairs.
The actual `/c map` command and selected cached pack pass 14 private/live checks.
All previous terrain versions' originals and journals remain archived with
identical bytes; untouched active originals and unrelated plugin JARs are verified.

The last normal development startup has no errors, retains the repaired pinned
pack and complete 3017-chunk scope, and has no temporary probe JARs/directories.
Code remains local on the feature branch; production is unaffected.

Next action: explore the native cave networks and lush/dripstone/pool landmarks
on dev; use normal mining/climbing for vertical passages and separate systems.
