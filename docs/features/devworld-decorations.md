# DevWorld decoration placement

In Survival, sneaking and right-clicking a block with a custom helmet places it as decoration; the Rancor skull places by right-clicking. DevWorld has no frozen wilderness capture, so it must not require a planet baseline for ordinary edits or decoration placement. Clan ownership and territory checks still run. Planet wilderness keeps its existing prepared-terrain requirement.

Natural vanilla mob spawning is globally disabled by `disable-all-natural-mobs` in the survival rules, preserving plugin CUSTOM, commands, breeding and deliberate spawners. Existing entities are not swept; new-worldgen entities continue through the existing new-chunk filter.

Verification: `HelmetDecorationProbe` accepts `-Dmandomc.decoration-test-world=devworld` on isolated port 25594. It exercises all four helmets plus the skull across all six faces, native input, exact metadata/recovery and cancellation handling without frozen DevWorld terrain.
