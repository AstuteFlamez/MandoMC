"""Read existing namespaced Paper dimension terrain; never generate missing chunks."""
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from region_nbt import *
class NativeWorld:
 def __init__(self,world):self.world=Path(world);self.regions={};self.chunks={};self.sections={}
 def chunk(self,cx,cz):
  if (cx,cz) not in self.chunks:
   r=(cx//32,cz//32)
   if r not in self.regions:self.regions[r]=records(self.world/'region'/f'r.{r[0]}.{r[1]}.mca')
   self.chunks[cx,cz]=parse(self.regions[r][cx%32+cz%32*32])
  return self.chunks[cx,cz]
 def get(self,x,y,z):
  key=x//16,z//16,y//16
  if key not in self.sections:
   sec=next((s for s in self.chunk(key[0],key[1])['sections'] if int(s['Y'])==key[2]),None)
   if sec is None or 'block_states' not in sec:self.sections[key]=(['air'],np.zeros(4096,dtype=np.int32))
   else:self.sections[key]=([block_name(p) for p in sec['block_states']['palette']],unpack(sec['block_states']))
  pal,arr=self.sections[key];return pal[int(arr[(y%16)*256+(z%16)*16+x%16])]
