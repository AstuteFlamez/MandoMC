#!/usr/bin/env python3
"""Overlay the native chestplate enchantment/crafting upgrade onto the current JAR."""
import argparse,hashlib,importlib.util,json,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--pack',type=Path,required=True);p.add_argument('--expected-jar',required=True);p.add_argument('--expected-pack',required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--dependencies',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(a.jar)==a.expected_jar and sha(a.pack)==a.expected_pack,'Live baseline changed'
a.output.mkdir(parents=True,exist_ok=True);classes=a.output/'classes';classes.mkdir(exist_ok=True)
cp=str(a.jar)+':'+a.classpath_file.read_text().strip();sources=[ROOT/'plugin/src/main/java/net/mandomc/gameplay/boba'/n for n in ['BobaEquipment.java','JetpackEquipmentMenu.java','JetpackChestplate.java','JetpackTooltip.java','ThrustFuel.java']]
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
with zipfile.ZipFile(a.jar) as z:before={n:z.read(n) for n in z.namelist()}
after=dict(before)
for file in classes.rglob('*.class'):after[file.relative_to(classes).as_posix()]=file.read_bytes()
for name in ['items/definitions/boba-equipment.yml','equipment/jetpack-chestplate.yml']:after[name]=(ROOT/'plugin/src/main/resources'/name).read_bytes()
for n in ['language/messages.yml']:
 if n in before:after[n]=before[n].replace(b'Equip the jetpack in your offhand first.',b'Wear a chestplate with the Jetpack enchantment first.')
changed=sorted(n for n in before if before[n]!=after[n]);allowed={'net/mandomc/gameplay/boba/BobaEquipment.class','net/mandomc/gameplay/boba/BobaEquipment$Wearer.class','net/mandomc/gameplay/boba/JetpackPose.class','net/mandomc/gameplay/boba/JetpackPose$Pose.class','net/mandomc/gameplay/boba/ThrustFuel.class','net/mandomc/gameplay/boba/JetpackEquipmentMenu.class','net/mandomc/integrations/models/PlayerBackAttachment.class','items/definitions/boba-equipment.yml','equipment/boba.yml','equipment/jetpack-chestplate.yml','net/mandomc/gameplay/boba/JetpackChestplate.class','language/messages.yml'};assert set(changed)<=allowed
jar=a.output/'MandoMC.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for n,data in sorted(after.items()):
  info=zipfile.ZipInfo(n,(2026,11,12,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
for name in sorted(n for n in changed if n.endswith('.class')):
 def api(path):
  text=subprocess.check_output([str(a.java_home/'bin/javap'),'-classpath',str(path),name[:-6].replace('/','.')],text=True)
  return {l.strip() for l in text.splitlines() if l.strip().startswith('public ') and l.strip().endswith(';')}
 assert api(a.jar)<=api(jar),'Public API lost: '+name
report={'baselineJarSha256':a.expected_jar,'jarSha256':sha(jar),'changedJarEntries':changed,'newJarEntries':sorted(set(after)-set(before)),'allOtherJarEntriesByteIdentical':True,'allOriginalPublicApisRetained':True,'packUnchanged':True}
(a.output/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
