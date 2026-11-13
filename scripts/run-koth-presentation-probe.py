#!/usr/bin/env python3
"""Build a standalone fixture and run it in a fresh loopback-only disposable Paper world."""
import argparse,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parent.parent;out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True)
cp=':'.join(map(str,[a.candidate,*a.runtime.joinpath('libraries').rglob('*.jar')]))
subprocess.run([str(a.java_home/'bin/javac'),'-cp',cp,'-d',str(classes),str(root/'scripts/integration/KothPresentationProbe.java')],check=True)
server=out/'server';server.mkdir(exist_ok=True);(server/'plugins').mkdir(exist_ok=True)
for directory in ['libraries','versions','cache']:
 if (a.runtime/directory).exists() and not (server/directory).exists():(server/directory).symlink_to((a.runtime/directory).resolve(),target_is_directory=True)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nlevel-name=koth_fixture\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
(server/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
with zipfile.ZipFile(a.candidate) as old,zipfile.ZipFile(server/'plugins/KothPresentationProbe.jar','w',zipfile.ZIP_DEFLATED) as jar:
 for name in old.namelist():
  if name!='plugin.yml':jar.writestr(name,old.read(name))
 jar.writestr('verification/koth-crates.yml',(a.runtime/'plugins/MandoMC/crates/crates.yml').read_bytes())
 for file in classes.rglob('*.class'):jar.write(file,str(file.relative_to(classes)))
 jar.writestr('plugin.yml',"name: KothPresentationProbe\nversion: '1'\nmain: net.mandomc.verification.KothPresentationProbe\napi-version: '26.1.2'\ncommands:\n  crates: {}\n")
for name in ['PASSED.txt','FAILED.txt']:
 (server/'plugins/KothPresentationProbe'/name).unlink(missing_ok=True)
with (out/'console.log').open('w') as log:
 result=subprocess.run([str(a.java_home/'bin/java'),'-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1536M','-jar',str(a.runtime.resolve()/'paper.jar'),'--nogui'],cwd=server,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=180)
passed=server/'plugins/KothPresentationProbe/PASSED.txt';failed=server/'plugins/KothPresentationProbe/FAILED.txt'
if failed.exists():raise SystemExit(failed.read_text())
if not passed.exists():raise SystemExit('Native probe did not pass; inspect console.log')
print(passed.read_text())
