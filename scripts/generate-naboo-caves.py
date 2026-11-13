#!/usr/bin/env python3
"""Generate organic merged caverns with winding passages and continuous rolling floors."""
from pathlib import Path
import json,math,heapq,collections
import numpy as np
from scipy import ndimage
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work-dir',type=Path,required=True);args=parser.parse_args();L=args.work_dir;m=json.loads((L/'scene.json').read_text());full=np.load(L/'blocks.npy',mmap_mode='r');original=np.asarray(full[64:208]);v=original.copy();names=m['names'].copy();ids={n:i for i,n in enumerate(names)};base=np.array([n.split('[')[0]for n in names]);rng=np.random.default_rng(20261113)
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
# Smooth, shared rolling floors make galleries continuous without artificial walking shelves.
Z2,X2=np.mgrid[0:512,0:512];fields={};layers={};rooms=[];paths=[];air=np.zeros(v.shape,bool)
for level in [14,40,76]:
 phase=level*.071
 field=level+3.4*np.sin(X2/69+phase)+2.6*np.sin(Z2/83-phase)+1.4*np.sin((X2+Z2)/43+phase)+.55*np.sin(X2/14)*np.cos(Z2/17)
 fields[level]=field.astype(np.float32);feet=np.rint(field).astype(int);valid=(feet>=9)&(feet<112)&(ground>=feet+30)&(wclear>=14)
 valid&=dist[np.clip(feet+4,0,143),Z2,X2]>=12
 coarse=valid[18:495:4,18:495:4];labels,n=ndimage.label(coarse);sizes=np.bincount(labels.ravel());sizes[0]=0;coarse=labels==int(sizes.argmax());zz,xx=np.where(coarse);positions=np.column_stack((18+xx*4,18+zz*4));rng.shuffle(positions)
 selected=[];wanted={14:40,40:32,76:22}[level]
 for x,z in positions:
  if len(selected)>=wanted:break
  if any((x-a)**2+(z-b)**2<43**2 for a,b in selected):continue
  selected.append((int(x),int(z)))
 layers[level]={'coarse':coarse,'selected':selected,'rooms':[]}
print('Organic room candidates',{k:len(d['selected'])for k,d in layers.items()},flush=True)

def floor_at(level,x,z):return float(fields[level][int(round(z)),int(round(x))])
def carve_form(cx,cz,rx,rz,height,field,angle=0.,rough=.12):
 x1=max(0,int(cx-rx-rz*.25-3));x2=min(512,int(cx+rx+rz*.25+4));z1=max(0,int(cz-rz-rx*.25-3));z2=min(512,int(cz+rz+rx*.25+4));basefield=field[z1:z2,x1:x2];y1=max(7,int(basefield.min())-2);y2=min(143,int(basefield.max()+height+5))
 Y,Z,X=np.ogrid[y1:y2,z1:z2,x1:x2];dx=X-cx;dz=Z-cz;u=dx*math.cos(angle)+dz*math.sin(angle);w=-dx*math.sin(angle)+dz*math.cos(angle);r=(u/rx)**2+(w/rz)**2
 edge=rough*(np.sin(X/6.7+Z/11.3)+.6*np.sin(Z/8.1-X/14.7));r=r+edge
 bottom=np.rint(basefield)[None,:,:]
 cap=height*np.sqrt(np.clip(1-r,0,1))+1.7*np.sin(X/9.1)*np.sin(Z/10.7)+1.5
 roof=np.minimum(bottom+np.maximum(2,cap),ground[None,z1:z2,x1:x2]-17.5-.8*np.sin(X/5.1+Z/8.3))
 model=(r<1)&(Y>=bottom)&(Y<=roof)
 air[y1:y2,z1:z2,x1:x2]|=model&eligible[y1:y2,z1:z2,x1:x2]

for level,layer in layers.items():
 for x,z in layer['selected']:
  rx=float(rng.integers(11,23));rz=float(rng.integers(10,21));height=float(rng.integers(8,19));angle=float(rng.uniform(-math.pi,math.pi));carve_form(x,z,rx,rz,height,fields[level],angle)
  # Offset overlapping lobes form asymmetric caverns, alcoves and overhangs.
  for j in range(int(rng.integers(1,4))):
   theta=float(rng.uniform(-math.pi,math.pi));distance=float(rng.uniform(5,rx*.8));cx=x+math.cos(theta)*distance;cz=z+math.sin(theta)*distance
   carve_form(cx,cz,rx*float(rng.uniform(.5,.85)),rz*float(rng.uniform(.5,.85)),height*float(rng.uniform(.65,1.15)),fields[level],angle+float(rng.uniform(-1,1)),.16)
  i=len(rooms);rooms.append({'name':f'c{i}','center':[x,float(round(floor_at(level,x,z))),z],'radii':[rx,rz],'height':height,'level':level});layer['rooms'].append(i)

def route2d(a,b,layer):
 x0,z0=layers[layer]['selected'][a];x1,z1=layers[layer]['selected'][b];start=((z0-18)//4,(x0-18)//4);goal=((z1-18)//4,(x1-18)//4);grid=layers[layer]['coarse'];best={start:0.};prev={};q=[(0.,start)];seen=set()
 while q:
  _,p=heapq.heappop(q)
  if p in seen:continue
  seen.add(p)
  if p==goal:
   result=[p]
   while result[-1]!=start:result.append(prev[result[-1]])
   return[(18+x*4,18+z*4)for z,x in result[::-1]]
  for dz,dx in [(0,1),(1,0),(0,-1),(-1,0),(1,1),(1,-1),(-1,1),(-1,-1)]:
   n=(p[0]+dz,p[1]+dx)
   if not(0<=n[0]<grid.shape[0]and 0<=n[1]<grid.shape[1])or not grid[n]:continue
   x,z=18+n[1]*4,18+n[0]*4;noise=.5+.25*math.sin(x/31+z/43)+.25*math.sin(z/28-x/52);cost=best[p]+math.hypot(dx,dz)*4*(1+noise*.9)
   if cost>=best.get(n,1e100):continue
   best[n]=cost;prev[n]=p;heapq.heappush(q,(cost+math.hypot((n[1]-goal[1])*4,(n[0]-goal[0])*4),n))
 raise RuntimeError('Disconnected organic gallery')
for level,layer in layers.items():
 selected=layer['selected'];connected={0};links=[]
 while len(connected)<len(selected):
  _,a,b=min((math.dist(selected[a],selected[b]),a,b)for a in connected for b in range(len(selected))if b not in connected);links.append((a,b));connected.add(b)
 existing={tuple(sorted(v))for v in links}
 for a in range(len(selected)):
  for b in sorted(range(len(selected)),key=lambda b:math.dist(selected[a],selected[b]))[1:3]:
   edge=tuple(sorted((a,b)))
   if edge not in existing and math.dist(selected[a],selected[b])<120:links.append((a,b));existing.add(edge)
 for a,b in links:
  points=route2d(a,b,level);phase=float(rng.uniform(0,6.28));travel=0.;dense=[]
  for start,end in zip(points,points[1:]):
   length=math.dist(start,end);steps=max(2,int(length*1.3))
   for t in np.linspace(0,1,steps,endpoint=False):
    x=start[0]+(end[0]-start[0])*t;z=start[1]+(end[1]-start[1])*t;radius=4.7+1.35*math.sin((travel+t*length)/17+phase);height=5.8+1.4*math.cos((travel+t*length)/23+phase)
    carve_form(x,z,radius,radius*.88,height,fields[level],rough=.09);dense.append([x,floor_at(level,x,z),z])
   travel+=length
  dense.append([points[-1][0],floor_at(level,*points[-1]),points[-1][1]])
  paths.append({'from':layer['rooms'][a],'to':layer['rooms'][b],'points':dense,'kind':'winding'})
 print('Winding gallery level',level,'rooms',len(selected),'routes',len(links),flush=True)

for low,high in [(14,40),(40,76)]:
 options=[]
 for a in layers[low]['rooms']:
  for b in layers[high]['rooms']:
   start=np.array(rooms[a]['center']);end=np.array(rooms[b]['center']);distance=math.hypot(end[0]-start[0],end[2]-start[2])
   if not 140<=distance<=280:continue
   steps=int(distance)+1;t=np.linspace(0,1,steps);xx=np.rint(start[0]+(end[0]-start[0])*t).astype(int);zz=np.rint(start[2]+(end[2]-start[2])*t).astype(int);u=np.clip((t-.20)/.60,0,1);blend=u*u*(3-2*u);floors=(1-blend)*fields[low][zz,xx]+blend*fields[high][zz,xx];yy=np.rint(floors).astype(int)
   ok=(dist[yy+4,zz,xx]>=11)&(ground[zz,xx]>=yy+27)&(wclear[zz,xx]>=12)&~air[yy-1,zz,xx]
   if np.all(ok):options.append((distance,a,b))
 if not options:raise RuntimeError(('No natural ramp corridor',low,high))
 distance,a,b=min(options);start=np.array(rooms[a]['center']);end=np.array(rooms[b]['center']);vx,vz=end[0]-start[0],end[2]-start[2];progress=((X2-start[0])*vx+(Z2-start[2])*vz)/(vx*vx+vz*vz);u=np.clip((progress-.20)/.60,0,1);blend=u*u*(3-2*u);field=(1-blend)*fields[low]+blend*fields[high];points=[]
 for t in np.linspace(0,1,int(distance*1.5)+1):
  x=start[0]+vx*t;z=start[2]+vz*t;carve_form(x,z,4.8,4.4,6.5,field,rough=.08);points.append([x,float(field[int(round(z)),int(round(x))]),z])
 paths.append({'from':a,'to':b,'points':points,'kind':'natural-ramp'});print('Natural sloping connection',low,high,'length',distance,flush=True)
v[air]=bid('air');print('Organic air volume',int(air.sum()),'rooms',len(rooms),'routes',len(paths),flush=True)
# Coherent limestone/andesite walls hide the old random cobblestone terrain fill.
shell=ndimage.binary_dilation(air,iterations=2)&~air&eligible;coords=np.argwhere(shell);Y,Z,X=coords.T;noise=np.sin(X/14+.25*Z/11)+.8*np.sin(Z/17-Y/9);paint=~np.isin(original[Y,Z,X],oreids);rock=np.where(noise>1.25,bid('diorite'),np.where(noise< -1.3,bid('andesite'),bid('stone')));v[Y[paint],Z[paint],X[paint]]=rock[paint]
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
for family,state,wanted,size in [('agrinium','iron_ore',360,8),('durasteel','diamond_ore',300,7),('electrum','redstone_ore[lit=false]',330,7)]:
 face=exposed&eligible&np.isin(v,rockids);seeds=np.argwhere(face);rng.shuffle(seeds);taken=[];ore=bid(state);placed=0
 for seed in seeds:
  if len(taken)>=wanted:break
  if any(np.sum((seed-q)**2)<30 for q in taken):continue
  pos=seed.copy();count=0
  for _ in range(size+int(rng.integers(2,7))):
   y,z,x=map(int,pos)
   if 7<=y<142 and 1<=z<511 and 1<=x<511 and eligible[y,z,x] and int(v[y,z,x])in rockids:v[y,z,x]=ore;count+=1
   pos+=rng.integers(-1,2,size=3);pos[0]=np.clip(pos[0],7,141);pos[1:]=np.clip(pos[1:],1,510)
  if count:taken.append(seed);placed+=count
 ore_report[family]={'new-hosts':placed,'veins':len(taken),'state':state};print(family,ore_report[family],flush=True)
# Low-key supported glow lichen, avoiding floating or unsupported decorations.
lichen=bid('glow_lichen[down=true,east=false,north=false,south=false,up=false,waterlogged=false,west=false]');floors=np.argwhere(air[1:-2]&air[2:-1]&~air[:-3]);floors[:,0]+=1;rng.shuffle(floors);lights=[]
for p in floors:
 if len(lights)>=450:break
 if all(np.sum((p-q)**2)>=49 for q in lights):
  y,z,x=map(int,p)
  if v[y,z,x]==0 and v[y-1,z,x]!=0:v[y,z,x]=lichen;lights.append(p)
diff=v!=original;assert not np.any(diff[:,~wild]);assert not np.any(diff&(ys>=ground[None,:,:]-12));assert np.array_equal(v[0],original[0]);assert not np.any(air&~eligible)
for state in ['iron_ore','redstone_ore','diamond_ore','coal_ore','gold_ore']:
 oldids=[i for i,n in enumerate(m['names'])if n.split('[')[0]==state];newids=[i for i,n in enumerate(names)if n.split('[')[0]==state];family={'iron_ore':'agrinium','redstone_ore':'electrum','diamond_ore':'durasteel'}.get(state);assert np.isin(v,newids).sum()==np.isin(original,oldids).sum()+(ore_report.get(family,{}).get('new-hosts',0))
changed=np.argwhere(diff);world=changed[:,[2,0,1]].astype(np.int32);world[:,0]+=ox;world[:,2]+=oz;np.save(L/'updated.npy',v);np.save(L/'changed.npy',world);np.save(L/'air.npy',air)
record={'origin':[ox,0,oz],'shape':list(v.shape),'names':names,'rooms':rooms,'paths':paths,'new-air':int(air.sum()),'changed-voxels':len(changed),'ores':ore_report,'relocated-original-ores':len(cut),'lights':[[int(p[2])+ox,int(p[0]),int(p[1])+oz]for p in lights],'safe-chunks':island['safe-chunks'],'war-chunks':island['build-war-chunks'],'surface-protected-layers':12,'rock-clearance':6};(L/'generation.json').write_text(json.dumps(record,indent=2));print('GENERATED',len(rooms),'rooms',len(paths),'routes',int(air.sum()),'air cells',len(changed),'changes',flush=True)
