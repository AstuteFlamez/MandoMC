#!/usr/bin/env python3
"""Import only the two generated herd model families and their new saddle texture."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__); p.add_argument('generated', type=Path); a = p.parse_args()
source = a.generated / 'assets/modelengine'
target = root / 'resource-pack/src/assets/modelengine'
for kind in ['items', 'models']:
    for species in ['bantha', 'dewback']:
        src = source / kind / species
        dst = target / kind / species
        assert (src / 'saddle.json').is_file(), f'Missing generated {species} saddle {kind}'
        previous = {path.relative_to(dst) for path in dst.rglob('*') if path.is_file()}
        generated = {path.relative_to(src) for path in src.rglob('*') if path.is_file()}
        assert previous <= generated, f'Generator unexpectedly removed existing {species} bones'
        shutil.copytree(src, dst, dirs_exist_ok=True)
        for path in dst.rglob('*.json'):
            path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
texture = Path('textures/entity/models/herd_saddle.png')
(target / texture).parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(source / texture, target / texture)
manifest = root / 'resource-pack/mob-models.json'
data = json.loads(manifest.read_text())
data['files'] = [{'path': str(path.relative_to(root / 'resource-pack/src')),
                  'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                 for path in sorted(target.rglob('*')) if path.is_file()]
manifest.write_text(json.dumps(data, indent=2) + '\n')
print('Imported Bantha/Dewback model families and the reused brown saddle atlas only.')
