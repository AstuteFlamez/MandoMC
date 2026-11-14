#!/usr/bin/env python3
"""Select a published pack, verify its bytes, and update only pack properties."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import socket
import sys
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAX_PACK_BYTES = 256 * 1024 * 1024


def fetch(url, maximum):
    request = urllib.request.Request(url, headers={'User-Agent': 'MandoMC-pack-sync'})
    with urllib.request.urlopen(request, timeout=15) as response:
        data = response.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError('Download exceeds the allowed size')
    return data


def verify_pack(data, manifest, version, resource_format, repository):
    if manifest['schemaVersion'] != 1 or manifest['minecraftVersion'] != version:
        raise ValueError('Pack manifest has the wrong schema or Minecraft version')
    if manifest['resourceFormat'] != resource_format:
        raise ValueError('Pack resource format differs from the local target')
    prefix = f'https://github.com/{repository}/releases/download/resource-pack-{version}-'
    if not re.fullmatch(re.escape(prefix) + r'[0-9a-f]{12}/MandoMC\.zip', manifest['url']):
        raise ValueError('Pack URL is not a versioned download in the configured repository')
    for algorithm, length in [('sha1', 40), ('sha256', 64)]:
        expected = manifest[algorithm]
        if not re.fullmatch(r'[0-9a-f]{' + str(length) + '}', expected):
            raise ValueError(f'Invalid {algorithm} hash')
        if hashlib.new(algorithm, data).hexdigest() != expected:
            raise ValueError(f'Pack {algorithm} checksum mismatch')
    if len(data) != manifest['bytes']:
        raise ValueError('Pack size differs from manifest')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        metadata = json.loads(archive.read('pack.mcmeta'))['pack']
        if metadata['min_format'] != resource_format or metadata['max_format'] != resource_format:
            raise ValueError('ZIP metadata does not match the advertised resource format')
        if archive.testzip() is not None:
            raise ValueError('ZIP integrity check failed')


def select_release(releases, version):
    pattern = re.compile(r'resource-pack-' + re.escape(version) + r'-[0-9a-f]{12}')
    eligible = [release for release in releases
                if not release['draft'] and pattern.fullmatch(release['tag_name'])]
    if not eligible:
        raise ValueError(f'No published pack for Minecraft {version}; wait for the publishing workflow')
    return max(eligible, key=lambda release: release['published_at'])


def latest_pack(repository, version, cache_dir):
    releases = json.loads(fetch(f'https://api.github.com/repos/{repository}/releases?per_page=100', 4 * 1024 * 1024))
    release = select_release(releases, version)
    base = f'https://github.com/{repository}/releases/download/{release["tag_name"]}/'
    assets = {asset['name']: asset['browser_download_url'] for asset in release['assets']}
    if assets.get('MandoMC.json') != base + 'MandoMC.json' or assets.get('MandoMC.zip') != base + 'MandoMC.zip':
        raise ValueError('Published release is missing its pack or manifest')
    manifest = json.loads(fetch(assets['MandoMC.json'], 64 * 1024))
    manifest['url'] = assets['MandoMC.zip']
    # Validate the cache filename before using it as a path.
    if not re.fullmatch(r'[0-9a-f]{64}', manifest['sha256']):
        raise ValueError('Invalid SHA-256 in published manifest')
    cached_zip = cache_dir / (manifest['sha256'] + '.zip')
    data = cached_zip.read_bytes() if cached_zip.is_file() else fetch(manifest['url'], MAX_PACK_BYTES)
    return manifest, data


def render_properties(text, settings):
    lines = []
    for line in text.splitlines():
        key = line.split('=', 1)[0].strip()
        if key not in settings:
            lines.append(line)
    lines.extend(f'{key}={value}' for key, value in settings.items())
    return '\n'.join(lines) + '\n'


def pinned_pack(path, cache_dir, repository, version, offline=False):
    manifest = json.loads(path.read_text())
    prefix = f'https://github.com/{repository}/releases/download/resource-pack-{version}-'
    if not re.fullmatch(re.escape(prefix) + r'[0-9a-f]{12}/MandoMC\.zip', manifest.get('url', '')):
        raise ValueError('Pinned pack URL is not a versioned download in the configured repository')
    checksum = manifest.get('sha256', '')
    if not re.fullmatch(r'[0-9a-f]{64}', checksum):
        raise ValueError('Invalid pinned pack hash')
    cached = cache_dir / (checksum + '.zip')
    if not cached.is_file() and offline:
        raise ValueError('Pinned pack is not cached')
    data = cached.read_bytes() if cached.is_file() else fetch(manifest['url'], MAX_PACK_BYTES)
    return manifest, data


def atomic_write(path, data):
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_bytes(data)
    temporary.replace(path)


def assert_stopped(properties):
    values = dict(line.split('=', 1) for line in properties.splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
    host = values.get('server-ip', '').strip() or '127.0.0.1'
    port = int(values.get('server-port', '25565'))
    try:
        connection = socket.create_connection((host, port), timeout=1)
    except ConnectionRefusedError:
        return
    except OSError as error:
        raise ValueError(f'Cannot confirm the server is stopped: {error}') from error
    connection.close()
    raise ValueError('Stop the server before changing its resource-pack properties')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline', action='store_true', help='Use the last verified cached release')
    parser.add_argument('--properties', type=Path, default=ROOT / 'server/runtime/server.properties')
    args = parser.parse_args()
    config = json.loads((ROOT / 'resource-pack/hosting.json').read_text())
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', config['repository']):
        raise ValueError('Invalid GitHub repository')
    pack_id = str(uuid.UUID(config['packId']))
    if not isinstance(config['required'], bool):
        raise ValueError('Pack required flag must be a boolean')
    target = json.loads((ROOT / 'resource-pack/sources.json').read_text())
    paper = json.loads((ROOT / 'server/paper.lock.json').read_text())
    version = paper['minecraftVersion']
    if target['minecraftVersion'] != version:
        raise ValueError('Pack and server targets disagree')
    properties = args.properties.read_text()
    assert_stopped(properties)
    cache_dir = args.properties.parent / 'resource-packs'
    cache_manifest = cache_dir / 'current.json'

    pin = cache_dir / 'pinned.json'
    if pin.is_file():
        manifest, data = pinned_pack(pin, cache_dir, config['repository'], version, args.offline)
        verify_pack(data, manifest, version, target['resourceFormat'], config['repository'])
        print(f'Using explicitly pinned development pack: {manifest["url"]}')
    else:
        try:
            if args.offline:
                raise OSError('Offline mode requested')
            manifest, data = latest_pack(config['repository'], version, cache_dir)
            verify_pack(data, manifest, version, target['resourceFormat'], config['repository'])
        except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
            if not cache_manifest.is_file():
                raise ValueError(f'No verified pack is cached. {error}') from error
            print(f'Pack refresh unavailable ({error}); using the last verified release.', file=sys.stderr)
            manifest = json.loads(cache_manifest.read_text())
            if not re.fullmatch(r'[0-9a-f]{64}', manifest['sha256']):
                raise ValueError('Invalid cached pack hash')
            data = (cache_dir / (manifest['sha256'] + '.zip')).read_bytes()
            verify_pack(data, manifest, version, target['resourceFormat'], config['repository'])

    cache_dir.mkdir(parents=True, exist_ok=True)
    atomic_write(cache_dir / (manifest['sha256'] + '.zip'), data)
    atomic_write(cache_manifest, (json.dumps(manifest, indent=2) + '\n').encode())
    settings = {
        'resource-pack': manifest['url'],
        'resource-pack-sha1': manifest['sha1'],
        'resource-pack-id': pack_id,
        'require-resource-pack': str(config['required']).lower(),
        'resource-pack-prompt': json.dumps({'text': config['prompt']}, ensure_ascii=True),
    }
    atomic_write(args.properties, render_properties(properties, settings).encode())
    print(f'Resource pack configured: {manifest["url"]}')
    print(f'SHA-1: {manifest["sha1"]}')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        raise SystemExit(f'Resource-pack setup failed: {error}') from error
