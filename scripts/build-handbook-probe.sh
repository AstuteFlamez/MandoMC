#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/handbook-probe"
mkdir -p "$probe_dir/classes"
./mvnw -o --batch-mode --no-transfer-progress "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
 org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 -cp "plugin/target/classes:$(cat "$probe_dir/classpath.txt")" \
 -d "$probe_dir/classes" scripts/integration/HandbookProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: HandbookProbe
version: '1'
main: net.mandomc.verification.HandbookProbe
api-version: '26.1.2'
depend: [MandoMC]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/HandbookProbe.jar" -C "$probe_dir/classes" .
