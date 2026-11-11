#!/usr/bin/env python3
"""Overlay only the reviewed HUD class/default config onto an exact current release."""
import argparse,hashlib,json,zipfile,copy
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--baseline',required=True);p.add_argument('--expected-sha256',required=True);p.add_argument('--built',required=True);p.add_argument('--output',required=True);a=p.parse_args()
def sha(b):return hashlib.sha256(b).hexdigest()
base=Path(a.baseline);assert sha(base.read_bytes())==a.expected_sha256,'Live baseline changed'
owned=['net/mandomc/gameplay/hud/PlayerHud.class','net/mandomc/gameplay/territory/TerritoryService.class','net/mandomc/gameplay/territory/TerritoryIdentity.class','net/mandomc/gameplay/territory/TerritoryIdentity$Relation.class','net/mandomc/gameplay/territory/TerritoryIdentity$1.class','interface/hud.yml','language/territory.yml']
with zipfile.ZipFile(base) as old,zipfile.ZipFile(a.built) as new:
 assert sha(old.read('net/mandomc/gameplay/territory/TerritoryService.class'))=='7135eb9cebb4693678b53d524060ae88d94b11e820b17ff2c9aee8957651ff0c','Territory owner changed; merge first'
 assert sha(old.read(owned[0]))=='0d0709980c9b98d9ad94d8480e1207b038bb2167f60a38bd24d64bb8d4535d76','HUD owner changed; merge first'
 with zipfile.ZipFile(a.output,'w') as out:
  for info in old.infolist():out.writestr(copy.copy(info),new.read(info.filename) if info.filename in owned else old.read(info.filename))
  for name in owned:
   if name not in old.namelist():out.writestr(copy.copy(new.getinfo(name)),new.read(name))
 with zipfile.ZipFile(a.output) as out:
  assert set(out.namelist())==set(old.namelist())|set(owned)
  assert all(out.read(n)==old.read(n) for n in old.namelist() if n not in owned)
report={'baseline-sha256':a.expected_sha256,'candidate-sha256':sha(Path(a.output).read_bytes()),'changed-entries':owned,'all-unrelated-entries-identical':True}
Path(a.output).with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
