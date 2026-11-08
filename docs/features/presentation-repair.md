# Presentation repair

Consolidate the previously accepted robe artwork (e6e6d89), Massiff Ribs model/texture and consumable badge into the required pack. Restore the legacy material badge as Č for Bantha Hide. Robes use the supplied artwork with white undyed tint and no obsolete overlays.

Custom menus fill unused slots with light gray stained glass panes named one space and with hidden tooltips. Recipe previews explicitly clear unused positions in the 3x3 grid to air. Native Paper checks exercise the actual admin menu and all four recipe previews.

Existing catalog-owned Bantha Hide and Massiff Ribs stacks refresh their lore and item-model fields at startup/login and when inventories open. Counts, names and other metadata are retained. Normal items are not changed. This also aligns older hide ingredients with the current recipe templates.

Validation: 51 Java tests and nine pack tests pass. Focused live checks cover worn equipment IDs, both category glyphs, real menu filler/grid contents and old hide migration. Client texture appearance remains to be confirmed after reconnecting with the consolidated pack.
