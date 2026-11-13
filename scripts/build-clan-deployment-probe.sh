#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
cd "$MANDOMC_ROOT"
probe_dir="$MANDOMC_ROOT/.local/clan-deployment-probe"
mkdir -p "$probe_dir/classes"
"$JAVA_HOME/bin/javac" --release 25 -cp "plugin/target/classes:$(cat .local/clan-verification/classpath.txt)" \
  -d "$probe_dir/classes" scripts/integration/ClanDeploymentProbe.java
cat > "$probe_dir/classes/plugin.yml" <<'YAML'
name: ClanDeploymentProbe
version: '1'
main: net.mandomc.verification.ClanDeploymentProbe
api-version: '26.1.2'
depend: [MandoMC]
YAML
"$JAVA_HOME/bin/jar" --create --file "$probe_dir/ClanDeploymentProbe.jar" -C "$probe_dir/classes" .
