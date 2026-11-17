# Feature: development playtest corrections
Status: verified; development activation pending
Branch: bilal/dev-feedback-fixes

## Player experience
Placing a Rancor Skull names the skull even when the player has already placed helmets or signs. Ilum checkpoint 3 retries on fall damage; suffocation is harmless and never moves the runner. Advancement progress remains available without chat announcements. Voting notices use the server brand, gray body and highlighted values. The vanilla recipe book unlocks custom recipes on join, including delayed vendor recipes. `/recipes` separates Lightsabers and Weapons; all 78 saber recipes group by blue, green, red, purple, yellow, orange and white Kyber, with crafted bases before their styles. Tutorial guidance opens Lightsabers.

Normal enchanting tables and enchanted-book anvils work. Bantha Hide remains leather for native books and leather-armor repairs. Dewbacks retain food and saddle returns but drop no leather/hide; banthas retain hide drops.

The owner's ammo follow-up raises every existing ammo-dropping mob to an expected 10 rounds per death: Quarren Guard, Twi'lek Guard, Boba Fett and Brown Jawa. Each uses 20 independent 50% single-round rolls (0–20, mean 10). Weapon, equipment and salvage rolls retain their existing chances; mobs without ammo drops stay unchanged.

## Rules
The dropper cancels other damage without a retry; the existing below-course Y guard remains as a softlock escape. Other Ilum checkpoints retain normal environmental damage and saved-checkpoint death recovery. PvP protection remains unchanged. Decoration custody still uses the existing journal and captured item, including asynchronous callbacks, recovery and full-inventory messages. Enchanting follows native compatibility, XP and lapis costs. Recipe unlocks suppress bulk recipe toasts and are reapplied on join after restart.

## Ownership and integrations
MandoMC owns crafting registrations/discovery, recipe guide ordering, decoration messages, advancement chat, Ilum rules and mob drop tuning. Paper owns enchanting/repair and advancement progression. MythicMobs supplies the actual death-drop list; DewbackDrops removes only leather before canonicalization, preserving saddles. VotingPlugin retains its listener, storage and reward authority; credentials, history and crate placement remain private. The new managed Config path accepts only the explicit reviewed source file.

## Parameters
`rules/survival.yml` sets `disable-enchanting: false`. Recipe categories are `lightsabers` or `weapons`; the guide derives saber color through the original crafting input. `mobs/drops/jabba.yml` sets `ammo-rolls: 20` and `ammo-chance: 0.5` for the four ammo sources. Changes activate on restart.

## Verification
Java tests cover all 78 saber categories/colors, base/style ordering, source cycles, and expected ammo output plus sampled distribution. A disposable Java 25 / Paper 26.1.2 fixture exercises recipe discovery and native guide navigation, advancement chat, actual enchanting button/XP/lapis, anvil book application, Bantha Hide book crafting/robe repair, herd death drops, mixed decoration messages and Ilum damage movement. All 346 Java tests (342 gameplay plus four access), 82 tooling tests and 4,288 native assertions pass. Three unrelated NBT migration tests are skipped locally because nbtlib is absent; CI installs that dependency. The unchanged resource pack builds to the currently pinned hash. Native vote verification observes exactly 500 credits and one key with the formatted broadcast/reward notice. Native ammo samples cover 1,000 rolls for each of the four sources.

## Pause note
All requested paths implemented and native-verified. Next action: merge the checked dev PR and activate the seven-file scoped release on development.
