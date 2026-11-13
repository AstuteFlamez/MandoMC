#!/usr/bin/env python3
"""Install the public, hash-pinned ModelEngine compile API; never deploy it."""
import hashlib
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
