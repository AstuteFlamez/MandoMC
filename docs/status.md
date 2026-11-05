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

The user wants a large power gap between basic and top-tier equipment. Their existing saber approach uses shield models/blocking, reflection and a returning throw; the recovered source confirms implementations to inspect during porting. Guard/overheat policy, vanilla weapon access and enchantment/repair rules remain open.

Research-backed travel recommendation is recorded in docs/player-experience.md as a proposal, not accepted policy: free commands/menu plus optional themed terminals, fixed public arrivals, group home, and a proposed five-second warmup. Published server guides support this as a comparable design; player preference remains a prediction to test.

## Implementation
Minimal bootstrap, config schema check and /mandomc status command. Maven reactor/wrapper, Java 25, pinned Paper 26.3 build 242 API. No gameplay modules or third-party plugins enabled. Server templates and lockfile exist; Minecraft is not running.

See docs/recovery.md for the old source. Maps/configs and editable ModelEngine blueprints still need recovery.

Local world-diagram source marks resource planets as deferred. The linked FigJam board still shows an earlier proposal and has not been refreshed in this design step.

## Verification
Maven verify passed and produced MandoMC.jar. Verified packaged descriptor/config and Java 25 bytecode. Documentation links and JSON/XML parsed successfully. All 294 legacy feature files match their original Git blobs exactly across the 16 recovery branches; 13 obsolete documentation/rule files remain in the full snapshot. Source inspection confirms legacy saber recognition, reflection, throwing and overheat implementations. No automated gameplay tests exist yet; no in-server test has run. Current work changes design documentation only.

## Next action
Decide saber guard/overheat limits, allowed vanilla weapons, and enchantment/repair progression. These three questions are awaiting the user's answers.

## Open
Group name/command, tutorial format, kit quantities/ammo, starting credits, travel, combat tag details, saber guard/direction rules, vanilla weapons/enchantments, group creation cooldown, claim capacity, warpoints, raid limits, recipes/prices, and content depth for the integrated playtest. Resource planets are deferred, not an active open design question. Continue design through concrete player scenarios; no gameplay code changed in this step.
