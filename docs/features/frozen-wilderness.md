# Feature: Frozen wilderness originals
Status: verified implementation; awaiting Jawa restart handoff before live capture
Branch: bilal/tutorial-handbook

## Player outcome
Claiming a prepared wilderness chunk succeeds immediately, using a durable existing terrain original. `/c claim`, Survival edits, placement and propagated changes never capture or refresh terrain. An unsupported or unfinished chunk reports that its frozen backup is unavailable. Existing claim limits, static-zone and rival buffers remain authoritative. Wilderness still restores gradually 24 hours after the latest Survival edit; abandoned claims retain their irreversible public-loot/disband lifecycle.

## Finite scope
The first explicit capture version covers existing-generated wilderness inside the current MandaloreReview and TatooineReview imported atlases, both chunks -16 through 15. Their saved native world borders are centered at zero with size 500. Only whole chunks inside those borders are supported: 757 Mandalore and 677 Tatooine chunks, 1,434 total. Another 223 atlas wilderness chunks straddle a border and are excluded. Of the supported wilderness, 1,038 chunk centers also satisfy the empty-neighbor static-zone/border buffer needed for claims. Other supported wilderness is still captured for ordinary wilderness restoration. All scoped chunks already exist on disk; capture forbids terrain generation.

Hoth is excluded pending its separate final cave and zone changes. No unmapped or infinite worlds are enumerated. All preexisting baseline files remain intact, including originals outside this batch. Four current in-scope originals are retained; remaining captures represent current terrain at capture time. Earlier edits cannot be reconstructed where no original was previously saved.

## Durable lifecycle
An explicit operator-owned `clans/prepared-land.yml` contains schema-version 1, a capture-id and a finite list of `world-key chunk-x chunk-z` strings. At most 8,192 unique chunks are accepted. No file means no automatic capture and no newly supported claims. The config is not bundled with a generated runtime chunk inventory. A different future scope requires an explicitly requested new capture version. This workflow never overwrites existing originals, even if terrain subsequently changes.

FrozenLandPreparation validates the fixed scope digest and verifies completed baseline checksums on its own worker before enabling the scope. Existing terrain loads use Paper's generate=false async API. A bounded main-thread snapshot is taken at most every 250ms, with one chunk in flight; snapshot scanning/compression and persistence use the existing land IO worker. New originals become ready only after their gzip file is atomically written and fsynced. No player can claim against an unfinished snapshot. Inventories/loot are not copied.

Each completed or retained original gets a SHA-256 entry in an append-only, fsynced journal. A crash after snapshot creation but before journaling retains and indexes that original on resume. An incomplete final journal line is truncated before validation; completed corrupt entries fail closed rather than overwrite terrain. Completion writes expected/completed counts and the journal digest. Failure writes a separate receipt and retains prior verified chunks; unverified new claims are disabled. Restart verifies and resumes the same scope instead of sampling it again. Preparation and restoration have separate lifecycles and IO owners, with no second terrain authority.

## Delivery and verification
The scoped release updates only LandRestoration, ClanCommands, ClanProtection, ClanFeature, the new preparation class and clan message defaults. The current runtime JAR must pass class fingerprints before overlay. A runtime scope file is installed with the stopped backup and normal startup; no Bukkit reload is used. Capturing does not require repeated restarts. Existing snapshots and world state are backed up before starting the batch; final checksums and receipt remain private runtime data.

The initial atlas-only count of 1,657 was refined against actual border data after an isolated boundary case failed closed. No live capture occurred with that broader list. The user held shared restarts while collecting six Jawa locations, then explicitly released the hold with Jawa first in the sequence. No live writes or capture occurred during that hold. Re-inventory current policy/borders and preserve all concurrent Jawa, ore, warp and map/event bytes before applying this slice.

## Verification
202 Java tests pass. The final frozen native suite passes 27 assertions against the scoped release rebased on the current shared JAR: finite capture and durable completion; retained original checksum; no unsupported capture; unchanged originals after later edits and actual Survival placement/break callbacks; first-attempt native claim; restart checksum preservation; scope-change refusal; and explicit partial failure with retained verified chunks, no missing-world generation and a durable failure receipt. Earlier disposable passes separately exercised recovery from a torn last journal line. The final combined release also passes 118 native sidebar/claim/raid/combat assertions.

## Next action
Obtain the coordinated handoff after Jawa rollout, rebase and back up the current server, install the verified combined slice/scope, then verify all 1,434 live checksums and the completion receipt.
