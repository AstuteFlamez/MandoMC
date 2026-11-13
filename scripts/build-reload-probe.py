#!/usr/bin/env python3
"""Build the disposable Paper reload probe; does not install it into any server."""
import argparse,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--vendor-dir',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];w=r/'.local/reload-release';classes=w/'probe-classes';classes.mkdir(exist_ok=True)
cp=str(w/'MandoMC-native-reload.jar')+':'+a.classpath_file.read_text().strip()+':'+':'.join(str(a.vendor_dir/n) for n in ['WeaponMechanics-4.3.1.jar','MechanicsCore-4.3.1.jar'])
runtime=a.vendor_dir.parent
cp+=':'+str(runtime/'versions/26.1.2/paper-26.1.2.jar')+':'+':'.join(str(f) for f in (runtime/'libraries').rglob('*.jar'))
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-cp',cp,'-d',str(classes),str(r/'scripts/integration/NativeReloadProbe.java')],check=True)
with zipfile.ZipFile(w/'NativeReloadProbe.jar','w')as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 z.writestr('plugin.yml',"name: NativeReloadProbe\nversion: '1'\nmain: net.mandomc.verification.NativeReloadProbe\napi-version: '26.1.2'\ndepend: [MandoMC, WeaponMechanics]\n")
