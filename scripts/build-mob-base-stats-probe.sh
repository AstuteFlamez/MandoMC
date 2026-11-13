#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
probe_dir="$MANDOMC_ROOT/.local/mob-base-stats-probe"
mkdir -p "$probe_dir/classes"
./mvnw --offline --batch-mode --no-transfer-progress \
  "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  "-Dmdep.outputFile=$probe_dir/classpath.txt"
runtime="${MANDOMC_RUNTIME:-$MANDOMC_ROOT/server/runtime}"
"$JAVA_HOME/bin/javac" --release 25 \
  -cp "plugin/target/classes:$runtime/plugins/MythicMobs-5.14.0.jar:$(cat "$probe_dir/classpath.txt")" \
  -d "$probe_dir/classes" scripts/integration/MobBaseStatsProbe.java
mkdir -p "$probe_dir/classes/net/mandomc/gameplay/mobs" "$probe_dir/classes/net/mandomc/integrations/mobs"
cp plugin/target/classes/net/mandomc/gameplay/mobs/{EncounterPlan*,SandySettings*}.class "$probe_dir/classes/net/mandomc/gameplay/mobs/"
cp plugin/target/classes/net/mandomc/integrations/mobs/{MythicMobAccess,MobBaseStats}.class "$probe_dir/classes/net/mandomc/integrations/mobs/"
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: MobBaseStatsProbe
version: '1'
main: net.mandomc.verification.MobBaseStatsProbe
api-version: '26.1.2'
depend: [MythicMobs, ModelEngine]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/MobBaseStatsProbe.jar" -C "$probe_dir/classes" .
echo "Built disposable probe. Boot twice on levels_test/loopback port25578 for save/restart checks."
