# Feature: legacy Rancor and rubble slam
Status: first pass deployed for dungeon-room playtesting.
Branch: bilal/jabba-generic-mobs

## Player experience
Fight the recovered Rancor with original model, scale, hitbox, voices, swings and stomps. The ground slam now lifts floor-matched chunks in an expanding ring with matching block particles. Rubble hops/spins and disappears; it does not break the dungeon or become collectible blocks. All attacks face the current target before their animation starts.

## Legacy baseline
- Pig base, 750 health, 0 native contact damage, Hutts faction, 0.22 speed, 24-block follow/combat range and 40-block red segmented boss bar. Preserve original projectile/arrow/magic damage modifiers.
- Original model scale 2 and large model hitbox; attach on spawn/load. Geometry, embedded textures, keyframes and Override flags are preserved. Attack clips finish instead of holding.
- Combat decision every 100 ticks: original random swing, stomp or slam. Swing/stomp still choose left/right; ambient, hurt and death voices remain original.
- Swing: 8 damage/radius 7 and original throw 5/0.9 at animation tick 16. Stomp: 9 damage/radius 12 and throw 14/14 at tick 16. Slam: 10 damage/radius 14 and throw 17/13 at tick 15. Shared facing prep adds six ticks before each clip without moving those animation-relative cues.
- Existing Mythic attack mechanics own damage and knockback. Rubble is visual only; its outward movement does not create additional damage or replace the legacy instant impact.
- No new loot, scheduled encounter or dungeon placement. Manual summon: `/mm mobs spawn Rancor`.

## Slam effect
The impact snapshots the caster's position. Rings expand in two-block steps, every two ticks, through radius 14. Nearby loaded floor surfaces supply each fragment's block data; surfaces beyond three blocks of the impact floor, absent surfaces, tile entities and combat-protected positions are skipped. Native BlockDisplays animate a short upward arc and spin, then shrink away after 20 ticks. Dust uses the same floor material. Real block state is never changed; no FallingBlock physics, drops or extra damage are emitted.

Settings are in rancor.yml. Default budgets: four active waves, one per Rancor, 256 fragments globally. Replacing a wave clears that caster's earlier debris. Source death/removal/world change, normal expiry and plugin shutdown remove owned displays. Displays are nonpersistent, gravity-free and short-lived; the effect does not force-load chunks.

## Integration and assets
`rancor-mob.yml` and `rancor-skills.yml` retain legacy behavior with current model/sound references and supported block particles. HostileFacing includes Rancor. MandoMC's console-only rancorslam bridge accepts only live Mythic Rancors at allowed combat locations. Provenance: resource-pack/rancor-sources.json and server/config/common/modelengine/sources.json.

Published `resource-pack-26.1.2-e2dea8c86e0b`: 404 files, 1,050,623 bytes; SHA-1 `ed8b2732f3df99e955a7d5184cbfe805d41d202b`. Includes ModelEngine R4.1.1-generated rig and six original voice samples. Every previous asset and sound mapping is preserved.

## Verification and next playtest
88 Java tests, 14 pack/tooling tests and 44 live assertions pass. Live checks exercise the real Mythic spawn command, original health/scale/hitbox, four swing/stomp clips and pre-facing, impact timing, outward ring progression to 14 blocks, two distinct floor materials, actual raised debris, global concurrency cap, expiry/source cleanup and unchanged real terrain. Fixture entities/chunks/platform are cleaned up. Client appearance and original knockback balance inside the room remain player playtests.

## Swipe/stomp/line refinement (2026-11-09)
This supersedes the radial slam visual/damage description above. Swings now have 60% selection weight, with stomp/slam at 20% each. White SWEEP_ATTACK crescents and EXPLOSION puffs follow the actual animated hand, including when the model has no viewers. Connected swipes launch players along the measured arm motion (1.6 horizontal, 0.5 upward); cancelled/zero-damage/protected hits do not launch. Swing damage and seven-block legacy target radius are retained.

Stomps keep legacy damage/throw behavior, but ground effects now originate at the planted foot: eight radial fissure spokes erupt at distances 3/6/9/12, using heavier floor-matched chunks and a higher hop than the slam. Real terrain remains unchanged.

The approved slam is a forward rupture, not an area circle. At impact it snapshots the target direction and sends a roughly three-block-wide line of rising floor blocks up to 14 blocks, stopping at walls. Its hit strip is 3.6 blocks wide with three-block vertical tolerance. Only survival/adventure players in the advancing strip receive ten raw damage, once per wave. Native damage events preserve attribution, armor, combat tags and cancellation; connected hits launch along the line at the legacy effective 1.7 horizontal/1.3 upward strength. The former radial Mythic slam damage/throw is removed. Side/rear players and protected positions are excluded.

The expanded live Rancor probe passes 59 assertions: hand positions and motion, directional connected-hit/cancelled-hit launch, distinct stomp debris, forward-only rupture progression/range, floor matching, expiry/ownership limits and unchanged terrain. Two geometry tests cover side/rear/beyond-wave/upstairs exclusions. These classes are byte-identical to the passing combat build used by the final dungeon deployment. No additional pack assets are needed for these native effects.

## Close pursuit (2026-11-09)
A higher-priority MoveWithinDistanceOfTarget goal pursues the current target to 2.5 blocks of center distance, subject to the original large hitbox and pathfinding collision. This supersedes the legacy melee-only chase and 0.22 movement speed: base speed is now 0.32 with a 1.25 chase modifier. The installed Mythic goal refreshes its path every ten ticks; nearby melee also tracks unseen targets. Follow/combat range stays 24 blocks. Scripted attacks, damage, knockback and facing remain unchanged.

Reference: [Mythic close-follow goal](https://wiki.mythiccraft.io/mythicmobs/Mobs/ai/goals/MoveWithinDistanceOfTarget). Next playtest: spawn a fresh Rancor in the palace and walk away or circle him to check sustained close pressure.
