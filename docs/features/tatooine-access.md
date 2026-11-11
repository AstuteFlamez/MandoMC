# Feature: Tatooine roads and territory
Status: playable, deployed with combined clans rollout
Branch: bilal/tatooine-access

## Player experience
Players arrive in a protected town, follow its shortest existing road toward Jabba's Palace, and enter the palace warzone. The remaining desert is claimable wilderness with existing Tusken patrols, banthas, dewbacks and massiffs. Palace walls are under the user's manual control.

## Rules
The spawn town and shortest existing road corridor are safezone. The full rotated palace footprint, including its outbuildings, is warzone and takes priority over the approach corridor. Every other chunk is wilderness. Boundaries use full-height claim chunks, so atlas presentation, building protection and claim checks agree. The configured review world is minecraft:tatooine_review; legacy Tatooine remains separate.

Bottom stair facing points uphill. Its low approach cannot meet a full cube at the stair's base Y. The high edge may meet a full cube as a valid upper landing. Use real half-block collision heights, preserve headroom, rotate toward an open approach, and use bottom slabs at multi-entry turns. Road ramps must connect at half-block resolution without requiring jumps. Architectural stairs and manual palace terrain are outside the repair scope.

Ambient sandy encounter selection and final spawn validation require wilderness. General creature spawning still permits warzones, preserving event-owned mobs. Existing combat, population caps, biome/ground checks, patrol frequency, AI and loot ownership remain unchanged. Safezone still blocks damage and spawning. Normal difficulty and world PvP are needed for the existing hostile wildlife/warzone behavior; native territory protection supplies the safezone boundary.

## Ownership and integration
TerritoryPolicy is the shared static authority. Optional additional-maps preserves the existing primary Mandalore atlas and adds the Tatooine atlas. SandySpawns remains the sole ambient encounter owner; MobProtection.encounter adds the wilderness restriction while allowed retains combat permission. MythicMobs/ModelEngine retain entity and model ownership. The clans chat owns the combined policy/command/config rollout, preserving its nearby-map, mechanism and claim-buffer additions.

## Parameters
The 32-by-32 Tatooine atlas has 79 safezone, 169 warzone and 776 wilderness chunks. The protected shortest road follows 607.5 blocks of existing connected routes. Chunk rounding includes the full road width and town assets. Existing mob population/frequency parameters are retained; only the target world changes from the retired overworld to TatooineReview. Configuration changes require a clean restart.

## Verification
178 Java tests and three directional stair-rule tests pass. The sparse trial changes 929 road-floor blocks in 103 chunks. All 1,577 route points connect through the 5,533 sampled road tiles using half-block collision transitions. A native NBT roundtrip checks 10,125,312 voxels and preserves unrelated blocks, block entities, biome palettes, heightmaps and all separate entity region files. Live patching takes a fresh stopped-world snapshot and refuses unreviewed route conflicts. Native Paper rehearsal passed 42,309 assertions; the fresh live patch passed 42,303. All 929 road changes and all 45 original light levels (15) persist after the normal restart; temporary checkers were removed. The combined clan smoke test separately passed 1,049 live assertions. Full runtime and pre-patch dimension backups are retained privately.

## Deferred
No further palace wall sculpting. No new mob types, loot changes, public warp migration or wilderness-cave changes are included.
