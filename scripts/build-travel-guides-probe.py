#!/usr/bin/env python3
from pathlib import Path
import argparse,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--server-root',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--actor-source',type=Path);p.add_argument('--output',type=Path,required=True);p.add_argument('--probe',choices=['TravelGuideProbe','SpawnNpcSmoke'],default='TravelGuideProbe');a=p.parse_args()
root=Path(__file__).resolve().parents[1];classes=a.output/'classes';classes.mkdir(parents=True,exist_ok=True);jdk=a.server_root/'.local/jdks/jdk-25.0.5+8/Contents/Home'
libs=[a.candidate,a.server_root/'server/runtime/plugins/ModelEngine-4.1.1.jar',*list((a.server_root/'server/runtime/libraries').rglob('*.jar'))]
sources=[root/f'scripts/integration/{a.probe}.java']
if a.probe=='TravelGuideProbe':
 if not a.actor_source:p.error('--actor-source required for TravelGuideProbe')
 sources.append(a.actor_source)
subprocess.run([str(jdk/'bin/javac'),'--release','25','-cp',':'.join(map(str,libs)),'-d',str(classes),*map(str,sources)],check=True)
package='net.mandomc.gameplay.decor' if a.probe=='TravelGuideProbe' else 'net.mandomc.verification'
(classes/'plugin.yml').write_text(f"name: {a.probe}\nversion: 1.0\nmain: {package}.{a.probe}\napi-version: '26.1.2'\ndepend: [MandoMC, ModelEngine]\n")
with zipfile.ZipFile(a.output/f'{a.probe}.jar','w',zipfile.ZIP_DEFLATED) as out:
 for f in classes.rglob('*'):
  if f.is_file():out.write(f,f.relative_to(classes))
print(a.output/f'{a.probe}.jar')
