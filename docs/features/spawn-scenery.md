# Feature: Fixed spawn scenery
Status: playable
Branch: bilal/spawn-decor

## Player experience
Boba is offset once by three model pixels (3/16 block) forward along his original captured yaw. His immutable baseline coordinates are retained in configuration. All five characters look toward the nearest non-spectator, non-vanished player within five blocks with six-degree updates every five ticks, returning to captured facing when nobody is nearby. Their feet never move.

Batuu contains the four vehicles and five characters at the nine user-captured positions and facing. The landed U-Wing has open doors, the X-Wing is landed, and the TIE fighter and large speeder remain stationary. Players can click Boba Fett, Bossk, the Gamorrean, Quarren and Twilek for short private dialogue and nearby original voice clips.

## Rules
Scenery is invulnerable, silent except for controlled voice playback, non-collidable and fixed. It cannot be driven, ridden, packed, stolen or equipped. Characters use idle models without combat AI. Clicks have a three-second per-player cooldown; voices have eight-second per-character and ambient global cooldowns. Restart reconstructs exactly one instance of each configured ID. Existing player vehicles, Jawas and Ilum NPCs retain their owners.

## Smallest playable version
Nine approved captures, four vehicles and five characters, with no additional pack assets. Client appearance and perceived model facing remain subject to player feedback.

## Ownership and integrations
DecorService owns configuration, protected bases, chunk tickets, dialogue and cleanup. DecorModel uses the original ModelEngine rigs and animation poses. TerritoryService authorizes the protected decorative entity spawn. No Mythic combat instances or VehicleService records are created. The deployment overlay preserves the actual integrated command hub and adds start/stop calls only; unrelated existing JAR entries remain identical.

## Parameters
`decor/placements.yml` defines one to 32 stable IDs with world, exact coordinates, yaw/pitch, original model, scale, animations and optional dialogue/voices. Changes require restart. Voice range defaults to seven blocks; ambient intervals default to 80–140 seconds.

## Verification
186 Java tests pass. Original scenery native Paper first boot passes 158 assertions and second boot 167, including exact placement, loaded original rigs, damage/teleport/mount/equipment protection, private dialogue, audio, throttling, duplicate lifecycle handling and new runtime UUIDs after restart. The Boba/look follow-up passes 221 assertions on first boot and 230 on second boot, including actual native/rendered head and body tracking, nearest selection, return-facing and unchanged feet/vehicles. Production verification uses a separate read-only mode without fake actors or interaction mutations.

## Pause note
Nine placements are deployed. 109 read-only live assertions passed; the temporary probe is removed and normal server/Jabba restored. Next action: inspect models and dialogue in a real client.
