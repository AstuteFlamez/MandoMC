# Gameplay architecture
The user accepted this division on 2026-11-05. Exact versions and integration hooks still need runtime verification. Components may be replaced while preserving player behavior and content contracts.

Design the [player experience](player-experience.md) first, choose the playable order, implement parameterized systems, then configure content.

Player-facing group naming and commands must replace Factions and `/f`; the final identity is pending. FactionsUUID remains the selected state owner. Verify its supported command and language customization or an adapter when implementing the group interface, including help and menu output. A display-name change alone does not complete the requirement.

Resource planets are deferred. Initial gathering and credit income use ordinary survival resources plus activity rewards. Keep future resource-planet support outside the current milestone; the ownership table below records responsibilities, not a requirement to build every capability now.

## Runtime and code
One local Paper process with multiple worlds; one modular MandoMC JAR. Main currently contains bootstrap and a status command. Paper 26.3 build 242 and its API are pinned; Java 25 is required.

Introduce packages as their implementation lands:
```text
net.mandomc
  bootstrap/
  gameplay/{combat,territory,items,vehicles,events,resources,travel,economy}/
  integrations/{weapons,models,mobs,factions,regions,economy,worlds}/
  persistence/
```
Use small injected interfaces at vendor boundaries. Keep vendor classes inside integrations and pass stable IDs, UUIDs and immutable facts to gameplay. Avoid speculative framework classes.

## Ownership
| Capability | Selected public component | MandoMC owns |
| --- | --- | --- |
| Blasters and grenades | WeaponMechanics and dependencies | Acquisition, saber interaction, combat policy |
| Animated models | ModelEngine | Vehicle controls, ownership, seats, fuel, health |
| Dungeon mobs | MythicMobs | Runs, objectives, doors, cleanup, completion rewards |
| Factions | FactionsUUID | Warpoints, vulnerability, controlled breaches |
| Protected regions | WorldGuard with WorldEdit | Enforcement for every custom action |
| Worlds | Multiverse-Core | Planet meaning, destinations, travel |
| Credits | EssentialsX Core through Vault | Shops, prices, sinks, reward transactions |
| Items, armor, crafting | Paper APIs and MandoPack | Stable IDs, recipes, custom behavior |
| KOTH and resource regeneration | Paper APIs | Event rules, bounded regeneration |

The catalog in server/plugins.catalog.json records admission status. Role selection does not imply installation or tested compatibility. Audit and restrict unrelated EssentialsX kits, travel, selling and item commands. Its separate chat/spawn/protection modules are excluded. Voting and global chat presentation remain deferred.

## Contracts
- FactionsUUID owns membership, relations and claims. Warpoints use stable faction IDs. Do not mirror claims into WorldGuard.
- WorldGuard owns static regions. Raids never override spawn/adventure-map protection.
- The economy provider owns balances; Vault is the bridge. Do not restore the old SQL balance mirror.
- WeaponMechanics generates its weapons and handles projectiles. MandoMC references vendor IDs instead of duplicating weapon definitions.
- MythicMobs owns mob definitions/AI. MandoMC tracks run-owned instances. Each reward has exactly one writer.
- ModelEngine owns rendering/animation; MandoMC owns vehicle gameplay.
- MandoMC stores only its own state. Select file or transactional local storage when a feature needs it.
- Disabled features must not load missing vendor classes. Missing dependencies for enabled features fail clearly. Protection failures never open an unprotected gameplay path.
- Stable MandoMC IDs survive vendor replacement through explicit mapping and saved-data migration.

## Custom damage
A weapon adapter receives an attributed hit, asks combat policy about regions, factions and defenses, then cancels or modifies a supported vendor event. Exactly one path applies damage. Vehicles and area damage follow the same policy and preserve attacker attribution.

## Explosive raiding
Check world/static protection, claim ownership/attacker relation, then warpoints, breach state and explosive type. A valid raid may affect an offline faction. Change only approved blocks; decide entity damage separately.

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
