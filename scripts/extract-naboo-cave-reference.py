#!/usr/bin/env python3
"""Export the bounded native Minecraft cave reference, not its blocks or entities, for Naboo authoring."""
from pathlib import Path
import sys,json,numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parent/'map_tools'));from native_world import *
import argparse
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',type=Path,required=True);p.add_argument('--work-dir',type=Path,required=True);a=p.parse_args();L=a.work_dir;L.mkdir(parents=True,exist_ok=True);w=NativeWorld(a.world);v=np.zeros((208,512,512),np.uint16);names=['air'];ids={'air':0}
for cz in range(32):
 for cx in range(32):
  f=w.chunk(cx,cz)
  for sec in f['sections']:
   sy=int(sec['Y'])*16
   if not -64<=sy<144 or 'block_states'not in sec:continue
   bs=sec['block_states'];pal=[block_name(p)for p in bs['palette']]
   for n in pal:
    if n not in ids:ids[n]=len(names);names.append(n)
   v[sy+64:sy+80,cz*16:cz*16+16,cx*16:cx*16+16]=np.array([ids[n]for n in pal],np.uint16)[unpack(bs)].reshape(16,16,16)
np.save(L/'reference.npy',v);(L/'reference.json').write_text(json.dumps({'names':names,'seed':2026111303,'y-offset':64},indent=2))
for y in range(8,144,8):
 c=np.isin(v[y],[i for i,n in enumerate(names)if n.split('[')[0]in ['air','cave_air','water','lava']]);print('targetY',y,'cavefraction',c.mean())
