#!/usr/bin/env python3
"""Apply a checked sparse Naboo delta to an explicitly stopped, backed-up world."""
from pathlib import Path
import argparse,collections,json,sys
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parent/'map_tools'))
from native_world import *


def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--world',type=Path,required=True)
 parser.add_argument('--work-dir',type=Path,required=True)
 parser.add_argument('--server-stopped',action='store_true',required=True)
 args=parser.parse_args();L=args.work_dir;m=json.loads((L/'final-generation.json').read_text());meta=json.loads((L/'scene.json').read_text());names=m['names'];ox,oy,oz=meta['origin'];source=np.load(L/'blocks.npy',mmap_mode='r');after=np.load(L/'final.npy',mmap_mode='r');points=np.load(L/'final-changed.npy');native=NativeWorld(args.world);groups=collections.defaultdict(list)
 for x,y,z in points:groups[int(x)//16,int(z)//16,int(y)//16].append((int(x),int(y),int(z)))
 payloads={};expected={};touched=set();checked=0
 for (cx,cz,sy),cells in groups.items():
  f=native.chunk(cx,cz);payloads.setdefault((cx,cz),native.regions[cx//32,cz//32][cx%32+cz%32*32]);touched.add((cx,cz));sec=next(s for s in f['sections']if int(s['Y'])==sy);pal=list(sec['block_states']['palette']);ns=[block_name(p)for p in pal];lookup={n:i for i,n in enumerate(ns)};a=unpack(sec['block_states']).copy()
  for x,y,z in cells:
   before=meta['names'][int(source[y-oy,z-oz,x-ox])];target=names[int(after[y,z-oz,x-ox])];index=y%16*256+z%16*16+x%16
   if ns[int(a[index])]!=before:raise RuntimeError(('Concurrent terrain edit',x,y,z,ns[int(a[index])],before))
   if target not in lookup:lookup[target]=len(ns);ns.append(target);pal.append(palette_tag(target))
   a[index]=lookup[target]
  unique,inverse=np.unique(a,return_inverse=True);state=T.Compound({'palette':T.List[T.Compound]([pal[int(i)]for i in unique])})
  if len(unique)>1:state['data']=packed(inverse,max(4,(len(unique)-1).bit_length()))
  sec['block_states']=state;expected[cx,cz,sy]=(ns,a)
 for cx,cz in touched:
  f=native.chunks[cx,cz];old=parse(payloads[cx,cz]);f['isLightOn']=T.Byte(0);f['Status']=T.String('minecraft:features')
  for sec in f['sections']:sec.pop('BlockLight',None);sec.pop('SkyLight',None)
  payload=encode(f);check=parse(payload);assert check.get('block_entities')==old.get('block_entities');assert check.get('Heightmaps')==old.get('Heightmaps')
  for sec in check['sections']:
   sy=int(sec['Y']);prior=next(s for s in old['sections']if int(s['Y'])==sy);assert sec.get('biomes')==prior.get('biomes')
   if 'block_states'not in sec:continue
   an=[block_name(p)for p in sec['block_states']['palette']];a=unpack(sec['block_states'])
   if (cx,cz,sy)in expected:ns,values=expected[cx,cz,sy]
   else:ns=[block_name(p)for p in prior['block_states']['palette']];values=unpack(prior['block_states'])
   lookup=np.array([ns.index(n)if n in ns else-1 for n in an]);assert np.array_equal(lookup[a],values);checked+=4096
  native.regions[cx//32,cz//32][cx%32+cz%32*32]=payload
 # Every compare-and-swap and round trip succeeds before the first region replacement.
 for key,data in native.regions.items():
  if not any(cx//32==key[0]and cz//32==key[1]for cx,cz in touched):continue
  path=args.world/'region'/f'r.{key[0]}.{key[1]}.mca';temporary=path.with_suffix('.mca.naboo-tmp');write_records(temporary,data);temporary.replace(path)
 record={'changed-voxels':len(points),'changed-chunks':len(touched),'roundtrip-voxels':checked,'chunks':sorted(touched),'block-entities-biomes-heightmaps-preserved':True};(L/'delta-roundtrip.json').write_text(json.dumps(record,indent=2));print({k:v for k,v in record.items()if k!='chunks'})


if __name__=='__main__':main()
