#!/usr/bin/env python3
"""Overlay only the chat presentation owner and audited chat send boundaries."""
from pathlib import Path
import argparse,hashlib,json,zipfile,subprocess,tempfile
import yaml
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--libraries',type=Path,required=True);a=p.parse_args()
with zipfile.ZipFile(a.baseline) as old,zipfile.ZipFile(a.built) as built:
 data={n:old.read(n) for n in old.namelist()};new=data.copy()
 exact={'net/mandomc/commands/CommandMessages.class','net/mandomc/gameplay/credits/CreditService.class','language/chat-style.yml','net/mandomc/gameplay/ranks/RankService.class'}
 for name in built.namelist():
  if name in exact or name.startswith(('net/mandomc/gameplay/messaging/','net/mandomc/integrations/messaging/')):new[name]=built.read(name)
 hub='net/mandomc/commands/CommandBindings.class'
 assert b'net/mandomc/gameplay/messaging/MessagingFeature' not in data[hub],'Existing chat lifecycle; review before duplicating'
 with tempfile.TemporaryDirectory(prefix='chat-lifecycle-',dir=a.output.parent) as directory:
  temp=Path(directory);(temp/'input.class').write_bytes(data[hub])
  subprocess.run([str(a.java_home/'bin/javac'),'-d',str(temp),str(Path(__file__).resolve().parent/'tools/ChatLifecycleOverlay.java')],check=True)
  classpath=':'.join(map(str,[temp,a.baseline,a.built,*a.libraries.rglob('*.jar')]))
  subprocess.run([str(a.java_home/'bin/java'),'-cp',classpath,'ChatLifecycleOverlay',str(temp/'input.class'),str(temp/'output.class')],check=True)
  new[hub]=(temp/'output.class').read_bytes()
 descriptor=yaml.safe_load(data['plugin.yml']);deps=descriptor.setdefault('softdepend',[])
 if 'packetevents' not in deps:deps.append('packetevents')
 new['plugin.yml']=yaml.safe_dump(descriptor,sort_keys=False).encode()
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as dst:
  for name,value in sorted(new.items()):dst.writestr(name,value)
 changed=[n for n in new if data.get(n)!=new[n]]
 assert new['net/mandomc/bootstrap/MandoPlugin.class']==data['net/mandomc/bootstrap/MandoPlugin.class']
 allowed=exact|{hub,'plugin.yml'}
 assert all(new[n]==value for n,value in data.items() if n not in allowed and not n.startswith(('net/mandomc/gameplay/messaging/','net/mandomc/integrations/messaging/')))
 report={'baseline-sha256':hashlib.sha256(a.baseline.read_bytes()).hexdigest(),'candidate-sha256':hashlib.sha256(a.output.read_bytes()).hexdigest(),'changed-entries':changed,'unrelated-entries-identical':True}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
