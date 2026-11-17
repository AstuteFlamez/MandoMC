#!/usr/bin/env python3
"""Pin a read-only public gameplay allowlist to the exact clean source used by this build."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / '.local' / 'build-info' / 'guide-repository.json'
RESOURCE_AREAS = {'arrival', 'clans', 'combat', 'crates', 'economy', 'equipment', 'events', 'items',
                  'leaderboards', 'mobs', 'parkour', 'rules', 'territory', 'travel', 'trophies', 'tutorial',
                  'vehicles', 'worlds'}
CODE_AREAS = {'arrival', 'auction', 'boba', 'credits', 'dungeon', 'explosions', 'leaderboards',
              'messaging', 'territory', 'trophies', 'clans', 'combat', 'crates', 'events', 'herd', 'ilum', 'items', 'jawa', 'mining', 'mobs',
              'progression', 'rules', 'saber', 'travel', 'tutorial', 'vehicles', 'worlds'}
DOCS = {'docs/commands.md', 'docs/player-experience.md'}


def approved(path):
    parts = Path(path).parts
    if path.startswith(('/', '.')) or '..' in parts or '\\' in path:
        return False
    if path in DOCS:
        return True
    if path.startswith('docs/features/') and path.endswith('.md'):
        return not any(word in Path(path).name for word in ('maintenance', 'security', 'deployment', 'dev-'))
    if (path.startswith('plugin/src/main/resources/') or path.startswith('server/config/common/mandomc/')) and path.endswith('.yml'):
        return len(parts) > 5 and parts[4] in RESOURCE_AREAS
    if path.startswith('plugin/src/main/java/net/mandomc/gameplay/') and path.endswith('.java'):
        return len(parts) > 8 and parts[7] in CODE_AREAS
    return False


def manifest(root=ROOT):
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    clean = not subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=normal'], cwd=root)
    paths = subprocess.check_output(['git', 'ls-files'], cwd=root, text=True).splitlines()
    sources = []
    for name in sorted(filter(approved, paths)):
        file = root / name
        if file.is_symlink():
            raise ValueError('Knowledge source must be a plain tracked file: ' + name)
        raw = file.read_bytes()
        if len(raw) > 131072:
            raise ValueError('Knowledge source exceeds 128 KiB: ' + name)
        sources.append({'path': name, 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)})
    if not sources or len(sources) > 512 or sum(row['bytes'] for row in sources) > 8388608:
        raise ValueError('Public gameplay knowledge exceeds bounded manifest size')
    return {'schemaVersion': 1, 'repository': 'AstuteFlamez/MandoMC', 'commit': commit, 'clean': clean,
            'sources': sources}


if __name__ == '__main__':
    data = manifest()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(data, separators=(',', ':')) + '\n')
    print(f"Pinned {len(data['sources'])} public gameplay sources at {data['commit'][:12]} "
          f"({'clean' if data['clean'] else 'modified; GitHub retrieval disabled'}).")
