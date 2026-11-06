#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT/server/runtime"
if [[ ! -f paper.jar ]]; then
    echo "Missing server/runtime/paper.jar; install the artifact in server/paper.lock.json." >&2
    exit 1
fi
exec "$JAVA_HOME/bin/java" -Xms1G -Xmx"${MANDOMC_MAX_MEMORY:-4G}" -jar paper.jar --nogui "$@"
