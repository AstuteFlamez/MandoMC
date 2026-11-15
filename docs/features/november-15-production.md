# November 15 production promotion
Status: live on production; authenticated client walkthrough pending
Branch: bilal/nov15-production

## Scope
Promote all development changes since production `6bd2266`: the interactive tutorial, its playtest refinements and compact action bars, and the command-only Crates Sign. Carry over the explicitly placed SpawnWorld sign and reset the owner's production gameplay state for the new-player tutorial flow. An earlier Naboo world import is still staged for the same next production startup.

## Placement migration
`SpawnSignMigration` is an explicitly installed one-shot helper, built with `scripts/build-spawn-sign-migration.sh`. Its private input is an exported schema-3 sign journal plus the reset account UUID. It accepts only placed 3×1 `crates_sign` entries in SpawnWorld. It checks every target against air or its own prior marker before writing anything, retains journal custody, and verifies one native item display and three collision cells. Other blocks and other accounts' decorations are preserved. It writes a completion receipt and is archived after the successful cutover; no location or player data is shipped in the public source.

The helper verifies that Minecraft reports the reset owner as never having played and that no credit account/tutorial reward receipt remains before applying the migration. The sign's placed-world record is deliberately retained independently of the account reset.

## Account reset and recovery
Perform only while production is stopped. Preserve a matching snapshot of all affected native player files, plugin data, SQLite databases and the affected SpawnWorld region/entity/POI files before mutation. Clear the selected owner's native inventory, equipment, ender chest, location, stats, advancements and persistent progression; remove credit/tutorial-reward state, memberships, owned vehicle/trophy state, pending auction items and recorded parkour/weapon/vote progress. Preserve staff permissions, access settings and other players' records. Keep deleted originals in the private production backup and use hash comparisons against the stopped snapshot before applying writes.

The new tutorial uses the first-join flag and starts after the required resource-pack stack loads. Joining early during a partially completed migration is not permitted: the one-shot helper stops the server if its validation fails.

## Verification
The current development reactor passes 333 Java tests; 77 tooling checks and resource-pack validation pass. Prior native coverage includes 492 tutorial assertions and 152 sign lifecycle/restart assertions. A fresh isolated copy containing production's actual account files and affected SpawnWorld region passes the reset and sign migration checks, including a second JVM startup with exactly one retained visual and three owned cells. All unrelated SQLite rows and staff files are verified unchanged by the reset rehearsal.

Production release `495bc11` uses the exact checked main CI artifact (gameplay SHA-256 `43a578b3a2a5ba14f2806b94c8886c6163b051486cb1e727628d8c180ec45757`) and published pack (SHA-1 `fdf1f4ee0eaf7b7f3c3ba20e2516b0bb6435351f`), also verified by anonymous download. The stopped-state snapshot and all five removed native/Mythic player files are retained privately on the server and locally. Six gameplay data files changed; other SQLite rows and five staff/access files are preserved.

The final startup loads 276 items and five tutorial worlds with no errors. The native migration receipt verifies exactly one placed sign with three owned collision cells and a fresh-player account before first join. Naboo's pinned payload applied 902 new originals while preserving 2,126 existing files, producing 3,017 prepared chunks. Completion receipts and temporary helpers are retained as private rollout evidence.

The first activation exposed an existing uploader defect: retained backup archives in `plugins/update/` were consumed by Paper as old updates, even with non-`.jar` suffixes. The guarded helper stopped that incompatible build before gameplay use. After a clean stop, all four stale archives were moved outside the plugin folders and the exact checked artifact restored. Upload temporaries and fallback backups now stay under `.mandomc/transfers/`; 79 tooling tests pass, including repeated-update isolation and failed-install recovery. No gameplay source changed during this repair.

## Next action
Join production, allow the resource-pack stack to load, and play the automatically offered fresh-player tutorial; visually check the sign outside the crates building.
