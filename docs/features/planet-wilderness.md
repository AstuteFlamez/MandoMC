# Planet wilderness direction
Status: Survival removal playable; planet zoning waiting for user boundaries.
Branch: bilal/planet-wilderness

## Player experience
Batuu remains the protected spawn hub. Public travel offers Batuu, Tatooine and Hoth.
There is no standalone Survival/Earth destination, /survival command or tutorial link.
Tatooine and Hoth will each contain safe zones, warzones and claimable wilderness.
The user will supply the boundaries; do not invent zone dimensions or apply new regions yet.
Claims remain owned by FactionsUUID and require the deferred clan/territory integration.

## Current slice
Remove Survival/Earth from warp resolution, completion, menu entries and command registration.
Allow arrival validation with Batuu only. Keep the main overworld as internal Paper data;
do not delete/reset worlds, inventories, claims or progression. Existing Hoth/Tatooine regions
and event behavior are unchanged until boundaries are supplied. Elarion import is cancelled.

## Deferred content, approved direction (2026-11-09)
- Hoth: tauntauns, wampas and krykna.
- Add a Mandalore map with Mandalorian warriors.
- Add a Mandalore beskar-zone event with regenerating ore; bounds, cadence, rewards,
  reset behavior and claim/protection interactions remain to be designed.
- Possible enemies: snowtroopers, sandtroopers and stormtroopers.
These are planning notes only; do not implement or activate this content now.

## Verification
167 Java tests pass, including rejection of removed warp aliases and spawn-only settings.
23 live Paper checks pass: actual Galaxy Map has only Batuu/Hoth/Tatooine, removed aliases and /survival are unavailable, Batuu validates independently, and all five existing dimensions stay loaded. Temporary probe is removed after verification. Resource pack and every unrelated deployed JAR entry are byte-identical to the deployment baseline.

## Next action
Receive the user's safe-zone and warzone boundaries for Tatooine and Hoth.
