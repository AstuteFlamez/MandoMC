# Feature: Jetpack chestplate enchantment
Status: LIVE; books I/II and tooltip refinement deployed 2026-11-12 17:04 America/Chicago. Replaces the offhand wearable and visual comparison experiment.
Branch: bilal/boba-equipment

## Player experience
Boba now drops a vanilla enchanted book named Jetpack I, containing the real stored Jetpack I enchantment. Craft two I books into one II book. Combine a book with one chestplate, in any positions in either crafting grid. The result is the same chestplate with the native `mandomc:jetpack` enchantment, displayed as Jetpack. Wear it in the chest slot and double-tap sneak to toggle thrust. The offhand remains free. There is no attached model or jetpack exhaust particle effect; normal armor appearance remains.

The jetpack ingredient is consumed. The chestplate retains its material, durability, name, lore, other enchantments, trim, persistent data and unrelated native components. All seven native chestplate materials and custom armor based on those materials are supported. The flamethrower is unchanged.

## Rules
- One valid Jetpack book plus one chestplate, or two I books for the II-book recipe. Extra ingredients, ordinary echo shards and upgrades beyond II reject the recipe. No layout requirement; both 2x2 and 3x3 crafting work.
- Minecraft owns normal/shift-click output transfers and ingredient consumption. The plugin validates the live matrix/result; it does not implement a second consumption path.
- Existing double-sneak controls, movement policy, speed, cooldowns and fuel rules remain: level I buys 0.1 real seconds per hotbar coal, level II buys 0.2 seconds; charcoal/storage coal excluded; purchased fractions persist; warning at five seconds. No separate visual preference affects ability.
- Native grindstone disenchanting removes Jetpack and the glide flag introduced by it. An independently preexisting glide flag is retained. Native crafting repair may strip enchantments as usual; orphaned upgrade flags are cleaned from crafting/anvil/grindstone results.
- Armor removal/breakage, death, logout and existing travel/disable boundaries stop powered flight with the existing credit persistence. Normal native armor/drop/keep-inventory rules own item loss. No virtual jetpack storage or duplicate item exists.
- Legacy offhand jetpacks become ingredients; their previous offhand glider/equippable components are cleared when carried. Already-stored ingredients remain craftable without requiring exact old lore/components. Ingredient hints update when carried.
- `/mmc equipment` and right-clicking an ingredient explain crafting. `/mmc jetpacktest` and the visual controller are no longer registered. Deprecated getters remain binary-compatible but return no attachment/controller. Historical rendering classes/assets are inert.

## Smallest playable version
Craft a named, damaged, enchanted/trimmed netherite chestplate with a Boba jetpack. Its original properties remain and Jetpack appears in the native enchantment tooltip. Equip it with a shield in the offhand, put coal in the hotbar and double-tap sneak. Flight consumes coal and no display/armorstand/cloud spawns.

## Ownership and integrations
The native enchantment registry is declared by the small version-pinned data pack under `server/datapacks/mandomc-jetpack`. Install it into the primary world's datapacks directory while stopped, before enabling the feature. `scripts/install-jetpack-enchantment.py --runtime <runtime>` derives the primary world from server.properties. The pinned 26.1.2 data version is 101.1. Missing registration fails explicitly at feature bootstrap; no fake lore-only fallback is used. The enchantment has no enchantment-table/trade tags and no independent native effects; gameplay remains in the existing equipment service.

`JetpackChestplate` owns the seven native shapeless registrations, exact catalog identity validation and upgrade-owned component cleanup. `BobaEquipment` reads the worn chestplate's native enchantment; the existing fuel/flight service remains the sole ability writer. Armor and the flamethrower retain their existing owners. The registry pack is a server data pack, so the resource pack is unchanged.

## Parameters
Existing equipment/boba.yml movement/flame settings remain in force. Fuel cost and recipe cost are fixed user rules. Crafting guidance is in equipment/jetpack-chestplate.yml. Code and registry changes require a restart.

## Verification
Six deterministic fuel tests pass. The exact-candidate disposable full runtime tests real registry loading, all seven material recipes, rejection cases, native 3x3 consumption, actual 2x2 normal and shift result clicks, repeated-click duplication prevention, full item-component preservation, native grindstone preparation, prior-glider retention, offhand migration/rejection, chest-slot native glide and zero visual entities. Registered flame/ammo/collision behavior is also exercised. Native result is 57 assertions passed. The real-time depletion check uses elapsed nanoseconds rather than assuming startup catch-up ticks take a fixed wall-clock duration.

## Pause note
Deployed exact JAR `f067c786195b454e0f3d0f9d9413447138458c8ce87ecb477c4b8d3ad7da6cfb` and the native enchantment data pack with one clean restart and a full stopped backup. All 41 production read-only checks passed, including native enchantment, all chestplate recipes, removed test route/controller, existing loot, NPCs/decor/boards, frozen baselines, pack and native agent. The pack is unchanged. Jabba active / next None was separately restored and read back. Temporary audit removed; gear window released. Next action: user verifies ordinary crafting and chest-slot flight in the connected client.

## Book and tooltip refinement
The native enchantment is capped at II, so vanilla enchantment descriptions include the I/II suffix. Two separate I-book ingredients produce one II book; II + II does not create III. A book applies its level to an unupgraded chestplate; native-style I + I on an existing chestplate also becomes II. Upgrades beyond II reject. Level II changes only the paid fuel interval; speed, steering and all other flight rules stay unchanged. Purchased nanoseconds are retained when changing levels, rather than rescaled into free fuel.

Catalog armor now hides the native enchantment block that precedes lore and reproduces all normally visible enchantment names below the original rarity/category badge row. Generated rows carry an invisible component insertion marker, allowing idempotent refresh and exact removal without deleting original descriptions. Original enchantment-hide flags are remembered and restored. Already-hidden unrelated enchantments stay hidden; actual enchantment data remains unchanged. Vanilla armor without badge lore keeps vanilla tooltip rendering.

Legacy model ingredients migrate to native books in carried inventories and on spawn/open. A grindstoned plain book is never recreated as an enchanted book. The native resource pack remains unchanged. Eight deterministic fuel tests and 81 exact-candidate native assertions pass, including actual book drops, normal/shift book consumption, direct II application/cap, screenshot-regression checks on real Beskar armor, exact tooltip/lore restoration, migration and real level-II activation with 0.2 seconds per coal. Connected-client appearance remains a manual confirmation.

The book/II/tooltip refinement is live as JAR `58a6238614d85e0875cc7775c53e5f74403b53ea2b3d79ba013d0afefd282423`, with one clean restart and a full stopped backup. All 44 production read-only preservation/registration checks passed; KOTH active / next None was restored and separately read back. Resource pack and mandatory native agent/launcher remain unchanged. Temporary audit removed; gear window released. Next action: connected-client book/tooltip confirmation and manual I+I crafting.
