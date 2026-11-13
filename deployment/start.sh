#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
version=$(java -version 2>&1 | head -n 1)
if [[ "$version" != *'"25"'* && "$version" != *'"25.'* && "$version" != *'"25+'* ]]; then
    echo 'MandoMC requires Java 25. Select Java 25 in PebbleHost before starting.' >&2
    exit 1
fi
if [[ ! -f server.jar || ! -f paper.jar ]]; then
    echo 'Missing checked MandoMC launcher or pinned Paper JAR.' >&2
    exit 1
fi
# The executable JAR loads its bundled agent before Paper through Java's manifest contract.
exec java -Xms512M -Xmx"${MANDOMC_MAX_MEMORY:-6G}" -jar server.jar --nogui "$@"
