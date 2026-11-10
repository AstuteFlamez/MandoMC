# Gameplay architecture

## Planet direction — 2026-11-09
Batuu remains protected spawn. Tatooine and Hoth replace the standalone Survival world
as the future safe-zone/warzone/claimable-wilderness planets. Boundaries come from the
user. FactionsUUID remains the claim owner; no new claim system or regions are activated
by the travel removal. See [planet wilderness](features/planet-wilderness.md).

The user accepted this division on 2026-11-05. The pinned versions boot together; gameplay integration hooks still need runtime verification. Components may be replaced while preserving player behavior and content contracts.

Design the [player experience](player-experience.md) first, choose the playable order, implement parameterized systems, then configure content.

Player-facing group naming and commands must replace Factions and `/f`; the final identity is pending. FactionsUUID remains the selected state owner. Verify its supported command and language customization or an adapter when implementing the group interface, including help and menu output. A display-name change alone does not complete the requirement.

Resource planets are deferred. Initial gathering and credit income use ordinary survival resources plus activity rewards. Keep future resource-planet support outside the current milestone; the ownership table below records responsibilities, not a requirement to build every capability now.

## Runtime and code
One local Paper process with multiple worlds; one modular MandoMC JAR. The arrival branch adds a protected one-way survival departure and persistent tutorial alongside bootstrap/status. MandoMC owns world loading and exact spawn coordinates through WorldService; shared travel validates them before teleporting. Paper 26.1.2 build 74 and its API are pinned; Java 25 is required.

Introduce packages as their implementation lands:
```text
net.mandomc
  bootstrap/
  gameplay/{combat,territory,items,vehicles,events,resources,travel,economy}/
  integrations/{weapons,models,mobs,factions,regions,economy,worlds}/
  persistence/
```
Use small injected interfaces at vendor boundaries. Keep vendor classes inside integrations and pass stable IDs, UUIDs and immutable facts to gameplay. Avoid speculative framework classes.

The new pack lives in `resource-pack/` in this repository. Its build targets the pinned server version, uses namespaced item models, and recovers assets from a recorded MandoPack commit. Runtime-generated ModelEngine output will be integrated when the corresponding models and blueprints are recovered.

## Ownership
| Capability | Selected public component | MandoMC owns |
| --- | --- | --- |
| Blasters and grenades | WeaponMechanics and dependencies | Acquisition, saber interaction, combat policy |
| Animated models | ModelEngine | Vehicle controls, ownership, seats, health |
| Dungeon mobs | MythicMobs | Runs, objectives, doors, cleanup, completion rewards |
| Factions | FactionsUUID | Warpoints, vulnerability, controlled breaches |
| Protected regions | WorldGuard with WorldEdit | Enforcement for every custom action |
| Worlds | Paper APIs | Existing dimension loading, exact arrivals, aliases and travel |
| Credits | MandoMC native ledger | Atomic balances, payments and admin adjustments; auction-house/reward integration deferred |
| Items, armor, crafting | Paper APIs and resource-pack/ | Stable IDs, recipes, custom behavior |
| KOTH and resource regeneration | Paper APIs | Event rules, bounded regeneration |

The catalog in server/plugins.catalog.json records admission status. Installed versions and artifact hashes are in server/plugins.lock.json. Boot validation does not establish gameplay compatibility. EssentialsX and Multiverse-Core are removed. MandoMC owns the selected utility commands and migration; their old runtime data is retained for rollback. Native rank badges now format tab, nameplates and the existing chat renderer; voting remains deferred.

## Contracts
- FactionsUUID owns membership, relations and claims. MandoMC owns pairwise warpoints keyed by stable group IDs; vendor faction power is not the raid authority. Do not mirror claims into WorldGuard.
- WorldGuard owns static regions. Raids never override spawn/adventure-map protection.
- The native CreditLedger owns current credit balances. Future auction currency/integration details require approval; do not introduce a second balance writer or restore the old SQL mirror.
- WeaponMechanics generates its weapons and handles projectiles. MandoMC references vendor IDs instead of duplicating weapon definitions.
- MythicMobs owns mob definitions/AI. MandoMC tracks run-owned instances. Each reward has exactly one writer.
- ModelEngine owns rendering/animation; MandoMC owns vehicle gameplay.
- MandoMC stores only its own state. Select file or transactional local storage when a feature needs it.
- Disabled features must not load missing vendor classes. Missing dependencies for enabled features fail clearly. Protection failures never open an unprotected gameplay path.
- Stable MandoMC IDs survive vendor replacement through explicit mapping and saved-data migration.
- Identify custom items before applying ordinary-weapon restrictions. Removing vanilla shields must not disable registered shield-based lightsabers. Enchanting is disabled; keep the custom guard limit independent of native repair behavior.

## Vehicles
Built-in aircraft weapons remain deferred; the U-wing gunner may fire held blasters. MandoMC owns vehicle state and seat access, while ModelEngine owns visuals. Persist the placing player's UUID as the pilot authority for a deployed vehicle. Group membership and passenger access do not grant piloting rights.

Passenger and gunner seats are available to any player. The existing U-wing secondary mount is Gunner, its only firing role; actual passenger seats are deferred until added to the model/config. Absent roles are hidden in the riding menu. Evaluate mounting, pilot-seat switching and vehicle damage separately: enemies can destroy vehicles outside protected areas, but cannot take over piloting. Destruction is permanent loss. The placer may pack a vehicle into an item only while stationary, unoccupied and out of combat. Use one authoritative identity across deployed/packed/destroyed states; preserve remaining health and prevent simultaneous item/entity copies. Acquisition is crafting-first for all equipment. NPC/server equipment shops and resource-selling shops are excluded; preserve runtime item/vehicle identity independently of acquisition. Future player-to-player auction-house rules and implementation remain undecided. Redeployment/transfer details belong in the vehicle feature brief.

## Events and warpoints
Jabba uses one scheduled shared dungeon with PvP and open loot. Private copies per group are outside the current design. MandoMC owns event state, progression and cleanup; MythicMobs owns mob definitions and AI.

KOTH owns a continuous capture timer keyed to the capturing group's stable ID, or a player UUID for an unaffiliated contestant. The selected group model resets when the last eligible holder leaves, dies or disconnects; enemy presence alone does not reset it. Players can re-equip and re-enter both events after death.

Warpoints start at zero per pair of groups. An attributed opposing-group PvP kill adds one to the killer group's net score against the victim group; the reverse view decreases by one. Store one consistent rivalry result and apply each death once, including combat-logout deaths. Event PvP counts; unrelated group relationships do not change. Death retains normal full-loot behavior. Unaffiliated and non-attributed deaths need explicit handling.

Initial raid eligibility is directional: `score(attackerGroup, defenderGroup) >= configuredThreshold`, with a positive threshold whose numeric value is not yet chosen. This can authorize an attack while defenders are offline. During an active raid, a drop below the threshold starts a configurable continuation window for that attacker/defender pair. The expiry is measured from the score drop, not the initial breach. Further raid actions require either a qualifying current score or the valid continuation window. Active-raid detection and refresh/retrigger semantics still need a feature-brief decision. The source event implementations are recovery material, not the authoritative specification.

## Custom damage
A weapon adapter receives an attributed hit, asks combat policy about regions, factions and defenses, then cancels or modifies a supported vendor event. Exactly one path applies damage. Vehicles and area damage follow the same policy and preserve attacker attribution.

## Explosive raiding
Check world/static protection, claim owner, attributed attacking group, the pairwise score or valid continuation window, and explosive type. Only approved explosions may breach an eligible rival's base, including offline. Claims retain their owners. Hand-breaking and takeover remain denied; looting accessible chests is allowed for eligible attackers. Unrelated groups may enter an existing hole and pick up dropped items, but may not break blocks or open the owner's chests. Evaluate block damage, container access and item pickup separately; an eligible pair's permissions must not grant a global bypass. Change only approved blocks; decide entity damage separately.

Do not unclaim a base, grant broad bypass, or indiscriminately uncancel another plugin's event. Prove the selected hooks and ordering in an integration spike before implementing raids.

## Dependency verification
For each enabled slice, record exact JAR/API versions, hashes, sources and transitive dependencies. Test boot, a non-op account, restart persistence and protection boundaries. Pin versions between intentional upgrades.

Current FactionsUUID changed names, packages and coordinates from the old source. Port that boundary deliberately.

## References
- [WeaponMechanics API](https://cjcrafter.gitbook.io/weaponmechanics/developer-api)
- [ModelEngine API](https://wiki.mythiccraft.io/modelengine/API)
- [MythicMobs API](https://wiki.mythiccraft.io/mythicmobs/API)
- [FactionsUUID API](https://factions.support/developers/) and [migration](https://factions.support/four/)
- [WorldGuard regions](https://worldguard.enginehub.org/en/latest/developer/regions/)
- [Multiverse configuration](https://mvplugins.org/core/reference/configuration-file/)
- [EssentialsX modules](https://essentialsx.net/wiki/modules) and [Vault API](https://github.com/MilkBowl/VaultAPI)
- [Paper requirements](https://docs.papermc.io/paper/getting-started/) and [project setup](https://docs.papermc.io/paper/dev/project-setup/)

## Custom items
`ItemCatalog` owns one validated snapshot of native item templates and explicit vendor factories. Native IDs use mandomc:item_id; vendor generation remains behind integrations/weapons. Definitions/presentation are configuration; item delivery and the paginated inventory browser are Java behavior. `RootCommand` dispatches registered feature handlers, keeping bootstrap and command parsing independent of catalog internals. No recipe or economy state is stored in item presentation.

## Combat basics
CombatService owns the sole 15-second elapsed-time tag, external teleport denial and combat-logout death. PlayerHud owns the persistent sidebar and reads that tag; combat replaces its kills row only while active. TeleportService consults that state. Positive, uncancelled Bukkit damage provides mob/player attribution, including WeaponMechanics. Paper performs one death/drop pipeline after SaberService returns an airborne item. SurvivalRules owns use/crafting/portal restrictions; Paper/Bukkit and the MandoMC world registry also disable dimension loading. StarterKit owns once-only first-join delivery; WeaponMechanics generates its E-11 and ammo. See [combat basics](features/combat-basics.md).

## Native utilities
WorldService owns the existing dimension registry and exact arrivals in worlds.yml. PersonalTravel saves homes/return locations in native player data; TeleportRequests stores bounded ephemeral consent and delegates to TeleportService. AdminService exposes explicit operator actions. See [native utilities](features/native-utilities.md).

## Native ranks, HUD and credits
RankService owns UUID memberships and Bukkit permission attachments. WorldEdit/WorldGuard query the same native grants through the supported WEPIF provider; no LuckPerms or Vault permission fallback. PlayerHud owns one sidebar per viewer while mirroring existing gameplay teams. CreditLedger commits integer-cent account changes on one IO worker with atomic durable replacement; the HUD reads only committed balances. The user requested fresh accounts on 2026-11-09: everyone defaults to Default and zero credits; only AstuteFlamez is explicitly MandoStudios. No legacy account import runs at startup. See [feature brief](features/native-hud-ranks-credits.md).
