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
FACE_SOURCE={'north':'west','east':'north','south':'east','west':'south','up':'up','down':'down'}

def head_tiles():
    blueprint=json.loads(SOURCE.read_text());head=next(e for e in blueprint['elements'] if e['uuid']=='abcd4ea5-db4d-c179-5800-7b471f232045')
    source=Image.open(ATLAS).convert('RGBA');tiles={};trace={}
    for face,original in FACE_SOURCE.items():
        u,v,U,V=head['faces'][original]['uv'];tile=source.crop((min(u,U),min(v,V),max(u,U),max(v,V)))
        if U<u:tile=tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if V<v:tile=tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        if face=='up':tile=tile.transpose(Image.Transpose.ROTATE_270)
        if face=='down':tile=tile.transpose(Image.Transpose.ROTATE_90)
        tiles[face]=tile
        trace[face]={'sourceFace':original,'sourceUV':[u,v,U,V],'respectSignedUV':True,'clockwiseRotation':90 if face=='up' else 270 if face=='down' else 0}
    # The original rear tile was the flat neck attachment. Complete it by carrying
    # the four adjoining skin boundaries through a continuous Coons surface, then
    # quantizing to the exact original skin palette. No noise or new color family.
    left=[tiles['west'].getpixel((23,y)) for y in range(24)]
    right=[tiles['east'].getpixel((0,y)) for y in range(24)]
    top=[tiles['up'].getpixel((x,23)) for x in range(24)]
    bottom=[tiles['down'].getpixel((x,0)) for x in range(24)]
    palette=sorted({px for face,t in tiles.items() if face!='north' for px in t.getdata()})
    back=tiles['south'].copy()
    crown=tiles['up'].crop((0,0,24,16)).resize((24,24),Image.Resampling.NEAREST)
    for y in range(24):
        v=y/23
        for x in range(24):
            u=x/23
            rgb=[]
            for c in range(3):
                edge=(1-u)*left[y][c]+u*right[y][c]+(1-v)*top[x][c]+v*bottom[x][c]
                corners=(1-u)*(1-v)*top[0][c]+u*(1-v)*top[-1][c]+(1-u)*v*bottom[0][c]+u*v*bottom[-1][c]
                weight=min(1,min(x,y,23-x,23-y)/4)
                rgb.append((1-weight)*(edge-corners)+weight*crown.getpixel((x,y))[c])
            back.putpixel((x,y),min(palette,key=lambda p:sum((p[c]-rgb[c])**2 for c in range(3))))
    for y in range(1,23):back.putpixel((0,y),left[y]);back.putpixel((23,y),right[y])
    for x in range(24):back.putpixel((x,0),top[x]);back.putpixel((x,23),bottom[x])
    tiles['south']=back;trace['south']['completion']='Original crown contours extended over the exposed rear, with exact adjoining skin boundaries and original-palette quantization; no noise'
    return tiles,trace

def build():
    tiles,trace=head_tiles();atlas=Image.new('RGBA',(128,64));uv={}
    for index,(face,tile) in enumerate(tiles.items()):
        ox=(index%3)*24;oy=(index//3)*24;atlas.paste(tile,(ox,oy));uv[face]=[ox,oy,ox+24,oy+24]
    elements=[{'name':'Rancor head','from':[1,0,1],'to':[15,14,15],
               'faces':{f:{'uv':[a/8,b/4,c/8,d/4],'texture':'#0'} for f,(a,b,c,d) in uv.items()}}]
    model={'credit':'Derived from the owner-supplied Rancor Beast head; MandoMC continuous UV wrap, original skin colors.',
           'textures':{'0':'mandomc:item/trophies/rancor_skull','particle':'#0'},'elements':elements,
           'display':{'gui':{'rotation':[25,-135,0],'translation':[0,1,0],'scale':[.7,.7,.7]},
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
      'sourceBone':'h_head','sourceSizePixels':[24,24,24],'trophySizeBlocks':[.875,.875,.875],'reservedSpaceBlocks':[1,1,1],
      'faceOrientationTrace':trace,'changes':['Uniformly scale original head to14 pixels per axis','Rotate original head -90 degrees around Y, retaining all signed face UVs','Match exact original Rancor skin palette and front pixels','Complete flat rear attachment from adjacent original skin boundaries','Remove all four added fang cubes','Center GUI three-quarter front presentation independently of placed transforms'],
      'bossAssetsModified':False},indent=2)+'\n')
    print('Built original-color six-face head, corrected signed UVs/top-bottom rotation, one cube; boss assets unchanged.')
if __name__=='__main__':build()
