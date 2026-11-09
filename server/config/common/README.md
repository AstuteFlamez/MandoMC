# Shared server configuration
This directory contains reviewed overrides and content. Defaults live in `plugin/src/main/resources`; private/generated files and world/player data stay in the ignored runtime.

| Folder | Owner and deployment destination |
| --- | --- |
| `mandomc/` | MandoMC overrides, with the same feature paths as `plugins/MandoMC/` |
| `worldguard/` | Named region templates; merge into the matching `WorldGuard/worlds/<world>/regions.yml` |
| `weaponmechanics/` | Weapon/ammo/projectile definitions; keep WeaponMechanics' required runtime folders |
| `mythicmobs/` | Creature/skill definitions; keep MythicMobs' required runtime folders |
| `modelengine/blueprints/` | Models deployed into `ModelEngine/blueprints/` |
| `archive/` | Historical configuration for removed plugins; do not deploy |

MandoMC settings are grouped into `arrival/`, `combat/`, `events/`, `items/`, `mobs/`, `rules/`, `travel/`, `worlds/`, `admin/`, `vehicles/` and `language/`. Bukkit's required `config.yml` and `plugin.yml` retain their required root locations. Item definitions are in `items/definitions/`, separate from `items/catalog.yml` and `items/recipes.yml`. Runtime vehicle/restoration journals are under `data/`.

On upgrade, MandoMC moves existing root configs to their feature folders **before installing missing defaults**. Values and comments remain byte-for-byte unchanged. Identical duplicate files are consolidated. Differing old/new copies stop startup with both paths named, rather than overwrite either copy. The path map is bundled as `config-layout.properties`. Missing language keys use bundled fallbacks, preserving operator translations.

Stop the server before applying code/content. Back up existing settings, deploy the JAR and required blueprints, then restart to migrate MandoMC settings. Do not overwrite existing operator configs wholesale with this directory. Copy a reviewed override to its matching destination only when deliberately applying that setting. Preserve other WorldGuard regions when merging the SpawnWorld, survival landing and Hoth templates.

Static regions remain the protection authority; clan/claim integration is deferred. Exact world keys, aliases and arrival coordinates belong to `mandomc/worlds/worlds.yml`; preserve existing dimension data. Nether/End stay disabled. MandoMC alone owns starter kits and combat logout, and keeps the survival full-loot rule.

Paper sends the required published MandoMC resource pack. Keep WeaponMechanics' automatic pack delivery disabled. `force-gamemode=false` preserves returning players' game modes. Removed Essentials/Multiverse data is not a deployment source.

Jabba shares the same layout: `mandomc/mobs/bossk.yml`, `mandomc/mobs/rancor.yml`, `mandomc/mobs/drops/jabba.yml`, `mandomc/events/jabba.yml` and `mandomc/items/definitions/jabba-loot.yml`. Private gate panels and run checkpoints stay in runtime `MandoMC/jabba/`; they are not moved or published. Five existing dimensions remain enabled, including Tatooine. Its region template is intentionally empty.
