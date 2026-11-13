#!/usr/bin/env python3
"""Validate pack sources and build a repeatable ZIP, without network access."""
import hashlib
import json
from pathlib import Path
import re
import zipfile
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'resource-pack'
SOURCE = PACK / 'src'


def packed_bytes(path):
    """Compact transport bytes without changing resource values or texture pixels."""
    data = path.read_bytes()
    if path.suffix in ('.json', '.mcmeta'):
        return json.dumps(read_json(path), ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    if path.suffix != '.png':
        return data
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise SystemExit(f'Invalid PNG: {path.relative_to(ROOT)}')
    chunks = []
    offset = 8
    while offset < len(data):
        length = struct.unpack_from('>I', data, offset)[0]
        kind = data[offset + 4:offset + 8]
        end = offset + length + 12
        if end > len(data):
            raise SystemExit(f'Truncated PNG: {path.relative_to(ROOT)}')
        # Text, timestamps and camera metadata have no texture-rendering role.
        if kind not in (b'tEXt', b'zTXt', b'iTXt', b'tIME', b'eXIf'):
            chunks.append((kind, data[offset + 8:end - 4], data[offset:end]))
        offset = end
    if not any(kind == b'acTL' for kind, _, _ in chunks):
        compressed = b''.join(value for kind, value, _ in chunks if kind == b'IDAT')
        if compressed:
            compact = zlib.compress(zlib.decompress(compressed), level=9)
            original_size = sum(len(raw) for kind, _, raw in chunks if kind == b'IDAT')
            if len(compact) + 12 < original_size:
                encoded = struct.pack('>I', len(compact)) + b'IDAT' + compact
                encoded += struct.pack('>I', zlib.crc32(b'IDAT' + compact))
                first = next(i for i, (kind, _, _) in enumerate(chunks) if kind == b'IDAT')
                chunks = [entry for i, entry in enumerate(chunks) if entry[0] != b'IDAT' or i == first]
                chunks[first] = (b'IDAT', compact, encoded)
    return data[:8] + b''.join(raw for _, _, raw in chunks)


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
    dependencies = read_json(PACK / 'dependencies.json') if (PACK / 'dependencies.json').is_file() else {'packs': []}
    if folder == 'models' and any(reference in pack['models'] for pack in dependencies['packs']):
        return  # Exact dependency model allowlist; official pack is separately delivered.
    path = SOURCE / 'assets' / namespace / folder / (name + extension)
    if not path.is_file():
        raise SystemExit(f'Missing {folder} resource: {reference}')


def check_model_texture(reference):
    """An existing PNG is unusable if no item/block atlas stitches its sprite."""
    if reference.startswith('#'):
        return
    check_reference(reference, 'textures', '.png')
    namespace, _, name = reference.partition(':')
    if not name:
        namespace, name = 'minecraft', namespace
    if namespace == 'minecraft' or name.startswith(('item/', 'block/')):
        return
    for atlas_name in ('items', 'blocks'):
        atlas = SOURCE / 'assets/minecraft/atlases' / (atlas_name + '.json')
        if not atlas.is_file():
            continue
        for entry in read_json(atlas).get('sources', []):
            kind = entry.get('type', '').removeprefix('minecraft:')
            if kind == 'directory':
                prefix = entry.get('prefix', '')
                source = entry.get('source', '').rstrip('/') + '/'
                if name.startswith(prefix) and source == prefix:
                    return
            if kind == 'single' and entry.get('sprite', entry.get('resource')) == reference:
                return
    raise SystemExit(f'Texture is not stitched into an item/block atlas: {reference}. Use textures/item/.')


def check_model_aliases(path, visiting=None):
    """Resolve inherited face/particle aliases so purple particles cannot slip through."""
    visiting = set() if visiting is None else visiting
    if path in visiting:
        raise SystemExit(f'Cyclic model parent: {path}')
    data = read_json(path)
    textures = {}
    parent = data.get('parent')
    if parent:
        namespace, _, name = parent.partition(':')
        if not name:
            namespace, name = 'minecraft', namespace
        parent_path = SOURCE / 'assets' / namespace / 'models' / (name + '.json')
        if parent_path.is_file():
            textures.update(check_model_aliases(parent_path, visiting | {path}))
    textures.update(data.get('textures', {}))
    references = list(textures.values())
    for element in data.get('elements', []):
        references.extend(face['texture'] for face in element.get('faces', {}).values() if 'texture' in face)
    for reference in references:
        seen = set()
        while reference.startswith('#'):
            key = reference[1:]
            if key not in textures or key in seen:
                raise SystemExit(f'Unresolved texture alias {reference} in {path.relative_to(SOURCE)}')
            seen.add(key)
            reference = textures[key]
        check_model_texture(reference)
    return textures


def check_item_models(value):
    if isinstance(value, dict):
        # 26.1.2 Dye.calculate passes an undyed integer default through as ARGB.
        # RGB-only integers silently make GUI icons transparent; use -1 for white.
        if value.get('type') == 'minecraft:dye':
            color = value.get('default')
            if isinstance(color, int) and ((color >> 24) & 255) != 255:
                raise SystemExit('Dye tint default must be opaque ARGB (use -1 for white), not RGB-only.')
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
                check_model_aliases(path)
            if '/equipment/' in name:
                for layer_type, layers in data.get('layers', {}).items():
                    for layer in layers:
                        check_reference(layer['texture'], 'textures/entity/equipment/' + layer_type, '.png')
            if '/font/' in name:
                for provider in data.get('providers', []):
                    if provider.get('type') in ('bitmap', 'minecraft:bitmap'):
                        reference = provider['file']
                        if not reference.endswith('.png'):
                            raise SystemExit(f'Bitmap font texture must be PNG: {reference}')
                        check_reference(reference[:-4], 'textures', '.png')
            if path.name == 'sounds.json':
                namespace = path.parent.name
                for event in data.values():
                    for sound in event['sounds']:
                        reference = sound if isinstance(sound, str) else sound['name']
                        if isinstance(sound, dict) and sound.get('type', 'file') != 'file':
                            raise SystemExit(f'Unsupported sound event reference: {reference}')
                        if ':' not in reference:
                            reference = namespace + ':' + reference
                        check_reference(reference, 'sounds', '.ogg')
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
            archive.writestr(info, packed_bytes(path), compresslevel=9)
    temporary.replace(output)
    data = output.read_bytes()
    hashes = {name: hashlib.new(name, data).hexdigest() for name in ('sha1', 'sha256')}
    report = {
        'schemaVersion': 1, 'minecraftVersion': manifest['minecraftVersion'],
        'resourceFormat': manifest['resourceFormat'], 'files': len(files),
        'bytes': len(data), **hashes,
    }
    output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'Built {output.relative_to(ROOT)} ({len(files)} files, {len(data)} bytes)')
    print(f'SHA-1: {hashes["sha1"]}\nSHA-256: {hashes["sha256"]}')


if __name__ == '__main__':
    main()
