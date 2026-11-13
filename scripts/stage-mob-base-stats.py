#!/usr/bin/env python3
"""Overlay only the verified mob-level removal classes and bundled spawn config on the current JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


def spawning(text):
    text = re.sub(r'^levels:\n(?:  .*\n)*', '', text, flags=re.M)
    return re.sub(r'^  damage-per-level:.*\n', '', text, flags=re.M)


def definitions(text):
    text = text.replace(' &7[Lv. <caster.level>]', '').replace('&bLvl. &b<caster.level> &7- &', '&')
    text = re.sub(r'^  LevelModifiers:\n(?:    .*\n)*', '', text, flags=re.M)
    return text.replace('MythicMobs owns AI, health and level.', 'MythicMobs owns AI, health and entity lifecycle.').replace(
        'Legacy behavior; group levels remain labels, with the original fixed combat stats.',
        'Legacy behavior with the original fixed combat stats.')


def stage(baseline, classes, output):
    names = ['gameplay/mobs/EncounterPlan', 'gameplay/mobs/SandySettings',
             'integrations/mobs/MythicMobAccess', 'integrations/mobs/MobBaseStats',
             'integrations/weapons/MobWeaponDamage']
    replacements = {f'net/mandomc/{name}.class': (classes / f'net/mandomc/{name}.class').read_bytes() for name in names}
    with zipfile.ZipFile(baseline) as old:
        replacements['mobs/spawning.yml'] = spawning(old.read('mobs/spawning.yml').decode()).encode()
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as new:
            for info in old.infolist():
                new.writestr(info, replacements.pop(info.filename, old.read(info.filename)))
            for name, data in replacements.items():
                assert name == 'net/mandomc/integrations/mobs/MobBaseStats.class', name
                new.writestr(name, data)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(output) as new:
        changed = [name for name in old.namelist() if old.read(name) != new.read(name)]
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert set(changed) <= {f'net/mandomc/{name}.class' for name in names} | {'mobs/spawning.yml'}
        assert not set(old.namelist()) - set(new.namelist())
        return dict(baseline_sha256=hashlib.sha256(baseline.read_bytes()).hexdigest(),
                    staged_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                    changed=changed, added=added, unchanged_entries=len(old.namelist()) - len(changed))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('classes', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    result = stage(args.baseline, args.classes, args.output)
    args.output.with_suffix('.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
