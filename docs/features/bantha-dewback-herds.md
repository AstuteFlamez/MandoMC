# Feature: Bantha and Dewback herds
Status: implemented and staged; deployment and client playtest pending
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

An owner's unsaddled adult opens the real native horse inventory on right click. Its native
saddle slot owns the single actual saddle and controls the model's visible saddle. A saddled
adult mounts on right click; sneak-right-click opens equipment. Armor is unavailable. Inventory
click, shift, hotbar, drag, automation and ModelEngine mount paths enforce ownership, adulthood,
territory and occupancy. Riding uses ModelEngine's native walking controller and existing gait.

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
baselines and coordinate the server window before deployment or publication. No live world,
shared runtime or published pack has been changed by this work.

Next action: hand the tested scoped artifacts to the coordinating chat, recheck the current
baselines and use its assigned deployment window before the client playtest.
