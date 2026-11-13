#!/usr/bin/env python3
"""Replace only the verified six-face trophy owner and bundled helmet language defaults."""
import argparse,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
EXPECTED='cd80a56db993debf4b1168e514a2af65c55671ba69058fe47d6a919a48368214'
p=argparse.ArgumentParser();p.add_argument('--baseline',required=True,type=Path);p.add_argument('--expected-sha256',required=True);p.add_argument('--classes',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(a.baseline.read_bytes())==a.expected_sha256,'Baseline JAR CAS mismatch'
with zipfile.ZipFile(a.baseline) as z:before={n:z.read(n) for n in z.namelist()}
owner='net/mandomc/gameplay/trophies/RancorTrophies.class'
assert sha(before[owner])==EXPECTED,'Live trophy implementation changed; review its current source first'
after=dict(before)
for n in (owner,owner.replace('.class','$1.class')):after[n]=(a.classes/n).read_bytes()
after['trophies/settings.yml']=(ROOT/'plugin/src/main/resources/trophies/settings.yml').read_bytes()
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 for n,b in after.items():z.writestr(n,b)
changed=[n for n in after if after[n]!=before[n]]
assert set(before)==set(after)
assert set(changed)<={owner,owner.replace('.class','$1.class'),'trophies/settings.yml'}
report=dict(baselineSha256=a.expected_sha256,sha256=sha(a.output.read_bytes()),changed=changed,unchanged=len(before)-len(changed))
a.output.with_suffix('.validation.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
