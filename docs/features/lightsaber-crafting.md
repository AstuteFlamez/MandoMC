# Finished lightsaber crafting
Status: verified implementation; coordinated deployment pending
Branch: bilal/durasteel-progression

## Player experience
Craft a kyber crystal and emitter coil, assemble one saber for a blade color, then optionally choose a character/model variant at a stonecutter. `/recipes <item>` explains each actual workstation and links back through the materials. Every registered finished saber has a route; combat attributes and existing models remain unchanged.

The user delegated recipe choices on 2026-11-09, superseding the former finished-saber deferral. All 78 existing variants have attack damage 10.5, so variants within a blade color are cosmetic choices rather than separate power tiers.

## Rules and costs
White Kyber uses four amethyst shards, four Durasteel scraps and one ordinary glass block in `ADA / DGD / ADA`. Each colored crystal uses one White Kyber and one matching ordinary dye, shapeless. These are chosen survival recipes, not claims about lore or a restored Ilum source.

The emitter coil uses two Electrum Sheets above/below a Circuit Board and two Transparisteel Shards to its sides: ` E / TCT / E `.

Each of seven assemblies uses one emitter coil, one color-matched kyber, one Durasteel Sheet, one Plasma Capacitor Array and one hilt in ` EC / SH / P `. Blue Obi-Wan uses Straight; green Qui-Gon uses Pike; red Vader uses Straight; orange Baylan uses Great; purple Windu uses Great; yellow Ventress uses Whip; white Ahsoka uses Fang. Every result is one genuine existing catalog saber.

The other 71 styles consume one corresponding pristine base-color saber at a stonecutter, returning one style. Green Ahsoka Shoto follows its green blade despite the historical white display-name color. The dark saber uses the white family. Only exact catalog input matches: damage, renamed/lookalike metadata and missing identity cannot yield a free pristine replacement. A style recipe cannot convert its own input to itself. Standard repairs and saber charge behavior remain owned by their existing systems.

## Ownership and lifecycle
`CraftingRecipes` owns native registrations; `RecipeDefinition` owns parsing shared with `RecipeGuide`. Stonecutting has a dedicated section, native `StonecuttingRecipe`, exact input and a stonecutter icon in the guide. Closing/reloading removes every registered key. Existing exact native-shaped recipes remain collision-checked. No artwork or combat defaults are changed.

## Verification
Unit checks resolve every ingredient/result and reject ambiguous shaped recipes. Isolated Paper checks cover native assembly/crystal/emitter crafting and consumption, all 71 exact stonecutting inputs/outputs, worn-item rejection and actual workstation guide navigation. Client appearance remains a playtest check.
