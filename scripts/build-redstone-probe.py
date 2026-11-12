#!/usr/bin/env python3
"""Prepare a disposable Paper runtime; never starts/stops or writes to the supplied server runtime."""
import argparse
import pathlib
import shlex
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("server_runtime", type=pathlib.Path)
parser.add_argument("overlay", type=pathlib.Path)
parser.add_argument("--java-home", required=True, type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
base = root / ".local/redstone"
runtime = base / "runtime"
classes = base / "classes"
classes.mkdir(parents=True, exist_ok=True)
(runtime / "plugins").mkdir(parents=True, exist_ok=True)
(runtime / "agents").mkdir(exist_ok=True)
for name in ("libraries", "versions", "cache"):
    path = runtime / name
    if not path.exists():
        path.symlink_to(args.server_runtime.resolve() / name, target_is_directory=True)
shutil.copy2(args.server_runtime / "paper.jar", runtime / "paper.jar")
shutil.copy2(root / "redstone-agent/target/MandoMC-RedstoneAgent.jar", runtime / "agents/MandoMC-RedstoneAgent.jar")
shutil.copytree(args.server_runtime / "plugins/MandoMC/items", runtime / "plugins/RedstoneProbe/items", dirs_exist_ok=True)
(runtime / "eula.txt").write_text("eula=true\n")
(runtime / "server.properties").write_text(
    'server-ip=127.0.0.1\nserver-port=25691\nonline-mode=false\nlevel-name=redstone-test\n'
    'level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n'
    'spawn-protection=0\nenable-command-block=true\n'
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
    '{"block":"minecraft:stone","height":2}],"biome":"minecraft:plains"}\n')
classpath = ":".join([str(args.overlay.resolve())]
    + [str(p) for p in (args.server_runtime / "versions").rglob("*.jar")]
    + [str(p) for p in (args.server_runtime / "libraries").rglob("*.jar")])
subprocess.run([str(args.java_home / "bin/javac"), "--release", "25", "-cp", classpath,
                "-d", str(classes), str(root / "scripts/integration/RedstoneProbe.java")], check=True)
with zipfile.ZipFile(args.overlay) as overlay, zipfile.ZipFile(runtime / "plugins/RedstoneProbe.jar", "w",
                                                             zipfile.ZIP_DEFLATED) as probe:
    for info in overlay.infolist():
        if info.filename != "plugin.yml":
            probe.writestr(info, overlay.read(info.filename))
    for path in (classes / "net/mandomc/verification").glob("RedstoneProbe*.class"):
        probe.write(path, str(path.relative_to(classes)))
    probe.writestr("plugin.yml", "name: RedstoneProbe\nversion: '1'\n"
                  "main: net.mandomc.verification.RedstoneProbe\napi-version: '26.1.2'\n")
command = [str(args.java_home / "bin/java"), "-Dterminal.jline=false", "-Dterminal.ansi=false",
           "-javaagent:agents/MandoMC-RedstoneAgent.jar", "-Xms256M", "-Xmx1G", "-jar", "paper.jar", "--nogui"]
launcher = base / "run-probe.sh"
launcher.write_text("#!/usr/bin/env bash\nset -euo pipefail\ncd " + shlex.quote(str(runtime))
                    + "\nexec " + shlex.join(command) + "\n")
print(f"Run: bash {launcher}")
