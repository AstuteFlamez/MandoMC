#!/usr/bin/env python3
"""Install the hash-pinned upstream scanner into the ignored local tool directory."""
import hashlib
import io
import json
from pathlib import Path
import platform
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def install():
    system = {'Darwin': 'darwin', 'Linux': 'linux'}[platform.system()]
    machine = {'arm64': 'arm64', 'aarch64': 'arm64', 'x86_64': 'x64'}[platform.machine()]
    lock = json.loads((ROOT / 'deployment/gitleaks.lock.json').read_text())
    name = f'gitleaks_{lock["version"]}_{system}_{machine}.tar.gz'
    artifact = next(a for a in lock['artifacts'] if a['name'] == name)
    with urllib.request.urlopen(artifact['browser_download_url'], timeout=120) as response:
        data = response.read()
    if 'sha256:' + hashlib.sha256(data).hexdigest() != artifact['digest']:
        raise ValueError('Secret scanner checksum mismatch')
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
        member = archive.getmember('gitleaks')
        if not member.isfile():
            raise ValueError('Expected scanner binary')
        binary = archive.extractfile(member).read()
    target = ROOT / '.local/bin/gitleaks'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(binary)
    target.chmod(0o700)
    print('Installed checksum-verified secret scanner')

if __name__ == '__main__':
    install()
