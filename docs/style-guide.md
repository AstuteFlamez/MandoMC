# MandoMC style guide
Scope: the new MandoMC project. This guide is the baseline and applies to newly authored or ported code. Do not reformat the entire legacy source.

## Java
- Four spaces; UTF-8; LF; aim for a 120-character line length.
- Types use PascalCase, methods/fields camelCase, constants UPPER_SNAKE_CASE, packages lowercase.
- Organize by gameplay responsibility and keep vendor APIs inside integrations.
- Use constructor injection for dependencies. Avoid mutable global state and static plugin singletons in gameplay code.
- Use records for immutable facts when useful. Prefer a straightforward class over an unnecessary manager/factory/interface hierarchy.
- Name services by responsibility: RaidEligibility, FactionAccess, CreditAccount; avoid broad EverythingManager classes.
- Return explicit outcomes for expected denials; include a reason that the caller can translate for the player.
- Do not swallow exceptions. Log actionable context once at the right boundary, without secrets or per-tick log spam.
- Comments explain decisions and constraints. Add Javadoc for contracts and subtle lifecycle/threading rules, not every obvious getter.

## Paper lifecycle and threads
- Access worlds, entities, inventories and other thread-confined server APIs on the appropriate server thread.
- Keep disk/database/network work off the tick loop. Snapshot necessary values first, then return to the server thread before touching Bukkit objects.
- Register and clean up every listener, task, entity and external resource owned by a feature.
- Handle disable, disconnect, death, world change and missing dependencies deliberately.
- Reload data only when the feature has a defined safe reload contract. Use restarts for code changes.

## Integrations and authority
- Depend on supported APIs and documented hooks; isolate unavoidable version-specific code.
- Compile vendor APIs as provided dependencies; do not bundle copies of other plugins.
- Store stable UUIDs/IDs, not display names. Avoid persisting vendor object instances.
- Each credit balance, faction membership, claim, event reward and vehicle has one authoritative writer.
- Preserve cancellation by protection systems. Never call direct damage/block mutation as a shortcut around a denied action.
- Preserve attacker attribution through projectiles, AoE and reflections; apply each effect once.
- Required dependencies fail clearly; disabled optional features must not cause classloading failures.
- Validate configuration at load time, with file/key/context in errors.

## Configuration and items
- YAML uses two spaces. IDs are lowercase snake_case; new MandoMC-owned configuration keys use kebab-case.
- Keep external plugin schemas as their authors require. Do not rename vendor keys for style consistency.
- Use stable namespaced persistent item IDs. Names, lore, model IDs and colors are presentation, not identity.
- Keep balance values, destinations, item references and language strings in configuration.
- Ship plugin defaults in plugin/src/main/resources. Store intentional server-specific overrides under server/config/common.
- Add schema versions and migrations when persisted meaning changes. Use safe, additive defaults where possible.

## Item tooltip standard
Accepted 2026-11-08; applies to weapons, ammo and future custom items.

- Item name, then the rarity/category badge row.
- One blank lore line before the gray description.
- Where an item has an action, use a compact yellow, non-italic control hint: `[<key:key.drop>] Reload` for blasters. Use the client keybinding component instead of hardcoding Q.
- Separate description, controls and ammunition requirements with one blank line when present. Omit sections that do not apply; ammo does not need a reload control.
- Keep the common/weapon/ammo image badges. Do not use oversized drop/reload images or an arrow between control badges.

## Player-facing language
- Use short, concrete messages: what happened and what the player can do next.
- Player-facing group identity must replace "Faction" and the `/f` command root. The final name/root is pending; use "player group" in planning until selected. Apply the chosen name consistently to commands, help, menus, messages and tutorial text. Preserve accurate vendor/API names and historical source references in technical documentation.
- Use Adventure components and MiniMessage for newly authored player messages where supported. Fixed developer-only bootstrap diagnostics may stay in code.
- Put messages in a language file. Do not scatter formatting codes through Java.
- Treat the existing resource pack as the visual source. Reuse its assets before requesting new art.
- No final color palette or visual redesign is implied by this engineering style guide.

## Validation
Automate formatting and build checks during bootstrap. Until a formatter is configured, do not claim style is machine-enforced.
Prefer tests for rules and failure paths. Use in-server checks for event ordering, resource-pack appearance, vehicle motion and multi-plugin behavior.

## Inventory menus
- All player-facing custom inventory menus, including command menus, fill unused slots with gray stained glass panes named exactly one space and hidden tooltip. Use `MenuItems.fill` for this shared background; controls and content replace the panes.
- Recipe grid slots are the exception: unused positions must remain actually empty (air), including unused rows and columns of the 3x3 grid. Do not put filler panes or labels in these cells.
- Filler panes are decorative and cannot be taken. Preserve cancellation of click/shift/hotbar/drag transfer paths.

## Hostile mob attack facing
Every hostile mob must face its current living target before its attack animation starts. Keep native body/head rotation and the ModelEngine model aligned, and track the target through the wind-up. Scripted attacks use the shared MandoFaceTarget skill with a short pre-turn before the pose; native melee uses MandoFaceTargetNow before its attack state. Preserve animation-relative hit/projectile cues when adding the pre-turn. Bossk's existing tracked pre-turn uses the same model-facing boundary. Do not turn a caster toward its own projectile/particle origins. Include this behavior when importing future hostile mobs, register their type in HostileFacing, and verify their visible model at attack onset.
