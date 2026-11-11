# Original Ilum and native crates

Status: deployed and verified on 2026-11-10.
Branch: bilal/legacy-ilum-crates.

## Player experience

`/warp ilum`, `/ilum` and `/parkour` enter the original user-owned Ilum world. This replaces the generated
Ilum review, which is archived rather than deleted. The original course geometry, four heavy pressure-plate
checkpoints, nine floating hints, Ahsoka teleport and Obi-Wan finish conversation are retained. Original
Ilum4kx4k is not the course and is not imported.

Entering stores the player's complete inventory, armor, offhand, selected slot, mode, health, food,
saturation, movement/flight settings and potion effects before issuing temporary controls. The barrier
leaves, compass retries the last checkpoint and red bed restarts. Original buttons work even with a
control selected. The current time, personal best and checkpoint appear in the action bar.

Ahsoka requires checkpoint two and teleports only the speaking player to the upper course after the
recovered dialogue. Obi-Wan requires all four checkpoints and proximity to the original finish location.
The conversation and final reward use the same ten-block range, and the final dialogue line completes
the run immediately. Walking farther away shows an explicit retry message.
Finishing returns the player to the Ilum start and awards one Kyber Crate Key, with a rolling 24-hour cooldown.
The old hologram already advertised this limit; it is now enforced. Replays can improve the best time
without granting another key. The old placeholder economy/diamond commands are replaced by this key.

The Kyber Crate stands beside Batuu spawn at (3, 93, -16). Left-click previews the actual weighted pool;
right-click spends one matching key and starts a short roll. `/crates preview kyber` and `/crates open kyber`
provide the same flow. The matching Ilum planet icon is in Galaxy Map slot 20. Both its artwork and the
key artwork reuse the original user-provided PNGs, without changing their pixels.

## Multiplayer, recovery and safety

The map is shared; run IDs, ordered checkpoints, clocks, dialogue and rewards belong to individual players.
Players cannot skip checkpoint numbers or reset another runner by punching them. Environmental failures
retry the current checkpoint. The scoped collision team prevents player pushing and restores prior teams.

Inventory escrow is stored in native player persistent data before temporary items are issued. Gear
restoration, run completion, cooldown and queued key delivery are committed with the same native
player-data save. Disconnects and clean shutdowns end runs and restore gear. Interrupted runs recover on
the next login and do not grant a finish reward. Active saber flights finish before inventory capture.
Potion effects are restored with their remaining duration rather than extended by time spent in the course.

Ilum is a whole-world native safezone. Player edits, PvP, hostile spawning and arbitrary old command blocks
are disabled. The territory owner explicitly allows original mechanisms only in configured safezone worlds:
plates, pistons, contained fluid flow, dispensers and hopper transfers. Protection remains unchanged in
other worlds. A narrowly scoped native spawn authorization admits only each explicitly placed NPC entity;
it never uncancels another listener's denial. Ahsoka and Obi-Wan use native skinned Mannequins, with no
Typewriter, FancyHolograms or other NPC plugin requirement.

Staff can use `/parkour inspect` for Creative inspection. Staff teleports during a run mark it as practice;
practice cannot earn keys or leaderboard records. Leaving restores the prior mode. Public starts use the
existing travel/combat policy. `/parkourfinish <player>` is an administrative compatibility command that
still requires the checkpoints and finish location.

## Crate system

`crates/crates.yml` defines named crates, catalog-backed rewards, positive integer weights, quantities,
physical or virtual keys, and optional bound containers. Multiple crates share one CrateService. There is
no separate economy or arbitrary console reward executor. A crate supports up to 28 reward entries.

The Kyber pool is blue 30%, green 30%, yellow 20%, purple 15%, orange 3.5%, red 1%, and white 0.5%.
These percentages come from the same integer weights used by server selection. Every prize is the
existing native catalog crystal, preserving stable IDs and the current resource-pack models.

The outcome and key consumption are saved before the opening GUI appears. Closing/skipping/disconnecting
cannot reroll it. The result is retained if space disappears and can be claimed with `/crates claim`.
Transaction IDs stop delayed callbacks from a closed inventory affecting a newer opening. Preview/roll
items and gray filler panes are protected against ordinary, shift, number-key and drag transfers.

`/crates key give <crate> <online-player> [1–64]` grants keys to an online player, queueing physical keys
safely if the player's inventory is unavailable. `/crates keys` reports usable keys. Administrators can
bind an existing container with `/crates setblock <crate>`; its existing contents are preserved.

## Implementation and deployment

The native services are owned by the existing CommandBindings lifecycle, registered with Bukkit's typed
service registry after bootstrap. The installed shared bootstrap is not replaced. This preserves newer
Jawa and recipe work that has not yet been consolidated into every source branch. Crate defaults add only
their own catalog definition and reload the existing catalog owner.

`scripts/import-legacy-ilum.py` stages byte-identical original terrain regions, separate Paper overrides
and void generation outside existing chunks. It excludes old player data and active entity/plugin state.
Source regions remain untouched; Paper performs native chunk data conversion on the new copy.

`scripts/build-ilum-overlay.py` overlays only the new packages and reviewed binding/warp/territory entries
on the exact installed combined JAR. All other class/resource entries are verified byte-identical.
Do not deploy a full build from an older checkout over concurrent Jawa, recipes or clans work.

`scripts/build-ilum-pack-overlay.py` adds six namespaced Ilum files to the latest published archive. The
published pack is `resource-pack-26.1.2-fbdcedea9ae1`, SHA-1 `e8ebbc8c4d626b7b8d927780aca18c132fcceac2`.
All 1,135 entries from the Jawa-era baseline remain byte-identical. The user explicitly approved this
publication in chat after automatic review declined to accept the recorded standing authorization.
Anonymous download, archive checksums and the server's normal pack-sync validator passed.

## Verification and next action

The Java build passes 183 tests. Pack reference validation and eight pack-model tests pass. Import
validation checks all four original plates and records SHA-256 for every staged region.

`scripts/build-ilum-probe.py` builds the isolated native fixture. Its full suite uses separate native actors
on the cloned test server, exercises both conversations, inventory snapshots/restoration, independent
checkpoints, daily reward limits, crate preview protections, rapid clicks, fixed outcomes, full inventories
and pending-claim recovery. Production smoke mode creates no actors and checks actual content/services.
The original suite passed 198 native assertions. The completion-fix suite passes 199 native assertions
through actual native client interaction packets, including moving seven blocks away during Obi-Wan’s
conversation, immediate completion on the final line, inventory recovery, key/cooldown persistence
and crate controls. The deployment receipt records the exact deployed JAR hash and live smoke result.

Live smoke verification passed 27 native assertions. The temporary probe was removed and the normal
server restarted cleanly. Deployed JAR SHA-256 is
`0ec9524b7835223a83f346b133af707db6960e10d3c382bce2aaf783f5f792d5`.
The active newer Jawa pack `resource-pack-26.1.2-38232778f1ab` preserves all six published Ilum entries
byte-identically. Runtime world/config/player backups and deployment receipts remain private.

Next action: reconnect with the updated resource pack and playtest the original course with two clients.

## Completion playtest correction (2026-11-10)

The player reported that Obi-Wan spoke after all four checkpoints but did not reward or return them.
The old conversation accepted a ten-block radius, then independently required six blocks for completion
after an extra silent 2.5-second delay. A native packet replay reproduces the failure at seven blocks.
Completion now uses the same radius as the conversation, runs on its final line and returns to Ilum’s
start as requested. Leave/recovery still use Batuu. No reward eligibility or inventory guarantees change.
`scripts/build-ilum-overlay.py --finish-only` preserves all concurrent clan, crate, Jawa and recipe bytes.
`scripts/deploy-ilum-finish.py` verifies that exact tested candidate before installing with private
player/config backups and actor-free live smoke checks.

Completion fix deployed SHA-256 `09cd9cc0626958f39aaaad35af45071ffd6097e8d58f2fa4f6dafeec5970d338`.
All 27 live smoke assertions passed; temporary checker removed, normal server restarted cleanly,
shared restart window released. Next action: player retests Obi-Wan’s finish conversation.
