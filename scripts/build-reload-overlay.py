#!/usr/bin/env python3
"""Build only reload UI additions against a pinned actual deployed release."""
import argparse, hashlib, json, subprocess, zipfile, shutil
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True);p.add_argument('--expected-sha256',required=True)
p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True)
p.add_argument('--output',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];w=r/'.local/reload-release';w.mkdir(parents=True,exist_ok=True)
hash=lambda b:hashlib.sha256(b).hexdigest()
assert hash(a.baseline.read_bytes())==a.expected_sha256,'Baseline changed'
classes=w/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(exist_ok=True)
shutil.rmtree(w/'after',ignore_errors=True)
cp=str(a.baseline)+':'+a.classpath_file.read_text().strip()
sources=[r/'plugin/src/main/java/net/mandomc/integrations/weapons/ReloadIndicator.java',r/'plugin/src/main/java/net/mandomc/integrations/weapons/NativeReloadSweep.java',r/'scripts/integration/ReloadOverlay.java']
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
with zipfile.ZipFile(a.baseline) as z:before={n:z.read(n) for n in z.namelist()}
after=dict(before);changed=[]
for name,data in before.items():
 if name.startswith('net/mandomc/') and name.endswith('.class') and name=='net/mandomc/bootstrap/MandoPlugin.class':
  src=w/'before'/name;dst=w/'after'/name;src.parent.mkdir(parents=True,exist_ok=True);src.write_bytes(data)
  result=subprocess.run([str(a.java_home/'bin/java'),'-cp',str(classes)+':'+cp,'ReloadOverlay',str(src),str(dst)],check=True,capture_output=True,text=True)
  if dst.exists():after[name]=dst.read_bytes();changed.append(name);print(result.stdout.strip())
for group in ['net/mandomc/integrations/weapons']:
 for f in (classes/group).glob('*.class'):after[f.relative_to(classes).as_posix()]=f.read_bytes()

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
 assert api(a.baseline)==api(a.output),'Public API changed: '+name
report={'allOriginalPublicApisRetained':True,'baselineSha256':a.expected_sha256,'candidateSha256':hash(a.output.read_bytes()),'changedExistingEntries':changed,'newEntries':sorted(set(after)-set(before)),'allOtherEntriesByteIdentical':all(before[n]==after[n] for n in before if n not in changed),'packUnchanged':True}
a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
