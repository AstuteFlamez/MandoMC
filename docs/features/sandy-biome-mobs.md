# Feature: Tatooine mobs in survival sandy biomes
Status: todo — accepted direction, not implemented

## Player experience
Encounter the existing Tatooine creature/mob models in sandy biomes of SurvivalWorld, without needing a separate Tatooine map. Candidate roster from the user's existing assets: Tusken Raiders, banthas, dewbacks and massiffs (called “mantiffs” in the request).

## Smallest playable version
Recover one hostile mob with a working blaster attack and its model/animation dependencies. Verify damage, region protection and combat tagging. This unlocks blocking/reflection work and, afterward, a substantial saber throw stamina cost.

Then add natural sandy-biome spawns for the roster. Use MythicMobs for definitions/AI and ModelEngine for rendering. Keep SpawnWorld and protected landing areas excluded.

## Decisions for the next brief
Confirm which mob fires the first blaster; inspect existing assets before choosing. Select exact sandy biomes, spawn density/caps, day/night behavior, hostile versus passive roles, health/damage and drops. No spawn rates, taming or mounts are implied by the current request. No new resource planet or Tatooine location is required.

## Verification
Future checks: model/animations, blaster attribution and protection, spawn caps and biome boundaries, no spawn in protected areas, cleanup/restart behavior and client combat feel.
