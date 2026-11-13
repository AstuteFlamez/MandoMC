#!/usr/bin/env bash
# Run only in a disposable runtime on port 25641. This probe shuts that server down.
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/saber-reflection-probe"
probe_runtime="${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}"
mkdir -p "$probe_dir/classes"
./mvnw --batch-mode --no-transfer-progress \
    "-Dmaven.repo.local=${MANDOMC_MAVEN_REPOSITORY:-$MANDOMC_ROOT/.local/maven-repository}" -pl plugin \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
    "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 \
    -cp "$MANDOMC_ROOT/plugin/target/classes:$probe_runtime/plugins/WeaponMechanics-4.3.1.jar:$probe_runtime/plugins/MechanicsCore-4.3.1.jar:$(cat "$probe_dir/classpath.txt")" \
    -d "$probe_dir/classes" scripts/integration/SaberReflectionProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: SaberReflectionProbe
version: '1'
main: net.mandomc.verification.SaberReflectionProbe
api-version: '26.1.2'
depend: [MandoMC, WeaponMechanics]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/SaberReflectionProbe.jar" -C "$probe_dir/classes" .
echo "Built disposable-server-only .local/saber-reflection-probe/SaberReflectionProbe.jar"
