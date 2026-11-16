# Feature: development playtest fixes
Status: deployed to development; owner acceptance pending
Branch: bilal/staff-flight-feedback (follow-up to bilal/dev-playtest-fixes)

## Player experience

Staff use `/staff on` to enter Creative with an empty temporary inventory. `/staff off` restores survival inventory and the pre-staff location. Gamemode changes, including `/gms` and `/gamemode survival`, keep staff mode, its temporary inventory and staff permissions active. Normal player rules apply with staff mode off, including to operators. Rank badges remain visible. `/back` requires staff access and active staff mode. Survival inventories, armor, offhand, XP, effects and health are stored in player data before temporary items replace them. Disconnect, normal shutdown and interrupted-session recovery restore survival state. Tutorial/parkour, combat, mounts and pending item transactions cannot overlap staff inventory custody; enabling staff while on a parkour run first exits the course.

Imported operator entitlement is saved privately in player data while native op status is disabled in survival. Turning staff mode on restores that authority. To revoke this imported entitlement use console `staff revokeop <online-player>`; native rank memberships remain separately managed by `/mmc rank`. Do not manually remove the staff inventory marker: it is the recovery record.

Staff can fly/teleport freely around Ilum without entering a ranked run, use `/parkour checkpoint 1` through `4`, bypass travel warmup/combat blocking and native saber, cannon and flamethrower cooldowns. Staff inspection does not award course records or keys. Temporary items do not become the survival loadout; passive crate and auction deliveries wait for the normal inventory.

Ilum permits its original buttons, levers, plates, doors and native circuits. Redstone remains disabled elsewhere. After reaching checkpoint 3, environmental damage and fire do not reset the player; death respawns at that checkpoint and preserves course inventory. Earlier checkpoints retain their reset-on-damage behavior. PvP remains disabled.

Batuu scenery interactions (including trapdoors) are protected. Safezone hunger loss is cancelled. Native mob target acquisition and paths are cleared at safezone boundaries; the Mythic adapter also removes protected targets from threat tables and attack skill snapshots. Combat protections remain in place.

The tutorial adds private directional particles and zone-colored chunk outlines (orange safezone, gray wilderness, green own claim), fixes the saber hologram to its arena, and requires visiting the Weapons recipe category. X-wing instruction is sequential: place → board → look up, press W/fly five seconds → hold D/right bank → hold A/left bank → S/stop → shoot the target → watch the explosion → descend with Sneak and land. The actual ModelEngine controller filters only the keys allowed in the current lesson; normal latched thrust, mouse yaw/pitch, speed, banking and braking remain unchanged. The airspace is expanded, and a temporary landing pad provides a solid surface below the stopped ship. Cannon use is gated until target practice. A 17-by-17 bullseye made of real world blocks appears ahead of the stopped ship and is destroyed by a native cannon block impact. Temporary target and landing blocks are restored when the lesson ends. The normal vehicle controls outside the tutorial are unchanged.

Completion explicitly recommends `/warp naboo` as the most beginner-friendly start. Galaxy Map lore explains each planet's role. The Agrinium pickaxe uses three ingots and two sticks. All seven Kyber crystal recipes are removed, including dye conversions; the existing Kyber crate supplies crystals. Recipes consuming crystals remain available, and acquisition lore points to Ilum and the crate.

## Ownership and deployment

RankService owns effective permissions and operator normalization. StaffMode owns its separate inventory recovery record. ParkourService owns checkpoint progression and death recovery. TerritoryService/Protection and ClanProtection own native protection; SafezoneMobs adapts the pinned Mythic API. InteractiveTutorial owns practice progression; the ModelEngine adapter applies its input/motion policy.

Deploy the tested gameplay JAR, matching redstone agent, and these reviewed configurations together while development is stopped: `admin/staff.yml`, `ranks/ranks.yml`, `territory/maps.yml`, `tutorial/tutorial.yml`, `travel/warps.yml`, `items/recipes.yml`. Production also needs the rebuilt executable launcher on its next separately approved promotion. No resource-pack update or world edit is required. Keep a full stopped-runtime backup. Never Bukkit-reload this change.

## Owner acceptance checklist

Use a normal/default account or `/staff off` except where specified. Report each number as pass/fail and describe any failed step before promoting this branch.

1. **Staff inventory and permissions:** carry identifiable stacks, armor and an offhand item. `/staff on` must show empty inventory and allow staff actions. `/gms` must keep staff mode active with the temporary inventory and staff commands. `/staff off` must restore every item and disable staff commands. Repeat once with a reconnect while staff mode is on.
2. **Ilum course:** outside staff mode, use buttons/plates and open the course doors. Before checkpoint 3, hazard damage should reset you. At checkpoint 3, take nonfatal hazard damage without a reset; die and verify return to checkpoint 3.
3. **Batuu scenery:** try trapdoors, doors, levers, containers and tool interactions. Scenery should remain protected; intended NPC/crate interactions should still work.
4. **Back command:** `/back` must be denied to Default and to staff with mode off. Check it works with staff mode on.
5. **Hunger:** sprint/jump around spawn and check that food bars do not decrease. Confirm ordinary wilderness survival still consumes food.
6. **Safezone mobs:** attract a melee mob and a ranged Tusken in wilderness, cross into safezone, and observe them dropping aggro/stopping new attacks. Stand inside safety near wilderness mobs and confirm they do not acquire you. Re-enter wilderness and confirm normal combat returns.
7. **Tutorial route:** `/tutorial` with staff mode off. Follow the blue particles; inspect orange/gray/green chunk borders through the clan lesson. Check the saber hologram remains at the arena anchor. Closing `/recipes` before opening Weapons must not advance. Look up and press W once: the X-wing must keep normal thrust after releasing W and steer wherever you look. Complete D, A and S in order; S must stop the ship. Attempt out-of-order keys, shoot the real block target, wait for the explosion and land with Sneak. Confirm the strong Naboo recommendation at the end, and inventory recovery using `/tutorial end` during a replay.
8. **Galaxy Map:** `/warp` and read each planet's role and beginner guidance; ensure the lore is readable at your normal GUI scale.
9. **Agrinium pickaxe:** craft with three Agrinium ingots and two sticks. Sheets must no longer match this recipe.
10. **Staff Ilum testing:** `/staff on`, `/warp ilum`, fly and teleport, then `/parkour checkpoint 3`. Restrictions should not pull you back or replace the staff inventory with parkour controls. Exit staff mode and verify normal rules return.
11. **Kyber acquisition:** `/recipes` must contain no crystal crafting/dye recipes. Test an old crystal recipe grid: no output. Confirm the Kyber crate still awards crystals and a lightsaber recipe still accepts them.

## Verification

`./scripts/build.sh` passes 337 Java tests; the tooling suite passes all 79 tests after building its pack fixture. Native Paper checks pass 264 staff/protection/circuit assertions and 1,236 tutorial assertions, including normal-speed latched flight, mouse steering, native cannon block impacts, target destruction and restoration of temporary target/landing blocks. Actual JVM restart checks pass 49 staff and 51 tutorial assertions. The exact tested gameplay JAR and circuit agent are active on development after a full stopped-runtime backup, with healthy startup. All other binaries and the pinned resource pack are unchanged; production is untouched.

`./scripts/build-dev-playtest-probe.sh` verifies native staff command/inventory isolation, Ilum interactions/damage/respawn, hunger, mob targeting and circuit power in a disposable server on port 25590. `./scripts/build-tutorial-probe.sh` exercises the real menus, models, controls, cannon impacts, completion reward and recovery. Both prepare private interrupted-session snapshots for an actual JVM restart check. Never install either probe on the shared development or production runtime.

Visual particles, hologram placement, physical keybindings and gameplay feel require the owner acceptance pass above.
