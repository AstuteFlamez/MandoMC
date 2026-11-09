# Feature: Red-glass combat borders
Status: implemented, live-verified and installed; client appearance awaits playtest
Branch: bilal/combat-barriers

## Player experience
While a player has the existing 15-second combat tag, nearby safe-zone edges appear as a red stained-glass wall to that player. Movement into the safe zone is blocked until combat expires. Untagged players see the normal world and can enter normally.

## Rules
The wall follows effective WorldGuard PvP-deny or invincibility-allow boundaries, including region priority/overlap. Render only the safe-side surface near the player; real terrain is never edited. Global safe worlds have no artificial interior edges. A player already inside can leave. No operator bypass.

Check the entire movement segment, including fast movement and vertical entry, not just its endpoint. Tagged boat/cart riders are returned with their vehicle; living mounts such as horses have their movement cancelled. Combat teleports remain refused by the existing service. The denial message is throttled. Breaking/using a fake wall cannot remove the server-side restriction.

Restore the current real block data when combat expires, the player moves away, dies, disconnects or the plugin stops. World changes discard old visual state without sending old-world blocks into the new dimension. Never load distant chunks to render a wall.

## Ownership and parameters
CombatService remains the only tag/timer owner. CombatBarrier owns only boundary enforcement and temporary client visuals. WorldGuard remains protection authority; use its resolved flag queries rather than hardcoded landing coordinates.

`combat/combat.yml` gains `barrier.radius-blocks` (2..8, default 6), `blocks-below` (0..3, default 1), and `blocks-above` (2..6, default 4). Restart after edits. Glass is red stained glass; it updates after position changes and is refreshed periodically to recover from normal server block updates.

## References
The [official DeluxeCombat FAQ](https://deluxecombat.com/wiki/faq#how-can-i-enable-combat-borders) describes physical combat borders as packet blocks visible only to the affected player. This implementation uses the existing MandoMC timer and WorldGuard setup. [WorldGuard spatial-query documentation](https://worldguard.enginehub.org/en/latest/developer/regions/spatial-queries/) specifies resolved queries for priorities and inherited flags. Current [Paper teleport behavior](https://jd.papermc.io/paper/26.1.2/io/papermc/paper/entity/TeleportFlag.EntityState.html) retains passengers by default.

## Verification
Geometry unit tests cover all four sides, full-segment crossing, thin diagonal intersections, exact corners, negative block coordinates, vertical entry and bounded processing. All 42 Java tests pass. Temporary BarrierProbe passed 42 live assertions using live worlds/WorldGuard, isolated packet-recipient proxies and native boat/horse riders. It covers recipient isolation, real terrain preservation/restoration, entry cancellation, rejected-position rendering, WorldGuard priority, mounted movement, teleport-invulnerability regression and automatic 15-second expiry. The probe was removed and normal 12-plugin startup verified through the automated server controller. Client visual appearance remains a playtest check.
