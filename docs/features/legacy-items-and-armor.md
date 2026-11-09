# Feature: Legacy items, armor and crafting
Status: implementation; isolated Paper verification passed; client appearance check pending
Branch: bilal/legacy-armor-sets

## Player experience
Players craft recovered equipment at a normal crafting table, refine metal ingots into sheets in a blast furnace, and use `/recipes` to follow the component chain. The landing page presents category cards; All recipes and each category paginate with visible page numbers. Clicking a result shows its actual shape, result quantity and workstation. Clicking an ingredient follows its recipe or explains its available source. Blank crafting cells stay empty; preview items cannot be taken.

Plastoid (Stormtrooper), blue Durasteel and gray Beskar each have a helmet, chestplate, leggings and boots. Their separate supplied 3D helmets use the ordinary native HEAD slot: players can place a valid helmet into that slot, shift-equip or swap normally. Chest/leg/boot pieces use namespaced worn textures, following the robes implementation. Robes stay unchanged.

## Rules
Recovered plastoid uses chainmail protection/durability and beskar uses netherite. Durasteel uses diamond as an explicit new default: neither recovered armor YAML contains a durasteel set. All native armor attributes, durability, stacking, equipment sound and swapping remain native. Only the three explicitly configured `head-model: true` helmets clear the equipment asset, letting the client render their 3D head item model. Other items cannot acquire this setting through lore or names.

145 catalog additions cover every missing item from the recovered legacy item YAML plus the requested durasteel set and all five tool models present in the old pack. Existing item IDs, native vehicles, palace loot presentation, robes, weapons, HUD, ranks and credits retain their current definitions. Durasteel pickaxe/shovel/wrench use diamond tool behavior (wrench is a hoe); Beskar pickaxe/shovel use netherite. Their conventional sheet/stick recipes are new defaults because the old pack contains models without item/recipe YAML.

Five recovered sabers join Vader in the existing saber service. Their old melee attributes and their own models are retained; guard, reflection, throw damage/cooldown and escrow use the accepted current shared rules. Ordinary shields remain disallowed. Models reference the already-delivered, unmodified official JustTimm pack; extracted artwork is not republished.

Fuel-stage items, old crate/security keys, the credit note and purple X-wing collectible are restored as catalog items only. This change does not enable the retired fuel-transfer, crate redemption, credit-note redemption, keycard-door or alternate vehicle deployment systems. Obsolete action hints were removed. Current transport fuel policy and native palace progression remain authoritative. Many recovered raw materials have no active loot source; the guide states this instead of inventing one. They can be issued with the existing operator `/mmc give` browser for testing. The seven existing palace-cache components retain their real acquisition source.

## Ownership and integrations
ItemCatalog owns stable `mandomc:item_id` templates and presentation. CraftingRecipes registers 51 shaped recipes and 11 blast recipes and supplies exactly those definitions to RecipeGuide. Paper owns consumption, shift-crafting, furnace queues/experience and armor-slot interactions. Custom ingredients use ExactChoice; plain vanilla shards cannot substitute. Shutdown unregisters all owned recipe keys and menu listeners.

Recovered recipe aliases `energy_conduit`, `plasma_capacitor` and `precision_emitter` map to their actual catalog IDs; BUCKET maps explicitly to minecraft:bucket. The original left/right X-wing wing recipes were identical. The right wing reverses vertical row order, preserving every ingredient quantity, so both outputs can be crafted. A regression rejects native mirror-equivalent recipe collisions.

## Parameters
Defaults are in `items/definitions/legacy.yml`, `items/catalog.yml`, `items/recipes.yml` and `combat/sabers.yml`. Recipe changes require a restart. `head-model` accepts only a boolean on a helmet without `equipment-model`. Blast recipes validate furnace type, input/result IDs, positive bounded cook-time ticks and finite nonnegative experience. Legacy metal cook times and experience are preserved.

`resource-pack/legacy-item-sources.json` records recovery mappings, repairs and explicit new defaults. Original user armor textures and helmet JSON are retained under `resource-pack/artwork/armor/`; runtime copies only normalize namespaced references. Plastoid pants use the original white chainmail second layer, since no separate white pants attachment was supplied. Missing legacy cryo/light-frame references are repaired; the missing high-efficiency energy texture uses the original mid-grade power-core artwork. The old money note has no pack model and uses paper. No global vanilla texture override is added.

## Verification
135 Java tests and 19 existing script/pack tests pass. The isolated Paper 26.1.2 run passed 462 assertions covering all catalog factories, every recipe through the actual native crafting matcher, exact blast inputs, all armor native attributes/slots/swapping/serialization, all six saber identities, category navigation, pagination, nested refining inputs and shift/hotbar/double-click/drop/drag preview cancellation. The initially failing identical-wing recipe was repaired and the full probe rerun passed. The probe shuts down its isolated server and never touches real player inventories.

Client mouse placement and visible 3D fit, worn equipment, tool icons and all recipe layouts still require an in-game visual check. This validation does not claim retired token/fuel systems are active.

## Pause note
Implementation and isolated verification complete. Next action: reconnect with the published pack, open `/recipes`, and wear all three armor sets in a matching client.
