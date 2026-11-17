#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
probe_dir="$MANDOMC_ROOT/.local/droid-repository-probe"
mkdir -p "$probe_dir/classes"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
./mvnw --batch-mode --no-transfer-progress -o "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 -cp "$MANDOMC_ROOT/plugin/target/classes:$(cat "$probe_dir/classpath.txt")" -d "$probe_dir/classes" scripts/integration/NativeProbePlayers.java scripts/integration/DroidRepositoryProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: DroidRepositoryProbe
version: '1'
main: net.mandomc.verification.DroidRepositoryProbe
api-version: '26.1.2'
depend: [MandoMC]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/DroidRepositoryProbe.jar" -C "$probe_dir/classes" .
