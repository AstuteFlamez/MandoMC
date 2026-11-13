#!/usr/bin/env bash
# Dedicated empty Paper runtime only; this probe instantiates scoped services without booting MandoMC.
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
probe_dir="$MANDOMC_ROOT/.local/boba-equipment-probe"
mkdir -p "$probe_dir/classes"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
./mvnw -o --batch-mode --no-transfer-progress "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" -pl plugin \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$probe_dir/classpath.txt"
"$JAVA_HOME/bin/javac" --release 25 -cp "$MANDOMC_ROOT/plugin/target/classes:$(cat "$probe_dir/classpath.txt")" \
    -d "$probe_dir/classes" scripts/integration/BobaEquipmentProbe.java
mkdir -p "$probe_dir/classes/net/mandomc/gameplay"
cp -R plugin/target/classes/net/mandomc/gameplay/boba "$probe_dir/classes/net/mandomc/gameplay/"
cp -R plugin/target/classes/net/mandomc/gameplay/ui "$probe_dir/classes/net/mandomc/gameplay/"
mkdir -p "$probe_dir/classes/net/mandomc/commands"
cp plugin/target/classes/net/mandomc/commands/RootCommand*.class "$probe_dir/classes/net/mandomc/commands/"
cp -R plugin/target/classes/net/mandomc/gameplay/items "$probe_dir/classes/net/mandomc/gameplay/"
mkdir -p "$probe_dir/classes/net/mandomc/integrations/mobs"
cp plugin/target/classes/net/mandomc/integrations/mobs/JabbaDrops.class "$probe_dir/classes/net/mandomc/integrations/mobs/"
mkdir -p "$probe_dir/classes/net/mandomc/gameplay/mobs"
cp plugin/target/classes/net/mandomc/gameplay/mobs/JabbaDropsSettings*.class "$probe_dir/classes/net/mandomc/gameplay/mobs/"
mkdir -p "$probe_dir/classes/equipment"
cp plugin/src/main/resources/equipment/boba.yml "$probe_dir/classes/equipment/"
cp plugin/src/main/resources/messages.yml "$probe_dir/classes/"
cp plugin/src/main/resources/items.yml "$probe_dir/classes/"
cp plugin/src/main/resources/jabba-mobs.yml "$probe_dir/classes/"
mkdir -p "$probe_dir/classes/items"
cp plugin/src/main/resources/items/boba-equipment.yml "$probe_dir/classes/items/"
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: BobaEquipmentProbe
version: '1'
main: net.mandomc.verification.BobaEquipmentProbe
api-version: '26.1.2'
depend: [MythicMobs]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/BobaEquipmentProbe.jar" -C "$probe_dir/classes" .
