# Progression and crafting
Status: deployed; client playtest pending.
Branch: bilal/durasteel-progression

## Player experience
Mine themed metals into catalog scraps, refine ingots, make sheets/components, and craft equipment and vehicles. Native tools, Fortune, Silk Touch, XP, protection and repairs remain authoritative. `/recipes` explains actual acquisition/workstations and reciprocal storage. Player trading is inside MandoMC through `/ah`; no additional plugin or NPC equipment/resource shop is introduced.

## Ordered queue
Requests retain arrival order; corrections supersede their original item. Checked entries have implementation and relevant verification. Gameplay and the latest shading pack are deployed; client playtesting remains.

1. [x] Durasteel: diamond/deepslate-diamond mining -> durasteel_scrap, native counts/tools/XP/Silk/Creative/protection preserved.
2. [x] Disable vanilla armor crafting and smithing creation; preserve custom armor and normal repairs, including automated crafting.
3. [x] Plastoid: shapeless two clay-ball slots + ordinary sand + coal -> 2.
4. [x] Finished-saber recipe hold superseded by the user's delegated choice. Seven assemblies and 71 style recipes cover all 78 variants; see [saber crafting](lightsaber-crafting.md).
5. [x] Speeder Bike chassis/repulsor, U-wing cockpit/wing components and final vehicle recipes.
6. [x] TIE guide pattern one row down; native matching/cost unchanged.
7. [x] Agrinium iron presentation; six Agrinium in A A / A A / A A -> Barrel Liner. Mining correction in item 32 supersedes raw-metal drops.
8. [x] Electrum replaces REDSTONE, superseding the earlier copper prototype. Mining correction in item 32 supersedes direct-ingot drops. Native redstone circuitry/storage behavior remains beneath item presentation.
9. [x] Alloy Plating: three Agrinium in one horizontal row -> 1. Nine ingots remain storage packing.
10. [x] Energy Conduits E/P/E -> 2; Fusion Coil Stabilizer four Durasteel around Wiring Coil -> 1; Plasma Capacitor Array three Electrum and two coils -> 1.
11. [x] Remove Jabba references from item lore/source descriptions; preserve event names and actual loot.
12. [x] Electrum Sheet row of three -> 1; Transparisteel Agrinium cross around glass -> 4; Circuit Board WW/EP -> 1; Lens Electrum cross around shard -> 1. Old one-ingot sheet shortcut removed.
13. [x] Materials category slot 11, between All (10) and Armor (12), includes components/metals/materials.
14. [x] Five one-output hilts: Straight D/P/D, Great Durasteel cross around Straight, Whip Electrum above Straight with Durasteel right, Pike D/D/H, Fang AHA.
15. [x] Remove Ilum crystal lore; later delegated synthetic crystal recipes are described honestly as new survival recipes.
16. [x] Cloned guide previews end with blank line + yellow nonitalic “Click to see how to obtain.” Real templates/outputs do not gain UI hints.
17. [x] Hide unused aurodium/cortosis/lommite/mandalorian_iron/neuranium/phrik/songsteel families and rhydonium fuel results from the guide. Keep IDs, assets, inventories, loot and existing native legacy registrations. Used Beskar routes remain.
18. [x] Agrinium pickaxe/shovel only, native iron bases, approved sheet/stick shapes and head-only recolors; preserve handles/alpha.
19. [ ] DEFERRED BY USER: concrete standard/Tibanna/proton ammo recipes. Existing identities remain; no guessed ingredients.
20. [x] U-wing and speeder use genuine standard plasma cells; TIE Tibanna; X-wing proton. Preserve damage, cooldowns, repairs and terrain policy.
21. [ ] DEFERRED BY USER: final-world generation. Later exclude gold/copper/lapis/emerald ores and all deepslate/biome/vein variants; retain coal, Agrinium iron, Durasteel diamond and Electrum redstone. No current map stripping or generation migration.
22. [x] Original legacy animated green laser for Tibanna weapons; standard/proton visuals unchanged. Original bytes/provenance and real cannon display/axis/ammo behavior verified.
23. [x] Crafting-first acquisition roadmap; exclude NPC/server equipment shops and resource-selling shops. Preserve credits/ranks/payments.
24. [ ] DEFERRED BY USER: remaining blaster recipes.
25. [x] Native auction house in MandoMC, with one credit/escrow authority and no new plugin. Fixed-price, eight listings, 48 hours, zero fees/tax, permanent claims and confirmation/recovery; see [auction house](auction-house.md).
26. [x] Global natural-spawn blacklist: SLIME, HUSK, RABBIT, CAMEL, CAMEL_HUSK, PARCHED. Preserve custom encounters, existing creatures, breeding, spawner, admin and custom reasons.
27. [x] Creative encounter eligibility alongside Survival/Adventure; Spectator remains excluded and Creative combat immunity remains native. Independent scheduler verified with vanilla spawning disabled.
28. [x] Patrol frequency and one active patrol/player: 3x free-patrol opportunity, unchanged wildlife probability and interval/chance/attempt overrides. Whole 2–4-raider group plus companions, levels, biomes, distance, protection and population caps remain. Real native target/side-effect guards prevent cross-patrol pileup; defeat/despawn/disengagement/player lifecycle release claims. 29 isolated assertions and paired 120-trial counts passed: patrol 35 -> 88, wildlife 84 -> 84. This was excluded from the first deployment and is included only in the verified follow-up.
29. [x] Combat HUD labels use regular-weight small caps like the normal HUD; preserve layout and timing.
30. [x] Twenty-six native iron utility recipes now use exact Agrinium Sheets instead of ingots/nuggets: doors/trapdoors/bars/chains, pressure plate, bucket/cauldron/hopper/anvil, rails/minecart, compass/crafter/piston, shears/igniter, lanterns/lodestone/saddle and workstations. Native shapes/counts and wood-tag alternatives remain; native old shortcuts are displaced and restored on close. Native output names become Agrinium equivalents through the pack; placement/container/durability identity remains native. Anvil's storage-block top row remains blocks; nine-ingot storage is preserved.
31. [x] Electrum storage face redesigned as a pale metal plate using built-in imagegen. Ores now use four distinct pinned Faithful mineral layouts: Agrinium iron, Durasteel diamond, Electrum gold, Beskar emerald; both stone/deepslate aliases updated, rock style and palettes preserved; the latest shading floor in item 36 also lifts selected dark rock/outline pixels. Asset/hash/source checks pass. See [art source and prompt](../../resource-pack/art-sources/README.md).
32. [x] All normal themed ore mining -> matching catalog scraps. Electrum/redstone -> electrum_scrap; Agrinium/iron -> agrinium_scrap; Nether Gold and Ancient Debris Beskar aliases -> beskar_scrap. Debris self-drop is replaced only without actual Silk Touch. Native gold-backed ore accepts any pickaxe; debris-backed ore needs diamond/netherite. Native quantities/Fortune/XP/cancellation/Creative remain. Scrap-to-ingot acquisition restored in guide; legacy raw materials/smelting retained and no longer advertised as current ore drops.
33. [x] Verified: fourteen themed ore/storage/raw item forms have standard rarity + Materials badge, spacing and gray nonitalic functional lore. Native drop/pickup/conversion canonicalization restores identity without overwriting named/third-party stacks. Native placement and one-item consumption passed for all four storage blocks.
34. [x] Verified: explicit four-metal ingot <-> block recipes (9:1), plus separate legacy raw Agrinium <-> raw block (9:1), appear in the guide. Ore/raw blocks never unpack into ingots. Direct native crafting/counts/guide navigation pass; actual native shift-click packing/unpacking passed for all four metals.
35. [x] Verified: `/mmc give` accepts a trailing 1–2304 quantity in self/target forms, defaults to one, independently generates correctly sized catalog/vendor stacks and refuses the entire grant if storage is full. Native self/target/console/ammo, bad count, stack splitting, whole-grant full-inventory rejection and old default syntax checks pass.

## Identity and acquisition
Agrinium ingots use IRON_INGOT; Electrum ingots use REDSTONE; Durasteel and Beskar retain existing catalog bases. Scraps use their existing stable catalog identities. Native-backed ingots retain ordinary backing-material uses; custom progression recipes require exact catalog metadata. Legacy plain ingots/dust and former catalog ingot bases normalize one-for-one on inventory access/join. Named/third-party stacks are not silently accepted.

`OreDrops` replaces only native item entities after successful mining. Iron retains native 1/Fortune up-to-4 quantities; redstone retains 4–5/Fortune up-to-8; Nether Gold retains 2–6/Fortune up-to-24; Ancient Debris remains one. These are native bounds; individual trial samples vary. Actual Silk Touch keeps catalog-presented ore blocks. Tool state is captured before durability can break an Ancient Debris mining tool, distinguishing its ordinary self-drop from Silk Touch. No second loot or XP path is created.

Crafting registration, displaced vanilla keys and recipe-guide definitions have one owner. Closing restores displaced native recipes. Ordinary native utility outputs keep their native material/components; only the selected iron utility ingredient choices and client names change. Storage forms use exact catalog identities, with native block placement/drop/pickup restoration.

## Spawn boundary and patrol ownership
The pinned biome implementation lists Rabbit/Camel/Husk/Parched and Husk can initialize Camel Husk/rider companions. The early natural-spawn guard aborts those candidates before companions initialize. Modern CHUNK_GEN no longer fires; new-chunk-only initial population handling skips existing chunks, explicit spawn reasons, names, PDC and scoreboard tags. Custom Mythic hosts remain allowed.

`PatrolState` is the sole association writer. Every member has a group ID; each eligible player reserves at most one active group. Nearby players can share a group, while independent players can have separate patrols. Hostile defeat/removal, disengagement after 30 seconds and logout/death/world/mode lifecycle release claims without deleting other players' groups. Native group-kind PDC reconstructs membership after loading. Mythic may cancel native target requests after changing a host target; the ownership guard inspects already-cancelled side effects too. Existing Mythic target selection remains authoritative within a permitted group.

Live overrides retain one opportunity every 10 seconds, chance 1 and 32 attempts. Free patrol probability is 0.25 -> 0.75; independent wildlife probability remains 0.75. Shipped defaults retain 20 seconds/chance 0.25/eight attempts with the same 3x patrol multiplier. Busy patrols, protection, placement and population checks still suppress creation. This is an opportunity increase, not a promise of production-world encounters per minute.

## Verification and rollout
First release: deployed at 20:35 on 2026-11-09, matching verified published pack, stopped runtime backup and KOTH restoration. Frozen source commit was local; automatic approval review rejected full-source publication, so authorized pack-only publication was used.

Follow-up: 166 Java checks and 32 current script checks pass. Isolated Paper passed 29 patrol assertions and 33 native auction/quantity assertions. Expanded mining, utility, saber, storage and tooltip pass reached 1,721 assertions; all final native shift/placement and give-quantity additions pass. Prior 140 real cannon and 11 independent periodic Creative assertions remain relevant; preserved class/resource entries are compared against the tested deployed baseline. No test plugin is installed live.

The combined gameplay candidate was deployed at 22:00 with a full stopped backup and the verified matching pack. Latest shading pack `resource-pack-26.1.2-bc1d5c9d474a` is anonymously verified and active; server running and KOTH restored. Next action: reconnect for the pack and client-playtest recipes, ore shading and `/ah`. Blaster/ammo recipes and final generation remain deferred by explicit user instruction.

36. [x] Verified latest user art correction: deepslate themed ores and stone Electrum (`redstone_ore`) must have no black painted shading. Lift dark mineral recesses and neutral outlines/rock to visible gray tone floors (80 deepslate, 96 Electrum stone), preserve ore geometry/palettes/alpha and all unrelated textures. Rebuilt, verified, published and applied the asset-only pack. All progression classes and the concurrent travel update are preserved; the missed KOTH round was restored.
