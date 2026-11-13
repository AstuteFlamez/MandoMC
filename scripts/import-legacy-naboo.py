#!/usr/bin/env python3
"""Copy the selected legacy Naboo terrain into a private Paper dimension."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import nbtlib
from nbtlib import tag as T


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit('Refusing to replace an existing dimension')
    regions = [args.source / 'region' / f'r.{x}.{z}.mca' for x in (-27, -26) for z in (6, 7)]
    if not regions:
        raise SystemExit('Legacy terrain is missing')
    (args.output / 'region').mkdir(parents=True)
    receipt = {}
    for source in regions:
        target = args.output / 'region' / source.name
        shutil.copy2(source, target)
        checksum = hashlib.sha256(source.read_bytes()).hexdigest()
        assert hashlib.sha256(target.read_bytes()).hexdigest() == checksum
        receipt[source.name] = checksum
    (args.output / 'data/paper').mkdir(parents=True)
    nbtlib.File({'DataVersion': T.Int(4790), 'data': T.Compound({
        'difficulty_settings': T.Compound({'locked': T.Byte(0), 'difficulty': T.String('normal'), 'hardcore': T.Byte(0)}),
        'spawn': T.Compound({'pos': T.IntArray([-13551, 101, 3359]), 'pitch': T.Float(0), 'yaw': T.Float(180)}),
        'initialized': T.Byte(1), 'game_time': T.Long(6000), 'game_type': T.Int(0)
    })}).save(args.output / 'data/paper/level_overrides.dat', gzipped=True)
    worldgen = nbtlib.load(args.reference)
    worldgen['data']['generate_structures'] = T.Byte(0)
    worldgen['data']['dimensions']['minecraft:overworld']['generator'] = T.Compound({
        'type': T.String('minecraft:flat'), 'settings': T.Compound({
            'biome': T.String('minecraft:the_void'), 'features': T.Byte(0), 'lakes': T.Byte(0),
            'layers': T.List[T.Compound]([T.Compound({'block': T.String('minecraft:air'), 'height': T.Int(1)})]),
            'structure_overrides': T.List[T.String]([])})})
    (args.output / 'data/minecraft').mkdir()
    worldgen.save(args.output / 'data/minecraft/world_gen_settings.dat', gzipped=True)
    record = {'world': 'minecraft:naboo', 'legacy-world': 'Naboo', 'regions': receipt,
              'terrain-byte-identical': True, 'legacy-entities-and-player-data-imported': False,
              'new-chunks': 'void', 'arrival': [-13550.5, 101.0, 3359.5], 'safe-chunk': [-847, 209]}
    (args.output.parent / 'naboo-import.json').write_text(json.dumps(record, indent=2) + '\n')
    print(f'Staged {len(regions)} unchanged terrain regions as Naboo')


if __name__ == '__main__':
    main()
