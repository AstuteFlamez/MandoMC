#!/usr/bin/env python3
"""Recolor Naboo masonry, roofs, acacia/jungle timber and terracotta without moving or reorienting blocks."""
from pathlib import Path
import json,numpy as np
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work-dir',type=Path,required=True);args=parser.parse_args();L=args.work_dir;m=json.loads((L/'generation.json').read_text());v=np.load(L/'updated.npy');names=m['names'].copy();ids={n:i for i,n in enumerate(names)};source=np.load(L/'blocks.npy',mmap_mode='r')[64:208]
# Material families retain identical geometry and the complete original property string.
mapping={'prismarine':'smooth_sandstone','prismarine_bricks':'cut_sandstone','prismarine_stairs':'smooth_sandstone_stairs','prismarine_slab':'smooth_sandstone_slab','prismarine_wall':'sandstone_wall','purpur_block':'oxidized_copper','purpur_stairs':'oxidized_cut_copper_stairs','purpur_slab':'oxidized_cut_copper_slab','purpur_pillar':'quartz_pillar','red_sandstone':'smooth_sandstone','cut_red_sandstone':'cut_sandstone','chiseled_red_sandstone':'chiseled_sandstone','red_sandstone_stairs':'smooth_sandstone_stairs','red_sandstone_slab':'smooth_sandstone_slab','red_sandstone_wall':'sandstone_wall','bricks':'sandstone','nether_bricks':'polished_blackstone_bricks','gray_concrete_powder':'white_concrete_powder','light_gray_concrete_powder':'white_concrete_powder'}
# Wood families retain every connection/orientation property while changing their species.
for n in m['names']:
 base=n.split('[')[0]
 if 'acacia' in base:
  target=base.replace('acacia','birch')
  if target in ['birch_log','birch_wood']:target='stripped_'+target
  mapping[base]=target
 elif 'jungle' in base:
  target=base.replace('jungle','spruce')
  if target in ['spruce_log','spruce_wood']:target='stripped_'+target
  mapping[base]=target
 elif base.endswith('glazed_terracotta'):mapping[base]='white_glazed_terracotta'
 elif base.endswith('terracotta'):
  mapping[base]='oxidized_copper' if base.startswith(('green_','lime_','cyan_')) else 'smooth_sandstone'
recolors=[];mask=np.zeros(v.shape,bool);mask[50:144,16:176,48:208]=True
for i,n in enumerate(m['names']):
 base=n.split('[')[0]
 if base not in mapping:continue
 replacement=mapping[base]+(n[n.index('['):]if'['in n else'');positions=np.argwhere((source==i)&mask)
 if not len(positions):continue
 if replacement not in ids:ids[replacement]=len(names);names.append(replacement)
 assert np.all(v[tuple(positions.T)]==i),'Recolor overlaps a cave change'
 v[tuple(positions.T)]=ids[replacement];recolors.append({'before':n,'after':replacement,'count':len(positions)})
 assert ('['not in n and '['not in replacement)or n.split('[',1)[1]==replacement.split('[',1)[1]
# Nothing moves: every material change is at the exact same cell and retains all properties.
np.save(L/'final.npy',v);diff=np.argwhere(v!=source);world=diff[:,[2,0,1]].astype(np.int32);world[:,0]-=13808;world[:,2]+=3184;np.save(L/'final-changed.npy',world);m['names']=names;m['recolors']=recolors;m['recolored-blocks']=sum(r['count']for r in recolors);m['changed-voxels']=len(diff);(L/'final-generation.json').write_text(json.dumps(m,indent=2));(L/'recolor-report.json').write_text(json.dumps({'blocks':m['recolored-blocks'],'states':recolors,'property-strings-identical':True,'positions-identical':True},indent=2));print('Recolored',m['recolored-blocks'],'blocks across',len(recolors),'states; all positions/properties retained')
