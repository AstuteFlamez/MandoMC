#!/usr/bin/env python3
"""Compile the narrow KOTH change against deployed APIs, test it, and produce a byte-preserving overlay."""
import argparse,hashlib,json,subprocess,zipfile,shutil
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--libraries',type=Path,required=True);p.add_argument('--test-libraries',type=Path,required=True)
p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parent.parent
out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
classes=out/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
libs=list(a.libraries.rglob('*.jar'))+list(a.test_libraries.rglob('*.jar'))
cp=':'.join(map(str,[classes,a.baseline,*libs]))
sources=[root/'plugin/src/main/java/net/mandomc/gameplay/events'/f'{n}.java' for n in ['KothEvent','KothPresentation']]
tests=[root/'plugin/src/test/java/net/mandomc/gameplay/events/KothPresentationTest.java',
       root/'plugin/src/test/java/net/mandomc/gameplay/territory/TerritoryIdentityTest.java']
subprocess.run([str(a.java_home/'bin/javac'),'-cp',cp,'-d',str(classes),*map(str,sources+tests)],check=True)
# Include defaults used by the existing event rule tests.
shutil.copytree(root/'plugin/src/main/resources',classes,dirs_exist_ok=True)
subprocess.run([str(a.java_home/'bin/java'),'-cp',cp,'org.junit.runner.JUnitCore',
                'net.mandomc.gameplay.events.KothPresentationTest',
                'net.mandomc.gameplay.territory.TerritoryIdentityTest'],check=True)
assert not list(classes.glob('net/mandomc/gameplay/events/KothPresentation$*.class')), 'Generated KOTH helpers require an overlay scope update'
sha=lambda b:hashlib.sha256(b).hexdigest()
with zipfile.ZipFile(a.baseline) as old:
 before={n:old.read(n) for n in old.namelist()};after=before.copy()
 assert sha(before['net/mandomc/gameplay/events/KothEvent.class'])=='8ee3abc0e0f741d3779e1eb02a15c49146201424ec038d521af00ed0cceb2e0c','KOTH baseline changed; reconcile before overlay'
 for name in ['KothEvent','KothPresentation']:
  n='net/mandomc/gameplay/events/'+name+'.class';after[n]=(classes/n).read_bytes()
 candidate=out/'MandoMC-koth-presentation.jar'
 with zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED) as jar:
  for n,data in sorted(after.items()):
   info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;jar.writestr(info,data)
 changed=sorted(n for n in after if before.get(n)!=after[n])
 assert changed==['net/mandomc/gameplay/events/KothEvent.class','net/mandomc/gameplay/events/KothPresentation.class']
 subprocess.run(['python3',str(root/'scripts/verify-java-api.py'),'--baseline',str(a.baseline),
                 '--candidate',str(candidate),'--javap',str(a.java_home/'bin/javap'),
                 '--class','net.mandomc.gameplay.events.KothEvent'],check=True)
 report={'baseline-sha256':sha(a.baseline.read_bytes()),'candidate-sha256':sha(candidate.read_bytes()),
         'changed-entries':changed,'unrelated-entries-identical':True,
         'baseline-koth-sha256':sha(before['net/mandomc/gameplay/events/KothEvent.class'])}
 (out/'receipt.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
