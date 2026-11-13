#!/usr/bin/env python3
"""Compile the starting-credit checks; install only in a disposable Paper runtime."""
from pathlib import Path
import argparse, subprocess, zipfile
p=argparse.ArgumentParser();p.add_argument('--server-root',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];classes=a.output/'classes';classes.mkdir(parents=True,exist_ok=True)
java=a.server_root/'.local/jdks/jdk-25.0.5+8/Contents/Home/bin/javac'
cp=':'.join(map(str,[a.candidate,*(a.server_root/'server/runtime/libraries').rglob('*.jar')]))
subprocess.run([str(java),'--release','25','-cp',cp,'-d',str(classes),str(root/'scripts/integration/StartingCreditsProbe.java'),str(root/'scripts/integration/NativeProbePlayers.java')],check=True)
(classes/'plugin.yml').write_text("name: StartingCreditsProbe\nversion: '1'\nmain: net.mandomc.verification.StartingCreditsProbe\napi-version: '26.1.2'\ndepend: [MandoMC]\n")
with zipfile.ZipFile(a.output/'StartingCreditsProbe.jar','w',zipfile.ZIP_DEFLATED) as out:
    for f in classes.rglob('*'):
        if f.is_file():out.write(f,f.relative_to(classes))
print(a.output/'StartingCreditsProbe.jar')
