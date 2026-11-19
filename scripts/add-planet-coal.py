#!/usr/bin/env python3
"""Plan/apply bounded coal veins in existing planet caves; retain frozen originals and a separate restoration overlay.

Requires scripts/map_tools/requirements.txt plus PyYAML. Dry-run by default.
Use --apply only against a stopped runtime; native session locks are acquired first.
Every modified region and the prior overlay are backed up before replacement.
"""
import argparse, collections, fcntl, hashlib, json, os, sys
from pathlib import Path
import numpy as np
import yaml
from scipy import ndimage
sys.path.insert(0, str(Path(__file__).resolve().parent/'map_tools'))
from frozen_land import baseline
from native_world import NativeWorld, records, parse, encode, write_records, block_name, unpack, packed, palette_tag, T

HOSTS={'stone','deepslate','tuff','andesite','diorite','granite','sandstone','packed_ice','blue_ice','ice'}
AIR={'air','cave_air','void_air'}
WORLDS={'minecraft:tatooine_review','minecraft:hoth','minecraft:mandalore_review','minecraft:naboo'}

def digest(raw): return hashlib.sha256(raw).hexdigest()

def candidates(volume,names,seed):
    """Only cave-exposed natural rock, 12 blocks below terrain; no ores, tile entities or structures."""
    base=np.array([n.split('[')[0] for n in names]);solid=~np.isin(volume,[i for i,n in enumerate(base) if n in AIR or n in {'water','lava'}])
    top=volume.shape[0]-1-np.argmax(solid[::-1],axis=0)
    hosts=np.isin(volume,[i for i,n in enumerate(base) if n in HOSTS])
    air=np.isin(volume,[i for i,n in enumerate(base) if n in AIR])
    exposed=ndimage.binary_dilation(air)&hosts
    structural=[i for i,n in enumerate(base) if any(s in n for s in ('_bricks','_planks','_stairs','_slab','_wall','_door','chest','barrel','glass','concrete','quartz','cut_sandstone','smooth_sandstone'))]
    buildings=ndimage.binary_dilation(np.any(np.isin(volume,structural),axis=0),iterations=6)
    exposed[:,buildings]=False
    exposed &= np.arange(volume.shape[0])[:,None,None] < top[None,:,:]-12
    exposed[:2]=False;exposed[:,[0,-1]]=False;exposed[:,:,[0,-1]]=False
    positions=np.argwhere(exposed);rng=np.random.default_rng(seed);rng.shuffle(positions)
    chosen=np.zeros_like(exposed)
    for y,z,x in positions:
        if chosen.sum()>=16: break
        if chosen[max(0,y-5):y+6,max(0,z-5):z+6,max(0,x-5):x+6].any():continue
        cluster=np.zeros_like(exposed);cluster[y,z,x]=True
        cluster=ndimage.binary_dilation(cluster,iterations=2)&hosts
        cluster[:2]=False;cluster[:,buildings]=False
        cluster &= np.arange(volume.shape[0])[:,None,None] < top[None,:,:]-12
        cluster[:,[0,-1]]=False;cluster[:,:,[0,-1]]=False
        for pos in np.argwhere(cluster)[:8]:chosen[tuple(pos)]=True
    return np.argwhere(chosen)

def migrate(runtime,output,apply=False):
    config=runtime/'plugins/MandoMC';worldroot=runtime/'dev_world'
    # Production may use another root level name; derive it from server.properties.
    props=dict(l.split('=',1) for l in (runtime/'server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
    worldroot=runtime/props.get('level-name','world')
    territory=yaml.safe_load((config/'territory/maps.yml').read_text());maps={territory['world']:territory}
    maps.update({m['world']:m for m in territory.get('additional-maps',{}).values()})
    claims=yaml.safe_load((config/'data/clans.yml').read_text()).get('claims',[])
    claimed={(c['world'],c['x'],c['z']) for c in claims}
    overlay=config/'data/coal-terrain.yml';old_overlay=overlay.read_bytes() if overlay.exists() else None
    if old_overlay:
        data=yaml.safe_load(old_overlay)
        if data.get('migration-id')!='planet-coal-v1':raise ValueError('Unknown existing coal overlay')
        return {'counts':{w:0 for w in WORLDS},'changes':[],'files':[]}
    rows=set()
    locks=[];staged={};counts=collections.Counter();changes=[];output.mkdir(parents=True,exist_ok=True)
    try:
        if apply:
            for lockpath in sorted(set(worldroot.rglob('session.lock'))|{worldroot/'session.lock'}):
                lock=open(lockpath,'r+b');fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);locks.append(lock)
        for world in sorted(WORLDS):
            atlas=maps[world];native=NativeWorld(worldroot/'dimensions'/world.replace(':','/'));touched=set()
            for cz in range(atlas['min-chunk-z'],atlas['max-chunk-z']+1):
                for cx in range(atlas['min-chunk-x'],atlas['max-chunk-x']+1):
                    zone=atlas['rows'][cz-atlas['min-chunk-z']][cx-atlas['min-chunk-x']]
                    if zone=='S' or (world,cx,cz) in claimed:continue
                    bp=config/'data/land-baselines'/f"{world.replace(':','_')}_{cx}_{cz}.gz"
                    original=baseline(bp) if bp.exists() else None
                    if zone=='.' and original is None:continue
                    try:chunk=native.chunk(cx,cz)
                    except (KeyError,FileNotFoundError):continue
                    sections={int(s['Y']):s for s in chunk['sections'] if 'block_states' in s}
                    lo=min(sections)*16;hi=(max(sections)+1)*16;names=['air'];lookup={'air':0};volume=np.zeros((hi-lo,16,16),np.int32)
                    for sy,section in sections.items():
                        ids=[]
                        for p in section['block_states']['palette']:
                            name=block_name(p)
                            if name not in lookup:lookup[name]=len(names);names.append(name)
                            ids.append(lookup[name])
                        volume[sy*16-lo:sy*16-lo+16]=np.array(ids)[unpack(section['block_states'])].reshape(16,16,16)
                    seed=int.from_bytes(hashlib.sha256(f'{world}:{cx}:{cz}:coal-v1'.encode()).digest()[:8],'big')
                    targets=candidates(volume,names,seed);persection=collections.defaultdict(list)
                    for y,z,x in targets:
                        y=int(y)+lo;z=int(z);x=int(x);before=names[int(volume[y-lo,z,x])];after='deepslate_coal_ore' if before=='deepslate' else 'coal_ore'
                        if original:
                            baseY,palette,ids,staff=original;i=(y-baseY)*256+z*16+x
                            if i<0 or i>=len(ids) or i<len(staff) and staff[i] or palette[int(ids[i])]!='minecraft:'+before:continue
                        xyz=f'{cx*16+x},{y},{cz*16+z}'
                        # Already migrated cells never get added a second time.
                        row=f'{world}|{xyz}|minecraft:{before}|minecraft:{after}'
                        rows.add(row);changes.append(row);counts[world]+=1;persection[y//16].append((y%16,z,x,after))
                    for sy,points in persection.items():
                        state=sections[sy]['block_states'];pal=list(state['palette']);ns=[block_name(p) for p in pal];arr=unpack(state).copy()
                        for y,z,x,after in points:
                            if after not in ns:ns.append(after);pal.append(palette_tag(after))
                            arr[y*256+z*16+x]=ns.index(after)
                        replacement=T.Compound({'palette':T.List[T.Compound](pal),'data':packed(arr,max(4,(len(pal)-1).bit_length()))})
                        assert np.array_equal(unpack(replacement),arr);sections[sy]['block_states']=replacement
                    if persection:
                        touched.add((cx//32,cz//32));chunk['isLightOn']=T.Byte(0)
                        chunk['Status']=T.String('minecraft:features');chunk.pop('starlight.light_version',None)
                        for section in chunk['sections']:
                            for key in ['BlockLight','SkyLight','starlight.skylight_state','starlight.blocklight_state']:section.pop(key,None)
                        native.regions[cx//32,cz//32][cx%32+cz%32*32]=encode(chunk)
            for rx,rz in touched:
                path=native.world/'region'/f'r.{rx}.{rz}.mca';dest=output/'staged'/path.relative_to(runtime)
                before=path.read_bytes();write_records(dest,native.regions[rx,rz]);after=bytearray(dest.read_bytes())
                after[4096:8192]=before[4096:8192];dest.write_bytes(after)
                readback=records(dest)
                assert readback==native.regions[rx,rz], 'Region roundtrip mismatch'
                staged[path]=(before,bytes(after))
            print(world,counts[world],flush=True)
        if not all(counts[w] for w in WORLDS):raise ValueError('No coal generated for one or more resource planets')
        new_overlay=json.dumps({'schema-version':1,'migration-id':'planet-coal-v1','changes':sorted(rows)},indent=2).encode()+b'\n'
        if changes:staged[overlay]=(old_overlay,new_overlay)
        report={'counts':dict(counts),'changes':changes,'files':[{'path':str(p.relative_to(runtime)),'before':digest(a) if a else None,'after':digest(b)} for p,(a,b) in staged.items()]}
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        if apply:
            for p,(before,after) in staged.items():
                if (p.read_bytes() if p.exists() else None)!=before:raise ValueError('Concurrent modification: '+str(p))
                if before is not None:
                    backup=output/'backup'/p.relative_to(runtime);backup.parent.mkdir(parents=True,exist_ok=True)
                    if backup.exists():raise ValueError('Backup already exists: '+str(backup))
                    backup.write_bytes(before)
            for p,(before,after) in staged.items():
                p.parent.mkdir(parents=True,exist_ok=True);tmp=p.with_name(p.name+'.coal-tmp');tmp.write_bytes(after);os.replace(tmp,p)
        return report
    finally:
        for lock in locks:lock.close()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    result=migrate(a.runtime.resolve(),a.output.resolve(),a.apply);print(json.dumps({'counts':result['counts'],'files':len(result['files']),'applied':a.apply}))
