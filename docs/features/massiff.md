# Massiff legacy restoration

Massiffs use the legacy idle/chase/bite behavior and original model assets. Idle speed is 0.12, chase uses the legacy 3x multiplier, and biting uses 0.33x. The brain runs every 35 ticks; a bite plays its original animation/sound, then deals 6 damage and knockback after 10 ticks. Health is 50 and native melee damage is zero, so the scripted bite is the damage owner. Group levels still appear in names; legacy combat stats are fixed rather than level-scaled.

The copied `massiff-skills.yml` is semantically identical to legacy `MassiffSkills.yml`. The existing normalized blueprint already matches all legacy geometry, animation and embedded texture data. Legacy ModelEngine state-machine/default idle/walk settings match the current runtime; no global ModelEngine configuration replacement is needed. The mob's legacy Model section restores view radius 64, drive false and damage tint true. Current player targeting, anti-transformation safeguards and model reattachment on chunk load remain; an explicit Mythic threat table enables the legacy combat conditions.

Massiff Ribs retain their legacy ID (`massiff_ribs`), name, lore, uncommon presentation and original smoked-ribs texture. They use native cooked-mutton food behavior. One rib item drops on 75% of Massiff deaths, configured in bundled `mobs/drops/massiff.yml`. `MassiffDrops` appends the catalog item to Mythic's death-drop list; the obsolete legacy drop command is removed, and despawns do not award food. The item is available through the normal catalog/browser. Texture provenance is recorded in `resource-pack/massiff-sources.json`.

## Deployment and verification

Source-only change in this side conversation: no live server restart, config replacement or pack publication was performed, to avoid disrupting the main thread's active work.

Plugin build/tests and resource-pack build passed. YAML parsing and exact legacy skill comparison passed. Blueprint comparison confirmed identical geometry, animations and embedded textures. In-game idle/chase/bite timing, edible item consumption and death-drop integration still require verification.

At the next coordinated deployment, stop the server, deploy the rebuilt plugin, copy the Massiff block of `server/config/common/mythicmobs/sandy-mobs.yml` to its runtime mob file and install `server/config/common/mythicmobs/massiff-skills.yml` in MythicMobs/skills. The plugin installs missing item/drop defaults and merges missing presentation badges. Publish the pack containing the ribs texture and consumable badge, then start Paper and reconnect with that pack. Preserve other in-progress mob definitions and player data.

Deployment completed 2026-11-08: live Massiff block/skills and catalog food integration loaded after clean restart. Hosted pack `resource-pack-26.1.2-29399f01207f` is selected. The source-only note above records the initial implementation checkpoint; client behavior checks remain pending.
