# Feature: Recipe acquisition context
Status: staged and verified
Branch: bilal/bantha-hide-utilities

## Player experience
Clickable ingredient previews in `/recipes` show brief acquisition lines as well as the existing recipe link. Craftable ingredients retain their preferred recipe and Back navigation. Ingredients without recipes send the same source context when clicked. Only cloned guide previews gain these lines; catalog templates, outputs and normal item lore stay untouched.

## Audited scope
The deployed guide exposes 207 definitions with 106 distinct ingredient IDs. Every one receives a nonblank source note, including craftable inputs. Unused/hidden catalog items are outside this update. Recipes, shapes, quantities, categories, native replacements and preferred-recipe settings remain unchanged.

The live stock and category definitions in `mobs/jawas.yml` distinguish brown sellers from gray salvage buyers. Notes name Tatooine and the actual category: Bantha Hide/string use Utility supplies; sugar cane uses Farming starters; poppies/dandelions use Greenery & dyes; lapis and native redstone/Electrum use Mineral supplies. Native redstone is the existing Electrum material identity. Gray purchase lists do not count as acquisition stock. Wool and paper notes describe their craft chain through stocked string/sugar cane instead of claiming these finished items are sold.

Bantha spawning/drop configuration confirms Tatooine and 1–3 hides. Tatooine terrain verification in the Dune Sea feature confirms cave Agrinium, Durasteel, coal and clay. The Hoth cave feature confirms deeper-stone Electrum. Ore notes retain 1–5 scraps, tools, Silk Touch and Fortune rules. Mandalore's live Beskar event has 25 nodes; notes explicitly require an active Beskar Rush rather than promising permanently available ore or automatic scheduling (live event weight is zero).

Jabba's seven-item cache loot pool is on current Tatooine and available during its event. Brown Jawa salvage can also drop Wiring Coil. Kyber notes include the actual crafting route plus random Kyber Crate rewards on Batuu; keys come from Ilum completion with the current 24-hour cooldown. Relevant metal sheets and Beskar Scrap list possible KOTH Crate rewards, with keys from the winning clan's KOTH reward on Hoth. Crate rewards are described as possible, not guaranteed.

Native source notes identify the crafting station, mob, geode or terrain type where a planet location is not verified. Rabbit notes disclose the current natural-spawn restriction; they do not invent a rabbit shop or planet spawn. No source changes alter acquisition mechanics.

## Ownership and deployment
`RecipeGuide` renders configured source strings as separate non-italic lore lines in cloned previews. `items/recipes.yml` owns the text. `scripts/build-recipe-acquisition-overlay.py` applies only these visible source IDs and the RecipeGuide class family to a hash-pinned live JAR, plus the matching runtime source-config update. All unrelated entries and non-source recipe data are verified unchanged.

## Verification
The repository build passes all 174 tests. A focused isolated Paper run passes 1,357 assertions: exact 106-ingredient coverage; source-line text/format; unchanged templates and native results; actual detail previews; craftable click-through and Back navigation; source-only chat actions; and retained twenty Bantha Hide Utilities entries. A separate live-config audit checks actual brown stock/category membership, event worlds, node count, crate reward pools and unchanged non-source recipe settings. No probe or actors are installed live.

## Pause note
Staged; no live stop. The coordinator currently controls restart scheduling. Next action: acquire the assigned window, rebase to the latest JAR, apply only the guide/source patch and verify normal startup.
