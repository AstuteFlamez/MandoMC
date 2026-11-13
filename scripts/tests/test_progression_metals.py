import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'resource-pack/src/assets'


class ProgressionMetalTests(unittest.TestCase):
    def test_all_generated_assets_match_recorded_sources_and_hashes(self):
        manifest = json.loads((ROOT / 'resource-pack/progression-metal-sources.json').read_text())
        for record in manifest['textures']:
            data = (ASSETS / record['target']).read_bytes()
            self.assertEqual(hashlib.sha256(data).hexdigest(), record['outputSha256'], record['target'])
            if 'localSource' in record:
                self.assertEqual(data, (ASSETS / record['localSource']).read_bytes())

    def test_redstone_aliases_replace_copper_plan_without_altering_existing_metal_art(self):
        manifest = json.loads((ROOT / 'resource-pack/progression-metal-sources.json').read_text())
        self.assertFalse(any('copper' in row['target'] for row in manifest['textures']))
        for row in json.loads((ROOT / 'resource-pack/metal-block-sources.json').read_text())['blocks']:
            path = ASSETS / ('mandomc/textures/block/metals/' + row['name'] + '.png')
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), row['outputSha256'])
        labels = json.loads((ASSETS / 'minecraft/lang/en_us.json').read_text())
        self.assertEqual('Electrum Ore', labels['block.minecraft.redstone_ore'])
        self.assertEqual('Deepslate Electrum Ore', labels['block.minecraft.deepslate_redstone_ore'])
        self.assertEqual('Electrum Block', labels['block.minecraft.redstone_block'])
        self.assertNotIn('block.minecraft.copper_block', labels)

    def test_metals_use_four_distinct_native_mineral_patterns(self):
        base = json.loads((ROOT / 'resource-pack/metal-block-sources.json').read_text())['blocks']
        progression = json.loads((ROOT / 'resource-pack/progression-metal-sources.json').read_text())['textures']
        sources = {row['palette']: row['source'] for row in base if row['name'].startswith('stone_')}
        sources.update({row['palette']: row['source'] for row in progression if row['target'] in (
            'minecraft/textures/block/iron_ore.png', 'minecraft/textures/block/redstone_ore.png')})
        self.assertEqual(len(set(sources.values())), 4)
        self.assertIn('iron_ore', sources['agrinium'])
        self.assertIn('gold_ore', sources['electrum'])
        self.assertIn('diamond_ore', sources['durasteel'])
        self.assertIn('emerald_ore', sources['beskar'])
        block = next(row for row in progression if row['target'].endswith('/redstone_block.png'))
        self.assertEqual(block['artSource'], 'art-sources/electrum-block-32.png')

    def test_deepslate_and_electrum_stone_shadows_use_visible_gray_floor(self):
        base = json.loads((ROOT / 'resource-pack/metal-block-sources.json').read_text())['blocks']
        for row in base:
            if row['name'].startswith('deepslate_'):
                self.assertEqual(row['shadowFloorLuma'], 80)
        progression = json.loads((ROOT / 'resource-pack/progression-metal-sources.json').read_text())['textures']
        wanted = {'minecraft/textures/block/deepslate_iron_ore.png',
                  'minecraft/textures/block/deepslate_redstone_ore.png',
                  'minecraft/textures/block/redstone_ore.png'}
        for row in progression:
            if row['target'] in wanted:
                self.assertEqual(row['shadowFloorLuma'], 96 if row['target'].endswith('/redstone_ore.png') else 80)

    def test_no_item_lore_or_recipe_source_points_to_jabba(self):
        recipes = json.loads((ROOT / 'plugin/src/main/resources/items/recipes.yml').read_text())
        self.assertNotIn('jabba', json.dumps(recipes['sources']).lower())
        for base in ['plugin/src/main/resources/items/definitions', 'server/config/common/mandomc/items/definitions']:
            for path in (ROOT / base).glob('*.yml'):
                self.assertNotIn('jabba', path.read_text().lower(), str(path))

    def test_legacy_green_bolt_keeps_red_bolt_geometry_and_original_animation(self):
        green = json.loads((ASSETS / 'mandomc/models/item/projectiles/greenlaser.json').read_text())
        red = json.loads((ASSETS / 'mandomc/models/item/projectiles/redlaser.json').read_text())
        self.assertEqual(green['elements'], red['elements'])
        self.assertEqual(green['display']['fixed'], red['display']['fixed'])
        # Native cannon displays use NONE; legacy head transforms are intentionally retained per model.
        self.assertEqual(green['textures']['3'], 'mandomc:item/projectiles/energy_green')
        provenance = json.loads((ROOT / 'resource-pack/sources.json').read_text())['recoveredAssets']
        for path in ['assets/mandomc/textures/item/projectiles/energy_green.png',
                     'assets/mandomc/textures/item/projectiles/energy_green.png.mcmeta']:
            row = next(row for row in provenance if row['destination'] == path)
            self.assertEqual(hashlib.sha256((ROOT / 'resource-pack/src' / path).read_bytes()).hexdigest(), row['sourceSha256'])
        animation = json.loads((ASSETS / 'mandomc/textures/item/projectiles/energy_green.png.mcmeta').read_text())
        self.assertEqual(animation['animation'], {'frametime': 2, 'interpolate': True})
