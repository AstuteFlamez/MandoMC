# Dropped helmet size

The requested follow-up increases only the dropped-ground size of Beskar and Plastoid helmets to match the existing Durasteel helmet. GUI views, worn/head, hand, fixed, shelf, decoration floor/wall/ceiling poses, original geometry, textures and palette remain unchanged.

Durasteel has no ground override, so its effective ground scale is 1. Beskar has exactly the same 10x9x8-unit mesh bounds and currently uses 0.5; its new ground scale is 1.0. Plastoid's muzzle/chin make its mesh larger: 10.02x9.1612x9.3194 units, including all original element rotations. Its new uniform ground scale is 0.9414, up from 0.5.

The Plastoid factor matches the geometric mean projected bounding-box area of Durasteel over 48 views: 16 spin headings at 20, 35 and 50 degrees of downward viewing pitch. Its resulting area ratio is 0.999934. Uniform scaling preserves the model's shape while matching its overall visual size across the rotating dropped-item views. The preview uses identical camera/framing for all three original textured models. Measurements and preview are retained under docs/assets/helmet-ground-*.

The staged overlay changes only two existing ground.scale arrays in the current approved 1178-entry pack; all 1176 other entries and every other model property are byte-identical. Source JSON/model/texture reference validation passes. No runtime code or custody behavior changes, and the existing ItemDisplay NONE decoration poses do not use the ground context.

Published after direct human approval and applied through one normal pack-only restart in the assigned window after the five-NPC release. Anonymous hosted bytes, actual server pack settings and actual client pack load are verified. The five-NPC JAR and all 80 protected runtime files, native agent and launchers remain byte-identical. Captured KOTH/None state was restored using supported controls; two stable seven-gray model snapshots and normal decor/Massiff startup messages were verified with no startup errors or test actors. The server window was explicitly released. Next action: client-playtest dropped Beskar and Plastoid beside Durasteel.
