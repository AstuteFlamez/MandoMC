"""Add the separate review dimension and companion plugin with a clean, reversible restart."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import shutil
import subprocess
import time

p=argparse.ArgumentParser()
p.add_argument('--server-root',type=Path,required=True)
p.add_argument('--staged',type=Path,required=True)
p.add_argument('--plugin',type=Path,required=True)
p.add_argument('--records',type=Path,required=True)
a=p.parse_args()
runtime=a.server_root/'server/runtime'
control=a.server_root/'scripts/server-control.py'
config=runtime/'plugins/MandoMC/worlds/worlds.yml'
dimension=runtime/'dev_world/dimensions/minecraft/ilumreview'
jar=runtime/'plugins/IlumReview.jar'
assert not dimension.exists() and not jar.exists(), 'Refusing to overwrite an existing Ilum review'
assert 'ilumreview:' not in config.read_text()
assert (a.staged/'dimension/data/paper/level_overrides.dat').is_file()
assert len(list((a.staged/'dimension/region').glob('*.mca')))>=4
assert a.plugin.is_file()
a.records.mkdir(parents=True,exist_ok=True)
backup=a.records/('before-install-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
original_jars={p.name:sha(p) for p in (runtime/'plugins').glob('*.jar')}
pack_properties=[l for l in (runtime/'server.properties').read_text().splitlines()
                 if l.startswith(('resource-pack=','resource-pack-sha1=','resource-pack-id='))]
subprocess.run([str(control),'stop'],check=True)
assert 'stopped' in subprocess.check_output([str(control),'status'],text=True).lower()
shutil.copy2(config,backup/'worlds.yml')
shutil.copy2(runtime/'server.properties',backup/'server.properties')
shutil.copy2(runtime/'plugins/MandoMC.jar',backup/'MandoMC.jar')
shutil.copytree(a.staged/'dimension',dimension)
original=config.read_text()
generation=json.loads((a.staged/'generation.json').read_text())
x,y,z,yaw=generation['spawn']
config.write_text(original+f'''  ilumreview:
    key: minecraft:ilumreview
    aliases: [IlumReview, ilum]
    weather: false
    pvp: false
    difficulty: peaceful
    spawn:
      x: {x}
      y: {y}
      z: {z}
      yaw: {yaw}
      pitch: 0.0
''')
shutil.copy2(a.plugin,jar)
record=dict(backup=str(backup),dimension='minecraft:ilumreview',original_plugin_jars=original_jars,
            plugin_sha256=sha(jar),pack_properties=pack_properties,
            map_generation=json.loads((a.staged/'generation.json').read_text()))
(a.records/'deployment.json').write_text(json.dumps(record,indent=2))
print('Installed independent Ilum review world and plugin; starting Paper.',flush=True)
subprocess.run([str(control),'start'],check=True)
verification=runtime/'plugins/IlumReview/verification.txt'
deadline=time.monotonic()+55
while time.monotonic()<deadline:
    if verification.exists():break
    time.sleep(.5)
assert verification.exists(), 'Inspect Paper log: Ilum verification did not finish'
assert config.read_text().startswith((backup/'worlds.yml').read_text())
assert all(sha(runtime/'plugins'/name)==digest for name,digest in original_jars.items())
assert pack_properties==[l for l in (runtime/'server.properties').read_text().splitlines()
                        if l.startswith(('resource-pack=','resource-pack-sha1=','resource-pack-id='))]
record['verification']=verification.read_text()
record['original_jars_unchanged']=True
record['existing_world_entries_preserved']=True
record['pack_unchanged']=True
(a.records/'deployment.json').write_text(json.dumps(record,indent=2))
print(verification.read_text(),flush=True)
