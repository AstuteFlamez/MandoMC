#!/usr/bin/env python3
"""Build a scoped overlay from the exact current deployment, retaining every unrelated entry."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("base", type=pathlib.Path)
parser.add_argument("output", type=pathlib.Path)
parser.add_argument("--java-home", required=True, type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
build = root / ".local/redstone-overlay"
classes = build / "classes"
classes.mkdir(parents=True, exist_ok=True)
cache = root / ".local/maven-repository"
classpath = ":".join(str(p) for p in cache.rglob("*.jar"))
subprocess.run([str(args.java_home / "bin/javac"), "--release", "25", "-cp", classpath,
                "-d", str(classes), str(root / "plugin/src/main/java/net/mandomc/integrations/paper/RedstoneCircuitBoundary.java"),
                str(root / "scripts/integration/RedstoneOverlay.java")], check=True)
target = "net/mandomc/gameplay/rules/SurvivalRules.class"
with zipfile.ZipFile(args.base) as base:
    original = build / "SurvivalRules-original.class"
    patched = build / "SurvivalRules-patched.class"
    original.write_bytes(base.read(target))
    subprocess.run([str(args.java_home / "bin/java"), "-cp", f"{classes}:{args.base}:{classpath}", "RedstoneOverlay",
                    str(original), str(patched)], check=True)
    updates = {target: patched.read_bytes()}
    updates.update({str(p.relative_to(classes)): p.read_bytes()
                    for p in (classes / "net/mandomc/integrations/paper").glob("RedstoneCircuitBoundary*.class")})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as output:
        for info in base.infolist():
            output.writestr(info, updates.pop(info.filename, base.read(info.filename)))
        for name, content in updates.items():
            output.writestr(name, content)
with zipfile.ZipFile(args.base) as base, zipfile.ZipFile(args.output) as output:
    changed = [name for name in base.namelist() if base.read(name) != output.read(name)]
    if changed != [target]:
        raise RuntimeError(f"Unrelated deployed entries changed: {changed}")
report = {"base-sha256": hashlib.sha256(args.base.read_bytes()).hexdigest(),
          "output-sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
          "changed-existing-entries": changed,
          "new-entries": sorted(set(output.namelist()) - set(base.namelist()))}
args.output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
