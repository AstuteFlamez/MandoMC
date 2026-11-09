#!/usr/bin/env python3
"""Generate references and gameplay metadata, never redistribute upstream artwork.

Usage: python3 scripts/content/import-sabers.py /path/to/JustTimm-3.2.2.zip
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
STABLE_IDS = {
    'obi-wan_kenobi_jedi_master_lightsaber': 'obiwan',
    'cal_kestis_survivor_crossguard_lightsaber': 'cal_survivor_crossguard',
    'mace_windu_lightsaber': 'windu', 'qui-gon_jinn_lightsaber': 'quigon',
    'asajj_ventress_yellow_lightsaber': 'ventress_yellow',
}
LEGENDARY = ('darth_vader', 'anakin_skywalker', 'obi-wan_kenobi', 'luke_skywalker',
             'yoda', 'mace_windu', 'revan', 'darth_maul', 'ahsoka_tano', 'dark_saber')
EPIC = ('cal_kestis', 'qui-gon_jinn', 'count_dooku', 'asajj_ventress', 'kylo_ren',
        'qimir', 'baylan_skoll', 'quinlan_vos', 'caleb_dume', 'ezra_bridger', 'cere_junda')
UNCOMMON = ('devon_izara', 'torbin', 'tak_rennod', 'jod_na_nawood', 'wim_discovered')


def main():
    archive = Path(sys.argv[1])
    dependency = ROOT / 'resource-pack/dependencies.json'
    deps = json.loads(dependency.read_text())
    pack = deps['packs'][0]
    if hashlib.sha1(archive.read_bytes()).hexdigest() != pack['sha1']:
        raise SystemExit('Expected the pinned, unmodified JustTimm 3.2.2 archive')
    z = zipfile.ZipFile(archive)
    shield = json.loads(z.read('1.21.9/assets/minecraft/items/shield.json'))
    # Use upstream's actual held-item selections, excluding aliases and animation helpers.
    sections = {}
    by_title = {}

    def walk(value):
        if isinstance(value, dict):
            if value.get('component') == 'minecraft:custom_name' and 'cases' in value:
                for case in value['cases']:
                    model = case.get('model', {}).get('model', '')
                    if model.startswith('workshop:item/'):
                        sections.setdefault(model, case['when'])
                        by_title.setdefault(case['when'], []).append(model)
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(shield)

    def resolve(model):
        data = json.loads(z.read('assets/' + model.replace(':', '/models/') + '.json'))
        inherited = resolve(data['parent']) if 'parent' in data else {}
        inherited.update(data)
        return inherited

    items = ['schema-version: 1', 'items:']
    variants = []
    manifest = []
    refs = set()
    for model, title in sorted(sections.items()):
        if model.endswith(('_hilt', '_blocking')):
            continue
        slug = model.split('/')[-1]
        item_id = 'vader' if slug == 'darth_vader_lightsaber' else STABLE_IDS.get(slug, slug.replace('-', '_'))
        # Hilt and blocking selections sometimes intentionally reuse a related saber.
        hilt = next(m for m in by_title[title] if m.endswith('_hilt'))
        blocking = next(m for m in by_title[title] if m.endswith('_blocking'))
        data = resolve(model)
        fixed = data['display']['fixed']
        rotation = fixed.get('rotation', [0, 0, 0])
        assert rotation in ([0, -90, 0], [0, 0, 0]), (slug, rotation)
        elements = [e for e in data['elements']
                    if any('blade' in face['texture'] for face in e.get('faces', {}).values())]
        low = [min(min(e['from'][i], e['to'][i]) for e in elements) for i in range(3)]
        high = [max(max(e['from'][i], e['to'][i]) for e in elements) for i in range(3)]
        scale = fixed.get('scale', [1, 1, 1])
        center = [((low[i] + high[i]) / 2 - 8) * scale[i] / 16 for i in range(3)]
        if rotation[1] == -90:
            center = [-center[2], center[1], center[0]]
        center = [round(center[i] + fixed.get('translation', [0, 0, 0])[i] / 16, 8) for i in range(3)]
        half = round((high[1] - low[1]) * scale[1] / 32, 8)
        rarity = ('common' if 'training' in slug else 'legendary' if slug.startswith(LEGENDARY)
                  else 'epic' if slug.startswith(EPIC) else 'uncommon' if slug.startswith(UNCOMMON) else 'rare')
        color = data.get('textures', {}).get('blade_color', '').split('/')[-1]
        color = {'blue': 'blue', 'green': 'green', 'red': 'red', 'purple': 'light_purple',
                 'white': 'white', 'yellow': 'yellow', 'orange': 'gold', 'orange_red': 'gold'}.get(color, 'white')
        item_model = 'mandomc:sabers/' + item_id
        wrapper = {'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                   'cases': [{'when': ['gui', 'ground', 'on_shelf'],
                              'model': {'type': 'minecraft:model', 'model': hilt}}],
                   'fallback': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                                'on_true': {'type': 'minecraft:model', 'model': blocking},
                                'on_false': {'type': 'minecraft:model', 'model': model}}}}
        output = ROOT / 'resource-pack/src/assets/mandomc/items/sabers' / (item_id + '.json')
        output.write_text(json.dumps(wrapper, indent=2) + '\n')
        # Existing Vader hilt entry remains valid for backwards compatibility.
        items += [f'  {item_id}:', '    material: SHIELD', f'    name: {json.dumps("<" + color + "><bold:false>" + title)}',
                  f'    custom-model-data: {11 if item_id == "vader" else 1000 + len(manifest)}',
                  f'    item-model: {item_model}', f'    rarity: {rarity}', '    category: weapons',
                  '    attack-damage: 10.5', '    attack-speed: 1.0', '    lore:',
                  f'      - {json.dumps("<gray>" + title + ".")}', "      - ''",
                  "      - '<yellow>[<key:key.attack>] Attack'",
                  "      - '<yellow>[<key:key.use>] Block / Reflect'",
                  "      - '<yellow>[<key:key.sneak> + <key:key.attack>] Throw'"]
        variants += [f'  {item_id}:', f'    item-model: {item_model}', f'    blade-center: {json.dumps(center)}',
                     f'    blade-half-length: {half}']
        manifest.append({'id': item_id, 'name': title, 'rarity': rarity, 'held': model,
                         'hilt': hilt, 'blocking': blocking, 'bladeCenter': center, 'bladeHalfLength': half})
        refs.update((model, hilt, blocking))
    (ROOT / 'plugin/src/main/resources/items/definitions/sabers.yml').write_text('\n'.join(items) + '\n')
    config = ROOT / 'plugin/src/main/resources/combat/sabers.yml'
    base = config.read_text().split('\nvariants:')[0].rstrip()
    config.write_text(base + '\n# Explicit catalog identities; all variants share player-owned charge and tuning.\nvariants:\n' + '\n'.join(variants) + '\n')
    pack['models'] = sorted(refs)
    dependency.write_text(json.dumps(deps, indent=2) + '\n')
    (ROOT / 'resource-pack/sabers.json').write_text(json.dumps({'upstreamVersion': pack['version'], 'sabers': manifest}, indent=2) + '\n')
    print(f'Generated {len(manifest)} named sabers and {len(refs)} upstream model references.')


if __name__ == '__main__':
    main()
