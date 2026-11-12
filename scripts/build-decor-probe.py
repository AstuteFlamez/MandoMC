#!/usr/bin/env python3
from pathlib import Path
import argparse,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--server-root',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--actor-source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];classes=a.output/'classes';classes.mkdir(parents=True,exist_ok=True);jdk=a.server_root/'.local/jdks/jdk-25.0.5+8/Contents/Home'
libs=[a.candidate,*list((a.server_root/'server/runtime/libraries').rglob('*.jar')),a.server_root/'server/runtime/plugins/ModelEngine-4.1.1.jar']
subprocess.run([str(jdk/'bin/javac'),'--release','25','-cp',':'.join(map(str,libs)),'-d',str(classes),str(root/'scripts/integration/SpawnDecorProbe.java'),str(a.actor_source)],check=True)
(classes/'plugin.yml').write_text("name: SpawnDecorProbe\nversion: 1.0\nmain: net.mandomc.verification.SpawnDecorProbe\napi-version: '26.1.2'\ndepend: [MandoMC, ModelEngine]\n")
with zipfile.ZipFile(a.output/'SpawnDecorProbe.jar','w',zipfile.ZIP_DEFLATED) as out:
 for f in classes.rglob('*'):
  if f.is_file():out.write(f,f.relative_to(classes))
print(a.output/'SpawnDecorProbe.jar')
