#!/usr/bin/env bash
# Package the candidate with a self-contained probe; run ONLY on a disposable Paper server.
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/clan-verification"
mkdir -p "$probe_dir/classes"
./mvnw --batch-mode --no-transfer-progress -o \
    "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
    "-Dmdep.outputFile=$probe_dir/classpath.txt"
cp -R plugin/target/classes/. "$probe_dir/classes/"
"$JAVA_HOME/bin/javac" --release 25 -cp "plugin/target/classes:$(cat "$probe_dir/classpath.txt")" \
    -d "$probe_dir/classes" scripts/integration/ClanFeatureProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: ClanFeatureProbe
version: '1'
main: net.mandomc.verification.ClanFeatureProbe
api-version: '26.1.2'
commands:
  c: {}
  crates: {}
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/ClanFeatureProbe.jar" -C "$probe_dir/classes" .
