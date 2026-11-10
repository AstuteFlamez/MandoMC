# Feature: Tatooine Dune Sea
Status: revised map review; final protection, claims and event relocation pending.
Branch: bilal/tatooine-dune-sea
Updated: 2026-11-10

## Player experience
An original 500 × 500 landscape combines a small northwest arrival safezone, southeast palace warzone and wilderness intended for claims. The user confirmed these zone types, requested only Agrinium/Durasteel ore, and then rejected the first review's scattered settlements, cut-open houses, terrain borders and roads. Revision 2 follows that feedback: 27 closed asset shells, four compact settlements, frontage streets, markets and coherent terrain integration.

The main town organizes around the original cantina with side streets, a front court and exits. Other outposts share caravan streets with small rear service lanes. Nine market stalls, two supported street shades, utility/salvage/water yards, grouped supplies, dry planting and two caravan pullouts give public spaces a purpose. Streets form one walkable network between all settlements and the palace approach. A junction-grade pass fixes lips where road brushes meet town streets.

Building bases sit above each footprint's natural maximum, allowing only one block of foundation embedment. Individual feathered sand banks use fill; foundation grading never excavates the original ground. No whole-town rectangular terrace is used. Original source-solid voxels remain present: no rooms or door tunnels are carved. Closed iron doors replace facade blocks with solid material retained behind them. Surface deposits remain predominantly sand, with spatially coherent sandstone/smooth sandstone and empty suspicious-sand pockets.

Caves include nine walkable descents, a larger branching network and six broad organic vaults with natural columns/side ledges, a central fissure, dripstone pockets, two daylight breaches and a deep aquifer. The large chambers contain roughly 60,000–111,000 open voxels each. Small tunnels connect these landmarks. Ore identities remain Agrinium/native iron and Durasteel/native diamond, with normal deepslate variants.

## Preservation and ownership
Clean-shutdown, per-file SHA-256 verified legacy Hoth/Tatooine snapshots are retained privately. Before updating, the preceding review dimension is backed up separately. Existing public dimensions, warps, plugin JAR, pack properties and event coordinates are retained. The revised content uses `minecraft:tatooine_review`, alias `TatooineReview`.

Paper/WorldService owns dimension loading, arrival and void generation. Existing metal drops remain authoritative. WorldGuard/FactionsUUID remain the intended protection/claim owners; this peaceful review dimension does not implement final territory rules. Moving the working Jabba event still requires deliberately transforming doors, room spawns, caches and its recovery checkpoint. None of those live coordinates change here.

## Content and credits
Assets: Wet, [tatooine assets](https://www.planetminecraft.com/project/tatooine-assets/). The creator allows reuse while retaining authorship; credit is appreciated. The user's original MCEdit schematic is pinned privately by SHA-256 `b2ecbd00b7ba29c545b23b5df0a5c6642bb53cd2ff53e2f87bc9b522f62ff96a`. Current wall states, rotated facings, native banner colors and stair corners are reconstructed. Display-board blocks are excluded. Source shells and any original furnishing are retained; no new interiors are made. The arrival sign, export metadata and README credit Wet. Palace source is the user's existing Tatooine world, retaining original authorship. MandoMC owns new terrain, caves, planning and adaptations.

Maps, local generation/import scripts, runtime data and snapshots remain ignored by Git.

## Revision 3 integration pass
The user requested further cantina/building integration, a deliberate more visible road design, restoration of the original mountain/arch/ship setting, and removal of large blocky cave forms.

The palace now imports its full source site volume, with no circular footprint cuts and no filling beneath a column's highest block. The complete assembly is shifted upward by 20 blocks to meet the new desert's elevation. It retains the original mountain, arch, hull clearance, outbuildings and the previously height-clipped minaret crown. Source comparison preserves all 1,202,353 occupied voxels and 312,857 original air voxels below overhangs/interior ceilings. Terrain transitions are outside this preserved volume. Paths follow natural ground to the foothills and beneath the arch, without a new ramp mass covering the original setting. The elevated entrance is at approximately 166.74, 120, 123.15; public event coordinates remain unchanged in the original world.

The cantina's low display-apron columns are excluded only where no architecture extends above them. Foundation grading follows each building's actual support mask rather than its rectangular clipboard bounds. Side elevations of the actual voxel geometry were inspected privately before deployment; no additional user renders were requested. Walls and roof geometry remain closed, with no newly excavated interiors.

The road surface now has a continuous warm packed-mud/terracotta/mud-brick core with pale sandstone edges and shaped mud-brick stair transitions. Its color is visibly separate from surrounding sand; 99% of recorded centerline samples use the colored palette. Broad curves and solved junction grades preserve walking access. Design reference: [Minecraft path examples](https://www.minecraft.net/en-us/article/five-simple-path-designs). The rectangular cave-ledge restoration pass is replaced by three-dimensional curved rock lips; the old rectangular palace support-fill volume is gone.

## Verification
Revision 2 passed 285,056 native assertions; revision 3 supersedes its layout/material and palace import details. The current 500 × 500 × 256 export round-trips all 64,000,000 block IDs and all 24,576 native chunk sections. Client model/property checks pass. All nine cave descents and connected walking routes between settlements, the palace foothills and arch spur pass offline checks. Independent source comparison checks the full preserved palace solid/void geometry; 38,821 explicit air samples cover imported overhangs/interiors for native readback, and the crown at y217 is included. House checks exclude only verified low, nonarchitectural display-apron columns.

Native revision 3 passes 454,913 assertions across all 1,024 map chunks, including 24,816 palace readback positions, 38,821 original airspace samples, retained source solids, all 58,086 architectural shell voxels after low-apron removal, closed doors, route clearance, crown geometry at y217, 7,323 allowed ore blocks and empty archaeology data. Arrival is verified at -125.5, 67, -106.5 after correcting fractional-coordinate rounding. Full native relighting is required before saving. The temporary checker validates all retained house shells, closed facades, route clearance, imported palace geometry/airspace, ore families, empty brushable data and newly generated distant void chunks, then is removed. Normal startup is clean; all 41 saved fixtures retain block light 15, and the main JAR, pack and protected public configs match their pre-install values.

## Pause note
Working: original dimensions and earlier review versions preserved; revision 3 installed in the same review dimension with successful native verification. Final protection, claims and Jabba migration remain pending. Next action: user reviews the cantina, restored palace/mountain airspace, colored roads and curved cave shelves.
