# Feature: Tatooine Dune Sea
Status: revised map review; final protection, claims and event relocation pending.
Branch: bilal/tatooine-dune-sea
Updated: 2026-11-10

## Player experience
An original 500 × 500 landscape combines a small northwest arrival safezone, southeast palace warzone and wilderness intended for claims. The user confirmed these zone types, requested only Agrinium/Durasteel ore, and then rejected the first review's scattered settlements, cut-open houses, terrain borders and roads. The current revision follows that feedback: 27 closed asset shells, four compact settlements, frontage streets, markets and coherent terrain integration.

The main town has a narrow, busy street along the cantina frontage, four vendor counters and two side frontage lanes. Other towns have gently curved streets serving their actual doors. All 26 house approaches start on the connected street network and end in front of the corresponding facade. Rear loops and unused back-of-house roads are removed. Six additional market stalls, shade cloths, utilities, grouped supplies and dry planting furnish useful public spaces.

The cantina is lowered to base y66 and its facade door meets the street grade. Individual banks follow actual building support silhouettes. Houses remain closed with no new interiors. The surface road pass paints each tile's existing ground elevation without cut/fill, adds shaped treads at natural rises, and fades 29 lane endings back into sand. Warm brown/ochre cores and pale shoulders keep roads visible.
Caves include nine walkable descents, a larger branching network and six broad organic vaults with natural columns/side ledges, a central fissure, dripstone pockets, two daylight breaches and a deep aquifer. The large chambers contain roughly 60,000–111,000 open voxels each. Small tunnels connect these landmarks. Ore identities remain Agrinium/native iron and Durasteel/native diamond, with normal deepslate variants.

## Preservation and ownership
Clean-shutdown, per-file SHA-256 verified legacy Hoth/Tatooine snapshots are retained privately. Before updating, the preceding review dimension is backed up separately. Existing public dimensions, warps, plugin JAR, pack properties and event coordinates are retained. The revised content uses `minecraft:tatooine_review`, alias `TatooineReview`.

Paper/WorldService owns dimension loading, arrival and void generation. Existing metal drops remain authoritative. WorldGuard/FactionsUUID remain the intended protection/claim owners; this peaceful review dimension does not implement final territory rules. Moving the working Jabba event still requires deliberately transforming doors, room spawns, caches and its recovery checkpoint. None of those live coordinates change here.

## Content and credits
Assets: Wet, [tatooine assets](https://www.planetminecraft.com/project/tatooine-assets/). The creator allows reuse while retaining authorship; credit is appreciated. The user's original MCEdit schematic is pinned privately by SHA-256 `b2ecbd00b7ba29c545b23b5df0a5c6642bb53cd2ff53e2f87bc9b522f62ff96a`. Current wall states, rotated facings, native banner colors and stair corners are reconstructed. Display-board blocks are excluded. Source shells and any original furnishing are retained; no new interiors are made. The arrival sign, export metadata and README credit Wet. Palace source is the user's existing Tatooine world, retaining original authorship. MandoMC owns new terrain, caves, planning and adaptations.

Maps, local generation/import scripts, runtime data and snapshots remain ignored by Git.

## Revision 3 integration pass (historical)
The user requested further cantina/building integration, a deliberate more visible road design, restoration of the original mountain/arch/ship setting, and removal of large blocky cave forms.

The palace now imports its full source site volume, with no circular footprint cuts and no filling beneath a column's highest block. The complete assembly is shifted upward by 20 blocks to meet the new desert's elevation. It retains the original mountain, arch, hull clearance, outbuildings and the previously height-clipped minaret crown. Source comparison preserves all 1,202,353 occupied voxels and 312,857 original air voxels below overhangs/interior ceilings. Terrain transitions are outside this preserved volume. Paths follow natural ground to the foothills and beneath the arch, without a new ramp mass covering the original setting. The elevated entrance is at approximately 166.74, 120, 123.15; public event coordinates remain unchanged in the original world.

The cantina's low display-apron columns are excluded only where no architecture extends above them. Foundation grading follows each building's actual support mask rather than its rectangular clipboard bounds. Side elevations of the actual voxel geometry were inspected privately before deployment; no additional user renders were requested. Walls and roof geometry remain closed, with no newly excavated interiors.

The road surface now has a continuous warm packed-mud/terracotta/mud-brick core with pale sandstone edges and shaped mud-brick stair transitions. Its color is visibly separate from surrounding sand; 99% of recorded centerline samples use the colored palette. Broad curves and solved junction grades preserve walking access. Design reference: [Minecraft path examples](https://www.minecraft.net/en-us/article/five-simple-path-designs). The rectangular cave-ledge restoration pass is replaced by three-dimensional curved rock lips; the old rectangular palace support-fill volume is gone.

## Revision 4: frontage streets and palace access
The user selected a palace staging area at the mountain foot, rock-cut switchbacks to the elevated entrance, and a narrow busy cantina street. Caravan routes now meet real town gates and the staging area instead of an unused arch spur. Ground heights are unchanged by the surface-road pass; it avoids building footprints, steep slopes and vendor counters. All town frontages and 26 individual front-door approaches connect to the walkable network.

The palace climb contains 66 recorded route positions, with flat resting transitions and lit alcoves, ending at feet position 166,119,121 outside the original entrance. It carves 377 scoped rock voxels and adds no large supporting embankment. The original mountain shape is retained apart from these small intentional cuts. All 312,857 original under-ceiling/overhang air voxels remain clear. Exposed natural geology and two backing layers use the current desert palette: 23,490 voxels recolored, with unchanged occupancy. Architectural envelopes protect the palace towers, buildings and ship; the reskin does not recolor them.

## Verification
The current 500 × 500 × 256 export round-trips all 64,000,000 block IDs and all 24,576 native chunk sections. Current client block-state/property checks pass. Offline three-dimensional route checks reach every town frontage, all 26 house-front approaches and the elevated palace entrance, including stacked switchbacks. There are zero surface-road earthworks, 29 feathered lane endings and zero rear loops. All nine cave descents remain walkable. Independent palace comparison allows only the 377 explicit rock cuts and preserves original void geometry; all retained house shells remain intact.

Native revision 4 passes 453,541 assertions across all 1,024 map chunks: retained house shells, closed doors, three-dimensional route clearance, palace geometry/airspace, 7,323 allowed ore blocks, 2,639 scrub plants, empty archaeology data, newly generated void and supported arrival. Full native relighting verifies 46 fixtures at block light 15. The temporary checker is removed and normal startup is clean. All 46 saved fixtures retain block light 15 after restart. The previous review is backed up; public warps/events, the main plugin and pack properties match their pre-install values. Earlier revision checks remain historical evidence: revision 3 passed 454,913 assertions, and revision 2 passed 285,056.

## Pause note
Current review arrival is -124.5,69,-120.5, facing the cantina. Original dimensions and earlier review versions are preserved. Final protection, claims and Jabba event migration remain pending. Next action: review the frontage lanes, lower cantina street, terrain-following caravan roads and lit palace switchbacks using `/mvtp TatooineReview`.
