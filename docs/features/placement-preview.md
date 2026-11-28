# Oversized block placement

Hold a Rhydonium Barrel and aim at a block within reach. An upright blue barrel shows where it will stand. Its orange front panel faces the placer, snapping to the nearest cardinal direction. A red barrel means that space is occupied or protected. Right-click a blue site to place one barrel. Break either half to recover the original item, including its fuel state.

The barrel occupies the two cells above the clicked block and uses the archive's empty, quarter, half, three-quarter and full thresholds. Preview and permanent display use the same placed-model key and grid pose. Preview materials inherit each model and replace its texture with a tinted, translucent version.

The placement journal owns inventory transfers, reservations, collision blocks, recovery and permanent displays. Each previewable block supplies a mounting rule, dimensions and placed-model resolver through `PreviewBlock`. The preview reads each rotated footprint cell using the same checks as the placement click. The click also runs native placement events before consuming the item. Paper reveals the transient item display to its owner alone.

To register another model, add its profile to `PreviewBlock.TYPES`. Run `scripts/build-placement-preview-assets.py` with Python and the namespaced placed-item keys to generate both tint variants. Each variant inherits the placed model's geometry and UVs. The two-cell barrel fits in one chunk column. Wider profiles are checked on both sides of a chunk boundary.
