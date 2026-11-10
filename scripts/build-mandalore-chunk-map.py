#!/usr/bin/env python3
"""Project the private Mandalore review map onto explicit chunk zones; no runtime scans.

Usage: build-mandalore-chunk-map.py <render/scene.json> <render/blocks.bin> <output.yml>
Only the spawn outpost footprint is safe. Cave interiors/decor stay warzone.
Chunk zoning spans the full vertical column; surface safezone wins at overlaps.
"""
import json
import math
import sys
from pathlib import Path


def generate(scene, blocks):
    width, height, length = scene['size']
    ox, oy, oz = scene['origin']
    assert (width, height, length, ox, oy, oz) == (500, 160, 500, -250, -12, -250)
    assert len(blocks) == width * height * length
    safe, war = set(), set()

    def mark_rect(target, x1, z1, x2, z2):
        for z in range(z1, z2 + 1):
            for x in range(x1, x2 + 1):
                target.add((x // 16, z // 16))

    # Cover the whole arrival terrace, shelter, fixtures and mine gateway shoulders.
    # The terrace extends beyond the fence; include every intersecting chunk.
    # The causeway's distant ruin and surrounding cities remain wilderness.
    mark_rect(safe, -42, -211, 6, -173)
    # Detect carved central cave air in the authored low-level interior, including
    # the central hall, loop and side chambers. Ignore surface air and outside void.
    air = scene['names'].index('air')
    for z in range(-102, 103):
        for x in range(-100, 101):
            if any(blocks[((y - oy) * length + z - oz) * width + x - ox] == air for y in range(0, 40)):
                war.add((x // 16, z // 16))
    # Connect both carved approaches to the interior with their authored 6.3-block radius.
    for route in [[(-18, -188), (-18, -160), (-18, -130), (-18, -100)],
                  [(50, 194), (50, 165), (18, 115), (18, 90), (18, 66)]]:
        for (ax, az), (bx, bz) in zip(route, route[1:]):
            for z in range(min(az, bz) - 7, max(az, bz) + 8):
                for x in range(min(ax, bx) - 7, max(ax, bx) + 8):
                    t = max(0, min(1, ((x-ax)*(bx-ax)+(z-az)*(bz-az))/((bx-ax)**2+(bz-az)**2)))
                    if math.hypot(x - ax - t*(bx-ax), z - az - t*(bz-az)) < 6.3:
                        war.add((x // 16, z // 16))
    return [''.join('S' if (x, z) in safe else 'W' if (x, z) in war else '.'
                    for x in range(-16, 16)) for z in range(-16, 16)]


if __name__ == '__main__':
    rows = generate(json.loads(Path(sys.argv[1]).read_text()), Path(sys.argv[2]).read_bytes())
    output = '''# Mandalore blocks X/Z -250..249; edge chunks include six blocks outside the border.
# Rows north to south; columns west to east. S safezone, W warzone, . wilderness.
# The spawn outpost wins over caves where their full-height chunk columns overlap.
# The atlas is the native territory authority for map and protection.
schema-version: 1
world: minecraft:mandalore_review
name: Mandalore (500×500)
min-chunk-x: -16
max-chunk-x: 15
min-chunk-z: -16
max-chunk-z: 15
rows:
'''
    output += ''.join(f"  - '{row}'\n" for row in rows)
    output += """# Entire world policy; the map shows a moving 32x32 view in these worlds.
safezone-worlds:
  minecraft:spawnworld: Batuu
# Existing Hoth safe arrival/warzone and retired survival landing are preserved.
hoth:
  world: minecraft:hoth
  warzone-min: [-237, -64, -121]
  warzone-max: [237, 319, 237]
legacy-landing:
  world: minecraft:overworld
  min: [-144, -64, -144]
  max: [-129, 319, -129]
"""
    Path(sys.argv[3]).parent.mkdir(parents=True, exist_ok=True)
    Path(sys.argv[3]).write_text(output)
    print({c: ''.join(rows).count(c) for c in 'SW.'})
