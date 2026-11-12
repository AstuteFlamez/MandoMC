#!/usr/bin/env python3
"""Reproduce the test carriers from the original jetpack, preserving texture/UVs and final size."""
import copy,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'resource-pack/src/assets/mandomc'
def generate():
 original=json.loads((ASSETS/'models/equipment/boba_jetpack_attached.json').read_text())
 # Installed client: ArmorStand head pivot 1/16, renderer origin 1.501,
 # CustomHeadLayer -0.25 translation and 0.625 item scale.
 head=1.501-1/16+.25
 target=(1.501-6/16)*.9375
 contact=3/16*.9375+.005-.125
 for mode in ['carrier','wearer']:
  for distance in range(3):
   name=f'jetpack_test_{mode}_{distance}'
   if mode=='carrier':
    model={'parent':'mandomc:equipment/boba_jetpack_attached'}
    ty=(target-1.8-head)*16/.625;scale=1.6
   else:
    model=copy.deepcopy(original)
    # Five native cloud mounts add 2.5 blocks. Half-size authored elements with a 3.2
    # head scale retain final size while leaving all element coordinates within [-16,32].
    delta=(target-(1.8+2.5)-head+3.125)*16/2
    for element in model['elements']:
     for key in ['from','to']:
      element[key]=[(v-8)*.5+8+(delta if axis==1 else 0) for axis,v in enumerate(element[key])]
     if 'rotation' in element:
      element['rotation']['origin']=[(v-8)*.5+8+(delta if axis==1 else 0)
         for axis,v in enumerate(element['rotation']['origin'])]
    ty=-80;scale=3.2
   model['display']={'head':{'translation':[0,ty,(contact+distance*.15)*16/.625],'scale':[scale]*3}}
   yield f'models/equipment/{name}.json',model
   yield f'items/equipment/{name}.json',{'model':{'type':'minecraft:model','model':f'mandomc:equipment/{name}'}}
if __name__=='__main__':
 for name,model in generate():
  path=ASSETS/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(model,indent=2)+'\n')
