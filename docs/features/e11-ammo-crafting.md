# E-11 and ammunition crafting
Status: deployed and pushed; client crafting walkthrough is the next action.
Branch: bilal/e11-ammo-recipes

## Player experience
Players can replace an E-11 and supply blasters/vehicles using ordinary survival resources. All recipes appear in `/recipes`, with ingredient navigation to the existing sheets, electronics and metal routes. User selected craftable Tibanna vials rather than a loot/trader-only source.

## Rules and smallest playable version
Five shaped crafting-table recipes, with no new item identities or artwork:
- E-11: `DB ` / `PCE` / ` D ` -> 1. D = Durasteel Sheet, B = Barrel Liner, P = Plastoid, C = Circuit Board, E = Energy Conduits.
- Standard Plasma Cells: ` A ` / `CEC` -> 32. A = Agrinium Sheet, C = ordinary coal, E = Electrum.
- Tibanna Gas Vial: ` E ` / `CBC` / ` P ` -> 1. E = Energy Conduits, C = ordinary coal, B = glass bottle, P = Plastoid. This is a new synthetic survival crafting route, not recovered lore or real chemistry.
- Tibanna Gas Cells: ` A ` / `EVE` -> 32. A = Agrinium Sheet, E = Electrum, V = Tibanna Gas Vial.
- Proton Torpedo: ` D ` / `GPG` / `DWD` -> 4. D = Durasteel Sheet, G = gunpowder, P = Plasma Capacitor Array, W = Wiring Coil.

Native crafting consumes ingredients and allows mirrored shapes; exact custom identities reject plain-material substitutes. Tibanna vials are consumed as sealed components. All dependencies terminate in obtainable native resources or the four established scrap families. Other blaster recipes stay deferred.

## Ownership and integrations
MandoMC owns recipe definitions and the guide. WeaponMechanics generates genuine E-11 and Standard Plasma Cells templates and continues to own magazines, reloads and shots. Existing native Tibanna/proton identities remain the vehicle cannon inputs. Vendor-output recipes wait up to 200 ticks for asynchronous WeaponMechanics template loading, then register atomically on the server thread. Failures log explicitly; shutdown cancels pending registration and removes only owned recipes.

Sabers are accepted as done. Their sole requested balance adjustment is `throw-cost-percent: 51.0` on the 100-charge bar, for both activation and consumption. Drain/recovery timing, motion and other combat tuning remain as accepted. Fractional drain clamps exhaustion within floating-point tolerance so exactly 51 charge behaves correctly.

## Verification
Java tests cover exact charge thresholds/reservations, exhaustion/recovery, recipe identity resolution, mirrored-pattern ambiguity, and complete survival dependency routes. Disposable Paper probe verifies all five native outputs, quantities, mirrored crafting, missing ingredients, lookalike rejection, guide visibility, genuine vendor weapon/ammo identity, and the deployed 51-charge settings. All 166 Java tests and 74 native assertions passed. Deployed at 18:12 local on November 10 after the coordinated Jawa handoff; all unrelated entries of its final JAR are byte-identical, with only the scoped crafting/charge changes and two merged config resources replaced/added. Normal startup registered both vendor recipes; pack unchanged, no test plugin installed, queued KOTH restored and stopped-runtime backup retained. Source commit `33e420d` is pushed.

## Pause note
Work is deployed; integrate `bilal/e11-ammo-recipes` before future full builds to retain the changes. Next action: open `/recipes e11` and craft an E-11 plus the three ammo types in the client.

## Ingredient acquisition help
November 10 follow-up: every currently visible ingredient without a guide recipe has an explicit source description. Clicking it explains gathering, native crafting/smelting or stocked brown Jawa supplies instead of the generic no-recipe response. Added/completed 29 native descriptions, including glass bottles, gunpowder, amethyst, dyes, utility blocks and soul torches. Ordinary-material restrictions and all actual acquisition rules are unchanged. A coverage test checks every visible recipe/smelting/stonecutting dependency and rejects missing/unobtainable source notes at acquisition leaves. All 167 Java tests pass. The source-only config update is deployed, with startup and all 29 actual runtime descriptions verified. Restart window released.

## Ionized cells for the Jawa blaster
New user follow-up: retain catalog `ionized_energy_cells` and vendor `Ionized_Energy_Cells`. The shaped recipe is ` A ` / `EWE` -> 32 cells, with A = Agrinium Sheet, E = Electrum, W = Wiring Coil. This matches the 32-cell standard/Tibanna batches and uses more conductors than standard cells, without requiring unavailable ionized-focusing-lens loot. All dependencies already have recipes or acquisition instructions in `/recipes`.

Only the Jawa blaster's `Reload.Ammo.Out_Of_Ammo_Mechanics` message becomes “Out of ionized cells!” The existing display name, IDs, 18-round magazine, reload timing, damage and ammo-consumption mechanics are unchanged. Source configuration retains the existing vendor ammo definition. All 167 Java tests and 23 isolated native assertions pass: recipe visibility/output/mirroring, missing and counterfeit ingredients, exact vendor identity, native consumption of 18 cells leaving 14, and captured chat packets from empty reload and actual dry-fire. Deployed after Ilum's completed warp/hologram update: normal startup registered three vendor recipes, Jabba was restored, the pack is unchanged and a full stopped-runtime backup is retained. All unrelated JAR entries and parsed weapon settings are preserved. Restart window released. Ready to test with `/recipes ionized_energy_cells` and an empty Jawa blaster.
