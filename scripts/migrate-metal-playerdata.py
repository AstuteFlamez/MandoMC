#!/usr/bin/env python3
"""Offline one-for-one sheet migration, including nested NBT and Paper inventory escrow.

Requires nbtlib, PyYAML and canonical ItemStack templates exported by the native probe.
Dry-run by default. --apply holds the world's native session lock, backs up every
changed file, verifies unchanged source bytes, then atomically replaces each file.
"""
import argparse,base64,copy,fcntl,gzip,hashlib,io,json,os,re,struct,tempfile
from pathlib import Path
import nbtlib,yaml
from nbtlib import tag as T

SHEETS={m+'_sheet':m for m in ('agrinium','aurodium','beskar','cortosis','durasteel','electrum','lommite','neuranium','phrik','songsteel')}
SHEETS['mandalorian_sheet']='mandalorian_iron'

def read_item(data):
    return nbtlib.File.parse(io.BytesIO(gzip.decompress(data)))

def write_item(nbt):
    out=io.BytesIO();nbtlib.File(dict(nbt),root_name=getattr(nbt,"root_name","")).write(out);return gzip.compress(out.getvalue(),mtime=0)

class Migration:
    def __init__(self,templates):
        self.templates={k:read_item(base64.b64decode(v)) for k,v in templates.items()}
        self.counts={}
        if set(self.templates)!=set(SHEETS.values()):raise ValueError('Templates must contain all eleven ingots')
        for key,t in self.templates.items():
            if self.item_id(t)!=key:raise ValueError('Template identity mismatch: '+key)
    @staticmethod
    def item_id(node):
        return str(node.get('components',{}).get('minecraft:custom_data',{}).get('PublicBukkitValues',{}).get('mandomc:item_id',''))
    def blob(self,data):
        if data.startswith(b'\x1f\x8b'):
            try:n=read_item(data)
            except Exception:return data
            before=dict(self.counts);self.visit(n)
            return write_item(n) if self.counts!=before else data
        # Paper ItemStack.serializeItemsAsBytes: version byte, count, length-prefixed gzip NBT.
        if len(data)<5 or data[0]!=1:return data
        count=struct.unpack_from('>i',data,1)[0]
        if not 0<=count<=4096:return data
        offset=5;parts=[]
        try:
            for _ in range(count):
                size=struct.unpack_from('>i',data,offset)[0];offset+=4
                if size<0 or offset+size>len(data):return data
                part=data[offset:offset+size];offset+=size
                if part and not part.startswith(b'\x1f\x8b'):return data
                parts.append(part)
            if offset!=len(data):return data
        except struct.error:return data
        changed=[self.blob(part) if part else part for part in parts]
        return data[:5]+b''.join(struct.pack('>i',len(p))+p for p in changed)
    def visit(self,node):
        if isinstance(node,T.Compound):
            old=self.item_id(node)
            if old in SHEETS:
                count=int(node.get('count',1));template=copy.deepcopy(self.templates[SHEETS[old]])
                # Replace only item identity/presentation, retaining slot, quantity and unrelated outer data.
                node['id']=template['id'];node['components']=template['components']
                self.counts[old]=self.counts.get(old,0)+count
            for key,value in list(node.items()):
                if isinstance(value,T.ByteArray):
                    raw=value.tobytes();updated=self.blob(raw)
                    if updated!=raw:node[key]=T.ByteArray(list(b if b<128 else b-256 for b in updated))
                elif isinstance(value,T.String):
                    # Known MandoMC escrow stores individual ItemStacks as base64 lines.
                    if str(key).startswith('mandomc:'):
                        lines=str(value).split('\n');out=[];changed=False
                        for line in lines:
                            prefix='';payload=line
                            if re.match(r'^[0-9a-fA-F-]{36}=',line):prefix,payload=line[:37],line[37:]
                            try:raw=base64.b64decode(payload,validate=True)
                            except Exception:out.append(line);continue
                            updated=self.blob(raw);out.append(prefix+base64.b64encode(updated).decode() if updated!=raw else line);changed|=updated!=raw
                        if changed:node[key]=T.String('\n'.join(out))
                else:self.visit(value)
        elif isinstance(node,T.List):
            for item in node:self.visit(item)

def migrate(world,templates,backup,apply=False):
    world=Path(world).resolve();backup=Path(backup).resolve()
    if not (world/'level.dat').is_file():raise ValueError('Expected a Minecraft world root with level.dat')
    lock=None
    if apply:
        lock=open(world/'session.lock','r+b')
        try:fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BaseException:lock.close();raise
    report={'filesScanned':0,'filesChanged':0,'itemsConverted':{},'files':[]}
    try:
        files=sorted(set(world.glob('playerdata/*.dat'))|set(world.glob('players/data/*.dat')))
        for path in files:
            original=path.read_bytes();nbt=read_item(original);worker=Migration(templates);worker.visit(nbt);report['filesScanned']+=1
            if not worker.counts:continue
            data=write_item(nbt);again=Migration(templates);again.visit(read_item(data))
            if again.counts:raise AssertionError('Migration was not idempotent')
            relative=path.relative_to(world);before=hashlib.sha256(original).hexdigest();after=hashlib.sha256(data).hexdigest()
            report['filesChanged']+=1;report['files'].append({'path':str(relative),'before':before,'after':after,'items':worker.counts})
            for k,v in worker.counts.items():report['itemsConverted'][k]=report['itemsConverted'].get(k,0)+v
            if apply:
                backup.mkdir(parents=True,exist_ok=True,mode=0o700);backup.chmod(0o700)
                saved=backup/relative;saved.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
                with saved.open('xb') as out:out.write(original)
                if path.read_bytes()!=original:raise RuntimeError('Player data changed during migration: '+str(relative))
                fd,name=tempfile.mkstemp(prefix=path.name+'.migration-',dir=path.parent)
                try:
                    with os.fdopen(fd,'wb') as out:out.write(data);out.flush();os.fsync(out.fileno())
                    os.chmod(name,path.stat().st_mode&0o777);os.replace(name,path)
                finally:
                    if os.path.exists(name):os.unlink(name)
                if path.read_bytes()!=data:raise IOError('Player data readback mismatch')
        return report
    finally:
        if lock:lock.close()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',type=Path,required=True);p.add_argument('--templates',type=Path,required=True);p.add_argument('--backup',type=Path,required=True);p.add_argument('--report',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    report=migrate(a.world,yaml.safe_load(a.templates.read_text()),a.backup,a.apply)
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='files'}))
if __name__=='__main__':main()
