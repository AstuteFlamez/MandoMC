#!/usr/bin/env python3
"""Stage original Ilum terrain as an existing Paper dimension; never modify the legacy source."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import shutil
import zlib

import nbtlib
from nbtlib import tag as T


class Blocks:
    def __init__(self, world):
        self.world = world
        self.regions = {}
        self.chunks = {}

    def get(self, x, y, z):
        cx, cz = x//16, z//16
        if (cx,cz) not in self.chunks:
            path = self.world/'region'/f'r.{cx//32}.{cz//32}.mca'
            data = self.regions.setdefault(path, path.read_bytes())
            index = cx%32+(cz%32)*32
            sector = int.from_bytes(data[index*4:index*4+4], 'big') >> 8
            if not sector:
                self.chunks[cx,cz] = {}
            else:
                offset = sector*4096
                length = int.from_bytes(data[offset:offset+4], 'big')
                assert data[offset+4] == 2
                nbt = nbtlib.File.parse(io.BytesIO(zlib.decompress(data[offset+5:offset+4+length])))
                self.chunks[cx,cz] = {int(s['Y']): s for s in nbt['sections']}
        section = self.chunks[cx,cz].get(y//16)
        if section is None or 'block_states' not in section:
            return 'minecraft:air'
        state = section['block_states']
        palette = state['palette']
        i = (y%16)*256+(z%16)*16+x%16
        value = 0
        if len(palette)>1:
            bits = max(4, (len(palette)-1).bit_length())
            per = 64//bits
            value = (int(state['data'][i//per]) >> ((i%per)*bits)) & ((1<<bits)-1)
        return str(palette[value]['Name'])


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--reference', type=Path, required=True)
    a = p.parse_args()
    assert not a.output.exists(), 'Refusing to replace an existing staged dimension'
    (a.output/'region').mkdir(parents=True)
    receipts = {}
    for f in sorted((a.source/'region').glob('*.mca')):
        target = a.output/'region'/f.name
        shutil.copy2(f,target)
        sha = hashlib.sha256(f.read_bytes()).hexdigest()
        assert hashlib.sha256(target.read_bytes()).hexdigest() == sha
        receipts[f.name] = sha
    assert len(receipts) > 4
    (a.output/'data/paper').mkdir(parents=True)
    nbtlib.File({'DataVersion':T.Int(4790),'data':T.Compound({
        'difficulty_settings':T.Compound({'locked':T.Byte(1),'difficulty':T.String('peaceful'),'hardcore':T.Byte(0)}),
        'spawn':T.Compound({'pos':T.IntArray([11,-60,-56]),'pitch':T.Float(0),'yaw':T.Float(0)}),
        'initialized':T.Byte(1),'game_time':T.Long(6000),'game_type':T.Int(2)
    })}).save(a.output/'data/paper/level_overrides.dat',gzipped=True)
    worldgen = nbtlib.load(a.reference)
    worldgen['data']['generate_structures'] = T.Byte(0)
    worldgen['data']['dimensions']['minecraft:overworld']['generator'] = T.Compound({
        'type':T.String('minecraft:flat'),'settings':T.Compound({
            'biome':T.String('minecraft:the_void'),'features':T.Byte(0),'lakes':T.Byte(0),
            'layers':T.List[T.Compound]([T.Compound({'block':T.String('minecraft:air'),'height':T.Int(1)})]),
            'structure_overrides':T.List[T.String]([])})})
    (a.output/'data/minecraft').mkdir()
    worldgen.save(a.output/'data/minecraft/world_gen_settings.dat',gzipped=True)
    reader = Blocks(a.source)
    positions = [(11,-61,-56),(27,-51,-60),(80,-46,-57),(-111,144,-186),(-120,-37,-230)]
    samples = {','.join(map(str,pos)):reader.get(*pos) for pos in positions}
    for pos in positions[1:]:
        assert reader.get(*pos)=='minecraft:heavy_weighted_pressure_plate',(pos,reader.get(*pos))
    record = dict(source='Original user-owned Ilum world',key='minecraft:ilum',regions=receipts,
                  terrain_byte_identical=True,new_chunks='void',samples=samples,
                  legacy_entities_and_player_data_imported=False)
    (a.output.parent/'import-receipt.json').write_text(json.dumps(record,indent=2))
    print(json.dumps({k:v for k,v in record.items() if k!='regions'},indent=2))


if __name__=='__main__':
    main()
