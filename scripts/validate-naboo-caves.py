#!/usr/bin/env python3
"""Independently validate cave walking connectivity and protected terrain before application."""
from pathlib import Path
import json,collections,numpy as np
from scipy import ndimage
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work-dir',type=Path,required=True);args=parser.parse_args();L=args.work_dir;m=json.loads((L/'generation.json').read_text());v=np.load(L/'updated.npy',mmap_mode='r');original=np.load(L/'blocks.npy',mmap_mode='r')[64:208];names=m['names'];air=np.load(L/'air.npy');passids=[i for i,n in enumerate(names)if n in ['air','cave_air','void_air']or n.startswith('glow_lichen[')];p=np.isin(v,passids);floor=(~p[:-2])&p[1:-1]&p[2:]&air[1:-1];raw=np.argwhere(floor);raw[:,0]+=1;floors={(int(x),int(y),int(z))for y,z,x in raw};print('Walkable cave floor tiles',len(floors),flush=True)
def anchor(room):
 x,y,z=room['center'];near=[n for n in floors if abs(n[0]-x)<=5 and abs(n[2]-z)<=5]
 if not near:raise RuntimeError(('No room floor',room))
 return min(near,key=lambda n:(n[0]-x)**2+(n[2]-z)**2+3*(n[1]-y)**2)
anchors={r['name']:anchor(r)for r in m['rooms']};start=anchors['c0'];q=collections.deque([start]);seen={start}
while q:
 x,y,z=q.popleft()
 for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
  for dy in [-1,0,1]:
   n=(x+dx,y+dy,z+dz)
   if n in seen or n not in floors:continue
   if dy>0 and not p[y+2,z,x]:continue
   if dy<0 and not p[n[1]+2,n[2],n[0]]:continue
   seen.add(n);q.append(n)
missing={k:v for k,v in anchors.items()if v not in seen};print('Reached',len(seen),'/',len(floors),'floors; missing rooms:',missing,flush=True)
# Air connectivity is independently checked apart from walkability.
labels,n=ndimage.label(air);counts=np.bincount(labels.ravel());print('Air components',n,'largest',sorted(counts[1:],reverse=True)[:6]);assert not missing,('Disconnected room floors',missing)
# Every surface column retains at least its highest 12 original terrain layers.
ground=np.load(L/'ground.npy');diff=v!=original;Y=np.arange(144)[:,None,None];assert not np.any(diff&(Y>=ground[None,:,:]-12));assert np.array_equal(v[0],original[0])
ox,oz=m['origin'][0],m['origin'][2]
for cx,cz in m['safe-chunks']+m.get('war-chunks',[]):
 x1=max(0,cx*16-ox);x2=min(512,cx*16+16-ox);z1=max(0,cz*16-oz);z2=min(512,cz*16+16-oz)
 if x1<x2 and z1<z2:assert np.array_equal(v[:,z1:z2,x1:x2],original[:,z1:z2,x1:x2])
record={'passed':True,'chambers':len(anchors),'all-chambers-walk-connected':True,'walkable-floors':len(floors),'reachable-floors':len(seen),'air-components':int(n),'surface-top-12-preserved':True,'island-columns-preserved':True,'bedrock-preserved':True,'anchors':anchors};(L/'geometry-validation.json').write_text(json.dumps(record,indent=2));print('Geometry validated',flush=True)
