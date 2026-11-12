#!/usr/bin/env python3
"""Apply preserved legacy palettes to the existing 32px ingot/sheet artwork.

Requires Pillow. Original 16px inputs are retained outside the distributed pack.
No resizing, dithering, random generation or alpha modification is performed.
"""
from bisect import bisect_right
import hashlib
import json
from pathlib import Path
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'resource-pack'
TEXTURES = PACK / 'src/assets/mandomc/textures/item/legacy/metals'
ARTWORK = PACK / 'artwork/metals'
MANIFEST = ARTWORK / 'palette-transfer.json'


def luminance(rgb):
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def recolor(original, template):
    palette = sorted({p[:3] for p in original.get_flattened_data() if p[3]}, key=luminance)
    levels = [luminance(p) for p in palette]
    pixels = list(template.get_flattened_data())
    shades = [luminance(p) for p in pixels if p[3]]
    low, high = min(shades), max(shades)
    result = []
    for pixel in pixels:
        if pixel[3] == 0:
            result.append(pixel)
            continue
        position = (luminance(pixel) - low) / (high - low)
        target = levels[0] + position * (levels[-1] - levels[0])
        right = min(len(levels) - 1, max(1, bisect_right(levels, target)))
        left = right - 1
        weight = (target - levels[left]) / (levels[right] - levels[left])
        rgb = tuple(round(palette[left][i] * (1 - weight) + palette[right][i] * weight) for i in range(3))
        result.append((*rgb, pixel[3]))
    output = Image.new('RGBA', template.size)
    output.putdata(result)
    assert output.getchannel('A').tobytes() == template.getchannel('A').tobytes()
    return output, palette


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    verify = '--verify' in sys.argv
    if MANIFEST.exists():
        manifest = json.loads(MANIFEST.read_text())
        entries = manifest['textures']
    else:
        assert not verify, 'Palette-transfer manifest is missing'
        entries = []
        for category in ('ingots', 'sheets'):
            for path in sorted((TEXTURES / category).glob('*.png')):
                if Image.open(path).size != (16, 16):
                    continue
                original = ARTWORK / '16x' / category / path.name
                original.parent.mkdir(parents=True, exist_ok=True)
                original.write_bytes(path.read_bytes())
                entries.append({'texture': path.relative_to(PACK).as_posix(),
                                'original': original.relative_to(PACK).as_posix(),
                                'originalSha256': digest(original),
                                'template': (TEXTURES / category / ('durasteel' + ('_sheet' if category == 'sheets' else '') + '.png')).relative_to(PACK).as_posix()})
        manifest = {'schema-version': 1, 'method': 'Luminance-ordered original RGB palette, linearly interpolated over normalized 32px template shading; unchanged template alpha and pixel positions.',
                    'textures': entries, 'preserved32px': {p.relative_to(PACK).as_posix(): digest(p)
                    for p in TEXTURES.rglob('*.png') if Image.open(p).size == (32, 32)}}
    for entry in entries:
        original_path = PACK / entry['original']
        assert digest(original_path) == entry['originalSha256']
        original = Image.open(original_path).convert('RGBA')
        template = Image.open(PACK / entry['template']).convert('RGBA')
        assert original.size == (16, 16) and template.size == (32, 32)
        output, palette = recolor(original, template)
        target = PACK / entry['texture']
        if verify:
            existing = Image.open(target).convert('RGBA')
            assert existing.size == (32, 32) and existing.tobytes() == output.tobytes(), target
        else:
            output.save(target)
            entry['palette'] = [list(color) for color in palette]
            entry['outputSha256'] = digest(target)
    for name, expected in manifest['preserved32px'].items():
        assert digest(PACK / name) == expected, f'Previously 32px artwork changed: {name}'
    if not verify:
        MANIFEST.write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'{"Verified" if verify else "Recolored"} {len(entries)} metal textures; all {len(manifest["preserved32px"])} existing 32px textures preserved.')


if __name__ == '__main__':
    main()
