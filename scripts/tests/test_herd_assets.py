import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class HerdAssetsTests(unittest.TestCase):
    def test_saddles_preserve_all_original_geometry_uvs_and_keyframes(self):
        expected = {
            'bantha': '446d2db4799c19e0c5677a14d061d031934470c6d3c6e6c6820dbb7323841609',
            'dewback': '4723dd134e31b7b1b4ab5d9ad6c0dd069246ee1b9b2217646d3b76a9d3e9e217',
        }
        for name, digest in expected.items():
            with self.subTest(species=name):
                data = json.loads((ROOT / f'server/config/common/modelengine/blueprints/{name}.bbmodel').read_text())
                original = {'elements': [cube for cube in data['elements'] if not cube['name'].startswith('saddle_')],
                            'animations': data['animations'], 'texture': data['textures'][0]}
                actual = hashlib.sha256(json.dumps(original, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
                self.assertEqual(digest, actual)

    def test_each_body_has_one_saddle_and_an_empty_driver_locator(self):
        for name in ['bantha', 'dewback']:
            with self.subTest(species=name):
                data = json.loads((ROOT / f'server/config/common/modelengine/blueprints/{name}.bbmodel').read_text())
                groups = {group['uuid']: group for group in data.get('groups', [])}
                def children(nodes):
                    result = []
                    for node in nodes:
                        if isinstance(node, dict):
                            group = groups.get(node['uuid'], node)
                            result.append((group['name'], node.get('children', [])))
                            result.extend(children(node.get('children', [])))
                    return result
                nodes = dict(children(data['outliner']))
                self.assertEqual(6, len(nodes['saddle']))
                self.assertEqual([], nodes['mount'])
                self.assertEqual('herd_saddle.png', data['textures'][1]['name'])
                self.assertTrue(all(cube['faces']['north']['texture'] == 1 for cube in data['elements']
                                    if cube['name'].startswith('saddle_')))
