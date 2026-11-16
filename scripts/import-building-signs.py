#!/usr/bin/env python3
"""Import the supplied Java models, retaining pixels and already-normalized UVs."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'resource-pack/art-sources/building-signs'
ASSETS = ROOT / 'resource-pack/src/assets/mandomc'
SIGNS = {'shops_sign': ('shops_sign_3x1', 1),
         'leaderboards_sign': ('leaderboards_sign_5x1', 2),
         'cantina_sign': ('cantina_sign_1x4', 2)}


def export():
    for item, (source, scale) in SIGNS.items():
        model = json.loads((SOURCE / f'{source}.json').read_text())
        model.pop('groups', None)
        model['textures'] = {'0': f'mandomc:item/decor/{item}', 'particle': '#0'}
        # Keep every element within Java's -16..32 range. The decoration display
        # restores full size; item poses compensate by the same scale factor.
        for element in model['elements']:
            for key in ('from', 'to'):
                element[key] = [8 + (v - 8) / scale for v in element[key]]
            if 'rotation' in element:
                element['rotation']['origin'] = [8 + (v - 8) / scale for v in element['rotation']['origin']]
        for pose in model.get('display', {}).values():
            pose['scale'] = [v * scale for v in pose.get('scale', [1, 1, 1])]
        outputs = {
            ASSETS / f'models/decor/{item}.json': (json.dumps(model, indent=2) + '\n').encode(),
            ASSETS / f'items/decor/{item}.json': (json.dumps({'model': {'type': 'minecraft:model', 'model': f'mandomc:decor/{item}'}}, indent=2) + '\n').encode(),
            ASSETS / f'textures/item/decor/{item}.png': (SOURCE / f'{source}.png').read_bytes(),
        }
        for path, data in outputs.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)


if __name__ == '__main__':
    export()
