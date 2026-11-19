import importlib.util
from pathlib import Path
import tempfile
import unittest

try:
    import numpy as np
    import scipy, nbtlib, yaml
except ImportError:
    np=None

@unittest.skipIf(np is None,'Optional map authoring dependencies unavailable')
class PlanetCoalTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        scripts = Path(__file__).resolve().parents[1]
        for name, script in [('module', 'add-planet-coal.py'), ('ore_balance', 'rebalance-planet-ores.py')]:
            spec = importlib.util.spec_from_file_location(name, scripts / script)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            setattr(cls, name, module)
    def test_only_deep_cave_host_rock_is_selected_and_existing_ores_are_preserved(self):
        v=np.ones((64,16,16),np.int32)
        v[12:20,4:12,4:12]=0
        v[9,5,5]=2
        v[57:63,4:12,4:12]=0
        names=['air','stone','diamond_ore']
        targets=self.module.candidates(v,names,17)
        self.assertGreater(len(targets),0)
        self.assertTrue(np.array_equal(targets,self.module.candidates(v,names,17)))
        for y,z,x in targets:
            self.assertEqual(v[y,z,x],1)
            self.assertLess(y,51)
            self.assertTrue(0<x<15 and 0<z<15)
    def test_structures_exclude_their_columns_and_six_block_margin(self):
        v=np.ones((64,16,16),np.int32);v[12:20,4:12,4:12]=0
        v[45,8,8]=2
        targets=self.module.candidates(v,['air','stone','stone_bricks'],17)
        for y,z,x in targets:self.assertGreater(abs(x-8)+abs(z-8),6)
    def test_completed_migration_is_idempotent_without_rewriting_terrain(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);config=root/'plugins/MandoMC';(config/'territory').mkdir(parents=True);(config/'data').mkdir()
            (root/'server.properties').write_text('level-name=world\n')
            (config/'territory/maps.yml').write_text('world: minecraft:hoth\n')
            (config/'data/clans.yml').write_text('claims: []\n')
            overlay=config/'data/coal-terrain.yml';raw='schema-version: 1\nmigration-id: planet-coal-v1\nchanges: []\n';overlay.write_text(raw)
            report=self.module.migrate(root,root/'out',True)
            self.assertEqual(report['files'],[]);self.assertEqual(overlay.read_text(),raw)

    def test_ore_rebalance_has_equal_fractions_and_preserves_deepslate(self):
        slots = {'coal_ore': [((x, -10, 0), 'minecraft:deepslate', 'minecraft:deepslate_coal_ore')
                              for x in range(200)]}
        changes, quotas = self.ore_balance.set_ratios('minecraft:mandalore_review', slots)
        self.assertEqual({'iron_ore': 150, 'redstone_ore': 30, 'diamond_ore': 20}, quotas)
        self.assertEqual(len(slots['coal_ore']), len(changes))
        self.assertEqual(quotas, {ore: sum(self.ore_balance.family(target) == ore
                                         for _, _, target in changes.values()) for ore in quotas})
        self.assertTrue(all(target.startswith('minecraft:deepslate_') for _, _, target in changes.values()))
        self.assertTrue(all(target.endswith('[lit=false]') for _, _, target in changes.values()
                            if self.ore_balance.family(target) == 'redstone_ore'))
        self.assertEqual((changes, quotas), self.ore_balance.set_ratios('minecraft:mandalore_review', slots))
