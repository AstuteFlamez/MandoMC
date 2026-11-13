#!/usr/bin/env python3
"""Build the explicit mapping from reviewed source files to production paths."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def entries():
    files = {
        'plugins/update/MandoMC.jar': 'plugin/target/MandoMC.jar',
        'plugins/update/MandoAccess.jar': 'access/target/MandoAccess.jar',
        'plugins/MandoAccess/config.yml': 'access/src/main/resources/config.yml',
        'agents/MandoMC-RedstoneAgent.jar': 'redstone-agent/target/MandoMC-RedstoneAgent.jar',
        'plugins/CoreProtect/config.yml': 'server/config/common/coreprotect.yml',
    }
    files['plugins/VotingPlugin/VoteSites.yml'] = 'server/config/common/votingplugin/VoteSites.yml'
    resources = ROOT / 'plugin/src/main/resources'
    for source in sorted(resources.rglob('*.yml')):
        relative = source.relative_to(resources)
        if str(relative) != 'plugin.yml':
            files['plugins/MandoMC/' + str(relative)] = str(source.relative_to(ROOT))
    common = ROOT / 'server/config/common/mandomc'
    for source in sorted(common.rglob('*.yml')):
        files['plugins/MandoMC/' + str(source.relative_to(common))] = str(source.relative_to(ROOT))
    for source in sorted((ROOT / 'server/config/common/modelengine/blueprints').glob('*.bbmodel')):
        files['plugins/ModelEngine/blueprints/' + source.name] = str(source.relative_to(ROOT))
    mythic = {
        'Mobs/mandomc-bossk.yml': 'bossk-mob.yml', 'Skills/mandomc-bossk.yml': 'bossk-skills.yml',
        'Mobs/boba-mob.yml': 'boba-mob.yml', 'Skills/boba-skills.yml': 'boba-skills.yml',
        'Mobs/mandomc-jabba.yml': 'jabba-mobs.yml', 'Skills/mandomc-jabba.yml': 'jabba-skills.yml',
        'Mobs/mandomc-jawa.yml': 'jawa-mobs.yml', 'Skills/mandomc-jawa.yml': 'jawa-skills.yml',
        'Mobs/rancor-mob.yml': 'rancor-mob.yml', 'Skills/rancor-skills.yml': 'rancor-skills.yml',
        'Mobs/mandomc-sandy.yml': 'sandy-mobs.yml', 'Skills/mandomc-sandy.yml': 'sandy-skills.yml',
        'Skills/mandomc-massiff.yml': 'massiff-skills.yml', 'Skills/hostile-facing.yml': 'hostile-facing.yml',
        'RandomSpawns/ExampleRandomSpawns.yml': 'randomspawns/ExampleRandomSpawns.yml',
    }
    for target, source in mythic.items():
        files['plugins/MythicMobs/' + target] = 'server/config/common/mythicmobs/' + source
    for source in sorted((ROOT / 'server/config/common/weaponmechanics').glob('*.yml')):
        if source.stem in ('ionized_energy_cells', 'standard_plasma_cells'):
            folder = 'ammos'
        elif source.stem in ('redlaser', 'jawabluelaser'):
            folder = 'projectiles'
        elif source.stem == 'ee3':
            folder = 'weapons'
        else:
            folder = 'weapons/mandomc'
        files[f'plugins/WeaponMechanics/{folder}/{source.name}'] = str(source.relative_to(ROOT))
    base = ROOT / 'server/datapacks/mandomc-jetpack'
    for source in sorted(base.rglob('*')):
        if source.is_file():
            files['dev_world/datapacks/mandomc-jetpack/' + str(source.relative_to(base))] = str(source.relative_to(ROOT))
    return [dict(source=source, target=target) for target, source in sorted(files.items())]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    document = json.dumps(dict(schemaVersion=1, files=entries()), indent=2) + '\n'
    path = ROOT / 'deployment/files.json'
    if args.check:
        if path.read_text() != document:
            raise SystemExit('Regenerate the reviewed deployment manifest: python scripts/build-deployment-manifest.py')
    else:
        path.write_text(document)
