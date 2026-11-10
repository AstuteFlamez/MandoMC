#!/usr/bin/env python3
"""Extend the pinned Faithful metal pipeline with native iron/Agrinium and redstone/Electrum aliases."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'resource-pack/src/assets'
spec = importlib.util.spec_from_file_location('metal_blocks', Path(__file__).with_name('build-metal-block-textures.py'))
metal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metal)


def main():
    archive = Path(sys.argv[1])
    assert hashlib.sha1(archive.read_bytes()).hexdigest() == '9447e84ee7121c86304e467a0f8d5b095872e1e6'
    records = []
    with zipfile.ZipFile(archive) as faithful:
        palettes = {name: Image.open(ASSETS / f'mandomc/textures/item/legacy/metals/ingots/{name}.png')
                    for name in ('agrinium', 'electrum')}

        def write(target, source, name, ore=False, soften=False):
            data = faithful.read(source)
            image = Image.open(io.BytesIO(data))
            result, pixels = metal.tint(image, palettes[name], ore, soften)
            path = ASSETS / target
            path.parent.mkdir(parents=True, exist_ok=True)
            result.save(path)
            records.append({'target': target, 'source': source, 'sourceSha256': hashlib.sha256(data).hexdigest(),
                            'palette': name, 'oreMask': ore, 'softStoneShadows': soften, 'recoloredPixels': pixels,
                            'outputSha256': hashlib.sha256(path.read_bytes()).hexdigest()})

        for name, native in [('agrinium', 'iron')]:
            for rock, template in [('', 'diamond_ore'), ('deepslate_', 'deepslate_diamond_ore')]:
                write(f'minecraft/textures/block/{rock}{native}_ore.png',
                      f'assets/minecraft/textures/block/{template}.png', name, True, not rock)
            for texture in (f'{native}_block', f'raw_{native}_block'):
                write(f'minecraft/textures/block/{texture}.png', f'assets/minecraft/textures/block/{texture}.png', name)
            # Reuse the accepted 32px scrap/ingot geometry and palette, with no redraw.
            for target, part in [(f'{native}_ingot', f'ingots/{name}'), (f'raw_{native}', f'scraps/{name}_scrap')]:
                source = ASSETS / f'mandomc/textures/item/legacy/metals/{part}.png'
                dest = ASSETS / f'minecraft/textures/item/{target}.png'
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(source.read_bytes())
                records.append({'target': str(dest.relative_to(ASSETS)), 'localSource': str(source.relative_to(ASSETS)),
                                'outputSha256': hashlib.sha256(dest.read_bytes()).hexdigest()})
            write(f'minecraft/textures/item/{native}_nugget.png', f'assets/minecraft/textures/item/{native}_nugget.png', name)

        for rock, template in [('', 'diamond_ore'), ('deepslate_', 'deepslate_diamond_ore')]:
            write(f'minecraft/textures/block/{rock}redstone_ore.png',
                  f'assets/minecraft/textures/block/{template}.png', 'electrum', True, not rock)
        write('minecraft/textures/block/redstone_block.png',
              'assets/minecraft/textures/block/redstone_block.png', 'electrum')
        source = ASSETS / 'mandomc/textures/item/legacy/metals/ingots/electrum.png'
        dest = ASSETS / 'minecraft/textures/item/redstone.png'
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(source.read_bytes())
        records.append({'target': str(dest.relative_to(ASSETS)), 'localSource': str(source.relative_to(ASSETS)),
                        'outputSha256': hashlib.sha256(dest.read_bytes()).hexdigest()})

    # Recolor only the blue metal head; preserve each wooden handle and all transparency exactly.
    for kind in ('pickaxe', 'shovel'):
        source = ASSETS / f'mandomc/textures/item/legacy/tools/durasteel_{kind}.png'
        image = Image.open(source).convert('RGBA')
        import colorsys
        mask = [pixel[3] > 0 and 0.45 <= colorsys.rgb_to_hsv(*(v / 255 for v in pixel[:3]))[0] <= 0.75
                and colorsys.rgb_to_hsv(*(v / 255 for v in pixel[:3]))[1] > 0.15
                for pixel in image.get_flattened_data()]
        layer = image.copy()
        layer.putdata([pixel if keep else (*pixel[:3], 0) for pixel, keep in zip(image.get_flattened_data(), mask)])
        colored, _ = metal.module.recolor(palettes['agrinium'].convert('RGBA'), layer)
        output_image = image.copy()
        output_image.putdata([color if keep else original for original, color, keep
                              in zip(image.get_flattened_data(), colored.get_flattened_data(), mask)])
        assert any(mask)
        assert all(a == b for a, b, keep in zip(image.get_flattened_data(), output_image.get_flattened_data(), mask) if not keep)
        target = ASSETS / f'mandomc/textures/item/tools/agrinium_{kind}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        output_image.save(target)
        records.append({'target': str(target.relative_to(ASSETS)), 'recoloredLocalSource': str(source.relative_to(ASSETS)),
                        'palette': 'agrinium', 'recoloredPixels': sum(mask),
                        'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                        'outputSha256': hashlib.sha256(target.read_bytes()).hexdigest()})

    output = ROOT / 'resource-pack/progression-metal-sources.json'
    output.write_text(json.dumps({'schema-version': 1, 'source': 'Official Faithful 32x 26.1-june-2026 Fj5GKJnf',
        'license': 'src/faithful-license.txt', 'scope': 'Native iron/Agrinium and redstone/Electrum presentation. '
        'Electrum replaces nonoxidizing redstone; lit/unlit ores share native texture aliases. Geometry, alpha, block states and placed shapes remain native.',
        'textures': records}, indent=2) + '\n')
    print(f'Built {len(records)} Agrinium/Electrum texture aliases without changing prior metal art.')


if __name__ == '__main__':
    main()
