#!/usr/bin/env python3
"""Replace only frozen wilderness preparation classes; preserve every unrelated live JAR entry."""
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
 for name,expected in {'net/mandomc/gameplay/clans/LandRestoration.class': '5deecbf5b6ae106f788e0a3174190efb61cf771310d43072c194b70dd4b55786', 'net/mandomc/gameplay/clans/ClanCommands.class': '979e86562d91722bfb41c4dc394ef6f56f632d60450d04b0be03179cd202d076', 'net/mandomc/gameplay/clans/ClanFeature.class': 'a4ecb8ed09053ba194a9d165439f13272823e9ef58fc534832e5adec9097445a', 'net/mandomc/gameplay/clans/ClanProtection.class': '4cffd76e33dbc2ae1861abce98d95338e8f64c258a2eee30b9f73614b27159b2'}.items():
  assert sha(before[name])==expected,"Clan implementation changed; merge before overlay: "+name+" ("+sha(before[name])+")"
 for name in built.namelist():
  if name in ['net/mandomc/gameplay/clans/LandRestoration.class', 'net/mandomc/gameplay/clans/ClanCommands.class', 'net/mandomc/gameplay/clans/ClanFeature.class', 'net/mandomc/gameplay/clans/ClanProtection.class', 'net/mandomc/gameplay/clans/FrozenLandPreparation.class', 'language/clans.yml']:
   after[name]=built.read(name)
 assert set(before)<=set(after)
 changed=sorted(n for n in after if before.get(n)!=after[n])
 assert all(n in ['net/mandomc/gameplay/clans/LandRestoration.class', 'net/mandomc/gameplay/clans/ClanCommands.class', 'net/mandomc/gameplay/clans/ClanFeature.class', 'net/mandomc/gameplay/clans/ClanProtection.class', 'net/mandomc/gameplay/clans/FrozenLandPreparation.class', 'language/clans.yml'] for n in changed)
 assert before['language/messages.yml']==after['language/messages.yml'],'First-join text changed'
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as out:
  for name,data in sorted(after.items()):out.writestr(name,data)
 report={'baseline-sha256':a.expected_sha256,'candidate-sha256':sha(a.output.read_bytes()),'changed-entries':changed,
         'first-join-language-preserved':True,'all-unrelated-entries-identical':all(after[n]==before[n] for n in before if n not in changed)}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
