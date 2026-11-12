#!/usr/bin/env python3
"""Build a private, visual-only sell GUI overlay. Never publishes or edits a running server."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_yaml(data):
    return json.loads(subprocess.check_output(
        ['ruby', '-ryaml', '-rjson', '-e', 'puts JSON.generate(YAML.safe_load(STDIN.read))'], input=data))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ['baseline', 'jawas-config', 'legacy-pack', 'current-pack', 'output']:
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--java', required=True)
    args = parser.parse_args()
    # Keep commercial/reference archives outside Git; stage only the runtime image needed here.
    if digest(args.legacy_pack) != '32a578caac7c4c8976868abf0aa20f545c9926eb8d6382b94ccf5e986c95cce5':
        raise ValueError('Expected the recorded original MandoPack archive')
    from PIL import Image
    from io import BytesIO
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.legacy_pack) as legacy:
        original_bytes = legacy.read('assets/minecraft/textures/custom/guis/pawnshop.png')
    original = Image.open(BytesIO(original_bytes)).convert('RGBA')
    if original.size != (256, 256):
        raise ValueError('Expected original 256×256 pawnshop canvas')
    wide = original.copy()
    # Two extra 18px columns use existing repeated pixels, preserving roof/sign/pillars.
    for x in range(44, 212):
        for y in range(67, 124):
            wide.putpixel((x, y), original.getpixel((x + 18 if x < 128 else x - 18, y)))
    # The live book/sell/return items replace the baked, noninteractive info symbol.
    for y in range(142, 161):
        for x in range(118, 138):
            wide.putpixel((x, y), original.getpixel((139, y)))
    tile = original.crop((65, 70, 83, 88))
    for y in [173, 191, 209, 231]:
        for column in range(9):
            wide.paste(tile, (47 + 18 * column, y))
    # Removing one unused interior row and raising ascent by 18 keeps four native rows aligned.
    image = Image.new('RGBA', (256, 238))
    image.paste(wide.crop((0, 0, 256, 124)), (0, 0))
    image.paste(wide.crop((0, 142, 256, 256)), (0, 124))
    for row in range(3):
        for column in range(9):
            x, y = 48 + 18 * column, 71 + 18 * row
            assert (x - 40, y - 53) == (8 + 18 * column, 18 + 18 * row)
            assert image.crop((x, y, x + 16, y + 16)).tobytes() == original.crop((66, 71, 82, 87)).tobytes()
    image.save(output / 'sell.png')
    names = ['net/mandomc/gameplay/jawa/JawaSellInventory.class',
             'net/mandomc/gameplay/jawa/JawaSellInventory$Tray.class']
    patch_args = []
    with zipfile.ZipFile(args.baseline) as baseline:
        for name in names:
            source = output / 'original' / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_bytes(baseline.read(name))
            patch_args += [str(source), str(output / 'classes' / name)]
    subprocess.run([args.java, str(Path(__file__).with_name('PatchSellGui.java')), *patch_args], check=True)

    def patch_config(data):
        config = load_yaml(data)
        config['text']['buyer-gui-title'] = '<white><font:mandomc:gui/sell>\ue100\ue101</font>'
        return (json.dumps(config, indent=2, ensure_ascii=False) + '\n').encode()

    replacements = {name: (output / 'classes' / name).read_bytes() for name in names}
    with zipfile.ZipFile(args.baseline) as baseline, zipfile.ZipFile(output / 'candidate.jar', 'w', zipfile.ZIP_DEFLATED) as candidate:
        replacements['mobs/jawas.yml'] = patch_config(baseline.read('mobs/jawas.yml'))
        for name in baseline.namelist():
            candidate.writestr(name, replacements.get(name, baseline.read(name)))
    with zipfile.ZipFile(args.baseline) as baseline, zipfile.ZipFile(output / 'candidate.jar') as candidate:
        assert all(baseline.read(n) == candidate.read(n) for n in baseline.namelist() if n not in replacements)
    (output / 'jawas.yml').write_bytes(patch_config(args.jawas_config.read_bytes()))
    font = {'providers': [
        {'type': 'space', 'advances': {'\ue100': -48}},
        {'type': 'bitmap', 'file': 'mandomc:gui/sell.png', 'ascent': 66, 'height': 238, 'chars': ['\ue101']}]}
    font_name = 'assets/mandomc/font/gui/sell.json'
    art_name = 'assets/mandomc/textures/gui/sell.png'
    with zipfile.ZipFile(args.current_pack) as current, zipfile.ZipFile(output / 'MandoMC-preview.zip', 'w', zipfile.ZIP_DEFLATED) as pack:
        if font_name in current.namelist() or art_name in current.namelist():
            raise ValueError('Sell GUI already exists in baseline; review before replacing it')
        for name in current.namelist():
            pack.writestr(name, current.read(name))
        pack.writestr(font_name, json.dumps(font, indent=2, ensure_ascii=False) + '\n')
        pack.writestr(art_name, (output / 'sell.png').read_bytes())
    with zipfile.ZipFile(args.current_pack) as current, zipfile.ZipFile(output / 'MandoMC-preview.zip') as pack:
        assert all(current.read(n) == pack.read(n) for n in current.namelist())
    report = {'baselineJarSha256': digest(args.baseline), 'baselineConfigSha256': digest(args.jawas_config),
              'baselinePackSha256': digest(args.current_pack), 'changedJarEntries': list(replacements),
              'addedPackEntries': [font_name, art_name], 'texture': [256, 238],
              'menu': {'rows': 4, 'inputs': 27, 'info': 27, 'sell': 31, 'return': 35},
              'privateStagingOnly': True}
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Staged two visual class edits and two additive pack assets; unrelated bytes verified unchanged.')


if __name__ == '__main__':
    main()
