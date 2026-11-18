# Finished lightsaber crafting
Status: deployed; client playtest pending
Branch: bilal/durasteel-progression

## Player experience
Craft a kyber crystal and emitter coil, assemble one saber for a blade color, then optionally choose a character/model variant at a Saber Workbench. `/recipes <item>` explains each actual workstation and links back through the materials. Every registered finished saber has a route; all styles share the same combat attributes.

The user delegated recipe choices on 2026-11-09, superseding the former finished-saber deferral. All 78 existing variants have base attack damage 8, so variants within a blade color are cosmetic choices rather than separate power tiers.

## Rules and costs
White Kyber uses four amethyst shards, four Durasteel scraps and one ordinary glass block in `ADA / DGD / ADA`. Each colored crystal uses one White Kyber and one matching ordinary dye, shapeless. These are chosen survival recipes, not claims about lore or a restored Ilum source.

The emitter coil uses two Electrum Sheets above/below a Circuit Board and two Transparisteel Shards to its sides: ` E / TCT / E `.

Each of seven assemblies uses one emitter coil, one color-matched kyber, one Durasteel Sheet, one Plasma Capacitor Array and one hilt in ` EC / SH / P `. Blue Obi-Wan uses Straight; green Qui-Gon uses Pike; red Vader uses Straight; orange Baylan uses Great; purple Windu uses Great; yellow Ventress uses Whip; white Ahsoka uses Fang. Every result is one genuine existing catalog saber.

Style recipes consume one corresponding base-color saber at the Saber Workbench, returning one style. Green Ahsoka Shoto follows its green blade despite the historical white display-name color. The dark saber uses the white family. Style inputs require the genuine matching catalog identity. Enchantments, names, durability and repair history are preserved; styling cannot repair or reset an upgraded saber. A style recipe cannot convert its own input to itself. Standard repairs and saber charge behavior remain owned by their existing systems.

## Ownership and lifecycle
`CraftingRecipes` owns native registrations; `RecipeDefinition` owns parsing shared with `RecipeGuide`. Stonecutting has a dedicated section, native `StonecuttingRecipe`, identity-checked input and a Saber Workbench icon in the guide. Closing/reloading removes every registered key. Existing exact native-shaped recipes remain collision-checked. No artwork or combat defaults are changed.

## Verification
Unit checks resolve every ingredient/result and reject ambiguous shaped recipes. Isolated Paper checks cover native assembly/crystal/emitter crafting and consumption, all 71 exact stonecutting inputs/outputs, worn-item rejection and actual workstation guide navigation. Client appearance remains a playtest check.

The Saber Workbench and enchantment-book revision supersedes the original pristine-only style rule. See [saber enchantments](saber-enchantments.md) for the complete current rules.
