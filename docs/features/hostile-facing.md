# Shared hostile attack facing
Status: deployed; live native/model facing checks pass.

The user requires Boba and every hostile mob to face the player before attacking. All currently shipped hostile types are covered: Boba Fett, Bossk, Rancor, Gamorrean, Quarren, Twi'lek, ranged/melee Tusken and Massiff. Passive Banthas and Dewbacks are unaffected.

ModelFacing owns synchronized native and visible body/head rotation, advancing at most 30 degrees per tracking update. Bossk's existing face implementation delegates to this same boundary. Console-only UUID commands reject dead/stale actors, other worlds, targets beyond 64 blocks and unrecognized mob types; Mythic remains the owner of targets and repeat timing.

Scripted attacks get a six-tick pre-turn before their attack clip, then continue following the target through the original wind-up. Original animation-relative projectile/hit delays are retained. Quarren/Twi'lek and ranged Tusken plant for 52 ticks, covering the six-tick turn plus the original 46-tick pose; the plant aura no longer fights explicit facing. Native melee faces its victim immediately before the attack state so contact timing remains native. Bossk's existing six-tick pre-turn and damage/shot timings are preserved.

Boba's flamethrower no longer turns him toward its own projectile origins. It faces the opponent during wind-up and the launch sequence, retaining original flame effects and damage.

Deploy the shared hostile-facing.yml skills and updated existing mob/skill files with the plugin through a normal restart. No blueprint or resource-pack change is needed. Future hostile imports must satisfy the rule recorded in docs/style-guide.md.

## Verification
All 86 Java tests pass. The isolated Paper facing probe passes 92 assertions across 13 attack cases and eight hostile types: no instant pre-animation half-turn, no attack clip before the pre-turn, correct native/rendered body orientation at pose onset, tracking after the target switches sides, and ranged plant-aura expiry. Boba’s separate 43-assertion regression also passes with all original gun, jump, dodge, flame, drop and warp checks. Native yaw can be held separately by a Mythic stun; the verification reads actual native body rotation and visible model rotation. Temporary test entities/tickets/platform are cleaned up. Pack assets are unchanged.

Rancor was added to the shared facing allowlist with its recovery; its swing/stomp/slam clips use the same six-tick pre-turn and wind-up tracking.
