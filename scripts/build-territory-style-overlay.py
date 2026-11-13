#!/usr/bin/env python3
"""Polish only territory value colors and its chosen sidebar icon; preserve every unrelated live JAR entry."""
from pathlib import Path
import argparse,hashlib,json,zipfile

def sha(data):return hashlib.sha256(data).hexdigest()
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--expected-sha256',required=True)
p.add_argument('--built',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
assert sha(a.baseline.read_bytes())==a.expected_sha256,'Live baseline changed'
with zipfile.ZipFile(a.baseline) as old,zipfile.ZipFile(a.built) as built:
 before={n:old.read(n) for n in old.namelist()};after=before.copy()
 for name,expected in {'net/mandomc/gameplay/hud/PlayerHud.class': '6468544d1e630a7c3035bde96fdb2ea4f0b3bfa706a2b4f1e2fd0bbcbc39e025', 'net/mandomc/gameplay/territory/TerritoryService.class': '7367f249edbbe6e67e9513a44bbdc8c18b6bf6d6cf572a13cd1ec9fe1eead792'}.items():
  assert sha(before[name])==expected,"Territory/HUD implementation changed; merge before overlay: "+name
 for name in ['net/mandomc/gameplay/hud/PlayerHud.class', 'net/mandomc/gameplay/territory/TerritoryService.class', 'interface/hud.yml']:after[name]=built.read(name)
 assert set(before)<=set(after)
 changed=sorted(n for n in after if before.get(n)!=after[n])
 assert all(n in ['net/mandomc/gameplay/hud/PlayerHud.class', 'net/mandomc/gameplay/territory/TerritoryService.class', 'interface/hud.yml'] for n in changed)
 assert before['language/messages.yml']==after['language/messages.yml'],'First-join text changed'
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as out:
  for name,data in sorted(after.items()):out.writestr(name,data)
 report={'baseline-sha256':a.expected_sha256,'candidate-sha256':sha(a.output.read_bytes()),'changed-entries':changed,
         'first-join-language-preserved':True,'all-unrelated-entries-identical':all(after[n]==before[n] for n in before if n not in changed)}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
