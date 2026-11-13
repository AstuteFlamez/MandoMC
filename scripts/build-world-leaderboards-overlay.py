#!/usr/bin/env python3
"""Add native read-only leaderboard views to an exact integrated baseline."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-root', type=Path, required=True)
p.add_argument('--baseline', type=Path, required=True)
p.add_argument('--built', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args(); a.output.mkdir(parents=True, exist_ok=True)
hub = 'net/mandomc/commands/CommandBindings.class'
with zipfile.ZipFile(a.baseline) as old, zipfile.ZipFile(a.built) as built:
    data = {n: old.read(n) for n in old.namelist()}; new = data.copy()
    assert b'net/mandomc/gameplay/leaderboards/LeaderboardFeature' not in data[hub], 'Lifecycle already installed'
    for n in built.namelist():
        if n.startswith('net/mandomc/gameplay/leaderboards/') or n == 'leaderboards/world.yml':
            assert n not in data, 'Existing leaderboard owner; review instead of replacing'
            new[n] = built.read(n)
    src = a.output / 'CommandBindings-before.class'; dst = a.output / 'CommandBindings-after.class'; src.write_bytes(data[hub])
    java = a.server_root / '.local/jdks/jdk-25.0.5+8/Contents/Home/bin/java'
    libs = [a.baseline, a.built, *list((a.server_root / 'server/runtime/libraries').rglob('*.jar'))]
    subprocess.run([str(java), '-cp', ':'.join(map(str, libs)), str(Path(__file__).with_name('LeaderboardLifecycleOverlay.java')), str(src), str(dst)], check=True)
    new[hub] = dst.read_bytes()
    candidate = a.output / 'candidate.jar'
    with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as out:
        for n, value in sorted(new.items()): out.writestr(n, value)
    assert all(new[n] == value for n, value in data.items() if n != hub)
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    report = {'baseline-sha256': sha(a.baseline), 'candidate-sha256': sha(candidate),
              'changed-entries': [n for n in new if data.get(n) != new[n]], 'all-unrelated-entries-identical': True}
    candidate.with_suffix('.json').write_text(json.dumps(report, indent=2)); print(json.dumps(report, indent=2))
