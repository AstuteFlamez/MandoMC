# Feature: New-player credits
Status: playable / live
Branch: bilal/new-player-credits

## Player experience
A genuinely new player starts with 2,500 credits. Returning players keep their balance.

## Rules
The credits service snapshots Paper's first-play status at join. Its existing single economy writer initializes an absent account atomically. A persisted entry, including zero, prevents another grant. Returning players with no ledger entry receive a persisted zero entry. Payments or administrative balances that already created an account are preserved. Death and respawn have no credits initialization hooks. No additional chat message is emitted; balance commands keep existing formatting.

## Smallest playable version
One new account receives 250,000 integer cents; repeated login and process restart preserve the amount. Spending the balance to zero never replenishes it.

## Ownership and integrations
Native CreditLedger remains the sole balance authority. Initialization uses its existing AtomicData save together with auction state, through CreditService's single writer. No starter-kit, welcome-display-name or vendor economy changes.

## Parameters
`economy/credits.yml` has `initial-balance: '2500.00'`, a nonnegative exact decimal accepted by the existing credits amount parser. Restart to apply. Existing balances do not migrate or reset.

## Verification
195 Java tests pass, including concurrent initialization, retry after rejected persistence, new and returning accounts, explicit zero balances, modified balances and reload durability. Final overlay passed 19 native Paper assertions: 12 first-login and seven after a clean process restart. Deployed Jawa supply and sell-supplies methods are retained exactly; only account initialization is added to their ledger source.

## Pause note
Deployed on 2026-11-11 with one normal restart and no live probes/test accounts. Final JAR SHA-256 `288c0ad7ea229977aa895ba262e96fd0af8f6b742288dfa9a0b4e3e2dbdf76f5`. All unrelated JAR entries, plugin binaries, static configuration and pack settings are preserved. Existing balances and a real returning player’s zero balance are preserved; Jabba current/KOTH next restored. Seven gray NPCs retained their identities and passed two healthy head-tracking samples; nine decor anchors and 2115 frozen chunks are intact. A stopped-runtime backup and deployment receipt are retained privately. Window released. Next action: observe the next genuinely new player receiving the starting balance.
