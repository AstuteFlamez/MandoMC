#!/usr/bin/env python3
"""Render original helmet geometry/UVs through actual ground scales, with identical camera/framing."""
import argparse,io,json,math,zipfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--baseline',required=True,type=Path);a=p.parse_args();pack=zipfile.ZipFile(a.baseline)
def rotation(v):
 x,y,z=np.radians(v);rx=np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]]);ry=np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]]);rz=np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
 return rx@ry@rz

def render(model,atlas,size=224,check_bounds=True):
 pose=model['display']['gui'];rot=rotation(pose.get('rotation',[0,0,0]));scale=np.array(pose.get('scale',[1]*3));tr=np.array(pose.get('translation',[0]*3));quads=[];bounds=[]
 for el in model['elements']:
  x,y,z=el['from'];X,Y,Z=el['to'];r=el.get('rotation',{});angles=[r.get(k,0) for k in ('x','y','z')]
  if 'axis' in r:angles['xyz'.index(r['axis'])]=r['angle']
  er=rotation(angles);origin=np.array(r.get('origin',[8]*3))
  def transform(v):return rot@((er@(np.array(v)-origin)+origin-8)*scale)+tr
  faces={'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)]}
  for face,uv in el['faces'].items():
   corners=[transform(v) for v in faces[face]];normal=np.cross(corners[1]-corners[0],corners[2]-corners[1]);normal/=np.linalg.norm(normal)
   # Face winding above is inward, hence visible normal points away from viewer.
   if normal[2]>=-1e-8:continue
   bounds.extend(corners);u,v,U,V=uv['uv'];n=max(1,round(abs(U-u)*atlas.width/16));m=max(1,round(abs(V-v)*atlas.height/16));p0,p1,p2,p3=corners
   for j in range(m):
    for i in range(n):
     color=atlas.getpixel((min(atlas.width-1,max(0,int((u+(U-u)*(i+.5)/n)*atlas.width/16))),min(atlas.height-1,max(0,int((v+(V-v)*(j+.5)/m)*atlas.height/16)))))
     if color[3]==0:continue
     pts=[p0*(1-s)*(1-t)+p1*s*(1-t)+p2*s*t+p3*(1-s)*t for s,t in [(i/n,j/m),((i+1)/n,j/m),((i+1)/n,(j+1)/m),(i/n,(j+1)/m)]]
     shade=.8+.2*max(0,float((-normal)@np.array([-.25,.8,.55])));color=tuple(round(c*shade) for c in color[:3])+(color[3],);quads.append((float(np.mean(pts,axis=0)[2]),pts,color))
 im=Image.new('RGBA',(size,size),'#8b8b8b');draw=ImageDraw.Draw(im)
 for depth,pts,c in sorted(quads,key=lambda q:q[0]):draw.polygon([(size/2+v[0]*size/16,size/2-v[1]*size/16) for v in pts],fill=c)
 if bounds and check_bounds:
  b=np.array(bounds);assert np.max(np.abs(b[:,:2]))<8, f'GUI clips: {b.min(0)}, {b.max(0)}'
 return im
sheet=Image.new('RGB',(1000,700),'#111923');d=ImageDraw.Draw(sheet)
d.text((20,16),'DROPPED HELMET SIZE - actual geometry, UVs and display.ground scale',fill='white')
d.text((20,38),'Identical 35-degree camera / 16-unit framing. Durasteel is unchanged. Offline orthographic preview.',fill='#b9cadb')
for i,name in enumerate(['plastoid','beskar','durasteel']):
 path=f'assets/mandomc/models/item/armor/{name}_helmet.json';old=json.loads(pack.read(path));new=json.loads((ROOT/'resource-pack/src'/path).read_text());tex=next(v for k,v in old['textures'].items() if k!='particle');ns,key=tex.split(':');atlas=Image.open(io.BytesIO(pack.read(f'assets/{ns}/textures/{key}.png'))).convert('RGBA');x=20+i*325
 d.text((x,72),name.upper()+(' (REFERENCE)' if name=='durasteel' else ''),fill='white')
 for j,(label,model) in enumerate([('BEFORE',old),('AFTER',new)]):
  ground=model.get('display',{}).get('ground',{});scale=ground.get('scale',[1]*3);assert ground.get('rotation',[0,0,0])==[0,0,0]
  # Camera rotation is outside the item; the actual ground pose retains zero rotation.
  model['display']['gui']={'rotation':[35,-135,0],'scale':scale,'translation':ground.get('translation',[0,0,0])};im=render(model,atlas,256);y=98+j*292;sheet.paste(im,(x,y));d.text((x,y+262),label+' ground scale '+str(scale[0]),fill='white')
out=ROOT/'docs/assets/helmet-ground-preview.png';sheet.save(out);print(out)
