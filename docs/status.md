# Resume here
Updated: 2026-11-05

## Milestone
Current: first-session gameplay design on bilal/player-session-design. Complete: clean buildable foundation and 16 legacy feature recovery branches, plus the full old-main snapshot.

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

Research-backed travel recommendation is recorded in docs/player-experience.md as a proposal, not accepted policy: free commands/menu plus optional themed terminals, fixed public arrivals, group home, and a proposed five-second warmup. Published server guides support this as a comparable design; player preference remains a prediction to test.

## Implementation
Minimal bootstrap, config schema check and /mandomc status command. Maven reactor/wrapper, Java 25, pinned Paper 26.3 build 242 API. No gameplay modules or third-party plugins enabled. Server templates and lockfile exist; Minecraft is not running.

See docs/recovery.md for the old source. Maps/configs and editable ModelEngine blueprints still need recovery.

Local diagram sources mark resource planets as deferred and event PvP deaths as a pairwise warpoint risk. The linked FigJam board still shows an earlier proposal and has not been refreshed in this design step.

## Verification
Maven verify passed and produced MandoMC.jar. Verified packaged descriptor/config and Java 25 bytecode. Documentation links and JSON/XML parsed successfully. All 294 legacy feature files match their original Git blobs exactly across the 16 recovery branches; 13 obsolete documentation/rule files remain in the full snapshot. Source inspection confirms legacy saber, Jabba and group/solo KOTH implementations. Published KOTH references support the continuous-capture model; the new group rule is not runtime-tested. No automated gameplay tests exist yet; no in-server test has run. Current work changes design documentation only.

## Next action
Define aircraft's first-playtest role, vehicle acquisition, and theft/destruction rules outside protected areas. These three questions are awaiting the user's answers.

## Open
Vehicle role/acquisition/theft, group name/command, tutorial format, kit quantities/ammo, starting credits, travel, combat tag details, saber direction/tuning, enchantment compatibility/repair, group creation cooldown, claim capacity, warpoint threshold/edge cases, raid-window duration/refresh and active-raid detection, explosive/block limits, KOTH reward delivery, event schedules, recipes/prices, and content depth for the integrated playtest. Resource planets and themed replacement swords/bows are deferred. Continue design through concrete player scenarios; no gameplay code changed in this step.
