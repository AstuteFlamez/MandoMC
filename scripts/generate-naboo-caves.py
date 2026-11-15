#!/usr/bin/env python3
"""Adapt native Minecraft cave geometry to protected Naboo terrain and themed mineral/lush pockets."""
from pathlib import Path
import json,math,collections
import numpy as np
from scipy import ndimage
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work-dir',type=Path,required=True);parser.add_argument('--reference-dir',type=Path,required=True);args=parser.parse_args();L=args.work_dir;m=json.loads((L/'scene.json').read_text());full=np.load(L/'blocks.npy',mmap_mode='r');original=np.asarray(full[64:208]);v=original.copy();names=m['names'].copy();ids={n:i for i,n in enumerate(names)};base=np.array([n.split('[')[0]for n in names]);rng=np.random.default_rng(20261113)
def bid(n):
 if n not in ids:ids[n]=len(names);names.append(n)
 return ids[n]
ox,oz=-13808,3184
island=json.loads((L/'zones.json').read_text());safe={tuple(c)for c in island['protected-chunks']}
wild=np.zeros((512,512),bool);wild[6:506,6:506]=True
for cx,cz in safe:
 x1=max(0,cx*16-ox);x2=min(512,cx*16+16-ox);z1=max(0,cz*16-oz);z2=min(512,cz*16+16-oz)
 if x1<x2 and z1<z2:wild[z1:z2,x1:x2]=False
# Existing architecture and an eight-block foundation margin stay untouched at every depth.
structural=[i for i,n in enumerate(base) if any(t in n for t in ['prismarine','purpur','sandstone','quartz','bricks','concrete','glazed_terracotta','planks','_stairs','_slab','_wall','_pane','_door','chest','barrel','lantern'])]
buildfoot=ndimage.binary_dilation(np.any(np.isin(original[40:],structural),axis=0),iterations=8)
wild[buildfoot]=False
np.save(L/'build-protected-columns.npy',buildfoot)
print('Protected structure/foundation columns',int(buildfoot.sum()),flush=True)
wclear=ndimage.distance_transform_edt(wild)
natural={'stone','cobblestone','andesite','granite','diorite','dirt','grass_block','coarse_dirt','podzol','gravel','clay'}
oreids=[i for i,n in enumerate(base)if n.endswith('_ore')];naturalids=[i for i,n in enumerate(base)if n in natural]+oreids
terrain=np.isin(original,[i for i,n in enumerate(base)if n in natural or n=='bedrock']);ground=143-np.argmax(terrain[::-1],axis=0);ground[~np.any(terrain,axis=0)]=0;np.save(L/'ground.npy',ground);del terrain
solid=np.isin(original,naturalids);solid[0]=False;solid[-1]=False;solid[:,[0,-1]]=False;solid[:,:,[0,-1]]=False
print('Computing original solid clearance',flush=True);dist=ndimage.distance_transform_edt(solid).astype(np.float32);del solid
ys=np.arange(144)[:,None,None];eligible=np.isin(original,naturalids)&(dist>=6)&(ys>=7)&(ys<=ground[None,:,:]-16)&(wclear[None,:,:]>=7)
# Use the actual pinned server's cheese/spaghetti/noodle/carver terrain, never copied ores,
# structures, entities, biomes or loot. Legacy Naboo's bedrock is Y=0, so shift native Y by 64.
reference_meta=json.loads((args.reference_dir/'reference.json').read_text())
reference=np.load(args.reference_dir/'reference.npy',mmap_mode='r');refbase=[n.split('[')[0]for n in reference_meta['names']]
refempty=np.isin(reference,[i for i,n in enumerate(refbase)if n in {'air','cave_air','water','lava'}])
refsolid=~refempty;refground=207-np.argmax(refsolid[::-1],axis=0)
air=refempty[:144]&eligible&(ys<refground[None,:,:]-12)
# Retain meaningful natural side systems, discard microscopic clipped pockets.
labels,n=ndimage.label(air);counts=np.bincount(labels.ravel());keep=np.flatnonzero(counts>=180);keep=keep[keep!=0];air=np.isin(labels,keep);del labels,refempty,refsolid
v[air]=bid('air');rooms=[];paths=[]
# Coherent geology follows slow 3D strata rather than random per-block speckling.
shell=ndimage.binary_dilation(air,iterations=2)&~air&eligible
coords=np.argwhere(shell);Y,Z,X=coords.T
strata=np.sin(X/32+Z/48)+.7*np.sin(Z/37-Y/18)+.35*np.sin(X/13+Y/9)
paint=~np.isin(original[Y,Z,X],oreids)
rock=np.where(strata>1.35,bid('calcite'),np.where(strata< -1.25,bid('tuff'),np.where(strata<-.55,bid('andesite'),bid('stone'))))
v[Y[paint],Z[paint],X[paint]]=rock[paint]
# Regions are spatial landmarks, not flattened rooms. Existing ledges, pillars, branching
# necks and drops remain native. Decorative growth only occupies generously high spaces.
floor=air[1:-7]&~air[:-8]
for dy in range(1,6):floor&=air[1+dy:144-7+dy]
floors=np.argwhere(floor);floors[:,0]+=1
rng.shuffle(floors);centers=[]
for p in floors:
 if len(centers)>=18:break
 if all(np.linalg.norm(p-q)>57 for q in centers):centers.append(p)
features={'lush-grottos':[],'dripstone-galleries':[],'enclosed-pools':[]};decor_counts=collections.Counter()
for i,c in enumerate(centers):
 cy,cz,cx=map(int,c);lush=i%3!=2;radius=18 if lush else 22
 nearby=floors[np.sum((floors-c)**2,axis=1)<radius**2]
 for p in nearby:
  y,z,x=map(int,p);n=.5+.25*math.sin(x/4+z/7)+.25*math.sin(z/5-y/3)
  if n<.35 or v[y,z,x]!=bid('air') or int(v[y-1,z,x]) in oreids:continue
  if lush:
   v[y-1,z,x]=bid('moss_block');decor_counts['moss_block']+=1
   if rng.random()<.035:v[y,z,x]=bid('azalea');decor_counts['azalea']+=1
   elif rng.random()<.15:v[y,z,x]=bid('moss_carpet');decor_counts['moss_carpet']+=1
  elif rng.random()<.045:
   h=int(rng.integers(1,4));v[y-1,z,x]=bid('dripstone_block')
   for d in range(h):v[y+d,z,x]=bid('pointed_dripstone[thickness='+('tip' if d==h-1 else 'frustum' if d==h-2 else 'base')+',vertical_direction=up,waterlogged=false]');decor_counts['stalagmite-blocks']+=1
 # Sparse supported ceiling details match the same local landscape.
 y1=max(8,cy-16);y2=min(136,cy+25);z1=max(1,cz-radius);z2=min(511,cz+radius);x1=max(1,cx-radius);x2=min(511,cx+radius)
 for _ in range(170):
  y=int(rng.integers(y1,y2));z=int(rng.integers(z1,z2));x=int(rng.integers(x1,x2))
  if not air[y,z,x] or not air[y-1,z,x] or air[y+1,z,x] or not eligible[y+1,z,x] or int(v[y+1,z,x]) in oreids or v[y,z,x]!=bid('air'):continue
  if lush:v[y+1,z,x]=bid('rooted_dirt');v[y,z,x]=bid('hanging_roots[waterlogged=false]');decor_counts['hanging_roots']+=1
  else:
   h=int(rng.integers(1,4))
   if not np.all(air[y-h:y+1,z,x]):continue
   v[y+1,z,x]=bid('dripstone_block')
   for d in range(h):v[y-d,z,x]=bid('pointed_dripstone[thickness='+('tip' if d==h-1 else 'frustum' if d==h-2 else 'base')+',vertical_direction=down,waterlogged=false]');decor_counts['stalactite-blocks']+=1
 key='lush-grottos'if lush else'dripstone-galleries';features[key].append([cx+ox,cy,cz+oz])
# One-block-deep natural sumps: entire horizontal air component must have a solid floor
# and be enclosed at the waterline. This cannot spill into a corridor or surface lake.
pool_count=0
for y in range(10,100):
 if pool_count>=18:break
 layer=(v[y]==bid('air'))&air[y];lab,n=ndimage.label(layer);sizes=np.bincount(lab.ravel())
 for k in np.flatnonzero((sizes>=8)&(sizes<=110)):
  if k==0 or pool_count>=18:continue
  zz,xx=np.where(lab==k)
  if not np.all(~air[y-1,zz,xx]) or not np.all(air[y+1,zz,xx]):continue
  if any(math.hypot(float(xx.mean())-p[0]+ox,float(zz.mean())-p[2]+oz)<36 for p in features['enclosed-pools']):continue
  # Solid neighboring walls including non-air decorations are required, not just clipped masks.
  basin=lab==k;rim=ndimage.binary_dilation(basin)&~basin
  sealids=[bid(n)for n in ['stone','andesite','diorite','granite','cobblestone','tuff','calcite','moss_block','dripstone_block','rooted_dirt','dirt','grass_block','coarse_dirt','podzol','gravel','clay']]
  if not np.all(np.isin(v[y][rim],sealids)) or not np.all(np.isin(v[y-1,zz,xx],sealids)):continue
  v[y,zz,xx]=bid('water[level=0]');pool_count+=1;features['enclosed-pools'].append([int(xx.mean())+ox,y,int(zz.mean())+oz])
features['decoration-blocks']=dict(decor_counts)
exposed=ndimage.binary_dilation(air)&~air
print('Native cave systems',len(keep),'air',int(air.sum()),'features', {k:len(v) if isinstance(v,list)else v for k,v in features.items()},flush=True)
# Keep any pre-existing ore states/counts by moving excavated cells into nearby walls.
cut=np.argwhere(air&np.isin(original,oreids));exposed=ndimage.binary_dilation(air)&~air;target=np.argwhere(exposed&eligible&~np.isin(v,oreids));used=set()
for p in cut:
 cost=np.sum((target-p)**2,axis=1);order=np.argsort(cost);chosen=None
 for j in order:
  q=tuple(map(int,target[j]))
  if q not in used:chosen=q;break
 if chosen is None:raise RuntimeError('No place to preserve original ore')
 used.add(chosen);v[chosen]=original[tuple(p)]
print('Preserved existing excavated ore:',len(cut),flush=True)
# Mineral veins are embedded in exposed stone, with the existing native metal-drop mapping.
ore_report={};rng=np.random.default_rng(2026111302);rockids=[bid(n)for n in ['stone','andesite','diorite','granite','cobblestone']]
for family,state,wanted,size in [('agrinium','iron_ore',2635,8),('durasteel','diamond_ore',2095,7),('electrum','redstone_ore[lit=false]',2294,7)]:
 face=exposed&eligible&np.isin(v,rockids);seeds=np.argwhere(face);rng.shuffle(seeds);taken=[];ore=bid(state);placed=0
 for seed in seeds:
  if placed>=wanted:break
  if any(np.sum((seed-q)**2)<30 for q in taken):continue
  pos=seed.copy();count=0
  for _ in range(size+int(rng.integers(2,7))):
   y,z,x=map(int,pos)
   if placed+count<wanted and 7<=y<142 and 1<=z<511 and 1<=x<511 and eligible[y,z,x] and int(v[y,z,x])in rockids:v[y,z,x]=ore;count+=1
   pos+=rng.integers(-1,2,size=3);pos[0]=np.clip(pos[0],7,141);pos[1:]=np.clip(pos[1:],1,510)
  if count:taken.append(seed);placed+=count
 assert placed==wanted,(family,placed,wanted)
 ore_report[family]={'new-hosts':placed,'veins':len(taken),'state':state};print(family,ore_report[family],flush=True)
# Low-key supported glow lichen, avoiding floating or unsupported decorations.
lichen=bid('glow_lichen[down=true,east=false,north=false,south=false,up=false,waterlogged=false,west=false]');floors=np.argwhere(air[1:-2]&air[2:-1]&~air[:-3]);floors[:,0]+=1;rng.shuffle(floors);lights=[]
for p in floors:
 if len(lights)>=450:break
 if all(np.sum((p-q)**2)>=49 for q in lights):
  y,z,x=map(int,p)
  if v[y,z,x]==bid('air') and v[y+1,z,x]==bid('air') and v[y-1,z,x] in rockids+[bid('moss_block'),bid('calcite'),bid('dripstone_block')]:v[y,z,x]=lichen;lights.append(p)
diff=v!=original;assert not np.any(diff[:,~wild]);assert not np.any(diff&(ys>=ground[None,:,:]-12));assert np.array_equal(v[0],original[0]);assert not np.any(air&~eligible)
for state in ['iron_ore','redstone_ore','diamond_ore','coal_ore','gold_ore']:
 oldids=[i for i,n in enumerate(m['names'])if n.split('[')[0]==state];newids=[i for i,n in enumerate(names)if n.split('[')[0]==state];family={'iron_ore':'agrinium','redstone_ore':'electrum','diamond_ore':'durasteel'}.get(state);assert np.isin(v,newids).sum()==np.isin(original,oldids).sum()+(ore_report.get(family,{}).get('new-hosts',0))
changed=np.argwhere(diff);world=changed[:,[2,0,1]].astype(np.int32);world[:,0]+=ox;world[:,2]+=oz;np.save(L/'updated.npy',v);np.save(L/'changed.npy',world);np.save(L/'air.npy',air)
record={'origin':[ox,0,oz],'shape':list(v.shape),'names':names,'rooms':rooms,'paths':paths,'native-cave-systems':len(keep),'new-air':int(air.sum()),'changed-voxels':len(changed),'ores':ore_report,'relocated-original-ores':len(cut),'lights':[[int(p[2])+ox,int(p[0]),int(p[1])+oz]for p in lights],'safe-chunks':island['safe-chunks'],'war-chunks':island['build-war-chunks'],'reference':reference_meta,'features':features,'surface-protected-layers':12,'rock-clearance':6};(L/'generation.json').write_text(json.dumps(record,indent=2));print('GENERATED',len(keep),'native cave systems',int(air.sum()),'air cells',len(changed),'changes',flush=True)
