# Feature: Tatooine Dune Sea
Status: revised map review; final protection, claims and event relocation pending.
Branch: bilal/tatooine-dune-sea
Updated: 2026-11-10

## Player experience
An original 500 × 500 landscape combines a small northwest arrival safezone, southeast palace warzone and wilderness intended for claims. The user confirmed these zone types, requested only Agrinium/Durasteel ore, and then rejected the first review's scattered settlements, cut-open houses, terrain borders and roads. The current revision follows that feedback: 26 closed house shells plus the furnished cantina, four compact settlements, frontage streets, markets and coherent terrain integration.

The main town has a narrow street outside the cantina's complete architectural envelope, including recesses and gaps within its silhouette. Four freestanding vendor stalls face the street with visible separation from the cantina; the two southern houses are set back to leave room. Other towns retain gently curved frontage streets, with all 26 house approaches connected to actual front doors and no unused rear loops.

The cantina remains at base y66. Its original supplied-asset entrance is restored, including its frame, birch/end-stone trim, lintel, sill, and intentional portal air. There are no replacement iron doors in this entrance. A short graded stoop reaches the original floor at y70. All 26 houses retain closed facades; no new house interiors are made. Surface trade roads retain their existing floor elevations and fade 29 lane endings into sand; the cantina stoop and new palace hillside are deliberate local grading exceptions.
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

## Revision 4: frontage streets and palace access (historical)
The user selected a palace staging area at the mountain foot, rock-cut switchbacks to the elevated entrance, and a narrow busy cantina street. Caravan routes now meet real town gates and the staging area instead of an unused arch spur. Ground heights are unchanged by the surface-road pass; it avoids building footprints, steep slopes and vendor counters. All town frontages and 26 individual front-door approaches connect to the walkable network.

The palace climb contains 66 recorded route positions, with flat resting transitions and lit alcoves, ending at feet position 166,119,121 outside the original entrance. It carves 377 scoped rock voxels and adds no large supporting embankment. The original mountain shape is retained apart from these small intentional cuts. All 312,857 original under-ceiling/overhang air voxels remain clear. Exposed natural geology and two backing layers use the current desert palette: 23,490 voxels recolored, with unchanged occupancy. Architectural envelopes protect the palace towers, buildings and ship; the reskin does not recolor them.

## Revision 5: original cantina entrance and open mountain road
The user reported stalls merging with the cantina, a damaged entrance, a town road crossing the building, and a palace route entering the mountain. They requested the original supplied entrance, and an outer mountain road starting diagonally left when leaving the palace and zigzagging along a terraformed edge.

The road exclusion mask now includes the complete cantina envelope rather than only occupied source columns. Four detached vendor footprints are reserved before routing, with separation checked for their posts and canopy. Through streets are checked against the whole envelope. The supplied entrance is restored at 336 source positions; materials, orientation and halves match the original, allowing only native automatic stair-corner reconstruction. Its entrance remains the supplied framed portal. The stoop joins the street at the original sill without altering its frame.

The former enclosed climb is replaced by 158 route positions on a broad sandstone hillside. From the exit it first runs northwest, then descends deliberate switchbacks to the caravan staging area at 176,73,54. Every centreline position is open to the sky. Sculpting adds 38,061 solid terrain voxels, clears 61 natural surface voxels, and changes 100 existing materials, all in a scoped northern hillside. The full 312,857 original below-ceiling/overhang air voxels, arch/hull clearance and y217 crown remain intact. The source geology palette remains desert sandstone with protected architecture.

## Verification
The 500×500×256 export round-trips all 64,000,000 IDs and 24,576 native sections. Client block-state/property checks pass. Offline three-dimensional walking checks reach all 26 house-front approaches, four settlement frontages and the palace entrance at 166,119,121. Additional checks cover all 336 original cantina entrance positions, four detached markets, no through-road intrusion into the cantina envelope and open sky over every palace route position. All retained 58,086 house/cantina solid shell voxels remain present, all nine cave descents remain available, and all 312,857 original overhang/interior air voxels remain clear. Public warps/events retain their existing worlds.

Native revision 5 passes 527,115 assertions, including all 38,222 hillside edits, 336 original entrance positions and open-sky checks over the entire palace road. All 7,323 allowed ore blocks, 2,647 scrub plants, source shells, closed house doors, palace voids and supported spawn pass. Normal startup is clean, the temporary checker is removed, and all 45 saved fixtures retain block light 15 after restart. The prior review is backed up. Main plugin, pack properties and protected public warp/event configs match their pre-install values. Earlier verification is historical: revision 4 passed 453,541 assertions and revision 3 passed 454,913.

## Pause note
Current review arrival is -125.5,67,-118.5, facing the cantina. Original dimensions and previous review versions are preserved. Final protection, claims and Jabba event migration remain pending. Next action: review the restored original entrance, detached markets and open hillside switchbacks in `/mvtp TatooineReview`.
