#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
export MAVEN_USER_HOME="$MANDOMC_ROOT/.local/maven"
cd "$MANDOMC_ROOT"
python3 scripts/build-guide-knowledge.py
./mvnw --batch-mode --no-transfer-progress \
    "-Dmaven.repo.local=$MANDOMC_ROOT/.local/maven-repository" verify "$@"
bash "$MANDOMC_ROOT/scripts/build-redstone-agent.sh"
python3 scripts/check-guide-knowledge.py
