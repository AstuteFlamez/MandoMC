import importlib.util
import json
from pathlib import Path
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('pack_builder', ROOT / 'scripts/build-resource-pack.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


def chunks(data):
    offset = 8
    while offset < len(data):
        size = struct.unpack_from('>I', data, offset)[0]
        kind = data[offset + 4:offset + 8]
        value = data[offset + 8:offset + 8 + size]
        yield kind, value
        offset += size + 12


class PackCompactionTests(unittest.TestCase):
    def test_all_json_values_and_texture_scanlines_are_unchanged(self):
        for path in builder.SOURCE.rglob('*'):
            if not path.is_file():
                continue
            compact = builder.packed_bytes(path)
            if path.suffix in ('.json', '.mcmeta'):
                self.assertEqual(json.loads(path.read_bytes()), json.loads(compact), str(path))
            elif path.suffix == '.png':
                before, after = list(chunks(path.read_bytes())), list(chunks(compact))
                for kind in (b'IHDR', b'PLTE', b'tRNS', b'gAMA', b'cHRM', b'sRGB', b'iCCP'):
                    self.assertEqual([v for k, v in before if k == kind],
                                     [v for k, v in after if k == kind], str(path))
                original = b''.join(v for k, v in before if k == b'IDAT')
                optimized = b''.join(v for k, v in after if k == b'IDAT')
                self.assertEqual(zlib.decompress(original), zlib.decompress(optimized), str(path))
                self.assertFalse(any(k in (b'tEXt', b'zTXt', b'iTXt', b'tIME', b'eXIf')
                                     for k, _ in after), str(path))
            else:
                self.assertEqual(path.read_bytes(), compact)
