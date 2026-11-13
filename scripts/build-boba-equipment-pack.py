#!/usr/bin/env python3
"""Add only Boba equipment to the current published pack, preserving every prior entry byte for byte."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'resource-pack/src'


def overlay(base, destination):
    with zipfile.ZipFile(base) as archive:
        entries = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
    original = dict(entries)
    additions = {}
    for kind in ('items/equipment', 'models/equipment', 'textures/item/equipment'):
        folder = SOURCE / f'assets/mandomc/{kind}'
        for path in sorted(folder.iterdir()):
            name = path.relative_to(SOURCE).as_posix()
            data = path.read_bytes()
            if name in entries and entries[name] != data:
                raise ValueError(f'Refusing to overwrite existing different asset: {name}')
            additions[name] = data
    entries.update(additions)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 12, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    with zipfile.ZipFile(destination) as archive:
        assert all(archive.read(name) == data for name, data in original.items())
    report = {'baseSha256': hashlib.sha256(base.read_bytes()).hexdigest(),
              'sha1': hashlib.sha1(destination.read_bytes()).hexdigest(),
              'sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
              'entries': len(entries), 'preservedEntries': len(original), 'assets': sorted(additions)}
    destination.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(overlay(args.base, args.output), indent=2))
