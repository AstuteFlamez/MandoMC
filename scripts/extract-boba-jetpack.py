#!/usr/bin/env python3
"""Reproduce the standalone Boba rocket/nozzle model without editing the original rig. No vendor dependency."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
PROVENANCE = ROOT / 'resource-pack/boba-equipment-sources.json'


def extract():
    source = json.loads(PROVENANCE.read_text())['jetpack']
    blueprint = json.loads((ROOT / source['source']).read_text())
    elements = []
    for element in blueprint['elements']:
        if element['uuid'] not in source['elements']:
            continue
        converted = {key: [element[key][0] + 8, element[key][1] - 10, element[key][2] + 8]
                     for key in ('from', 'to')}
        converted['faces'] = {}
        for face, values in element['faces'].items():
            if values.get('texture') is None:
                continue
            converted['faces'][face] = {'uv': [uv / 8 for uv in values['uv']], 'texture': '#0'}
            if values.get('rotation'):
                converted['faces'][face]['rotation'] = values['rotation']
        rotation = element.get('rotation', [0, 0, 0])
        if any(rotation):
            axis = next(i for i, value in enumerate(rotation) if value)
            converted['rotation'] = {'origin': [element['origin'][0] + 8, element['origin'][1] - 10,
                                                element['origin'][2] + 8], 'axis': 'xyz'[axis], 'angle': rotation[axis]}
        elements.append(converted)
    if len(elements) != 14:
        raise ValueError('Expected all six rocket cubes and eight nozzle cubes')
    return {'textures': {'0': 'mandomc:item/equipment/boba_jetpack', 'particle': '#0'}, 'elements': elements}


if __name__ == '__main__':
    destination = ROOT / 'resource-pack/src/assets/mandomc'
    (destination / 'models/equipment/boba_jetpack_attached.json').write_text(json.dumps(extract(), indent=2) + '\n')
    shutil.copyfile(ROOT / 'resource-pack/src/assets/modelengine/textures/entity/models/boba_fett.png',
                    destination / 'textures/item/equipment/boba_jetpack.png')
    print('Extracted original 14 rocket/nozzle elements; original Boba rig untouched.')
