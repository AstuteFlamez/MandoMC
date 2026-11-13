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
import secrets
import yaml

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


def prepare(runtime, destination, port=25565, public=False, voting_identity=None):
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
        allowed_dirs = ('MandoMC', 'MandoAccess', 'CoreProtect', 'ModelEngine', 'MechanicsCore',
                        'WeaponMechanics', 'MythicMobs', 'WorldEdit', 'WorldGuard', 'Factions', 'Vault', 'VotingPlugin', 'Votifier')
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
        shutil.copyfile(ROOT / 'access/target/MandoAccess.jar', destination / 'plugins/MandoAccess.jar')
        (destination / 'agents').mkdir()
        shutil.copyfile(ROOT / 'redstone-agent/target/MandoMC-RedstoneAgent.jar',
                        destination / 'agents/MandoMC-RedstoneAgent.jar')
        source_access = ROOT / 'access/src/main/resources/config.yml'
        (destination / 'plugins/MandoAccess').mkdir(exist_ok=True)
        shutil.copyfile(source_access, destination / 'plugins/MandoAccess/config.yml')
        if public:
            maintenance = destination / 'plugins/MandoAccess/maintenance.yml'
            state = yaml.safe_load(maintenance.read_text()) if maintenance.exists() else {}
            state['enabled'] = False
            maintenance.write_text(yaml.safe_dump(state, sort_keys=False))
        if voting_identity:
            listener = destination / 'plugins/Votifier'
            (listener / 'rsa').mkdir(parents=True, exist_ok=True)
            for name in ('private.key', 'public.key'):
                source = voting_identity / 'rsa' / name
                shutil.copyfile(source, listener / 'rsa' / name)
                (listener / 'rsa' / name).chmod(0o600)
            config = dict(host='0.0.0.0', port=19132,
                          **{'disable-v1-protocol': False},
                          tokens={'default': secrets.token_urlsafe(32)}, forwarding={'method': 'none'})
            (listener / 'config.yml').write_text(yaml.safe_dump(config, sort_keys=False))
            (listener / 'config.yml').chmod(0o600)
            votes = destination / 'plugins/VotingPlugin'
            votes.mkdir(exist_ok=True)
            (votes / 'Config.yml').write_text(yaml.safe_dump(dict(
                AutoCreateVoteSites=False, AllowUnjoined=False, DataStorage='SQLITE',
                ProcessRewards=True), sort_keys=False))
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
            'server-ip': '', 'server-port': str(port), 'enable-rcon': 'false',
            'rcon.password': '', 'rcon.port': '25575', 'enable-query': 'false',
            'query.port': str(port), 'online-mode': 'true',
            **({'white-list': 'false', 'enforce-whitelist': 'false'} if public else {})}))
        # Carry the established owner's access records with the rebuilt server;
        # the independent MandoAccess maintenance state remains private.
        for name in ('ops.json', 'banned-ips.json', 'banned-players.json', 'whitelist.json'):
            shutil.copyfile(runtime / name, destination / name)
        level_name = next((line.partition('=')[2] for line in props.read_text().splitlines()
                           if line.startswith('level-name=')), 'dev_world')
        datapacks = destination / level_name / 'datapacks'
        for source in (ROOT / 'server/datapacks').iterdir():
            shutil.copytree(source, datapacks / source.name, dirs_exist_ok=True)
        import importlib.util
        spec = importlib.util.spec_from_file_location('transfer', ROOT / 'scripts/pebblehost.py')
        transfer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(transfer)
        for target, data in transfer.payload().items():
            installed = destination / transfer.installed_path(target)
            installed.parent.mkdir(parents=True, exist_ok=True)
            installed.write_bytes(data)
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        props.write_bytes(transfer.production_properties(props.read_bytes(), commit))
        # Reuse the already accepted Minecraft agreement without accepting it here.
        shutil.copyfile(runtime / 'eula.txt', destination / 'eula.txt')
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
    parser.add_argument('--port', type=int, default=25565)
    parser.add_argument('--public', action='store_true')
    parser.add_argument('--voting-identity', type=Path, help='Private existing Votifier directory containing rsa keys')
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error('Port must be between 1 and 65535')
    prepare(args.runtime, args.output, args.port, args.public, args.voting_identity)
