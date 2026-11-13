#!/usr/bin/env python3
"""Prepare a private PebbleHost runtime from a stopped rebuilt local server."""
import argparse
import fcntl
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def verified(source, target, expected):
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
        raise ValueError(f'Locked binary checksum mismatch: {source.name}')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


def properties(text, updates):
    result = []
    for line in text.splitlines():
        key = line.partition('=')[0]
        if key not in updates:
            result.append(line)
    result.extend(f'{key}={value}' for key, value in updates.items())
    return '\n'.join(result) + '\n'


def prepare(runtime, destination):
    runtime = runtime.resolve()
    if destination.exists():
        raise ValueError('Use a fresh output directory so older snapshots remain intact.')
    plugin = ROOT / 'plugin/target/MandoMC.jar'
    if not plugin.is_file():
        raise ValueError('Build the intended main commit before preparing production.')
    # Java FileLock uses POSIX record locks. Hold them for the whole copy so a
    # stopped world cannot be started and written while its snapshot is copied.
    locks = []
    try:
        for lock in runtime.rglob('session.lock'):
            handle = lock.open('rb')
            locks.append(handle)
            fcntl.lockf(handle, fcntl.LOCK_SH | fcntl.LOCK_NB)
        destination.mkdir(parents=True, mode=0o700)
        for source in runtime.iterdir():
            if source.is_symlink():
                raise ValueError(f'Refusing linked runtime entry: {source.name}')
            # Worlds include Paper 26 custom dimensions under the primary world.
            if source.is_dir() and (source / 'level.dat').is_file():
                for child in source.rglob('*'):
                    if child.is_symlink():
                        raise ValueError(f'Refusing linked world payload: {child}')
                shutil.copytree(source, destination / source.name,
                                ignore=shutil.ignore_patterns('session.lock'))
        for name in ('bukkit.yml', 'spigot.yml', 'commands.yml', 'permissions.yml',
                     'server-icon.png', 'server.properties', 'config'):
            source = runtime / name
            if source.is_dir():
                shutil.copytree(source, destination / name)
            elif source.is_file():
                shutil.copyfile(source, destination / name)
        (destination / 'plugins').mkdir()
        lock = json.loads((ROOT / 'server/plugins.lock.json').read_text())
        allowed_dirs = ('MandoMC', 'ModelEngine', 'MechanicsCore', 'WeaponMechanics',
                        'MythicMobs', 'WorldEdit', 'WorldGuard', 'Factions', 'Vault')
        for name in allowed_dirs:
            source = runtime / 'plugins' / name
            if source.is_dir():
                for child in source.rglob('*'):
                    if child.is_symlink():
                        raise ValueError(f'Refusing linked plugin content: {child}')
                shutil.copytree(source, destination / 'plugins' / name,
                                ignore=shutil.ignore_patterns('*.log', '*.tmp', 'logs', 'cache'))
        for artifact in lock['artifacts']:
            source = runtime / 'plugins' / artifact['name']
            if source.exists():
                verified(source, destination / 'plugins' / artifact['name'], artifact['sha256'])
            elif artifact.get('url'):
                with tempfile.TemporaryDirectory() as temporary:
                    download = Path(temporary) / artifact['name']
                    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
                                    '--max-time', '180', artifact['url'], '-o', str(download)], check=True)
                    verified(download, destination / 'plugins' / artifact['name'], artifact['sha256'])
            else:
                raise ValueError(f'Owner-supplied binary required: {artifact["name"]}')
        shutil.copyfile(plugin, destination / 'plugins' / 'MandoMC.jar')
        # Routine deployment overrides are applied from the selected source tree.
        config = ROOT / 'server/config/common/mandomc'
        for source in config.rglob('*.yml'):
            target = destination / 'plugins/MandoMC' / source.relative_to(config)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        paper = json.loads((ROOT / 'server/paper.lock.json').read_text())['server']
        verified(runtime / 'paper.jar', destination / 'server.jar', paper['checksums']['sha256'])
        props = destination / 'server.properties'
        props.write_text(properties(props.read_text(), {
            'server-ip': '', 'server-port': '25570', 'enable-rcon': 'false',
            'rcon.password': '', 'rcon.port': '25575', 'enable-query': 'false',
            'query.port': '25570', 'online-mode': 'true'}))
        # A fresh deployment never inherits the laptop owner/operator list.
        for name in ('ops.json', 'banned-ips.json', 'banned-players.json', 'whitelist.json'):
            (destination / name).write_text('[]\n')
        # Reuse the already accepted Minecraft agreement without accepting it here.
        shutil.copyfile(runtime / 'eula.txt', destination / 'eula.txt')
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        inventory = {str(path.relative_to(destination)): hashlib.sha256(path.read_bytes()).hexdigest()
                     for path in destination.rglob('*') if path.is_file()}
        (destination / '.prepared.json').write_text(json.dumps(dict(
            schemaVersion=1, sourceCommit=commit, files=inventory), indent=2) + '\n')
        print(f'Prepared private runtime: {destination}')
        print('Set PebbleHost to Java 25 and startup JAR server.jar. Start remains manual.')
    except BlockingIOError:
        raise SystemExit('The local rebuilt server is running. Stop it before creating a world snapshot.')
    finally:
        for handle in locks:
            handle.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.runtime, args.output)
