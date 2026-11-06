#!/usr/bin/env python3
"""Validate pack sources and build a repeatable ZIP, without network access."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'resource-pack'
SOURCE = PACK / 'src'


def read_json(path):
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except (ValueError, OSError) as error:
        raise SystemExit(f'{path.relative_to(ROOT)}: {error}') from error


def check_reference(reference, folder, extension):
    if reference.startswith('#'):
        return
    namespace, _, name = reference.partition(':')
    if not name:
        namespace, name = 'minecraft', namespace
    if namespace == 'minecraft':
        return  # Vanilla assets come from the client.
    path = SOURCE / 'assets' / namespace / folder / (name + extension)
    if not path.is_file():
        raise SystemExit(f'Missing {folder} resource: {reference}')


def check_item_models(value):
    if isinstance(value, dict):
        if value.get('type') == 'minecraft:model':
            check_reference(value['model'], 'models', '.json')
        for child in value.values():
            check_item_models(child)
    elif isinstance(value, list):
        for child in value:
            check_item_models(child)


def main():
    manifest = read_json(PACK / 'sources.json')
    paper = read_json(ROOT / 'server/paper.lock.json')
    metadata = read_json(SOURCE / 'pack.mcmeta')['pack']
    if manifest['minecraftVersion'] != paper['minecraftVersion']:
        raise SystemExit('Pack target differs from the pinned Paper version.')
    for key in ('min_format', 'max_format'):
        if metadata[key] != manifest['resourceFormat']:
            raise SystemExit(f'pack.mcmeta {key} differs from sources.json.')

    files = sorted(p for p in SOURCE.rglob('*') if p.is_file())
    for path in files:
        name = path.relative_to(SOURCE).as_posix()
        if path.is_symlink() or not re.fullmatch(r'[a-z0-9_./-]+', name):
            raise SystemExit(f'Invalid pack path: {name}')
        if path.suffix in ('.json', '.mcmeta'):
            data = read_json(path)
            if '/models/' in name:
                if 'parent' in data:
                    check_reference(data['parent'], 'models', '.json')
                for texture in data.get('textures', {}).values():
                    check_reference(texture, 'textures', '.png')
            if '/items/' in name:
                check_item_models(data)

    output = PACK / 'build' / 'MandoMC.zip'
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.zip.tmp')
    with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(SOURCE).as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())
    temporary.replace(output)
    data = output.read_bytes()
    hashes = {name: hashlib.new(name, data).hexdigest() for name in ('sha1', 'sha256')}
    report = {'minecraftVersion': manifest['minecraftVersion'], 'files': len(files), **hashes}
    output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'Built {output.relative_to(ROOT)} ({len(files)} files, {len(data)} bytes)')
    print(f'SHA-1: {hashes["sha1"]}\nSHA-256: {hashes["sha256"]}')


if __name__ == '__main__':
    main()
