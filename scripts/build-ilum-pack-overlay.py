#!/usr/bin/env python3
"""Add Ilum's planet/key artwork to the exact currently published pack without reverting other assets."""
from pathlib import Path
import argparse,hashlib,json,zipfile,subprocess

p=argparse.ArgumentParser()
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--manifest',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[1]
baseline=json.loads(a.manifest.read_text())
assert hashlib.sha256(a.baseline.read_bytes()).hexdigest()==baseline['sha256']
with zipfile.ZipFile(a.baseline) as source:
    entries={n:source.read(n) for n in source.namelist()}
old=entries.copy();added=[]
for kind,name in [('planets','ilum'),('keys','ilum_kyber_crate_key')]:
    for path in [f'items/{kind}/{name}.json',f'models/item/{kind}/{name}.json',f'textures/item/{kind}/{name}.png']:
        name='assets/mandomc/'+path
        assert name not in old,'Refusing to overwrite an existing asset'
        entries[name]=(root/'resource-pack/src'/name).read_bytes();added.append(name)
        if name.endswith('.json'):json.loads(entries[name])
assert all(entries[n]==data for n,data in old.items())
a.output.mkdir(parents=True,exist_ok=True)
target=a.output/'MandoMC.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
    for name,data in sorted(entries.items()):
        info=zipfile.ZipInfo(name,date_time=(2026,11,10,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        out.writestr(info,data)
raw=target.read_bytes();digest=hashlib.sha256(raw).hexdigest();tag='resource-pack-26.1.2-'+digest[:12]
report={**baseline,'files':len(entries),'bytes':len(raw),'sha1':hashlib.sha1(raw).hexdigest(),'sha256':digest,
        'sourceCommit':subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip(),
        'sourceBranch':'bilal/legacy-ilum-crates','sourceWorkingTree':True,
        'baselineRelease':'resource-pack-26.1.2-'+baseline['sha256'][:12],
        'sourcePublished':False,'tagBasis':'pack-sha256',
        'url':f'https://github.com/AstuteFlamez/MandoMC/releases/download/{tag}/MandoMC.zip',
        'addedEntries':added,'preservedPreviousEntries':len(old),'allPreviousEntriesByteIdentical':True}
(a.output/'MandoMC.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
