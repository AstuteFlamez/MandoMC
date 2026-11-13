#!/usr/bin/env python3
"""Build the disposable first-join display-name fixture against a scoped candidate."""
from pathlib import Path
import argparse, subprocess, zipfile
parser = argparse.ArgumentParser()
parser.add_argument('--server-root', type=Path, required=True)
parser.add_argument('--candidate', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
classes = args.output / 'classes'
classes.mkdir(parents=True, exist_ok=True)
jdk = args.server_root / '.local/jdks/jdk-25.0.5+8/Contents/Home'
classpath = ':'.join(map(str, [args.candidate, *(args.server_root / 'server/runtime/libraries').rglob('*.jar')]))
subprocess.run([str(jdk / 'bin/javac'), '--release', '25', '-cp', classpath, '-d', str(classes),
                str(root / 'scripts/integration/NativeProbePlayers.java'), str(root / 'scripts/integration/WelcomeNameProbe.java')], check=True)
(classes / 'plugin.yml').write_text("name: WelcomeNameProbe\nversion: 1.0\nmain: net.mandomc.verification.WelcomeNameProbe\napi-version: '26.1.2'\ndepend: [MandoMC]\n")
with zipfile.ZipFile(args.output / 'WelcomeNameProbe.jar', 'w', zipfile.ZIP_DEFLATED) as output:
    for file in classes.rglob('*'):
        if file.is_file(): output.write(file, file.relative_to(classes))
print(args.output / 'WelcomeNameProbe.jar')
