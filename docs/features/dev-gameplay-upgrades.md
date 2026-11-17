# Feature: recipe search, M4-ND, KOTH equipment, Jawa shops and Saber Forge
Status: verified; development activation pending
Branch: bilal/dev-gameplay-upgrades

## Player experience
The owner approved these five changes together on November 16, 2026. Vehicle interplanetary travel is deferred. This decision supersedes earlier material-only KOTH rewards and ordinary supply categories on brown Jawas.

Recipe menus include Search, Clear Search and an output/ingredient-use toggle. A private native text dialog accepts partial, case-insensitive names or IDs. Pagination and Back retain the query, category and mode. Existing ingredient source links and saber color ordering remain.

M4-ND replaces Cassian Andor at Cassian's saved Batuu anchor. The native golden humanoid droid looks toward nearby travelers and opens a private question dialog. `/ask` offers the same explanations. The Rebel Pilot and existing scenery retain their owners. The droid knows his fixed identity, speaks formally with light dry humor, and explains maintained MandoMC notes. No tools, commands or player-state actions are granted to the model. Unsupported topics receive a local refusal; missing AI service or quota falls back to written answers. User questions are labeled as being sent to Groq.

KOTH retains one saved key and one reward per opening. Its 100-ticket pool is SE-14C 18, A280 16, EE-3 12, Jawa Ionization Blaster 10, Cycler Rifle 12, Bossk’s Relby V10 8, Jetpack I book 10, Speeder Bike 6, U-Wing 3, X-Wing 2, TIE Fighter 2 and Black Kyber 1. There are no armor rewards. Black Kyber is required for the new Darksaber recipe with Beskar, Phrik and Energy Conduits. The old white-saber stonecutter conversion is removed. These are configurable initial development weights.

Every brown Jawa sells six basic components plus one to three independently randomized advanced items. Each entity's selected IDs are saved in its persistent data when spawned/first migrated; menus, chunk loads and restarts do not reroll it. The same entity keeps its stock for its lifetime. Seven existing gray Jawa anchors receive seven distinct ordinary-goods categories, readable floating titles and Buy/Sell Goods controls. All retain the existing protected salvage tray, exact item matching, credit authority and recovery behavior. Existing positions and model owners remain intact.

A craftable Saber Forge is a marked smithing-table block. Hold a saber and use it outside combat in Survival/Adventure to choose upgrades. There are six permanent points, at most three in one path. Damage adds one melee damage per level; Speed adds 0.1 attacks/second; Defense reduces guard drain from hits by 10%; Throw adds two throw damage. Each path has a configurable material cost multiplied by the next level. Choose styles before upgrading; stonecutting forged sabers is blocked to avoid losing paid upgrades.

## Rules and ownership
Search, trade and forge inventories block preview extraction. The forge keeps the actual saber in its inventory slot and revalidates the exact item, held slot, machine, distance and combat state before payment. Only exact catalog materials pay. Item attributes and persistent levels survive serialization, drops and existing throw custody. Native damage/protection remains authoritative. Marked forge blocks retain their identity through ordinary drops and chunk persistence; piston movement is blocked.

Groq HTTP uses a dedicated worker, bounded queue and timeouts. Questions and selected public notes are sent; inventories, private files and credentials are never added to prompts. Plain replies cannot execute commands or inject MiniMessage. Default limits are ten successful AI answers per player per day, a 20-second personal cooldown, 15-second request spacing, 140,000 reserved/actual tokens per day and an 800-request safety ceiling. Personal days reset in America/Chicago. Reservations persist before HTTP; failures refund the player's answer. Provider 429 retry intervals and low reported token capacity pause AI calls. FAQ and cached answers do not spend AI allowance. The free Groq account has no paid fallback.

The API key lives only in ignored, owner-only local/runtime `secrets/groq-api-key` files or the `MANDOMC_GROQ_API_KEY` environment variable. `.gitignore` also explicitly excludes any file named `groq-api-key`. No key belongs in tracked YAML, GitHub secrets for this dev release, logs or resource packs.

Crate and scenery startup now wait for their vendor catalogs/blueprints before validation. This preserves one shared crate service and prevents asynchronous vendor startup from disabling Ilum, KOTH rewards or scenery.

## Content and guidance
Bundled defaults and reviewed server overrides are paired. Update the interactive Shops lesson, event explanations, recipe search lesson, eleven-topic handbook, ingredient acquisition hints, travel-guide hints and droid knowledge together. The Shops exercise buys a Wiring Coil using practice credits, then sells supplied String through the normal gray tray. Rewards and isolated tutorial inventory behavior remain unchanged.

## Verification
All 353 Java tests, 85 tooling checks, 133 native gameplay checks and 548 focused tutorial checks pass. The disposable Paper probe exercises actual search/detail/Back, native text dialogs, seven existing gray shops/titles, entity stock, actual Darksaber crafting and ingredient rejection, exact forge payment, native combat attributes, item serialization, Cassian replacement, off-topic refusal and the Groq response path. No probe is installed on shared development. Client appearance and gameplay balance still require player feedback.

## Pause note
Next action: complete the final native rehearsal, then deploy the exact verified build/configuration/pack to development with a stopped-runtime backup and normal restart. Production is excluded.
