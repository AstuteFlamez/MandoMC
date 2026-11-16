# Feature: Building signs
Status: native verification
Branch: bilal/star-wars-signs

## Player experience
Staff obtain `shops_sign`, `leaderboards_sign` or `cantina_sign` through `/mmc give <item>` or `/mmc give <player> <item> <quantity>`. Right-click a block face to place a sign. Break any part to recover one original item. The signs also appear in the existing permission-gated give browser.

## Rules
Shops occupies three horizontal blocks; Leaderboards occupies five. The clicked cell is the horizontal center. Cantina occupies four vertical blocks, extending upward from the clicked placement cell on walls/floors and downward from a ceiling. Wall signs face outward; floor/ceiling signs remain upright and face the player.

Every occupied cell must be clear, loaded, within the build height/world border, and editable. Existing territory, clan and block-event protections apply to every cell. One item is consumed even in Creative. Breaking any part returns one item with its original metadata. Full inventories queue recovery in the existing custody journal. Explosions and pistons cannot separate the parts. Existing Crates Signs, helmets and skulls retain their identities and footprints.

## Smallest playable version
The three supplied models and textures become static decorations, with no crafting, reward, shop inventory or functional menu interaction.

## Ownership and integrations
ItemCatalog owns identity. RancorTrophies remains the sole placement/display/custody owner. TrophyJournal schema 4 records width and height, reads schemas 1–3, and rejects overlapping reservations and footprint changes during transfers. Legacy records default to height one. Restore the matching journal backup with an older plugin when rolling back.

## Parameters
The stable IDs and model keys are `shops_sign`, `leaderboards_sign`, `cantina_sign` and `mandomc:decor/<id>`. Lore is in `items/definitions/building-signs.yml`; shared sign feedback is in `trophies/settings.yml`. Changes require a normal restart and matching resource pack.

Original source files and hashes are retained under `resource-pack/art-sources/building-signs`. The importer preserves texture bytes and supplied Java UV coordinates. Leaderboards/Cantina geometry is halved around the Java model origin to keep element bounds within −16…32, with equivalent pose/display scale compensation restoring the supplied dimensions. Cantina's placed display is lifted one block so its collision footprint starts at the bottom.

## Verification
Run `scripts/build.sh`, the Python tooling suite and `scripts/build-resource-pack.py`. `BuildingSignsProbe` runs only on disposable localhost port 25593 and selects a model with `-Dmandomc.test.sign=<id>`. It exercises six placement faces, outer-cell obstruction/protection, collision, metadata, single-item recovery, full inventories, explosions, pistons and an actual JVM restart. A final run using `crates_sign` checks the existing sign against the extended service.

## Pause note
Implementation and automated builds are complete. Native lifecycle/restart tests and development rollout are in progress. Next action: finish isolated verification and deploy the tested scoped build to development.
