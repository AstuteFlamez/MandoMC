#!/usr/bin/env python3
"""Render the editable native 32x32 flamethrower pixel grid; enlargement is preview-only."""
import argparse
import binascii
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'resource-pack/artwork/boba_flamethrower.pixels.json'
OUTPUT = ROOT / 'resource-pack/src/assets/mandomc/textures/item/equipment/boba_flamethrower.png'


def render(scale=1):
    source = json.loads(SOURCE.read_text())
    assert source['width'] == source['height'] == 32
    assert len(source['pixels']) == 32 and all(len(row) == 32 for row in source['pixels'])
    palette = {key: bytes.fromhex(color) for key, color in source['palette'].items()}
    assert len(palette) <= 16 and all(len(color) == 4 and color[3] in (0, 255) for color in palette.values())
    raw = b''
    for row in source['pixels']:
        raw += (b'\0' + b''.join(palette[pixel] * scale for pixel in row)) * scale

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', binascii.crc32(kind + data) & 0xffffffff)

    size = 32 * scale
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scale', type=int, default=1, choices=range(1, 33))
    parser.add_argument('--output', type=Path, default=OUTPUT)
    arguments = parser.parse_args()
    if arguments.scale != 1 and arguments.output == OUTPUT:
        parser.error('The in-game texture must remain 32x32; choose a separate preview output.')
    arguments.output.write_bytes(render(arguments.scale))
