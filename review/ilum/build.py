"""Compile against the exact installed Paper API, without touching MandoMC.jar."""
from pathlib import Path
import argparse
import json
import shutil
import subprocess
import zipfile

p=argparse.ArgumentParser()
p.add_argument('--server-root',type=Path,required=True)
p.add_argument('--staged',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
root=Path(__file__).resolve().parent
classes=a.output/'classes'
classes.mkdir(parents=True,exist_ok=True)
jdk=a.server_root/'.local/jdks/jdk-25.0.5+8/Contents/Home'
libraries=list((a.server_root/'server/runtime/libraries').rglob('*.jar'))
subprocess.run([str(jdk/'bin/javac'),'--release','25','-cp',':'.join(map(str,libraries)),
                '-d',str(classes),*map(str,(root/'src').rglob('*.java'))],check=True)
subprocess.run([str(jdk/'bin/java'),'-cp',str(classes),'net.mandomc.review.ilum.ProgressTest'],check=True)
for f in (root/'resources').iterdir():shutil.copy2(f,classes/f.name)
shutil.copy2(a.staged/'map.yml',classes/'map.yml')
proof=json.loads((a.staged/'walk-proof.json').read_text())
(classes/'walk-proof.csv').write_text('\n'.join(','.join(map(str,p)) for p in proof)+'\n')
with zipfile.ZipFile(a.output/'IlumReview.jar','w',zipfile.ZIP_DEFLATED) as jar:
    for f in classes.rglob('*'):
        if f.is_file():jar.write(f,f.relative_to(classes))
print('Built',a.output/'IlumReview.jar')
