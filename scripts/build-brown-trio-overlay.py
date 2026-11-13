#!/usr/bin/env python3
"""Preserve deployed gameplay and extend the existing scenery owner/config only."""
from pathlib import Path
import argparse,hashlib,json,zipfile
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
owned=lambda n:n.startswith('net/mandomc/gameplay/decor/DecorService') or n.startswith('net/mandomc/integrations/models/DecorModel') or n=='decor/placements.yml'
with zipfile.ZipFile(a.baseline) as z:old={n:z.read(n) for n in z.namelist()}
new=old.copy()
with zipfile.ZipFile(a.built) as z:
 for n in z.namelist():
  if owned(n):new[n]=z.read(n)
assert all(new[n]==v for n,v in old.items() if not owned(n))
with zipfile.ZipFile(a.output,'w') as z:
 for n,v in sorted(new.items()):
  i=zipfile.ZipInfo(n,(1980,1,1,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,v)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
r={'baseline-sha256':sha(a.baseline),'candidate-sha256':sha(a.output),'changed-entries':[n for n in new if new[n]!=old.get(n)],'all-unrelated-entries-identical':True}
a.output.with_suffix('.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
