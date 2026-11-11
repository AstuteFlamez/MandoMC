#!/usr/bin/env python3
"""Preserve the installed combined build; replace only the reviewed Ilum/crate integration entries."""
from pathlib import Path
import argparse,hashlib,json,zipfile
import yaml

p=argparse.ArgumentParser()
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--built',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--finish-only',action='store_true',help='Patch the existing Ilum completion owner without replacing shared integration classes')
p.add_argument('--crate-area',action='store_true',help='Patch completion, crate holograms and explicit crate warp support')
p.add_argument('--warp-menu',action='store_true',help='Patch only warp menu visibility; preserve all other feature bytes')
a=p.parse_args()
with zipfile.ZipFile(a.baseline) as src,zipfile.ZipFile(a.built) as built:
    old={n:src.read(n) for n in src.namelist()}
    new=old.copy()
    prefixes=('net/mandomc/gameplay/crates/','net/mandomc/gameplay/ilum/',
              'net/mandomc/gameplay/territory/TerritoryPolicy','net/mandomc/gameplay/territory/TerritoryService',
              'net/mandomc/gameplay/territory/TerritoryProtection','net/mandomc/gameplay/travel/WarpService')
    resources=('net/mandomc/commands/CommandBindings.class','crates/crates.yml','parkour/ilum.yml',
               'items/definitions/crates.yml','territory/maps.yml')
    if a.finish_only or a.crate_area or a.warp_menu:
        assert 'net/mandomc/gameplay/ilum/ParkourService.class' in old,'Existing Ilum owner required'
        prefixes=('net/mandomc/gameplay/ilum/ParkourService',)
        resources=('parkour/ilum.yml',)
        if a.crate_area:
            prefixes+=('net/mandomc/gameplay/crates/CrateService','net/mandomc/gameplay/travel/WarpService')
            resources+=('crates/crates.yml',)
        if a.warp_menu:
            prefixes=('net/mandomc/gameplay/travel/WarpService',)
            resources=()
    for name in built.namelist():
        if name.startswith(prefixes) or name in resources:new[name]=built.read(name)
    if not (a.finish_only or a.crate_area or a.warp_menu):
        descriptor=yaml.safe_load(old['plugin.yml'])
        extra=yaml.safe_load(built.read('plugin.yml'))
        for command in ('parkour','parkourfinish','crates'):
            assert command not in descriptor['commands'],f'{command} already exists; review baseline'
            descriptor['commands'][command]=extra['commands'][command]
        for permission in ('mandomc.parkour.play','mandomc.parkour.admin','mandomc.crates.play','mandomc.crates.admin'):
            assert permission not in descriptor['permissions']
            descriptor['permissions'][permission]=extra['permissions'][permission]
        new['plugin.yml']=yaml.safe_dump(descriptor,sort_keys=False).encode()
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as dst:
        for name,data in sorted(new.items()):dst.writestr(name,data)
    changed=[n for n in new if old.get(n)!=new[n]]
    assert set(old)<=set(new)
    assert all(old[n]==new[n] for n in old if n not in changed)
    report={'baseline-sha256':hashlib.sha256(a.baseline.read_bytes()).hexdigest(),
            'candidate-sha256':hashlib.sha256(a.output.read_bytes()).hexdigest(),
            'changed-entries':changed,'all-unrelated-entries-identical':True}
    a.output.with_suffix('.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
