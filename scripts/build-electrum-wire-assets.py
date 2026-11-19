#!/usr/bin/env python3
"""Build the Electrum slot hint and gold wire palette from the pinned client sprites."""
import argparse
import io
import json
from pathlib import Path
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'resource-pack/src/assets'
PARTICLE_MARKER = (219, 11)
WIRE_TINT = 'vec3(1.0, 0.72, 0.24)'
WIRE_LEVELS = {
    'idle': ('0', (133, 88, 29)),
    'low': ('1|2|3|4|5', (184, 122, 38)),
    'medium': ('6|7|8|9|10', (226, 161, 52)),
    'high': ('11|12|13|14|15', (255, 199, 72)),
}
CIRCUIT_SPRITES = {
    'block': ('redstone_lamp', 'redstone_lamp_on', 'redstone_torch',
              'redstone_torch_off', 'repeater', 'repeater_on', 'comparator', 'comparator_on',
              'observer_back', 'observer_back_on', 'target_side', 'target_top',
              'powered_rail', 'powered_rail_on', 'detector_rail', 'detector_rail_on',
              'activator_rail', 'activator_rail_on'),
    'item': ('repeater', 'comparator'),
    'trims/color_palettes': ('redstone',),
}


def write_asset(path, data):
    output = ASSETS / path
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)


def write_sprite(path, sprite):
    output = io.BytesIO()
    sprite.save(output, format='PNG')
    write_asset(path, output.getvalue())


def marked(sprite, marker):
    # Grayscale brightness stays in red; the other two channels identify the individual sprite on the atlas.
    result = sprite.copy()
    result.putdata([(r, *marker, a) for r, _, _, a in sprite.get_flattened_data()])
    return result


def mark_expression(sample, marker):
    values = ', '.join(f'{value:.1f} / 255.0' for value in marker)
    return f'all(lessThan(abs({sample}.gb - vec2({values})), vec2(2.0 / 255.0)))'


def replace_line(shader, needle, replacement):
    if shader.count(needle) != 1:
        raise ValueError('Pinned Minecraft shader changed; review the palette patch for this version')
    return shader.replace(needle, replacement)


def main(client):
    dust_path = ASSETS / 'mandomc/textures/item/legacy/metals/scraps/electrum_dust.png'
    dust = Image.open(dust_path).convert('RGBA')
    assert dust.size == (32, 32)
    placeholder = dust.copy()
    values = []
    for r, g, b, a in dust.get_flattened_data():
        shade = round(65 + (0.299 * r + 0.587 * g + 0.114 * b) * 0.42)
        values.append((shade, shade, shade, a))
    placeholder.putdata(values)
    write_sprite('minecraft/textures/gui/sprites/container/slot/lapis_lazuli.png', placeholder)

    def sprite(path):
        return Image.open(io.BytesIO(client.read('assets/' + path))).convert('RGBA')

    def gold(sprite_image, palette=None, all_pixels=False):
        result = sprite_image.copy()
        pixels = []
        for r, g, b, a in sprite_image.get_flattened_data():
            red = r > 1.2 * g and r > 1.3 * b and r - b >= 18
            if palette is not None:
                strength = max(r, g, b) / 255
                pixels.append((*[round(channel * strength) for channel in palette], a))
            elif red or all_pixels:
                pixels.append((r, round(r * .72), round(r * .24), a))
            else:
                pixels.append((r, g, b, a))
        result.putdata(pixels)
        return result

    for folder, names in CIRCUIT_SPRITES.items():
        for name in names:
            path = f'minecraft/textures/{folder}/{name}.png'
            texture = gold(sprite(path), all_pixels=folder == 'trims/color_palettes')
            if folder != 'trims/color_palettes':
                texture = texture.resize((32, 32), Image.Resampling.NEAREST)
            write_sprite(path, texture)

    def write_json(path, value):
        write_asset(path, (json.dumps(value, indent=2) + '\n').encode())

    write_json('minecraft/items/redstone.json', json.loads(
        (ASSETS / 'mandomc/items/legacy/electrum_dust.json').read_text()))

    wire_models = ('dot', 'side', 'side0', 'side1', 'side_alt', 'side_alt0', 'side_alt1', 'up')
    original_wire = json.loads(client.read('assets/minecraft/blockstates/redstone_wire.json'))
    multipart = []
    for level, (powers, palette) in WIRE_LEVELS.items():
        texture_prefix = f'mandomc:block/circuits/wire/{level}'
        model_prefix = f'mandomc:block/circuits/wire/{level}'
        for shape in ('dot', 'line0', 'line1', 'overlay'):
            origin = f'minecraft/textures/block/redstone_dust_{shape}.png'
            output = f'mandomc/textures/block/circuits/wire/{level}/{shape}.png'
            write_sprite(output, gold(sprite(origin), palette).resize((32, 32), Image.Resampling.NEAREST))
            write_asset(output + '.mcmeta', client.read('assets/' + origin + '.mcmeta'))
        for model in wire_models:
            source = json.loads(client.read(f'assets/minecraft/models/block/redstone_dust_{model}.json'))
            if 'parent' in source:
                base = source['parent'].rsplit('redstone_dust_',1)[1]
                source['parent'] = f'{model_prefix}/{base}'
            for name, texture in source['textures'].items():
                value = texture['sprite'] if isinstance(texture, dict) else texture
                shape = value.rsplit('redstone_dust_',1)[1]
                replacement = f'{texture_prefix}/{shape}'
                if isinstance(texture, dict):
                    texture['sprite'] = replacement
                else:
                    source['textures'][name] = replacement
            if model in ('side', 'side_alt'):
                source['textures']['line'] = {'sprite': f'{texture_prefix}/line0', 'force_translucent': True}
            for element in source.get('elements', []):
                for face in element['faces'].values():
                    face.pop('tintindex', None)
            write_json(f'mandomc/models/block/circuits/wire/{level}/{model}.json', source)
        for part in original_wire['multipart']:
            model = part['apply']['model'].rsplit('redstone_dust_',1)[1]
            variant = {**part['apply'], 'model': f'{model_prefix}/{model}'}
            multipart.append({'when': {'AND': [{'power': powers}, part['when']]}, 'apply': variant})
    write_json('minecraft/blockstates/redstone_wire.json', {'multipart': multipart})

    textures = []
    for frame in reversed(range(8)):
        source = sprite(f'minecraft/textures/particle/generic_{frame}.png')
        name = f'electrum_wire_dust_{frame}'
        texture = marked(source, PARTICLE_MARKER).resize((32, 32), Image.Resampling.NEAREST)
        write_sprite(f'mandomc/textures/particle/{name}.png', texture)
        textures.append(f'mandomc:{name}')
    write_asset('minecraft/particles/dust.json', (json.dumps({'textures': textures}, indent=2) + '\n').encode())

    particle_path = 'minecraft/shaders/core/particle.fsh'
    particle = client.read('assets/' + particle_path).decode()
    particle = replace_line(particle, '    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;', f'''    vec4 sprite = texture(Sampler0, texCoord0);
    vec4 tint = vertexColor;
    if ({mark_expression('sprite', PARTICLE_MARKER)}) {{
        sprite.rgb = vec3(sprite.r);
        // Native wire sparks use the red power ramp (zero blue, green at the highest levels).
        if (tint.b < 0.004 && tint.g <= tint.r * 0.23 + 0.004) {{
            tint.rgb = tint.r * {WIRE_TINT};
        }}
    }}
    vec4 color = sprite * tint * ColorModulator;''')
    write_asset(particle_path, particle.encode())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('client_jar', type=Path, help='Minecraft Java 26.1.2 client JAR')
    args = parser.parse_args()
    with zipfile.ZipFile(args.client_jar) as client:
        version = json.loads(client.read('version.json'))
        if version['id'] != '26.1.2':
            parser.error('the wire palettes require the 26.1.2 client')
        main(client)
