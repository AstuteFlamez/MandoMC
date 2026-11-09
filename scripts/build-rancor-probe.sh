#!/usr/bin/env bash
# Builds a temporary verification plugin. Never leave it installed for ordinary play.
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/rancor-probe"
mkdir -p "$probe_dir/classes"
./mvnw --batch-mode --no-transfer-progress \
    "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
    "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 \
    -cp "$MANDOMC_ROOT/plugin/target/classes:${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}/plugins/WeaponMechanics-4.3.1.jar:${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}/plugins/MechanicsCore-4.3.1.jar:${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}/plugins/MythicMobs-5.14.0.jar:${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}/plugins/ModelEngine-4.1.1.jar:$(cat "$probe_dir/classpath.txt")" \
    -d "$probe_dir/classes" scripts/integration/RancorProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: RancorProbe
version: '1'
main: net.mandomc.verification.RancorProbe
api-version: '26.1.2'
depend: [MandoMC, WeaponMechanics, MythicMobs, ModelEngine]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/RancorProbe.jar" -C "$probe_dir/classes" .
echo "Built .local/rancor-probe/RancorProbe.jar. Install only while the local server is stopped."
