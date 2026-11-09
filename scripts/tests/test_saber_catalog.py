"""Validate complete catalog coverage and official-pack reference ownership."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SaberCatalogTests(unittest.TestCase):
    def test_all_named_variants_have_held_hilt_and_blocking_references(self):
        manifest = json.loads((ROOT / 'resource-pack/sabers.json').read_text())['sabers']
        dependencies = json.loads((ROOT / 'resource-pack/dependencies.json').read_text())['packs'][0]
        self.assertEqual(len(manifest), 78)
        self.assertEqual(len({s['held'] for s in manifest}), 78)
        expected = {s[key] for s in manifest for key in ('held', 'hilt', 'blocking')}
        self.assertEqual(expected, set(dependencies['models']))
        for saber in manifest:
            wrapper = json.loads((ROOT / 'resource-pack/src/assets/mandomc/items/sabers'
                                  / (saber['id'] + '.json')).read_text())['model']
            self.assertEqual(wrapper['cases'][0]['model']['model'], saber['hilt'])
            self.assertEqual(wrapper['fallback']['on_true']['model'], saber['blocking'])
            self.assertEqual(wrapper['fallback']['on_false']['model'], saber['held'])
        self.assertFalse((ROOT / 'resource-pack/src/assets/workshop').exists())

    def test_rarity_badges_exist_and_vader_remains_stable(self):
        manifest = json.loads((ROOT / 'resource-pack/sabers.json').read_text())['sabers']
        vader = next(s for s in manifest if s['id'] == 'vader')
        self.assertEqual(vader['rarity'], 'legendary')
        self.assertEqual(vader['bladeCenter'], [.00625, 1.34375, -.09375])
        font = json.loads((ROOT / 'resource-pack/src/assets/minecraft/font/default.json').read_text())
        labels = {p['file'].rsplit('/', 1)[-1][:-4] for p in font['providers'] if 'file' in p}
        self.assertTrue({s['rarity'] for s in manifest} <= labels)
