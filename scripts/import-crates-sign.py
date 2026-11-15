#!/usr/bin/env python3
"""Export the supplied Blockbench Java model without changing geometry or pixels."""
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'resource-pack/art-sources/crates-sign/crates.bbmodel'
ASSETS = ROOT / 'resource-pack/src/assets/mandomc'


def export():
    source = json.loads(SOURCE.read_text())
    texture = source['textures'][0]
    model = {'credit': 'User-supplied crates.bbmodel; exported by import-crates-sign.py',
             'ambientocclusion': source['ambientocclusion'],
             'textures': {'0': 'mandomc:item/decor/crates_sign', 'particle': '#0'},
             'elements': [], 'display': source['display']}
    for element in source['elements']:
        if not element.get('export', True):
            continue
        cube = {key: element[key] for key in ('name', 'from', 'to')}
        rotation = element.get('rotation', [0, 0, 0])
        axes = [i for i, angle in enumerate(rotation) if angle]
        if len(axes) > 1:
            raise ValueError('Java block elements require single-axis rotation')
        if axes:
            axis = axes[0]
            cube['rotation'] = {'origin': element['origin'], 'axis': 'xyz'[axis],
                                'angle': rotation[axis], 'rescale': element.get('rescale', False)}
        cube['faces'] = {}
        for direction, face in element['faces'].items():
            if face.get('texture') is None:
                continue
            if face['texture'] != 0:
                raise ValueError('Unexpected texture reference')
            uv = [value * 16 / texture['uv_width' if i % 2 == 0 else 'uv_height']
                  for i, value in enumerate(face['uv'])]
            cube['faces'][direction] = {'uv': uv, 'texture': '#0'}
            if face.get('rotation'):
                cube['faces'][direction]['rotation'] = face['rotation']
        model['elements'].append(cube)
    outputs = {
        ASSETS / 'models/decor/crates_sign.json': (json.dumps(model, indent=2) + '\n').encode(),
        ASSETS / 'items/decor/crates_sign.json': (json.dumps({'model': {'type': 'minecraft:model',
            'model': 'mandomc:decor/crates_sign'}}, indent=2) + '\n').encode(),
        ASSETS / 'textures/item/decor/crates_sign.png': base64.b64decode(texture['source'].split(',', 1)[1]),
    }
    for path, data in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


if __name__ == '__main__':
    export()
