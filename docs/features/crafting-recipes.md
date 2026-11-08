# Crafting recipes and guide

Players craft custom items at normal crafting tables and use `/recipes` (alias `/recipe`) to browse available recipes. `/recipes <item>` opens a result directly. The guide filters by category, paginates results, shows the crafting grid and output, and lets players click ingredients to follow recipes or read gathering sources. Back returns to the prior view. Preview items cannot be taken, including via shift-click, number keys, drag or double-click. No blueprints or advanced workbench are in scope.

`recipes.yml` is the shared source for registration and guide display. Each recipe has a stable ID, catalog result, output amount, category, rectangular shape up to 3x3, and symbol-to-ingredient map. Custom ingredients use catalog IDs; vanilla ingredients use `minecraft:material`. Multiple recipes may share a result; browsing lists all, while direct lookup/ingredient links open the first. The first content is the four legacy robe recipes using Bantha Hide.

Custom ingredients register as native exact choices, and vanilla ingredients as material choices. Native crafting owns consumption, shift-crafting and output transfer. Thus renamed or otherwise modified custom ingredients do not qualify in this first pass. Restart after recipe or ingredient-metadata changes; `/mandomc items reload` does not rebuild recipe snapshots. Unknown items, malformed shapes, unmatched symbols, unsupported fields and invalid output amounts fail setup instead of silently registering a broken recipe. Registration rolls back on failure; disable unregisters owned recipes and closes guide windows.

Design references: the recovered legacy `RecipeRegistry`/`RecipeViewerGUI` and [BetterPvP's crafting viewer](https://github.com/BetterPvP/BetterPvP/blob/master/core/src/main/java/me/mykindos/betterpvp/core/recipe/menu/GuiCraftingRecipeViewer.java), whose ingredient navigation informed this guide. Implementation is new MandoMC code with no BetterPvP dependency or copied source. Smelting, blueprint progression, custom workbenches and bulk custom-stack ingredients remain outside this first normal-crafting slice.

Verification: all 51 Maven tests pass, including malformed recipe rejection, immutable definitions and bundled recipe/item references. Live Paper checks still pending: crafting and shift-crafting all four robes, rejecting ordinary echo shards/leather, hide drops, guide pagination/navigation and inventory transfer attempts. No production server restart or deployment was performed from this side conversation.

Next action: deploy on the next coordinated restart and run those in-game checks.

Deployment update: deployed locally and cleanly restarted Paper at 17:12:42 on 2026-11-08. Plugin hash matches the verified build, all 11 items load, and `/recipes` registers successfully. Interactive crafting/drop/client checks remain pending.
