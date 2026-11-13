#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/clan-refresh-probes"
mkdir -p "$probe_dir"
./mvnw -o --batch-mode --no-transfer-progress "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
 org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
for name in HudTerritoryProbe FrozenLandProbe ConnectedClansProbe ClanRolesRestartProbe; do
 mkdir -p "$probe_dir/$name/classes"
 "$JAVA_HOME/bin/javac" --release 25 -cp "plugin/target/classes:$(cat "$probe_dir/classpath.txt")" \
  -d "$probe_dir/$name/classes" "scripts/integration/$name.java"
 cat > "$probe_dir/$name/classes/plugin.yml" <<YAML
name: $name
version: '1'
main: net.mandomc.verification.$name
api-version: '26.1.2'
depend: [MandoMC]
YAML
 "$JAVA_HOME/bin/jar" --create --file "$probe_dir/$name.jar" -C "$probe_dir/$name/classes" .
done
