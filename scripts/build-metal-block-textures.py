#!/usr/bin/env python3
"""First-pass block-only metal palettes on the pinned Faithful 32x art; requires Pillow."""
import colorsys
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'resource-pack'
ASSETS = PACK / 'src/assets'
spec = importlib.util.spec_from_file_location('metal_palette', Path(__file__).with_name('recolor-metal-textures.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def tint(source, palette, ore=False):
    source = source.convert('RGBA')
    mask = []
    for pixel in source.get_flattened_data():
        hue, saturation, _ = colorsys.rgb_to_hsv(*(v / 255 for v in pixel[:3]))
        mask.append(not ore or (0.40 <= hue <= 0.61 and saturation > 0.15))
    layer = source.copy()
    layer.putdata([pixel if keep else (*pixel[:3], 0) for pixel, keep in zip(source.get_flattened_data(), mask)])
    colored, _ = module.recolor(palette.convert('RGBA'), layer)
    output = source.copy()
    output.putdata([(*color[:3], original[3]) if keep else original for original, color, keep in zip(source.get_flattened_data(), colored.get_flattened_data(), mask)])
    if ore:
        assert 0 < sum(mask) < 512
        assert all(a == b for a, b, keep in zip(source.get_flattened_data(), output.get_flattened_data(), mask) if not keep)
    return output, sum(mask)


def main():
    archive = Path(sys.argv[1])
    assert hashlib.sha1(archive.read_bytes()).hexdigest() == '9447e84ee7121c86304e467a0f8d5b095872e1e6'
    records = []
    with zipfile.ZipFile(archive) as faithful:
        license_path = ASSETS.parent / 'faithful-license.txt'
        license_path.write_bytes(faithful.read('LICENSE.txt'))
        entries = [('durasteel_block','diamond_block','durasteel',False,['diamond_block']),
                   ('stone_durasteel_ore','diamond_ore','durasteel',True,['diamond_ore']),
                   ('deepslate_durasteel_ore','deepslate_diamond_ore','durasteel',True,['deepslate_diamond_ore']),
                   ('beskar_block','netherite_block','beskar',False,['netherite_block']),
                   ('stone_beskar_ore','diamond_ore','beskar',True,['nether_gold_ore']),
                   ('deepslate_beskar_ore','deepslate_diamond_ore','beskar',True,['ancient_debris_side','ancient_debris_top'])]
        for name, source, metal, ore, replacements in entries:
            member='assets/minecraft/textures/block/'+source+'.png'
            import io
            image=Image.open(io.BytesIO(faithful.read(member)))
            palette=Image.open(ASSETS/'mandomc/textures/item/legacy/metals/ingots'/f'{metal}.png')
            result,pixels=tint(image,palette,ore)
            assert result.size == (32,32)
            path=ASSETS/'mandomc/textures/block/metals'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);result.save(path)
            for target in replacements:
                dest=ASSETS/'minecraft/textures/block'/f'{target}.png';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(path.read_bytes())
            model=ASSETS/'mandomc/models/block/metals'/f'{name}.json';model.parent.mkdir(parents=True,exist_ok=True);model.write_text(json.dumps({'parent':'minecraft:block/cube_all','textures':{'all':'mandomc:block/metals/'+name}},indent=2)+'\n')
            records.append({'name':name,'source':member,'sourceSha256':hashlib.sha256(faithful.read(member)).hexdigest(),'palette':metal,'replaces':replacements,'recoloredPixels':pixels,'outputSha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    (PACK/'metal-block-sources.json').write_text(json.dumps({'schema-version':1,'source':'Faithful 32x 26.1 June 2026, official unmodified Modrinth version Fj5GKJnf','website':'https://faithfulpack.net/','license':'src/faithful-license.txt','scope':'Block textures only. Native block IDs, world generation, drops, armor and tool textures are unchanged. Ore rock backgrounds are byte-for-byte Faithful; only cyan mineral pixels are recolored.','blocks':records},indent=2)+'\n')
    print('Built six 32px metal block candidates and seven native block texture replacements.')


if __name__ == '__main__':
    main()
