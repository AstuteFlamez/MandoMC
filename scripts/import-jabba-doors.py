#!/usr/bin/env python3
"""Recover only private Jabba gate panels from the operator-supplied legacy map/database. No BigDoors runtime dependency."""
import struct,zlib,io,collections,json
from pathlib import Path
class NBT:
 def __init__(self,b):self.f=io.BytesIO(b)
 def n(self,fmt):return struct.unpack('>'+fmt,self.f.read(struct.calcsize(fmt)))[0]
 def string(self):return self.f.read(self.n('H')).decode('utf8')
 def tag(self,t):
  if t in [1,2,3,4,5,6]:return self.n({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.f.read(self.n('i'))
  if t==8:return self.string()
  if t==9:
   k=self.n('B');return [self.tag(k) for _ in range(self.n('i'))]
  if t==10:
   d={}
   while (k:=self.n('B'))!=0:
    s=self.string();d[s]=self.tag(k)
   return d
  if t in [11,12]:return [self.n('i' if t==11 else 'q') for _ in range(self.n('i'))]
 def root(self):k=self.n('B');self.string();return self.tag(k)
root=None;cache={}
def block(x,y,z):
 cx,cz=x//16,z//16;key=(cx,cz)
 if key not in cache:
  p=root/f'r.{cx//32}.{cz//32}.mca';b=p.read_bytes();v=int.from_bytes(b[((cx%32)+(cz%32)*32)*4:][:4],'big');off=(v>>8)*4096;n=int.from_bytes(b[off:off+4],'big');raw=b[off+5:off+4+n];d=NBT(zlib.decompress(raw)).root();cache[key]=d
 d=cache[key];sections=d.get('sections',d.get('Level',{}).get('Sections',[]));s=next((s for s in sections if s['Y']==y//16),None)
 if not s or 'block_states' not in s:return {'Name':'minecraft:air'}
 states=s['block_states'];p=states['palette']
 if len(p)==1:return p[0]
 bits=max(4,(len(p)-1).bit_length());per=64//bits;i=(y%16)*256+(z%16)*16+x%16;v=states['data'][i//per]&((1<<64)-1);return p[(v>>((i%per)*bits))&((1<<bits)-1)]

if __name__=='__main__':
 import argparse,sqlite3,hashlib
 parser=argparse.ArgumentParser();parser.add_argument('legacy',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
 root=args.legacy/'Tatooine/region';db=args.legacy/'plugins/BigDoors/doorDB.db'
 result={'schema-version':1,'world':'minecraft:tatooine','source-db-sha256':hashlib.sha256(db.read_bytes()).hexdigest(),'gates':{}}
 for row in sqlite3.connect(f'file:{db}?mode=ro',uri=True).execute('select id,xMin,yMin,zMin,xMax,yMax,zMax,engineY,isOpen,openDirection from doors where id between 15 and 23 order by id'):
  id,x,y,z,X,Y,Z,engine,opened,direction=row;shift=y-engine if opened else 0;cells=[];clear=[]
  for a in range(x,X+1):
   for b in range(y,Y+1):
    for c in range(z,Z+1):
     data=block(a,b,c);props=data.get('Properties',{});value=data['Name']+('['+','.join(k+'='+v for k,v in sorted(props.items()))+']' if props else '')
     cells.append(f'{a},{b-shift},{c}|{value}')
     if shift and b>Y-shift and data['Name']!='minecraft:air':clear.append(f'{a},{b},{c}')
  result['gates'][str(id)]={'blocks':cells,'clear-imported':clear}
 args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n');print(f'Recovered {len(result["gates"])} private native gate panels.')
