# Feature: Starter-kit handbook

## Player experience
A new player receives one current native MandoMC Handbook alongside the existing first-join gear. The book is the same eleven-page handbook produced by `/tutorial`, with the installed content and clan guidance.

## Rules
StarterKit retains ownership of `starter_kit_v1` and `starter_kit_pending_v1`. First join schedules one atomic kit delivery; full storage keeps the kit pending for an eligible reconnect. The capacity check includes the handbook. Failure gives none of the kit, overwrites no items and leaves existing handbook contents unchanged. Once granted, reconnects and deaths never refill either the gear or book. Established players without a pending kit keep their existing eligibility.

If an eligible player already has a marked handbook in storage or the offhand, successful kit delivery refreshes that book using TutorialHandbook.create(), preserving its stack count and adding no duplicate. Storage/offhand refresh commits only after the complete kit fits. Armor, unrelated offhand gear, cursor and crafting inventories are not closed or modified.

The resource-pack success welcome and `arrival_tutorial_v1` marker remain separate from kit ownership. Pack callbacks do not give books. `/tutorial` still opens, gives or refreshes the book with its existing full-inventory/cursor/crafting behavior. No resource-pack or messaging change.

## Implementation and deployment
StarterKit uses the existing TutorialHandbook identity/template API. No book-generation fork or bootstrap/command lifecycle change. The release overlay replaces only StarterKit.class after checking the exact live JAR, original StarterKit hash and matching handbook/kit dependencies; every other JAR entry remains byte-identical. Rebuild the overlay if another task changes the baseline before deployment.

## Verification
The native HandbookProbe full suite runs only with its explicit marker on disposable localhost port 25590. It exercises scheduled new-player delivery, original material/generated items, reconnect/death behavior, established-player eligibility, insufficient room including the extra book, pending reconnect recovery, existing storage/offhand stacks, failed-refresh atomicity and continued actual `/tutorial` command reuse. Existing handbook metadata, command, full-inventory, cursor/crafting and pack-welcome checks remain included.

The build passes 217 Java tests. The disposable Paper suite passes 547 native assertions. Deployment rebases the one-class overlay on the NPC-head release; all other entries remain byte-identical. The installed handbook configuration is preserved.

Live JAR: `f4cbaa76678cfdca39b709876926206c74bc65e02d0a4d78f461455dc5d5437e` from baseline `a10aabe28da5f5c279c58d1dd85b8150415babf69e92f8330dcdf68e5426ff45`. Full stopped-runtime backup retained privately. Normal startup is clean, no checker is installed, active Jabba and queued KOTH are restored, all seven gray Jawa UUIDs and frozen originals are preserved, and unrelated configurations/plugins and pack properties are unchanged. Restart window released.

## Next action
Client-check a new player receives the usual kit plus one handbook.
