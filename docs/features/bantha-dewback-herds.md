# Feature: Bantha and Dewback herds
Status: deployed; client appearance and riding-feel playtest pending
Branch: bilal/bantha-dewback-babies

## Player experience
Banthas follow Hay Bales; Dewbacks follow Cactus held in either hand. Adults and babies use
native navigation, stop within 2.5 blocks and lose interest beyond ten blocks, without line
of sight, or when the holder becomes a spectator. Native float, panic and wandering remain.
There is no owner teleport or combat-pet behavior. Ridden animals do not chase food holders.

Feed a wild adult to attempt taming (one-third chance by default). The owner can feed an
injured animal to heal four health points, accelerate a baby's remaining growth by ten percent,
or put a healthy mature animal into love mode for 600 ticks. Each accepted attempt consumes
one food; Creative retains it. Cancelled/protected actions, cooldowns and duplicate hand events
consume nothing. Food takes precedence over equipment and riding.

Two mature, healthy animals of the same species and owner navigate together and produce one
Mythic offspring after courtship. Territory checks and the cancellable native breed event apply.
Both parents then receive the native 6000-tick breeding cooldown. The child inherits its owner.
Babies follow a nearby adult of their species and owner, with food attraction taking precedence.
Natural encounters still spawn adults only.

An owner's unsaddled adult opens a compact custom equipment GUI on right click. The center
saddle control transfers one real saddle to or from the native saddle slot; shift-clicking a
saddle below equips one, and shift-clicking the control returns it to available storage. Display
icons and filler panes cannot be taken. A saddled adult mounts on right click; sneak-right-click
opens equipment. Armor is unavailable. Ownership, adulthood, territory, range and occupancy
are checked again for every transfer. The menu closes if access is lost. Native horse screens
are blocked. Food and leads take priority over equipment and riding.

Right-click with a lead to attach a native leash. Untamed animals may be led; tamed animals
and their babies require their owner. Leads work from either hand, use native movement and
fence knots, and retain the fence tether across restart. Ordinary right-click releases a leash
and returns one lead in Survival; Creative retains the attaching item and creates no item on
manual release. Sneak-right-click with a clear hand opens equipment while tethered. Cancelled
or protected actions consume nothing. Custom food, mating and parent goals yield to the leash.
There is no owner teleport or custom rope timer. Native protection/owner events also guard
fence transfers and detachment. Babies still drop no items on death, including attached leads.

Babies reuse the adult geometry, textures and animation at uniform half scale, including
collision. Native Ageable state owns growth, cooldowns and save/load. Default 20-minute loaded
growth remains; unloading pauses it. Babies drop no items or experience, including equipment.
Adult Banthas add 1–3 ordinary brown wool alongside their existing hide. Adult Dewback food is
unchanged. Adults retain the actual equipped saddle on death without duplicate equipment.

## State and vendor boundaries
New animals use HORSE bases for native owner/tame/age/HorseInventory state. There is no shadow
saddle inventory, owner save file or menu-proxy horse. Existing pig-backed animals convert only
when fed: create the same Mythic species, preserve native age/lock, position, AI/awareness,
velocity and encounter tags, clamp current health to the new base maximum, then remove the old
entity without death. Failed replacement leaves the old entity. Current fixed base stats and
level-one spawns preserve the separately deployed removal of mob leveling. No injury ratio or
age-based health multiplier is introduced. Owned animals avoid far-player encounter cleanup.

HerdAnimals owns accepted actions and birth; HerdCourtship owns native navigation goals;
HerdModels owns model scale, saddle visibility and riding. HerdBootstrap provides one lifecycle
entry and uses the current territory integration. Model setup receives the event's modeled
entity because the active model is not yet linked during AddModelEvent. Mythic 5.14 reapplies
volatile goals five ticks after tame events, including cancelled events; the adapter reattaches
only herd goals at tick six and retains native persistence.

Mythic and final native death handlers clear all baby drops/XP. Adults allow native equipment
drops and filter ordinary leather/pork body drops, preserving catalog rewards and the real
saddle. PreventOtherDrops is false for these two horses because Mythic otherwise deletes their
equipped saddle. All other species retain their existing pipeline.

Each original blueprint gains a body-parented saddle and driver locator. Existing cube UVs,
textures and keyframes remain unchanged. The saddle texture reuses the existing brown atlas.
The pack overlay adds only five files over the actual published pack; every existing entry,
including current fonts, credits, Rancor trophy and scenery, remains byte-identical.

## Parameters
`mobs/herd.yml` schema 1: `bantha-food: HAY_BLOCK`, `dewback-food: CACTUS`,
`tame-chance: 0.3333333333333333`, `healing: 4`, `baby-growth-fraction: 0.1`,
`love-ticks: 600`, `breed-cooldown-ticks: 6000`. Restart applies configuration.
`mobs/drops/bantha.yml` adds `drops.wool-minimum: 1` and `drops.wool-maximum: 3`;
integers must satisfy 1 <= minimum <= maximum <= 64. Existing hide settings retain meaning.
Language uses `language/herd.yml` through the existing chat styling owner.

## Verification and rollout
191 Java tests and two asset geometry tests pass. Full interaction tests on disposable Paper
26.1.2 build 74 / MythicMobs 5.14.0 / ModelEngine R4.1.1 pass 56 first-boot and 12 restart
assertions. These exercise real native equipment container clicks, taming cancellation,
ownership/protection, food consumption, healing, courtship, offspring, controller input,
saddle visibility/removal/death, persisted owner/age/saddle/cooldown and actual legacy pig
conversion after restart with injury and AI preserved. The final foundation regression adds 41 first-boot and six growth-restart checks, for
115 passing native assertions in total. These are native API/protocol checks; client appearance is pending.

Both probe plugins are destructive fixtures for their isolated world/port only. Never install
them on the shared development world. The foundation fixture clears its previous wall before
checking attraction, so repeated runs start from the same terrain.

The isolated source baseline predates concurrent shared features. The deployment script overlays
only the herd class families, two drop owners and narrow SandySpawns changes over the current
shared JAR. A Java ClassFile patch adds one bootstrap call while preserving every unrelated
bootstrap method. All unrelated JAR entries, including the deployed level-removal adapters,
remain byte-identical. The scoped pack merges over the current published ZIP. Recheck both
baselines and coordinate the server window before deployment or publication. The coordinated rollout used one normal restart, scoped backups, a verified public pack
download and the current baselines. Live JAR SHA-256 is
`34352f7b05410c52b12f69fd554e282983399c5d811fd4bbc20779daccfbadd9`. Pack
`resource-pack-26.1.2-2c075adc803b` was selected at startup and successfully loaded by the player.
171 read-only live assertions passed across two snapshots, including seven gray host UUIDs,
nine decor anchors, both boards and 2115 ready frozen chunks. All 2131 frozen baseline files
remained byte-identical. There were no loaded Mythic mobs in the startup sample; the seven
existing gray hosts had valid actual health. The checker self-disabled and its JAR was removed.
KOTH was restored from the fresh pre-stop status and the deployment window released.

Next action: client-test food attraction, native saddle equipment, saddle appearance and riding feel.


## Client saddle-screen correction and Dewback collision (2026-11-12)
The first client playtest found a gap in the original inventory test: a server-side HorseInventory
can open even when ModelEngine suppresses the real horse's spawn packet. The client then cannot
resolve the owner ID in ClientboundMountScreenOpenPacket and displays no screen. HerdModels now
registers the actual horse as the model's supported PivotOverride, with native and forced
invisibility. ModelEngine retains that exact horse on the client while suppressing its ordinary
body/equipment rendering. Native owner, inventory and saddle remain the only state authority;
there is no proxy horse. Renderer hitbox and native base INTERACT_AT routes use the same action.

The new isolated HerdClickProbe verifies the real generated interaction-box spawn and relay,
the actual outgoing ModelEngine horse-spawn filter, matching native owner ID, forced invisibility
metadata, emitted native mount-screen packet and a menu that remains open after ten ticks. It
covers both species at three and 4.5 blocks. The regression fails on the prior deployed class
because the native horse is hidden entirely. Existing taming, breeding, native inventory, riding,
death and restart/conversion checks are also rerun.

At the user's request, only Dewback's hidden hitbox cube expands from its old head-only bounds
to [-13,0,-34]..[13,36,22]. The resulting native adult collision is 3.5 blocks wide and 2.25 tall;
babies retain uniform half size. Main native collision uses a square horizontal bounding box;
the existing head sub-hitbox remains. All visible cubes, textures, UVs and animation keyframes
are unchanged. Regenerating the model produces byte-identical visible pack assets, so the
current published pack remains in use. 191 Java tests, two asset tests and132 native checks
pass (64 packet/collision,56 full interactions,12 restart/conversion). Deployed with one normal
restart and171 read-only live assertions. The JAR changes only HerdModels.class; all476 other
entries remain identical. Live SHA-256 is
`5e56f3e9fbc757bb63ac032ada528516759ad21d5c339edbe9d1e8b216ec1245`. The current
pack remains unchanged, the checker is removed, pre-stop currentNone/nextJabba is restored
and the coordinated window released. Actual client menu/model placement and collision feedback
remain the next playtest.


## Custom equipment and leads (2026-11-12)
The user superseded native horse menus with a custom GUI. HerdEquipment owns display/actions;
the actual HorseInventory saddle remains the sole persistent equipment state. GUI stacks are
presentation clones. Cursor and shift transfers move one original saddle; close, death, unload,
protection and cancelled inventory paths never return display copies. Gray unused slots use
the shared MenuItems background. HerdLeads routes actual model interactions into native leash
state and cancellable Paper events. Only the two herd definitions change PreventLeashing to
false; other species retain their vendor restrictions. Precise-hit Paper events have a separate
handler list and are explicitly cancelled before queued herd actions, avoiding native/custom
duplicate feeding or lead changes. 191 Java tests, two asset tests and176 native assertions pass:64 two-species actual click/screen
checks,35 GUI/lead packet/physics checks,9 fence-restart/rejoin checks,56 full interactions
and12 ordinary restart/conversion checks. Current rollout state is in docs/status.md.

The custom GUI/leads rollout is live with JAR SHA-256
`989c7a597d6c827d9e1463de8b465ee42a605c8877719414e8661cca74cdd7cf`. One normal
restart and175 read-only live assertions preserve the gray7/decor9/boards2/frozen2115 state.
All473 unrelated JAR entries and the current pack/Dewback blueprint remain unchanged. KOTH
was restored, the checker JAR removed and the coordinated window released. Client appearance
and feel remain the next playtest.
