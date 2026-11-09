# Feature: Survival combat basics
Status: implemented and verified in Paper; client playtest pending
Branch: bilal/combat-basics

## Player experience
New players receive stone pickaxe, axe, shovel and hoe, 64 steak, all four Desert Robes pieces, an E-11 and 64 standard plasma cells once. The kit goes into inventory without replacing existing items. Death and reconnect do not replenish it. Existing accounts are not automatically backfilled.

An uncancelled, positive-damage player/mob or player/player hit puts involved players into combat for 15 seconds. Further hits refresh the deadline. The sidebar shows `Safe in 15.0s`, counting down with one decimal. Voluntary teleports are refused while tagged. Leaving while tagged causes one real death and drops storage, armor, offhand and any airborne saber at the player's location.

Elytra flight, enchanting, potions and ordinary swords/bows/crossbows/shields are disabled; the registered Vader saber is exempt. Nether/End portals and entry are disabled, and neither dimension loads. Normal repair and renaming remain available through anvils. Crafting repairs of matching custom items preserve their identity and repaired wear. Applying or increasing enchantments through an anvil is denied.

## Rules
- Both dealing and receiving attributed damage refresh combat, including WeaponMechanics E-11 and mob projectiles. Cancelled or zero-damage hits and environmental damage do not tag. Environmental damage still interrupts a pending travel countdown.
- Expiry uses monotonic elapsed time, independent of TPS. One sidebar row updates every two server ticks. Previous scoreboard is restored on expiry, death, quit or disable if still owned by this feature. Team friendly-fire/collision/nameplate rules are mirrored from the original board while the sidebar is active.
- No normal logout, kick or timeout bypass. A clean server shutdown or disabled service does not punish players. A process crash cannot synthesize a death; crash recovery is not a second punishment system.
- Combat logout restores a reserved airborne saber before the native death pipeline. The death event forces full carried-item loss even if keepInventory was enabled. No duplicate manual drops or logout NPC.
- Existing safe-zone cancellation stays authoritative. Ordinary deaths remain native; survival keepInventory must remain false. Clan rules and pairwise kill scoring are deferred with clan integration.
- Potions cannot be drunk, thrown, dispensed, brewed or applied through potion/cloud effects. Food, beacons and plugin abilities keep their effects. Banned items cannot be crafted or used, but this does not erase existing stored items.
- First-join kit uses a persistent granted marker. Construct and capacity-check the complete kit before delivery; a failed first-join grant remains pending for the next join. WeaponMechanics owns weapon identity, loaded magazine, reserve ammo and consumption.

## Ownership and integrations
MandoMC CombatService is the sole tag authority. TeleportService delegates to it; remove the old provisional teleport.yml `combat-block-seconds` key. Tags are session state; kit and saber ownership use player PDC. WorldGuard cancels protected damage, Multiverse owns normal world placement, and Paper owns the actual death/drops. Essentials' default newbie kit is disabled to avoid duplicate grants.

## Parameters
- `combat/combat.yml`: schema-version 1; tag-seconds 1..300, default 15.
- `arrival/starter-kit.yml`: schema-version 1; materials and custom-items IDs with whole-stack quantities 1..64, constrained by each item's native maximum stack.
- `rules/survival.yml`: schema-version 1; disable-elytras, disable-enchanting, disable-potions, disable-vanilla-weapons, disable-nether-and-end, all true.
- `language/messages.yml`: combat sidebar/messages, rule denials and kit receipt text.
All gameplay config changes require a clean restart. Bundled language defaults are additive.

For fresh installs, also apply the Paper/Bukkit/Multiverse/Essentials settings in [shared config instructions](../../server/config/common/README.md). Disabling a gameplay flag alone does not re-enable unloaded dimensions.

## Verification
`./scripts/build.sh` covers timer rounding, exact expiry, independent players, refresh, cleanup and invalid durations alongside existing saber/arrival/item tests. `./scripts/build-combat-probe.sh` builds a temporary real-Paper integration probe with isolated native players; remove its JAR after testing. All 33 Java tests and 80 live assertions pass. Real E11 player damage, safe-zone cancellation, native logout death/drops including an airborne saber, first-join delivery, timer updates/expiry and team-policy preservation are verified. Survival keep_inventory is false. Client sidebar appearance, real mouse repairs and an authenticated disconnect/reconnect remain playtest checks.

## Deferred
Blocking, reflection tuning/testing and substantial throw stamina cost wait until the first mobs that shoot blasters exist. The accepted oval throw remains unchanged. Sandy-biome Tatooine mob spawning is the next content brief, not part of this implementation. Clan/FactionsUUID work stays deferred.

## Deployment result
Installed build SHA-256: `5b7e8258436e44941ffa67dd1762557f9765e4f6e2e779f32184ebfa7d70b8d9`. The temporary probe is removed. Normal 12-plugin startup and automated restart pass through `scripts/server-control.py`; five controller safety tests also pass. No manual restart step is needed.

Global natural-spawn restriction: `rules/survival.yml` lists `disabled-natural-mobs: [SLIME]`. This cancels NATURAL and CHUNK_GEN spawns in every world and biome, including DevWorld. It preserves spawners, eggs, commands, custom summons, slime splitting and existing entities.
