# November 15 production promotion
Status: verified rehearsal; production cutover pending
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
The current development reactor passes 333 Java tests; 77 tooling checks and resource-pack validation pass. Prior native coverage includes 492 tutorial assertions and 152 sign lifecycle/restart assertions. A fresh isolated copy containing production's actual account files and affected SpawnWorld region passes the reset and sign migration checks. All unrelated SQLite rows and staff files are verified unchanged by the reset rehearsal.

Before activation, validate the pending Naboo payload against its pinned SHA-256 and previous frozen-scope hash. Its existing helper and payload match the earlier successful native rehearsal and preserve all previous originals. The production release uses the exact checked main CI artifacts, published pack, managed-upload receipt and a clean restart. Record startup, migration receipts, item registration and account-reset verification after activation.

## Next action
Complete the checked main promotion, stopped-state backup/reset and one-shot sign import, then verify the fresh production login and tutorial.
