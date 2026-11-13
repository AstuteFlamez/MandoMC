import unittest
from stair_rules import *
class StairRulesTest(unittest.TestCase):
 def test_low_face_direction_and_valid_upper_landing(self):
  for facing,(dx,dz) in DIRECTIONS.items():
   s=f'mud_brick_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]';b={(dx,70,dz):'mud_bricks'};get=lambda x,y,z:b.get((x,y,z),'air')
   self.assertFalse(low_face_blocked(get,(0,70,0),s));b[-dx,70,-dz]='mud_bricks';self.assertTrue(low_face_blocked(get,(0,70,0),s))
 def test_repair_rotates_toward_real_open_approach(self):
  b={(0,70,0):'mud_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]',(-1,70,0):'mud_bricks',(1,70,0):'mud_bricks',(0,69,-1):'mud_bricks'};get=lambda x,y,z:b.get((x,y,z),'air')
  repair_low_faces(get,lambda x,y,z,s:b.__setitem__((x,y,z),s),[(0,70,0)])
  self.assertEqual('south',properties(b[0,70,0])['facing']);self.assertEqual('mud_bricks',b[1,70,0]);self.assertFalse(low_face_blocked(get,(0,70,0),b[0,70,0]))
 def test_collision_height_uses_half_block_steps(self):
  s='mud_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'
  self.assertEqual(141,quadrant_height(s,70,0,0));self.assertEqual(142,quadrant_height(s,70,1,0))
if __name__=='__main__':unittest.main()
