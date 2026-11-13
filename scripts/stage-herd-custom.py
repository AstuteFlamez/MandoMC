#!/usr/bin/env python3
"""Stage custom herd equipment/leads over a current JAR; no installation or publication."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stage(baseline, classes, output, mythic):
    root = Path(__file__).resolve().parents[1]
    families = ['integrations/mobs/HerdAnimals', 'integrations/mobs/HerdEquipment',
                'integrations/mobs/HerdLeads', 'integrations/mobs/HerdTempt',
                'integrations/mobs/HerdCourtship', 'gameplay/mobs/SandySpawns']
    replacements = {}
    for family in families:
        parent = classes / 'net/mandomc' / Path(family).parent
        stem = Path(family).name
        files = [p for p in parent.glob(stem + '*.class') if p.stem == stem or p.stem.startswith(stem + '$')]
        if not files:
            raise ValueError(f'Missing compiled family {family}')
        for p in files:
            replacements[p.relative_to(classes).as_posix()] = p.read_bytes()
    replacements['language/herd.yml'] = (root / 'plugin/src/main/resources/language/herd.yml').read_bytes()
    allowed = set(replacements)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as new:
        for info in old.infolist():
            new.writestr(info, replacements.pop(info.filename, old.read(info.filename)))
        for name, data in sorted(replacements.items()):
            new.writestr(name, data)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(output) as new:
        changed = [n for n in old.namelist() if old.read(n) != new.read(n)]
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert set(changed) | set(added) <= allowed
        assert not set(old.namelist()) - set(new.namelist())
        report = dict(baseline_sha256=sha(baseline.read_bytes()), candidate_sha256=sha(output.read_bytes()),
                      changed=changed, added=added, unrelated_entries_preserved=len(old.namelist())-len(changed))
    before = mythic.read_bytes()
    text = before.decode()
    for species in ['Bantha', 'Dewback']:
        match = re.search(rf'(?m)^{species}:\n[\s\S]*?(?=^[^ \n#][^\n]*:\n|\Z)', text)
        if not match:
            raise ValueError(f'Missing current {species} block')
        block, count = re.subn(r'(?m)^    PreventLeashing: (true|false)$', '    PreventLeashing: false', match.group())
        if count != 1:
            raise ValueError(f'Expected one {species} leashing option')
        text = text[:match.start()] + block + text[match.end():]
    output.with_suffix('.sandy.yml').write_text(text)
    report.update(mythic_baseline_sha256=sha(before), mythic_candidate_sha256=sha(text.encode()))
    output.with_suffix('.json').write_text(json.dumps(report, indent=2)+'\n')
    return report


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('baseline', type=Path); p.add_argument('classes', type=Path)
    p.add_argument('output', type=Path); p.add_argument('--mythic-config', type=Path, required=True)
    a = p.parse_args()
    print(json.dumps(stage(a.baseline, a.classes, a.output, a.mythic_config), indent=2))
