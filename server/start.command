#!/usr/bin/env bash
# One persistent console view; managed startup/restart never needs an Enter confirmation.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/server-control.py start
exec python3 scripts/server-control.py console
