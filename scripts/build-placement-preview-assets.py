#!/usr/bin/env python3
"""Build two translucent variants of a placed item model, preserving its geometry and UVs."""
import argparse
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / 'resource-pack/src/assets/mandomc'
COLORS = {'blue': ((8, 36, 108), (85, 192, 255)), 'red': ((89, 9, 17), (255, 100, 91))}


def resource(folder, key, suffix):
    namespace, path = key.split(':')
    if namespace != 'mandomc' or '..' in Path(path).parts or Path(path).is_absolute():
        raise ValueError(f'Expected a local MandoMC asset, got {key}')
    return ASSETS / folder / (path + suffix)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def generate(item_key):
    route = json.loads(resource('items', item_key, '.json').read_text())['model']
    if route['type'] != 'minecraft:model':
        raise ValueError(f'{item_key} must resolve to a placed model')
    parent = route['model']
    textures = json.loads(resource('models', parent, '.json').read_text())['textures']
    for color, (dark, light) in COLORS.items():
        key = 'mandomc:placement/' + color + '/' + item_key.split(':')[1]
        preview_model = key.replace('mandomc:', 'mandomc:item/')
        overrides = {}
        for slot, source in textures.items():
            if slot == 'particle':
                continue
            sprite = source['sprite'] if isinstance(source, dict) else source
            if sprite.startswith('#'):
                overrides[slot] = sprite
                continue
            texture = preview_model if slot == '0' else preview_model + '_' + slot
            with Image.open(resource('textures', sprite, '.png')) as image:
                image = image.convert('RGBA')
            pixels = []
            for r, g, b, alpha in image.get_flattened_data():
                luminance = (r * .299 + g * .587 + b * .114) / 255
                rgb = (round(lo + (hi - lo) * luminance) for lo, hi in zip(dark, light))
                pixels.append((*rgb, round(alpha * .48)))
            image.putdata(pixels)
            dest = resource('textures', texture, '.png')
            dest.parent.mkdir(parents=True, exist_ok=True)
            image.save(dest)
            overrides[slot] = {'sprite': texture, 'force_translucent': True}
        overrides['particle'] = '#' + next(iter(overrides))
        write_json(resource('models', preview_model, '.json'), {'parent': parent, 'textures': overrides})
        write_json(resource('items', key, '.json'), {'model': {'type': 'minecraft:model', 'model': preview_model}})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('placed_models', nargs='+', help='Namespaced item-model keys used by the placed block')
    for model in parser.parse_args().placed_models:
        generate(model)
