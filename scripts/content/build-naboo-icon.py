#!/usr/bin/env python3
"""Refine the legacy Naboo icon on its original 32x32 pixel grid; never resize it."""
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'resource-pack/art-sources/planets/naboo-legacy.png'
TARGET = ROOT / 'resource-pack/src/assets/mandomc/textures/item/planets/naboo.png'
PALETTES = {
    'land': [(191,215,116),(137,182,83),(93,141,61),(57,98,45),(36,66,36)],
    'water': [(117,201,228),(63,157,204),(36,113,173),(27,76,132),(23,48,88)],
    'valley': [(227,213,144),(199,179,105),(162,139,78),(119,99,57),(78,67,44)],
}

def main():
    image = Image.open(SOURCE).convert('RGBA')
    assert image.size == (32,32), 'Only a native 32x32 source is accepted'
    output = Image.new('RGBA',(32,32))
    for y in range(32):
        for x in range(32):
            r,g,b,a = image.getpixel((x,y))
            if a < 128:
                continue
            kind = 'land' if g > r*1.25 and g > b*1.15 else 'valley' if r > b*1.5 and g > b*1.4 else 'water'
            shade = min(4,max(0,(x+y-12)//8))
            if (x-15.5)**2+(y-16)**2 > 10.5**2 and x+y>33:
                shade = min(4,shade+1)
            output.putpixel((x,y),(*PALETTES[kind][shade],255))
    for x,y in [(9,9),(10,9),(11,10),(18,18),(19,18),(20,17)]:
        if output.getpixel((x,y))[3]:
            output.putpixel((x,y),(219,237,231,255))
    TARGET.parent.mkdir(parents=True,exist_ok=True)
    output.save(TARGET)
    print('Refined Naboo at native 32x32; no resize or downscale')

if __name__ == '__main__':
    main()
