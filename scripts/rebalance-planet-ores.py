#!/usr/bin/env python3
"""Equalize mineable metal ore ratios on the four resource planets using stopped world files."""

import argparse
from collections import Counter, defaultdict
from contextlib import ExitStack
import fcntl
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile

import numpy as np
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent / 'map_tools'))
from frozen_land import baseline
from native_world import T, block_name, encode, packed, palette_tag, parse, records, unpack, write_records

MATERIALS = {'iron_ore': 75, 'redstone_ore': 15, 'diamond_ore': 10}
PLANETS = ('minecraft:hoth', 'minecraft:mandalore_review', 'minecraft:naboo', 'minecraft:tatooine_review')
MANDALORE = 'minecraft:mandalore_review'
OVERLAY = Path('plugins/MandoMC/data/planet-ore-terrain.yml')
MIGRATION = 'planet-ores-v2'


def family(block):
    return block.removeprefix('minecraft:').split('[', 1)[0].removeprefix('deepslate_')


def material(source, target):
    deepslate = source.startswith('minecraft:deepslate')
    props = '[lit=false]' if target == 'redstone_ore' else ''
    return 'minecraft:' + ('deepslate_' if deepslate else '') + target + props


def set_ratios(world, slots):
    """Keep the original total and change only surplus cells, in spatially grouped order."""
    count = sum(map(len, slots.values()))
    if count < 100:
        raise ValueError(f'Not enough surveyed metal ore slots on {world}: {count}')
    quota = {ore: count * weight // 100 for ore, weight in MATERIALS.items()}
    # Assign fractional rounding to the common metal.
    quota['iron_ore'] += count - sum(quota.values())
    surplus = []
    for ore, locations in slots.items():
        excess = len(locations) - quota.get(ore, 0)
        if excess <= 0:
            continue

        def order(location):
            x, y, z = location[0]
            # Nearby cells stay together when the patch repurposes part of a vein.
            return hashlib.blake2b(f'{world}:{x//4}:{y//4}:{z//4}'.encode(), digest_size=8).digest(), x, y, z

        surplus.extend(sorted(locations, key=order)[:excess])
    changes = {}
    for ore, target in quota.items():
        for _ in range(max(0, target - len(slots.get(ore, ())))):
            xyz, original, current = surplus.pop()
            changes[xyz] = original, current, material(current, ore)
    if surplus:
        raise AssertionError('Ore targets do not account for all surplus cells')
    return changes, quota


def frozen_ores(data, atlas, world, claimed, previous):
    slots = defaultdict(list)
    for dz, row in enumerate(atlas['rows']):
        for dx, zone in enumerate(row):
            cx, cz = atlas['min-chunk-x'] + dx, atlas['min-chunk-z'] + dz
            if zone != '.' or (world, cx, cz) in claimed:
                continue
            original_path = data / 'land-baselines' / f"{world.replace(':', '_')}_{cx}_{cz}.gz"
            if not original_path.is_file():
                continue
            bottom, palette, ids, staff = baseline(original_path)
            natural_ores = {i: name for i, name in enumerate(palette) if family(name) in MATERIALS}
            for index in np.flatnonzero(np.isin(ids, list(natural_ores))):
                index = int(index)
                if index < len(staff) and staff[index]:
                    continue
                xyz = (16 * cx + index % 16, bottom + index // 256, 16 * cz + index // 16 % 16)
                name = natural_ores[ids[index]]
                slots[family(name)].append((xyz, name, name))
            for xyz, (original, coal) in previous.get((cx, cz), {}).items():
                x, y, z = xyz
                index = (y - bottom) * 256 + (z % 16) * 16 + x % 16
                if not 0 <= index < len(ids) or palette[ids[index]] not in (original, coal):
                    raise ValueError(f'Mandalore coal overlay disagrees with frozen terrain at {xyz}')
                if index < len(staff) and staff[index]:
                    continue
                slots['coal_ore'].append((xyz, palette[ids[index]], coal))
    return slots


def region_patches(worldroot, world, changes):
    by_region = defaultdict(lambda: defaultdict(lambda: defaultdict(dict)))
    for (x, y, z), (expected, target) in changes.items():
        by_region[x // 512, z // 512][x // 16 % 32 + 32 * (z // 16 % 32)][y // 16][
            (y % 16) * 256 + (z % 16) * 16 + x % 16
        ] = expected, target
    for (rx, rz), changed_chunks in sorted(by_region.items()):
        path = worldroot / 'dimensions' / world.replace(':', '/') / 'region' / f'r.{rx}.{rz}.mca'
        region = records(path)
        originals = region.copy()
        count = 0
        for offset, changed_sections in changed_chunks.items():
            chunk = parse(region[offset])
            sections = {int(section['Y']): section for section in chunk['sections'] if 'block_states' in section}
            changed = False
            for sy, cells in changed_sections.items():
                if sy not in sections:
                    continue  # A missing section has no matching original ore.
                state = sections[sy]['block_states']
                palette = list(state['palette'])
                names = ['minecraft:' + block_name(tag) for tag in palette]
                indices = unpack(state).copy()
                edited = set()
                for index, (expected, target) in cells.items():
                    current = names[indices[index]]
                    # A nearby interaction can temporarily change the same redstone block's lit property.
                    if current == target or current.split('[', 1)[0] != expected.split('[', 1)[0]:
                        continue
                    if target not in names:
                        names.append(target)
                        palette.append(palette_tag(target.removeprefix('minecraft:')))
                    indices[index] = names.index(target)
                    edited.add(index)
                if not edited:
                    continue
                replacement = T.Compound({'palette': T.List[T.Compound](palette),
                                          'data': packed(indices, max(4, (len(palette) - 1).bit_length()))})
                if not np.array_equal(unpack(replacement), indices):
                    raise ValueError(f'Ore block encoding failed for {path} chunk {offset} section {sy}')
                sections[sy]['block_states'] = replacement
                changed = True
                count += len(edited)
            if changed:
                # Force the native relight path while retaining all other chunk data.
                chunk['isLightOn'] = T.Byte(0)
                chunk['Status'] = T.String('minecraft:features')
                chunk.pop('starlight.light_version', None)
                for section in chunk['sections']:
                    for key in ('BlockLight', 'SkyLight', 'starlight.skylight_state', 'starlight.blocklight_state'):
                        section.pop(key, None)
                region[offset] = encode(chunk)
        if region != originals:
            yield path, region, count


def plan(runtime, worldroot, output):
    data = runtime / 'plugins/MandoMC/data'
    config = data.parent
    terrain = yaml.safe_load((config / 'territory/maps.yml').read_text())
    atlases = {terrain['world']: terrain}
    atlases.update({atlas['world']: atlas for atlas in terrain['additional-maps'].values()})
    clans = yaml.safe_load((data / 'clans.yml').read_text())
    claimed = {(claim['world'], claim['x'], claim['z']) for claim in (clans.get('claims') or [])}
    coal = yaml.safe_load((data / 'coal-terrain.yml').read_text())
    if coal.get('migration-id') != 'planet-coal-v1':
        raise ValueError('Expected the existing planet-coal-v1 overlay')
    original_coal = defaultdict(dict)
    overlay = []
    for row in coal['changes']:
        world, coords, original, ore = row.split('|')
        if world != MANDALORE:
            overlay.append(row)
            continue
        x, y, z = map(int, coords.split(','))
        original_coal[x // 16, z // 16][x, y, z] = original, ore

    report = {'migration-id': MIGRATION, 'input-state': input_state(runtime), 'worlds': {}, 'files': []}
    for world in PLANETS:
        previous = original_coal if world == MANDALORE else {}
        slots = frozen_ores(data, atlases[world], world, claimed, previous)
        changed, targets = set_ratios(world, slots)
        patches = {(x, y, z): (source, target) for (x, y, z), (_, source, target) in changed.items()}
        for chunk, cells in previous.items():
            if (world, *chunk) in claimed:
                continue
            # Unlike repurposed wilderness cells, protected cave coal returns to its original host.
            atlas = atlases[world]
            cx, cz = chunk
            zone = atlas['rows'][cz - atlas['min-chunk-z']][cx - atlas['min-chunk-x']]
            if zone == 'W':
                patches.update({xyz: (ore, rock) for xyz, (rock, ore) in cells.items()})
        for (x, y, z), (original, _, target) in changed.items():
            overlay.append(f'{world}|{x},{y},{z}|{original}|{target}')
        count = 0
        for path, region, patched in region_patches(worldroot, world, patches):
            rel = path.relative_to(runtime)
            stage = output / 'staged' / rel
            write_records(stage, region)
            before = path.read_bytes()
            after = bytearray(stage.read_bytes())
            after[4096:8192] = before[4096:8192]
            stage.write_bytes(after)
            if records(stage) != region:
                raise ValueError(f'Region roundtrip mismatch at {rel}')
            report['files'].append(record(rel, before, after))
            count += patched
        report['worlds'][world] = {'frozen-metal-targets': targets, 'live-cells-changed': count,
                                    'live-identical-or-modified': len(patches) - count}
    after = json.dumps({'schema-version': 2, 'migration-id': MIGRATION, 'changes': sorted(overlay)},
                       indent=2).encode() + b'\n'
    stage = output / 'staged' / OVERLAY
    stage.parent.mkdir(parents=True, exist_ok=True)
    stage.write_bytes(after)
    report['files'].append(record(OVERLAY, None, after))
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def digest(data):
    return None if data is None else hashlib.sha256(data).hexdigest()


def record(path, before, after):
    return {'path': str(path), 'before': digest(before), 'after': digest(after)}


def input_state(runtime):
    """Bind a staged plan to the claims, frozen terrain and staff exclusions used to make it."""
    config = runtime / 'plugins/MandoMC'
    data = config / 'data'
    state = hashlib.sha256()
    paths = [runtime / 'server.properties', config / 'territory/maps.yml',
             data / 'clans.yml', data / 'coal-terrain.yml',
             *sorted((data / 'land-baselines').glob('*.gz*'))]
    for path in paths:
        state.update(os.fsencode(path.relative_to(runtime)))
        state.update(hashlib.sha256(path.read_bytes()).digest())
    return state.hexdigest()


def apply(runtime, output, report):
    # Check the whole staged transaction first. An interruption can be resumed with the same output directory.
    if report['input-state'] != input_state(runtime):
        raise ValueError('Terrain inputs changed after staging; plan again in a new output directory')
    paths = []
    for entry in report['files']:
        path = Path(entry['path'])
        if path.is_absolute() or '..' in path.parts:
            raise ValueError(f'Invalid migration path {path}')
        target, stage = runtime / path, output / 'staged' / path
        after, before = stage.read_bytes(), target.read_bytes() if target.exists() else None
        if digest(after) != entry['after'] or digest(before) not in (entry['before'], entry['after']):
            raise ValueError(f'Terrain changed after staging: {path}')
        if digest(before) == entry['after']:
            continue
        if before is not None:
            backup = output / 'backup' / path
            backup.parent.mkdir(parents=True, exist_ok=True)
            if not backup.exists():
                with backup.open('xb') as stream:
                    stream.write(before)
                    stream.flush()
                    os.fsync(stream.fileno())
            if digest(backup.read_bytes()) != entry['before']:
                raise ValueError(f'Invalid migration backup: {path}')
        paths.append((target, stage))
    for target, stage in paths:
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as pending:
            temp = Path(pending.name)
            try:
                os.fchmod(pending.fileno(), target.stat().st_mode & 0o777 if target.exists() else 0o600)
                pending.write(stage.read_bytes())
                pending.flush()
                os.fsync(pending.fileno())
                os.replace(temp, target)
            finally:
                temp.unlink(missing_ok=True)


def migrate(runtime, output, write=False):
    settings = dict(line.split('=', 1) for line in (runtime / 'server.properties').read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    worldroot = runtime / settings.get('level-name', 'world')
    with ExitStack() as resources:
        if write:
            for path in sorted(set(worldroot.rglob('session.lock')) | {worldroot / 'session.lock'}):
                lock = resources.enter_context(path.open('r+b'))
                fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        existing = output / 'report.json'
        if (runtime / OVERLAY).exists():
            overlay = yaml.safe_load((runtime / OVERLAY).read_text())
            if overlay.get('migration-id') != MIGRATION or overlay.get('schema-version') != 2:
                raise ValueError('Unknown existing ore terrain overlay')
            return {'migration-id': MIGRATION, 'worlds': {}, 'files': []}
        if existing.is_file():
            report = json.loads(existing.read_text())
            if report.get('migration-id') != MIGRATION:
                raise ValueError('Output directory contains a different migration')
        else:
            output.mkdir(parents=True, exist_ok=True)
            report = plan(runtime, worldroot, output)
        if write:
            apply(runtime, output, report)
        return report


if __name__ == '__main__':
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--runtime', type=Path, required=True)
    cli.add_argument('--output', type=Path, required=True)
    cli.add_argument('--apply', action='store_true')
    args = cli.parse_args()
    result = migrate(args.runtime.resolve(), args.output.resolve(), args.apply)
    print(json.dumps(result['worlds'], indent=2))
