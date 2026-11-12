#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT/server/runtime"
if [[ ! -f paper.jar ]]; then
    echo "Missing server/runtime/paper.jar; install the artifact in server/paper.lock.json." >&2
    exit 1
fi
python3 "$MANDOMC_ROOT/scripts/sync-resource-pack.py"
cp "$MANDOMC_ROOT/docs/assets/server-icon.png" server-icon.png
exec "$JAVA_HOME/bin/java" -javaagent:agents/MandoMC-RedstoneAgent.jar \
    -Xms1G -Xmx"${MANDOMC_MAX_MEMORY:-4G}" -jar paper.jar --nogui "$@"
