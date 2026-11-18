#!/usr/bin/env python3
"""Run the native Discord bridge in disposable Paper against an HTTPS stub, never live Discord.

Requires --paper-runtime containing the pinned paper.jar/cache/libraries/versions and
--java-home. All fixture writes stay in .local/discord-native. The original runtime
is read-only input. Run ./scripts/build.sh first. No real tokens or player data used.
"""
import argparse
import http.server
import json
import os
from pathlib import Path
import shutil
import socket
import ssl
import subprocess
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
UUID = "12345678-1234-1234-1234-123456789012"
SECRET = "disposable-native-fixture-" + "x" * 40


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper-runtime", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    args = parser.parse_args()
    output = ROOT / ".local/discord-native"
    if output.exists():
        raise SystemExit("Fixture already exists; retain it and choose a clean checkout before rerunning.")
    runtime = output / "runtime"
    runtime.mkdir(parents=True)
    for name in ("cache", "libraries", "versions"):
        shutil.copytree(args.paper_runtime / name, runtime / name)
    shutil.copyfile(args.paper_runtime / "paper.jar", runtime / "paper.jar")
    (runtime / "eula.txt").write_text("eula=true\n")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        paper_port = listener.getsockname()[1]
    (runtime / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={paper_port}\nonline-mode=false\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
        "generate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=2\n")
    classes = output / "classes"
    classes.mkdir()
    candidate = ROOT / "plugin/target/MandoMC.jar"
    classpath = os.pathsep.join(map(str, [candidate, *(runtime / "libraries").rglob("*.jar")]))
    subprocess.run([str(args.java_home / "bin/javac"), "--release", "25", "-cp", classpath, "-d", str(classes),
                    str(ROOT / "scripts/integration/DiscordBridgeProbe.java")], check=True)
    plugin_dir = runtime / "plugins"
    plugin_dir.mkdir()
    with zipfile.ZipFile(candidate) as original, zipfile.ZipFile(plugin_dir / "MandoMC.jar", "w", zipfile.ZIP_DEFLATED) as probe:
        for entry in original.infolist():
            if entry.filename == "plugin.yml":
                descriptor = original.read(entry).decode().replace("main: net.mandomc.bootstrap.MandoPlugin", "main: net.mandomc.verification.DiscordBridgeProbe")
                probe.writestr(entry, descriptor)
            else:
                probe.writestr(entry, original.read(entry))
        for file in classes.rglob("*.class"):
            probe.write(file, str(file.relative_to(classes)))
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                    "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost",
                    "-keyout", str(output / "key.pem"), "-out", str(output / "cert.pem")],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run([str(args.java_home / "bin/keytool"), "-importcert", "-noprompt", "-alias", "fixture",
                    "-file", str(output / "cert.pem"), "-keystore", str(output / "trust.p12"), "-storepass", "fixture-only"],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    state = {"phase": 0, "failures": []}

    class BridgeStub(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def send(self, value, status=200):
            data = json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if self.headers.get("Authorization") != "Bearer " + SECRET:
                self.send({}, 401)
                return
            self.send({"uuids": [UUID], "next": None})

        def do_POST(self):
            if self.headers.get("Authorization") != "Bearer " + SECRET:
                self.send({}, 401)
                return
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if self.path == "/bridge/snapshot":
                self.send({"ok": True})
                return
            players = body["players"]
            if not players:
                self.send({"updates": [], "errors": []})
                return
            player = players[0]
            ranks = set(player["ranks"])
            phase = state["phase"]
            expected = [{"builder", "warrior"}, {"builder", "marshal"}, {"builder"},
                        {"builder", "warrior"}, {"builder", "warrior"}, {"builder", "marshal"}][min(phase, 5)]
            if ranks != expected:
                state["failures"].append(f"Phase {phase}: unexpected native ranks {sorted(ranks)}")
            target = ["marshal"] if phase in (0, 2, 4, 5) else []
            if phase == 2:
                (output / "intervene").write_text("change ranks while response is in flight\n")
                time.sleep(2)
            if phase == 3:
                state["phase"] += 1
                self.send({"error": "simulated outage"}, 503)
                return
            if phase == 5 and player.get("ack") != 5:
                state["failures"].append("Native rank acknowledgment was not committed with memberships")
            self.send({"updates": [{"uuid": UUID, "expected": player["ranks"], "ranks": target,
                                   "revision": phase + 1, "signature": "disposable-native-fixture", "reward": True}], "errors": []})
            state["phase"] += 1
            if phase == 5:
                (output / "complete").write_text("verified native snapshots\n")

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), BridgeStub)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(output / "cert.pem", output / "key.pem")
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    command = [str(args.java_home / "bin/java"), "-Xms256m", "-Xmx1g", "-Dterminal.jline=false", "-Dterminal.ansi=false",
               f"-Djavax.net.ssl.trustStore={output / 'trust.p12'}", "-Djavax.net.ssl.trustStorePassword=fixture-only",
               f"-Dprobe.endpoint=https://localhost:{server.server_port}", f"-Dprobe.control={output}",
               "-jar", "paper.jar", "--nogui"]
    try:
        with (output / "paper.log").open("w") as log:
            result = subprocess.run(command, cwd=runtime, env={**os.environ, "MANDOMC_DISCORD_BRIDGE_SECRET": SECRET},
                                    stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, timeout=150)
    finally:
        server.shutdown()
    log = (output / "paper.log").read_text()
    if result.returncode or state["failures"] or "DISCORD_BRIDGE_PROBE_PASSED" not in log:
        raise SystemExit(f"Native bridge fixture failed: {state['failures']}; inspect {output / 'paper.log'}")
    print("PASS: disposable Paper native bridge, six HTTPS reconciliation stages and once-only durable 5,000-credit linking reward")


if __name__ == "__main__":
    main()
