import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prepare_production', Path(__file__).parents[1] / 'prepare-production.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PreparedPathTests(unittest.TestCase):
    def test_exact_manifest_case_is_preserved_across_mac_and_linux(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = root / 'plugins/MythicMobs/mobs'
            original.mkdir(parents=True)
            (original / 'Boba-Mob.yml').write_bytes(b'unchanged mob data')
            result = module.canonical_target(root, 'plugins/MythicMobs/Mobs/boba-mob.yml')
            self.assertEqual(result.read_bytes(), b'unchanged mob data')
            self.assertIn('Mobs', [p.name for p in (root / 'plugins/MythicMobs').iterdir()])
            self.assertIn('boba-mob.yml', [p.name for p in result.parent.iterdir()])
            self.assertNotIn('mobs', [p.name for p in (root / 'plugins/MythicMobs').iterdir()])

    def test_missing_parent_is_returned_without_touching_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertEqual(module.canonical_target(root, 'plugins/New/config.yml'), root / 'plugins/New/config.yml')
            self.assertEqual(list(root.iterdir()), [])

    def test_unsafe_targets_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            for relative in ('/tmp/outside', '../outside', 'a/../outside', 'a//b', './config.yml'):
                with self.subTest(relative=relative), self.assertRaises(ValueError):
                    module.canonical_target(Path(directory), relative)
