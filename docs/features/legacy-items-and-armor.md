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

## Development rollout (2026-11-09)
Published asset-only release `resource-pack-26.1.2-4f8f6f4d77fc` (952 entries; SHA-1 `c932a8ad4a96e3ccb085e74f920472983ed33363`). Anonymous download matches both hashes. All 515 prior entries remain present and byte-identical except the expanded shared font. Published asset history is merged into the feature branch.

The final immutable combined HUD/vehicle/armor JAR passed all 137 current unit tests and the complete 462-assertion isolated Paper probe again. Its SHA-256 is `cbf9f2a00d713408fb87edb90169b78629d8257cc8dc109757ea65dae13a4951`. The normal development server restarted cleanly at 17:21 local with that exact JAR and pack, 174 catalog entries, no startup errors and no temporary probes. Existing runtime overrides were preserved through additive configuration merges. Catalog reload also succeeds. The previously active Jabba event was restarted. Backup is retained in the feature worktree's ignored `.local/rollout-backup-20261109-172059`.

Armor implementation is also applied in the active HUD worktree, preserving its concurrent unrelated edits and future build path. Further vehicle-weapon edits after the immutable tested build are outside this rollout. Next action: reconnect and visually check helmet fit, equipment textures and `/recipes`.

## Client texture correction (2026-11-09)
Texture atlas and helmet GUI correction (2026-11-09): client log confirmed missing sprites under mandomc:custom because the 26.1.2 item atlas only discovers item/ by default. Moved 125 recovered PNGs unchanged into item/legacy and corrected model references. Added 22 missing 3D particle aliases. Pack builder now rejects existing-but-unstitched model textures and unresolved/cyclic/inherited aliases; 24 script checks pass. All four helmet GUI icons now tilt horizontally and vertically; the three 3D helmets explicitly face forward, correcting the backwards blue Durasteel icon. Worn/held transforms and geometry are preserved. Current active vehicle ammunition/sound assets are retained. Next action: publish the corrected pack, restart with the matching hash and check a client reload.

Texture/helmet correction published as `resource-pack-26.1.2-88cc6784f426` (1043 files, SHA-1 `2d69ab18f51b79f42be87df13d5304771f438b5a`). All 125 client-reported legacy sprite misses are moved byte-for-byte to `item/legacy`; 22 particle aliases are explicit. Four helmet GUI poses tilt on both axes and all three 3D helmets face forward. Combined release retains the newer 78-saber catalog and current vehicle ammunition/sounds, avoiding the simultaneous saber publication's stale armor paths. The pack builder validates atlas inclusion and inherited/cyclic texture aliases; 26 script checks pass. Public anonymous ZIP/manifest hashes verify. Next action: reconnect and inspect the client reload log and inventory icons.

The corrected combined pack is active after a clean asset-only restart at 17:38 local; the installed plugin JAR was preserved. The client downloaded/cached the exact server SHA-1 and completed a reload with both required packs: no MandoMC missing sprites or unresolved texture aliases remain in that reload. The unrelated official upstream Ezra bow-pulling/builtin-entity template warnings remain outside this correction. Server startup has no errors. Next action: visually inspect the new helmet inventory angles in game.

## Retired facility attribution
November 11: removed the obsolete two-line Tatooine facility attribution and its unused separator from `basic_power_cell`, `durable_mid_grade_power_core`, `batteries` and `switch`. Functional descriptions, catalog identity, standard badges/spacing and all acquisition rules remain unchanged; no replacement source claim is added.

The existing presentation refresher strips only this complete statement from these four identified catalog items on startup/join/inventory-open, plus uncancelled player pickup. It recognizes split/single-line and capitalization/punctuation variants. Custom names, enchantments, PDC, stack counts, model data and unrelated player lore remain intact; unknown or material-mismatched items are untouched. Stored containers are refreshed when opened, rather than rewriting offline world/player files. Existing vehicle, food and wrench refresh behavior is preserved.

Verification: 171 Java tests and 40 isolated Paper assertions pass, including freshly generated items, actual recipe previews, exact metadata equality on old customized stacks, idempotence, third-party/mismatched-item rejection, registered join handling, inventory-open and cancelled/allowed pickup. Deployed at 15:45 local November 11 after the Jawa chat released a short restart window. Actual runtime comparison confirms only those four lore arrays changed; all unrelated JAR entries are byte-identical to the live baseline, the pack is unchanged and KOTH is restored. Source commit `c85dc61` is pushed. Full stopped-runtime backup retained. Restart window released back to Jawa.
