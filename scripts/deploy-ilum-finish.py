#!/usr/bin/env python3
"""Install only a verified completion fix over the exact current combined server build."""
from pathlib import Path
import argparse, datetime, hashlib, json, shutil, subprocess, time
import yaml

p = argparse.ArgumentParser()
p.add_argument('--server-root', type=Path, required=True)
p.add_argument('--records', type=Path, required=True)
a = p.parse_args()
source = Path(__file__).resolve().parents[1]
rt = a.server_root / 'server/runtime'
ctl = a.server_root / 'scripts/server-control.py'
r = a.records
candidate = r / 'MandoMC-finish-candidate.jar'
report = json.loads(candidate.with_suffix('.json').read_text())
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
assert 'PASSED: 199 ' in (r / 'preflight/plugins/IlumCratesProbe/PASSED.txt').read_text()
assert sha(r / 'preflight/plugins/MandoMC.jar') == sha(candidate)
assert sha(rt / 'plugins/MandoMC.jar') == report['baseline-sha256']
probe = rt / 'plugins/IlumCratesProbe'
assert not probe.exists() and not (rt / 'plugins/IlumCratesProbe.jar').exists()
subprocess.run([str(ctl), 'stop'], check=True)
assert sha(rt / 'plugins/MandoMC.jar') == report['baseline-sha256']
backup = r / ('before-finish-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir()
shutil.copy2(rt / 'plugins/MandoMC.jar', backup / 'MandoMC.jar')
config = rt / 'plugins/MandoMC/parkour/ilum.yml'
shutil.copy2(config, backup / 'ilum.yml')
shutil.copytree(rt / 'dev_world/players', backup / 'players')
data = yaml.safe_load(config.read_text())
defaults = yaml.safe_load((source / 'server/config/common/mandomc/parkour/ilum.yml').read_text())
for key in ['finished', 'npc-interrupted']:
    data['messages'][key] = defaults['messages'][key]
config.write_text(yaml.safe_dump(data, sort_keys=False, allow_unicode=True))
shutil.copy2(candidate, rt / 'plugins/MandoMC.jar')
shutil.copy2(r / 'finish-probe/IlumCratesProbe.jar', rt / 'plugins/IlumCratesProbe.jar')
probe.mkdir()
record = {'backup': str(backup), 'overlay': report, 'isolated-native-assertions': 199}
(r / 'finish-deployment.json').write_text(json.dumps(record, indent=2))
subprocess.run([str(ctl), 'start'], check=True)
deadline = time.monotonic() + 55
while time.monotonic() < deadline:
    if (probe / 'PASSED.txt').exists(): break
    if (probe / 'FAILED.txt').exists(): raise RuntimeError((probe / 'FAILED.txt').read_text())
    time.sleep(.5)
assert (probe / 'PASSED.txt').exists(), 'Live native smoke check did not finish'
record['live-smoke'] = (probe / 'PASSED.txt').read_text()
print(record['live-smoke'], flush=True)
subprocess.run([str(ctl), 'stop'], check=True)
shutil.move(str(rt / 'plugins/IlumCratesProbe.jar'), str(backup / 'IlumCratesProbe.jar'))
shutil.move(str(probe), str(backup / 'IlumCratesProbe'))
shutil.copy2(rt / 'logs/latest.log', r / 'finish-live-smoke.log')
subprocess.run([str(ctl), 'start'], check=True)
record['final-sha256'] = sha(rt / 'plugins/MandoMC.jar')
assert record['final-sha256'] == report['candidate-sha256']
record['temporary-probe-removed'] = True
(r / 'finish-deployment.json').write_text(json.dumps(record, indent=2))
print('Completion fix deployed; normal server ready; temporary probe removed.', flush=True)
