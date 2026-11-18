# Native materials and reliable stonecutting

Bantha Hide is the existing `bantha_hide` identity on native LEATHER. Normal leather drops, generated loot, pickup, opened inventories, hopper moves and crafting normalize plain native leather without adding drops or changing counts. Books, item frames and ordinary leather recipes still work. Owned legacy ECHO_SHARD hides migrate with custom names, enchantments, PDC and player lore preserved; third-party or customized unidentified leather is untouched. Jawa supply stock references the same identity. The native leather language key reads Bantha Hide, including in the recipe book.

Material badges follow actual acquisition:

- Common: Plastoid, Alloy Plating and Transparisteel Shard use basic ingredients.
- Uncommon: Energy Conduits, Wiring Coil, Circuit Board, craftable Tibanna Gas Vial, Straight Hilt and the six Durasteel forms require processed/gathered materials.
- Rare: the three previously inconsistent synthetic Kyber colors (red, orange, white) now match the other craftable colors; the three X-wing assemblies match complex craftable ship components.
- Epic: all six Beskar forms reflect contested event acquisition. Reserved or currently unavailable legacy components retain their tiers.

There are 26 changed material IDs. Rarity refresh changes only a recognized badge row on an owned, matching-material item. Other metadata and badge spacing remain intact. Existing named Agrinium/Electrum no longer lose metadata during native-material canonicalization.

The client and server now receive the same filtered, ordered recipe set for the exact current input. Only compatible conversions for an original, undamaged saber appear. Changing saber metadata or switching between shield-based families refreshes both lists, clears the previous result and selection, and retains the existing selection/take guards. Amount-only changes preserve a valid selection for repeated crafting. Vanilla stonecutting remains available, and the complete recipe list is restored when the menu closes. The pinned Paper adapter is isolated in `integrations/paper/StonecutterOptions`.

Planet badges are Mandalore Epic, Ilum Epic and Hoth Uncommon. Ilum messages show rounded tenths with correct minute carry; persisted records and timing remain in milliseconds. The compatibility overlay patches only `ParkourService.format(long)` and verifies every unrelated method's bytecode remains identical. Integrate its `IlumTimeFormat.format(millis)` delegate into the Ilum source owner before a future full rebuild.

Verification: 174 Java tests and 921 isolated Paper assertions, including six saber families, every packet-visible index, actual click packets, stale-result rejection, damaged inputs, full inventories, repeated shifts, vanilla stonecutting, native leather recipes, quantity-preserving migration and metadata safety. The language-only published pack changes exactly one translation value; all other entries match the deployed gray-Jawa pack byte-for-byte and the anonymous download matches both hashes.
