import importlib.util,json,unittest
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
class SkullAssets(unittest.TestCase):
 def test_trophy_fits_one_block_with_opaque_complete_six_face_wrap(self):
  model=json.loads((ROOT/'resource-pack/src/assets/mandomc/models/trophies/rancor_skull.json').read_text());texture=Image.open(ROOT/'resource-pack/src/assets/mandomc/textures/item/trophies/rancor_skull.png')
  self.assertEqual(len(model['elements']),1)
  for cube in model['elements']:
   self.assertEqual(set(cube['faces']),{'north','south','east','west','up','down'})
   for a,b in zip(cube['from'],cube['to']):self.assertGreaterEqual(a,0);self.assertLessEqual(b,16);self.assertGreater(b,a)
   for face in cube['faces'].values():
    a,b,c,d=face['uv'];self.assertLess(a,c);self.assertLess(b,d)
    tile=texture.crop((int(a*texture.width/16),int(b*texture.height/16),int(c*texture.width/16),int(d*texture.height/16)))
    self.assertEqual(tile.getchannel('A').getextrema(),(255,255))
 def test_face_orientation_palette_and_back_boundaries_match_original_rig(self):
  spec=importlib.util.spec_from_file_location('skull_builder',ROOT/'scripts/build-rancor-skull-model.py');builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder);tiles,trace=builder.head_tiles();source=Image.open(builder.ATLAS).convert('RGBA');blueprint=json.loads(builder.SOURCE.read_text());head=next(e for e in blueprint['elements']if e['uuid']=='abcd4ea5-db4d-c179-5800-7b471f232045')
  original_colors=set(source.getdata())
  for face,tile in tiles.items():
   self.assertTrue(set(tile.getdata()).issubset(original_colors))
   if face=='south':continue
   u,v,U,V=head['faces'][builder.FACE_SOURCE[face]]['uv'];expected=source.crop((min(u,U),min(v,V),max(u,U),max(v,V)))
   if U<u:expected=expected.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if V<v:expected=expected.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   if face=='up':expected=expected.transpose(Image.Transpose.ROTATE_270)
   if face=='down':expected=expected.transpose(Image.Transpose.ROTATE_90)
   self.assertEqual(tile.tobytes(),expected.tobytes(),face)
  for y in range(1,23):
   self.assertEqual(tiles['south'].getpixel((0,y)),tiles['west'].getpixel((23,y)))
   self.assertEqual(tiles['south'].getpixel((23,y)),tiles['east'].getpixel((0,y)))
  for x in range(24):
   self.assertEqual(tiles['south'].getpixel((x,0)),tiles['up'].getpixel((x,23)))
   self.assertEqual(tiles['south'].getpixel((x,23)),tiles['down'].getpixel((x,0)))
 def test_native_editable_model_matches_export_geometry_and_pixels(self):
  import base64,io
  bb=json.loads((ROOT/'resource-pack/artwork/rancor_skull/rancor_skull.bbmodel').read_text());model=json.loads((ROOT/'resource-pack/src/assets/mandomc/models/trophies/rancor_skull.json').read_text())
  self.assertEqual([(e['from'],e['to'])for e in bb['elements']],[(e['from'],e['to'])for e in model['elements']])
  embedded=Image.open(io.BytesIO(base64.b64decode(bb['textures'][0]['source'].split(',')[1]))).convert('RGBA');actual=Image.open(ROOT/'resource-pack/src/assets/mandomc/textures/item/trophies/rancor_skull.png').convert('RGBA');self.assertEqual(embedded.tobytes(),actual.tobytes())
 def test_source_boss_head_and_texture_are_recorded_without_modification(self):
  import hashlib
  origin=json.loads((ROOT/'resource-pack/artwork/rancor_skull/provenance.json').read_text())
  for source,key in [('source','sourceSha256'),('sourceAtlas','atlasSha256')]:self.assertEqual(hashlib.sha256((ROOT/origin[source]).read_bytes()).hexdigest(),origin[key])
