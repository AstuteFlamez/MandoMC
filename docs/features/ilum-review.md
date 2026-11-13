# Ilum adventure review

Status: deployed for review, awaiting a multiplayer client playtest.
Branch: bilal/ilum-review.

## Player experience

`/ilumreview` starts or resumes a linear Ilum-inspired Minecraft adventure in Adventure mode. Six chapters
contain 30 rooms/checkpoints: snowy approach, Jedi temple, frozen lake, crystal caverns, Imperial excavation,
and the Gathering. The final interaction attunes the blue kyber crystal. The 50–70 minute first-run target
is an estimate, not a measured completion time; there are no forced waits.

The route alternates 18 clue-puzzle rooms (three personal panels each) with 12 short parkour courses.
Each course has 13 broad landings, no more than two air blocks between landings, level jumps, and wider
rest platforms. Every room starts with a supported emerald meditation pad and a labeled checkpoint.
Falling returns players immediately; the build has no combat or item-loss mechanic.

## Multiplayer and safety

Players share the physical world but own separate checkpoint and three-bit puzzle state in their native
player persistent data. Answers can be discussed cooperatively. A wrong answer, retry, reset or disconnect
never resets another player. Buttons do not physically toggle, and the permanent exit bulkhead transfers
only an eligible player to their next checkpoint. Reconnecting restores the current checkpoint and solved
panels. Final crystal attunement is also personal; it does not remove the shared crystal or dispense loot.

The whole `minecraft:ilumreview` dimension has PvP off, peaceful difficulty, damage cancellation, protected
blocks, denied buckets/explosions/projectiles/item use, stable weather/time and frozen terrain updates.
Player collision uses a scoped main-scoreboard team mirrored by the existing HUD; previous team and
collision policy are restored on exit, disconnect or plugin shutdown. Inventory contents are preserved.

`/ilumreview retry` returns to the current pad. `status` reports progress. `reset` resets only the caller.
`leave` returns to the entry location and previous game mode. `/mvtp ilumreview` provides operator inspection;
Creative operators can fly through the geometry, while `/ilumreview` selects the actual Adventure flow.
All editing remains denied in this review world, including Creative inspection.

## Ownership and delivery

The review uses a separate IlumReview companion JAR, deliberately isolated from the development server's
combined MandoMC JAR. This avoids replacing concurrently deployed gameplay. MandoMC's existing world
registry loads the new generated dimension from its additive worlds.yml entry and supplies `/mvtp` aliases.
No existing world, public warp, event, resource-pack asset or plugin JAR is replaced. This is temporary
review content; integration into the single production gameplay JAR follows accepted playtest feedback.

`review/ilum/generate.py` deterministically writes the original voxel terrain, native Paper dimension,
map manifest and route-footing proof. NumPy, nbtlib and PyYAML are the generation dependencies.
`review/ilum/build.py` compiles the companion against the exact installed Paper API and runs its rules tests.
`review/ilum/install.py` installs only into an absent target, stops cleanly, retains configuration/JAR backups,
appends the world entry, starts Paper, and verifies that all existing plugin bytes and pack settings agree.
Generated worlds, binaries, local paths and backup state remain ignored outside tracked source.

## Verification

The repository's `scripts/build.sh -o` passed against the pinned Java 25/Paper baseline. The companion's
192 rules assertions cover incomplete gates, independent players, wrong/repeat answers, checkpoint
advancement, completion bounds and saved-state reconstruction. Generation verifies 2,310 supported route
positions, 156 landings and mandatory jump dimensions against the final voxel array.

On-server verification checks checkpoint support/clearance, all 162 answer buttons, native waxed sign text,
native checkpoint lighting, all platform landings and route-footing samples. It simulates both players with real Bukkit events, blocks
and binary-serialized native persistent data, testing personal gates, all correct panel interactions,
the final crystal, independent player progress, safe fall retry, damage denial and block protection.
These isolated proxies never manipulate a connected player's position or inventory. They verify the
review handlers and actual block data, not a Minecraft client's rendering or complete vendor event ordering.
The live run passed 8,881 assertions. A first-run sign/pedestal overlap was repaired before opening access.
Deployment receipts confirm all preexisting plugin JARs, earlier world entries and pack properties are preserved.

## Next action

Play `/ilumreview` with two players and report timing, route readability and the rooms needing visual or
puzzle refinement. The first-person experience and approximately one-hour pacing remain unverified until then.
