import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / 'resource-pack/src'


class VehicleAssetsTests(unittest.TestCase):
    def test_every_generated_vehicle_texture_is_present(self):
        for vehicle in ('speederbike', 'xwing', 'tiefighter', 'uwing'):
            models = list((PACK / f'assets/modelengine/models/{vehicle}').glob('*.json'))
            self.assertTrue(models, vehicle)
            for model in models:
                for reference in json.loads(model.read_text()).get('textures', {}).values():
                    if reference.startswith('#'):
                        continue
                    namespace, path = reference.split(':', 1) if ':' in reference else ('minecraft', reference)
                    self.assertTrue((PACK / f'assets/{namespace}/textures/{path}.png').is_file(), (model, reference))

    def test_legacy_blueprints_have_fixed_identity_and_embedded_textures(self):
        for vehicle in ('speederbike', 'xwing', 'tiefighter', 'uwing'):
            model = json.loads((ROOT / f'server/config/common/modelengine/blueprints/{vehicle}.bbmodel').read_text())
            self.assertEqual(vehicle, model['model_identifier'])
            for texture in model['textures']:
                self.assertEqual('modelengine', texture['namespace'])
                self.assertIsInstance(texture['folder'], str)
                self.assertTrue(texture['source'].startswith('data:image/png;base64,'))
                self.assertNotIn('path', texture)
                self.assertNotIn('relative_path', texture)

    def test_catalog_icons_and_engine_sounds_are_shipped(self):
        sounds = json.loads((PACK / 'assets/mandomc/sounds.json').read_text())
        for vehicle in ('speederbike', 'xwing', 'tiefighter', 'uwing'):
            self.assertTrue((PACK / f'assets/mandomc/items/vehicles/{vehicle}.json').is_file())
            self.assertTrue((PACK / f'assets/mandomc/textures/item/vehicles/{vehicle}.png').is_file())
        for sound in ('speederbike_drive', 'xwing_fly', 'tie_fighter_fly'):
            self.assertIn(f'vehicles.{sound}', sounds)
            self.assertTrue((PACK / f'assets/mandomc/sounds/vehicles/{sound}.ogg').is_file())
