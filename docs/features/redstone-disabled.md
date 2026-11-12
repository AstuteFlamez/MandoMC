# Feature: Disable redstone circuits
Status: live, verified 2026-11-12
Branch: bilal/redstone-disabled

## Player experience
Redstone circuits do not function in any world. There are no operator, region, clan, event or Ilum exceptions.
Electrum, its redstone-backed ores and storage blocks, mining loot, item identity and ordinary recipes remain available.
Independent plugin doors, inventory menus, vehicles and native hopper transfers keep their existing owners.

## Rules and ownership
`RedstoneCircuitBoundary` is the single MandoMC circuit policy boundary. Its startup agent installs native signal
cutoffs before Minecraft bootstrap; its plugin listener normalizes existing state and provides defensive event guards.
The native boundary is necessary because a redstone block supplies constant power without a Bukkit power-change
event, and already-queued dispenser/crafter/piston/command actions can execute after the input disappears.

The Java 25 agent suppresses weak, strong, analog and neighbor/control signal reads, including constant blocks,
dust, torches, observers, comparators, repeaters, daylight detectors, pressure plates, buttons, levers, trapped
containers, tripwire and sculk sensors. Scheduled dispenser/dropper/crafter actions and queued piston block events
are inert. Native daylight recalculation is inert too, avoiding recurring rejected power events. Redstone-dependent command-block ticks are inert; explicitly always-active command scripts remain
independent of redstone. This is a pinned Paper 26.1.2 integration, with required-hook validation and fail-closed boot.

The plugin clears powered circuit properties synchronously at enable and chunk load. Section palettes skip unrelated
terrain; only relevant sections are scanned, once per load. It acquires no chunk tickets, runs no repeating terrain
scan and force-loads no worlds. It resets electrically opened doors, lights and rails, and unlocks previously powered
hoppers. It changes block properties without physics, retaining block identities, inventories and every ore.
Static piston/head terrain is retained; new and queued piston moves are blocked. Existing water remains ordinary
water; Ilum's redstone/piston/button/dispenser course machinery has no exception and cannot drive new water actions.

Native local/manual interactions may retain their own animation; they cannot supply circuit power. Explicit plugin
block changes, GUI actions and direct independent gameplay APIs are outside native redstone propagation.
There is no feature switch or bypass permission. Installation and code changes require a normal restart.

## Build and deployment
`scripts/build.sh` also produces `redstone-agent/target/MandoMC-RedstoneAgent.jar`.
Install it as `server/runtime/agents/MandoMC-RedstoneAgent.jar`; the launch script requires that agent.
Deploy the matching plugin and launcher together while stopped. Do not attach to the running shared JVM or use reload.
Back up the stopped runtime, preserving all world/player/plugin state and the prior JAR/launcher for rollback.

For a scoped release, `scripts/build-redstone-overlay.py BASE_JAR OUTPUT_JAR --java-home JDK` patches the exact current
deployed SurvivalRules constructor and adds the boundary classes. Every other existing JAR entry is byte-identical.
Rebuild against the freshly verified deployed JAR when the restart window is assigned; do not deploy a stale overlay
over another task's work. The source constructor has the equivalent registration for future integrated builds.
No pack, catalog, loot class, recipe, progression, event, vehicle or animal source is replaced.

## Verification
Pinned Paper 26.1.2 build 74 / Java 25. The disposable `RedstoneProbe` runs the actual deployed plugin constructor,
catalog, loot and recipe code through the scoped overlay. It checks native signal methods, actual native neighbor
callbacks, queued dispenser/dropper/crafter/piston/command ticks, source/consumer matrices, saved world/chunk unload
and reload, hopper transfers, independent open state, Electrum identities/mining/crafting and a dense wire sheet over
constant power sources. The server stops itself after the probe; it never starts or stops the shared server.

Prepare with `scripts/build-redstone-probe.py SERVER_RUNTIME OVERLAY_JAR --java-home JDK`, then run the generated
`.local/redstone/run-probe.sh`. It binds loopback port 25691 and writes only disposable ignored data.
The runtime item configuration is copied read-only from the supplied runtime; testing does not replace that configuration.

The final isolated probe passes 709 assertions: 870 bounded initial/settling power events across 200 ticks, then zero events in the final 100 ticks. Loaded-state normalization took 4ms for the existing loaded fixture chunk.

The focused overlay compiles and verifies. Full Maven verification passes 221 tests when the two existing missing
KOTH messaging source dependencies are supplied temporarily from their owning branch. Those dependencies are not
part of this change. The unmodified KOTH checkout otherwise fails compilation on missing ChatStyle/ChatCategory.
The focused production overlay preserves all deployed messaging and KOTH code.

## Live rollout
Deployed against the exact post-Herd base `989c7a597d6c827d9e1463de8b465ee42a605c8877719414e8661cca74cdd7cf`.
All 480 unrelated existing entries remain byte-identical. The current launcher received only the agent requirement
and Java agent flag; its memory options, argument forwarding and normal pack-sync behavior are retained.

Live plugin SHA-256: `2fb70b25b406df3d619b32d03b19f38426e1afe971d6a7ba9c65679f9d2241f7`.
Live agent SHA-256: `a0b19da28369de724979767342fe402a19fafff8d9b764296fe9acceec171027`.
Live launcher SHA-256: `4aee2621b2d5885b814592f574a5e4d2a4bd3e435f054fc1a709326e530f8e62`.

A full stopped-runtime backup contains 4,722 entries before any loaded-state normalization. Final ordinary startup
completed at 13:30:40 America/Chicago. All seven native hook classes validated, and 201 read-only assertions passed
across two snapshots after gray reconciliation: seven gray hosts with stable UUIDs, nine decor anchors, both boards,
all 2,115 frozen baselines, the current Herd owners, and the effective native pack. No actors or world fixtures were
created. The checker self-disabled and its JAR was removed.

Two normal restart cycles were required: the first unchanged pack-sync step selected newly published `4a7e3d355c2d`.
The health check rejected that drift. A recovery launch restored the backed-up, hash-verified `2c075adc803b` cache
with one-time offline sync. The normal launcher was immediately restored afterward. The effective live pack is
again `2c075adc803b28496eb6a7d5f8164a6c7cc85c87c0cb7ed83ceb4139459e8f54`, SHA-1
`0fe2d63160a1cf1d570d39348ee88d0e656cccc1`. Future normal online starts can select a newer published pack;
coordinate that explicitly rather than treating the cached pack as permanently pinned.

Current KOTH / next None was restored at 13:30:57, clearing the inherited unintended Jabba queue. The existing
normal event lifecycle resets KOTH capture progress and timer on restart; no unsupported state restoration was used.
Ilum's actual redstone/piston/button/dispenser-driven water course machinery is disabled, as requested; its independent
NPC/plugin behavior retains its owner. The restart window is released.

## Next action
Integrate this feature and preserve the mandatory startup agent during future scoped releases so redstone cannot
be restored by an older build or launcher.
