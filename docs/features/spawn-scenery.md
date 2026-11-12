# Feature: Fixed spawn scenery
Status: playable; natural looking and written dialogue update staged
Branch: bilal/spawn-decor

## Player experience
Nine approved placements decorate Batuu: landed open-door U-Wing, landed X-Wing, fixed TIE fighter and large speeder, plus Boba Fett, Bossk, Gamorrean, Quarren and Twilek. Right-clicking a character gives one private, character-appropriate written line in chat. Three lines cycle independently per player and character. Decorative NPCs do not play audio.

Boba is offset once by three model pixels (3/16 block) forward along his saved yaw, retaining immutable capture coordinates. Character heads respond within two ticks and turn independently. Bodies follow only when the target exceeds a 45-degree comfortable angle; neck yaw stays within 60 degrees and pitch within 35 degrees. A small target-distance margin prevents jitter between equally near players. Characters return to captured facing when no eligible player is within five blocks.

## Rules
Feet remain at exact saved anchors; vehicles never look at players. Scenery is invulnerable, non-collidable and nonpersistent, cannot be driven, mounted, packed, stolen or equipped, and never uses combat AI. Click dialogue has a three-second per-player cooldown shared by native/model/offhand paths. ModelEngine supplies transparent client interaction entities for the five characters only; enabling those targets does not make decorative bases visible or damageable. Existing gray Jawas, Ilum NPCs and player vehicles retain their own state.

## Ownership and integrations
DecorService owns configuration, protected bases, chunk tickets, target selection, written dialogue and cleanup. DecorLook computes shortest-arc head/body poses. DecorModel writes native and rendered locked rotations through ModelEngine. TerritoryService authorizes protected scenery spawning. The existing integrated lifecycle is retained; unrelated deployed entries remain identical.

## Parameters
`decor/placements.yml` defines one to 32 stable IDs with world, exact location/facing, original rig, scale, animations and optional written dialogue. `look-range` defaults to five blocks. `forward-offset-pixels` derives the applied anchor from its immutable capture. Changes require restart.

## Verification
189 Java tests pass, including prompt head-only response, behind-player body following, shortest-arc wraparound, neck/pitch limits, exact idle return and nonaccumulating Boba offset. The current targeted native run passes 221 scene/motion assertions and 76 actual packet interaction assertions across all five speakers: real ModelEngine client interaction-spawn packets, actual renderer IDs and base INTERACT_AT, held item, main/offhand deduplication, private speaker/prefix components, line cycling and zero decorative sound packets. Original entity persistence/duplicate/protection checks remain verified from prior boots.

The client-click fault was reproduced with the original artifact: interaction hitboxes were registered server-side but `hitboxVisible=false` made ModelEngine omit their client INTERACTION spawn packets. Forcing those unspawned IDs in direct tests had hidden the bug; the revised tests require actual client spawn packet production first.

## Pause note
The scoped natural-looking/chat-only update is ready. Next action: deploy in the assigned window and verify the five client click targets read-only, then ask the user to right-click Boba and Bossk in game.
