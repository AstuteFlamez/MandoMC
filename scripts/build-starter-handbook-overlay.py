#!/usr/bin/env python3
"""Add the starter handbook while preserving every unrelated deployed JAR entry."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


parser = argparse.ArgumentParser(description=__doc__)
for arg in ('baseline', 'built', 'output'):
    parser.add_argument('--' + arg, type=Path, required=True)
parser.add_argument('--expected-sha256', required=True)
args = parser.parse_args()
assert sha(args.baseline.read_bytes()) == args.expected_sha256, 'Baseline changed'
entry = 'net/mandomc/gameplay/arrival/StarterKit.class'
with zipfile.ZipFile(args.baseline) as old, zipfile.ZipFile(args.built) as built:
    before = {name: old.read(name) for name in old.namelist()}
    assert sha(before[entry]) == 'e5b8e6b196245a48f6b4e87068f6abede4bca5aedc93c0e197e795a7f8e56233', 'StarterKit changed; merge first'
    for name in ('net/mandomc/gameplay/tutorial/TutorialHandbook.class',
                 'net/mandomc/gameplay/arrival/StarterKit$Entry.class', 'language/handbook.yml'):
        assert before[name] == built.read(name), 'Book/kit dependency changed: ' + name
    after = dict(before)
    after[entry] = built.read(entry)
    assert before[entry] != after[entry], 'No starter kit change'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as out:
        for name, data in sorted(after.items()):
            out.writestr(name, data)
    assert set(before) == set(after)
    assert all(before[name] == after[name] for name in before if name != entry)
    report = {'baseline-sha256': args.expected_sha256, 'candidate-sha256': sha(args.output.read_bytes()),
              'changed-entries': [entry], 'all-unrelated-entries-identical': True}
    args.output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
