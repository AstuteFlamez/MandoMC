# Mandalore and the beskar cave
Status: proposed design; implementation and import remain deferred.
Updated: 2026-11-09

## Player experience
Mandalore has three land types: a protected spawn safe zone, a small event warzone,
and claimable wilderness. Players settle in the wilderness and return to contest a
shared underground beskar mine when its event becomes active. Carry ore home and
refine it through the existing metal progression. PvP and ordinary death drops make
mining and the return journey risky. No separate Survival world is needed.

This three-zone structure is user-selected. Every dimension, timer, node count and
mob count below is a proposal for playtesting, not an approved setting or live change.
Tatooine/Hoth boundaries still come from the user and are not set by this design.

## Map source and limits
- Creator: LeonisBuilds.
- Listing: https://www.planetminecraft.com/project/custom-mandalore-star-wars-nuked-planet-map-500x500-blocks/
- Title: Custom Mandalore Star Wars Nuked Planet Map 500x500 Blocks.
- Listed release: 16 November 2025; Java and Bedrock; Patreon-only ZIP.
- The user will download it. No map has been downloaded or installed by this design.
- Inspect the actual Java payload, import origin, terrain depth, build limits,
  entities, command blocks and creator terms before implementation. The listing's
  size field is not proof of a complete world download. Give LeonisBuilds credit in
  world-source documentation and the Mandalore menu description when installed.
- Placement is conceptual: no terrain survey or exact coordinates are claimed.
  If the surface is shallow, extend its stone substrate before carving the cave;
  never cut into the void or depend on assumed underground terrain.

## Planet layout and territory
Start with an approximately 48 x 48 block spawn camp and a 128 x 112 block event
footprint. Both are chunk-aligned and adjacent, leaving most of the 500 x 500 map
as wilderness. Final boundaries depend on the actual terrain and the user's selection.

The camp sits on a defensible surface ledge, overlooking the ruined landscape.
Give it at least two walkable exits into wilderness. Put the cave's two surface
entrances inside the event footprint, near different edges, with a visible route
from the camp. Players should not need to cross another player's claim to enter.

Safe zone: no PvP, hostile spawns, terrain destruction or player claims. Existing
combat-tag entry restrictions still apply; spawn is not a combat escape exploit.

Event warzone: PvP allowed, terrain protected, claims denied, ordinary building
and explosions cannot alter it. The region includes the cave, approach ground,
both entrances and the entire vertical columns of its reserved chunks. This avoids
players claiming the cave roof/surface while the event controls blocks underneath.
Only the event's registered ore nodes may be mined, during the active phase.
These restrictions remain even when the event is inactive.

Wilderness: normal survival building, gathering and player claims, subject to the
existing clan/raid rules. Static safe-zone/event reservations always take precedence.
No event regeneration touches wilderness blocks, claimed land, player containers
or bases. Do not silently turn the whole planet into a warzone.

WorldGuard owns static protection. FactionsUUID owns claims. One shared eligibility
check rejects claims intersecting reserved chunks, including auto-claim, admin claim
tools, restoration/import and future expansion paths. Do not maintain a competing
custom claim ledger. The deferred claim integration must land before advertising
Mandalore wilderness as claim-ready.

## Cave architecture for the builder
Working name: the Glassed Mines. Target roughly 100 x 80 blocks of usable cave
footprint within the event reservation, with 25–35 blocks of vertical relief.
Leave approximately 15–20 blocks of sound roof above major rooms where terrain
permits. Move or deepen the cave if those margins do not fit the downloaded map.

1. Main breach: a ruined mine ramp, roughly seven blocks wide, visible from the
   approach. A switchback and broken machinery interrupt long spawn-to-cave sightlines.
2. Secondary entrance: a separate fissure or collapsed freight shaft, approximately
   60–90 blocks from the main entrance. It must work both ways without parkour,
   flight, consumables or a one-person ladder choke.
3. Outer loop: two seven-block-wide passages link both entrances and all galleries.
   A connecting cross-passage prevents one defended doorway from sealing the cave.
4. Three galleries: approximately 22 x 18 blocks each, with distinct identities:
   an abandoned drill hall, a broken foundry and a deep mineral fault. Split ore
   evenly between them, so a single group cannot cover every node from one position.
5. Central landmark: a large inert forge or exposed metallic fault, around 25 x 20
   blocks. It connects the loop and aids navigation; it is not another capture point.
6. Cover and height: two to four blocks of meaningful elevation variation, broad
   stairs and rock/machine cover roughly every 12–18 blocks. Avoid a single sniper
   balcony dominating all three galleries or mandatory jumps over lethal drops.

Use the supplied surface palette where possible. For the custom cave, use dark
rock, scorched supports, cracked industrial flooring and restrained metallic seams.
Three different landmarks and clear lighting guide navigation. Beskar nodes use
the existing themed ore art; decorative mineral veins must look different from
mineable event nodes. No new radiation meter or special movement mechanic is required.

Keep vehicles on the surface. Prevent vehicles from deploying inside the cave;
entrances need believable geometry and an explicit vehicle rule, not hitbox luck.

## Event: Beskar Rush
One public shared event, using the existing server event rotation and active-event
owner. It does not run on a second timer alongside KOTH or Jabba. First activation
is manual; automatic rotation is a later configuration choice.

The cave is always explorable. The event activates ore, not entry doors. Players
may scout or contest it beforehand. There are no private instances, tickets,
keycards, mandatory boss kills or automatic participation rewards.

Proposed lifecycle:
- Idle: cave remains accessible; no active beskar nodes.
- Warning: five-minute server announcement. No ore yet.
- Active: fifteen minutes. Twenty-four registered nodes, eight per gallery.
  Nodes activate at start; depleted nodes regenerate at minutes five and ten.
  An unmined node remains one node and never stacks extra rewards.
- Ending: reuse the established final-ten-second event countdown for cave/event
  occupants. No additional ore pulse occurs at minute fifteen.
- Cleanup: active ore reverts to its original rock; event-owned warriors are
  removed. The cave stays open and nobody is trapped, killed or forced into a
  safe zone. Normal combat and travel rules continue. No whole-region reset.

One harvested node gives one existing `beskar_scrap` item. Use the debris-backed
Beskar ore appearance and require a diamond/netherite pickaxe for the first slice.
Silk Touch cannot remove a node as an ore item; Fortune gives no event multiplier.
Creative/admin mining never grants rewards. Ordinary ore mining elsewhere retains
its existing progression behavior; the special rules apply only to registered nodes.

This creates a ceiling of 72 scrap per completed event if every node is harvested
in all three pulses. Existing recipes use nine scrap for one Raw Beskar, then blast
smelt Raw Beskar into a Beskar Sheet: at most eight sheets' worth of new material.
That is a proposed server-wide supply budget, not a guaranteed per-player reward.
Rebalance node count/cadence after playtesting without changing recipe identities.

Players keep what they successfully mine; normal full-loot deaths allow others to
take it. There is no ownership lock on player death drops. No bonus beskar from
event-end participation, player kills or warriors: the node budget is the supply.
Players leave through either entrance and travel home under normal combat rules.

## Mandalorian warriors
Use hostile rival-clan scavengers as the event defenders; keep the spawn camp safe.
Begin with six defenders across the galleries. Reinforce on the two ore pulses,
with a proposed maximum of twelve living event warriors. Use existing shared
hostile-facing and combat/protection integrations. AI should challenge miners and
patrol galleries without pursuing players into the safe zone.

Kills do not unlock mining or gate passages. Warriors protect the resource through
pressure, making this a contested mining activity rather than a copy of Jabba's
room-clear dungeon. Bosses and surface patrol ecology are later extensions.

## Ownership and implementation architecture
- Existing `EventManager`: schedule, announcements, sole active event and shutdown.
- A `BeskarEvent` implementing `ManagedEvent`: lifecycle, pulses and event-owned mobs.
- Node registry: configured world key + block position, original block data,
  expected active appearance, node state, pulse number and generation ID.
- Existing `ItemCatalog`: sole factory for Beskar Scrap and existing recipes.
- Existing metal mining integration: one coordinated event-node override; do not
  let both ordinary mining and the event grant a drop for the same block break.
- MythicMobs/ModelEngine: warrior definitions and AI; event service owns cleanup IDs.
- WorldGuard/FactionsUUID: static reservations and claims, respectively.

Validate positions/region coverage and save original block data before activating.
Only allow break access for the exact active registered node, player, world and
tool. Do not broadly uncancel protected block breaks or grant a world-wide bypass.
Block pistons, explosions, fluids and ore-item placement from moving/copying nodes.
Administrative edits that make a node differ from its expected block suspend that
node and report it; do not overwrite unknown player/admin changes during reset.

Persist event generation and node consumption. A node's generation/pulse reward
can be consumed only once, even for simultaneous breaks, relogging or chunk reloads.
Persist a reward reservation before granting items; reconcile inventory/drop delivery
with an idempotent grant record. Do not claim crash-safe exactly-once delivery from
a plain YAML save followed by an inventory write. Ambiguous grants fail closed and
are logged for reconciliation, rather than being blindly paid again after restart.

On restart, abort the interrupted event, restore its managed nodes, clean its mobs
and do not replay elapsed pulses or auto-grant rewards. Existing inventory items
stay with players. Reset work is bounded to registered nodes; no wilderness rollback.

## Smallest playable slice and acceptance checks
First import/inspect privately, build the cave, mark exact reservations and twenty-four
nodes, then manually run one fifteen-minute event. Clans/claims must be integrated
before enabling public claiming; no new clan system is part of this event.

Verify two usable entrances, no dominant single choke, no safe-zone combat bypass,
claims denied inside reservations and allowed in eligible wilderness, correct tool
rules, one reward per node, bounded pulses and the 72-scrap supply ceiling. Exercise
death/drop behavior, full inventory, explosions, pistons, Silk Touch/Fortune, Creative,
simultaneous breaks, chunk reload, interruption/restart and administrative reset.
Prove that player bases and containers outside the event reservation are untouched.

## Decisions still needed
Exact safe-zone/event boundaries and cave placement follow the imported map survey.
The user supplies territory boundaries. Proposed dimensions, event supply, warrior
strength and rotation weight require playtesting. No runtime changes are authorized
by this design brief; implementation remains deferred until requested.
