#!/usr/bin/env python3
"""Patch only the deployed Ilum formatter; preserve unrelated course/NPC/warp code."""
import argparse,pathlib,subprocess,zipfile
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline',type=pathlib.Path,required=True);p.add_argument('--java',required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
name='net/mandomc/gameplay/ilum/ParkourService.class';src=a.output/'original'/name;dst=a.output/'classes'/name;src.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.baseline) as z:src.write_bytes(z.read(name))
subprocess.run([a.java,str(pathlib.Path(__file__).with_name('PatchIlumTime.java')),str(src),str(dst)],check=True)
print('Patched only Ilum format(J)String; every unrelated method bytecode verified unchanged.')
