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
        self.assertEqual('Electrum Dust Block', labels['block.minecraft.redstone_block'])
        self.assertEqual('Electrum Dust', labels['item.minecraft.redstone'])
        self.assertEqual('Electrum Wire', labels['block.minecraft.redstone_wire'])
        self.assertTrue(all('redstone' not in value.lower() for value in labels.values()))
        self.assertEqual('1 Electrum Dust', labels['container.enchant.lapis.one'])
        vanilla_dust = json.loads((ASSETS / 'minecraft/items/redstone.json').read_text())
        catalog_dust = json.loads((ASSETS / 'mandomc/items/legacy/electrum_dust.json').read_text())
        self.assertEqual(vanilla_dust, catalog_dust)
        self.assertNotIn('block.minecraft.copper_block', labels)

    def test_electrum_slot_uses_the_grayscale_dust_silhouette(self):
        from PIL import Image
        source = Image.open(ASSETS / 'mandomc/textures/item/legacy/metals/scraps/electrum_dust.png').convert('RGBA')
        hint = Image.open(ASSETS / 'minecraft/textures/gui/sprites/container/slot/lapis_lazuli.png').convert('RGBA')
        self.assertEqual((32, 32), hint.size)
        self.assertEqual(source.getchannel('A').tobytes(), hint.getchannel('A').tobytes())
        self.assertTrue(all(r == g == b for r, g, b, a in hint.get_flattened_data() if a))

    def test_wire_uses_gold_models_for_every_signal_strength(self):
        wire = json.loads((ASSETS / 'minecraft/blockstates/redstone_wire.json').read_text())
        tiers = {int(power) for part in wire['multipart']
                 for power in part['when']['AND'][0]['power'].split('|')}
        self.assertEqual(set(range(16)), tiers)
        for part in wire['multipart']:
            model = ASSETS / (part['apply']['model'].replace(':', '/models/', 1) + '.json')
            self.assertTrue(model.is_file(), model)
            self.assertNotIn('tintindex', model.read_text())

    def test_electrum_visible_sprites_are_32_pixels_square(self):
        import importlib.util
        from PIL import Image
        tool = ROOT / 'scripts/build-electrum-wire-assets.py'
        spec = importlib.util.spec_from_file_location('electrum_assets', tool)
        assets = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(assets)
        paths = list((ASSETS / 'mandomc/textures/block/circuits/wire').rglob('*.png'))
        paths.extend((ASSETS / 'mandomc/textures/particle').glob('electrum_wire_dust_*.png'))
        paths.extend(ASSETS / f'minecraft/textures/{folder}/{name}.png'
                     for folder in ('block', 'item') for name in assets.CIRCUIT_SPRITES[folder])
        paths.extend((ASSETS / path) for path in (
            'mandomc/textures/item/legacy/metals/scraps/electrum_dust.png',
            'mandomc/textures/block/electrum_block.png',
        ))
        self.assertEqual(46, len(paths))
        for path in paths:
            with Image.open(path) as sprite:
                self.assertEqual((32, 32), sprite.size, path.name)

    def test_both_electrum_blocks_use_the_same_storage_face(self):
        def read(path):
            return json.loads((ASSETS / path).read_text())

        storage = 'mandomc:block/electrum_block'
        solid_block = read('minecraft/blockstates/waxed_copper_block.json')
        solid_item = read('mandomc/items/legacy/electrum_block.json')
        dust_block = read('minecraft/models/block/redstone_block.json')
        self.assertEqual(storage, solid_block['variants']['']['model'])
        self.assertEqual(storage, solid_item['model']['model'])
        self.assertEqual(storage, dust_block['parent'])
        self.assertEqual(storage, read('mandomc/models/block/electrum_block.json')['textures']['all'])
        self.assertFalse((ASSETS / 'minecraft/textures/block/redstone_block.png').exists())

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
        block = next(row for row in progression if row['target'].endswith('/electrum_block.png'))
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
