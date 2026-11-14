#!/usr/bin/env python3
"""Paint Naboo directly on a native 32x32 grid, matching the active planet globes."""
from pathlib import Path
import math
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'resource-pack/src/assets/mandomc/textures/item/planets/naboo.png'
# Final-resolution continents: ocean (.), meadow (g), forest (f), and warm shore (s).
TERRAIN = [
 '........................',
 '..........ggggg.........',
 '.......gggggggggg.......',
 '......ggggffggggggg.....',
 '.....gggfffffggggggg....',
 '....ggffffffgggssgggg...',
 '...ggffffffggs...sggg...',
 '...ggffffgggs.....sgg...',
 '..gggfffgggs.......sg...',
 '..ggggggggs.............',
 '...sgggggs..............',
 '....ssggs......gggg.....',
 '......ss......ggffgg....',
 '.............ggffffgg...',
 '...gg.......ggfffffgg...',
 '..ggggg....gggffffgggg..',
 '..ggffgg..sgggfffggggg..',
 '...gfffggssgggggggggg...',
 '....gfffggggggssgggg....',
 '.....ggffggggs..sgg.....',
 '......ggggggs....s......',
 '.......sgggs............',
 '.........ss.............',
 '........................',
]
PALETTES = {
 '.': ['102c45','153b5b','1b4f76','22648e','2c7eab','419cc4','69bad7','a0d8e7'],
 'g': ['243b25','34512b','466d33','5b873e','77a34b','96ba60','b6d27e','d4e39f'],
 'f': ['1c3426','28482e','345e34','42753a','588d47','73a359','92bd73','b4d394'],
 's': ['4b4931','655f3c','817348','9b8b55','b8a46b','d1bd85','e3d2a5','f0e2bf'],
}

def main():
 assert len(TERRAIN)==24 and all(len(row)==24 for row in TERRAIN)
 out=Image.new('RGBA',(32,32))
 for y in range(4,28):
  for x in range(4,28):
   nx,ny=(x-15.5)/11.8,(y-15.5)/11.8;r2=nx*nx+ny*ny
   if r2>1:continue
   nz=math.sqrt(1-r2)
   light=max(0,-.48*nx-.58*ny+.66*nz)
   # Small coherent pixel clusters add the surface detail seen on Hoth/Tatooine.
   cluster=((x//2*13+y//2*7)%9-4)*.025
   detail=(((x*11+y*17)%7)-3)*.018
   level=max(0,min(7,int((.10+.86*light+cluster+detail)*7)))
   kind=TERRAIN[y-4][x-4]
   if kind=='g' and (x//2+y//2*3)%7==0:kind='f'
   out.putpixel((x,y),(*bytes.fromhex(PALETTES[kind][level]),255))
 # Wispy cloud clusters sit on the surface, never outside the circular silhouette.
 for x,y,c in [(8,10,'d6e8d8'),(9,10,'edf5e5'),(10,9,'edf5e5'),(11,9,'d6e8d8'),
               (19,12,'dcebe2'),(20,12,'b8d4cf'),(21,11,'dcebe2')]:
  out.putpixel((x,y),(*bytes.fromhex(c),255))
 # Compact Theed-style ivory waterfront and green domes, like other planets' landmarks.
 pixels={
  18: {17:'88b89b'},
  19: {16:'afcfaa',17:'75a68b',18:'4a7f70',22:'7fab92'},
  20: {15:'e1d6ae',16:'f0e6c7',17:'cfc39b',18:'a59e7c',21:'abc9a4',22:'679581',23:'467568'},
  21: {15:'bbae87',16:'f0e3bd',17:'4e655e',18:'c7bb92',20:'ded3af',21:'eee2bd',22:'c3b58e',23:'9a9476'},
  22: {15:'829073',16:'d0c49a',17:'e3d5ac',18:'b3a780',19:'777f65',20:'b9ad86',21:'445c58',22:'d0c098',23:'8d8e72'},
  23: {16:'728369',17:'9a9e77',18:'79876a',20:'7c896c',21:'aba47e',22:'8e9271'},
 }
 for y,row in pixels.items():
  for x,c in row.items():
   if out.getpixel((x,y))[3]:out.putpixel((x,y),(*bytes.fromhex(c),255))
 TARGET.parent.mkdir(parents=True,exist_ok=True);out.save(TARGET)
 print('Painted native 32x32 Naboo: detailed sphere, forests, lakes and Theed domes; no resizing')

if __name__=='__main__':main()
