# Jawa traders
Status: implemented; native Paper verification passed, client playtest pending.
Branch: bilal/jawa-traders

## Player experience
Brown Jawas roam Tatooine in neutral groups of 2–4. Each has six independently shuffled offers drawn from the configured offworld supply pool. The selection stays stable for that entity's lifetime and after chunk reloads; another Jawa can carry different stock. Right-click to browse and left-click to buy a bundle. A damaging, uncancelled attack provokes that Jawa's nearby group against the actual attacker. Cancelled hits do not provoke them. Combat closes shops. Brown Jawas retain their original ionization blaster attack, voices and death loot.

Gray Jawas are fixed salvage buyers, immune to damage, with idle looking behavior. Right-click to browse the bundles they buy, then left-click to sell one matching bundle from the inventory. Custom items must match the exact catalog stack; ordinary material trades cannot consume custom gear or metals. Unused menu positions use the shared gray filler. Shift, hotbar, double-click and drag paths cannot extract previews. Trades require Survival/Adventure mode, proximity and no combat tag.

The first gray buyer uses the user's captured Batuu position/facing, stored only in runtime configuration. `/mmc jawa gray <id>` saves the issuing administrator's current position and facing; `/mmc jawa remove <id>` removes it. `/mmc jawa brown` summons a two-Jawa test group ahead of the administrator. `/mmc jawa status` reports service health. Additional fixed locations await the user.

## Economy and recovery
Use the existing CreditService, ledger and serialized writer; preserve existing balances. Accounts start at zero. No starter credits or changes to other reward systems are introduced. `/balance`, `/pay` and administrative `/credits give|take|set` continue to work.

Shop changes extend the existing atomic auction escrow snapshot rather than adding another balance store. A purchase commits its debit and collectible item claim together. Inventory delivery and a saved player-data receipt precede claim acknowledgement. Sales save removed inventory and a recovery receipt together, then commit credit and the accepted transaction ID atomically. Save failures return unaccepted sale escrow; joins and `/ah collect` recover outstanding handoffs. A disconnected buyer's item remains claimable through `/ah collect`. Repeated transaction IDs are rejected. Original purchase prices exceed matching gray buyback prices; buyback deliberately excludes broad gear/component pools.

Initial prices and bundle sizes are configurable starting values, not a final balance target. Brown stock covers saplings, farming starters, dirt, greenery, ocean imports, Nether/End building supplies, utility drops and missing vanilla materials. Gray buys common gathered materials and exact Agrinium/Durasteel stacks. Beskar, event components, enchanted items, potions and elytras are excluded.

## Mobs and placement
Mythic IDs: `JawaBrown` and `JawaGray`. Models: recovered `jawa_brown` and `jawa_black` (the user's gray/dark variant). Original rig geometry, embedded textures and animation keyframes are preserved; local exporter paths are removed, animation override is enabled and attack/death clips finish once. A pig body supports these custom neutral mobs even in peaceful review worlds, and avoids zombie daylight/transformation behavior. Mounting, breeding and vanilla feeding interactions are blocked. Gray anchors use persistent Mythic ownership and native invulnerability, and reattach their model on load.

Brown population checks run every 45 seconds, at most 16 loaded Jawas per configured Tatooine world. Spawn 24–64 blocks from non-spectator players, on loaded desert ground, with clear headroom and within the world border. Use native territory spawning/edit policy and keep new groups separated. Never load chunks just to spawn. Old groups expire after 20 minutes once players are farther than 48 blocks. These are configurable initial density values. Both the legacy public Tatooine and current Tatooine review dimension are configured; no worlds, map boundaries or territory zones are changed.

Before its attack pose, a brown Jawa uses the existing shared hostile-facing skill, tracks its target during wind-up, then fires the original paired shots. The recovered player gun retains 1.0 base damage, 18-cell magazine, 32-tick reload, 8 automatic shots/second and blue projectile. Armor wear is adapted to the supported integer value 1. Original drops use the single Mythic death-loot pipeline: 12% blaster chance plus three independent 50% single-ionized-cell rolls. No duplicate console drops.

## Ownership and files
- `JawaTraders` owns placement, neutral group retaliation, anchored looking and menus.
- `JawaSettings` validates population, offers and prices.
- `JawaMobs` isolates Mythic threat calls; existing `HostileFacing` handles model rotation.
- Mythic owns model attachment, AI and attack scheduling; WeaponMechanics owns gun/ammo identity and projectile damage.
- `mobs/jawas.yml` holds populations, trade content, messages and gray anchors.
- `items/catalog.yml` adds the vendor gun/ammo factories; `mobs/drops/jabba.yml` registers recovered loot with the shared adapter.
- `resource-pack/jawa-sources.json` and ModelEngine `sources.json` record asset provenance.

## Verification
181 Java tests, 31 script tests and 37 native Paper assertions pass. The published Jawa pack downloads anonymously with matching hashes; all previous assets are preserved except additive sounds.json registrations. Run the Java suite, resource-pack validation and script suite. `scripts/build-jawa-probe.sh` builds a temporary native Paper probe for neutral groups, group retaliation, damage cancellation, rig attachment, original loot rolls, actual weapon firing, menu transfer protection, real supply purchase/sale, ledger restart and custom-item rejection. The probe and its three proven test accounts were removed, with real balances preserved, before a clean normal restart. Gray anchors persist across restarts and orphaned removed anchors are cleaned when their chunks load. Client-rendered animation, shop feel and encounter density require the user's playtest.
