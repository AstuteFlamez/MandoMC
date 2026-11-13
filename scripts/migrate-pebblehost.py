#!/usr/bin/env python3
"""Stage a verified private runtime, then archive/activate it while Paper is stopped."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sftp_tools', ROOT / 'scripts/pebblehost.py')
transfer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transfer)


def inventory(runtime):
    metadata = json.loads((runtime / '.prepared.json').read_text())
    if metadata.get('schemaVersion') != 1:
        raise ValueError('Invalid prepared-runtime inventory')
    for name, expected in metadata['files'].items():
        source = runtime / transfer.safe_path(name)
        if source.is_symlink() or not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise ValueError(f'Prepared file changed or is missing: {name}')
    return metadata


def stage(sftp, runtime, metadata):
    commit = metadata['sourceCommit']
    base = f'.mandomc/incoming/{commit}'
    for index, (name, expected) in enumerate(metadata['files'].items(), 1):
        target = f'{base}/{name}'
        # Resume a failed staging run without retransmitting verified files.
        current = transfer.read_remote(sftp, target)
        if transfer.sha(current) != expected:
            transfer.atomic_write(sftp, target, (runtime / name).read_bytes())
        if index % 100 == 0:
            print(f'Staged and verified {index}/{len(metadata["files"])} files', flush=True)
    transfer.atomic_write(sftp, f'{base}/.prepared.json', json.dumps(metadata).encode())
    print('Staging complete. Live files have not changed.', flush=True)


def activate(sftp, metadata):
    commit = metadata['sourceCommit']
    if not __import__('re').fullmatch('[a-f0-9]{40}', commit):
        raise ValueError('Invalid prepared source commit')
    base = f'.mandomc/incoming/{commit}'
    staged = transfer.read_remote(sftp, f'{base}/.prepared.json')
    if staged is None or json.loads(staged) != metadata:
        raise ValueError('Stage and verify the complete runtime first')
    # Re-verify the complete staged inventory before moving a single live file.
    for name, expected in metadata['files'].items():
        if transfer.sha(transfer.read_remote(sftp, f'{base}/{name}')) != expected:
            raise ValueError(f'Staged checksum mismatch: {name}')
    keep = {'.mandomc', '.java', '.server-uuid', 'pebblehost.yml', 'start.sh'}
    host_before = transfer.read_remote(sftp, 'pebblehost.yml')
    if host_before is None:
        raise ValueError('Missing host startup settings')
    archive = f'.mandomc/legacy/{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}'
    transfer.mkdirs(sftp, archive)
    moved_old = []
    moved_new = []
    try:
        for item in sftp.listdir_attr('.'):
            name = transfer.safe_path(item.filename)
            if name not in keep:
                sftp.rename(name, f'{archive}/{name}')
                moved_old.append(name)
        for item in sftp.listdir_attr(base):
            name = transfer.safe_path(item.filename)
            if name == '.prepared.json':
                continue
            if name in keep or stat.S_ISLNK(item.st_mode):
                raise ValueError(f'Unsafe staged root entry: {name}')
            sftp.rename(f'{base}/{name}', name)
            moved_new.append(name)
        # Managed hashes use the update target name even though initial install
        # puts the JAR in plugins/ directly. The uploader understands that alias.
        files = transfer.payload()
        for target, data in files.items():
            installed = transfer.installed_path(target)
            if transfer.read_remote(sftp, installed) != data:
                raise ValueError(f'Installed content differs from the selected source: {installed}')
        transfer.atomic_write(sftp, archive + '/pebblehost.yml', host_before)
        transfer.atomic_write(sftp, 'pebblehost.yml', transfer.host_settings(host_before, files))
        receipt = dict(schemaVersion=1, sourceCommit=commit,
                       files={name: transfer.sha(data) for name, data in files.items()},
                       restartRequired=True, legacyArchive=archive)
        transfer.atomic_write(sftp, '.mandomc/deployed.json', json.dumps(receipt).encode())
        transfer.atomic_write(sftp, '.mandomc/bootstrap.json', json.dumps(dict(
            schemaVersion=1, sourceCommit=commit, verifiedStartup=False, legacyArchive=archive)).encode())
    except Exception:
        transfer.atomic_write(sftp, 'pebblehost.yml', host_before)
        # Reverse the directory cutover; retain all new and old bytes.
        for name in reversed(moved_new):
            sftp.rename(name, f'{base}/{name}')
        for name in reversed(moved_old):
            sftp.rename(f'{archive}/{name}', name)
        raise
    print(f'Activated {commit}. Old server retained at {archive}.', flush=True)
    print('No start/restart was sent. Set Java 25 and JAR server.jar, then start manually.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('stage', 'activate'))
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--server-stopped', action='store_true', help='Operator confirms PebbleHost is fully stopped')
    args = parser.parse_args()
    if args.action == 'activate' and not args.server_stopped:
        parser.error('Stop PebbleHost in the panel and confirm with --server-stopped before activation.')
    metadata = inventory(args.runtime)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if metadata['sourceCommit'] != head:
        raise SystemExit('Use the exact checkout that prepared the runtime.')
    client, sftp = transfer.connect(transfer.settings())
    transfer.mkdirs(sftp, '.mandomc')
    sftp.mkdir('.mandomc/deploy.lock')
    try:
        if args.action == 'stage':
            stage(sftp, args.runtime, metadata)
        else:
            activate(sftp, metadata)
    finally:
        sftp.rmdir('.mandomc/deploy.lock')
        sftp.close()
        client.close()
