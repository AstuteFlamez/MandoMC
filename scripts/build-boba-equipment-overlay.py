#!/usr/bin/env python3
"""Rebase only Boba equipment onto an exact deployed JAR; never replace unrelated classes or settings."""
import argparse
import hashlib
import json
import io
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def sha(data):
    return hashlib.sha256(data).hexdigest()

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline', type=Path, required=True)
p.add_argument('--expected-sha256', required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--classpath-file', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
baseline_bytes = a.baseline.read_bytes()
assert sha(baseline_bytes) == a.expected_sha256, 'Deployment changed: rebuild against its new exact baseline'
work = ROOT / '.local/boba-release'
classes = work / 'classes'
classes.mkdir(parents=True, exist_ok=True)
cp = str(a.baseline) + ':' + a.classpath_file.read_text().strip()
sources = list((ROOT / 'plugin/src/main/java/net/mandomc/gameplay/boba').glob('*.java'))
sources += [ROOT / 'plugin/src/main/java/net/mandomc/gameplay/mobs/JabbaDropsSettings.java',
            ROOT / 'plugin/src/main/java/net/mandomc/integrations/mobs/JabbaDrops.java']
subprocess.run([str(a.java_home / 'bin/javac'), '--release', '25', '-cp', cp, '-d', str(classes),
                *map(str, sources), str(ROOT / 'scripts/integration/BobaBootstrapOverlay.java')], check=True)
with zipfile.ZipFile(io.BytesIO(baseline_bytes)) as z:
    before = {n: z.read(n) for n in z.namelist()}
after = dict(before)
bootstrap = 'net/mandomc/bootstrap/MandoPlugin.class'
original, patched = work / 'bootstrap-before.class', work / 'bootstrap-after.class'
original.write_bytes(before[bootstrap])
subprocess.run([str(a.java_home / 'bin/java'), '-cp', str(classes) + ':' + cp, 'BobaBootstrapOverlay', str(original), str(patched)], check=True)
after[bootstrap] = patched.read_bytes()
for group in ['net/mandomc/gameplay/boba', 'net/mandomc/gameplay/mobs', 'net/mandomc/integrations/mobs']:
    for file in (classes / group).glob('*.class'):
        name = file.relative_to(classes).as_posix()
        if group.endswith('/boba') or file.name.startswith(('JabbaDropsSettings', 'JabbaDrops')):
            if name == 'net/mandomc/gameplay/mobs/JabbaDropsSettings$Salvage.class' and name in before:
                continue  # The existing immutable salvage record remains byte-identical.
            after[name] = file.read_bytes()
resource = ROOT / 'plugin/src/main/resources'
for name in ['equipment/boba.yml', 'items/definitions/boba-equipment.yml']:
    assert name not in before, 'Equipment already installed; reconcile explicitly'
    after[name] = (resource / name).read_bytes()
language = before['language/messages.yml'].decode()
assert not re.search(r'^boba-equipment:', language, re.M), 'Existing language section must be reconciled'
section = (resource / 'messages.yml').read_text().split('\nboba-equipment:', 1)[1]
after['language/messages.yml'] = (language.rstrip() + '\n\nboba-equipment:' + section).encode()
plugin = before['plugin.yml'].decode()
assert not re.search(r'^  mandomc.equipment:', plugin, re.M)
after['plugin.yml'] = (plugin.rstrip() + '\n  mandomc.equipment:\n    default: true\n').encode()
drops = before['mobs/drops/jabba.yml'].decode()
match = re.search(r'^  BobaFett:\n(?P<body>(?:^(?!  [A-Za-z]).*\n?)*)', drops, re.M)
assert match and not re.search(r'^    equipment:', match['body'], re.M), 'Unexpected existing Boba drop structure'
end = match.end()
after['mobs/drops/jabba.yml'] = (drops[:end].rstrip() + '\n    equipment:\n      boba_jetpack: 0.10\n      boba_flamethrower: 0.15\n' + drops[end:]).encode()
allowed = {bootstrap, 'net/mandomc/integrations/mobs/JabbaDrops.class', 'language/messages.yml', 'plugin.yml', 'mobs/drops/jabba.yml'}
allowed.update(n for n in before if n.startswith('net/mandomc/gameplay/mobs/JabbaDropsSettings'))
changed = sorted(n for n in before if before[n] != after[n])
assert set(changed) <= allowed, 'Unowned entry changed'
for name in before:
    if name not in allowed:
        assert before[name] == after[name], name
for name in before:
    if 'Redstone' in name:
        assert before[name] == after[name], 'Required redstone boundary changed'
a.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(a.output, 'w', zipfile.ZIP_DEFLATED) as z:
    for name, data in sorted(after.items()):
        info = zipfile.ZipInfo(name, (2026, 11, 12, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, data)
# A fresh overlay must retain every old public field/constructor/method signature.
for name in [bootstrap, 'net/mandomc/gameplay/mobs/JabbaDropsSettings.class', 'net/mandomc/gameplay/mobs/JabbaDropsSettings$Drop.class',
             'net/mandomc/gameplay/mobs/JabbaDropsSettings$Salvage.class', 'net/mandomc/integrations/mobs/JabbaDrops.class']:
    if name not in before:
        continue
    java_class = name[:-6].replace('/', '.')
    def api(jar):
        out = subprocess.check_output([str(a.java_home / 'bin/javap'), '-classpath', str(jar), java_class], text=True)
        return {line.strip() for line in out.splitlines() if line.strip().startswith('public ') and line.strip().endswith(';')}
    assert api(a.baseline) <= api(a.output), 'Public API lost: ' + java_class
report = {'baselineSha256': a.expected_sha256, 'candidateSha256': sha(a.output.read_bytes()),
          'changedExistingEntries': changed, 'newEntries': sorted(set(after) - set(before)),
          'allUnownedEntriesByteIdentical': True, 'allOriginalPublicApisRetained': True,
          'redstoneBoundaryPreserved': True, 'requiresExistingRedstoneJavaAgentLauncher': True}
a.output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
