# Feature: Spawn travel
Status: implemented and deployed locally; six unit tests and 53 integration assertions pass; client visual/audio playtest pending
Branch: bilal/spawn-teleport (based on the unmerged arrival branch)

## Player experience
`/spawn` returns to SpawnWorld. `/survival` travels to SurvivalWorld. Both display a configurable three-second colored countdown with rising chimes. Looking around is allowed; position changes cancel with an explicit message. Successful travel shows a short title, teleport sound and double-helix particles.

## Smallest playable version
Two registered routes share one teleport lifecycle, destination validation and presentation. WorldGuard continues to own protected destinations. Existing map data is preserved; the old dev_world now has the Multiverse alias SurvivalWorld.

## Rules and parameters
See [command architecture and lifecycle](../commands.md). Timers, sound keys, volume and spiral duration are configured in teleport.yml; text/color/title templates are in messages.yml. Combat blocks these voluntary teleports. Full combat/logout mechanics and the future tutorial library remain deferred.

## Verification
Build and movement tests cover looking around, coordinate noise and actual movement. The temporary Paper probe checks both route destinations, command ownership, protection, countdown titles/sounds, duplicate requests, movement/logout/mount/world-change cancellation, combat refusal, stale destinations and cancelled teleports. These are server/API checks with simulated players; the final visual/audio feel requires an actual Minecraft client.

## Next action
Try `/spawn` and `/survival` while turning the camera, then while walking, and tune the visual/audio timing based on that client playtest.
