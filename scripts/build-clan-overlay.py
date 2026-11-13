#!/usr/bin/env python3
"""Build a scoped clan/KOTH overlay while retaining the installed shared bootstrap and crate engine.
Run with the project's existing map-tools Python (PyYAML); runtime data is never bundled.
"""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile
import yaml


def sha(data):
    return hashlib.sha256(data).hexdigest()


def load(data):
    return yaml.safe_load(data) or {}


def dump(value):
    return yaml.safe_dump(value, sort_keys=False, allow_unicode=True).encode()


def merged_config(name, existing, source):
    old, new = load(existing), load(source)
    if name == 'territory/maps.yml':
        old.setdefault('safezone-worlds', {}).update(new.get('safezone-worlds', {}))
        old['mechanism-worlds'] = sorted(set(old.get('mechanism-worlds', [])) | set(new.get('mechanism-worlds', [])))
        old.setdefault('additional-maps', {}).update(new.get('additional-maps', {}))
    elif name == 'crates/crates.yml':
        if not old:
            old = new
        else:
            old.setdefault('crates', {}).setdefault('koth', new['crates']['koth'])
            for key, value in new['messages'].items():
                old.setdefault('messages', {}).setdefault(key, value)
        if 'Kyber Crate Key' in old.get('messages', {}).get('no-key', ''):
            old['messages']['no-key'] = '<yellow>You need a key for this crate. Complete its event to earn one.'
    elif name == 'events/koth.yml':
        old.setdefault('rewards', {}).update({'enabled': True, 'crate': 'koth'})
        old['clan-rewards-version'] = 1
        old.setdefault('messages', {}).setdefault('reward', new['messages']['reward'])
    elif name == 'language/territory.yml':
        old.setdefault('claims-legend', new['claims-legend'])
    elif name == 'mobs/spawning.yml':
        old['world'] = 'minecraft:tatooine_review'
    else:
        raise ValueError('Unsupported merge: ' + name)
    return dump(old)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--expected-sha256', required=True)
    parser.add_argument('--built', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runtime', type=Path)
    parser.add_argument('--stage-configs', type=Path)
    args = parser.parse_args()
    assert sha(args.baseline.read_bytes()) == args.expected_sha256, 'Baseline changed'
    with zipfile.ZipFile(args.baseline) as baseline, zipfile.ZipFile(args.built) as compiled:
        old = {n: baseline.read(n) for n in baseline.namelist()}
        built = {n: compiled.read(n) for n in compiled.namelist()}
    result = old.copy()
    prefixes = ('net/mandomc/gameplay/clans/', 'net/mandomc/gameplay/events/KothEvent',
                'net/mandomc/gameplay/events/EventManager', 'net/mandomc/gameplay/explosions/TemporaryExplosions',
                'net/mandomc/gameplay/territory/TerritoryPolicy', 'net/mandomc/gameplay/territory/ClanMapCommand',
                'net/mandomc/integrations/weapons/TerritoryWeapons', 'net/mandomc/commands/CommandBindings',
                'net/mandomc/gameplay/mobs/SandySpawns', 'net/mandomc/integrations/regions/MobProtection')
    for name, data in built.items():
        if name.endswith('.class') and name.startswith(prefixes):
            result[name] = data
    # Use the final deployed Kyber engine, including its newest UI fixes. Never overwrite it.
    if 'net/mandomc/gameplay/crates/CrateService.class' not in old:
        for name, data in built.items():
            if name.startswith('net/mandomc/gameplay/crates/'):
                result[name] = data
    merged = ('territory/maps.yml', 'crates/crates.yml', 'events/koth.yml',
              'language/territory.yml', 'mobs/spawning.yml')
    added = ('clans/settings.yml', 'language/clans.yml', 'items/definitions/koth.yml')
    for name in merged:
        result[name] = merged_config(name, old.get(name, b''), built[name])
    for name in added:
        result[name] = built[name]
    descriptor, extra = load(old['plugin.yml']), load(built['plugin.yml'])
    for command in ('c', 'crates'):
        descriptor.setdefault('commands', {}).setdefault(command, extra['commands'][command])
    descriptor['commands']['c']['description'] = extra['commands']['c']['description']
    descriptor['commands']['c']['usage'] = extra['commands']['c']['usage']
    for key in ('mandomc.crates.play', 'mandomc.crates.admin'):
        descriptor.setdefault('permissions', {}).setdefault(key, extra['permissions'][key])
    result['plugin.yml'] = dump(descriptor)
    assert result['net/mandomc/bootstrap/MandoPlugin.class'] == old['net/mandomc/bootstrap/MandoPlugin.class']
    assert set(old) <= set(result)
    changed = sorted(n for n in result if result[n] != old.get(n))
    for name in changed:
        assert name.startswith(prefixes) or name in merged + added + ('plugin.yml',) or name.startswith('net/mandomc/gameplay/crates/')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, data in sorted(result.items()):
            output.writestr(name, data)
    report = {'baseline-sha256': args.expected_sha256, 'candidate-sha256': sha(args.output.read_bytes()),
              'changed-entries': changed, 'bootstrap-preserved': True,
              'existing-crate-engine-preserved': all(result[n] == old[n] for n in old if n.startswith('net/mandomc/gameplay/crates/')),
              'all-unrelated-entries-identical': all(result[n] == old[n] for n in old if n not in changed)}
    if args.runtime or args.stage_configs:
        assert args.runtime and args.stage_configs
        configs = {}
        for name in merged + added:
            path = args.runtime / 'plugins/MandoMC' / name
            original = path.read_bytes() if path.exists() else b''
            data = merged_config(name, original or old.get(name, built[name]), built[name]) if name in merged else original or built[name]
            target = args.stage_configs / 'plugins/MandoMC' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            configs['plugins/MandoMC/' + name] = {'before': sha(original), 'after': sha(data)}
        name = 'plugins/MandoMC/worlds/worlds.yml'
        path = args.runtime / name
        original = path.read_bytes()
        worlds = load(original)
        target_world = worlds['worlds']['tatooine_review']
        assert target_world['key'] == 'minecraft:tatooine_review'
        target_world.update({'pvp': True, 'difficulty': 'normal'})
        data = dump(worlds)
        target = args.stage_configs / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        configs[name] = {'before': sha(original), 'after': sha(data)}
        report['runtime-configs'] = configs
    args.output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
