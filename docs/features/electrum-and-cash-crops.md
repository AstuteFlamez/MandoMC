# Electrum, redstone and Jawa sales

Branch: bilal/nov18-gameplay-feedback

## Player experience

Mine Electrum Ore with an iron or better pickaxe for one Electrum Dust. Use dust at an enchanting table, or smelt it one-for-one into ingots for equipment and storage recipes. The resource pack updates the enchanting slot, cost text and Electrum palette. General Supplies on Batuu sells vanilla Redstone Dust. Redstone circuits use ordinary protection rules.

Batuu's Plants & Dyes Jawa sells bamboo. The Farming Jawa sells cactus and sugar cane. Sell these crops at any gray Jawa for 20, 28 and 24 credits per 16, respectively. Other existing gray Jawa sell rates rose by 25%. Each crop costs 6 credits to buy, so reselling purchases alone loses credits.

Inventory views display an item rarity and category, including vanilla blocks. The server still uses original item metadata and the same recipes, stack comparisons and shop quotes. Voting and KOTH crate previews retain the configured chance on enchanted book tooltips.

## Rules

Tagged former Electrum Scrap, ingot and storage items migrate by quantity when inventories are opened, loaded or picked up. Native redstone dust and blocks retain their own recipes. Previously placed redstone blocks use their native behavior. New placed Electrum storage is tracked independently of waxed copper for mining and compaction. Placed waxed copper shares the Electrum storage texture with the current pack. Vanilla lapis has its ordinary uses outside the enchanting table.

Staff mode retains its saved inventory, location and player state. Temporary workstations such as crafting tables, anvils and enchanting tables work in staff mode. Staff can inspect accessible storage while item transfers are blocked. Builder staff in Creative can make direct block edits in protected territory. Persistent economy actions keep their existing staff restrictions.

## Rollout

Deploy the updated managed item catalog and definitions, recipe config, crate pools, Jawa stock and rates, and staff rank config along with the plugin. Use the matching resource pack and replace the compatibility launcher and redstone agent together. The previous release's redstone Java agent changes native behavior before Paper loads and cannot be reused.
