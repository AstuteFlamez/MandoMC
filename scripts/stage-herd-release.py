#!/usr/bin/env python3
"""Stage only herd classes/resources over an exact current JAR; never deploy or publish."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def animal_definitions(text):
    """Keep current base-only labels/stats and every unrelated species/skill/config byte."""
    for species in ['Bantha', 'Dewback']:
        pattern = rf'(?m)^{species}:\n[\s\S]*?(?=^[^ \n#][^\n]*:\n|\Z)'
        match = re.search(pattern, text)
        if not match:
            raise ValueError(f'Missing current {species} definition')
        block = match.group()
        if 'LevelModifiers:' in block or '<caster.level>' in block:
            raise ValueError('Merge the deployed level-removal configuration first')
        block, count = re.subn(r'(?m)^  Type: .*$', '  Type: HORSE', block, count=1)
        assert count == 1
        if re.search(r'(?m)^    Tameable:', block):
            block = re.sub(r'(?m)^    Tameable: .*$', '    Tameable: true', block)
        else:
            block = block.replace('  Options:\n', '  Options:\n    Tameable: true\n', 1)
        block = re.sub(r'(?m)^    PreventOtherDrops: .*$', '    PreventOtherDrops: false', block)
        block = re.sub(r'(?m)^    PreventLeashing: .*$', '    PreventLeashing: false', block)
        block, count = re.subn(r'(?m)^  AIGoalSelectors:\n(?:    .*\n)*',
                              '  AIGoalSelectors:\n    - clear\n    - 0 float\n    - 1 panic\n'
                              '    - 4 randomstroll\n    - 5 lookatplayers\n', block, count=1)
        assert count == 1
        text = text[:match.start()] + block + text[match.end():]
    return text


def stage(baseline, classes, bootstrap, output, source):
    families = ['gameplay/mobs/HerdAge', 'gameplay/mobs/HerdSettings', 'bootstrap/HerdBootstrap',
                'integrations/mobs/HerdBabies', 'integrations/mobs/HerdAnimals',
                'integrations/mobs/HerdCourtship', 'integrations/mobs/HerdTempt',
                'integrations/models/HerdModels', 'integrations/mobs/BanthaDrops',
                'integrations/mobs/DewbackDrops', 'gameplay/mobs/SandySpawns']
    replacements = {'net/mandomc/bootstrap/MandoPlugin.class': bootstrap.read_bytes()}
    for family in families:
        parent = classes / 'net/mandomc' / Path(family).parent
        stem = Path(family).name
        files = [p for p in parent.glob(stem + '*.class') if p.stem == stem or p.stem.startswith(stem + '$')]
        if not files:
            raise ValueError(f'Missing compiled herd class {family}')
        for path in files:
            replacements[path.relative_to(classes).as_posix()] = path.read_bytes()
    for name in ['mobs/herd.yml', 'language/herd.yml', 'mobs/drops/bantha.yml']:
        replacements[name] = (source / 'plugin/src/main/resources' / name).read_bytes()
    allowed = set(replacements)
    with zipfile.ZipFile(baseline) as old:
        if any('/Herd' in name and name.endswith('.class') for name in old.namelist()):
            raise ValueError('Baseline already contains a herd slice; inspect its initialization before adding another')
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as new:
            for info in old.infolist():
                new.writestr(info, replacements.pop(info.filename, old.read(info.filename)))
            for name, data in sorted(replacements.items()):
                new.writestr(name, data)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(output) as new:
        changed = [name for name in old.namelist() if old.read(name) != new.read(name)]
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert set(changed) | set(added) <= allowed
        assert not set(old.namelist()) - set(new.namelist())
        return dict(baseline_sha256=sha(baseline.read_bytes()), staged_sha256=sha(output.read_bytes()),
                    changed=changed, added=added, unrelated_entries_preserved=len(old.namelist())-len(changed))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('classes', type=Path)
    parser.add_argument('patched_bootstrap', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--mythic-config', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    report = stage(args.baseline, args.classes, args.patched_bootstrap, args.output, root)
    current = args.mythic_config.read_bytes()
    candidate = animal_definitions(current.decode()).encode()
    args.output.with_suffix('.sandy.yml').write_bytes(candidate)
    report.update(mythic_baseline_sha256=sha(current), mythic_candidate_sha256=sha(candidate))
    args.output.with_suffix('.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))
