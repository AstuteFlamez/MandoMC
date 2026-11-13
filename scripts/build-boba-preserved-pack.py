#!/usr/bin/env python3
"""Preserve the current pack, recover missing live-feature assets, add Boba gear, and validate all references."""
import argparse,hashlib,importlib.util,json,shutil,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('current',type=Path);p.add_argument('good',type=Path);p.add_argument('output',type=Path);p.add_argument('--dependencies',type=Path,required=True);a=p.parse_args()
def sha(data):return hashlib.sha256(data).hexdigest()
assert sha(a.current.read_bytes())=='3a9858232672dfda57834ae67e1acf0696ebf31df78cb474fc8b2a973359a9f1'
assert sha(a.good.read_bytes())=='2c075adc803b28496eb6a7d5f8164a6c7cc85c87c0cb7ed83ceb4139459e8f54'
with zipfile.ZipFile(a.current) as z:current={n:z.read(n) for n in z.namelist()}
with zipfile.ZipFile(a.good) as z:good={n:z.read(n) for n in z.namelist()}
merged=dict(current);restored={n:good[n] for n in good if n not in merged};assert len(restored)==68
merged.update(restored)
sounds='assets/mandomc/sounds.json';new=json.loads(current[sounds]);old=json.loads(good[sounds]);missing={k:v for k,v in old.items() if k not in new};assert set(missing)=={'mobs.jawa1','mobs.jawa2','mobs.jawa3','shoot.dc-15'}
new.update(missing);assert all(new[k]==v for k,v in json.loads(current[sounds]).items());merged[sounds]=(json.dumps(new,indent=2,ensure_ascii=False)+'\n').encode()
font='assets/minecraft/font/default.json';new=json.loads(current[font]);old=json.loads(good[font]);extra=[v for v in old['providers'] if v not in new['providers']];assert len(extra)==1 and extra[0]['chars']==['$']
new['providers']=extra+new['providers'];assert new['providers'][0]['file']=='mandomc:font/credit.png';assert new['providers'][1:]==json.loads(current[font])['providers'];merged[font]=(json.dumps(new,indent=2,ensure_ascii=False)+'\n').encode()
trophy=json.loads(merged['assets/mandomc/font/trophies.json']);assert trophy==json.loads(good['assets/mandomc/font/trophies.json'])
assets=[]
for folder in ['items/equipment','models/equipment','textures/item/equipment']:
 for file in (ROOT/'resource-pack/src/assets/mandomc'/folder).iterdir():
  name='assets/mandomc/'+folder+'/'+file.name;assert name not in merged;merged[name]=file.read_bytes();assets.append(name)
assert len(assets)==8
changed=sorted(n for n in current if current[n]!=merged[n]);assert changed==sorted([font,sounds])
work=ROOT/'.local/boba-release/merged-validation';source=work/'resource-pack/src';source.mkdir(parents=True,exist_ok=True)
for name,data in merged.items():
 file=source/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(data)
for name in ['resource-pack/sources.json','resource-pack/dependencies.json','server/paper.lock.json']:
 target=work/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(a.dependencies if name=='resource-pack/dependencies.json' else ROOT/name,target)
spec=importlib.util.spec_from_file_location('pack_validation',ROOT/'scripts/build-resource-pack.py');validation=importlib.util.module_from_spec(spec);spec.loader.exec_module(validation);validation.ROOT=work;validation.PACK=work/'resource-pack';validation.SOURCE=source;validation.main()
a.output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(work/'resource-pack/build/MandoMC.zip',a.output);shutil.copyfile(work/'resource-pack/build/MandoMC.json',a.output.with_suffix('.json'))
with zipfile.ZipFile(a.output) as z:
 assert all(z.read(n)==v for n,v in merged.items())
report={'currentBaseSha256':sha(a.current.read_bytes()),'lastGoodSha256':sha(a.good.read_bytes()),'restoredFiles':{n:sha(v) for n,v in sorted(restored.items())},'gearAssets':sorted(assets),'changedCurrentEntries':changed,'currentOtherEntriesByteIdentical':True,'currentCommonTexturesByteIdentical':True,'currentSoundEventsUnchanged':True,'addedSoundEvents':sorted(missing),'currentFontProvidersUnchangedAndOrdered':True,'creditDollarProviderFirst':True,'trophyNamespacedFontRestoredExactly':True,'allReferencesValidated':True,'final':json.loads(a.output.with_suffix('.json').read_text())}
a.output.with_name('preservation-audit.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report['final'],indent=2))
