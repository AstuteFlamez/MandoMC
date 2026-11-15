import struct
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

class PlanetIconContract(unittest.TestCase):
    def test_deployed_planet_icons_are_32_by_32_rgba_not_generated_originals(self):
        for name in ('mandalore','batuu','hoth','tatooine','naboo'):
            with self.subTest(planet=name):
                data=(ROOT/'resource-pack/src/assets/mandomc/textures/item/planets'/f'{name}.png').read_bytes()
                self.assertEqual(data[:8],b'\x89PNG\r\n\x1a\n')
                self.assertEqual(struct.unpack('>II',data[16:24]),(32,32))
                self.assertEqual(data[25],6)  # RGBA, preserving the transparent margins
