# Feature: Tatooine Dune Sea
Status: separate map review; final protection, claims and event relocation pending.
Branch: bilal/tatooine-dune-sea
Updated: 2026-11-10

## Player experience
An original 500 × 500 desert landscape combines a small arrival safezone, a southeast palace warzone and wilderness intended for player claims. The user confirmed the palace is the warzone. They also requested bushes and short dry grass, natural-style caves, and only Agrinium/Durasteel ore on Tatooine.

Four settlements contain nineteen selected/adapted buildings from Wet’s supplied asset sheet, including the furnished cantina. The palace’s existing geometry and underground rooms were rotated to face into the map and blended into a new corner mesa. The existing mountain’s rectangular air crop is filled only above its highest original block; roofed interior air and original solid building geometry are retained. Interior/threshold adjustments make the selected settlement shells usable.

## Preservation and ownership
Original Hoth and Tatooine were copied after a clean Paper shutdown. Every world file was SHA-256 verified, with supporting MandoMC/WorldGuard configuration retained privately. Original runtime dimensions, public warps and both event configurations remain in use. The new landscape is the independent `minecraft:tatooine_review` dimension, alias `TatooineReview`.

Paper/WorldService owns dimension loading, exact arrival and void generation. Existing metal identities and drops remain authoritative: native iron ore is Agrinium, native diamond ore is Durasteel, with corresponding normal deepslate variants. No other ore families occur in the export. No pack or deployed main-plugin changes are required.

WorldGuard and FactionsUUID remain the intended owners of static reservations and claims; this review world does not implement them. The review is peaceful with PvP disabled. Moving the working Jabba event requires transforming its doors, room spawns, caches and recovery checkpoint deliberately; none of those live coordinates were changed here.

## Content and credits
Source: Wet, [tatooine assets](https://www.planetminecraft.com/project/tatooine-assets/). The creator allows reuse while retaining authorship; credit is appreciated. The user supplied the original MCEdit schematic. It is pinned privately by SHA-256 `b2ecbd00b7ba29c545b23b5df0a5c6642bb53cd2ff53e2f87bc9b522f62ff96a`. Models/layout and native banner colors were converted using the installed WorldEdit legacy mapping with current wall enum corrections. Display-board blocks are omitted. Hollowed sand fill is stabilized with sandstone, and 669 stair corners are reconstructed. The cantina retains its original furnishing with a fitted raised entry. A native arrival sign, export metadata and accompanying credits name Wet.

Palace source is the user's existing Tatooine map; its original authorship is retained. MandoMC owns the new terrain, caves, trails, placement and adaptations. Binary maps, legacy snapshots, local scripts and runtime data remain ignored by Git.

## Verification
The Sponge v2 export round-trips all 56,000,000 block IDs, including multi-byte palette varints. All five cave descents and the spawn-to-cantina route pass walkability checks. Current client models/property values, settlement doors, palette, copied palace samples, empty container data and ore restrictions pass offline checks. Native region serialization round-trips all 24,576 sections.

Native Java 26.1.2 verification passes 137,893 assertions across all 1,024 map chunks, including 4,578 palace samples, supplied asset details, native barrels/banners, all 6,380 allowed ore blocks, 1,908 dry plants and 26 real light fixtures. Full native relighting is explicitly run before saving; imported empty light arrays alone are insufficient. Eight newly generated distant chunks verify void generation outside the map. Arrival has clear feet/head space and support. Existing worlds, main plugin, public warp/event configs and resource-pack settings are retained. The successful temporary checker is removed for normal operation. All 26 saved fixture light values remain 15 after normal restart. A concurrent clan-map deployment subsequently updated the main JAR; it was retained, and its normal startup still loads this review dimension. The map workflow never replaces the main JAR.

## Pause note
Working: preserved legacy dimensions and the new map available through `/mvtp TatooineReview`. Pending: user review, final region boundaries, claim integration and deliberate Jabba-event migration. Next action: user reviews the new Tatooine landscape and palace placement in game.
