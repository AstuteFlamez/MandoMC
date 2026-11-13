#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
: "${MANDOMC_HELMET_CLASSPATH:?Set the current Paper/plugin dependency classpath}"
: "${MANDOMC_HELMET_BASELINE:?Set the baseline MandoMC JAR}"
stage=.local/helmet-placement
mkdir -p "$stage/classes" "$stage/probe-classes"
"$JAVA_HOME/bin/javac" --release 25 -cp "$MANDOMC_HELMET_BASELINE:$MANDOMC_HELMET_CLASSPATH" -d "$stage/classes" plugin/src/main/java/net/mandomc/gameplay/trophies/RancorTrophies.java
"$JAVA_HOME/bin/javac" --release 25 -cp "$stage/classes:$MANDOMC_HELMET_BASELINE:$MANDOMC_HELMET_CLASSPATH" -d "$stage/probe-classes" scripts/integration/HelmetDecorationProbe.java
cat > "$stage/probe-classes/plugin.yml" <<'YAML'
name: HelmetDecorationProbe
version: '1'
main: net.mandomc.verification.HelmetDecorationProbe
api-version: '26.1.2'
depend: [MandoMC]
YAML
"$JAVA_HOME/bin/jar" --create --file "$stage/HelmetDecorationProbe.jar" -C "$stage/probe-classes" .
