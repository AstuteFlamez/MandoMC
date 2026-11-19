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
WIRE_MARKER = (231, 7)
PARTICLE_MARKER = (219, 11)
WIRE_TINT = 'vec3(1.0, 0.72, 0.24)'


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

    for part in ('dot', 'line0', 'line1'):
        path = f'minecraft/textures/block/redstone_dust_{part}.png'
        write_sprite(path, marked(sprite(path), WIRE_MARKER))

    textures = []
    for frame in reversed(range(8)):
        source = sprite(f'minecraft/textures/particle/generic_{frame}.png')
        name = f'electrum_wire_dust_{frame}'
        write_sprite(f'mandomc/textures/particle/{name}.png', marked(source, PARTICLE_MARKER))
        textures.append(f'mandomc:{name}')
    write_asset('minecraft/particles/dust.json', (json.dumps({'textures': textures}, indent=2) + '\n').encode())

    terrain_path = 'minecraft/shaders/core/terrain.fsh'
    terrain = client.read('assets/' + terrain_path).decode()
    line = next(line for line in terrain.splitlines() if 'vec4 color = (UseRgss' in line)
    sample = line.replace('vec4 color = ', 'vec4 sprite = ').removesuffix(' * vertexColor;') + ';'
    terrain = replace_line(terrain, line, f'''{sample}
    vec4 color = sprite * vertexColor;
    if ({mark_expression('sprite', WIRE_MARKER)}) {{
        color.rgb = sprite.r * vertexColor.r * {WIRE_TINT};
    }}''')
    write_asset(terrain_path, terrain.encode())

    particle_path = 'minecraft/shaders/core/particle.fsh'
    particle = client.read('assets/' + particle_path).decode()
    particle = replace_line(particle, '    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;', f'''    vec4 sprite = texture(Sampler0, texCoord0);
    vec4 tint = vertexColor;
    if ({mark_expression('sprite', WIRE_MARKER)}) {{
        sprite.rgb = vec3(sprite.r);
        tint.rgb = tint.r * {WIRE_TINT};
    }} else if ({mark_expression('sprite', PARTICLE_MARKER)}) {{
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
