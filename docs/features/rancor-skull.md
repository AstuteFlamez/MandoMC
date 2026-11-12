# Rancor Skull trophy
Status: deployed and normal startup verified; isolated branch `bilal/rancor-skull`.

## Player experience
Defeating a real Rancor drops one Legendary Rancor Skull alongside existing loot. The skull derives from the supplied beast's head geometry and facial layout; a complete six-face bone atlas and four small raised fang tips make a one-block base trophy. Right-click the top of a solid block to place it facing the player; legitimately break it to recover the same item, including its custom name and other metadata.

## Rules and ownership
Placement needs one clear block above full solid support, within reach, outside protected areas and allowed by current clan build roles. No player/entity may overlap the reserved block. The placement and break requests pass through ordinary Bukkit block events so existing territory/clan policies own authorization. Nearby blocks and all existing resource-pack assets remain unchanged.

A dedicated atomic custody journal owns each item through preparation, placement and return. Player inventory and the matching receipt are saved together before custody transfers. Placed trophies use one collision block and one tagged display, reconciled from that journal on chunk load/restart. Breaks return the exact escrowed stack; full/offline inventories retain a saved return until space is available. Explosions and pistons cannot duplicate or silently remove a trophy/support. No external custom-block dependency is introduced.

## Parameters
Default drop: one per real Rancor death. Placement reservation: one block, sturdy full support below, four horizontal rotations. Model size: 0.875 blocks wide/high, under one block deep including fang tips. All six faces use a private atlas; the boss model and atlas are unchanged.

## Verification
Native Blockbench preview loads the editable model and complete atlas. The [front/rear asset preview](../assets/rancor-skull-preview.png) renders the actual exported geometry and UVs on one normal block, not a conceptual replacement or a Minecraft screenshot. The boss rig and atlas hashes are unchanged. Assets are published in pack `resource-pack-26.1.2-d38d5b8f3714` and the shared server selected it during the separately owned map rollout; gameplay is now live on the scoped post-decor overlay.

199 Java tests and 36 script checks pass. A disposable current-runtime overlay passes 54 native lifecycle assertions across two actual server starts: real Rancor death adds one skull without replacing existing loot; alive/replayed death notifications cannot generate extras; ordinary placement consumes one item into one collision block/visual; late cancellation, collision, support, safezone, explosions, pistons, foreign claims and current Recruit build permissions behave correctly. Exact custom names/PDC survive recovery; full inventory returns wait safely. Real player NBT and the journal survive an actual restart at prepared/consumed/delivered/full-inventory boundaries without duplicate returns. Placed blocks/displays survive that restart; retired chunk markers/visuals clean up, and actual player death drops one carried item. A further 8 native input assertions call the pinned server's real use-item-on and survival destroy-block handlers to verify the normal interaction path and exact metadata round trip. The test server binds only to localhost:25593 and is stopped; no probe is installed on the shared server.

Custody settings live under `trophies/settings.yml`, outside the item-definition resource installer. The source overlay changes only the existing Jabba drop bridge, the new trophy classes/resources, and the added catalog category/definition. Other gameplay classes and current runtime configs must be preserved when rebasing for deployment.

Deployment: rebased onto the released decor JAR `78d10152d258`, changing only ten trophy-related entries. Live JAR `1707e1dcdfef04dfd8aacac3734ad3d7e2904145b86ba26a5bd44d643f7c43be` preserves all unrelated entries, including the decor lifecycle. All seven deployed class entries match the native-tested candidate. Two normal starts completed cleanly; nine decor placements, seven gray buyers, 2,115 frozen chunks, active Jabba and queued KOTH are retained. No live probe or test data was installed. The current pack is `resource-pack-26.1.2-5efa3fdb6361`, retaining skull assets and raising the credit symbol one pixel.

Next action: client-playtest the skull appearance, placement and recovery at a permitted base.
