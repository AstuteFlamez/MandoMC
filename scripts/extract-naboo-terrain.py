#!/usr/bin/env python3
"""Survey the existing Naboo play area and configured protected columns from a private world copy."""
from pathlib import Path
import sys,json,collections,numpy as np
root=Path(__file__).resolve().parents[1];sys.path.insert(0,str(root/'scripts/map_tools'));from native_world import *
import argparse,yaml
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--world',type=Path,required=True);parser.add_argument('--work-dir',type=Path,required=True);args=parser.parse_args()
L=args.work_dir;L.mkdir(parents=True,exist_ok=True);w=NativeWorld(args.world);origin=(-13808,-64,3184);shape=(224,512,512);v=np.lib.format.open_memmap(L/'blocks.npy',mode='w+',dtype=np.uint16,shape=shape);names=['air'];ids={'air':0};bes=[];statuses=collections.Counter()
for cz in range(199,231):
 for cx in range(-863,-831):
  f=w.chunk(cx,cz);statuses[str(f.get('Status',''))]+=1
  for e in f.get('block_entities',[]):bes.append({'id':str(e.get('id')),'pos':[int(e[k]) for k in ['x','y','z']]})
  for sec in f['sections']:
   sy=int(sec['Y'])*16
   if not -64<=sy<160 or 'block_states'not in sec:continue
   states=sec['block_states'];pal=[block_name(p)for p in states['palette']]
   for n in pal:
    if n not in ids:ids[n]=len(names);names.append(n)
   a=np.array([ids[n]for n in pal],np.uint16)[unpack(states)].reshape(16,16,16);v[sy+64:sy+80,(cz-199)*16:(cz-198)*16,(cx+863)*16:(cx+864)*16]=a
v.flush();base=np.array([n.split('[')[0]for n in names]);solid=~np.isin(v,[i for i,n in enumerate(base)if n in ['air','cave_air','void_air','water','lava','short_grass','fern','tall_grass','large_fern','snow']]);height=159-np.argmax(solid[::-1],axis=0);height[~np.any(solid,axis=0)]=-65;np.save(L/'surface-height.npy',height)
counts={}
for y in [-60,-32,-1,0,1,8,16,32,48,64,96,128]:
 c=collections.Counter(base[v[y+64]].ravel());counts[y]=c.most_common(6)
scene={'origin':origin,'shape':shape,'names':names,'statuses':dict(statuses),'block-entities':bes,'surface-range':[int(height.min()),int(height.max())],'level-counts':counts};(L/'scene.json').write_text(json.dumps(scene,indent=2));print(json.dumps({k:v for k,v in scene.items()if k not in ['names','block-entities']},indent=2))

config=yaml.safe_load((root/'server/config/common/mandomc/territory/maps.yml').read_text())['additional-maps']['naboo'];safe=[];war=[]
for zi,row in enumerate(config['rows']):
 for xi,char in enumerate(row):
  if char=='S':safe.append([config['min-chunk-x']+xi,config['min-chunk-z']+zi])
  if char=='W':war.append([config['min-chunk-x']+xi,config['min-chunk-z']+zi])
(L/'zones.json').write_text(json.dumps({'safe-chunks':safe,'build-war-chunks':war,'protected-chunks':safe+war},indent=2))
