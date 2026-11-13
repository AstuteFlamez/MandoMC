#!/usr/bin/env python3
"""Add only the Bantha Hide utility recipes to a SHA-pinned live JAR and runtime config."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline', type=Path, required=True)
p.add_argument('--expected-sha256', required=True)
p.add_argument('--runtime-recipes', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
assert hashlib.sha256(a.baseline.read_bytes()).hexdigest() == a.expected_sha256, 'Baseline changed'
source = json.loads((ROOT / 'plugin/src/main/resources/items/recipes.yml').read_text())
additions = {key: value for key, value in source['recipes'].items() if key.startswith('bantha_')}
assert len(additions) == 20 and all(value['category'] == 'utilities' for value in additions.values())
natives = ['minecraft:' + ('leather' if key == 'bantha_hide_from_rabbit_hide' else key.removeprefix('bantha_'))
           for key in additions]


def patch(data):
    config = json.loads(data)
    for key, value in additions.items():
        assert key not in config['recipes'] or config['recipes'][key] == value, f'Conflicting recipe: {key}'
        config['recipes'][key] = value
    for recipe in additions.values():
        ingredients = recipe['ingredients']
        for key in ingredients.values() if isinstance(ingredients, dict) else ingredients:
            if key in source['sources']:
                config['sources'].setdefault(key, source['sources'][key])
    config['replace-native'] += [key for key in natives if key not in config['replace-native']]
    return (json.dumps(config, indent=2, ensure_ascii=False) + '\n').encode()


a.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(a.baseline) as before, zipfile.ZipFile(a.output, 'w', zipfile.ZIP_DEFLATED) as after:
    for info in before.infolist():
        data = before.read(info.filename)
        after.writestr(info, patch(data) if info.filename == 'items/recipes.yml' else data)
with zipfile.ZipFile(a.baseline) as before, zipfile.ZipFile(a.output) as after:
    assert before.namelist() == after.namelist()
    changed = [key for key in before.namelist() if before.read(key) != after.read(key)]
    assert changed == ['items/recipes.yml'], changed
runtime = a.output.with_suffix('.recipes.json')
runtime.write_bytes(patch(a.runtime_recipes.read_bytes()))
manifest = {'baselineSha256': a.expected_sha256,
            'outputSha256': hashlib.sha256(a.output.read_bytes()).hexdigest(), 'changedEntries': changed,
            'nativeReplacements': natives,
            'runtimeBeforeSha256': hashlib.sha256(a.runtime_recipes.read_bytes()).hexdigest(),
            'runtimeAfterSha256': hashlib.sha256(runtime.read_bytes()).hexdigest()}
a.output.with_suffix('.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps(manifest, indent=2))
