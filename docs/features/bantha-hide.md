# Bantha hide and Desert Robes

Banthas drop 1–3 Bantha Hide per death, giving players a renewable route to the existing Desert Robes set. This is a new initial balance choice; the legacy Bantha config dropped `bantha_wrap` at 50%, while its robe recipes consumed `bantha_hide`.

The four recipes restore the legacy leather-armor shapes: hood 5 hides, tunic 8, pants 7 and boots 4 (24 for a full set). Ordinary leather and echo shards do not qualify. Hide keeps the legacy `bantha_hide` identity and echo-shard base, with the recovered legacy hide texture, renamed from ronto_hide to bantha_hide. The distinct base avoids collisions with vanilla leather armor recipes. Rarity is Common; the material category uses a text label until its badge art is ported.

MandoMC owns the catalog item and shaped recipes. The MythicMobs integration adds hide to its death-drop list once; natural population cleanup does not create drops. Recipes register on enable and are removed on disable. Drop amounts live in `bantha-hide.yml`, recipe shapes in `recipes.yml`, and item presentation in `items/materials.yml`. Restart after changing recipe ingredients or hide metadata, as native exact-choice recipes snapshot the ingredient at registration.

Validation: Maven verification passes all 51 tests. New drop integration, actual crafting/shift crafting, normal-item rejection and client appearance still require live Paper/client validation. The running server was left untouched to avoid interrupting concurrent main-thread work.

The player-facing recipe guide is documented in [crafting-recipes.md](crafting-recipes.md).

Next action: deploy on the next coordinated restart and verify one Bantha death plus all four crafting recipes in-game.

Deployment update: deployed locally and cleanly restarted Paper at 17:12:42 on 2026-11-08. Plugin hash matches the verified build, all 11 items load, and `/recipes` registers successfully. Interactive crafting/drop/client checks remain pending.
