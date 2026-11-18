# Feature: Saber Workbench and enchantment books
Status: deployed to development; client playtest pending
Branch: bilal/saber-enchantments

## Player experience
Craft and place a Saber Workbench, then choose Craft, Style or Enchant. Craft opens a native 3×3 crafting grid, Style opens native stonecutting with only recipes valid for the inserted saber, and Enchant opens the native two-input anvil screen. `/recipes` identifies saber assembly and style recipes with the Saber Workbench name and smithing-table icon. Existing crafting-table and stonecutter registrations remain compatible.

All saber styles share netherite sword attributes: 8 attack damage and 1.6 attack speed. Sharpness I–V follows native Java behavior, reaching 11 melee damage at V. Each throw contact uses the same fully charged critical damage: 12 at base and 15 with Sharpness V, before armor. Damage is captured at launch before the actual saber leaves the hand. The existing outbound/return contact rules and protection checks remain.

Endurance and Recovery are native, combinable enchantments capped at III. Endurance reduces charge consumed by blocking, reflecting and throwing. Recovery increases continuous charge regeneration, including during combat and the gradual throw drain. There is no recovery delay. The shared player-owned charge pool and exhaustion lockout remain; changing sabers cannot grant free charge. Tooltip descriptions explain Endurance and Recovery without numerical bonuses.

Endurance I and Recovery I books are obtainable from enchanting tables and the Voting and KOTH crates. KOTH also awards Mending I. Jetpack retains its existing acquisition and native anvil application; Mending and Jetpack do not roll at enchanting tables. Equal-level books combine according to native anvil rules, with native XP costs, prior-work penalties, input consumption and inventory handling.

All vanilla and custom enchanted books display the existing rarity badge followed by a new matching Enchantment image badge and descriptive gray lore. Multi-enchantment books list each enchantment and its explanation. Original foreign lore and player renames are retained. Real enchantment data remains authoritative.

Admin `/enchant <enchantment> <level> [player]` accepts short or namespaced names. Operators can apply Recovery V and Endurance V directly while survival books remain capped at III. Level 0 removes an enchantment. Consumption retains a minimum cost even at extreme admin levels.

## Rules
The approved table pool is Aqua Affinity, Blast Protection, Depth Strider, Efficiency, Feather Falling, Fire Aspect, Fire Protection, Fortune, Knockback, Looting, Projectile Protection, Protection, Respiration, Sharpness, Silk Touch, Swift Sneak, Thorns, Unbreaking, Endurance and Recovery. Existing incompatibilities remain native.

Luck of the Sea and Lure are removed from books and equipment, including existing carried/container items as encountered, generated loot, fishing catches and newly acquired villager offers. Other excluded table enchantments retain their native behavior when acquired elsewhere; this change does not grant them new sources.

The workbench cannot be used while dead, riding, in combat, in tutorial escrow, during a saber throw or beyond six blocks. Placed machine identity survives ordinary drops and chunk persistence; piston movement is blocked. Native transactions revalidate the marked machine before pickup. Closing returns inputs through the native menu lifecycle. Saber style changes preserve enchantments, durability, repair history, extra persistent state and player renames while replacing only the catalog style identity, model, standard name and style lore.

## Migration and ownership
Saber Workbench replaces the prototype Saber Forge and material-funded point menu. Existing marked blocks work in place, and the old item ID resolves to the new catalog item. Old Damage levels migrate to Sharpness, Defense to Endurance, and the greater of Speed/Throw to Recovery, clamped to native caps. Prototype attribute modifiers and generated point-summary lore are removed. Existing sabers receive the new base attributes without changing their identity, enchantments or names.

Native Minecraft registries own enchantment levels, compatibility and anvil/table transactions. MandoMC owns the approved pool, book descriptions, charge effects, crate configuration, item migration and workstation entry point. The existing registry datapack is extended in place and must be installed before server startup. Existing default enchantment tags are extended for shield-backed sabers; the table pool is explicitly replaced.

## Parameters
Initial configurable balance: Endurance reduces consumption by 1/15 per level, capped at 20% at III. Recovery adds 25% of the base regeneration rate per level, capped at 75% faster at III. Baseline recharge remains 0.5 charge per tick (10 per second). Default throw cost is 51 charge, reduced consistently by Endurance for both launch eligibility and gradual spending.

KOTH retains existing reward weights and adds Endurance I weight 8, Recovery I weight 8 and Mending I weight 4, bringing its total to 120. Voting adds Endurance I and Recovery I at weight 6 each, bringing its total to 112. These are weights, not percentages. One key still awards one reward.

## Verification
Unit tests cover charge consumption, reservation, constant regeneration during combat/throws, exhaustion, tier scaling, critical damage and configuration. The isolated Paper fixture passed 2,679 native assertions covering all 43 vanilla plus three custom book presentations, 300 real table rolls, book/saber anvil operations, real result-pickup packets, material/XP consumption, style preservation, recipe station labels, removed-workbench pickup denial, admin permission/level overrides, native 11-damage Sharpness V melee and actual 15-damage throw contacts with all enchantments preserved on return. Native actor equipment/cooldown state is synchronized, the flight corridor is clear and its chunks have explicit tickets.

## Pause note
Development is running native-tested release `361eb26` with the verified hosted pack, matching bundled M4-ND knowledge and the deployed spawner/event release preserved. Saved recipe-book entries migrate without warnings or duplicate entries. Next action: client-playtest the Workbench, books and admin commands after reconnecting for the pack.

## Workbench interaction and lore follow-up

Native screen titles are supplied when each workstation menu is created, avoiding the unsupported title-change call. Transient workbench ownership is recorded before opening so staff Creative can use crafting, styles and enchanting while physical storage remains blocked. The actual button-packet path is tested in Survival and staff Creative.

Applied enchantments now appear at the bottom of equipment lore, after the original description and controls with one blank separator. The shared formatter preserves item badges, notes, names, actual enchantment data and original hidden-enchantment intent. It also migrates former Jetpack rows, restores original lore/visibility after disenchanting and formats styled-saber previews. Enchanted books retain their badge header; any additional notes precede the final effect explanations. The native regression passes 2,721 assertions.


## Compact titles and style refresh

The workbench hub and its native screens use dark-gray titles that fit the vanilla panels: Saber Workbench, Saber Crafting, Saber Styles and Saber Enchanting. Button labels remain separate from window titles.

After synchronizing a filtered style recipe list, the pinned Paper adapter refreshes the client input slot through empty and back to its real contents. Minecraft 26.1.2 otherwise caches stonecutter options by input material, so shield-backed sabers can show empty or stale choices. This refresh does not mutate or consume the authoritative item. Mace Windu's saber offers Revan's redeemed style and Vernestra Rwoh's style. Switching saber families refreshes both client choices and server indices together.

The native regression reproduces the empty Mace list with the previous adapter and passes 2,764 assertions with the fix, including ordered outbound-packet replay using the pinned client material-cache behavior, same-material family switches, both Mace outputs, metadata-preserving pickup and measured compact title/color checks.
