# Feature: Mandalore review refinements and planet icons
Status: implementation; deployment pending
Branch: bilal/deepslate-ore-base

## Player experience
Players can choose Mandalore in the normal warp menu and arrive at a clearer, furnished mining outpost. The descent has explicit stair flights and flat landings. Caves use regular deepslate, varied multi-segment dripstone clusters, inhabited workshop/supply bays and actual light sources. Pillar fixtures are attached sconces; the overhead crossing is continuous. Outside the original 500-block map, new terrain must be void.

Batuu, Hoth and Tatooine changes are icons only. The user clarified that Rebel-base and Jabba's-Palace motifs belong in the icons; no Hoth/Tatooine terrain edits are authorized by that request. All four final planet PNGs are exactly 32x32 RGBA, centred in a 24px content box. Generated originals and prompt provenance are separate artwork sources.

## Rules
Keep the single arrival safezone and connected mine warzone as layout intent. Final region boundaries, claims, mobs and Beskar event/regeneration remain deferred. The existing review-world key and admin alias stay stable. Public travel delegates to the existing teleport service, including Creative travel, warmup, combat and destination checks. Survival/Earth stay retired.

## Smallest playable version
`/warp mandalore` reaches the current review arrival and the menu uses mandomc:planets/mandalore. Regular deepslate replaces natural cobbled deepslate. Sixteen sample Beskar blocks use the existing ANCIENT_DEBRIS identity; deepslate ore backgrounds now match normal pinned Faithful deepslate exactly, preserving accepted mineral/recess pixels. Stone ores are byte-identical to the prior pack.

## Ownership and integrations
WorldService owns the dimension/arrival. WarpService owns the menu and delegates travel. The dimension's native flat-air generator owns future void chunks; replacement region data is scoped to the backed-up Mandalore review dimension. No generator affects other worlds. Barrels have native empty block entities, not custom loot. Resource-pack publication uses the existing authorized release destination.

Concurrent source branches own unrelated deployed features. Deployment therefore overlays only WarpService classes and travel/warps.yml onto the exact deployed plugin baseline; every other JAR entry is verified byte-identical. Full source builds require integration with bilal/planet-wilderness before a future general deployment.

## Parameters
Main map remains 500x500, with a fixed 500-block review border. The main descent has 57 height changes and planned landings. Native checks inspect fixtures, valid barrel inventories, complete pillar/capital boxes, stair facing/rows, fresh void chunks and the actual warp command/menu. Exact lighting minima will be recorded after native validation.

## Verification
166 Java tests and 33 tooling tests pass. Source checks cover all routes, shafts/capitals, supported constructed components, zero natural cobbled deepslate and sixteen exposed ore samples. Pack checks enforce four 32px icons, exact ore-background/foreground separation and unchanged unrelated pack bytes. Native deployment checks remain pending.

## Pause note
Content and scoped release candidates are prepared. Next action: back up the runtime, deploy, and complete native verification before the user's in-game review.
