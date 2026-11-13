#!/usr/bin/env python3
"""Install the verified Ilum slice only during its explicitly coordinated server window."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,subprocess,time
import yaml

p=argparse.ArgumentParser()
p.add_argument('--server-root',type=Path,required=True)
p.add_argument('--records',type=Path,required=True)
p.add_argument('--window-owner',choices=['ilum-crates'],required=True)
a=p.parse_args()
source=Path(__file__).resolve().parents[1];local=a.records;rt=a.server_root/'server/runtime';ctl=a.server_root/'scripts/server-control.py'
plugin=rt/'plugins/MandoMC';dimension=rt/'dev_world/dimensions/minecraft/ilum'
candidate=local/'MandoMC-candidate.jar';overlay=json.loads(candidate.with_suffix('.json').read_text())
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
assert 'PASSED: 198 ' in (local/'preflight/plugins/IlumCratesProbe/PASSED.txt').read_text()
assert sha(local/'preflight/plugins/MandoMC.jar')==sha(candidate),'Candidate differs from full native test'
assert sha(rt/'plugins/MandoMC.jar')==overlay['baseline-sha256'],'Live JAR changed; rebase reviewed overlay before stopping'
assert not dimension.exists(),'Refusing to overwrite an existing original Ilum world'
assert json.loads((local/'publication/anonymous-download-verified.json').read_text())['anonymousVerified']
worlds=yaml.safe_load((plugin/'worlds/worlds.yml').read_text())
assert 'ilum' not in worlds['worlds']
for name,digest in json.loads((local/'import-receipt.json').read_text())['regions'].items():
    assert sha(local/'staged/region'/name)==digest,'Original terrain stage changed'
if 'ilumreview' in worlds['worlds']:
    spawn=worlds['worlds']['spawnworld']['spawn']
    command=('execute in minecraft:ilumreview as @a[distance=0..] in minecraft:spawnworld run minecraft:tp @s '
             f"{spawn['x']} {spawn['y']} {spawn['z']} {spawn['yaw']} {spawn['pitch']}")
    subprocess.run([str(ctl),'command',command],check=True)
subprocess.run([str(ctl),'stop'],check=True)
assert 'stopped' in subprocess.check_output([str(ctl),'status'],text=True).lower()
assert sha(rt/'plugins/MandoMC.jar')==overlay['baseline-sha256'],'Live JAR changed during window; do not overwrite'
backup=local/('before-live-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));backup.mkdir()
shutil.copytree(plugin,backup/'MandoMC-data')
shutil.copy2(rt/'plugins/MandoMC.jar',backup/'MandoMC.jar')
shutil.copy2(rt/'server.properties',backup/'server.properties')
shutil.copy2(rt/'resource-packs/current.json',backup/'pack-manifest.json')
for name in ['playerdata','players']:
    path=rt/'dev_world'/name
    if path.exists():shutil.copytree(path,backup/name)
spawn_region=rt/'dev_world/dimensions/minecraft/spawnworld/region/r.0.-1.mca'
shutil.copy2(spawn_region,backup/'batuu-crate-region.mca')
for name in ['IlumReview.jar','IlumReview']:
    path=rt/'plugins'/name
    if path.exists():shutil.move(str(path),str(backup/name))
retired=rt/'dev_world/dimensions/minecraft/ilumreview'
if retired.exists():shutil.move(str(retired),str(backup/'retired-ilumreview-world'))
shutil.copytree(local/'staged',dimension)
original_worlds=yaml.safe_load((plugin/'worlds/worlds.yml').read_text())
worlds=yaml.safe_load((plugin/'worlds/worlds.yml').read_text());worlds['worlds'].pop('ilumreview',None)
common=source/'server/config/common/mandomc'
worlds['worlds']['ilum']=yaml.safe_load((common/'worlds/worlds.yml').read_text())['worlds']['ilum']
for name,data in original_worlds['worlds'].items():
    if name!='ilumreview':assert worlds['worlds'][name]==data
(plugin/'worlds/worlds.yml').write_text(yaml.safe_dump(worlds,sort_keys=False))
path=plugin/'territory/maps.yml';territory=yaml.safe_load(path.read_text())
territory['safezone-worlds']['minecraft:ilum']='Ilum'
territory['mechanism-worlds']=list(dict.fromkeys(territory.get('mechanism-worlds',[])+['minecraft:ilum']))
path.write_text(yaml.safe_dump(territory,sort_keys=False))
path=plugin/'travel/warps.yml';warps=yaml.safe_load(path.read_text())
defaults=yaml.safe_load((common/'travel/warps.yml').read_text())
warps['warps']['ilum']=defaults['warps']['ilum'];warps['messages']['unknown']=defaults['messages']['unknown']
path.write_text(yaml.safe_dump(warps,sort_keys=False,allow_unicode=True))
for relative,section,entry in [('items/definitions/crates.yml','items','ilum_kyber_crate_key'),('crates/crates.yml','crates','kyber')]:
    path=plugin/relative;defaults=yaml.safe_load((common/relative).read_text())
    data=yaml.safe_load(path.read_text()) if path.exists() else {'schema-version':1,section:{}}
    assert data.get('schema-version')==1 and isinstance(data.get(section),dict)
    data[section][entry]=defaults[section][entry]
    if 'messages' in defaults:
        data.setdefault('messages',{})
        for key,value in defaults['messages'].items():data['messages'].setdefault(key,value)
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(yaml.safe_dump(data,sort_keys=False,allow_unicode=True))
path=plugin/'parkour/ilum.yml';path.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(common/'parkour/ilum.yml',path)
data=plugin/'data';data.mkdir(exist_ok=True)
shutil.copy2(local/'legacy-times.json',data/'legacy-ilum-times.json')
recipes=plugin/'items/recipes.yml'
if recipes.exists():
    recipe_data=yaml.safe_load(recipes.read_text());recipe_data.setdefault('unobtainable',{})['ilum_kyber_crate_key']='<gray>Complete the Ilum trials, once every 24 hours.'
    recipes.write_text(yaml.safe_dump(recipe_data,sort_keys=False,allow_unicode=True))
shutil.copy2(candidate,rt/'plugins/MandoMC.jar')
shutil.copy2(local/'probe/IlumCratesProbe.jar',rt/'plugins/IlumCratesProbe.jar')
probe=rt/'plugins/IlumCratesProbe'
assert not probe.exists(),'Refusing to reuse an old probe result directory'
probe.mkdir()
record={'backup':str(backup),'window-owner':a.window_owner,'overlay':overlay,'original-worlds-preserved':True,
        'generated-review-retired':True,'original-map-import':json.loads((local/'import-receipt.json').read_text())}
(local/'deployment.json').write_text(json.dumps(record,indent=2))
subprocess.run([str(ctl),'start'],check=True)
deadline=time.monotonic()+55
while time.monotonic()<deadline:
    if (probe/'PASSED.txt').exists():break
    if (probe/'FAILED.txt').exists():raise RuntimeError((probe/'FAILED.txt').read_text())
    time.sleep(.5)
assert (probe/'PASSED.txt').exists(),'Native smoke check did not finish; inspect server log'
record['live-smoke']=(probe/'PASSED.txt').read_text()
pack=json.loads((rt/'resource-packs/current.json').read_text())
with __import__('zipfile').ZipFile(rt/'resource-packs'/(pack['sha256']+'.zip')) as archive, __import__('zipfile').ZipFile(local/'publication/MandoMC.zip') as expected:
    for asset in json.loads((local/'publication/MandoMC.json').read_text())['addedEntries']:assert archive.read(asset)==expected.read(asset)
record['pack']=pack;record['deployed-sha256']=sha(rt/'plugins/MandoMC.jar')
(local/'deployment.json').write_text(json.dumps(record,indent=2))
print(record['live-smoke'],flush=True)
subprocess.run([str(ctl),'stop'],check=True)
shutil.move(str(rt/'plugins/IlumCratesProbe.jar'),str(backup/'IlumCratesProbe-smoke.jar'))
shutil.move(str(probe),str(backup/'IlumCratesProbe-smoke-data'))
shutil.copy2(rt/'logs/latest.log',local/'live-smoke-server.log')
subprocess.run([str(ctl),'start'],check=True)
record['temporary-probe-removed']=True;record['final-sha256']=sha(rt/'plugins/MandoMC.jar')
assert record['final-sha256']==overlay['candidate-sha256']
(local/'deployment.json').write_text(json.dumps(record,indent=2))
print('Original Ilum and native crates deployed; normal server ready; temporary probe removed.',flush=True)
