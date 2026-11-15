# Feature: Interactive tutorial
Status: playable on local development; client appearance/control verification pending
Branch: bilal/interactive-tutorial

## Player experience
New players enter their private training scene after the required resource packs load. `/tutorial` replays it; `/tutorial end` leaves immediately, as advertised by the persistent boss bar. Objectives use the action bar and nearby holograms. The map lesson deliberately uses chat and no hologram. Each successful action unlocks the next objective.

The route is staging → `/warp` and the central Tutorial diamond → safezone → display `/c map` → walk into wilderness → `/c create <name>` → `/c claim` outside the one-chunk safezone buffer → `/c disband` → kill three Gamorrean guards → throw Vader's saber once and hold it after its return for at least one second → buy supplies from brown Jawas → sell supplied scrap to a gray Jawa → return to the safezone starting point facing straight ahead → open and close `/recipes` → place the supplied TIE fighter item → board it and fly airborne for ten seconds → hit a stationary target with its cannons → read the three event explanations.

First completion gives 5,000 credits and the existing starter kit. The old first-join kit grant is deferred until completion. The existing once-only kit receipt is honored, so returning players who already received it do not get a duplicate. Normal 2,500-credit account initialization remains unchanged. All future completions are practice only.

## Rules
- Combat, active blaster reloads, mounted vehicles (including ModelEngine virtual seats), other temporary-inventory activities, pending crate rewards and unfinished trades block entry. Being mounted is treated as unsafe for tutorial entry; it does not introduce combat-logout deaths for peaceful travel elsewhere.
- Each player has a separate world, temporary inventory, fake clan and claim, practice wallet and training ship. Real clan membership and claims are never edited. No practice items, transactions, drops, experience or ship records leave the tutorial.
- `/warp` displays the normal configured Galaxy Map, with the central Tutorial diamond as its only working selection. Other planet icons keep their normal appearance but cannot travel or advance the lesson. A map must actually display. The clan lessons use the normal Safezone/Wilderness/own-claim territory titles and timing, including claim creation and disbanding. Creating, claiming and disbanding operate only on the session. Merely typing a later command cannot skip an objective.
- The player cannot die. The TIE fighter cannot be damaged, packed or dismounted during the flight lesson. Flight uses the entire private world up to its normal world border; `/tutorial end` still works while mounted. Ammo replenishes when depleted.
- Gray Jawas are introduced as salvage buyers also found on Batuu (spawn). Brown Jawas require a purchase of four oak saplings using $40 of the $100 practice wallet. Gray Jawas require depositing and selling eight Agrinium Scrap for $6. These mirror normal offers, but never call the real credit ledger. Closing the sell tray returns its temporary contents.
- After the gray-Jawa sale, the player returns to the safezone starting point at a level pitch before the recipe instruction appears. The real recipe guide remains browsable; closing it advances. The flight lesson initially supplies an TIE fighter in the hotbar without spawning one in front of its text. A valid nearby ground placement consumes that item and unlocks boarding/flight; denied placement cannot advance. Event information has three nine-second pages: KOTH, Jabba's Palace and Beskar Rush. No event participation is required. Each page has a decreasing boss-bar timer and a hologram/action-bar countdown saying no action is needed. The final page explicitly counts down to tutorial completion.
- Physical crate deliveries remain in persistent mail until normal inventory is restored; queued deliveries cannot be consumed into the temporary inventory. Only the current lesson's commands and menus are allowed. Crates, auction trading, external containers, building, portals, external teleports, dropping items and unrelated commands are blocked, including namespaced aliases. Inventory rearrangement is allowed; crafting and transfer shortcuts are restricted.
- Leaving or disconnecting cancels both vendor weapon-hand tasks before restoring inventory. Leaving or disconnecting restores the saved normal inventory, armor, offhand, experience, mode, health, hunger, effects, flight flags and return location. A fresh player returns to Batuu. If the original world is unavailable, Batuu is the fallback. An interrupted tutorial restarts from the beginning when requested again.
- A completed tutorial is saved before reward processing. The credit ledger stores its receipt atomically with the balance. Lost callbacks are safe to retry. Full inventories defer the kit; the kit and its receipt save together in player.dat. Restoration failures retain recovery data and disconnect the player instead of exposing a temporary inventory.

## Ownership and integrations
`InteractiveTutorial` owns session transitions and input restrictions. `TutorialInventory` keeps the original state in player.dat before any temporary inventory replaces it. `TutorialWorlds` owns five prewarmed, procedurally generated private scenes; a bounded pool can expand to sixteen concurrent lessons. Overflow worlds unload on release and can be reused. Pool leases have unique tokens so a canceled preparation callback cannot release someone else's room. No existing worlds are copied, deleted or overwritten.

The saber step waits for the native throw to finish and then requires at least one continuous second holding the returned saber; putting it away restarts that holding interval. The flight target is a 17-block luminous bullseye over the center of the world, with a matching large hit area and long display range. TIE cannons receive Tibanna Gas Cells and work throughout the private training world.

Native clans remain authoritative outside lessons. The native territory map renders the tutorial safezone, wilderness and the viewer's practice claim without adding ClanState records. MythicMobs and ModelEngine provide real Gamorrean and Jawa models. Existing saber code reports a successful native throw. VehicleService creates ephemeral TIE fighters using native pilot controls; their identities are excluded from the ownership journal. VehicleCannons reports direct swept-bolt impacts against the lesson target. The real CreditLedger is the only owner of completion credits.

The existing handbook stays in the starter kit as a reference. `/tutorial` now starts practice rather than replacing the book. Production promotion is outside this development-only rollout.

## Parameters
Configuration: `tutorial/tutorial.yml`; changes require restart. Defaults: five warm worlds, sixteen maximum concurrent lessons, 5,000 completion credits, three guards, ten seconds of airborne piloting and nine seconds per event page. Item IDs and all instructional text live in that file. Instance geometry and the ordered curriculum are versioned code.

## Verification
The reactor passes 325 gameplay tests and four access tests; all 75 tooling checks pass. The final native walkthrough passes 240 assertions and actual JVM restart recovery passes 51. GitHub Server checks pass for the merged feature. Unit tests cover forbidden step skips, three-guard completion, airborne/pilot timing, bounded elapsed time, terminal completion, clan-name validation, credit receipt persistence, failed-write retry and the balance limit.

`scripts/build-tutorial-probe.sh` builds a disposable localhost:25590 probe with native players. It exercises real command/menu dispatch, concurrent worlds, isolated clans/claims, guard deaths, the native saber throw, practice purchase/sale, the real recipe guide, actual ModelEngine boarding, airborne time, native cannon impacts, completion, replay, inventory restoration, reward retry, deferred external crate keys, real-clan preservation and exit during a real weapon reload. A second JVM boot reinstates a captured mid-tutorial player.dat and verifies all slots and the persisted credit receipt. Never install this mutation probe on the shared dev or production server.

Client rendering and physical keyboard/mouse interaction require an ordinary Minecraft playthrough; native callbacks are not presented as visual verification.

## Initial development deployment
Merged through PR #28 into dev (`c0ea13d`). The local development server was stopped cleanly, fully backed up, updated with the exact native-tested MandoMC JAR and new tutorial configuration, then restarted. Existing handbook/travel-guide wording was patched without replacing private placements. All twelve other binary hashes and the pinned resource-pack URL/hash remain unchanged. No test probe was installed on shared development. Startup has no errors, the tutorial command resolves, and all five warm worlds load.

Deployed MandoMC SHA-256: `f7c0d973c843f0c9b6766949aacd082c11e009a54fff2dbdf8f754ce91bd85f8`. The full runtime backup, native logs and deployment receipt are retained locally under ignored `.local`; no runtime data is tracked. Production was not changed.

## Pause note
Next action: play `/tutorial` in an ordinary Minecraft client on dev, checking hologram readability, model appearance and physical flight/shoot controls, then adjust from that feedback.

## Playtest follow-up (2026-11-15)
The Galaxy Map now shares the regular menu renderer; tutorial input remains isolated. Territory presentation reads a session-only claim identity without touching real clans. TIE fighter placement goes through the normal block-interaction entry point and still creates only an ephemeral training vehicle. The recipe return resets position, yaw and pitch before rendering its instructions. Event timers refresh each second and retain the exit command in the boss bar.

The reactor passes 330 Java tests and all 75 tooling checks. The expanded native walkthrough passes 412 assertions, including the original five changes plus the returned-saber pause, Batuu buyer explanation and full-world TIE flight. Actual JVM restart recovery passes 51 assertions.

Merged the playtest follow-up through PR #30 into dev (`95b2218`) and deployed its exact native-tested JAR after a clean stop and full runtime backup. Startup has no errors, five warm worlds load, and the tutorial command resolves. Only the MandoMC JAR and tutorial configuration were deployed; twelve other binary hashes and the pinned resource pack remain unchanged. Follow-up JAR SHA-256: `6be3f7b01f8b66a926354cd23e781fa1c955fb37cc2c9cad852f5109e6da15c1`.

The user-requested dev account progression reset was rehearsed on copied data, then applied while stopped. It clears onboarding/reward eligibility, credits, clan membership, owned vehicles and remaining account progression while preserving staff access and other players’ records. Backup and detailed receipt remain local and untracked. Next action: a fresh first-join client playthrough for the revised controls, hologram readability and large target.

## Compact action bars (2026-11-15)
Tutorial action bars show one short action at a time. Longer context stays in holograms (or chat for the map lesson). Event action bars contain only the event name and countdown; they no longer concatenate the full explanation and automatic-transition sentence. Flight instructions change from boarding to takeoff to airborne progress. Tutorial vehicle controls/errors and the completion starter-kit confirmation are compact too.

A regression check renders every configured tutorial action-bar template at maximum counter values and checks a conservative 240-unit standard-font width budget (seven units per ASCII character, ten for other glyphs). The native probe checks the actual assembled instruction at each observed step and verifies the three flight prompts. These are conservative text checks; actual client GUI/font rendering is still verified through play feedback.
