#!/usr/bin/env python3
"""Build only reload UI additions against a pinned actual deployed release."""
import argparse, hashlib, json, subprocess, zipfile, shutil, struct
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True);p.add_argument('--expected-sha256',required=True)
p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True)
p.add_argument('--output',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];w=r/'.local/reload-release';w.mkdir(parents=True,exist_ok=True)
hash=lambda b:hashlib.sha256(b).hexdigest()
baseline_bytes=a.baseline.read_bytes()
assert hash(baseline_bytes)==a.expected_sha256,'Baseline changed'
snapshot=w/'baselines'/f'{a.expected_sha256}.jar';snapshot.parent.mkdir(exist_ok=True);snapshot.write_bytes(baseline_bytes)
classes=w/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(exist_ok=True)
shutil.rmtree(w/'after',ignore_errors=True)
cp=str(snapshot)+':'+a.classpath_file.read_text().strip()
sources=[r/'plugin/src/main/java/net/mandomc/integrations/weapons/ReloadIndicator.java',r/'plugin/src/main/java/net/mandomc/integrations/weapons/NativeReloadSweep.java',r/'scripts/integration/ReloadOverlay.java']
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
with zipfile.ZipFile(snapshot) as z:before={n:z.read(n) for n in z.namelist()}
after=dict(before);changed=[]
for name,data in before.items():
 if name.startswith('net/mandomc/') and name.endswith('.class') and name=='net/mandomc/bootstrap/MandoPlugin.class':
  src=w/'before'/name;dst=w/'after'/name;src.parent.mkdir(parents=True,exist_ok=True);src.write_bytes(data)
  result=subprocess.run([str(a.java_home/'bin/java'),'-cp',str(classes)+':'+cp,'ReloadOverlay',str(src),str(dst)],check=True,capture_output=True,text=True)
  if dst.exists():after[name]=dst.read_bytes();changed.append(name);print(result.stdout.strip())
for group in ['net/mandomc/integrations/weapons']:
 for f in (classes/group).glob('*.class'):after[f.relative_to(classes).as_posix()]=f.read_bytes()

# Inspect actual classfile member bytes; all pre-existing constant-pool indexes must stay stable.
def members(data):
 offset=8
 def u2():
  nonlocal offset
  value=struct.unpack_from('>H',data,offset)[0];offset+=2;return value
 count=u2();pool_start=offset;utf={};index=1
 while index<count:
  tag=data[offset];offset+=1
  if tag==1:
   size=u2();utf[index]=data[offset:offset+size].decode('utf-8','replace');offset+=size
  elif tag in (3,4,9,10,11,12,17,18):offset+=4
  elif tag in (5,6):offset+=8;index+=1
  elif tag in (7,8,16,19,20):offset+=2
  elif tag==15:offset+=3
  else:raise AssertionError(f'Unknown classfile constant tag {tag}')
  index+=1
 pool=data[pool_start:offset];header_start=offset;offset+=6
 interfaces=u2();offset+=interfaces*2;header=data[header_start:offset]
 def group():
  nonlocal offset
  result={}
  for _ in range(u2()):
   start=offset;offset+=2;name=u2();descriptor=u2()
   for _ in range(u2()):
    offset+=2;size=struct.unpack_from('>I',data,offset)[0];offset+=4+size
   result[(utf[name],utf[descriptor])]=data[start:offset]
  return result
 fields=group();methods=group()
 return pool,header,fields,methods,data[offset:]
bootstrap='net/mandomc/bootstrap/MandoPlugin.class'
old=members(before[bootstrap]);new=members(after[bootstrap])
assert new[0].startswith(old[0]),'Existing constant pool changed'
assert old[1]==new[1] and old[2]==new[2] and old[4]==new[4],'Unowned bootstrap header/fields/attributes changed'
assert old[3].keys()==new[3].keys(),'Existing bootstrap method set changed'
unowned=[key for key in old[3] if key[0] not in ('onEnable','onDisable')]
assert all(old[3][key]==new[3][key] for key in unowned),'Unowned bootstrap method bytes changed'

assert 'net/mandomc/bootstrap/MandoPlugin.class' in changed
assert all('Redstone' not in n for n in changed)
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(after.items()):z.writestr(name,data)
for name in changed:
 cls=name[:-6].replace('/','.')
 def api(jar):
  out=subprocess.check_output([str(a.java_home/'bin/javap'),'-classpath',str(jar),cls],text=True)
  return {line.strip() for line in out.splitlines() if line.strip().startswith('public ') and line.strip().endswith(';')}
 assert api(snapshot)==api(a.output),'Public API changed: '+name
assert hash(a.baseline.read_bytes())==a.expected_sha256,'Baseline changed during build; discard and rebase'
report={'allOriginalPublicApisRetained':True,'unownedBootstrapMethodsByteIdentical':len(unowned),'originalLifecycleCallsRetained':True,'baselineSha256':a.expected_sha256,'candidateSha256':hash(a.output.read_bytes()),'changedExistingEntries':changed,'newEntries':sorted(set(after)-set(before)),'allOtherEntriesByteIdentical':all(before[n]==after[n] for n in before if n not in changed),'packUnchanged':True}
a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
