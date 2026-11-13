#!/usr/bin/env python3
"""Apply persistent clan roles/menu, connected claims and four members; preserve every unrelated live JAR entry."""
from pathlib import Path
import argparse,hashlib,json,zipfile,subprocess,sys

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
 for name,expected in {'net/mandomc/gameplay/clans/ClanState.class': '593328fe6fbbb09be6b8ecf0022f55fad1b4fb8642786b04966faf18fd520295', 'net/mandomc/gameplay/clans/ClanState$Clan.class': '674134dbc322095baa5502425915e731275c9fe9eda371f4f7e7fc7e123172db', 'net/mandomc/gameplay/clans/ClanCommands.class': '9d1195fbd1d200b943556693d181187045484afed173771fce3b86d87913f5eb', 'net/mandomc/gameplay/clans/ClanFeature.class': 'ca44d6a2eb4899498deef9daaa619ba883ccac8adb6d9308b6aaace9018c2d5b', 'net/mandomc/gameplay/clans/ClanService.class': '87adf8acd3f88c9228fe52b7960e82f65cca07f81ef1c4e93075d26ec119ebc3', 'net/mandomc/gameplay/clans/ClanStorage.class': '92a54d202e80481481fa4b975b70cb4526e322cd29ec49c47629be81aca312cc', 'net/mandomc/gameplay/clans/ClanProtection.class': '607224a5c96319d01a4e36f57a8131bcbde5812dbecc00779e1ba513e351a7de'}.items():
  assert sha(before[name])==expected,"Clan implementation changed; merge before overlay: "+name
 for name in ['net/mandomc/gameplay/clans/ClanState.class', 'net/mandomc/gameplay/clans/ClanState$Clan.class', 'net/mandomc/gameplay/clans/ClanCommands.class', 'net/mandomc/gameplay/clans/ClanFeature.class', 'net/mandomc/gameplay/clans/ClanService.class', 'net/mandomc/gameplay/clans/ClanStorage.class', 'net/mandomc/gameplay/clans/ClanProtection.class', 'net/mandomc/gameplay/clans/ClanRole.class', 'net/mandomc/gameplay/clans/ClanMenu.class', 'net/mandomc/gameplay/clans/ClanMenu$View.class', 'clans/settings.yml', 'language/clans.yml', 'language/handbook.yml', 'interface/hud.yml']:after[name]=built.read(name)
 assert set(before)<=set(after)
 changed=sorted(n for n in after if before.get(n)!=after[n])
 assert all(n in ['net/mandomc/gameplay/clans/ClanState.class', 'net/mandomc/gameplay/clans/ClanState$Clan.class', 'net/mandomc/gameplay/clans/ClanCommands.class', 'net/mandomc/gameplay/clans/ClanFeature.class', 'net/mandomc/gameplay/clans/ClanService.class', 'net/mandomc/gameplay/clans/ClanStorage.class', 'net/mandomc/gameplay/clans/ClanProtection.class', 'net/mandomc/gameplay/clans/ClanRole.class', 'net/mandomc/gameplay/clans/ClanMenu.class', 'net/mandomc/gameplay/clans/ClanMenu$View.class', 'clans/settings.yml', 'language/clans.yml', 'language/handbook.yml', 'interface/hud.yml'] for n in changed)
 assert before['language/messages.yml']==after['language/messages.yml'],'First-join text changed'
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as out:
  for name,data in sorted(after.items()):out.writestr(name,data)
 api=[sys.executable,str(Path(__file__).with_name('verify-java-api.py')),'--baseline',str(a.baseline),'--candidate',str(a.output)]
 for name in ['ClanState','ClanState$Clan','ClanCommands','ClanFeature','ClanService','ClanProtection']:
  api.extend(['--class','net.mandomc.gameplay.clans.'+name])
 api.extend(['--class','net.mandomc.gameplay.territory.TerritoryService'])
 subprocess.run(api,check=True)
 report={'baseline-sha256':a.expected_sha256,'candidate-sha256':sha(a.output.read_bytes()),'changed-entries':changed,
         'first-join-language-preserved':True,'all-unrelated-entries-identical':all(after[n]==before[n] for n in before if n not in changed)}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
