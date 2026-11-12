# Bantha Hide utility recipes

Player outcome: `/recipes` → Utilities exposes the existing native leather utility recipes with Bantha Hide ingredients.

The 26.1.2 native recipe data supplies the exact shapes, quantities and shaped/shapeless modes for books, item frames, bundles, all sixteen colored harnesses and four rabbit hides → one Bantha Hide. These twenty registrations replace their corresponding native keys; there is one recipe per route. Existing saddle registration remains Utilities with its existing agrinium cost. Bantha Hide retains native LEATHER material matching and its persistent catalog identity. Harness recoloring remains native.

Desert Robes stay in Armor. Ordinary leather armor and leather horse armor retain the existing vanilla armor crafting restriction; no new gear recipes or policy bypasses are introduced. Indirect book/frame derivatives and armor dyeing are unchanged.

The patch adds acquisition hints for the new native ingredients. Only the recipe configuration is deployed; there are no gameplay class, resource-pack or world-registry changes. `scripts/build-bantha-utilities-overlay.py` overlays additions onto a SHA-pinned current JAR and runtime recipe config, preserving unrelated entries and existing recipes/sources. Never deploy a whole older progression build over current live features.

Verification: `scripts/build.sh` passes all 174 Java tests. An isolated Paper 26.1.2 run with current catalog definitions passes 199 assertions covering guide visibility, Utilities category action, detail selection, canonical hide ingredient previews, native crafting/results/counts/mirroring, missing costs, shapeless books, displaced native keys and retained armor/saddle decisions. The probe shuts down its disposable server; never install it in the ordinary runtime.

Deployment: staged, awaiting the coordinator's assigned restart window. Next action: deploy the recipe-only overlay against the fresh live hash while stopped, then verify normal startup and restore the previously active event.
