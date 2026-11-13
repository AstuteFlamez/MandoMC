#!/usr/bin/env python3
"""Render exported face UVs, GUI transform and matching badge for asset review (offline)."""
from pathlib import Path
import importlib.util,json,math
import numpy as np
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('skull',ROOT/'scripts/build-rancor-skull-model.py');builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
model=json.loads((builder.OUT/'models/trophies/rancor_skull.json').read_text());atlas=Image.open(builder.OUT/'textures/item/trophies/rancor_skull.png').convert('RGBA')

def render(eye,gui=False,size=260):
 eye=np.array(eye,dtype=float);forward=-eye/np.linalg.norm(eye);up0=np.array([0,1,0]if abs(forward[1])<.99 else[0,0,-1]);right=np.cross(forward,up0);right/=np.linalg.norm(right);up=np.cross(right,forward)
 im=Image.new('RGBA',(size,size),(21,27,37,255));draw=ImageDraw.Draw(im);quads=[];center=np.array([8,7,8]);light=np.array([-.3,.8,1]);light/=np.linalg.norm(light);rotation=np.eye(3);scale=1;translation=np.zeros(3)
 if gui:
  d=model['display']['gui'];x,y,z=np.radians(d['rotation']);rx=np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]]);ry=np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]]);rotation=rx@ry;scale=d['scale'][0];translation=np.array(d['translation']);center=np.array([8,8,8])
 def transform(p):return rotation@(np.array(p)-center)*scale+translation
 factor=size/(19 if gui else 19)
 def project(p):return(size/2+float(p@right)*factor,size/2-float(p@up)*factor)
 for element in model['elements']:
  x,y,z=element['from'];X,Y,Z=element['to'];planes={
   'north':([(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],(0,0,-1)),
   'south':([(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],(0,0,1)),
   'east':([(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],(1,0,0)),
   'west':([(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],(-1,0,0)),
   'up':([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],(0,1,0)),
   'down':([(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],(0,-1,0))}
  for face,uv in element['faces'].items():
   vertices,normal=planes[face];normal=rotation@np.array(normal)
   if normal@eye<=0:continue
   p0,p1,p2,p3=[transform(p)for p in vertices];u,v,U,V=uv['uv'];w=round(abs(U-u)*atlas.width/16);h=round(abs(V-v)*atlas.height/16)
   for j in range(h):
    for i in range(w):
     points=[p0*(1-a)*(1-b)+p1*a*(1-b)+p2*a*b+p3*(1-a)*b for a,b in[(i/w,j/h),((i+1)/w,j/h),((i+1)/w,(j+1)/h),(i/w,(j+1)/h)]];px=atlas.getpixel((int((u+(U-u)*(i+.5)/w)*atlas.width/16),int((v+(V-v)*(j+.5)/h)*atlas.height/16)));shade=.78+.22*max(0,float(normal@light));rgb=tuple(round(c*shade)for c in px[:3]);quads.append((float((sum(points)/4)@forward),[project(p)for p in points],rgb))
 for _,poly,color in sorted(quads,key=lambda q:q[0],reverse=True):draw.polygon(poly,fill=color)
 return im

sheet=Image.new('RGB',(1040,1080),'#101620');d=ImageDraw.Draw(sheet);d.text((20,14),'RANCOR HEAD REVISION - ORIGINAL COLORS / CORRECTED UV ORIENTATION',fill='#e6d2b3')
views=[('FRONT',(0,0,-1)),('BACK',(0,0,1)),('LEFT',(-1,0,0)),('RIGHT',(1,0,0)),('TOP',(0,1,0)),('UNDERSIDE',(0,-1,0))]
for i,(name,eye)in enumerate(views):
 x=(i%3)*260;y=50+(i//3)*285;sheet.paste(render(eye),(x,y));d.text((x+12,y+4),name,fill='white')
x=780;sheet.paste(render((0,0,1),gui=True),(x,50));d.text((x+12,54),'GUI TRANSFORM',fill='white')
slot=Image.new('RGB',(18,18),'#8b8b8b');slot.paste(render((0,0,1),gui=True,size=16).convert('RGB'),(1,1));sheet.paste(slot.resize((144,144),Image.Resampling.NEAREST),(838,350));d.text((800,510),'16px inventory view (8x)',fill='#b4c6da')
# Reference uses the actual original rig's signed UVs under the same -90Y orientation.
reference=Image.new('RGBA',(128,64));blueprint=json.loads(builder.SOURCE.read_text());head=next(e for e in blueprint['elements']if e['uuid']=='abcd4ea5-db4d-c179-5800-7b471f232045');source=Image.open(builder.ATLAS).convert('RGBA')
for index,(face,original)in enumerate(builder.FACE_SOURCE.items()):
 u,v,U,V=head['faces'][original]['uv'];tile=source.crop((min(u,U),min(v,V),max(u,U),max(v,V)))
 if U<u:tile=tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
 if V<v:tile=tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
 if face=='up':tile=tile.transpose(Image.Transpose.ROTATE_270)
 if face=='down':tile=tile.transpose(Image.Transpose.ROTATE_90)
 reference.paste(tile,((index%3)*24,(index//3)*24))
d.text((20,650),'ORIGINAL RIG REFERENCE - SAME PALETTE AND FACE DIRECTIONS (NORMALIZED)',fill='white');revised=atlas;atlas=reference
for i,(name,eye)in enumerate(views):
 sheet.paste(render(eye,size=148),(10+i*172,680));d.text((14+i*172,832),name.lower()+' <- '+builder.FACE_SOURCE[{'FRONT':'north','BACK':'south','LEFT':'west','RIGHT':'east','TOP':'up','UNDERSIDE':'down'}[name]],fill='#b4c6da')
atlas=revised
# Exact established badge pixels; same 2:1 source-to-game glyph scale.
labels=ROOT/'resource-pack/src/assets/mandomc/textures/font/item_labels';legendary=Image.open(labels/'legendary.png').convert('RGBA');trophy=Image.open(labels/'trophy.png').convert('RGBA');d.text((20,875),'RARITY + CATEGORY - EXISTING LETTER PIXELS',fill='white')
sheet.paste(legendary.resize((324,54),Image.Resampling.NEAREST),(20,900));sheet.paste(trophy.resize((240,54),Image.Resampling.NEAREST),(350,900));d.text((20,985),'One cube; no added fang blocks. Original front/side/top/underside colors and directions retained.',fill='#c1c9d5');d.text((20,1007),'Rear neck tile completed using original crown contours and adjoining skin edges. No new color family.',fill='#c1c9d5');d.text((20,1029),'Offline geometry/UV contact sheet. Native Blockbench GUI also checked; placed size stays 0.875 blocks.',fill='#c1c9d5');out=ROOT/'docs/assets/rancor-head-revision.png';sheet.save(out);print(out)
