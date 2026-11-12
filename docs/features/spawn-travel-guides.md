# Feature: Spawn travel guides and peaceful Massiffs
Status: playable, deployed
Branch: bilal/rebel-pilot-travel

## Player experience
A Rebel pilot at the user's saved Batuu anchor has the exact overhead title `Click to Travel`. Right-click opens the current public Galaxy Map. Cassian Andor at his separately saved anchor gives or refreshes the current handbook on each player's first successful interaction per America/Chicago calendar day. Further interactions give random private written hints, without immediate repeats. Three peaceful Massiffs walk around the spawn plaza with brief idle pauses.

## Rules
Both guide captures are immutable. Native mannequins are stationary, invulnerable and nonblocking, look toward nearby visible players, and have transparent title backgrounds. Main-hand interactions alone run actions; repeated interact packets are deduplicated. Cassian has a three-second chat cooldown. A full inventory or occupied cursor/crafting grid never consumes the daily entitlement or overwrites unrelated items. Player PDC stores only the last successful local date and is saved after delivery. No audio dialogue.

The Massiffs use separate native ground navigation; the static guide controller never freezes them. Their bounded footprint is X 2..18, Z -24..-6, Y 93. Initial centers are (4.5,93,-20.5), (11.5,93,-12.5), and (15.5,93,-21.5), selected from a native survey of the current spawn map. Every path node must stay inside this safezone footprint and have solid full-height support plus one-block surrounding clearance. Water, portals, doors, hazards and ledges are excluded. Bodies are silent, invulnerable, noncolliding and untargeted; native combat, mounting, taming, leashing and drops are denied. No Mythic hostile definition or skills are instantiated. Four bounded chunks at most are retained by this controller.

All five runtime entities have stable configuration IDs and private ownership markers. Nonpersistent bodies reconstruct at startup, owned stale duplicates are removed at startup/chunk load, invalid bodies/models reconstruct, and disable closes tasks/listeners/models/bodies/tickets. Existing nine scenery anchors and seven gray Jawa buyers retain their owners.

## Ownership and integrations
The existing DecorFeature lifecycle owns these additions. The existing registered native `warp` command owns the Galaxy Map; its actual executor opens the current menu. Selecting a destination continues through WarpService and TeleportService permissions, combat protection, destination safety and warmup. No destinations, menu slots or teleport policies are copied.

The existing registered `tutorial` executor owns handbook creation, current eleven-page content and duplicate-safe inventory refresh. Cassian delegates to it and records daily success only after the player possesses the marked handbook. `/tutorial`, starter delivery and existing book content are untouched.

Paper mannequins render the guides, as with existing Ilum NPCs. ModelEngine renders the original `massiff` rig; a narrow model adapter plays existing `walk`/`idle` clips. No new pack is required.

## Assets
No matching guide character was found in the project or recovered legacy sources. Reused the published [Rebel Pilot by Cipher_Punk](https://www.minecraftskins.net/rebelpilot) and [Cassian Andor skin](https://www.minecraftskins.com/skin/23529241/cassian-andor/). Signed Minecraft texture profiles were resolved with MineSkin. Pilot PNG metadata was stripped and compression changed without altering pixels. Textures are pinned in configuration rather than following a changing player account.

## Parameters
`decor/travel-guides.yml` contains schema version 1, enabled flags, immutable guide locations/profiles/titles, the America/Chicago timezone, Cassian messages/hints and bounded Massiff footprint/start positions. Restart applies configuration changes; ordinary code deployment uses the project's restart policy.

## Verification
The final recovery run passed 1,106 combined native assertions. All three destroyed renderers reattach to the same native identities, and removing one body reconstructs exactly three stable IDs. Native menu-click packets prove combat denial and ordinary warmup completion. Every Massiff walked 16–18 blocks inside the surveyed bounds. The seven existing gray buyer audits each report exactly one initialized `jawa_black` model with preserved eyes. The deployment checker separately passed 732 read-only assertions and self-disabled with zero listeners and zero noncancelled tasks, allowing a single production restart followed by removal of its own JAR. Scoped deployment is complete with one production restart. The live audit passed 732 read-only assertions and observed all three Massiffs physically walk 10.7–11.4 blocks. Its checker self-disabled with no remaining listeners/tasks; its JAR/data were archived and removed while Paper stayed running. Two stable gray-seven snapshots 24 seconds apart, all nine scenery anchors, both leaderboard entities, frozen 2,115 chunks and restored KOTH/next None were verified. All unrelated JAR entries/configs/plugins and the current helmet pack were retained byte-for-byte. Final deployed JAR SHA-256: `8b1c5be3ae203db5ae51647c0e9b65edda40947c99da9bca358cee89711b7f7d`. No pack publication was required. The server window was explicitly released. The first guide-only native run passed 29 checks with actual incoming mannequin entity interaction packets, current warp GUI slot equivalence, native menu permission denial, current eleven-page handbook, main/offhand deduplication, full inventory retry, independent players, native player save/load, Chicago midnight and DST boundary. Build passes 189 Java tests.

## Pause note
All five NPCs are live; the shared server is healthy and the window is released. Native validation is complete; real-client visual feedback remains. Next action: right-click the pilot and Cassian, then observe the three Massiffs walking around the spawn plaza.
