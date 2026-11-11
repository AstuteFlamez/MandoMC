# E-11 and ammunition crafting
Status: implementation; native verification in progress.
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
Java tests cover exact charge thresholds/reservations, exhaustion/recovery, recipe identity resolution, mirrored-pattern ambiguity, and complete survival dependency routes. Disposable Paper probe verifies all five native outputs, quantities, mirrored crafting, missing ingredients, lookalike rejection, guide visibility, genuine vendor weapon/ammo identity, and the deployed 51-charge settings. Deployment must preserve every unrelated entry of the current live JAR and merge only the new recipes/cost into current runtime configuration.

## Pause note
Await native verification, scoped deployment and source publication. Next action: finish the disposable Paper check.
