import json,unittest,hashlib
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
class TrophyLabel(unittest.TestCase):
 def test_every_letter_reuses_exact_existing_pixels_and_frame(self):
  art=json.loads((ROOT/'resource-pack/artwork/trophy_label/provenance.json').read_text());base=ROOT/'resource-pack/src/assets/mandomc/textures/font/item_labels';out=Image.open(base/'trophy.png').convert('RGBA');frame=Image.open(base/'weapon.png').convert('RGBA');ink={(255,255,255,255),(163,208,227,255)}
  self.assertEqual(art['word'],'TROPHY');self.assertFalse(art['newLetterPixels']);self.assertEqual(out.size,(80,18));self.assertEqual(out.crop((0,0,9,18)).tobytes(),frame.crop((0,0,9,18)).tobytes());self.assertEqual(out.crop((71,0,80,18)).tobytes(),frame.crop((69,0,78,18)).tobytes())
  for row in art['letters']:
   source=Image.open(base/row['source']).convert('RGBA');self.assertEqual(hashlib.sha256((base/row['source']).read_bytes()).hexdigest(),row['sourceSha256']);a,b=row['sourceColumns'];start,end=row['targetColumns']
   for y in range(18):
    for x in range(a,b):
     expected=source.getpixel((x,y));actual=out.getpixel((start+x-a,y));self.assertEqual(actual in ink,expected in ink)
     if expected in ink:self.assertEqual(actual,expected)
 def test_isolated_font_uses_established_metrics_and_catalog_category(self):
  font=json.loads((ROOT/'resource-pack/src/assets/mandomc/font/trophies.json').read_text());self.assertEqual(font['providers'],[{'type':'bitmap','file':'mandomc:font/item_labels/trophy.png','ascent':8,'height':9,'chars':['\ue000']}]);catalog=json.loads((ROOT/'plugin/src/main/resources/items/catalog.yml').read_text());self.assertEqual(catalog['categories']['trophies'],'<font:mandomc:trophies>\ue000</font>')
