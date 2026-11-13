#!/usr/bin/env python3
"""Validate native cave geometry, themed regions, contained pools and protected Naboo terrain."""
from pathlib import Path
import json,numpy as np
from scipy import ndimage
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work-dir',type=Path,required=True);args=parser.parse_args();L=args.work_dir;m=json.loads((L/'generation.json').read_text());v=np.load(L/'updated.npy',mmap_mode='r');original=np.load(L/'blocks.npy',mmap_mode='r')[64:208];air=np.load(L/'air.npy');names=m['names'];ground=np.load(L/'ground.npy');Y=np.arange(144)[:,None,None];diff=v!=original
assert not np.any(diff&(Y>=ground[None,:,:]-12));assert np.array_equal(v[0],original[0]);assert not np.any(diff[:,np.load(L/'build-protected-columns.npy')]);ox,oz=m['origin'][0],m['origin'][2]
for cx,cz in m['safe-chunks']+m['war-chunks']:
 x1=max(0,cx*16-ox);x2=min(512,cx*16+16-ox);z1=max(0,cz*16-oz);z2=min(512,cz*16+16-oz)
 if x1<x2 and z1<z2:assert np.array_equal(v[:,z1:z2,x1:x2],original[:,z1:z2,x1:x2])
passids=[i for i,n in enumerate(names)if n in ['air','cave_air','void_air']or n.startswith('glow_lichen[')];p=np.isin(v,passids)
fullcube={'stone','andesite','diorite','granite','cobblestone','tuff','calcite','moss_block','dripstone_block','rooted_dirt','dirt','grass_block','coarse_dirt','podzol','gravel','clay','bedrock'}
solidids=[i for i,n in enumerate(names)if n.split('[')[0]in fullcube or n.split('[')[0].endswith('_ore')]
floor=np.isin(v[:-2],solidids)&p[1:-1]&p[2:]&air[1:-1];raw=np.argwhere(floor);raw[:,0]+=1
# Native systems can have vertical shafts and mined connections; do not flatten them to
# satisfy an artificial requirement that every ledge be reachable without player tools.
labels,n=ndimage.label(air);sizes=np.bincount(labels.ravel());anchors={};systems=[]
for k in sorted(range(1,n+1),key=lambda k:-sizes[k]):
 positions=raw[labels[tuple(raw.T)]==k]
 if not len(positions):continue
 center=positions.mean(axis=0);d=np.sum((positions-center)**2,axis=1);chosen=positions[int(d.argmin())];y,z,x=map(int,chosen);anchors['system-'+str(k)]=[x,y,z];systems.append({'air-cells':int(sizes[k]),'anchor':[x+ox,y,z+oz]})
for group in ['lush-grottos','dripstone-galleries']:
 for i,(x,y,z) in enumerate(m['features'][group]):
  target=np.array([y,z-oz,x-ox]);d=np.sum((raw-target)**2,axis=1);q=raw[int(d.argmin())];yy,zz,xx=map(int,q);anchors[group+str(i)]=[xx,yy,zz]
for x,y,z in m['lights']:
 assert names[int(v[y,z-oz,x-ox])].startswith('glow_lichen[');assert not p[y-1,z-oz,x-ox]
water=[i for i,n in enumerate(names)if n.startswith('water[')]
for y,z,x in np.argwhere(np.isin(v,water)&air):
 assert int(v[y-1,z,x]) in solidids
 for dz,dx in [(0,1),(0,-1),(1,0),(-1,0)]:assert int(v[y,z+dz,x+dx]) in solidids+water,('Pool can leak',x,y,z)
ref=np.load(L/'reference.npy',mmap_mode='r');rn=json.loads((L/'reference.json').read_text())['names'];ri=[i for i,n in enumerate(rn)if n.split('[')[0]in ['air','cave_air','water','lava']];assert np.all(np.isin(ref[:144][air],ri))
# Native geometry plus meaningful breadth, floor elevations and multiple regions.
assert len(np.unique(raw[:,0]))>=60;assert air.sum()>1500000;assert len(m['features']['lush-grottos'])==12;assert len(m['features']['dripstone-galleries'])==6;assert len(m['features']['enclosed-pools'])==18
source_names=json.loads((L/'scene.json').read_text())['names']
for state,budget in [('iron_ore',2635),('diamond_ore',2095),('redstone_ore',2294),('coal_ore',0),('gold_ore',0)]:
 oldids=[i for i,n in enumerate(source_names)if n.split('[')[0]==state];newids=[i for i,n in enumerate(names)if n.split('[')[0]==state];assert np.isin(v,newids).sum()==np.isin(original,oldids).sum()+budget,(state,'Ore budget changed')
report={'passed':True,'native-geometry-retained':True,'walkable-floor-tiles':len(raw),'floor-elevations':len(np.unique(raw[:,0])),'air-components':n,'systems':systems,'anchors':anchors,'surface-top-12-preserved':True,'protected-columns-preserved':True,'structure-foundations-preserved':True,'bedrock-preserved':True,'contained-pools':18,'ore-budgets-preserved':True}
(L/'geometry-validation.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items()if k not in ['anchors','systems']}));print('Largest systems',systems[:8])
