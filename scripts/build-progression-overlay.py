#!/usr/bin/env python3
"""Build an immutable progression overlay; preserve newer concurrent gameplay and native vehicle ammo."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline', type=Path, required=True)
parser.add_argument('--expected-sha256', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
assert hashlib.sha256(args.baseline.read_bytes()).hexdigest() == args.expected_sha256, 'Baseline changed'
classes = {'net/mandomc/gameplay/items/' + name for name in
           ('CraftingRecipes', 'RecipeDefinition', 'RecipeGuide', 'OreDrops', 'MetalMaterials')}
classes |= {'net/mandomc/gameplay/vehicles/' + name for name in ('VehicleCannons', 'VehicleWeaponSettings')}
classes |= {'net/mandomc/gameplay/mobs/' + name for name in ('SandySpawns', 'SandySettings', 'EncounterPlan', 'PatrolState')}
classes |= {'net/mandomc/gameplay/rules/' + name for name in ('SurvivalRules',)}
classes |= {'net/mandomc/gameplay/credits/CreditService', 'net/mandomc/gameplay/credits/CreditLedger', 'net/mandomc/gameplay/auction/AuctionBook', 'net/mandomc/gameplay/auction/AuctionHouse', 'net/mandomc/commands/ItemCommand'}
resources = {'items/recipes.yml', 'items/definitions/legacy.yml', 'items/definitions/jabba.yml',
             'items/definitions/jabba-loot.yml', 'rules/survival.yml', 'vehicles/weapons.yml', 'mobs/spawning.yml', 'interface/hud.yml', 'economy/credits.yml', 'economy/auction-house.yml', 'language/messages.yml'}
with zipfile.ZipFile(args.baseline) as original, zipfile.ZipFile(ROOT / 'plugin/target/MandoMC.jar') as compiled:
    replacements = resources | {'plugin.yml'} | {name for name in compiled.namelist() if name.endswith('.class')
        and any(name == cls + '.class' or name.startswith(cls + '$') for cls in classes)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as output:
        for name in original.namelist():
            if name not in replacements:
                output.writestr(name, original.read(name))
        for name in sorted(replacements):
            if name == 'plugin.yml':
                descriptor = json.loads(subprocess.check_output(['ruby', '-ryaml', '-rjson', '-e',
                    'puts JSON.generate(YAML.load(STDIN.read))'], input=original.read(name)))
                source = json.loads(subprocess.check_output(['ruby', '-ryaml', '-rjson', '-e',
                    'puts JSON.generate(YAML.load(STDIN.read))'], input=compiled.read(name)))
                descriptor.setdefault('commands', {})['auctionhouse'] = source['commands']['auctionhouse']
                descriptor['commands']['mandomc']['usage'] = source['commands']['mandomc']['usage']
                descriptor.setdefault('permissions', {})['mandomc.auction'] = source['permissions']['mandomc.auction']
                output.writestr(name, json.dumps(descriptor, indent=2) + '\n')
            elif name == 'language/messages.yml':
                merged = json.loads(subprocess.check_output(['ruby', '-ryaml', '-rjson', '-e',
                    'puts JSON.generate(YAML.load(STDIN.read))'], input=original.read(name)))
                source = json.loads(subprocess.check_output(['ruby', '-ryaml', '-rjson', '-e',
                    'puts JSON.generate(YAML.load(STDIN.read))'], input=compiled.read(name)))
                for key in ('quantity', 'usage', 'console-item-required'):
                    merged['items'][key] = source['items'][key]
                output.writestr(name, json.dumps(merged, indent=2) + '\n')
            else:
                output.writestr(name, compiled.read(name))
with zipfile.ZipFile(args.baseline) as original, zipfile.ZipFile(args.output) as output:
    assert all(output.read(name) == original.read(name) for name in original.namelist() if name not in replacements)
manifest = {'baselineSha256': args.expected_sha256,
            'outputSha256': hashlib.sha256(args.output.read_bytes()).hexdigest(), 'replacedEntries': sorted(replacements)}
args.output.with_suffix('.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Built progression overlay preserving all unrelated entries: ' + manifest['outputSha256'])
