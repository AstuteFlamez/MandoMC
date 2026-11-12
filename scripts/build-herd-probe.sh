#!/usr/bin/env bash
# Disposable-server probe; do not install this in the shared development runtime.
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/herd-probe"
mkdir -p "$probe_dir/classes"
./mvnw --offline --batch-mode --no-transfer-progress \
    "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
    "-Dmdep.outputFile=$probe_dir/classpath.txt"
runtime="${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}"
cp -R plugin/target/classes/net "$probe_dir/classes/"
"$JAVA_HOME/bin/javac" --release 25 \
    -cp "plugin/target/classes:$runtime/plugins/MythicMobs-5.14.0.jar:$runtime/plugins/ModelEngine-4.1.1.jar:$(cat "$probe_dir/classpath.txt")" \
    -d "$probe_dir/classes" scripts/integration/HerdProbe.java
mkdir -p "$probe_dir/classes/items/definitions"
cp plugin/src/main/resources/items/catalog.yml "$probe_dir/classes/items/"
cp plugin/src/main/resources/items/definitions/{materials,dewback}.yml "$probe_dir/classes/items/definitions/"
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: HerdProbe
version: '1'
main: net.mandomc.verification.HerdProbe
api-version: '26.1.2'
depend: [MythicMobs, ModelEngine]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/HerdProbe.jar" -C "$probe_dir/classes" .
echo "Built .local/herd-probe/HerdProbe.jar for a disposable runtime only."
