#!/usr/bin/env python3
"""Render the actual NPC skin's head as a Discord avatar, without AI approximating its pixels."""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw


def render(skin):
    image = Image.new('RGB', (512, 512))
    draw = ImageDraw.Draw(image)
    for y in range(512):
        draw.line((0, y, 512, y), fill=(12 + y // 40, 29 + y // 24, 49 + y // 17))
    draw.ellipse((35, 35, 477, 477), fill='#203f59', outline='#365d75', width=3)
    draw.ellipse((125, 405, 413, 450), fill='#132a3b')
    texture = skin.convert('RGBA')

    def face(box, overlay, points, shade):
        tile = texture.crop(box); tile.alpha_composite(texture.crop(overlay))
        # Project every real 8x8 texel into the same orthographic cube face.
        origin, right, _, down = points
        def at(x, y):
            return (round(origin[0] + (right[0]-origin[0])*x/8 + (down[0]-origin[0])*y/8),
                    round(origin[1] + (right[1]-origin[1])*x/8 + (down[1]-origin[1])*y/8))
        for y in range(8):
            for x in range(8):
                r, g, b, alpha = tile.getpixel((x, y))
                if alpha:
                    draw.polygon([at(x,y), at(x+1,y), at(x+1,y+1), at(x,y+1)],
                                 fill=tuple(min(255, round(c*shade)) for c in (r,g,b)))
    face((8,0,16,8),(40,0,48,8),[(90,170),(216,90),(426,125),(300,205)],1.12)
    face((0,8,8,16),(32,8,40,16),[(300,205),(426,125),(426,345),(300,425)],.75)
    face((8,8,16,16),(40,8,48,16),[(90,170),(300,205),(300,425),(90,390)],1.0)
    return image


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('skin', type=Path); parser.add_argument('output', type=Path)
    args = parser.parse_args()
    with Image.open(args.skin) as skin:
        if skin.size != (64,64): raise SystemExit('Expected a 64x64 Minecraft skin')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        render(skin).save(args.output)
