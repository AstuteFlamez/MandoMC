# Feature: JustTimm lightsaber catalog

## Player experience
All 78 distinct named sabers from the pinned JustTimm Lightsaber Workshop 3.2.2 pack appear in Weapons in `/mmc items`. Give one with `/mmc give <id>`; `/mmc give vader` retains its existing identity. Full names, IDs, rarity decisions and upstream references are recorded in `resource-pack/sabers.json`. Character aliases that share the same artwork are one item; optional recolors, animation intermediate frames and template helpers are excluded by the user's selection. Crafting recipes remain deferred.

The five previously recovered IDs (`obiwan`, `cal_survivor_crossguard`, `windu`, `quigon`, `ventress_yellow`) are retained and moved into the family definition without duplicate items. Their existing recipes are preserved; no new recipes are added. Titles explicitly disable bold. Rarity reflects the user's character-coolness preference: two common training sabers, five uncommon, eighteen rare, twenty-seven epic and twenty-six legendary variants. Vader is legendary alongside Ahsoka, Anakin, Obi-Wan, Luke, Maul, Yoda, Mace, Revan and the Darksaber. Rarity is presentation only; all new sabers use the accepted 10.5 melee damage, 1.0 attack speed and shared Vader behavior.

## Rules and ownership
The existing SaberService owns every explicitly configured catalog identity. No ordinary shield is admitted by its name/model. Blocking, reflection, tired-action gates, shared player-owned charge, throw reservation/recovery/death cleanup and protected-area rules all apply to the full family. Switching variants cannot refill charge. Each thrown item preserves its own model and metadata; join/death normalization uses that variant rather than converting it to Vader. Joining refreshes default titles and the rarity badge, preserving player renames, other lore and metadata.

Throw render offsets and collision lengths derive from each upstream fixed-context model. Short blades retain their shorter reach and double blades retain their longer span; damage and throw tuning stay shared. A double blade uses one axial collider including its central hilt, consistent with the existing continuous-contact rule. Decorative crossguards use the shared hit thickness, not additional side colliders.

## Configuration and delivery
`items/definitions/sabers.yml` owns names, attributes and rarity. `combat/sabers.yml` adds the explicit `variants` map of catalog ID, item model, fixed-context blade center and half length. Existing configs without the map continue accepting only Vader; deploy the bundled catalog and variant map together. The original top-level Vader keys are retained for compatibility. The bundled items/catalog.yml adds epic and legendary badges; the existing bootstrap migration installs missing badge keys.

The MandoMC pack contains only item dispatch wrappers and the recovered MandoPack legendary badge. No JustTimm models or textures are extracted into it. Official, unmodified CDN pack delivery and its checksum stay unchanged. All 231 external model references are explicitly allowlisted and verified against the pinned archive. `scripts/content/import-sabers.py` regenerates the catalog, wrappers, numeric bounds and reference manifest from a checksum-verified local upstream ZIP without copying artwork. Credits remain in the distributed pack.

## Verification
140 combined Java tests and 21 script/pack tests pass. Coverage includes catalog parity, model identity, malformed geometry rejection, legacy settings fallback, transformed render/collision centers and blade lengths. All 231 model references resolve in the pinned official ZIP. A live Paper probe checks each variant's real catalog item, explicit non-bold title, rarity badge, block eligibility, throw/return ownership and join normalization, plus charge sharing and the existing full saber regression checks. All 791 live assertions pass, including old bold-title migration. Normal startup and catalog reload are clean, with the temporary probe removed. Deployment is recorded in docs/status.md. Rendered appearance remains a matching-client playtest.

## Next action
Reconnect for the pack, open Weapons in `/mmc items`, and client-check short, double, crossguard and standard sabers before choosing recipes.

## Concurrent deployment baseline

The live development server has changes owned by other feature checkouts. `scripts/build-saber-overlay.py` builds a reproducible saber-only release from a SHA-256-verified deployed JAR plus the tested build. It replaces only saber classes and the three affected bundled definitions, and asserts that all other baseline entries are byte-identical. This avoids reverting or prematurely enabling unrelated ongoing changes. Normal source integration and full builds remain the long-term workflow.
