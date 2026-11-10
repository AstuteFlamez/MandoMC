#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/vehicle-weapons-probe"
runtime="${MANDOMC_TEST_RUNTIME:-$MANDOMC_ROOT/server/runtime}"
mkdir -p "$probe_dir/classes"
./mvnw --batch-mode --no-transfer-progress "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -o -pl plugin \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 \
  -cp "$MANDOMC_ROOT/plugin/target/classes:$runtime/plugins/WeaponMechanics-4.3.1.jar:$runtime/plugins/MechanicsCore-4.3.1.jar:$(cat "$probe_dir/classpath.txt")" \
  -d "$probe_dir/classes" scripts/integration/VehicleWeaponsProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: MandoVehicleWeaponsProbe
version: '1'
main: net.mandomc.verification.VehicleWeaponsProbe
api-version: '26.1.2'
depend: [MandoMC, WeaponMechanics, ModelEngine]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/VehicleWeaponsProbe.jar" -C "$probe_dir/classes" .
