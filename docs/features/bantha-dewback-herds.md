# Feature: Bantha and Dewback herds
Status: implementation; first slice staged, not deployed
Branch: bilal/bantha-dewback-babies

## Player experience
Banthas follow a player holding a Hay Bale; Dewbacks follow a player holding Cactus.
Either hand attracts them. They walk using the native navigator, stop within 2.5 blocks,
and lose interest when food is removed, the player becomes a spectator, exceeds ten blocks,
or breaks line of sight. This applies to adults and babies; no owner teleport or combat-pet behavior.

Babies reuse exactly the adult geometry, texture and animation at uniform half scale, including
collision. Native Ageable age owns the baby/adult transition and save/load. Native default
20-minute loaded growth is retained; normal server unload behavior pauses growth. Growth does
not introduce separate health or level formulas. Both the Mythic and final native death-drop
pipelines suppress all baby items and experience, including player and environmental kills.
Adult Banthas add 1–3 ordinary brown wool alongside their existing 1–3 hide. Adult Dewback food
and all other species' drops are unchanged. Natural encounter rolls still spawn only adults;
no ambient baby chance was added.

## Smallest playable version
This slice provides a breeding-callable baby spawn boundary, automatic growth/scaling,
complete baby death-loot suppression, adult Bantha wool and food attraction. Native panic and
float goals precede attraction; existing idle/wander animation remains ModelEngine-owned.
Food interaction, taming, mating, ownership and saddle controls below are the next slice,
not already enabled by this checkpoint. Existing blanket interaction/mount/breed denial remains.

## Riding and breeding architecture
The existing base is PIG for both species. The current models have no saddle geometry,
saddle texture variant, driver/passenger bone, riding controller, or mount/dismount clip.
Bantha's separate groups are body/head/horns/tail/four legs/hitbox. Dewback has body/head/jaws/
back/tail/four legs/hitbox/shadow. Both have only walk, idle and death clips. Reuse these walking
clips while riding; no new gait is required. Existing adult textures are 256 by 256.

A true native horse inventory requires a horse-family base; Bukkit cannot open one for a pig.
Use HORSE for new herd spawns, with its single native owner UUID/tamed state and native
HorseInventory saddle slot as the authority. Do not mirror a saddle in a custom menu or PDC.
Keep native Ageable growth and breeding cooldown; record any new schema-marked breeding
metadata on the entity only when native APIs cannot express it. Prevent native horse armor and
unapproved pig carrot/potato breeding. Existing wild pig-backed herd instances need a deliberate
one-time conversion on interaction: preserve location, level, current/max health ratio and natural
encounter tags; publish the replacement only after successful creation, then remove the old entity
without death loot. Tamed animals must be excluded from far-player natural cleanup. Do not
rewrite every wild mob at startup or change natural spawn budgets.

One herd service owns accepted feeding, taming, love/cooldown decisions and offspring creation.
Use the configured species food for attraction and accepted feeding. Consume exactly one item
for a successful action (Creative exempt), reject repeat/offhand duplicate callbacks, and give
food precedence over menu/mount. Only the owner may manage equipment or mount; ordinary
food attraction may select anyone. Breeding must validate same species, mature age, cooldown,
owner authorization and territory before producing one Mythic offspring. Use native navigation
for courtship and parent following, not teleport or position writes. Rejected actions and protected
interactions consume nothing. Tamability chance, healing and baby feeding acceleration must be
configured and explicitly documented when implemented, not silently treated as settled rules.

After taming: ordinary right click on an unsaddled adult opens the native horse menu; placing
a real saddle updates visible equipment. Ordinary right click on a saddled adult mounts;
sneak-right-click opens the same menu. Check owner/adult/saddle/protection before the native
menu, inventory edits and ModelEngine mount events, including shift/hotbar/drag paths. Gate
native auto-mounts through the same authority. No temporary horse entity as a menu proxy.

Extend each existing model with a driver locator and an initially hidden saddle/belt bone,
attached to its animated body. Inspect the existing UV atlas before allocating a small saddle
texture area or a separate approved texture. Toggle geometry from the native saddle slot on
inventory mutation, load and death; opening a menu is not itself an equipment change. Reuse
ModelEngine's native ride controller and existing vehicle integration boundary patterns, without
putting herd animals into the packed vehicle item/fuel/death-blast system. A restart restores
native owner, saddle, age and cooldown, then reapplies the model state. Babies cannot mount or
hold equipment. Validate the merged current pack and coordinate the shared server window before any
resource-pack release.

## Parameters
`mobs/herd.yml` schema 1: `bantha-food: HAY_BLOCK`, `dewback-food: CACTUS`.
These are working choices from the user follow-up. Restart applies food configuration.
`mobs/drops/bantha.yml`: additive `drops.wool-minimum` and `drops.wool-maximum`,
default 1 and 3, validated as integers with 1 <= minimum <= maximum <= 64. Older runtime
configuration receives the same additive defaults. Existing hide quantity keys retain meaning.

## Verification
The complete Java build passes all 191 tests. The disposable Paper 26.1.2 build 74 /
MythicMobs 5.14.0 / ModelEngine R4.1.1 probe passes 41 first-boot assertions and six
restart assertions (47 total). Actual collision dimensions are half the adult dimensions for
both species. Player and environmental deaths, ordinary animal isolation, configured adult
drops, native growth, actual registered food-goal/path creation, both hands, food changes,
wall/spectator rejection and persisted growth/model state pass. Client rendering remains
unverified. No shared server restart or publication occurred.

See the disposable-server HerdProbe. It injects extra custom/native death drops to verify the
final baby guards, exercises actual Mythic deaths, uniform scale, native growth, real native
pathfinding, main/offhand attraction, wrong food, walls and spectator rejection, and performs
a second boot for persisted baby age/identity/model state. Never install this destructive test
plugin in the development world: it builds its own floor and stops the disposable server.

## Rollout
No shared runtime or resource-pack source was changed. This isolated checkout starts at the
chat integration checkpoint `cc9c9f6`; its full built JAR is not a replacement for the newer
shared JAR. Integrate current NPC/starter-book and other concurrent changes before packaging,
or prepare a verified scoped deployment that preserves every unrelated current entry. Read
the actual shared JAR/hash and coordinate its restart window immediately before deployment.

## Pause note
Staged source only. Native saddle/owner/mating implementation and client visual playtesting
remain. Next action: coordinate the shared runtime window and integrate current concurrent source
before deploying this tested first slice. Continue with the native horse-backed interaction
flow in the next implementation slice.
