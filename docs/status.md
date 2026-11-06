# Resume here
Updated: 2026-11-05

## Milestone
Current: local Paper boot and initial resource-pack source, integrated into main from bilal/local-paper-server after validation. Complete: clean plugin foundation, all selected public gameplay dependencies installed, and the first recovered E-11 visual slice. Legacy gameplay remains preserved on 16 recovery branches plus the old-main snapshot.

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

MandoMC itself still only provides bootstrap, config schema checking and `/mandomc`. The runtime has a fresh survival test world and vendor defaults; it does not implement the accepted MandoMC rules. No safe regions, starter kit, custom combat, raids, events or vehicles exist yet. Brand replacement for FactionsUUID and restrictions on unrelated vendor behavior remain feature work.

The server binds to localhost:25565. Launch with `./scripts/start-server.sh` or `server/start.command`; type `stop` to save and exit. See server/README.md for exact setup and restoration instructions. WeaponMechanics automatic pack sending is disabled. No pack is automatically hosted yet.

The new pack builds from `resource-pack/src/` with `./scripts/build-resource-pack.py`, targeting resource format 84.0. It includes the E-11 model, aim/sprint variants and texture recovered from the pinned MandoPack commit. Its item_model keys are cosmetic previews only; firing behavior and pose switching are not wired. Remaining assets and ModelEngine blueprints must be recovered feature by feature. Client visual verification is pending.

The player experience, FigJam diagrams and proposed feature order remain recorded: arrival/protection, group base/travel, combat, supplies/progression, KOTH, Jabba, vehicles, raids. Pairwise kill recording lands with combat. See docs/features/arrival-and-protection.md for the first slice and docs/recovery.md for legacy code.

## Verification
Maven verify passed with the pinned 26.1.2 API and produced the deployed MandoMC.jar. All 12 plugins enabled together; the plugin list, `/mandomc`, FactionsUUID list and Vault's EssentialsX economy bridge were checked. A Minecraft status handshake returned Paper 26.1.2 at 127.0.0.1:25565. Clean stop/restart and loading the existing test world were verified. This establishes boot compatibility, not gameplay correctness. No authenticated client join has been tested.

The pack builds without downloads, has root pack.mcmeta, and validates JSON plus local item-model/model/texture references. Two builds produced the same ZIP hash; missing referenced assets fail the build. Resource-pack rendering and gameplay integration await a client test. Generated packs, downloaded JARs, worlds and local tools are ignored by Git. Documentation, JSON and shell syntax were checked.

Earlier recovery verification remains valid: all 294 legacy feature files matched their original Git blobs across 16 recovery branches; the full snapshot preserves obsolete docs and old main. FigJam diagrams were reviewed during the design checkpoint.

## Next action
Join localhost with Minecraft Java 26.1.2, then recover SpawnWorld for the arrival/protection slice. The new pack's E-11 preview can be enabled locally for visual inspection. Public group naming and travel remain open before their interfaces are implemented.

## Open
Group name/command, tutorial format, kit quantities/ammo, starting credits, travel, combat tag details, saber direction/tuning, enchantment compatibility/repair, group creation cooldown, claim capacity, warpoint threshold/edge cases, raid-window duration/refresh and active-raid detection, explosive/block limits, KOTH reward delivery, event schedules, recipes/prices, vehicle redeployment details and content depth for the integrated playtest. Resource planets, aerial combat and themed replacement swords/bows are deferred. Continue through concrete player scenarios and the proposed playable slices. This checkpoint sets up the runtime and pack; custom gameplay remains unimplemented.
