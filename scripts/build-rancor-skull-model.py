#!/usr/bin/env python3
"""Derive a one-block trophy from the supplied Rancor head, preserving its facial layout.

The six head UV regions are baked separately. No boss atlas/model file is modified.
"""
from pathlib import Path
import base64, hashlib, io, json, uuid
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parent.parent
SOURCE=ROOT/'server/config/common/modelengine/blueprints/rancor.bbmodel'
ATLAS=ROOT/'resource-pack/src/assets/modelengine/textures/entity/models/rancor.png'
OUT=ROOT/'resource-pack/src/assets/mandomc'
ART=ROOT/'resource-pack/artwork/rancor_skull'
FACES={'north':(24,130,48,154),'east':(126,100,150,124),'south':(110,126,134,150),
       'west':(0,130,24,154),'up':(132,24,156,48),'down':(134,124,158,148)}
BONE=[(132,115,87),(156,138,106),(179,160,126),(201,183,148),(220,204,170),(237,223,190)]

def build():
    source=Image.open(ATLAS).convert('RGBA'); atlas=Image.new('RGBA',(128,64)); uv={}
    for index,(face,box) in enumerate(FACES.items()):
        tile=source.crop(box); ox=(index%3)*24;oy=(index//3)*24
        for y in range(24):
            for x in range(24):
                r,g,b,a=tile.getpixel((x,y))
                # Retain the source teeth; replace skin shading with the same warm bone family.
                tooth=r>155 and g>120 and b<g
                shade=max(0,min(5,int((r-35)/16)))
                rgb=(r,g,b) if tooth else BONE[shade]
                # The source has tiny eyes and a skin-lined mouth. Broaden the dark cavities
                # within those same anatomical regions to read as an exposed skull.
                if face=='north':
                    eye=((4<=x<=9 or 14<=x<=19) and 8<=y<=11)
                    mouth=(6<=x<=17 and 14<=y<=22)
                    nose=(10<=x<=13 and 11<=y<=13)
                    if (eye and not tooth) or (mouth and r<70) or nose:rgb=(38,32,25)
                tile.putpixel((x,y),(*rgb,255))
        if face in ['up','south']:
            pen=ImageDraw.Draw(tile)
            seam=[(11+(y%4//2),y) for y in range(2,22)]
            pen.line(seam,fill=(*BONE[1],255),width=1)
            for y in range(4,21,4):pen.line([(10,y-1),(14,y+1)],fill=(*BONE[2],255))
        atlas.paste(tile,(ox,oy));uv[face]=[ox,oy,ox+24,oy+24]
    # Small sampled ivory region for four physical fang tips; complements painted source teeth.
    for y in range(8):
        for x in range(8):atlas.putpixel((80+x,y),(*(BONE[4 if (x+y)%5 else 5]),255))
    elements=[{'name':'Rancor cranium','from':[1,0,1],'to':[15,14,15],
               'faces':{f:{'uv':[a/8,b/4,c/8,d/4],'texture':'#0'} for f,(a,b,c,d) in uv.items()}}]
    for name,lo,hi in [('left upper fang',[4.7,4.7,.15],[5.55,7.2,1.15]),('right upper fang',[10.45,4.7,.15],[11.3,7.2,1.15]),
                       ('left lower fang',[4.4,.9,.25],[5.15,2.65,1.15]),('right lower fang',[10.85,.9,.25],[11.6,2.65,1.15])]:
        elements.append({'name':name,'from':lo,'to':hi,'faces':{f:{'uv':[10,0,11,2],'texture':'#0'} for f in FACES}})
    model={'credit':'Derived from the owner-supplied Rancor Beast head; MandoMC skull UVs and bone finish.',
           'textures':{'0':'mandomc:item/trophies/rancor_skull','particle':'#0'},'elements':elements,
           'display':{'gui':{'rotation':[22,225,0],'translation':[0,0,0],'scale':[.9,.9,.9]},
                      'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.4,.4,.4]},
                      'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
                      'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},
                      'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.5,.5,.5]}}}
    path=OUT/'textures/item/trophies/rancor_skull.png';path.parent.mkdir(parents=True,exist_ok=True);atlas.save(path)
    for path,data in [(OUT/'models/trophies/rancor_skull.json',model),(OUT/'items/trophies/rancor_skull.json',{'model':{'type':'minecraft:model','model':'mandomc:trophies/rancor_skull'}})]:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
    # Retain an editable native Blockbench model with the complete six-face atlas embedded.
    data=io.BytesIO();atlas.save(data,format='PNG');texid=str(uuid.uuid5(uuid.NAMESPACE_URL,'mandomc:rancor_skull:atlas'))
    cubes=[]
    for i,element in enumerate(elements):
        cube={**element,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'mandomc:rancor_skull:'+str(i))),'type':'cube','origin':[8,7,8],'box_uv':False,
              'faces':{f:{'uv':[v['uv'][0]*8,v['uv'][1]*4,v['uv'][2]*8,v['uv'][3]*4],'texture':0} for f,v in element['faces'].items()}}
        cubes.append(cube)
    bb={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':'Rancor Skull','resolution':{'width':128,'height':64},
        'elements':cubes,'outliner':[c['uuid'] for c in cubes],'textures':[{'uuid':texid,'name':'rancor_skull.png','id':'0','width':128,'height':64,'uv_width':128,'uv_height':64,'source':'data:image/png;base64,'+base64.b64encode(data.getvalue()).decode()}],
        'display':model['display']}
    ART.mkdir(parents=True,exist_ok=True);(ART/'rancor_skull.bbmodel').write_text(json.dumps(bb,indent=2)+'\n')
    (ART/'provenance.json').write_text(json.dumps({'source':'server/config/common/modelengine/blueprints/rancor.bbmodel','sourceSha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
      'sourceAtlas':'resource-pack/src/assets/modelengine/textures/entity/models/rancor.png','atlasSha256':hashlib.sha256(ATLAS.read_bytes()).hexdigest(),
      'sourceBone':'h_head','sourceSizePixels':[24,24,24],'trophySizeBlocks':[.875,.875,.928125],'reservedSpaceBlocks':[1,1,1],
      'facesFromSourcePixels':FACES,'changes':['Uniformly scale the original head to14 pixels per axis','Map all six source head faces into a separate complete bone atlas','Retain facial layout and teeth; expose dark eye/nose/mouth cavities','Add four small fang tips over the source canine positions'],
      'bossAssetsModified':False},indent=2)+'\n')
    print('Built complete six-face skull, five elements, one-block placement bounds; boss assets unchanged.')
if __name__=='__main__':build()
