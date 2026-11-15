import base64
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'resource-pack/src/assets/mandomc'


class CratesSignTests(unittest.TestCase):
    def test_export_retains_geometry_rotation_pixels_and_normalizes_uvs(self):
        source = json.loads((ROOT / 'resource-pack/art-sources/crates-sign/crates.bbmodel').read_text())
        model = json.loads((ASSETS / 'models/decor/crates_sign.json').read_text())
        self.assertEqual(6, len(model['elements']))
        self.assertEqual(source['display'], model['display'])
        for original, exported in zip(source['elements'], model['elements']):
            self.assertEqual(original['from'], exported['from'])
            self.assertEqual(original['to'], exported['to'])
            if 'rotation' in original:
                self.assertEqual(original['rotation'][1], exported['rotation']['angle'])
                self.assertEqual(original['origin'], exported['rotation']['origin'])
            for face, data in original['faces'].items():
                if data['texture'] is None:
                    self.assertNotIn(face, exported['faces'])
                else:
                    self.assertEqual([v / 8 for v in data['uv']], exported['faces'][face]['uv'])
        self.assertEqual([-16, 0, 0], model['elements'][0]['from'])
        self.assertEqual([32, 16, 1.5], model['elements'][0]['to'])
        self.assertEqual(base64.b64decode(source['textures'][0]['source'].split(',')[1]),
                         (ASSETS / 'textures/item/decor/crates_sign.png').read_bytes())

    def test_sign_has_no_recipe_or_reward_source(self):
        for base in ('plugin/src/main/resources', 'server/config/common'):
            for path in (ROOT / base).rglob('*.yml'):
                if path.name == 'crates-sign.yml':
                    continue
                self.assertNotIn('crates_sign', path.read_text(), str(path))
