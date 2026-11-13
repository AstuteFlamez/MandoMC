#!/usr/bin/env python3
"""Private SFTP downloads and bounded production uploads. Never starts/stops Paper."""
import argparse
import base64
import errno
import getpass
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.local' / 'pebblehost'
SETTINGS = PRIVATE / 'sftp.json'
REMOTE_STATE = '.mandomc/deployed.json'


def safe_path(value):
    path = PurePosixPath(value)
    if not value or path.is_absolute() or any(p in ('..', '.', '') for p in value.split('/')):
        raise ValueError(f'Unsafe relative path: {value!r}')
    if '\\' in value or '\0' in value:
        raise ValueError('Unsafe path characters')
    return path.as_posix()


def private_write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(content)


def settings():
    raw = os.environ.get('PEBBLEHOST_SFTP_JSON')
    if raw:
        return json.loads(raw)
    if not SETTINGS.exists():
        raise ValueError('Run scripts/setup-pebblehost.command first.')
    return json.loads(SETTINGS.read_text())


def connect(config):
    import paramiko
    entry = paramiko.hostkeys.HostKeyEntry.from_line(config['known_host'])
    if entry is None:
        raise ValueError('Invalid pinned SSH host key')
    client = paramiko.SSHClient()
    host = config['host']
    port = int(config['port'])
    key_host = host if port == 22 else f'[{host}]:{port}'
    client.get_host_keys().add(key_host, entry.key.get_name(), entry.key)
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    try:
        client.connect(host, port=port, username=config['username'], password=config['password'],
                       allow_agent=False, look_for_keys=False, timeout=30, auth_timeout=30)
        sftp = client.open_sftp()
        sftp.get_channel().settimeout(60)
        # PebbleHost jails each SFTP account into its own server root.
        sftp.chdir('.')
        return client, sftp
    except Exception:
        client.close()
        raise


def configure():
    import paramiko
    host = input('SFTP hostname [na1992.pebblehost.net]: ').strip() or 'na1992.pebblehost.net'
    port = int(input('SFTP port [2222]: ').strip() or '2222')
    username = input('SFTP username [mandomcsocial@gmail.com.28fd9169]: ').strip()
    username = username or 'mandomcsocial@gmail.com.28fd9169'
    # Establish and inspect the SSH host identity before sending any password.
    transport = paramiko.Transport((host, port))
    try:
        transport.start_client(timeout=30)
        key = transport.get_remote_server_key()
        fingerprint = base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip('=')
        print(f'Host {host}:{port}\nSSH host fingerprint: SHA256:{fingerprint}')
        print('Check this fingerprint with PebbleHost/support before accepting an unfamiliar host.')
        if input('Trust and pin this host key? Type yes: ').strip() != 'yes':
            raise ValueError('Host key was not accepted; nothing saved.')
    finally:
        transport.close()
    config = dict(host=host, port=port, username=username,
                  known_host=f'[{host}]:{port} {key.get_name()} {key.get_base64()}',
                  password=getpass.getpass('PebbleHost GAME PANEL password (hidden): '))
    client, sftp = connect(config)
    try:
        print(f'Authenticated successfully; {len(sftp.listdir("."))} items at server root.')
    finally:
        sftp.close()
        client.close()
    private_write(SETTINGS, json.dumps(config))
    print('Saved to ignored .local/pebblehost/sftp.json with owner-only permissions.')
    if input('Also save this connection as a GitHub production environment secret? [yes/no]: ').strip() == 'yes':
        repo = 'AstuteFlamez/MandoMC'
        subprocess.run(['gh', 'api', '--method', 'PUT', f'repos/{repo}/environments/production',
                        '--input', '-'], input=b'{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}',
                       check=True, stdout=subprocess.DEVNULL)
        # Do not embed a password in process arguments, command history, or logs.
        subprocess.run(['gh', 'secret', 'set', 'PEBBLEHOST_SFTP_JSON', '--env', 'production', '--repo', repo],
                       input=json.dumps(config).encode(), check=True)
        print('GitHub production secret saved. Deployment stays disabled until migration is verified.')


def mkdirs(sftp, path):
    current = ''
    for part in PurePosixPath(safe_path(path)).parts:
        current = f'{current}/{part}'.lstrip('/')
        try:
            attributes = sftp.lstat(current)
        except OSError as exc:
            if exc.errno != errno.ENOENT:
                raise
            sftp.mkdir(current)
        else:
            if not stat.S_ISDIR(attributes.st_mode):
                raise ValueError(f'Remote path is not a plain directory: {current}')


def read_remote(sftp, path):
    try:
        attributes = sftp.lstat(path)
        if not stat.S_ISREG(attributes.st_mode):
            raise ValueError(f'Refusing non-regular remote file: {path}')
        with sftp.open(path, 'rb') as source:
            return source.read()
    except OSError as exc:
        if exc.errno == errno.ENOENT:
            return None
        raise


def atomic_write(sftp, path, data):
    path = safe_path(path)
    parent = str(PurePosixPath(path).parent)
    if parent != '.':
        mkdirs(sftp, parent)
    suffix = uuid.uuid4().hex
    temporary = path + '.mandomc-upload-' + suffix
    with sftp.open(temporary, 'wb') as target:
        target.write(data)
    if read_remote(sftp, temporary) != data:
        raise ValueError(f'Remote checksum verification failed: {path}')
    try:
        sftp.posix_rename(temporary, path)
        return
    except OSError:
        # PebbleHost's SFTP implementation rejects overwrite renames. A failed
        # response might still mean success after a connection interruption.
        if read_remote(sftp, path) == data:
            return
        if read_remote(sftp, temporary) != data:
            raise ValueError(f'Upload temporary file unavailable: {path}')
    existing = read_remote(sftp, path)
    if existing is None:
        sftp.rename(temporary, path)
        return
    previous = path + '.mandomc-previous-' + suffix
    sftp.rename(path, previous)
    try:
        sftp.rename(temporary, path)
    except Exception:
        # Restore the old name on a bounded rename failure. Both byte sets are
        # retained for recovery; never truncate or delete the previous file.
        sftp.rename(previous, path)
        raise
    if read_remote(sftp, path) != data:
        raise ValueError(f'Installed checksum verification failed: {path}')


def sha(data):
    return hashlib.sha256(data).hexdigest() if data is not None else None


def payload(root=ROOT):
    manifest = json.loads((root / 'deployment' / 'files.json').read_text())
    result = {}
    for item in manifest['files']:
        source = root / safe_path(item['source'])
        target = safe_path(item['target'])
        if target == 'agents/MandoMC-RedstoneAgent.jar':
            target = f'agents/MandoMC-RedstoneAgent-{sha(source.read_bytes())}.jar'
        if target in result:
            raise ValueError(f'Duplicate deployment target: {target}')
        if not source.is_file() or source.is_symlink():
            raise ValueError(f'Missing or unsafe source file: {source}')
        if root.resolve() not in source.resolve().parents:
            raise ValueError('Source must be inside the checkout')
        if not (target in {'plugins/update/MandoMC.jar', 'plugins/update/MandoAccess.jar',
                           'start.sh'} or
                re.fullmatch(r'agents/MandoMC-RedstoneAgent-[a-f0-9]{64}\.jar', target) or
                (target.startswith('plugins/MandoMC/') and target.endswith('.yml')) or
                target == 'plugins/MandoAccess/config.yml' or
                target == 'plugins/CoreProtect/config.yml' or
                target == 'plugins/VotingPlugin/VoteSites.yml' or
                (target.startswith('plugins/ModelEngine/blueprints/') and target.endswith('.bbmodel')) or
                (target.startswith(('plugins/MythicMobs/Mobs/', 'plugins/MythicMobs/Skills/',
                                    'plugins/MythicMobs/RandomSpawns/',
                                    'plugins/WeaponMechanics/weapons/mandomc/',
                                    'plugins/WeaponMechanics/ammos/', 'plugins/WeaponMechanics/projectiles/'))
                 and target.endswith('.yml')) or target == 'plugins/WeaponMechanics/weapons/ee3.yml' or
                (target.startswith('dev_world/datapacks/mandomc-jetpack/') and
                 (target.endswith('.json') or target.endswith('/pack.mcmeta')))):
            raise ValueError(f'Target is outside the deployment allowlist: {target}')
        parts = PurePosixPath(target).parts
        datapack = target.startswith('dev_world/datapacks/mandomc-jetpack/')
        if any(part in {'playerdata', 'runtime', 'cache', 'logs'} for part in parts) or ('data' in parts and not datapack):
            raise ValueError('Runtime/player data cannot be deployed')
        result[target] = source.read_bytes()
    if 'plugins/update/MandoMC.jar' not in result:
        raise ValueError('The production plugin is missing')
    return result


def plan(sftp, files, previous):
    removed = {name for name in set(previous['files']) - set(files)
               if not re.fullmatch(r'agents/MandoMC-RedstoneAgent-[a-f0-9]{64}\.jar', name)}
    if removed:
        raise ValueError('Managed file removal needs a reviewed migration: ' + ', '.join(sorted(removed)))
    changes = []
    for target, data in files.items():
        current = read_remote(sftp, target)
        # Paper consumes the update JAR at startup; compare its installed copy.
        if current is None:
            installed = installed_path(target)
            if installed != target:
                current = read_remote(sftp, installed)
        if sha(current) == sha(data):
            continue
        if sha(current) != previous['files'].get(target):
            raise ValueError(f'Production drift at {target}; pull/review it before deploying.')
        changes.append((target, data, current))
    return changes


def installed_path(target):
    return {'plugins/update/MandoMC.jar': 'plugins/MandoMC.jar',
            'plugins/update/MandoAccess.jar': 'plugins/MandoAccess.jar'}.get(target, target)


def host_settings(original, files):
    agents = [target for target in files if re.fullmatch(r'agents/MandoMC-RedstoneAgent-[a-f0-9]{64}\.jar', target)]
    if len(agents) != 1:
        raise ValueError('Expected one immutable redstone agent in deployment')
    lines = original.decode('utf-8').splitlines()
    flags = next((line.partition('=')[2] for line in lines if line.startswith('startupflags=')), '')
    flags = re.sub(r'(?<!\S)-javaagent:agents/MandoMC-RedstoneAgent[^\s]*', '', flags).strip()
    updates = dict(javaversion='25', autoupdate='false',
                   startupflags=(flags + ' -javaagent:' + agents[0]).strip())
    lines = [line for line in lines if line.partition('=')[0] not in updates]
    lines.extend(f'{key}={value}' for key, value in updates.items())
    return ('\n'.join(lines) + '\n').encode('utf-8')


def production_properties(original, commit, root=ROOT):
    report = json.loads((root / 'resource-pack/build/MandoMC.json').read_text())
    hosting = json.loads((root / 'resource-pack/hosting.json').read_text())
    if not re.fullmatch('[a-f0-9]{40}', commit) or not re.fullmatch('[a-f0-9]{40}', report['sha1']):
        raise ValueError('Invalid source commit or pack hash')
    tag = f'resource-pack-{report["minecraftVersion"]}-{commit[:12]}'
    motd = next(line.partition('=')[2] for line in
                (root / 'server/runtime/server.properties.template').read_text().splitlines()
                if line.startswith('motd='))
    # Java .properties supports ASCII Unicode escapes. Preserve literal \n
    # separators in the template; do not double-escape their backslashes.
    motd = ''.join(character if ord(character) < 128 else f'\\u{ord(character):04x}' for character in motd)
    updates = {
        'resource-pack': f'https://github.com/{hosting["repository"]}/releases/download/{tag}/MandoMC.zip',
        'resource-pack-sha1': report['sha1'], 'resource-pack-id': hosting['packId'],
        'require-resource-pack': str(hosting['required']).lower(),
        'resource-pack-prompt': json.dumps(dict(text=hosting['prompt']), ensure_ascii=True),
        'motd': motd,
    }
    lines = [line for line in original.decode('utf-8').splitlines()
             if line.partition('=')[0] not in updates]
    lines.extend(f'{key}={value}' for key, value in updates.items())
    return ('\n'.join(lines) + '\n').encode('utf-8')


def deploy(sftp, commit, apply=False):
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise ValueError('A full source commit SHA is required')
    # This receipt can only be installed after the separate one-time migration.
    receipt = read_remote(sftp, '.mandomc/bootstrap.json')
    if receipt is None or json.loads(receipt).get('schemaVersion') != 1 or not json.loads(receipt).get('verifiedStartup'):
        raise ValueError('Initial migration has not been verified; automatic upload is blocked.')
    previous_raw = read_remote(sftp, REMOTE_STATE)
    if previous_raw is None:
        raise ValueError('Migration must seed the production file hashes first.')
    previous = json.loads(previous_raw)
    files = payload()
    properties_before = read_remote(sftp, 'server.properties')
    if properties_before is None:
        raise ValueError('Missing production server.properties')
    properties_after = production_properties(properties_before, commit)
    host_before = read_remote(sftp, 'pebblehost.yml')
    if host_before is None:
        raise ValueError('Missing PebbleHost startup configuration')
    host_after = host_settings(host_before, files)
    mkdirs(sftp, '.mandomc')
    # mkdir is an atomic cross-runner lock; a stale lock needs human inspection.
    sftp.mkdir('.mandomc/deploy.lock')
    try:
        changes = plan(sftp, files, previous)
        print(f'{len(changes)} changed files for {commit}; restart is manual.')
        for target, _, _ in changes:
            print(f'  {target}')
        if not apply:
            print('Plan only; no production files changed.')
            return
        backup = f'.mandomc/backups/{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}-{commit[:12]}'
        # Preserve the complete previous managed set and its receipt before mutation.
        atomic_write(sftp, f'{backup}/deployed.json', previous_raw)
        atomic_write(sftp, f'{backup}/files/server.properties', properties_before)
        atomic_write(sftp, f'{backup}/files/pebblehost.yml', host_before)
        for target, _, current in changes:
            if current is not None:
                atomic_write(sftp, f'{backup}/files/{target}', current)
        for target, data, _ in changes:
            atomic_write(sftp, target, data)
        if properties_after != properties_before:
            atomic_write(sftp, 'server.properties', properties_after)
        if host_after != host_before:
            atomic_write(sftp, 'pebblehost.yml', host_after)
        state = dict(schemaVersion=1, sourceCommit=commit,
                     files={target: sha(data) for target, data in files.items()},
                     restartRequired=bool(changes) or properties_after != properties_before or previous.get('restartRequired', False),
                     previousBackup=backup)
        atomic_write(sftp, REMOTE_STATE, json.dumps(state, indent=2).encode())
        print('Uploaded and hash-verified. Click Restart in PebbleHost when ready.')
    finally:
        sftp.rmdir('.mandomc/deploy.lock')


def verify_startup(sftp):
    raw = read_remote(sftp, '.mandomc/bootstrap.json')
    if raw is None:
        raise ValueError('No migration receipt; activate the prepared runtime first.')
    receipt = json.loads(raw)
    log_path = 'logs/latest.log'
    if sftp.lstat(log_path).st_mtime < sftp.lstat('.mandomc/bootstrap.json').st_mtime:
        raise ValueError('The current log predates the cutover. Start the server manually first.')
    log = (read_remote(sftp, log_path) or b'').decode('utf-8', errors='replace')
    required = ('Running Java 25', 'Loading Paper 26.1.2-74', 'Done (',
                'Arrival enabled:', 'Native clans ready:', 'Native crates ready;',
                'Redstone circuits disabled in all worlds', 'Travel guides ready:',
                'Ambient Massiffs ready:', 'World leaderboards ready:')
    missing = [text for text in required if text not in log]
    if missing or '/ERROR]' in log or '/SEVERE]' in log:
        raise ValueError('Startup not healthy; inspect latest.log. Missing markers: ' + ', '.join(missing))
    state_raw = read_remote(sftp, REMOTE_STATE)
    if state_raw is None:
        raise ValueError('Missing managed-file receipt')
    state = json.loads(state_raw)
    drift = []
    for target, expected in state['files'].items():
        current = read_remote(sftp, installed_path(target))
        if sha(current) != expected:
            drift.append(target)
    if drift:
        raise ValueError('Startup changed managed bytes; review before enabling upload: ' + ', '.join(drift))
    receipt['verifiedStartup'] = True
    receipt['verifiedAt'] = datetime.now(timezone.utc).isoformat()
    state['restartRequired'] = False
    atomic_write(sftp, '.mandomc/bootstrap.json', json.dumps(receipt).encode())
    atomic_write(sftp, REMOTE_STATE, json.dumps(state).encode())
    print('Paper, Java, gameplay startup and managed hashes verified. No power command was sent.')


def download(sftp, remote, destination, resume=False):
    """Read-only recursive download; never follows server-controlled symlinks."""
    for item in sftp.listdir_attr(remote):
        if item.filename in {'tmp', '.paper-remapped'} or item.filename.endswith(('.tmp', '.lock', '.jfr')):
            continue
        if item.filename in ('.', '..') or '/' in item.filename or '\\' in item.filename:
            raise ValueError('Unsafe remote filename')
        source = f'{remote}/{item.filename}'
        target = destination / item.filename
        if stat.S_ISLNK(item.st_mode):
            print(f'Skipping symbolic link: {source}')
        elif stat.S_ISDIR(item.st_mode):
            target.mkdir(mode=0o700, exist_ok=resume)
            download(sftp, source, target, resume)
        elif stat.S_ISREG(item.st_mode):
            if resume and target.is_file() and target.stat().st_size == item.st_size and item.st_mtime <= target.stat().st_mtime:
                continue
            try:
                sftp.get(source, str(target), max_concurrent_prefetch_requests=32)
            except OSError:
                # Some Wings SFTP servers reject pipelined reads on a changing
                # file. Retry with plain reads and retain the failing path.
                try:
                    sftp.get(source, str(target), prefetch=False)
                except Exception as exc:
                    raise OSError(f'Download failed at {source}: {exc}') from exc
            target.chmod(0o600)
            os.utime(target, (item.st_atime, item.st_mtime))


def pull(config, sftp, resume=None):
    destination = Path(resume).resolve() if resume else PRIVATE / 'downloads' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    if resume and (PRIVATE / 'downloads').resolve() not in destination.parents:
        raise ValueError('Resume only into an existing private download directory.')
    destination.mkdir(parents=True, mode=0o700, exist_ok=bool(resume))
    print(f'Downloading into {destination}. A running server is not a consistent world backup.', flush=True)
    generated = {'.mandomc', '.cache', '.java', 'cache', 'libraries', 'versions', 'logs', 'debug', '.trash', '.ufs-tmp'}
    items = [item for item in sftp.listdir_attr('.') if item.filename not in generated
             and '.mandomc-upload' not in item.filename and '.mandomc-previous' not in item.filename]

    def worker(group):
        client, channel = connect(config)
        try:
            for item in group:
                name = safe_path(item.filename)
                target = destination / name
                if stat.S_ISDIR(item.st_mode):
                    target.mkdir(mode=0o700, exist_ok=bool(resume))
                    download(channel, name, target, bool(resume))
                elif stat.S_ISREG(item.st_mode):
                    if resume and target.is_file() and target.stat().st_size == item.st_size and item.st_mtime <= target.stat().st_mtime:
                        continue
                    channel.get(name, str(target), max_concurrent_prefetch_requests=32)
                    target.chmod(0o600)
                    os.utime(target, (item.st_atime, item.st_mtime))
                else:
                    continue
                print(f'Downloaded {name}', flush=True)
        finally:
            channel.close()
            client.close()

    with ThreadPoolExecutor(max_workers=4) as pool:
        # Four persistent sessions reduce round-trip cost without unbounded fanout.
        for future in [pool.submit(worker, items[index::4]) for index in range(4)]:
            future.result()
    private_write(destination / '.download-complete.json', json.dumps(dict(
        completedAt=datetime.now(timezone.utc).isoformat(), consistentSnapshot=False,
        excludedGeneratedPaths=sorted(generated), resumed=bool(resume))))
    print(f'Download complete: {destination}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('configure')
    commands.add_parser('check')
    pull_command = commands.add_parser('pull')
    pull_command.add_argument('--resume', type=Path)
    commands.add_parser('verify-startup')
    release = commands.add_parser('deploy')
    release.add_argument('--commit', required=True)
    release.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.command == 'configure':
        configure()
        return
    config = settings()
    client, sftp = connect(config)
    try:
        if args.command == 'check':
            print(f'Connected. {len(sftp.listdir("."))} server-root items.')
            print('Migration receipt:', 'present' if read_remote(sftp, '.mandomc/bootstrap.json') else 'absent')
        elif args.command == 'pull':
            pull(config, sftp, args.resume)
        elif args.command == 'verify-startup':
            verify_startup(sftp)
        else:
            deploy(sftp, args.commit, args.apply)
    finally:
        sftp.close()
        client.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        # Do not print connection objects or configuration dictionaries.
        print(f'{type(exc).__name__}: {exc}', file=sys.stderr)
        sys.exit(1)
