#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
probe_dir="$MANDOMC_ROOT/.local/access-probe"
mkdir -p "$probe_dir/classes"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
./mvnw --batch-mode --no-transfer-progress "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl access org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 -cp "$MANDOMC_ROOT/access/target/classes:$MANDOMC_ROOT/.local/vendor/CoreProtect-CE-24.1.jar:$(cat "$probe_dir/classpath.txt")" -d "$probe_dir/classes" scripts/integration/AccessProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: AccessProbe
version: '1'
main: net.mandomc.verification.AccessProbe
api-version: '26.1.2'
depend: [MandoAccess, CoreProtect]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/AccessProbe.jar" -C "$probe_dir/classes" .
