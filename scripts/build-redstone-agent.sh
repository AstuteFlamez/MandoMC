#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
agent_out="$MANDOMC_ROOT/redstone-agent/target"
mkdir -p "$agent_out/classes"
"$JAVA_HOME/bin/javac" --release 25 -d "$agent_out/classes" \
    "$MANDOMC_ROOT/redstone-agent/src/main/java/net/mandomc/redstone/RedstoneAgent.java"
printf 'Manifest-Version: 1.0\nPremain-Class: net.mandomc.redstone.RedstoneAgent\n\n' > "$agent_out/MANIFEST.MF"
"$JAVA_HOME/bin/jar" --create --date=2026-11-05T00:00:00Z --file "$agent_out/MandoMC-RedstoneAgent.jar" \
    --manifest "$agent_out/MANIFEST.MF" -C "$agent_out/classes" .
