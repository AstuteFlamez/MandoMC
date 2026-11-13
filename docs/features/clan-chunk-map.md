# Feature: Native territories and clan chunk map
Status: deployed; client playtest pending
Branch: bilal/clan-chunk-map

## Player experience
`/c map` (`/clan map`, `/cl map`) shows a north-up grid with hover chunk/block coordinates and a yellow + for the viewer. Mandalore displays all 1,024 chunks intersecting its 500×500 map; Batuu shows a moving 32×32 nearby view and explains that the entire world is safezone. Chat output may require scrolling.

Crossing into a different territory category shows a title: orange Safezone (#FFAA00), dark red Warzone (#AA0000), or gray Wilderness. Join, teleport, world change and respawn update the title; movement within the same category does not repeat it. Cancelled movement/teleports do not announce a destination.

## Rules
Mandalore's only safezone is the full spawn outpost, terrace and connected mine gateway (authored footprint X -42..6, Z -211..-173), projected onto 16 full-height chunks. Nearby cities and ruins no longer get safezone status. Central caves and both approaches are warzone. Counts: 16 safezone, 127 warzone, 881 wilderness. The outpost takes priority at overlaps; underground furnishings remain part of the cave warzone. Edge chunks include six blocks outside the authored border.

All of Batuu, at every coordinate and height, is safezone. Hoth preserves its exact existing warzone cuboid X -237..237, Y -64..319, Z -121..237; outside it remains safezone. The retired overworld landing retains its old safe cuboid. Other worlds, including Tatooine, retain wilderness behavior.

Safezone denies damage in either direction, blaster firing, building, creature spawning and protected container access. Warzone permits combat while protecting terrain/containers from ordinary edits and explosions. Wilderness allows ordinary combat and edits. Native protection covers bucket/tool/physical interactions, entities/armor stands/hanging items, fire/liquids, pistons, dispensers, inventory automation and explosion block lists. Existing cancellations remain cancelled. Staff may maintain protected builds only in Creative with mandomc.territory.edit (default op); this never bypasses combat protection. Native custom-attack/vehicle/mob adapters fail closed without the territory service.

## Ownership and integrations
MandoMC TerritoryPolicy is the sole zone authority used by map, TerritoryProtection listeners, title transitions, saber/mob/vehicle adapters and arrival validation. Bukkit services share the plugin-owned instance. Supported pinned WeaponMechanics pre-shoot and damage events enforce safezone boundaries; Paper damage events cover ordinary/projectile/environmental damage. Mandalore world PvP is enabled so native warzone/wilderness PvP can work; safezone remains protected by the native policy.

FactionsUUID and WorldGuard are removed from active runtime and catalog/lock. Their JARs/data are archived privately for rollback. Old WorldGuard templates are under server/config/common/archive/worldguard and must not be deployed. WorldEdit remains for staff tools. Native membership, claims and ownership migration remain deferred. Existing vendor-specific historical probes predate this replacement; use NativeTerritoryProbe for the current protection boundary.

## Parameters
territory/maps.yml schema-version 1 stores the Mandalore atlas (S/W/.), whole-world safezone names and preserved legacy cuboids. Atlas max 64×64; rows, keys and cuboid bounds validate at startup. language/territory.yml holds labels, colors, titles and protection feedback. Configuration changes require restart. The reproducible atlas generator takes private scene/blocks paths as arguments; no map payload is tracked.

## Verification
175 Java tests pass, covering complete/negative/edge chunk coverage, complete 16-chunk spawn coverage, distant Batuu positions, permissions and exact legacy cuboid boundaries. 5,179 focused native Paper assertions pass against the installed JAR: vendor absence, native listener/authority registration, arrival validation, adapters, staff/edit rules, placement, fall/attack protection, explosion filtering, liquid/piston borders, dispenser buckets/piston heads/tree growth across borders, decorative armor stand protection, every one of the 1,911 spawn-footprint columns, exact titles/colors/deduplication, complete Mandalore/Batuu grids, and actual pinned weapon pre-shoot hooks. Constructed action events do not edit terrain or damage real players. Normal server restart removes the probe; KOTH is restored. No resource-pack change.

## Pause note
Next action: walk from Mandalore spawn into the caves/wilderness, then visit Batuu, and client-check the titles, colors and non-op protection behavior.
