#!/usr/bin/env python3
"""Assemble ENCHANTMENT entirely from existing item-label pixels and the existing gray frame."""
from pathlib import Path
import json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parent.parent
LABELS=ROOT/'resource-pack/src/assets/mandomc/textures/font/item_labels'
LETTER_SOURCES={'E':('material',43,51),'N':('component',61,69),'C':('component',9,17),'H':('mythic',45,53),'A':('material',21,29),'T':('material',31,41),'M':('material',9,19)}
LETTERS=[(letter,*LETTER_SOURCES[letter]) for letter in 'ENCHANTMENT']
INK={(255,255,255,255),(163,208,227,255)}
def build():
    frame=Image.open(LABELS/'weapon.png').convert('RGBA');width=18+sum(b-a for _,_,a,b in LETTERS)+2*(len(LETTERS)-1)
    out=Image.new('RGBA',(width,18));out.paste(frame.crop((0,0,9,18)),(0,0));out.paste(frame.crop((frame.width-9,0,frame.width,18)),(width-9,0))
    for x in range(9,width-9):out.paste(frame.crop((20,0,21,18)),(x,0))
    position=9;mapping=[]
    for letter,name,a,b in LETTERS:
        source=Image.open(LABELS/(name+'.png')).convert('RGBA')
        for y in range(18):
            for x in range(a,b):
                pixel=source.getpixel((x,y))
                if pixel in INK:out.putpixel((position+x-a,y),pixel)
        mapping.append({'letter':letter,'source':name+'.png','sourceColumns':[a,b],'targetColumns':[position,position+b-a],'sourceSha256':hashlib.sha256((LABELS/(name+'.png')).read_bytes()).hexdigest()});position+=b-a+2
    out.save(LABELS/'enchantment.png')
    font=ROOT/'resource-pack/src/assets/mandomc/font/enchantment.json';font.write_text(json.dumps({'providers':[{'type':'bitmap','file':'mandomc:font/item_labels/enchantment.png','ascent':8,'height':9,'chars':['\ue000']}]},indent=2)+'\n')
    art=ROOT/'resource-pack/artwork/enchantment_label';art.mkdir(exist_ok=True);(art/'provenance.json').write_text(json.dumps({'word':'ENCHANTMENT','font':'mandomc:enchantment','glyph':'U+E000','size':[width,18],'frame':'weapon.png','frameCenterColumn':20,'letters':mapping,'newLetterPixels':False},indent=2)+'\n')
    print('Built ENCHANTMENT from existing letter masks and the unchanged gray frame:',width,'x18')
if __name__=='__main__':build()
