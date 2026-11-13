#!/usr/bin/env python3
"""Apply only helmet display.gui to an explicitly pinned deployed pack."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
NAMES = [f'assets/mandomc/models/item/armor/{name}_helmet.json'
         for name in ('beskar', 'durasteel', 'plastoid', 'robes')]


def build(baseline, expected_sha256, output):
    assert hashlib.sha256(baseline.read_bytes()).hexdigest() == expected_sha256, 'Baseline CAS mismatch'
    with zipfile.ZipFile(baseline) as source:
        before = {n: source.read(n) for n in source.namelist()}
    after = dict(before)
    for name in NAMES:
        model = json.loads(before[name])
        wanted = json.loads((ROOT / 'resource-pack/src' / name).read_text())
        original = copy.deepcopy(model)
        model.setdefault('display', {})['gui'] = wanted['display']['gui']
        check = copy.deepcopy(model)
        check['display']['gui'] = original['display']['gui']
        assert check == original, f'Non-GUI mutation: {name}'
        after[name] = (json.dumps(model, indent=2) + '\n').encode()
    # Strict JSON parse for every existing and changed document.
    for name, data in after.items():
        if name.endswith('.json') or name == 'pack.mcmeta':
            json.loads(data)
    changed = [n for n in before if before[n] != after[n]]
    assert set(changed) <= set(NAMES)
    assert set(before) == set(after)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name in sorted(after):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(info, after[name])
    data = output.read_bytes()
    report = dict(baselineSha256=expected_sha256, files=len(after), bytes=len(data),
                  sha1=hashlib.sha1(data).hexdigest(), sha256=hashlib.sha256(data).hexdigest(),
                  changed=changed, unchanged=len(after)-len(changed))
    output.with_suffix('.validation.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--expected-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build(args.baseline, args.expected_sha256, args.output)
