#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
version=$(java -version 2>&1 | head -n 1)
if [[ ! "$version" =~ \"25\. ]]; then
    echo 'MandoMC requires Java 25. Select Java 25 in PebbleHost before starting.' >&2
    exit 1
fi
mkdir -p agents
if [[ -f .mandomc/pending/MandoMC-RedstoneAgent.jar ]]; then
    mv .mandomc/pending/MandoMC-RedstoneAgent.jar agents/MandoMC-RedstoneAgent.jar
fi
if [[ ! -f agents/MandoMC-RedstoneAgent.jar ]]; then
    echo 'Missing MandoMC redstone agent; refusing an incomplete startup.' >&2
    exit 1
fi
exec java -javaagent:agents/MandoMC-RedstoneAgent.jar -Xms512M \
    -Xmx"${MANDOMC_MAX_MEMORY:-6G}" -jar server.jar --nogui "$@"
