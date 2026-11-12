# Feature: New-player credits
Status: playable (deployment pending)
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
Validated implementation and scoped three-entry deployment tooling; shared runtime unchanged. Next action: deploy in the coordinator-assigned window and verify the normal live server.
