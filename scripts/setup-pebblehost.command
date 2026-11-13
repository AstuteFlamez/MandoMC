#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -x .local/tools/bin/python ]]; then
    python3 -m venv .local/tools
fi
.local/tools/bin/python -m pip install -r deployment/requirements.txt
.local/tools/bin/python scripts/pebblehost.py configure
read -r -p "Setup finished. Press Enter to close. "
