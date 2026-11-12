# Feature: Disable redstone circuits
Status: verified implementation; staged, not deployed
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
are inert. Redstone-dependent command-block ticks are inert; explicitly always-active command scripts remain
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

## Pause note
Implementation and isolated native checks are complete. No shared restart, deployment, terrain scan or resource-pack
change has occurred. KOTH has released its prior restart window; this task awaits explicit assignment.
Next action: after the coordinator explicitly assigns this task a restart window, rebuild the scoped overlay from
the then-current deployment, back up the stopped runtime and deploy the matching launcher/agent/plugin atomically.
