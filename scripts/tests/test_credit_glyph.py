import json,unittest
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
class CreditGlyph(unittest.TestCase):
 def test_only_dollar_is_claimed_by_white_tintable_provider(self):
  providers=json.loads((ROOT/'resource-pack/src/assets/minecraft/font/default.json').read_text())['providers'];credit=[p for p in providers if p.get('file')=='mandomc:font/credit.png'];self.assertEqual(len(credit),1)
  self.assertEqual(credit[0]['chars'],['$']);self.assertEqual(credit[0]['height'],8);self.assertEqual(credit[0]['ascent'],8);self.assertEqual(providers[0],credit[0])
  im=Image.open(ROOT/'resource-pack/src/assets/mandomc/textures/font/credit.png').convert('RGBA');self.assertEqual(im.size,(5,8));self.assertEqual(im.getchannel('A').getbbox()[2]+1,6)
  self.assertEqual({p for p in im.get_flattened_data()if p[3]},{(255,255,255,255)})
 def test_authentic_resh_structure_has_two_strokes_and_diagonal(self):
  im=Image.open(ROOT/'resource-pack/src/assets/mandomc/textures/font/credit.png').convert('RGBA')
  self.assertEqual([im.getpixel((x,0))[3]for x in range(5)],[255,0,255,0,0]);self.assertTrue(all(im.getpixel((x,1))[3]==255 for x in range(5)))
  self.assertTrue(all(im.getpixel((7-y,y))[3]==255 for y in range(3,8)))
