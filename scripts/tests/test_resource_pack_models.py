import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('pack_build', Path(__file__).resolve().parents[1] / 'build-resource-pack.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class ItemTintTests(unittest.TestCase):
    def test_nested_rgb_only_dye_defaults_are_rejected(self):
        for color in (16777215, 10511680):
            with self.subTest(color=color), self.assertRaisesRegex(SystemExit, 'opaque ARGB'):
                build.check_item_models({'model': {'tints': [{'type': 'minecraft:dye', 'default': color}]}})

    def test_opaque_white_and_vanilla_leather_defaults_are_accepted(self):
        for color in (-1, -6265536):
            build.check_item_models({'type': 'minecraft:dye', 'default': color})

    def test_rgb_vector_defaults_and_equipment_colors_are_not_argb_integers(self):
        build.check_item_models({'type': 'minecraft:dye', 'default': [1, 1, 1]})
        build.check_item_models({'dyeable': {'color_when_undyed': 10511680}})


class ModelAtlasTests(unittest.TestCase):
    def setUp(self):
        import tempfile
        from unittest.mock import patch
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.source = Path(self.directory.name)
        self.addCleanup(patch.stopall)
        patch.object(build, 'SOURCE', self.source).start()

    def texture(self, name):
        path = self.source / 'assets/mandomc/textures' / (name + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'png fixture')

    def model(self, name, data):
        import json
        path = self.source / 'assets/mandomc/models' / (name + '.json')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data))
        return path

    def test_existing_legacy_png_outside_the_atlas_is_rejected(self):
        self.texture('custom/metals/beskar')
        with self.assertRaisesRegex(SystemExit, 'not stitched'):
            build.check_model_texture('mandomc:custom/metals/beskar')

    def test_standard_item_paths_are_stitched(self):
        self.texture('item/legacy/metals/beskar')
        build.check_model_texture('mandomc:item/legacy/metals/beskar')

    def test_explicit_modelengine_atlas_source_is_supported(self):
        import json
        self.texture('entity/models/test')
        path = self.source / 'assets/minecraft/atlases/blocks.json'
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({'sources': [{'type': 'minecraft:directory',
            'source': 'entity/models', 'prefix': 'entity/models/'}]}))
        build.check_model_texture('mandomc:entity/models/test')

    def test_missing_and_cyclic_aliases_are_rejected_but_inheritance_works(self):
        self.texture('item/test')
        self.model('item/parent', {'textures': {'base': 'mandomc:item/test'}})
        path = self.model('item/child', {'parent': 'mandomc:item/parent',
            'textures': {'particle': '#base'}, 'elements': [{'faces': {'north': {'texture': '#base'}}}]})
        build.check_model_aliases(path)
        path = self.model('item/missing', {'textures': {'particle': '#missing'}})
        with self.assertRaisesRegex(SystemExit, 'Unresolved texture alias'):
            build.check_model_aliases(path)
        path = self.model('item/cycle', {'textures': {'a': '#b', 'b': '#a'}})
        with self.assertRaisesRegex(SystemExit, 'Unresolved texture alias'):
            build.check_model_aliases(path)


class HelmetPoseTests(unittest.TestCase):
    def test_every_helmet_icon_has_horizontal_and_vertical_tilt(self):
        import json
        root = Path(__file__).resolve().parents[2] / 'resource-pack/src/assets/mandomc/models/item/armor'
        for family in ('plastoid', 'durasteel', 'beskar', 'robes'):
            with self.subTest(family=family):
                model = json.loads((root / (family + '_helmet.json')).read_text())
                x, y, _ = model['display']['gui']['rotation']
                self.assertNotEqual(0, x)
                self.assertNotEqual(0, y)
                if family != 'robes':
                    import math
                    self.assertGreater(-math.cos(math.radians(y)), 0, 'front face must face the GUI camera')
                    self.assertEqual([1.811] * 3, model['display']['head']['scale'])
