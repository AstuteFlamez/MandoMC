#!/usr/bin/env python3
"""Install the version-pinned native enchantment registry pack into a STOPPED runtime's primary world."""
import argparse,hashlib,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path,required=True);a=p.parse_args()
runtime=a.runtime.resolve();properties=(runtime/'server.properties').read_text();level=next((l.split('=',1)[1] for l in properties.splitlines() if l.startswith('level-name=')),'world')
world=(runtime/level).resolve()
if not world.is_relative_to(runtime) or world==runtime:raise SystemExit('Primary world must be inside the runtime')
source=ROOT/'server/datapacks/mandomc-jetpack';target=world/'datapacks/mandomc-jetpack'
for file in source.rglob('*'):
 if file.is_file():
  destination=target/file.relative_to(source);destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,destination)
print(json.dumps({'installed':str(target),'files':{f.relative_to(source).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in source.rglob('*') if f.is_file()}},indent=2))
