# Optional tutorial hub and simplified metals

Status: deployed to development; client playtest pending.
Branch: bilal/tutorial-hub-metals

## Player behavior
New players arrive at the center of their private tutorial hub after resource packs load. Five clickable holograms offer independent Survival/Planets, Clans, Combat, Shops and Vehicles lessons. The sixth hologram exits to normal play. `/tutorial` returns to the hub during a lesson; `/tutorial end` exits. Completed sections remain saved across reconnects and restarts. Interrupted exercises can be restarted from their section hologram. Each section awards $500 once, for $2,500 total; replays are practice. Starter equipment does not depend on completing an optional lesson. Naboo remains the recommended first base destination.

Survival opens the real Galaxy Map, whose descriptions compare resources, base-building, danger and activities. Players then mine three Agrinium ores with a practice stone pickaxe, smelt their three scraps in a normal furnace, browse `/recipes`, and craft a pickaxe using three ingots and two supplied sticks. The furnace and crafting table are native blocks. Only the three lesson ores can be mined.

Clans retains the zone map, wilderness, practice clan creation, claim and disband exercises. Zone-colored particles outline only the player's current chunk. Practice claims never enter the real clan store.

Combat uses one stationary Gamorrean Guard, one Quarren Guard and one Twi'lek Guard. After shooting them, players must open the Weapons recipe category before receiving their saber. A separate Gamorrean must actually be hit by a native saber throw. Misses remain on the lesson. Practice throws become available one second after the saber returns; ordinary combat timing is unchanged.

Shops begins in the simulated Tatooine market: purchase 16 String for $24 in practice credits. Players then teleport to real public Batuu. A surveyed waypoint trail follows the approach, stairs and shop entrance. Particle packets go only to that player. Inside, the prompt asks them to sell to a real gray Jawa. The regular selling tray, rates and controls are used, but tutorial items and payments stay outside real shop escrow and the credit ledger. Successful selling returns to the tutorial hub. Normal players retain normal shop behavior.

Vehicles keeps actual X-wing placement, boarding, native W/D/A/S controls, cannon target and landing. Completion returns to the hub. Event descriptions are available with the planets; timed event pages no longer form part of the route.

## Metal progression
Every existing themed ore drop is exactly one scrap, independent of Fortune. Existing tool and Silk Touch protections remain. All eleven metal scraps have a normal-furnace recipe producing one matching ingot. Nine-scrap crafting recipes and all sheet production recipes are removed. Every sheet ingredient becomes the corresponding ingot. The catalog no longer lists sheets; stable legacy sheet IDs resolve to ingots for queued rewards. `/recipes`, source descriptions and the handbook match the new process.

KOTH keeps its reward weights and quantities, replacing sheets with ingots. Beskar Rush selects ten spaced hosts from the previously verified mine. All ten nodes yield one scrap. Recovery still accepts previous 25-node journals, so upgrading cannot strand an old event's temporary terrain.

## Saved items and transaction boundaries
`MetalMaterials` converts old sheets one-for-one when inventories are loaded/opened or items are picked up, including nested shulker boxes and bundles. Old scrap presentation is refreshed to match the exact furnace recipes.

`scripts/migrate-metal-playerdata.py` performs the offline sweep. It uses canonical ingot ItemStack bytes exported by the native probe, handles inventory/ender storage, nested NBT, Paper byte-array inventory escrow and base64 saved items/auction receipts. Dry-run is the default. Applying requires the world's native session lock, per-file original backups, source-byte checks and atomic replacement. Unrelated fields, slots and stack counts are preserved. Backups and `.dat_old` files remain unchanged.

Completion is saved before payment. The credit ledger atomically saves each `(player, section)` receipt with its balance. Retry after an interrupted callback cannot pay twice. Legacy tutorial reward receipts are retained. Quitting or restarting while in public Batuu restores the original inventory and location using the existing escrow.

## Verification
The dedicated native probe is `scripts/build-tutorial-hub-probe.sh` / `scripts/integration/TutorialHubProbe.java`. Run only in an isolated localhost:25590 runtime. It exercises real ore breaks, timed furnace smelting, native pickaxe crafting, actual blaster shots and saber contact, public gray-Jawa menus, player-only particle packets, full ModelEngine flight/cannons/landing, native Beskar mining, saved section payments and actual JVM recovery. It exports templates for the offline item migration.

A normal Minecraft client playthrough is still required to confirm visual readability and how the walking trail feels at the user's GUI scale. Native API assertions do not establish visual acceptance.

The final reactor passes 342 Java tests (338 gameplay and four access); all 84 tooling checks pass. The full lesson walkthrough passes 605 assertions, actual JVM recovery passes 52, and the ten-node Beskar event passes 43. Tests cover all eleven furnace recipes, nested sheet migration, offline backup/locking/idempotence and deserialization of migrated Paper inventory bytes.

A read-only production audit found 60 Agrinium sheets in two of eight player files. These are queued for one-for-one conversion during the stopped production cutover; production data has not been changed. The development sweep finds no remaining sheets. Promotion must run a fresh offline sweep against the stopped server's latest data, not upload the audit copies.

Development was stopped cleanly and fully backed up before deploying the final tested gameplay JAR and seven merged configurations. The stopped playerdata sweep scanned one file and found no sheets to convert. Normal startup loads 268 custom items and the tutorial without errors; all fourteen other binaries and the pinned resource pack remain unchanged. No probe plugin is installed on shared development.

## Shop playtest follow-up
The owner accepted the other sections and requested a centered stair approach plus a directional arrow farther inside the shop. The route now follows the middle of the four-block-wide stair flight with half-block heights, then turns through the doorway. A three-block particle arrow points inward at the endpoint and stays visible after entering, until selling completes. Both trail and arrow remain private.

Tutorial selling accepts any nearby public Batuu gray Jawa through the normal salvage tray. Previously a fixed shop rectangle rejected the buyer near the entrance even though normal trading worked; that rectangle is now used only for the interior instruction. Normal distance, entity identity and selling-step checks remain.

Verification: 342 Java tests and 342 focused native shop assertions pass, including sampled route clearance, the interior-only arrow packet check, observer privacy, all seven gray buyers and successful lesson sale. The full tutorial was already owner-accepted apart from these shop refinements.

The follow-up is deployed to development after a full stopped-runtime backup. Startup is healthy with the exact tested JAR and one merged tutorial configuration. Other binaries, pack and production are unchanged. Next action: client-check the revised Shops lesson.
