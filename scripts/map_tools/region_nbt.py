"""Read and rewrite standard region records and palette states without discarding unrelated data."""
from pathlib import Path
import numpy as np,nbtlib,io,zlib,gzip,struct,time
from nbtlib import tag as T

def records(path):
 raw=path.read_bytes();out={}
 for i in range(1024):
  pos=int.from_bytes(raw[i*4:i*4+3],'big')*4096
  if pos:
   size=int.from_bytes(raw[pos:pos+4],'big');out[i]=raw[pos+4:pos+4+size]
 return out

def parse(payload):
 mode=payload[0];data=payload[1:]
 if mode==2:data=zlib.decompress(data)
 elif mode==1:data=gzip.decompress(data)
 elif mode!=3:raise ValueError(('Unsupported compression',mode))
 return nbtlib.File.parse(io.BytesIO(data))

def encode(f):
 b=io.BytesIO();f.write(b);return b'\x02'+zlib.compress(b.getvalue(),6)

def write_records(path,items):
 header=bytearray(8192);body=bytearray();sector=2
 for i,payload in sorted(items.items()):
  record=len(payload).to_bytes(4,'big')+payload;n=(len(record)+4095)//4096;assert n<256
  header[i*4:i*4+4]=(sector<<8|n).to_bytes(4,'big');header[4096+i*4:4100+i*4]=int(time.time()).to_bytes(4,'big')
  body+=record+b'\x00'*(n*4096-len(record));sector+=n
 path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(header+body)

def block_name(p):
 name=str(p['Name']).removeprefix('minecraft:');props=p.get('Properties',{})
 return name+('['+','.join(k+'='+str(val) for k,val in sorted(props.items()))+']' if props else '')

def unpack(state):
 pal=state['palette']
 if len(pal)==1:return np.zeros(4096,np.int32)
 bits=max(4,(len(pal)-1).bit_length());a=np.asarray(state['data'],np.int64).view(np.uint64);i=np.arange(4096);per=64//bits
 return ((a[i//per]>>((i%per)*bits).astype(np.uint64))&np.uint64((1<<bits)-1)).astype(np.int32)

def packed(values,bits):
 per=64//bits;values=np.asarray(values,dtype=np.uint64).ravel();a=np.zeros(((len(values)+per-1)//per)*per,np.uint64);a[:len(values)]=values;a=a.reshape(-1,per);out=np.zeros(len(a),np.uint64)
 for j in range(per):out|=a[:,j]<<np.uint64(j*bits)
 return T.LongArray(out.view(np.int64))

def palette_tag(name):
 base=name.split('[')[0];p=T.Compound({'Name':T.String('minecraft:'+base)})
 if '[' in name:p['Properties']=T.Compound({k:T.String(v) for k,v in (s.split('=') for s in name.split('[')[1].rstrip(']').split(','))})
 return p
