#!/usr/bin/env python3
"""Incremental fix for jetpack attachment, GUI/drop transforms and requested 0.1-second coal rate."""
import argparse,hashlib,importlib.util,json,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--pack',type=Path,required=True);p.add_argument('--expected-jar',required=True);p.add_argument('--expected-pack',required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--dependencies',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(a.jar)==a.expected_jar and sha(a.pack)==a.expected_pack,'Live baseline changed'
a.output.mkdir(parents=True,exist_ok=True);classes=a.output/'classes';classes.mkdir(exist_ok=True)
cp=str(a.jar)+':'+a.classpath_file.read_text().strip();sources=[ROOT/'plugin/src/main/java/net/mandomc/gameplay/boba'/n for n in ['BobaEquipment.java','JetpackPose.java','ThrustFuel.java']];sources+=[ROOT/'plugin/src/main/java/net/mandomc/integrations/models/PlayerBackAttachment.java']
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
with zipfile.ZipFile(a.jar) as z:before={n:z.read(n) for n in z.namelist()}
after=dict(before)
for file in classes.rglob('*.class'):after[file.relative_to(classes).as_posix()]=file.read_bytes()
for name in ['items/definitions/boba-equipment.yml','equipment/boba.yml']:after[name]=(ROOT/'plugin/src/main/resources'/name).read_bytes()
changed=sorted(n for n in before if before[n]!=after[n]);allowed={'net/mandomc/gameplay/boba/BobaEquipment.class','net/mandomc/gameplay/boba/BobaEquipment$Wearer.class','net/mandomc/gameplay/boba/JetpackPose.class','net/mandomc/gameplay/boba/JetpackPose$Pose.class','net/mandomc/gameplay/boba/ThrustFuel.class','net/mandomc/integrations/models/PlayerBackAttachment.class','items/definitions/boba-equipment.yml','equipment/boba.yml'};assert set(changed)<=allowed
jar=a.output/'MandoMC.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for n,data in sorted(after.items()):
  info=zipfile.ZipInfo(n,(2026,11,12,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
for name in sorted(n for n in changed if n.endswith('.class')):
 def api(path):
  text=subprocess.check_output([str(a.java_home/'bin/javap'),'-classpath',str(path),name[:-6].replace('/','.')],text=True)
  return {l.strip() for l in text.splitlines() if l.strip().startswith('public ') and l.strip().endswith(';')}
 assert api(a.jar)<=api(jar),'Public API lost: '+name
with zipfile.ZipFile(a.pack) as z:oldpack={n:z.read(n) for n in z.namelist()}
pack=dict(oldpack);paths=['assets/mandomc/items/equipment/boba_jetpack.json','assets/mandomc/models/equipment/boba_jetpack.json','assets/mandomc/models/equipment/boba_jetpack_gui.json']
for n in paths:pack[n]=(ROOT/'resource-pack/src'/n).read_bytes()
assert all(pack[n]==v for n,v in oldpack.items() if n not in paths)
work=a.output/'validation';source=work/'resource-pack/src';source.mkdir(parents=True,exist_ok=True)
for n,data in pack.items():f=source/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
for n in ['resource-pack/sources.json','server/paper.lock.json','resource-pack/dependencies.json']:
 f=work/n;f.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(a.dependencies if n.endswith('dependencies.json') else ROOT/n,f)
spec=importlib.util.spec_from_file_location('packcheck',ROOT/'scripts/build-resource-pack.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);module.ROOT=work;module.PACK=work/'resource-pack';module.SOURCE=source;module.main()
shutil.copyfile(work/'resource-pack/build/MandoMC.zip',a.output/'MandoMC.zip');shutil.copyfile(work/'resource-pack/build/MandoMC.json',a.output/'MandoMC.json')
report={'baselineJarSha256':a.expected_jar,'jarSha256':sha(jar),'changedJarEntries':changed,'newJarEntries':sorted(set(after)-set(before)),'allOtherJarEntriesByteIdentical':True,'allOriginalPublicApisRetained':True,'baselinePackSha256':a.expected_pack,'pack':json.loads((a.output/'MandoMC.json').read_text()),'updatedPackEntries':[n for n in paths if n in oldpack],'newPackEntries':[n for n in paths if n not in oldpack],'allOtherPackEntriesByteIdentical':True}
(a.output/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
