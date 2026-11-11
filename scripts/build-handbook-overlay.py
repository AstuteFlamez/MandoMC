#!/usr/bin/env python3
"""Replace only the explicit tutorial handler/book classes; preserve every unrelated live JAR entry."""
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
 assert sha(before['net/mandomc/gameplay/arrival/ArrivalListener.class'])=='c901340c607ef0de75d223a5e63c82f60a9acdc895b15154664f8fe808ccf138','Arrival implementation changed: merge before overlay'
 for name in built.namelist():
  if name.startswith('net/mandomc/gameplay/tutorial/') or name in ('net/mandomc/gameplay/arrival/ArrivalListener.class','language/handbook.yml'):
   after[name]=built.read(name)
 assert set(before)<=set(after)
 changed=sorted(n for n in after if before.get(n)!=after[n])
 assert all(n.startswith('net/mandomc/gameplay/tutorial/') or n in ('net/mandomc/gameplay/arrival/ArrivalListener.class','language/handbook.yml') for n in changed)
 assert before['language/messages.yml']==after['language/messages.yml'],'First-join text changed'
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as out:
  for name,data in sorted(after.items()):out.writestr(name,data)
 report={'baseline-sha256':a.expected_sha256,'candidate-sha256':sha(a.output.read_bytes()),'changed-entries':changed,
         'first-join-language-preserved':True,'all-unrelated-entries-identical':all(after[n]==before[n] for n in before if n not in changed)}
 a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
