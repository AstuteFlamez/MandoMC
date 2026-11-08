#!/usr/bin/env python3
"""Own one local Paper process; clean starts/stops, persistent console and no manual restart prompt."""
from __future__ import annotations

import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parent.parent
RUNTIME = ROOT / "server/runtime"
LOCAL = ROOT / ".local"
STATE = LOCAL / "server-control.json"
FIFO = LOCAL / "server-console.fifo"
LOG = LOCAL / "server-console.log"
LAUNCH = ROOT / "scripts/start-server.sh"


def output(*command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else ""


def identity(pid: int) -> str:
    state = output("ps", "-p", str(pid), "-o", "stat=")
    if not state or state.startswith("Z"):
        return ""
    return output("ps", "-p", str(pid), "-o", "lstart=")


def project_process(pid: int) -> bool:
    """Never signal a process based only on a PID file or occupied Minecraft port."""
    command = output("ps", "-p", str(pid), "-o", "command=")
    directories = output("/usr/sbin/lsof", "-a", "-p", str(pid), "-d", "cwd", "-Fn").splitlines()
    cwd = next((line[1:] for line in directories if line.startswith("n")), "")
    paper = re.search(r"(?:^|\s)-jar paper\.jar --nogui(?:\s|$)", command)
    launcher = str(LAUNCH) in command and "bash" in command
    return bool((paper and cwd == str(RUNTIME)) or (launcher and cwd in (str(ROOT), str(RUNTIME))))


def saved() -> dict | None:
    try:
        record = json.loads(STATE.read_text())
        if (identity(record["pid"]) == record["identity"] and project_process(record["pid"])):
            return record
    except (FileNotFoundError, ValueError, KeyError, TypeError):
        pass
    return None


def find_server() -> tuple[int | None, dict | None]:
    record = saved()
    if record:
        return record["pid"], record
    # Recognize an older foreground launcher, without inspecting unrelated Java arguments.
    for line in output("ps", "-axo", "pid=,comm=").splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) == 2 and Path(parts[1]).name == "java":
            pid = int(parts[0])
            if project_process(pid):
                return pid, None
    return None, None


def listening() -> bool:
    try:
        with socket.create_connection(("127.0.0.1", 25565), timeout=0.3):
            return True
    except OSError:
        return False


def send(record: dict, command: str) -> None:
    if "\n" in command or "\r" in command or not command.strip():
        raise RuntimeError("Console command must be one nonempty line")
    if saved() != record:
        raise RuntimeError("Server identity changed; command refused")
    fd = os.open(FIFO, os.O_WRONLY | os.O_NONBLOCK)
    try:
        os.write(fd, (command + "\n").encode())
    finally:
        os.close(fd)


def start() -> None:
    pid, _ = find_server()
    if pid:
        print(f"Server already running (PID {pid}).")
        return
    if listening():
        raise RuntimeError("Port 25565 belongs to another process; refusing to start")
    FIFO.unlink(missing_ok=True)
    os.mkfifo(FIFO, 0o600)
    # A read/write endpoint prevents EOF when the short-lived command sender exits.
    fd = os.open(FIFO, os.O_RDWR)
    try:
        with LOG.open("ab", buffering=0) as log:
            offset = log.tell()
            process = subprocess.Popen([str(LAUNCH)], cwd=ROOT, stdin=fd, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True, close_fds=True)
    finally:
        os.close(fd)
    record = {"pid": process.pid, "identity": identity(process.pid), "log-offset": offset}
    STATE.write_text(json.dumps(record) + "\n")
    print(f"Starting Paper (PID {process.pid})…", flush=True)
    deadline = time.monotonic() + 55
    while time.monotonic() < deadline:
        if process.poll() is not None:
            STATE.unlink(missing_ok=True)
            raise RuntimeError(f"Startup exited ({process.returncode}); inspect {LOG}")
        with LOG.open() as log:
            log.seek(offset)
            ready = "Done (" in log.read()
        if ready and listening():
            print(f"Server ready at localhost:25565 (PID {process.pid}).")
            return
        time.sleep(0.5)
    raise RuntimeError(f"Startup is still in progress; inspect {LOG}. The process has been left running.")


def stop() -> None:
    pid, record = find_server()
    if not pid:
        print("Server already stopped.")
        return
    original = identity(pid)
    if not original or not project_process(pid):
        raise RuntimeError("Server process could not be verified; stop refused")
    # Disable the former Terminal loop's restart marker before a deliberate stop.
    (LOCAL / "server-restart-request").unlink(missing_ok=True)
    if record:
        try:
            send(record, "stop")
        except OSError:
            os.kill(pid, signal.SIGTERM)
    else:
        os.kill(pid, signal.SIGTERM)
    print(f"Saving and stopping Paper (PID {pid})…", flush=True)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if identity(pid) != original:
            STATE.unlink(missing_ok=True)
            FIFO.unlink(missing_ok=True)
            print("Server stopped cleanly.")
            return
        time.sleep(0.25)
    raise RuntimeError("Shutdown has not completed. No forced kill was sent; do not deploy until it stops.")


def console() -> None:
    # This view can stay in one Terminal tab across starts/restarts from other shells.
    ended = threading.Event()
    def follow() -> None:
        with LOG.open("a+") as log:
            log.seek(0, os.SEEK_END)
            while not ended.is_set():
                line = log.readline()
                if line:
                    print(line, end="", flush=True)
                else:
                    ended.wait(0.2)
    thread = threading.Thread(target=follow, daemon=True)
    thread.start()
    print("MandoMC console: commands go to Paper. start / stop / restart / status control the server; exit closes only this view.")
    try:
        for line in sys.stdin:
            command = line.strip()
            if command == "exit":
                break
            if not command:
                continue
            args = [sys.executable, str(Path(__file__).resolve())]
            args += [command] if command in {"start", "stop", "restart", "status"} else ["command", command]
            subprocess.run(args, check=False)
    finally:
        ended.set()
        thread.join(timeout=1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "stop", "restart", "status", "command", "console"])
    parser.add_argument("text", nargs="?")
    args = parser.parse_args()
    LOCAL.mkdir(exist_ok=True)
    try:
        if args.action == "console":
            console()
            return 0
        with (LOCAL / "server-control.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            if args.action == "status":
                pid, record = find_server()
                print(f"{'Managed' if record else 'Foreground'} server running (PID {pid})." if pid else "Server stopped.")
            elif args.action == "command":
                record = saved()
                if record is None or args.text is None:
                    raise RuntimeError("No managed server or command; use start/restart first")
                send(record, args.text)
            else:
                if args.action in {"stop", "restart"}:
                    stop()
                if args.action in {"start", "restart"}:
                    start()
        return 0
    except (OSError, RuntimeError) as error:
        print(f"Server control: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
