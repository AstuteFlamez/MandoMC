#!/usr/bin/env python3
"""Add isolated decorative rendering classes/config without replacing existing gameplay owners."""
from pathlib import Path
import argparse,zipfile,json,hashlib,subprocess,tempfile
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--lifecycle',action='store_true');p.add_argument('--java-home',type=Path);p.add_argument('--libraries',type=Path);a=p.parse_args()
with zipfile.ZipFile(a.baseline) as old,zipfile.ZipFile(a.built) as built:
 data={n:old.read(n) for n in old.namelist()};new=data.copy()
 for name in built.namelist():
  if name.startswith('net/mandomc/gameplay/decor/') or name.startswith('net/mandomc/integrations/models/DecorModel') or name=='decor/placements.yml':new[name]=built.read(name)
 hub='net/mandomc/commands/CommandBindings.class'
 if a.lifecycle:
  assert a.java_home and a.libraries and b'net/mandomc/gameplay/decor/DecorFeature' not in data[hub],'Existing decor lifecycle; review instead of duplicating'
  with tempfile.TemporaryDirectory(prefix='decor-lifecycle-',dir=a.output.parent) as directory:
   temp=Path(directory);(temp/'input.class').write_bytes(data[hub])
   subprocess.run([str(a.java_home/'bin/javac'),'-d',str(temp),str(Path(__file__).resolve().parent/'tools/DecorLifecycleOverlay.java')],check=True)
   classpath=':'.join(map(str,[temp,a.baseline,a.built,*a.libraries.rglob('*.jar')]))
   subprocess.run([str(a.java_home/'bin/java'),'-cp',classpath,'DecorLifecycleOverlay',str(temp/'input.class'),str(temp/'output.class')],check=True)
   new[hub]=(temp/'output.class').read_bytes()
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as dst:
  for name,value in sorted(new.items()):dst.writestr(name,value)
 changed=[n for n in new if data.get(n)!=new[n]]
 owned=lambda n:n.startswith('net/mandomc/gameplay/decor/') or n.startswith('net/mandomc/integrations/models/DecorModel') or n=='decor/placements.yml'
 assert all(new[n]==value for n,value in data.items() if not owned(n) and not(a.lifecycle and n==hub))
 report={'baseline-sha256':hashlib.sha256(a.baseline.read_bytes()).hexdigest(),'candidate-sha256':hashlib.sha256(a.output.read_bytes()).hexdigest(),'changed-entries':changed,'existing-entry-exceptions':[hub] if a.lifecycle else [],'all-unrelated-entries-identical':True,'lifecycle-installed':a.lifecycle}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
