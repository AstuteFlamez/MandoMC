#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
stage="$MANDOMC_ROOT/.local/spawn-sign-migration"
mkdir -p "$stage/classes"
./mvnw -o --batch-mode --no-transfer-progress "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$stage/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 -cp "plugin/target/classes:$(cat "$stage/classpath.txt")" -d "$stage/classes" scripts/integration/SpawnSignMigration.java
cat > "$stage/classes/plugin.yml" <<'YAML'
name: SpawnSignMigration
version: '1'
main: net.mandomc.verification.SpawnSignMigration
api-version: '26.1.2'
depend: [MandoMC]
YAML
"$JAVA_HOME/bin/jar" --create --file "$stage/SpawnSignMigration.jar" -C "$stage/classes" .
