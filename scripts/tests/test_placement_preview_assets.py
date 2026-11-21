import json
from pathlib import Path
import unittest

from PIL import Image


class PlacementAppearanceTests(unittest.TestCase):
    def test_all_ten_variants_use_placed_geometry_and_partial_alpha(self):
        assets = Path(__file__).resolve().parents[2] / 'resource-pack/src/assets/mandomc'

        def resource(folder, name, suffix):
            return assets / folder / (name.removeprefix('mandomc:') + suffix)

        def read(folder, name):
            return json.loads(resource(folder, name, '.json').read_text())

        for stage in ('block', 'quarter', 'half', 'three', 'full'):
            route = 'legacy/rhydonium_barrel_' + stage
            placed = read('items', route)['model']['model']
            for color in ('blue', 'red'):
                with self.subTest(stage=stage, color=color):
                    key = read('items', f'placement/{color}/{route}')['model']['model']
                    preview = read('models', key)
                    self.assertEqual(set(preview), {'parent', 'textures'})
                    self.assertEqual(preview['parent'], placed)
                    texture = preview['textures']['0']
                    self.assertTrue(texture['force_translucent'])
                    original = read('models', placed)['textures']['0']
                    with Image.open(resource('textures', original, '.png')) as original_image:
                        source = original_image.convert('RGBA')
                    with Image.open(resource('textures', texture['sprite'], '.png')) as image:
                        self.assertEqual(image.size, source.size)
                        tinted = list(image.convert('RGBA').get_flattened_data())
                    for (_, _, _, original_alpha), (r, g, b, alpha) in zip(source.get_flattened_data(), tinted):
                        self.assertEqual(alpha, round(original_alpha * .48))
                        self.assertTrue(b > g > r if color == 'blue' else r > max(g, b))
