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

## Verification
The export round-trips all 56,000,000 block IDs, including multi-byte palette varints, and all 24,576 serialized chunk sections. Offline checks compare all 63,595 source-solid shell voxels, require 27 closed doors, clear supported route samples, no building/street overlap and connected walking routes to all four settlements and the palace approach. All nine descents pass walkability checks. Every large vault exceeds 15,000 open voxels. Current client property/model checks, palace readback, empty inventories and ore-family restrictions pass.

Native revision 2 passes 285,056 assertions across all 1,024 map chunks, including all 63,595 source-solid shell voxels, 27 closed doors, 2,645 route samples, six broad vault interiors, 6,314 allowed ore blocks and 6,996 native suspicious-sand blocks with no loot table. All 44 native light fixtures are relit before saving; every saved fixture remains at block light 15 after the normal restart. The supplied source geometry is retained; exterior sand-fill foundations are never excavated into the original terrain. Imported lighting must be explicitly relit before saving; empty imported light arrays alone are insufficient. The native checker also exercises full shell readback, closed doors, physical street clearance, empty brushable data and distant newly generated void chunks. The checker is removed for normal operation.

## Pause note
Working: preserved original dimensions and revised Tatooine source/export, installed in the same review world. The previous review map is backed up, own temporary checker removed, and normal server startup restored. Final protection, claims and Jabba coordinate migration remain pending. Next action: user reviews the revised towns, terrain fit and cave system in game.
