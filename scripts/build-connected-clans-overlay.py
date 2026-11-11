#!/usr/bin/env python3
"""Apply only connected claims, four-member defaults and matching handbook; preserve every unrelated live JAR entry."""
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
 for name,expected in {'net/mandomc/gameplay/clans/ClanState.class': '593328fe6fbbb09be6b8ecf0022f55fad1b4fb8642786b04966faf18fd520295', 'net/mandomc/gameplay/clans/ClanCommands.class': '9d1195fbd1d200b943556693d181187045484afed173771fce3b86d87913f5eb', 'net/mandomc/gameplay/clans/ClanFeature.class': 'ca44d6a2eb4899498deef9daaa619ba883ccac8adb6d9308b6aaace9018c2d5b'}.items():
  assert sha(before[name])==expected,"Clan implementation changed; merge before overlay: "+name
 for name in ['net/mandomc/gameplay/clans/ClanState.class', 'net/mandomc/gameplay/clans/ClanCommands.class', 'net/mandomc/gameplay/clans/ClanFeature.class', 'clans/settings.yml', 'language/clans.yml', 'language/handbook.yml']:after[name]=built.read(name)
 assert set(before)<=set(after)
 changed=sorted(n for n in after if before.get(n)!=after[n])
 assert all(n in ['net/mandomc/gameplay/clans/ClanState.class', 'net/mandomc/gameplay/clans/ClanCommands.class', 'net/mandomc/gameplay/clans/ClanFeature.class', 'clans/settings.yml', 'language/clans.yml', 'language/handbook.yml'] for n in changed)
 assert before['language/messages.yml']==after['language/messages.yml'],'First-join text changed'
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as out:
  for name,data in sorted(after.items()):out.writestr(name,data)
 report={'baseline-sha256':a.expected_sha256,'candidate-sha256':sha(a.output.read_bytes()),'changed-entries':changed,
         'first-join-language-preserved':True,'all-unrelated-entries-identical':all(after[n]==before[n] for n in before if n not in changed)}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
