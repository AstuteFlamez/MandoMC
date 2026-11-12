#!/usr/bin/env python3
"""Preserve the actual deployed JAR; replace only the native starting-credit owner."""
import argparse, hashlib, json, zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
owned={'net/mandomc/gameplay/credits/CreditLedger.class','net/mandomc/gameplay/credits/CreditService.class','economy/credits.yml'}
with zipfile.ZipFile(a.baseline) as old, zipfile.ZipFile(a.built) as built:
    data={n:old.read(n) for n in old.namelist()};new=dict(data)
    for n in owned:new[n]=built.read(n)
    assert all(new[n]==v for n,v in data.items() if n not in owned)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as out:
        for n,v in new.items():out.writestr(n,v)
report={'baseline':hashlib.sha256(a.baseline.read_bytes()).hexdigest(),'candidate':hashlib.sha256(a.output.read_bytes()).hexdigest(),'changed':sorted(n for n in new if data.get(n)!=new[n]),'unrelated-entries-identical':True}
a.output.with_suffix('.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
