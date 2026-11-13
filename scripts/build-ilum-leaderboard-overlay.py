#!/usr/bin/env python3
"""Change only Ilum leaderboard background methods in the current combined deployment."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-root', type=Path, required=True)
p.add_argument('--baseline', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
name = 'net/mandomc/gameplay/ilum/ParkourService.class'
original = a.output / 'ParkourService.class'
patched = a.output / 'patched' / name
with zipfile.ZipFile(a.baseline) as src:
    original.write_bytes(src.read(name))
java = a.server_root / '.local/jdks/jdk-25.0.5+8/Contents/Home/bin/java'
libs = [a.baseline, *list((a.server_root / 'server/runtime/libraries').rglob('*.jar'))]
subprocess.run([str(java), '-cp', ':'.join(map(str, libs)),
                str(Path(__file__).with_name('PatchIlumLeaderboard.java')), str(original), str(patched)], check=True)
candidate = a.output / 'candidate.jar'
with zipfile.ZipFile(a.baseline) as src, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as dst:
    for item in src.infolist():
        dst.writestr(item, patched.read_bytes() if item.filename == name else src.read(item.filename))
with zipfile.ZipFile(a.baseline) as src, zipfile.ZipFile(candidate) as dst:
    changed = [n for n in src.namelist() if src.read(n) != dst.read(n)]
    assert changed == [name] and set(src.namelist()) == set(dst.namelist())
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
report = {'baseline-sha256': sha(a.baseline), 'candidate-sha256': sha(candidate),
          'changed-entries': changed, 'all-unrelated-entries-identical': True}
candidate.with_suffix('.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
