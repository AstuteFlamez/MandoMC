#!/usr/bin/env python3
"""Keep feature work on dev and production promotions on dev/hotfix."""
import json
import os
from pathlib import Path

event_file = os.environ.get('GITHUB_EVENT_PATH')
if event_file and os.environ.get('GITHUB_EVENT_NAME') == 'pull_request':
    request = json.loads(Path(event_file).read_text())['pull_request']
    source = request['head']['ref']
    target = request['base']['ref']
    if target == 'main' and (source not in ('dev', 'hotfix') or
                             request['head']['repo']['full_name'] != os.environ['GITHUB_REPOSITORY']):
        raise SystemExit('Production PRs must come from this repository’s dev or hotfix branch.')
    print(f'Branch policy passed: {source} -> {target}')
