#!/usr/bin/env bash
# Keep the console in this Terminal tab between server runs.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .local
restart_request="$PWD/.local/server-restart-request"
rm -f "$restart_request"
while true; do
    if ./scripts/start-server.sh; then
        server_exit=0
    else
        server_exit=$?
    fi
    # For a managed restart, create this marker before cleanly stopping this
    # launcher's Java child. Never use it with an older, unsupervised launcher.
    if [[ -f "$restart_request" ]]; then
        rm -f "$restart_request"
        continue
    fi
    echo "Server stopped (exit $server_exit)."
    if ! read -r -p "Press Enter to start again in this tab, or type q to close: " answer; then
        exit "$server_exit"
    fi
    case "$answer" in q|Q|quit|exit) exit 0 ;; esac
done
