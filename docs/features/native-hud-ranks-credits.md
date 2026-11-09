# Native player identity, sidebar and credits

## Player experience
The legacy animated MandoMC tab/header/sidebar returns with the exact original 68 logo frames, 288 separator frames, rotating server links and rank sorting. Tab/chat use the small rank icons; nameplates/sidebar use the full badges. Nameplates preserve existing gameplay-team ownership. The sidebar shows local date, player, ping, rank, kills/deaths and credits. During combat the kills row becomes the authoritative combat deadline to one decimal; it returns on expiry/death. `/sb` toggles the sidebar for this session. Hiding it does not cancel combat.

Warps retain the existing four destination textures. Their descriptions now begin with the legacy Common + Planet badge row for Batuu/Earth/Tatooine and Rare + Planet for Hoth, then a blank line, description and travel hint. Earth takes the legacy common survival-destination rarity.

## Rules and ownership
MandoMC owns ranks/permissions, tab/sidebar presentation and credits. TAB, LuckPerms, Essentials, DeluxeCombat and PlaceholderAPI are not runtime dependencies. CombatService alone owns tags, travel denial and logout death; PlayerHud only reads remaining time. External scoreboard teams retain friendly-fire, collision and visibility settings; detach restores the previous scoreboard and tab settings.

Recovered rank order: mandostudios → mod → builder → creator → mandalore → marshal → warrior → default. Original parent relationships and the two glyphs per rank are preserved. `mandostudios` retains the legacy wildcard grant; Builder retains building/flight/game-mode tools; Moderator retains supported moderation/inventory/vanish/world-travel tools. Existing public MandoMC travel, recipes, events, weapons and credits remain available to Default. Permissions for removed/deferred plugins (including old Ilum contexts, faction features, tempban, economy shops and crates) are not activated. Permanent bans use Minecraft's native permissions. Rank membership never grants operator status. Explicit node rules beat wildcard rules; child ranks override identical inherited nodes.

`/mmc rank list|get <player|UUID>|set|add|remove <player|UUID> <rank>` requires `mandomc.ranks.manage` (operator/MandoStudios). Changes save atomically off the tick thread before taking effect. Rank removal replaces the permission attachment; unknown ranks, cyclic inheritance and malformed saved data fail closed. New players default to Default. Rank definitions restart with the plugin; no partial reload path.

`/balance [player]` (`/bal`, `/money`), `/pay <player|UUID> <amount>` and `/balancetop` (`/baltop`) are public. `/credits give|take|set <player|UUID> <amount>` requires `mandomc.credits.admin`. Accounts start at zero. Integer cents avoid floating-point drift. Payments must be positive with at most two decimals; self-pay, insufficient funds, overflow and invalid input fail without changing either account. The maximum is 90 trillion credits. Serialized atomic file replacement commits both sides together; rejected writes never mutate the displayed balance. Shutdown drains pending saves. Names use exact online/cached matches; UUIDs support offline administration without network lookups. Vault/shop integration, earning rates and event rewards remain future work.

## Persistence and migration
Native UUID assignments live in ignored `data/rank-users.yml`; integer-cent balances live in ignored `data/credits-data.yml`. Defaults never contain player identities. `scripts/import-native-accounts.py` stages new files and refuses to overwrite existing native data. Export LuckPerms players/user-permissions tables from a copied H2 database with CSVWRITE; supply these CSVs and Essentials userdata directories in oldest-to-newest order. It imports explicit, permanent, global group memberships and rejects unresolved contextual/temporary memberships. Cached PRIMARY_GROUP values are not permission grants: stale admin/dev/helper labels are ignored. Editor keys and unrelated permission bypasses are never imported. Source backups remain unchanged. The user subsequently requested a fresh rollout: no imported account records are deployed; all accounts default to Default/zero credits, with only AstuteFlamez explicitly assigned MandoStudios. Newer Essentials snapshots replace older values per UUID; amounts are never summed or rounded.

Pack provenance is in rank-sources.json and planet-sources.json. All sixteen rank textures and the planet label preserve original pixels, glyphs, height and ascent. Ranks use `mandomc:ranks` so ordinary Latin letters are not globally replaced. Existing item badges/planet artwork remain intact.

## Verification
Combined build: 132 Java tests and 19 script tests pass. Pack validates and builds deterministically. Combined vehicle/config integration is included; 51 focused Paper assertions pass, plus 81 combat regressions covering tags, drops, logout punishment and travel denial. Client appearance remains to be checked.

## Pause note
Implementation and account migration are staged on `bilal/native-hud-ranks`. Next action: publish the combined pack and deploy alongside the latest vehicle/config changes.
