# Design the game from the player's perspective
Status: working design, not a finalized feature order.

## Confirmed foundation
Players use Java Minecraft and a required resource pack. They form player groups, build in claimable wilderness on Tatooine and Hoth, and encounter PvP outside designated safe areas. Death drops carried items, weapons and armor. KOTH and Jabba are proven activities. A base can be vulnerable, including offline, to a rival group that has earned the required pairwise net-kill lead. Force powers and resource planets are deferred.

## Player-facing group identity
Clans is the accepted name. `/c map` is the first native command, with `/clan map` and `/cl map` aliases.

The user replaced the FactionsUUID direction on 2026-11-10: remove the vendor and implement clans in MandoMC. Native membership, four-player clans, four-chunk claims, clan homes, group-aware respawn, pairwise raiding and gradual land restoration are deployed and ready for client playtesting; see [native clans](features/native-clans.md). Mandalore's single spawn outpost is safezone, central caves and approaches are warzone, and the remaining land/builds are wilderness. All of Batuu is safezone. MandoMC now enforces zones natively, replacing WorldGuard. Entering a different zone shows a title: orange Safezone, dark red Warzone or gray Wilderness.

## Confirmed first session and recovery
New players arrive in SpawnWorld, an existing map file the user already has. The arrival slice uses a short replayable chat tutorial, shown after the required pack loads. Batuu stays the protected spawn hub. Players use /warp to visit Tatooine or Hoth. Each planet will have safe zones, warzones and claimable wilderness; the user will supply the boundaries. The standalone Survival/Earth route is retired.

A solo player can create a group and claim a starter base on eligible land outside the spawn safe zone, without a credit requirement. A group creation cooldown of around three hours was suggested tentatively; neither the cooldown nor its duration is decided. Clans now have at most four players and four claims, with one full intervening chunk between rivals or static safezones/warzones, including diagonals.

Death loses only what the player carries. Respawn priority is group home, then a valid bed, then SpawnWorld. If no valid group home or bed is available, use SpawnWorld. A player with stored group supplies can re-equip from that stockpile. Otherwise they repeat the same acquisition process used to obtain the gear originally; there is no separate replacement mechanic. Voluntary return travel and specific recipes/prices remain open.

Combat blocks voluntary teleporting. Logging out while in combat causes death, with the normal carried-item loss. An uncancelled positive-damage player/mob or player/player hit refreshes a 15-second tag for involved players. Show the remaining time to one decimal on the scoreboard. Clean server shutdowns are exempt from logout punishment.

The first join grants a stone pickaxe, axe, shovel and hoe, 64 steak, four Desert Robes pieces, an E-11 and 64 standard plasma cells. Equipment is granted only once, not on subsequent joins or deaths. Starting credits are possible but neither their inclusion nor amount is decided.

The first friends playtest should bring the in-scope gameplay systems together in a rough, integrated version. The development slices below are incremental building and checking steps toward that target. The precise content depth for each system still needs agreement; force powers and resource planets are excluded for now.

## Proposed everyday loop
Establish a base, gather supplies, prepare equipment, venture out, fight or complete an activity, return with loot, and fund the next expedition. Building gives the group a home and stockpile. Equipment acquisition is crafting-first. NPC equipment shops and resource-selling shops are excluded. Existing credits/balances/payments remain; player-to-player auction-house trading is planned, with its rules and currency integration undecided. Specific unapproved recipes and event rewards still need decisions.

Resource planets are deferred. The initial supply loop must work with ordinary survival resources and in-scope activities. Revisit special resource planets only when the user brings them back into scope.

## Design order
1. Describe the experience: what the player sees, chooses, risks, earns and does next.
2. Set rules and failure cases: death, disconnect, restart, abuse, safe zones and other players.
3. Choose the smallest playable version and the dependencies it needs.
4. Define the parameters used by that version.
5. Implement the system, then add and balance content through configuration.
6. Play it before expanding the feature.

Use [the feature brief](templates/feature.md). Record unresolved gameplay choices as unresolved; infrastructure work must not silently decide them.

## Proposed development slices
The [build order](build-order.md) proposes: arrival/protection → combat → KOTH → Jabba → vehicles → crafting/progression → remaining equipment recipes → later raids (clan integration deferred). These are small internal checkpoints toward the integrated friends playtest, not separate launch promises. Both KOTH and Jabba remain in scope.

Record pairwise kills when combat lands; enable explosive raiding only after claim and damage permissions have been verified. Begin with the [arrival/protection brief](features/arrival-and-protection.md). The order is an advisor recommendation awaiting review, not a new accepted gameplay decision.

## Next player decisions
- Review the consolidated gameplay loop and proposed implementation order.
- Settle the public group name and travel proposal before implementing those interfaces.

Clans naming is accepted; clan integration is deferred. Travel beyond the implemented spawn/survival routes remains open. Combat tagging and initial equipment quantities are defined in the combat-basics brief.

## Vehicle baseline
Aircraft carry players in the first integrated playtest; recovered built-in cannons use their configured ammunition. Speeders, aircraft, blasters and ammunition are part of the current all-equipment crafting direction. Preserve stable item identities and existing ownership as recipes land; do not add an initial purchase phase.

Only the player who placed a vehicle can pilot it. Membership in the same group does not grant pilot access. Anyone, including an opposing player, may use an available passenger seat. The U-wing currently has one open-access gunner seat; it is the only role allowed to fire held blasters. Its actual passenger seats will be added later. Pilots and passengers cannot fire held blasters, and menus hide seat roles absent from the vehicle.

Outside protected areas, enemies may destroy vehicles but cannot steal them by taking the pilot seat. Destruction permanently removes the vehicle; the player must obtain another. The placing player can pack a vehicle back into an inventory item only when it is stationary, empty and out of combat. Packing must not repair or duplicate it for free. Damage permission, piloting and other seat access are separate rules; do not infer one from another.

## Accepted acquisition loop
Gather ordinary survival materials and craft equipment, including armor, blasters, ammunition and vehicles. Existing event components/loot remain alternate acquisition routes where implemented. Finished lightsaber recipes remain deferred until that implementation is ready.

The user revised acquisition on 2026-11-09: all equipment should have recipes. Crafting also supplies the normal route for replacing lost equipment. Remaining recipes, equipment variants, drop rates and grind duration require approval. Resource planets are not required.

No NPC/server equipment shops or resource-selling shops are planned. A player-to-player auction house replaces shops as future trading; rules, fees, listing duration, implementation choice and currency details are undecided. Existing credits, ranks and payment commands remain.

## Saber baseline and equipment power
The user uses shield-based models for lightsabers, taking advantage of shield blocking. Their earlier implementation included reflection and saber throwing. Recover that approach as the starting point; a new timed-parry requirement has not been accepted. Separate Force powers remain deferred.

The intended progression has a large power difference between basic and top-tier gear. Exact damage, protection, availability and time-to-kill targets are undecided. This is a deliberate power progression, not a mostly-sidegrade equipment model.

Keep a configurable guard/overheat limit so sustained fire can temporarily overwhelm saber defense. Ordinary Minecraft swords, bows, crossbows and shields are removed from the intended gameplay. Recognized custom sabers remain allowed despite using the shield material. Star Wars-themed swords and bows may be added later; they are not in the current scope.

Updated 2026-11-08: enchanting, potions, elytras, Nether/End portals and the Nether/End dimensions are disabled. Normal anvil repairs and repair crafting remain available; no separate repair system. Avoid accidentally making the guard limit disappear through an item's enchantment or repair behavior; separating guard state from permanent item wear is a recommendation to evaluate during implementation.

Source inspection of `bilal/legacy-lightsabers` confirms:
- Saber recognition requires `SHIELD` material and a saber tag; saber definitions include models, recipes and combat values.
- [The deflection listener](https://github.com/AstuteFlamez/MandoMC/blob/bilal/legacy-lightsabers/recovery/lightsabers/src/main/java/net/mandomc/gameplay/lightsaber/listener/SaberDeflectListener.java) redirects projectiles toward the blocking player's facing direction. The listener itself has no timed-parry or incoming-angle check.
- [The throw listener](https://github.com/AstuteFlamez/MandoMC/blob/bilal/legacy-lightsabers/recovery/lightsabers/src/main/java/net/mandomc/gameplay/lightsaber/listener/SaberThrowListener.java) launches on sneak plus left-click, removes the held saber, and implements a returning throw with configurable damage/cooldown and cleanup paths. Some old item lore describes different controls, so choose one control scheme when porting.
- [The stamina manager](https://github.com/AstuteFlamez/MandoMC/blob/bilal/legacy-lightsabers/recovery/lightsabers/src/main/java/net/mandomc/gameplay/lightsaber/SaberStaminaManager.java) consumes shield durability on deflection, then resets it and temporarily disables shielding on overheat. The user has accepted retaining a configurable guard limit; the old values and durability implementation are reference material.

These are source observations, not new-server runtime verification. Porting must validate current weapon hooks, reflected-hit attribution, directional blocking, region/group protection, and item ownership across death or disconnect. Legacy values are reference material, not accepted balance settings.

## Accepted event structure
Jabba is one shared dungeon with PvP competition, opened through a server-wide schedule. Chest and boss loot can be taken or stolen by anyone; it is not reserved to a contributing player or group. The legacy source contains a room/keycard progression, bosses and randomized loot chests to recover selectively. Current maps and MythicMobs definitions still need restoration; this inspection does not establish runtime compatibility.

KOTH capture progress and victory belong to a player group; an unaffiliated solo player can compete as their own contestant. Players who die during either event may re-equip and return as often as they can while it remains active. Normal full-loot death and respawn priority apply.

The user delegated the KOTH rules to a conventional model. Selected baseline: continuous capture, adapted to groups. The first eligible contestant to take control owns the timer. It continues while at least one member remains alive and eligible inside; it resets when that contestant is entirely cleared out. Enemy presence alone does not pause or reset the timer: opponents must kill or push the holders out. Each fresh capture starts from zero, with no accumulated score. Start with a configurable three-minute timer. KOTH awards one saved KOTH Crate Key to a random eligible winning clan member currently inside the hill, announcing both clan and recipient publicly. It uses the shared Kyber crate engine with a material reward pool.

This differs from the recovered `KothActiveTask`, which resets immediately when multiple contestants occupy the zone. Change that rule deliberately during porting. Published precedents checked 2026-11-05: [AxKoth capture mode](https://docs.artillex-studios.com/axkoth-capture.html) uses uninterrupted occupancy and a three-minute default; [Netherite's KOTH guide](https://netherite.gg/wiki/lifesteal/combat/koth) describes losing capture on leaving, death or disconnect. The group adaptation and starting duration are advisor-selected under the user's delegation; these examples do not establish a measured majority of servers.

## Warpoints and base vulnerability
Warpoints are specific to each pair of opposing groups, not a global group power pool. Each pair starts at zero. A kill gives the killer's group +1 against the victim's group and the victim's group -1 against the killer's group. Those are two views of one kill, not a two-point change. No score against an unrelated group changes.

`score(A, B) = kills by A against B - kills by B against A`; `score(B, A) = -score(A, B)`.

A configurable positive net-kill threshold unlocks A's permission to raid B. Only the group that earned that advantage may breach the rival's base; it does not make the base globally vulnerable. Killing that same rival's members recovers the losing side's score. Passive time-based recovery is not part of the selected model. The accepted threshold is +10 (2026-11-10).

Event PvP kills/deaths at KOTH and Jabba count toward the corresponding rivalry. Losses therefore risk both carried equipment and the survival base. Raid eligibility can persist while the defending group is offline.

Eligible attackers may breach using approved explosives and loot accessible chests. Defenders retain land ownership. This does not permit takeover, unclaiming or unrestricted hand-breaking. Approved explosive types and block-damage limits remain configurable design work. Safe spawn/adventure regions remain protected.

The first successful explosive breach starts a fixed one-hour raid. That pair's warpoints immediately reset to zero and stop changing for the hour, including at KOTH/Jabba. Other rivalries keep scoring. Additional attacks cannot extend the hour. When it expires, ten fresh net kills are needed for another raid. This supersedes the old continuation-window proposal.

An unrelated group may enter through an existing breach and pick up dropped items, but cannot break blocks or open the defender's chests. An eligible raider's permissions do not transfer to other groups. This base protection rule is distinct from Jabba's open chest/boss loot.

Clanmates cannot damage one another. Seven days with nobody online starts an irreversible 24-hour public-loot announcement/window; then the clan disbands, claims become wilderness and blocks gradually return to their pre-edit baseline. Wilderness resets 24 hours after the latest Survival edit in its chunk, with snapshots preserving imported terrain. See [native clans](features/native-clans.md) for permissions, timers and restoration limits.

## Public world travel
The legacy warp/menu system is adapted for exactly Batuu / Black Spire Outpost (SpawnWorld), Earth (the existing survival world) and Hoth. `/warp` (`/warps`, `/travel`) opens the menu; `/warp batuu|earth|hoth` selects directly. `/spawn` and temporary `/survival` retain the same shared three-second stationary warmup. Movement, damage, mounts and combat prevent travel. All public arrivals are safe and protected, using MandoMC worlds.yml as their exact arrival authority. Other legacy planets are excluded.

The recovered Hoth map is protected against edits/explosions, with the old warzone's PvP boundary and a safe arrival. KOTH/events use the recovered hourly framework. Native clan capture and a random present-member KOTH crate key are implemented; the accepted three-minute continuous hold and last-member-removal reset apply. Shared-server deployment completed on 2026-11-10. The event menu shows current/next/selection chances and routes travel through the same service. See [KOTH and warps](features/koth-and-warps.md).

Clan-home travel is implemented through the shared travel policy; themed terminals remain deferred. Personal homes and death-return were approved on 2026-11-09 and use the shared public travel policy.

## Survival mob content
Use existing Tusken Raider, bantha, dewback and massiff models in Desert, Badlands and Eroded Badlands in SurvivalWorld. Beaches and Wooded Badlands remain unchanged. Random encounter levels are 1–5; group members remain within one level of the leader. No distance-from-spawn or player-dependent scaling.

Tusken patrols have 2–4 ranged/melee raiders, 0–2 banthas and 0–2 massiffs, with six total at most. Separate wildlife groups contain 1–3 of exactly one species: bantha, dewback or massiff. Natural vanilla spawns are replaced within the selected biomes, preserving player breeding/spawners and existing animals. Custom wildlife cannot use vanilla pig feeding, saddling, breeding or mounting. Banthas/dewbacks wander; Tuskens/massiffs are hostile.

The first cycler-shooting Tusken and bounded spawning are implemented. Playtest density/combat before the drop pass: cycler rifle, bantha hide and species-specific materials. Rates, quantities and crafting uses are undecided. Saber blocking/reflection spends regenerating durability at 5× incoming damage. Throws require and spend 75% over one second. Zero charge stuns all saber actions; recharge starts after five seconds without charge use or positive damage dealt/taken, then restores 10% per second. Exhausted actions say “You are too tired to …”. Balance and rendered feedback can now be tested against ranged mobs. See [the mob brief](features/sandy-biome-mobs.md) for parameters and verification.

## Tatooine test access (2026-11-09)
The user requested importing legacy Tatooine before implementing Boba, to test flight inside his actual dungeon room. `/warp tatooine` is now an additional combat-enabled adventure destination with map protection and the shared travel countdown/combat gate. Dungeon scheduling and encounter placement remain deferred. The arrival is now the user-selected palace entrance, preserving precise position and camera angles. Legacy Boba and his EE-3 are restored for manual room testing before boss refinement.

## Jabba operation flow (2026-11-09)
The latest user decision supersedes earlier chest-gated proposals: spawn every cache at event start, open each next door on the final owned room enemy's death, and remove remaining caches at event end. Caches use legacy one-click randomized loot and disappear when claimed. After Rancor dies the palace stays open until the scheduled end. Nearby palace players receive `Room [x] unlocked » blast doors opening`. Native MandoMC gates replace BigDoors.

## Accepted progression follow-up (2026-11-09)
The user delegated finished-saber recipes and a conventional player auction house, then required the auction implementation inside MandoMC with no additional plugin. Native `/ah` uses existing credits, eight 48-hour fixed-price listings, no fees/tax, confirmation and permanent item claims. All themed ores yield catalog scraps before ingot refining. Four-metal storage has visible reciprocal 9:1 conversions; iron utility recipes use Agrinium Sheets. Blaster/ammo recipes and final-world generation are explicitly deferred. These decisions supersede the earlier undecided auction/saber holds above. See the ordered progression queue for verification and rollout state.

## Deferred planet content
See [planet wilderness direction](features/planet-wilderness.md) for Hoth creatures, Mandalore, the beskar ore event and possible trooper enemies. All are deferred.
