# Feature: Mandalore Beskar Rush
Status: staged; native validation passed, live rollout pending the shared window
Updated: 2026-11-11
Branch: bilal/beskar-event

## Player experience
During Beskar Rush, 25 exposed deepslate seams in the existing Mandalore mine become Beskar ore. Players contest the protected warzone and mine the nodes with diamond or netherite pickaxes. Each successful node yields one final stack of 1–5 existing Beskar scrap. The event has no mid-event refill. The current event item in `/events` shows `Beskar Ore Blocks Left: X/25`, including zero after depletion. Preparing and inactive entries have explicit status instead of stale counts.

## Accepted rules
The user's final count is **25**, superseding the earlier 100/30 requests and all historical supply proposals. The uniform shared 1–5 quantity averages 75 scrap per complete event, about eight sheets' worth through the existing nine-scrap refining chain. Fortune/Silk Touch cannot multiply or extract a node. Creative and weak tools cannot farm it. Cancelled breaks leave both the ore and remaining count unchanged. Native drops/XP are suppressed; the event is the sole reward writer for its registered cells.

Only the retained original connected mine/event warzone is used. The previously reverted wilderness caves are not recreated. Hosts are exposed plain deepslate with at least three deepslate neighbors and intact deepslate caps above/below. Existing decorative Beskar, ore elsewhere, supports, workstations and all wilderness remain untouched. The caps prevent a mined seam from becoming a player-sized passage into a block that will later restore.

## Ownership and lifecycle
The existing EventManager owns the sole schedule, start/stop/reset, menu refresh, travel and final-ten-second countdown. The feature registers `beskar`; it has no competing event timer. Default `enabled: true, weight: 0` follows the established manual-first plan: `/event start beskar` or `/event next beskar`. Stop the current event first when manually starting another. The existing hourly minute-55 end and 3,300-second maximum remain authoritative. Setting a positive rotation weight later is a separate configuration choice.

Native territory and clan protection consult EventMining only for an active, registered, unconsumed node with a valid Survival tool. This is not a general warzone edit bypass. Event mining does not create wilderness restoration baselines. All other cancellations remain authoritative. The shared `OreDropQuantity.roll(RandomGenerator)` implementation and tests come byte-for-byte from the progression owner's f370ab2; the Beskar rollout depends on its deployed class and does not replace its global OreDrops writer.

## Reachability and presentation
The final 25 positions were chosen using the actual native cave, a 0.6×1.8 player collision body, ordinary one-block movement/jumps, and unobstructed mining rays to exposed faces. A reduced 1,352-floor witness network connects each standing point to the known mine entrance at (0,8,-45). Every activation repeats a bounded live collision survey and validates all 25 hosts/warzone classifications before placing anything. Changed or inaccessible hosts refuse activation rather than exposing a smaller supply or overwriting edits. Existing ancient-debris-backed deepslate Beskar artwork and catalog identity are reused; no pack change.

## Persistence and exact restoration
Before any ore/marker placement, a small atomic fsynced journal records all original block states, generation and mining receipts. Each affected chunk stores its generation marker in the same native NBT transaction as its block states. End/admin stop/reset/clean shutdown restores owned ore or mined air to the precise original state and clears those markers, without drops. Non-event solid edits are preserved. Per-generation receipts remain durable: startup replays only a matching chunk generation, so an older on-disk active chunk after a crash is recoverable while completed receipts never overwrite later legitimate edits. Recovery handles one small receipt per tick and blocks activation until complete.

Each mining reservation is persisted before native removal; late cancellation releases it. Rewards are emitted once on the following tick only after the uncancelled native break produces air. A crash may forfeit a reward interrupted between its durable receipt and delivery; it cannot duplicate that reward. Cleanup never copies inventories or drops items.

## Verification
199 Java tests pass, including the shared owner quantity tests. The final rebased disposable native stage A passed 191 assertions: all 25 native Survival breaks, exact 1–5 final stacks, Fortune/Silk behavior, tool/Creative/late-cancellation denials, actual menu 25/25 →18/25 →0/25 and inactive status, and precise oriented host restoration. A coherent saved active chunk snapshot with 22 ore plus three mined holes was replayed after shutdown. Stage B passed 31 assertions proving exact restart recovery and that old receipts leave a subsequent legitimate air edit untouched. The final suite also proves an uncancelled notification without native block removal consumes nothing and pays nothing. Earlier exploratory native runs passed the same lifecycle while selecting the final body/ray-valid route configuration. Both final stages used a loopback disposable server, not the shared live server. Live scoped deployment/read-only smoke remain pending.

## Next action
After the progression quantity API rollout and the explicitly released shared restart window, rebase the scoped overlay on the current live JAR, install the configuration with a stopped backup, run read-only live smoke, and preserve all unrelated Jawa/HUD/frozen/recipe/travel entries. Hoth cave/zoning work is separate and must precede any Hoth wilderness baseline capture.
