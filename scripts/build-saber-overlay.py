#!/usr/bin/env python3
"""Apply the compiled saber package/defaults to an immutable deployed baseline.

Used while concurrent feature checkouts own newer deployed code. All unrelated
classes and resources remain byte-identical to the baseline. Full project builds
remain available through scripts/build.sh after the source branches are integrated.
"""
import argparse
import hashlib
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline', type=Path, required=True)
p.add_argument('--expected-sha256', required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
assert hashlib.sha256(a.baseline.read_bytes()).hexdigest() == a.expected_sha256, 'Baseline changed'
resources = {'combat/sabers.yml', 'items/definitions/sabers.yml', 'items/definitions/legacy.yml'}
with zipfile.ZipFile(a.baseline) as original, zipfile.ZipFile(root / 'plugin/target/MandoMC.jar') as compiled:
    replacements = {n for n in compiled.namelist() if n.startswith('net/mandomc/gameplay/saber/') and n.endswith('.class')} | resources
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.output, 'w', zipfile.ZIP_DEFLATED) as output:
        for name in original.namelist():
            if name not in replacements:
                output.writestr(name, original.read(name))
        for name in sorted(replacements):
            output.writestr(name, compiled.read(name))
with zipfile.ZipFile(a.baseline) as original, zipfile.ZipFile(a.output) as output:
    assert all(output.read(n) == original.read(n) for n in original.namelist() if n not in replacements)
print(f'Built verified saber overlay: {a.output} SHA-256 {hashlib.sha256(a.output.read_bytes()).hexdigest()}')
