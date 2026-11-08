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
