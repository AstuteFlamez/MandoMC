# Resume here
Updated: 2026-11-07

## Milestone
Current: `bilal/spawn-teleport` extends the arrival branch with `/spawn` -> SpawnWorld and temporary `/survival` -> SurvivalWorld (Multiverse alias of the existing dev_world). Both use one configurable three-second countdown with colored titles, rising sounds, position-only cancellation and a double-helix arrival effect. Six unit tests and 53 live-world/simulated-player assertions pass. The feature is deployed locally; visual/audio feel awaits a client walkthrough. The future tutorial library is recorded in docs/commands.md and remains deferred.

## Accepted direction
Laptop development, selective recovery, and the public/custom division in architecture.md. Design player experience, choose playable feature order, build parameterized systems, then configure content. Merge features after validation and satisfaction.

New arrivals start in the existing SpawnWorld map, receive a short tutorial, and go to survival to establish a base. Death loses carried equipment; group stockpiles support re-equipping. The first friends playtest should combine the discussed systems in a rough integrated version; individual development slices build toward it.

Survival has a fixed protected landing zone. Solo players can create a group and claim a starter base outside protected spawn without a credit requirement. Respawn priority: group home, valid bed, SpawnWorld. A possible three-hour group creation cooldown remains tentative.

Player-facing identity must replace Factions and `/f`. Final naming is pending; Clans (`/clan`, `/cl`) is the advisor recommendation, with Crews and Syndicates as alternatives. FactionsUUID remains the underlying integration.

Combat blocks voluntary teleports; logging out in combat causes death. First join grants a basic blaster, vanilla tools and food once. Starting credits remain undecided.

Lost equipment is reacquired through its original acquisition route if no stockpile is available. Credits come from both resource selling and activity rewards. Resource planets are deferred, alongside force powers.

Accepted acquisition loop: sell survival resources to buy basic blasters/ammo; craft basic armor from survival materials; earn credits and rare components through KOTH/Jabba; craft lightsabers and advanced equipment with those components. Prices, recipes and quantities are still open.

The user wants a large power gap between basic and top-tier equipment. Their existing saber approach uses shield models/blocking, reflection and a returning throw. Keep configurable guard/overheat limits and vanilla enchantments. Remove ordinary swords, bows, crossbows and shields; recognized custom sabers remain exempt. Enchantment compatibility and repair behavior need verification.

Jabba is a scheduled shared PvP dungeon with open loot. KOTH victory/capture belongs to groups, with solo participation allowed. Both events allow players to re-equip and return after death. Under the user's delegation to conventional KOTH rules, use continuous group capture, reset when all holders are cleared out, and a configurable three-minute initial timer. Enemy presence alone does not reset it; this differs from the recovered implementation.

Warpoints are pairwise net-kill scores, starting at zero. One opposing-group kill is +1 for the killer's side and -1 in the reverse view; unrelated pairs are unchanged. KOTH/Jabba PvP counts. A configurable net-kill lead gives only the leading group breach rights against that rival, including while defenders are offline. The numeric threshold is undecided. Approved explosive breaches and accessible chest looting are allowed; defenders retain ownership. Passive time-based regeneration is not part of this model.

When an active attacker's lead drops below the threshold, that score drop starts a configurable continuation window for the same pair. Unrelated groups can enter a breach and pick up dropped items, but cannot break blocks or open the owner's chests. Timer duration, active-raid detection and retrigger behavior remain feature-brief details.

Aircraft are transport-only initially; aerial combat is deferred. Vehicles start as credit purchases; all equipment, including blasters and vehicles, eventually moves toward crafting. Only the placing player may pilot; anyone may occupy passenger seats, with the same permission intended for future gunner seats. Enemies may destroy vehicles outside protected areas but cannot pilot them. Destruction permanently loses the vehicle. The placer can pack it into an inventory item only while stationary, empty and out of combat.

Research-backed travel recommendation is recorded in docs/player-experience.md as a proposal, not accepted policy: free commands/menu plus optional themed terminals, fixed public arrivals, group home, and a proposed five-second warmup. Published server guides support this as a comparable design; player preference remains a prediction to test.

## Implementation
Repository layout is `plugin/` for Java source, `server/runtime/` for the ignored runnable server, and `resource-pack/` for the new pack source. Paper 26.1.2 build 74 / Java 25 is the newest documented common baseline verified for the selected plugin stack; 26.3 is newer but is not the validated dependency target. Exact artifacts are pinned in server/paper.lock.json and server/plugins.lock.json.

All selected public vendors are installed fresh: WeaponMechanics, MechanicsCore, PacketEvents, ModelEngine free edition, MythicMobs, FactionsUUID, WorldEdit, WorldGuard, Multiverse-Core, EssentialsX Core and Vault. With MandoMC, all 12 plugins enable together. FactionsUUID uses the complete official Maven 4.6.0 artifact. MythicMobs is the official 5.14.0 release whose descriptor reports 5.14.0-SNAPSHOT-local.

MandoMC validates both arrival destinations and WorldGuard protection. A shared TeleportService owns request deduplication, three-second warmups, movement/damage/logout/death/world-change cleanup, titles/sounds and bounded arrival spirals. Looking around does not count as movement. Both commands are usable from either world; destination resolution runs again at completion. A provisional configurable 30-second Bukkit PvP tag blocks these travel routes, including after reconnect. Full combat/logout penalties and custom weapon adapters remain future work. The existing tutorial text and first-pack persistent marker remain temporary.

`ArrivalCommands` registers feature commands; handlers select destinations and delegate rather than adding timers or a central command switch. See docs/commands.md for permissions, extension steps, configuration/migration, lifecycle and integration boundaries. `teleport.yml` replaces the removed arrival.warmup-seconds key; messages use MiniMessage templates with additive bundled defaults.

Multiverse loads the copied BlackSpireOutpost as `minecraft:spawnworld` (SpawnWorld) and owns first-join placement, world spawn coordinates and adventure/survival game modes. The restored entrance comes from the old Essentials spawn near (-0.25, 93, -16.25); the old world default (0, 4, 0) was unsafe. Paper migrated the map into the ignored dev_world dimension container. The original supplied backup is unchanged and its old playerdata/statistics were not imported.

WorldGuard protects all SpawnWorld and a survival landing at x/z -176..-112 inclusive, y -64..319, around the existing dev_world spawn. Boundaries show greeting/farewell messages. Both areas deny edits/PvP/destructive actions and make players invulnerable. SpawnWorld is adventure mode, with natural mob spawning/random ticks/day advance disabled; survival retains normal day progression and random ticks. Bed/anchor-aware SpawnWorld respawn fallback is configured; group-home priority remains next. Starter kit, custom combat, raids, events, vehicles, group branding and vendor-policy restrictions are still unimplemented.

The server binds to localhost:25565. Launch with `./scripts/start-server.sh` or `server/start.command`; type `stop` to save and exit. See server/README.md for exact setup and restoration instructions. WeaponMechanics automatic pack sending is disabled. Pack changes on main publish a versioned GitHub release through Actions. Startup verifies the newest release matching the pinned Minecraft version and applies the URL/hash/UUID/required prompt together, with a verified local cache for temporary network failures.

The new pack builds from `resource-pack/src/` with `./scripts/build-resource-pack.py`, targeting resource format 84.0. It includes the E-11 model, aim/sprint variants and texture recovered from the pinned MandoPack commit. Its item_model keys are cosmetic previews only; firing behavior and pose switching are not wired. Remaining assets and ModelEngine blueprints must be recovered feature by feature. Client visual verification is pending.

The player experience, FigJam diagrams and proposed feature order remain recorded: arrival/protection, group base/travel, combat, supplies/progression, KOTH, Jabba, vehicles, raids. Pairwise kill recording lands with combat. See docs/features/arrival-and-protection.md for the first slice and docs/recovery.md for legacy code.

## Verification
Spawn travel: six unit tests pass (world configuration and camera/position movement rules). The updated temporary Paper probe passed 53 assertions, including exact 3/2/1 title text, no teleport before 60 ticks, both destinations, command ownership, sound/title emission, duplicate prevention, movement/logout/mount/world-change cancellation, combat refusal, stale/missing destinations and cancelled teleport handling. It also reruns the existing WorldGuard/tutorial checks. These use simulated players with live worlds/vendor APIs, not rendered Minecraft-client observations. The test JAR is removed before ordinary play.

Arrival: `./scripts/build.sh` passes with four configuration tests. A temporary integration plugin ran 43 passing assertions against actual loaded worlds/WorldGuard plus simulated non-op player callbacks: safe footing; block/PvP policy at all four landing boundaries; building permitted outside; unprotected/missing destinations rejected; public command ownership; pack-gated tutorial and serialized persistence marker; replay; duplicate departure handling; move/logout/mounted/world-change cancellation. This is API/callback coverage, not an authenticated multiplayer gameplay test. The probe is removed from the ordinary runtime. Multiple clean stops/restarts retained the imported map, spawn settings and regions. Live pack rendering, map appearance, actual new-player placement and non-op interactions await the client walkthrough.

Git: reviewed runtime YAML configuration and sanitized secret-bearing config templates are now tracked; all world files and installed plugin folders under server remain ignored; top-level `plugin/` source remains tracked. No map payloads or installed plugin files are tracked. The original 64x64 server icon is tracked in `docs/assets/server-icon.png`, shown in the GitHub README, and copied to the runtime by startup. Config templates contain only sanitized settings. The original map is an external backup, not a Git asset.

Maven verify passed with the pinned 26.1.2 API and produced the deployed MandoMC.jar. All 12 plugins enabled together; the plugin list, `/mandomc`, FactionsUUID list and Vault's EssentialsX economy bridge were checked. A Minecraft status handshake returned Paper 26.1.2 at 127.0.0.1:25565. Clean stop/restart and loading the existing test world were verified. This establishes boot compatibility, not gameplay correctness. The user confirmed a successful client join before this arrival deployment; the new route still needs a real-client walkthrough.

The pack builds without downloads, has root pack.mcmeta, and validates JSON plus local item-model/model/texture references. Two builds produced the same ZIP hash; missing referenced assets fail the build. Six deployment tests pass: compatible release selection, property preservation, checksum rejection, network failure cache fallback, incompatible cache rejection and running-server protection. The first GitHub Actions run succeeded (42136788904); the public release ZIP downloaded without authentication and matched both hashes. Paper stopped cleanly, synchronized the release settings, restarted with all 12 plugins, and is listening on localhost:25565. Runtime server.properties contains the versioned URL, SHA-1, stable UUID and require-resource-pack=true. Resource-pack rendering and gameplay integration await a client test. Generated packs, downloaded JARs, worlds and local tools are ignored by Git. Documentation, JSON and shell syntax were checked.

Earlier recovery verification remains valid: all 294 legacy feature files matched their original Git blobs across 16 recovery branches; the full snapshot preserves obsolete docs and old main. FigJam diagrams were reviewed during the design checkpoint.

## Next action
Playtest `/spawn` and `/survival`: turn the camera during the 3/2/1 countdown, then try walking/jumping, and assess the arrival spiral/sounds. Tune presentation before merging the stacked arrival/travel work and moving to groups/bases.

## Open
Group name/command, kit quantities/ammo, starting credits, travel, combat tag details, saber direction/tuning, enchantment compatibility/repair, group creation cooldown, claim capacity, warpoint threshold/edge cases, raid-window duration/refresh and active-raid detection, explosive/block limits, KOTH reward delivery, event schedules, recipes/prices, vehicle redeployment details and content depth for the integrated playtest. Resource planets, aerial combat and themed replacement swords/bows are deferred. Continue through concrete player scenarios and the proposed playable slices. The arrival slice is deployed; the remaining gameplay systems are unimplemented.

Temporary interface note (2026-11-07): `/tutorial` and `/survival` are development-only commands. Their behavior is available for testing; the final onboarding/travel interface is undecided.

Repository configuration follow-up: `WORLD_SOURCES.txt` inventories active and archived maps with source-link placeholders, installation paths and fallback options. Runtime uses a file allowlist; logs, caches, credentials and player data remain excluded. No live gameplay settings were changed in this follow-up.

Tutorial direction: much later `/tutorial` will show short text and send players to a library in SpawnWorld, where they can take feature-specific guidebooks. Current text is temporary; library coordinates and book content remain unimplemented.
