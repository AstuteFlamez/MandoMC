"""Read the terrain and staff mask from a frozen wilderness original."""

import base64
import gzip
import io
import struct

import numpy as np


def baseline(path):
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))

    def integer():
        return struct.unpack('>i', stream.read(4))[0]

    def text():
        size = struct.unpack('>H', stream.read(2))[0]
        return stream.read(size).decode('utf-8')

    if integer() != 1:
        raise ValueError(f'Unsupported baseline {path}')
    text()  # world
    integer()  # chunk x
    integer()  # chunk z
    bottom, top = integer(), integer()
    stream.read(12)  # due time and restore cursor
    palette = [text() for _ in range(integer())]
    ids = np.frombuffer(stream.read((top - bottom) * 256 * 4), dtype='>i4')
    staff_mask = stream.read(integer())
    metadata = path.with_name(path.name + '.meta')
    if metadata.exists():
        staff_mask = base64.b64decode(metadata.read_text().splitlines()[2])
    staff = np.unpackbits(np.frombuffer(staff_mask, dtype=np.uint8), bitorder='little')
    return bottom, palette, ids, staff
