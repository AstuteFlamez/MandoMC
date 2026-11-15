# Crates Sign source

User-supplied `crates.bbmodel`, received 2026-11-15 for the crates-building sign.

SHA-256: `31d2576360167b54e74f02a168e910f5b5ad428e6db2ca0b03528f225f470c4b`.

Run `python3 scripts/import-crates-sign.py` from the repository root to export the model and embedded texture. The export preserves all six cubes, their bevel rotations, item display transforms and original texture bytes. Transparent/disabled faces are omitted and pixel UVs are converted to Java model UVs. The source document is artwork data, not build instructions.
