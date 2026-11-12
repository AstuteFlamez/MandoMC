import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import unittest
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / 'resource-pack/src'
ASSETS = PACK / 'assets/mandomc'
spec = importlib.util.spec_from_file_location('extract_boba', ROOT / 'scripts/extract-boba-jetpack.py')
extractor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extractor)


class BobaEquipmentAssets(unittest.TestCase):
    def test_original_rocket_geometry_and_uvs_reproduce_exactly(self):
        attached = json.loads((ASSETS / 'models/equipment/boba_jetpack_attached.json').read_text())
        self.assertEqual(attached, extractor.extract())
        self.assertEqual(len(attached['elements']), 14)

    def test_original_texture_bytes_preserved(self):
        self.assertEqual((ASSETS / 'textures/item/equipment/boba_jetpack.png').read_bytes(),
                         (PACK / 'assets/modelengine/textures/entity/models/boba_fett.png').read_bytes())

    def test_original_flame_icon_is_32_by_32_rgba_png(self):
        data = (ASSETS / 'textures/item/equipment/boba_flamethrower.png').read_bytes()
        self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
        width, height, depth, color = struct.unpack('>IIBB', data[16:26])
        self.assertEqual((width, height, depth, color), (32, 32, 8, 6))
        offset, compressed = 8, b''
        while offset < len(data):
            length = int.from_bytes(data[offset:offset + 4], 'big')
            if data[offset + 4:offset + 8] == b'IDAT':
                compressed += data[offset + 8:offset + 8 + length]
            offset += length + 12
        rows = zlib.decompress(compressed)
        self.assertTrue(all(rows[y * 129] == 0 for y in range(32)))
        alpha = [rows[y * 129 + 1 + x * 4 + 3] for y in range(32) for x in range(32)]
        self.assertIn(0, alpha)
        self.assertIn(255, alpha)

    def test_native_pixel_grid_reproduces_clean_palette_and_solid_alpha(self):
        spec = importlib.util.spec_from_file_location('flame_render', ROOT / 'scripts/render-boba-flamethrower.py')
        renderer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(renderer)
        self.assertEqual(renderer.render(), (ASSETS / 'textures/item/equipment/boba_flamethrower.png').read_bytes())
        source = json.loads(renderer.SOURCE.read_text())
        self.assertLessEqual(len(source['palette']), 16)
        self.assertTrue(all(bytes.fromhex(value)[3] in (0, 255) for value in source['palette'].values()))

    def test_gui_model_is_recentered_and_fits_inventory_slot(self):
        model = json.loads((ASSETS / 'models/equipment/boba_jetpack_gui.json').read_text())
        for axis in range(3):
            low = min(e['from'][axis] for e in model['elements'])
            high = max(e['to'][axis] for e in model['elements'])
            self.assertEqual((low + high) / 2, 8)
        self.assertLess((max(e['to'][1] for e in model['elements']) -
                         min(e['from'][1] for e in model['elements'])) * model['display']['gui']['scale'][1], 15)

    def test_both_handednesses_hide_duplicate_held_jetpack(self):
        item = json.loads((ASSETS / 'items/equipment/boba_jetpack.json').read_text())['model']
        case = next(c for c in item['cases'] if isinstance(c['when'], list))
        self.assertEqual(set(case['when']), {'firstperson_lefthand', 'firstperson_righthand',
                                             'thirdperson_lefthand', 'thirdperson_righthand'})
        self.assertEqual(case['model']['type'], 'minecraft:empty')

    def test_dropped_jetpack_is_full_scale_and_bottom_aligned(self):
        model = json.loads((ASSETS / 'models/equipment/boba_jetpack.json').read_text())
        ground = model['display']['ground']
        self.assertEqual(ground['scale'], [1, 1, 1])
        attached = json.loads((ASSETS / 'models/equipment/boba_jetpack_attached.json').read_text())
        self.assertEqual(min(e['from'][1] for e in attached['elements']) - 8 + ground['translation'][1], 0)

    def test_both_ids_and_attached_geometry_are_in_validated_pack(self):
        with zipfile.ZipFile(ROOT / 'resource-pack/build/MandoMC.zip') as pack:
            for name in ('boba_jetpack', 'boba_jetpack_attached', 'boba_flamethrower'):
                path = f'assets/mandomc/items/equipment/{name}.json'
                self.assertEqual(pack.read(path), (PACK / path).read_bytes())


if __name__ == '__main__':
    unittest.main()
