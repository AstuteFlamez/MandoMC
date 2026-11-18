#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
probe_dir="$MANDOMC_ROOT/.local/spawner-preview-probe"
mkdir -p "$probe_dir/classes"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
./mvnw --batch-mode --no-transfer-progress -o "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
native_runtime="${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}"
native_classpath="$(python3 -c 'import pathlib,sys; r=pathlib.Path(sys.argv[1]); print(":".join(str(p) for folder in ["versions","libraries"] for p in (r/folder).rglob("*.jar")))' "$native_runtime")"
"$JAVA_HOME/bin/javac" --release 25 -cp "$native_classpath:$MANDOMC_ROOT/plugin/target/classes:${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}/plugins/WeaponMechanics-4.3.1.jar:${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}/plugins/MechanicsCore-4.3.1.jar:$(cat "$probe_dir/classpath.txt")" -d "$probe_dir/classes" scripts/integration/NativeProbePlayers.java scripts/integration/SpawnerPreviewProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: SpawnerPreviewProbe
version: '1'
main: net.mandomc.verification.SpawnerPreviewProbe
api-version: '26.1.2'
depend: [MandoMC]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/SpawnerPreviewProbe.jar" -C "$probe_dir/classes" .
