#!/usr/bin/env python3
"""Install the public, hash-pinned ModelEngine compile API; never deploy it."""
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
output = root / '.local' / 'ci' / 'ModelEngine-free-api.jar'
output.parent.mkdir(parents=True, exist_ok=True)
url = 'https://mythiccraft.io/downloads/modelengine4/free/ModelEngine-4.1.1.jar'
expected = '020ef9361c2ab3805933a78994aac529e5e624b0746755044609caf5a6e5df09'
subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
                '--max-time', '120', url, '--output', str(output)], check=True)
data = output.read_bytes()
if hashlib.sha256(data).hexdigest() != expected:
    raise SystemExit('Official ModelEngine compile API checksum mismatch')
subprocess.run([str(root / 'mvnw'), '--batch-mode', '--no-transfer-progress',
                f'-Dmaven.repo.local={root}/.local/maven-repository',
                'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file',
                f'-Dfile={output}', '-DgroupId=com.ticxo.modelengine',
                '-DartifactId=ModelEngine', '-Dversion=R4.1.1', '-Dpackaging=jar',
                '-DgeneratePom=true'], cwd=root, check=True)

# PacketEvents' public runtime artifact also supplies its provided compile API.
# Use the official hash-pinned release instead of depending on CodeMC availability.
packet = next(item for item in json.loads((root / 'server/plugins.lock.json').read_text())['artifacts']
              if item['id'] == 'packetevents')
output = root / '.local/ci' / packet['name']
subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
                '--max-time', '120', packet['url'], '--output', str(output)], check=True)
if hashlib.sha256(output.read_bytes()).hexdigest() != packet['sha256']:
    raise SystemExit('PacketEvents compile API checksum mismatch')
subprocess.run([str(root / 'mvnw'), '--batch-mode', '--no-transfer-progress',
                f'-Dmaven.repo.local={root}/.local/maven-repository',
                'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file',
                f'-Dfile={output}', '-DgroupId=com.github.retrooper',
                '-DartifactId=packetevents-spigot', f'-Dversion={packet["version"]}',
                '-Dpackaging=jar', '-DgeneratePom=true'], cwd=root, check=True)
