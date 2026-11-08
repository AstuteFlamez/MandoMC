#!/usr/bin/env python3
"""Import the pinned ModelEngine generator's model assets into the required MandoMC pack."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('pack', type=Path, help='ModelEngine generated resource pack directory')
args = parser.parse_args()
source = args.pack / 'assets/modelengine'
expected = {'ranged_raider', 'melee_raider', 'bantha', 'dewback', 'massiff'}
actual = {p.name for p in (source / 'items').iterdir() if p.is_dir()}
if not expected <= actual or actual - expected - {'internal_fire'}:
    raise SystemExit(f'Expected only the five sandy models and optional internal_fire; got {sorted(actual)}')
destination = ROOT / 'resource-pack/src/assets/modelengine'
# No vanilla shader overrides are required for these rigid models (none uses player limbs).
shutil.copytree(source, destination, dirs_exist_ok=True)
atlas = ROOT / 'resource-pack/src/assets/minecraft/atlases/blocks.json'
atlas.parent.mkdir(parents=True, exist_ok=True)
data = json.loads(atlas.read_text()) if atlas.exists() else {'sources': []}
entry = {'type': 'minecraft:directory', 'source': 'entity/models', 'prefix': 'entity/models/'}
if entry not in data['sources']:
    data['sources'].append(entry)
atlas.write_text(json.dumps(data, indent=2) + '\n')
manifest = ROOT / 'resource-pack/mob-models.json'
files = sorted(p for p in destination.rglob('*') if p.is_file())
# Keep generated JSON byte-identical to the LF-normalized files stored by Git.
for path in files:
    if path.suffix == '.json':
        path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
manifest.write_text(json.dumps({'generator': 'ModelEngine R4.1.1', 'models': sorted(expected),
    'files': [{'path': str(p.relative_to(ROOT / 'resource-pack/src')), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]}, indent=2) + '\n')
print(f'Imported {len(files)} model files for {len(expected)} mob models.')
