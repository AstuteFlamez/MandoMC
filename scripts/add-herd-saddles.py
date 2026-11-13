#!/usr/bin/env python3
"""Add rigid leather saddle geometry/driver locators without changing existing cubes, UVs or animations."""
import copy
import json
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
BLUEPRINTS = ROOT / 'server/config/common/modelengine/blueprints'
leather = json.loads((BLUEPRINTS / 'bantha.bbmodel').read_text())['textures'][0]

def identity(species, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f'mandomc:herd:{species}:{name}'))

for species, body_name, seat, shapes in [
    ('bantha', 'body', [-1, 43, 8], [
        ([-12, 39, -3], [10, 42, 18]), ([-12, 42, -4], [10, 46, 0]),
        ([-12, 42, 15], [10, 46, 19]), ([-13.25, 18, 3], [-11.75, 40, 6]),
        ([9.75, 18, 3], [11.25, 40, 6]), ([-12, 17, 3], [10, 19, 6])]),
    ('dewback', 'Body', [0, 37, -10], [
        ([-9, 33, -21], [9, 36, 1]), ([-10, 36, -22], [10, 40, -18]),
        ([-10, 36, -2], [10, 40, 2]), ([-10.5, 10, -12], [-9, 34, -9]),
        ([9, 10, -12], [10.5, 34, -9]), ([-9, 9, -12], [9, 11, -9])]),
]:
    path = BLUEPRINTS / f'{species}.bbmodel'
    data = json.loads(path.read_text())
    groups = {group['uuid']: group for group in data.get('groups', [])}
    def find(nodes):
        for node in nodes:
            if not isinstance(node, dict):
                continue
            group = groups.get(node['uuid'], node)
            if group.get('name') == body_name:
                return node
            found = find(node.get('children', []))
            if found:
                return found
    body = find(data['outliner'])
    if body is None:
        raise SystemExit(f'{species}: missing animated body')
    if any(isinstance(n, dict) and groups.get(n['uuid'], n).get('name') == 'saddle' for n in body['children']):
        continue
    texture = copy.deepcopy(leather)
    texture.update(name='herd_saddle.png', id=str(len(data['textures'])), uuid=identity(species, 'texture'),
                   namespace='modelengine', folder='entity/models')
    texture_index = len(data['textures'])
    data['textures'].append(texture)
    cubes = []
    for index, (start, end) in enumerate(shapes):
        cube = dict(name=f'saddle_{index}', type='cube', uuid=identity(species, f'cube-{index}'),
                    box_uv=False, from_=start, to=end, origin=seat, autouv=0, color=0,
                    faces={face: dict(uv=[2, 2, 14, 14], texture=texture_index)
                           for face in ['north', 'east', 'south', 'west', 'up', 'down']})
        cube['from'] = cube.pop('from_')
        data['elements'].append(cube)
        cubes.append(cube['uuid'])
    for name, children in [('saddle', cubes), ('mount', [])]:
        group = dict(name=name, uuid=identity(species, name), origin=seat, rotation=[0, 0, 0],
                     export=True, visibility=True, children=children, color=0, mirror_uv=False, autouv=0)
        if groups:
            data['groups'].append({**group, 'children': []})
            body['children'].append(dict(uuid=group['uuid'], children=children, isOpen=True))
        else:
            body['children'].append(group)
    path.write_text(json.dumps(data, separators=(',', ':')) + '\n')
    print(f'{species}: added hidden-at-runtime saddle and driver locator')
