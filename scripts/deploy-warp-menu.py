#!/usr/bin/env python3
"""Hide only crate menu entries over a coordinated, exact live baseline."""
from pathlib import Path
import argparse, datetime, hashlib, json, shutil, subprocess, time, zipfile
import yaml
p=argparse.ArgumentParser()
p.add_argument('--server-root',type=Path,required=True)
p.add_argument('--records',type=Path,required=True)
a=p.parse_args();r=a.records;rt=a.server_root/'server/runtime';ctl=a.server_root/'scripts/server-control.py'
candidate=r/'MandoMC-warp-menu-live.jar';report=json.loads(candidate.with_suffix('.json').read_text())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
name='net/mandomc/gameplay/travel/WarpService.class'
assert report['changed-entries']==[name]
assert 'PASSED: 212 ' in (r/'preflight/plugins/IlumCratesProbe/PASSED.txt').read_text()
with zipfile.ZipFile(candidate) as new,zipfile.ZipFile(r/'preflight/plugins/MandoMC.jar') as tested:
    assert new.read(name)==tested.read(name),'Warp owner differs from native-tested class'
assert sha(rt/'plugins/MandoMC.jar')==report['baseline-sha256']
probe=rt/'plugins/IlumCratesProbe';assert not probe.exists() and not (rt/'plugins/IlumCratesProbe.jar').exists()
subprocess.run([str(ctl),'stop'],check=True)
assert sha(rt/'plugins/MandoMC.jar')==report['baseline-sha256']
backup=r/('before-warp-menu-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));backup.mkdir()
shutil.copy2(rt/'plugins/MandoMC.jar',backup/'MandoMC.jar')
config=rt/'plugins/MandoMC/travel/warps.yml';shutil.copy2(config,backup/'warps.yml')
data=yaml.safe_load(config.read_text());data['warps']['crates']['show-in-menu']=False
config.write_text(yaml.safe_dump(data,sort_keys=False,allow_unicode=True))
shutil.copy2(candidate,rt/'plugins/MandoMC.jar')
shutil.copy2(r/'warp-menu-probe/IlumCratesProbe.jar',rt/'plugins/IlumCratesProbe.jar');probe.mkdir()
record={'backup':str(backup),'overlay':report,'isolated-native-assertions':212}
receipt=r/'warp-menu-deployment.json';receipt.write_text(json.dumps(record,indent=2))
subprocess.run([str(ctl),'start'],check=True)
deadline=time.monotonic()+55
while time.monotonic()<deadline:
    if (probe/'PASSED.txt').exists():break
    if (probe/'FAILED.txt').exists():raise RuntimeError((probe/'FAILED.txt').read_text())
    time.sleep(.5)
assert (probe/'PASSED.txt').exists(),'Live warp menu verification timed out'
record['live-smoke']=(probe/'PASSED.txt').read_text();print(record['live-smoke'],flush=True)
subprocess.run([str(ctl),'stop'],check=True)
shutil.move(str(rt/'plugins/IlumCratesProbe.jar'),str(backup/'IlumCratesProbe.jar'))
shutil.move(str(probe),str(backup/'IlumCratesProbe'))
shutil.copy2(rt/'logs/latest.log',r/'warp-menu-live-smoke.log')
subprocess.run([str(ctl),'start'],check=True)
record['final-sha256']=sha(rt/'plugins/MandoMC.jar');assert record['final-sha256']==report['candidate-sha256']
record['temporary-probe-removed']=True;receipt.write_text(json.dumps(record,indent=2))
print('Crates hidden from warp menu; direct aliases preserved; normal server ready.',flush=True)
