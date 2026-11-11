# Feature: Tutorial handbook
Status: verified implementation; coordinated deployment pending
Branch: bilal/tutorial-handbook

## Player experience
`/tutorial` gives and opens a native signed MandoMC Handbook. The confirmed thirteen topics are Travel; combined Combat/Death/Blasters/Lightsabers; Armor/Repairs; Mining/Materials; Crafting/recipes; Credits/Auctions; Clans/Claims; Raids/Warpoints; KOTH; Jabba; Vehicles; Jawa Trading; Ilum/Crates. Each topic occupies exactly one book page. Titles are concise and dark/bold; text uses the normal black Minecraft book font.

The command reuses an existing handbook, including one in the offhand or a stack of matching books; book counts are preserved. With full storage, it opens a preview without adding/dropping/replacing items and tells the player to free a slot for a physical copy. A held cursor item or crafting ingredients defer the request without closing that inventory, because vanilla closure could drop those items. Ordinary owned items, armor and offhand gear remain untouched. Console receives a players-only response. Books are normal droppable inventory items and can be requested again for free.

## Rules and scope
First-join pack-success messaging, the original tutorial-seen PDC flag, spawn locations, beds and respawn behavior are unchanged. Only the explicit tutorial command delivers a book. This supersedes the previously deferred tutorial-library direction for that command. No automatic first-join book is introduced.

Book pages contain plain text and verified controls/commands, not arbitrary MiniMessage actions. Action names such as Use/Drop are used rather than assuming a player's custom keybindings. No resource pack or new assets are needed. The default 26.1.2 ASCII font advances are derived from the locally installed client bitmap; artwork is not included in source. Wrapping respects 114 pixels and fourteen text lines, including a title and blank separator. Oversized pages/words and invalid schema/topic counts fail validation instead of silently truncating. Custom client fonts are outside this layout check.

Jabba's page describes its implemented scheduled room/cache/PvP flow without promising a particular public warp route: the existing Jabba event currently still uses the legacy Tatooine world while public Tatooine travel is the review map. That separate entry-route discrepancy was reported to the coordinator and is not changed by the handbook.

## Ownership
ArrivalListener retains arrival and first-join state. Its explicit tutorial method delegates to TutorialHandbook, which owns only the written-book template and safe delivery. HandbookDefinition and BookPageLayout validate immutable content/layout. `language/handbook.yml` schema 1 stores the title, author, messages, page count and ordered thirteen feature texts; sanitized defaults are also in server/config/common/mandomc/language. Content changes require restart; an existing handbook updates when /tutorial is requested again. The marker is mandomc:tutorial_handbook and does not claim an item-catalog identity.

The scoped overlay replaces only ArrivalListener, new gameplay/tutorial classes and handbook language resource. It preserves the live bootstrap, /spawn handler, WarpService, CrateService, ParkourService, recipes and every unrelated byte. No installed language/messages.yml tutorial text is changed. Rebase from the exact current live JAR before deployment.

## Verification
199 Java tests pass, including confirmed topic order, the combined combat subjects, every page's font/line bounds, word-preserving command wrapping and invalid/oversized configurations. The final full disposable Paper run passes 361 assertions: native written-book metadata/pages; actual /tutorial dispatch; repeated requests; existing/offhand stacks; exact forty-one-slot preservation on full-inventory preview; cursor/crafting safety; and unchanged first-join welcome marker/book delivery behavior.

The native probe's full suite is restricted to a disposable localhost:25590 runtime; production mode only inspects metadata and binding. No fixture actors are created on the shared server. Client rendering still needs ordinary in-game feedback; font widths/serialized pages are checked natively.

## Next action
Coordinate the shared restart window, rebase the scoped overlay on the latest deployed recipe/Ilum changes, then back up, deploy, run read-only smoke, remove the probe and restart normally.
