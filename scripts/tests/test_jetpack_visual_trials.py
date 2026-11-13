import importlib.util,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'resource-pack/src/assets/mandomc'
spec=importlib.util.spec_from_file_location('trial_models',ROOT/'scripts/build-jetpack-test-models.py')
generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(generator)
class VisualModels(unittest.TestCase):
 def test_sources_reproduce_exactly_and_stay_within_client_limits(self):
  for name,model in generator.generate():
   self.assertEqual(json.loads((ASSETS/name).read_text()),model)
   for e in model.get('elements',[]):
    self.assertTrue(all(-16<=v<=32 for key in ['from','to'] for v in e[key]))
   for display in model.get('display',{}).values():
    self.assertTrue(all(-80<=v<=80 for v in display['translation']))
    self.assertTrue(all(0<v<=4 for v in display['scale']))
 def test_elevated_and_direct_carriers_keep_same_size_and_mounting_plate(self):
  original=json.loads((ASSETS/'models/equipment/boba_jetpack_attached.json').read_text())
  for mode,origin in [('carrier',1.8),('wearer',4.3)]:
   model=json.loads((ASSETS/f'models/equipment/jetpack_test_{mode}_0.json').read_text())
   display=model['display']['head'];scale=.625*display['scale'][0]
   elements=model.get('elements',original['elements'])
   height=(max(e['to'][1] for e in elements)-min(e['from'][1] for e in elements))/16*scale
   self.assertAlmostEqual(height,22/16)
   # First cuboid's mounting plate center: native stand renderer + custom-head item transform.
   y=(elements[0]['from'][1]+elements[0]['to'][1])/2
   center=origin+1.501-1/16+.25+.625*display['translation'][1]/16+(y-8)/16*scale
   self.assertAlmostEqual(center,1.055625+1/16)
