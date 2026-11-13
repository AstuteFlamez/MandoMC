#!/usr/bin/env python3
"""Overlay only the guide lifecycle, guide classes and guide configuration onto deployed code."""
import argparse,hashlib,json,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
owned=lambda n:n.startswith('net/mandomc/gameplay/decor/TravelGuides') or n.startswith('net/mandomc/gameplay/decor/AmbientMassiffs') or n.startswith('net/mandomc/integrations/models/AmbientMassiffModel') or n=='net/mandomc/gameplay/decor/DecorFeature.class' or n=='decor/travel-guides.yml'
with zipfile.ZipFile(a.baseline) as z:old={n:z.read(n) for n in z.namelist()}
new=old.copy()
with zipfile.ZipFile(a.built) as z:
 for n in z.namelist():
  if owned(n):new[n]=z.read(n)
assert all(new[n]==v for n,v in old.items() if not owned(n))
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 for n,v in sorted(new.items()):z.writestr(n,v)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
r={'baseline-sha256':sha(a.baseline),'candidate-sha256':sha(a.output),'changed-entries':[n for n in new if new[n]!=old.get(n)],'all-unrelated-entries-identical':True}
a.output.with_suffix('.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
