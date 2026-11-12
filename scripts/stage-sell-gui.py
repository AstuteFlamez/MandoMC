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
    for key in ['baseline', 'jawas-config', 'legacy-pack', 'base-assets', 'current-pack', 'jawa-source', 'classpath-file', 'output']:
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
    image = original.copy()
    icon_inputs = []
    with zipfile.ZipFile(args.base_assets) as supplied:
        for name, x in [('confirm', 65), ('cancel', 173)]:
            entry = 'Dark_Sci-Fi_GUI_RP/assets/minecraft/textures/gui/sprites/container/beacon/' + name + '.png'
            data = supplied.read(entry)
            icon = Image.open(BytesIO(data)).convert('RGBA')
            if icon.size != (18, 18):
                raise ValueError('Expected original 18px ' + name + ' icon')
            image.alpha_composite(icon, (x, 142))
            icon_inputs.append({'path': entry, 'sha256': hashlib.sha256(data).hexdigest()})
    # No slot/roof/frame/tooltip-symbol redraw: only two supplied 18px icons differ.
    for y in range(256):
        for x in range(256):
            if image.getpixel((x, y)) != original.getpixel((x, y)):
                assert 142 <= y < 160 and (65 <= x < 83 or 173 <= x < 191)
    image.save(output / 'sell.png')
    source = args.jawa_source.read_text()
    original_source = source
    changes = [
        ('private static final int INPUTS=27, SIZE=36;',
         'private static final int INPUTS=21, SIZE=54;\n'
         '    private static int inputSlot(int index){return 10+(index/7)*9+index%7;}\n'
         '    private static boolean input(int raw){return raw>=10 && raw<=34 && raw%9>=1 && raw%9<=7;}'),
        ('MenuItems.fill(inventory);', ''),
        ('label("gray-title")', 'label("buyer-gui-title")'),
        ('inventory.setItem(27,info)', 'inventory.setItem(49,info)'),
        ('inventory.setItem(35,close)', 'inventory.setItem(52,close)'),
        ('tray.inventory.setItem(31,button)', 'tray.inventory.setItem(46,button)'),
        ('if(raw==35)', 'if(raw==52)'), ('if(raw==31 &&', 'if(raw==46 &&'),
        ('raw>=0 && raw<INPUTS', 'input(raw)'), ('if(raw<INPUTS)', 'if(input(raw))'),
        ('inventory.setItem(i,', 'inventory.setItem(inputSlot(i),'),
        ('inventory.getItem(i)', 'inventory.getItem(inputSlot(i))')]
    for before, after in changes:
        if before not in source:
            raise ValueError('Sell source changed; review layout transformation: ' + before)
        source = source.replace(before, after)
    for variable in ['info', 'close', 'button']:
        before = variable + '.setItemMeta(meta)'
        if source.count(before) != 1:
            raise ValueError('Unexpected control item presentation for ' + variable)
        source = source.replace(before, 'meta.setItemModel(new NamespacedKey("mandomc","gui/control"));' + before)
    source_path = output / 'source/net/mandomc/gameplay/jawa/JawaSellInventory.java'
    source_path.parent.mkdir(parents=True, exist_ok=True)
    source_path.write_text(source)
    import difflib
    (output / 'source.patch').write_text(''.join(difflib.unified_diff(
        original_source.splitlines(True), source.splitlines(True),
        fromfile='JawaSellInventory.java', tofile='JawaSellInventory.java')))
    # Compile only the explicitly reviewed current owner source against its runtime APIs.
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    classpath = str(args.baseline.resolve()) + ':' + args.classpath_file.read_text().strip()
    subprocess.run([str(Path(args.java).with_name('javac')), '-sourcepath', '', '-cp', classpath,
                    '-d', str(classes), str(source_path)], check=True)
    replacements = {str(path.relative_to(classes)): path.read_bytes()
                    for path in (classes / 'net/mandomc/gameplay/jawa').glob('JawaSellInventory*.class')}
    if not replacements:
        raise ValueError('No compiled sell inventory classes')

    def patch_config(data):
        config = load_yaml(data)
        config['text']['buyer-gui-title'] = '<white><font:mandomc:gui/sell>\ue100\ue101</font>'
        return (json.dumps(config, indent=2, ensure_ascii=False) + '\n').encode()

    with zipfile.ZipFile(args.baseline) as baseline, zipfile.ZipFile(output / 'candidate.jar', 'w', zipfile.ZIP_DEFLATED) as candidate:
        replacements['mobs/jawas.yml'] = patch_config(baseline.read('mobs/jawas.yml'))
        for name in baseline.namelist():
            candidate.writestr(name, replacements.get(name, baseline.read(name)))
    with zipfile.ZipFile(args.baseline) as baseline, zipfile.ZipFile(output / 'candidate.jar') as candidate:
        assert all(baseline.read(n) == candidate.read(n) for n in baseline.namelist() if n not in replacements)
    (output / 'jawas.yml').write_bytes(patch_config(args.jawas_config.read_bytes()))
    font = {'providers': [
        {'type': 'space', 'advances': {'\ue100': -48}},
        {'type': 'bitmap', 'file': 'mandomc:gui/sell.png', 'ascent': 48, 'height': 256, 'chars': ['\ue101']}]}
    font_name = 'assets/mandomc/font/gui/sell.json'
    art_name = 'assets/mandomc/textures/gui/sell.png'
    info_name = 'assets/mandomc/items/gui/control.json'
    with zipfile.ZipFile(args.current_pack) as current, zipfile.ZipFile(output / 'MandoMC-preview.zip', 'w', zipfile.ZIP_DEFLATED) as pack:
        if any(name in current.namelist() for name in [font_name, art_name, info_name]):
            raise ValueError('Sell GUI already exists in baseline; review before replacing it')
        for name in current.namelist():
            pack.writestr(name, current.read(name))
        pack.writestr(font_name, json.dumps(font, indent=2, ensure_ascii=False) + '\n')
        pack.writestr(art_name, (output / 'sell.png').read_bytes())
        pack.writestr(info_name, json.dumps({'model': {'type': 'minecraft:empty'}}) + '\n')
    with zipfile.ZipFile(args.current_pack) as current, zipfile.ZipFile(output / 'MandoMC-preview.zip') as pack:
        assert all(current.read(n) == pack.read(n) for n in current.namelist())
    report = {'baselineJarSha256': digest(args.baseline), 'baselineConfigSha256': digest(args.jawas_config),
              'baselinePackSha256': digest(args.current_pack), 'changedJarEntries': list(replacements),
              'addedPackEntries': [font_name, art_name, info_name], 'texture': [256, 256],
              'menu': {'rows': 6, 'inputs': 21, 'info': 49, 'sell': 46, 'return': 52},
              'sourceSha256': digest(args.jawa_source), 'baseAssetsSha256': digest(args.base_assets),
              'iconInputs': icon_inputs,
              'privateStagingOnly': True}
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Staged original 21-slot artwork, supplied confirm/cancel icons and layout-only service changes; unrelated JAR/pack entries unchanged.')


if __name__ == '__main__':
    main()
