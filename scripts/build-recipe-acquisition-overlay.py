#!/usr/bin/env python3
"""Overlay visible-ingredient source notes and RecipeGuide onto a pinned live JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline', required=True, type=Path)
p.add_argument('--expected-sha256', required=True)
p.add_argument('--runtime-recipes', required=True, type=Path)
p.add_argument('--classes', required=True, type=Path)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
assert hashlib.sha256(a.baseline.read_bytes()).hexdigest() == a.expected_sha256, 'Baseline changed'
source = json.loads((ROOT / 'plugin/src/main/resources/items/recipes.yml').read_text())
with zipfile.ZipFile(a.baseline) as before:
    baseline = json.loads(before.read('items/recipes.yml'))
    inputs = set()
    for kind in ('recipes', 'smelting', 'stonecutting'):
        for key, recipe in baseline.get(kind, {}).items():
            if key in baseline['guide'].get('hidden-recipes', []) or recipe['result'] in baseline['guide'].get('hidden-results', []):
                continue
            ingredients = recipe.get('ingredients', {'I': recipe.get('input')})
            inputs.update(ingredients.values() if isinstance(ingredients, dict) else ingredients)
    inputs.discard(None)
    notes = {key: source['sources'][key] for key in sorted(inputs)}
    assert all(value.strip() for value in notes.values())

    def patch(data):
        config = json.loads(data)
        config['sources'].update(notes)
        return (json.dumps(config, indent=2, ensure_ascii=False) + '\n').encode()

    prefix = 'net/mandomc/gameplay/items/'
    replacements = {'items/recipes.yml': patch(before.read('items/recipes.yml'))}
    for name in ('RecipeGuide.class', 'RecipeGuide$Menu.class'):
        replacements[prefix + name] = (a.classes / prefix / name).read_bytes()
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.output, 'w', zipfile.ZIP_DEFLATED) as after:
        for info in before.infolist():
            after.writestr(info, replacements.get(info.filename, before.read(info.filename)))
with zipfile.ZipFile(a.baseline) as before, zipfile.ZipFile(a.output) as after:
    assert before.namelist() == after.namelist()
    changed = [key for key in before.namelist() if before.read(key) != after.read(key)]
    assert set(changed) <= set(replacements)
    old = json.loads(before.read('items/recipes.yml'))
    new = json.loads(after.read('items/recipes.yml'))
    assert all(new[key] == value for key, value in old.items() if key != 'sources')
a.output.with_suffix('.recipes.json').write_bytes(patch(a.runtime_recipes.read_bytes()))
a.output.with_suffix('.json').write_text(json.dumps({
    'baselineSha256': a.expected_sha256,
    'outputSha256': hashlib.sha256(a.output.read_bytes()).hexdigest(),
    'changedEntries': changed, 'sourceIds': sorted(inputs),
    'runtimeBeforeSha256': hashlib.sha256(a.runtime_recipes.read_bytes()).hexdigest(),
}, indent=2) + '\n')
print('Changed entries:', changed, '; visible ingredient notes:', len(notes))
print('Output SHA-256:', hashlib.sha256(a.output.read_bytes()).hexdigest())
