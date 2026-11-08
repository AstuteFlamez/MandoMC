# Feature: Darth Vader’s Lightsaber
Status: implementation; client playtest pending
Branch: bilal/vader-lightsaber

## Player experience
`/mmc give vader` issues one shield-based Darth Vader lightsaber; it also appears in Weapons in the admin browser. The hilt appears in GUI/ground contexts, the red blade when held, and the original JustTimm blocking pose on use. Holding/deselecting plays legacy ignition sounds. Item lore follows the accepted badge/blank-line/description/yellow-keybind standard.

## Rules
Main-hand attack uses native attack attributes and charge. Right-click blocks; incoming front-facing E11 bolts and arrows reflect toward the defender’s aim. No timed parry. Side/rear bolts do not reflect. Each block/reflection consumes guard heat; overheat clears use and disables defense/attack/throw for the configured interval, then restores the guard. Heat is player-owned, independent of durability, so changing sabers or repair cannot reset it. Native shield wear remains separate; broader enchantment/repair balance is deferred. Offhand saber use is denied.

Sneak + attack launches a returning saber. The real item stays in its inventory slot, displaying the hilt while a nonpersistent ItemDisplay flies. Inventory transfers/drops/swaps are blocked during flight. Switching slots, teleport, death, logout and shutdown finish the flight without granting another item. Death drops follow normal server rules. Login normalizes a hilt left by an interrupted session. Outbound movement checks blocks and protected locations each tick, hits each victim at most once, then returns without dealing return-path damage. No terrain edits or chunk generation are requested. The lifetime is bounded to 100 ticks; range and speed are configurable.

Combat is denied in SpawnWorld and the survival landing, including throw starts, flight endpoints, victims and reflected-shot destinations. Bukkit damage events preserve vendor/future clan cancellation; clan-specific behavior remains deferred. WeaponMechanics retains responsibility for E11 damage and final hit processing.

## Smallest playable version
One Vader saber, one E11 interaction, one configured heat meter and a returning throw. Tuning is provisional, not final gear balance. Crafting, other saber variants, Force powers and global removal of ordinary weapons remain future work.

## Ownership and integrations
MandoMC owns catalog identity `vader` / `mandomc:item_id`, item attributes, heat and flight lifecycle. WeaponMechanics 4.3.1 owns projectile simulation/damage. Its immutable shooter field requires constructing a new reflected projectile, not simply changing the old bolt’s direction. The public API adapter preserves weapon configuration/speed and bounds reflection chains; the defender owns the new bolt.

JustTimm owns the artwork. His terms permit server use but prohibit rehosting extracted assets without permission. The original version 3.2.2 ZIP is downloaded by clients from its official Modrinth CDN on player join, with a deduplicated pack-loaded fallback. Its SHA-1 and URL are pinned in sabers.yml and dependencies.json. We reference only the three Vader model entry points. The upstream pack has no font/shader overrides; its named-item shield selection does not control our explicitly assigned item_model. Credits and thanks are included in resource-pack/src/credits.txt and the pack README.

Legacy improvements: directional reflection instead of omnidirectional protection; correct reflected-shot owner; guard independent of wear; bounded shared scheduler instead of a task per throw; no removal/recreation of owned items; safe-zone checks on each damage path. Content is config, vendor APIs stay in integrations, and no parallel item registry is introduced.

## Parameters
`items/sabers.yml` owns name, lore, model, rarity, native attack-damage (10.5) and attack-speed (1.0). `sabers.yml` owns the item/model mapping and behavior: throw damage 8, cooldown 40 ticks, range 12 blocks, speed 1 block/tick, return speed 1.5 blocks/tick, hit radius 0.45 blocks; guard capacity 100, cost 20 per hit, cooling 0.5/tick after 30 ticks idle, overheat 80 ticks; reflection half-angle 80 degrees and chain limit 4. `messages.yml` owns feedback. Item content uses `/mmc items reload`; behavior and dependency pins require restart. Existing inventory items are not automatically rebalanced.

Adding item families now installs missing bundled rarity/category badge keys without overwriting operator choices. The existing robes remain unchanged.

## Verification
The final live Paper probe passed 33 assertions; 14 Java tests and nine pack tests pass. Four guard tests cover overheat timing, cooldown delay, heat floor and directional rejection; existing item/travel tests remain. scripts/build-saber-probe.sh builds the isolated live Paper probe. It tests native item attributes, zone policy, reflection creation/ownership and throw ownership/lifecycle with real APIs and fake-player inventories. The temporary test JAR was removed after passing. This does not replace rendered client checks or authenticated multiplayer reflection/death testing.

## Pause note
Next action: client-playtest Vader’s hilt/blade/blocking pose, throw path and heat feedback with E11 fire outside the safe zone. Crafting, broad enchantment validation and multiplayer balance remain deferred.
