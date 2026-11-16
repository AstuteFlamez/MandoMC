import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'resource-pack/art-sources/building-signs'
ASSETS = ROOT / 'resource-pack/src/assets/mandomc'
SIGNS = {'shops_sign': ('shops_sign_3x1', 1, [48, 16]),
         'leaderboards_sign': ('leaderboards_sign_5x1', 2, [80, 16]),
         'cantina_sign': ('cantina_sign_1x4', 2, [16, 64])}


class BuildingSignsTests(unittest.TestCase):
    def test_java_safe_geometry_restores_original_size_and_exact_uvs_pixels(self):
        for item, (name, scale, dimensions) in SIGNS.items():
            source = json.loads((SOURCE / f'{name}.json').read_text())
            model = json.loads((ASSETS / f'models/decor/{item}.json').read_text())
            self.assertEqual((SOURCE / f'{name}.png').read_bytes(),
                             (ASSETS / f'textures/item/decor/{item}.png').read_bytes())
            for before, after in zip(source['elements'], model['elements']):
                self.assertEqual(before['faces'], after['faces'])
                for key in ('from', 'to'):
                    self.assertEqual(before[key], [8 + (v - 8) * scale for v in after[key]])
                    self.assertTrue(all(-16 <= v <= 32 for v in after[key]))
            self.assertEqual(dimensions, [scale * (max(e['to'][i] for e in model['elements']) -
                min(e['from'][i] for e in model['elements'])) for i in (0, 1)])
            for pose, before in source['display'].items():
                self.assertEqual(before['scale'], [v / scale for v in model['display'][pose]['scale']])

    def test_command_only_acquisition(self):
        for base in ('plugin/src/main/resources', 'server/config/common'):
            for path in (ROOT / base).rglob('*.yml'):
                if path.name == 'building-signs.yml':
                    continue
                for item in SIGNS:
                    self.assertNotIn(item, path.read_text(), str(path))
