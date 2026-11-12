# Helmet inventory orientation

Player outcome: Beskar, Durasteel and Plastoid helmet inventory icons use an upright head-style three-quarter view. The flat robe hood faces straight ahead.

Only each model's `display.gui` changes. The three 3D models use `[30,-135,0]` in their native north-facing geometry basis, equivalent to the vanilla skull GUI view after its special-renderer basis flip. Scale remains 0.95. All four translations are centered at zero. No shared parent, geometry, UV, texture, head/equipment, hand, shelf or ground transform changes.

The overlay builder accepts an explicit deployed ZIP and SHA-256, preserves its entry set and all unrelated bytes, parses every JSON document, and replaces only these four GUI objects. It can therefore be rebuilt against a later pack without reverting concurrent Boba equipment, credit glyphs, TROPHY or saddle work. Do not publish a complete source-tree build from this branch: its unrelated assets lag the live pack.

Verification: source pack build validates JSON and local references; live-baseline overlay preserves 1174/1178 entries byte for byte; non-GUI model equality checked. The offline actual geometry/UV contact sheet includes before/after, 16px views, robe hood and vanilla Steve-head reference. Corrected projected model bounds fit a 16-unit slot. Minecraft client appearance still awaits playtesting.

Published and applied through one normal restart after direct user approval. Anonymous hosted bytes and actual client pack load are verified. All unrelated pack entries remain unchanged. The subsequent dropped-ground-size request is tracked separately in helmet-ground-size.md.
