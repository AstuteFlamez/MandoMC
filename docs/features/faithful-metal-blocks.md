# Feature: Faithful base and metal block preview
Status: first visual pass, pending client preference feedback
Branch: bilal/legacy-armor-sets

## Player experience
The required pack order is MandoMC at the top, JustTimm's Lightsaber Workshop in the middle, and Faithful 32x at the bottom. The base and lightsaber packs are unchanged official Modrinth CDN downloads, separately pinned by UUID and hash. MandoMC supplies the overrides.

Eight older-style scrap sprites now use the accepted modern Durasteel scrap silhouette with each metal's original palette. The sixteen upgraded ingots/sheets keep their prior pixels. The nine already-modern Beskar/Durasteel/Electrum item textures remain unchanged. Originals and deterministic regeneration are retained under resource-pack/artwork/metals and scripts/recolor-metal-textures.py.

## Block-only mappings
- Diamond block becomes Durasteel Block.
- Diamond ore becomes stone Durasteel Ore.
- Deepslate diamond ore becomes deepslate Durasteel Ore.
- Netherite block becomes Beskar Block.
- Nether gold ore becomes stone Beskar Ore, at the user's explicit choice.
- Ancient debris side/top become deepslate Beskar Ore, at the user's explicit choice.

Faithful's stone and deepslate backgrounds are preserved byte-for-byte: only cyan ore pixels are recolored using the existing Durasteel/Beskar item palettes. Storage blocks use Faithful's diamond/netherite layouts with the metal palettes. Netherite armor, tools and other items are not recolored as Beskar. This pass changes block art and six English display names only; native IDs, drops, world generation, mining, damage/protection and acquisition remain unchanged. Further block families await user feedback. Preview artwork is resource-pack/artwork/metals/block-preview.png.

## Ownership and parameters
SaberPack owns one required replacement request containing Faithful, JustTimm, then MandoMC in client insertion order. This clears the previous server ordering and inserts MandoMC last/highest. Join and resource-status paths deduplicate per session; quit releases the session marker. The current main URL/hash/UUID comes from the real Paper server-pack configuration; upstream pins are in combat/sabers.yml upstream-pack and upstream-pack.base-pack. No separate hardcoded main release can go stale.

Faithful pin: official project faithful-32x, Modrinth version Fj5GKJnf, 26.1-june-2026 for Minecraft 26.1/26.1.1/26.1.2; SHA-1 9447e84ee7121c86304e467a0f8d5b095872e1e6. UUID c7431f86-b798-586f-819f-e863f0c9e1e9. JustTimm's 3.2.2 URL/hash are unchanged. Exact official metadata and provenance are in dependencies.json and metal-block-sources.json. The unchanged Faithful license is distributed as faithful-license.txt and specific source attribution/website are in credits.txt.

## Verification
137 Java tests and 26 pack/script tests pass. A real isolated Paper instance with the candidate JAR passed 13 pack-stack API assertions, including order, required replacement, official URLs/hashes, distinct UUIDs, duplicate requests, reconnect and invalid-pin rejection. The candidate preserves every entry in the current deployed JAR except SaberPack.class and its bundled combat/sabers.yml resource; the live external configuration receives only an additive Faithful pin. This retains the latest unrelated saber/HUD/vehicle changes. All three original palette-transfer forms reproduce exactly; ore-mask assertions prove their rock backgrounds are unchanged.

## Next action
Apply the verified pack/JAR, verify the actual client's final three-pack order, and review the six block candidates in game before extending other blocks.

## Deployment verification
Published and anonymously verified resource-pack-26.1.2-d772599bf5de (1064 entries, SHA-1 0245e2984db2875b4aa2ad7a0b12aee6ad80896c). Clean deployment at 18:02 retained all unrelated gameplay classes; JAR SHA-256 2be26b956dd9f449e906408ea011053a3f27cdd731293f1a66582b8bc64dd4e7. At 18:03 the actual client accepted, downloaded and successfully loaded all three packs. Its resource manager lists bottom-to-top Faithful UUID c7431f86-b798-586f-819f-e863f0c9e1e9, JustTimm UUID 20a340e6-9abc-481d-8878-312e26ca08e8, MandoMC UUID a3feb494-6a5c-5495-8f33-efdd5783633f; no MandoMC missing-resource warnings remain. No probes are deployed. Next action: user reviews the six metal block candidates in game before extending other block families.
