#!/usr/bin/env python3
"""Apply verified herd models to the exact published pack; preserve all unrelated current assets."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline', type=Path, required=True); p.add_argument('--manifest', type=Path, required=True)
p.add_argument('--output', type=Path, required=True); a = p.parse_args()
root = Path(__file__).resolve().parents[1]
base = json.loads(a.manifest.read_text()); raw = a.baseline.read_bytes()
assert hashlib.sha256(raw).hexdigest() == base['sha256']
with zipfile.ZipFile(a.baseline) as z:
    old = {name: z.read(name) for name in z.namelist()}
entries = old.copy(); allowed = set()
for kind in ['items', 'models']:
    for species in ['bantha', 'dewback']:
        for path in (root / f'resource-pack/src/assets/modelengine/{kind}/{species}').rglob('*'):
            if path.is_file():
                name = path.relative_to(root / 'resource-pack/src').as_posix()
                entries[name] = path.read_bytes(); allowed.add(name)
name = 'assets/modelengine/textures/entity/models/herd_saddle.png'
entries[name] = (root / 'resource-pack/src' / name).read_bytes(); allowed.add(name)
changed = sorted(name for name in old if entries[name] != old[name]); added = sorted(set(entries)-set(old))
assert set(changed) | set(added) <= allowed
assert all(entries[name] == data for name, data in old.items() if name not in allowed)
assert all(f'assets/modelengine/{kind}/{species}/saddle.json' in entries
           for kind in ['items', 'models'] for species in ['bantha', 'dewback'])
for name in allowed:
    if name.endswith('.json'): json.loads(entries[name])
atlas = json.loads(entries['assets/minecraft/atlases/blocks.json'])
assert any(source.get('type') == 'minecraft:directory' and source.get('source') == 'entity/models'
           for source in atlas['sources']), 'Current atlas must already cover ModelEngine entity textures'
a.output.mkdir(parents=True, exist_ok=True); archive = a.output / 'MandoMC.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for name, data in sorted(entries.items()):
        info = zipfile.ZipInfo(name, date_time=(2026, 11, 12, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, data)
raw = archive.read_bytes(); digest = hashlib.sha256(raw).hexdigest()
report = {**base, 'files': len(entries), 'bytes': len(raw), 'sha1': hashlib.sha1(raw).hexdigest(), 'sha256': digest,
          'sourceBranch': 'bilal/bantha-dewback-babies', 'sourceWorkingTree': True, 'sourcePublished': False,
          'sourceCommit': subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip(),
          'tagBasis': 'pack-sha256', 'baselineUrl': base['url'], 'changedEntries': changed, 'addedEntries': added,
          'unrelatedEntriesByteIdentical': len(old)-len(changed),
          'changes': 'Add separate Bantha/Dewback saddle bones and driver locators; retain all original cube UVs and keyframes. All unrelated current assets, fonts, trophy and scenery are byte-identical.',
          'url': f'https://github.com/AstuteFlamez/MandoMC/releases/download/resource-pack-26.1.2-{digest[:12]}/MandoMC.zip'}
(a.output / 'MandoMC.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
