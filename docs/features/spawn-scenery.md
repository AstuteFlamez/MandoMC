# Feature: Fixed spawn scenery
Status: playable
Branch: bilal/spawn-decor

## Player experience
Nine approved placements decorate Batuu: landed open-door U-Wing, landed X-Wing, fixed TIE fighter and large speeder, plus Boba Fett, Bossk, Gamorrean, Quarren and Twilek. Right-clicking a character gives one private, character-appropriate written line in chat. Three lines cycle independently per player and character. Decorative NPCs do not play audio.

Boba is offset once by three model pixels (3/16 block) forward along his saved yaw, retaining immutable capture coordinates. Character heads respond within two ticks and turn independently. Bodies follow only when the target exceeds a 45-degree comfortable angle; neck yaw stays within 60 degrees and pitch within 35 degrees. A small target-distance margin prevents jitter between equally near players. Characters return to captured facing when no eligible player is within five blocks.

## Rules
Feet remain at exact saved anchors; vehicles never look at players. Scenery is invulnerable, non-collidable and nonpersistent, cannot be driven, mounted, packed, stolen or equipped, and never uses combat AI. Click dialogue has a three-second per-player cooldown shared by native/model/offhand paths. ModelEngine supplies transparent client interaction entities for the five characters only; enabling those targets does not make decorative bases visible or damageable. Existing gray Jawas, Ilum NPCs and player vehicles retain their own state.

## Ownership and integrations
DecorService owns configuration, protected bases, chunk tickets, target selection, written dialogue and cleanup. DecorLook computes shortest-arc head/body poses. DecorModel writes native and rendered locked rotations through ModelEngine. For these five decor instances it replaces the hidden fallback HEAD behavior on the actual head bone with the normal provider, enables local/inherited rotation, and refreshes descendant behaviors so Boba’s visor inherits the head transform. Shared blueprints and pack assets remain unchanged. TerritoryService authorizes protected scenery spawning. The existing integrated lifecycle is retained; unrelated deployed entries remain identical.

## Parameters
`decor/placements.yml` defines one to 32 stable IDs with world, exact location/facing, original rig, scale, animations and optional written dialogue. `look-range` defaults to five blocks. `forward-offset-pixels` derives the applied anchor from its immutable capture. Changes require restart.

## Verification
189 Java tests pass, including prompt head-only response, behind-player body following, shortest-arc wraparound, neck/pitch limits, exact idle return and nonaccumulating Boba offset. The current targeted native run passes 221 scene/motion assertions and 76 actual packet interaction assertions across all five speakers: real ModelEngine client interaction-spawn packets, actual renderer IDs and base INTERACT_AT, held item, main/offhand deduplication, private speaker/prefix components, line cycling and zero decorative sound packets. Original entity persistence/duplicate/protection checks remain verified from prior boots.

The client-click fault was reproduced with the original artifact: interaction hitboxes were registered server-side but `hitboxVisible=false` made ModelEngine omit their client INTERACTION spawn packets. Forcing those unspawned IDs in direct tests had hidden the bug; the revised tests require actual client spawn packet production first.

## Rendered head correction (2026-11-11)
The original five blueprints name the bone `head` without a head tag. ModelEngine nevertheless inserts a hidden `HeadForcedImpl` fallback on ordinary bones; therefore `hasBoneBehavior(HEAD)` alone is not evidence of a working head. With target yaw changing from +30 to -30, the old fallback produced exactly zero display-head movement despite correct stored head yaw. The instance-only replacement uses the public HEAD behavior provider and refreshes parent inheritance for descendants. No private vendor implementation or shared blueprint mutation is needed.

Six focused pose/config tests pass. The native renderer regression passes 134 assertions with idle animations running: five real head drivers, signed world-space forward direction toward the player, approximately 60-degree head yaw changes with stationary torsos, approximately 40-degree pitch changes, Boba visor attachment, body following beyond the neck cone, exact anchors, unchanged vehicle orientations and idle return. Embedded actors explicitly enter the renderer tracking map and the actual ActiveModel tick/readModelData path supplies display quaternions; stored yaw fields alone cannot pass. The final same-runtime packet test passes 76 assertions for client-visible transparent click targets, main/base/offhand interactions, cooldown/line rotation and no decorative sound.

## Pause note
Visible head correction deployed as JAR SHA-256 `a10aabe28da5f5c279c58d1dd85b8150415babf69e92f8330dcdf68e5426ff45` over skull revision baseline `b34cf6d82787`. Only DecorModel.class changed; unrelated entries are byte-identical. The live smoke passed 128 read-only assertions including all five non-hidden head drivers/local head hierarchy, transparent targets, exact anchors and fixed vehicles. Normal server restarted at 22:35 America/Chicago, November 11, without probes. All seven gray Jawa UUIDs/rigs, current pack/configs, 2115 frozen chunks, active Jabba and queued KOTH are preserved; window released. Renderer evidence is verified, while a real client visual confirmation is still requested. Next action: approach the five characters from both sides and different heights, then right-click for written dialogue.
