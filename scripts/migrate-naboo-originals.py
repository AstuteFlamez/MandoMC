#!/usr/bin/env python3
"""Derive new originals by sparse changes, preserving every unmodified pristine cell and input file."""
from pathlib import Path
import collections,gzip,hashlib,io,json,struct
import numpy as np
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work-dir',type=Path,required=True);parser.add_argument('--baseline-dir',type=Path,required=True);parser.add_argument('--output-dir',type=Path,required=True);args=parser.parse_args();L=args.work_dir;m=json.loads((L/'final-generation.json').read_text());scene=json.loads((L/'scene.json').read_text());ox,oy,oz=scene['origin'];source=np.load(L/'blocks.npy',mmap_mode='r');after=np.load(L/'final.npy',mmap_mode='r');points=np.load(L/'final-changed.npy');active=args.baseline_dir;out=args.output_dir;assert out.resolve()!=active.resolve() and active.resolve() not in out.resolve().parents,'Output must be separate from active originals';out.mkdir(parents=True,exist_ok=True);groups=collections.defaultdict(list)
for x,y,z in points:groups[int(x)//16,int(z)//16].append((int(x),int(y),int(z)))
record=[]
def utf(s):
 b=s.encode('ascii');return struct.pack('>H',len(b))+b
for n,((cx,cz),cells)in enumerate(sorted(groups.items())):
 path=active/f'minecraft_naboo_{cx}_{cz}.gz'
 if not path.exists():continue
 raw=gzip.decompress(path.read_bytes());f=io.BytesIO(raw)
 def i():return struct.unpack('>i',f.read(4))[0]
 def u():return f.read(struct.unpack('>H',f.read(2))[0]).decode('ascii')
 assert i()==1;world=u();x,z,ymin,ymax=i(),i(),i(),i();due=struct.unpack('>q',f.read(8))[0];cursor=i();count=i();palette=[u()for _ in range(count)];lookup={s:j for j,s in enumerate(palette)};a=np.frombuffer(f.read((ymax-ymin)*256*4),dtype='>i4').copy();tail=f.read();assert (x,z)==(cx,cz);assert world=='minecraft:naboo'
 for x,y,z in cells:
  before='minecraft:'+scene['names'][int(source[y-oy,z-oz,x-ox])];target='minecraft:'+m['names'][int(after[y,z-oz,x-ox])];index=(y-ymin)*256+(z%16)*16+x%16
  assert palette[int(a[index])]==before,('Original differs from changed cell',x,y,z,palette[int(a[index])],before)
  if target not in lookup:lookup[target]=len(palette);palette.append(target)
  a[index]=lookup[target]
 header=struct.pack('>i',1)+utf(world)+struct.pack('>iiiiqi',cx,cz,ymin,ymax,due,cursor)+struct.pack('>i',len(palette))+b''.join(utf(s)for s in palette)
 data=gzip.compress(header+a.tobytes()+tail,compresslevel=6,mtime=0);target=out/path.name;assert not target.exists() or target.read_bytes()==data,'Refusing to replace a differing derived original';target.write_bytes(data);record.append({'chunk':[cx,cz],'old-sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'new-sha256':hashlib.sha256(data).hexdigest(),'changed-cells':len(cells)})
 if n%100==0:print('Derived originals',n+1,'/',len(groups),flush=True)
(L/'original-migration.json').write_text(json.dumps({'chunks':record,'unchanged-original-cells-preserved':True,'old-active-originals-must-be-archived':True},indent=2));print('Derived',len(record),'cave/recolor originals by exact sparse migration, preserving every other pristine cell')
