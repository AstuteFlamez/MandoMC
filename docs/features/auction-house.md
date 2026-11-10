# Native player auction house
Status: verified native implementation; deployment pending
Branch: bilal/durasteel-progression

## Player experience
Use `/ah` to browse/sort player listings, `/ah search <name>` to filter item/seller names, hold an item and use `/ah sell <price> [amount]` to list it, confirm a purchase, or reclaim items through `/ah collect`. The price buys the listed stack. Existing credits shown by `/balance` and `/pay` are the currency. No new plugin is installed: the user explicitly required the auction house inside MandoMC.

Chosen playtest rules: fixed-price sales, eight simultaneous listings per player, 48-hour listings, no listing fee or sale tax, minimum price 1 credit and maximum 1,000,000 credits. Bidding is excluded. Damaged gear retains its damage. Cancelled, expired and purchased items remain in permanent claim storage until collected; a full inventory never drops them into the world. Creative/Spectator and combat-tagged actions are denied, including actions from an already-open menu.

## Ownership and integrations
MandoMC owns the entire implementation. Familiar listing, confirmation, cancellation and collection behavior follows established [AuctionHouse](https://www.spigotmc.org/resources/auctionhouse.61836/) and [CrazyAuctions](https://github.com/Crazy-Crew/CrazyAuctions) patterns. Those projects were research references only. The external candidate was removed from the disposable server and was never installed live. No Vault economy bridge, external market binary or second currency is added.

`CreditLedger` atomically persists balances, active listings, item claims and accepted-listing receipts together in its existing `credits-data.yml`. `/pay`, admin adjustments and market changes share one IO executor and synchronized atomic replacement. A sale removes the listing, transfers the price once and creates the buyer's item claim in one commit. Failed saves, stale confirmations, wrong-owner cancellation and insufficient funds leave stored state unchanged. Expiry moves ownership back into the seller's claims without fees.

Player-data receipts bridge inventory handoff. Listing first saves the removed held quantity and escrow receipt together in player.dat. A writer barrier on recovery waits for prior queued listing commits; accepted listing IDs prevent refunding already-committed escrow. Delivery saves the actual item and a delivered receipt together in player.dat before completing its ledger claim. Recovery acknowledges that receipt without giving another item. Standard native player saves run on the server thread because player state is thread-confined; ledger IO runs on the writer. Items keep original metadata and receive no auction PDC or tooltip changes. Receipt IDs remain in the ledger to recognize old saved player-data receipts after later sales/claims.

## Parameters and lifecycle
Defaults and reviewed overrides are `economy/auction-house.yml`: listing limit, hours, min/max total stack price and all interface/messages. Restart for changes. One minute expiry checks do not write unchanged snapshots. The native inventory menus use shared gray hidden-tooltip fillers and cancel click, shift, hotbar and drag transfers.

Back up the whole stopped runtime for rollback: the credit/market snapshot and player.dat inventory receipts must remain consistent. Older binaries do not preserve the new market section on subsequent balance writes, so rollback requires the complete stopped backup, not only a JAR replacement. Runtime data and player identities remain ignored.

## Verification
Four unit scenarios cover atomic sale/reload, expiry, cancellation, wrong owner, listing limits, competing buyers, seller balance overflow, insufficient funds and failed persistence. 33 isolated native auction/quantity assertions passed, covering actual commands/confirmation/claim menus, full inventory, custom stack and damaged saber identity, stale confirmation, delivery/listing recovery receipts, mode/combat restrictions and no additional plugin.
