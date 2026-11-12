#!/usr/bin/env python3
"""Pixel-grid export of the Galactic credit emblem, keeping vanilla dollar advance at six pixels."""
from pathlib import Path
import json
from PIL import Image
ROOT=Path(__file__).resolve().parent.parent
PATTERN=['#.#..','#####','#.#.#','....#','...#.','..#..','.#...','#....']
def build():
    image=Image.new('RGBA',(5,8));rects=[]
    for y,row in enumerate(PATTERN):
        for x,c in enumerate(row):
            if c=='#':image.putpixel((x,y),(255,255,255,255));rects.append(f'<rect x="{x}" y="{y}" width="1" height="1"/>')
    out=ROOT/'resource-pack/src/assets/mandomc/textures/font/credit.png';out.parent.mkdir(parents=True,exist_ok=True);image.save(out)
    art=ROOT/'resource-pack/artwork/credit_symbol';art.mkdir(parents=True,exist_ok=True)
    (art/'credit.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 5 8" shape-rendering="crispEdges"><g fill="white">'+''.join(rects)+'</g></svg>\n')
    (art/'design.json').write_text(json.dumps({'symbol':'Galactic credit: Aurebesh Resh / angular7 with two strokes through the top bar',
        'reference':'https://starwars.fandom.com/wiki/Galactic_Credit_Standard/Legends','art':'Original hand-pixelled interpretation; no third-party image copied',
        'codepoint':'U+0024','size':[5,8],'ascent':8,'height':8,'advance':6,'pattern':PATTERN,'tint':'white RGBA, transparent background'},indent=2)+'\n')
    font=ROOT/'resource-pack/src/assets/minecraft/font/default.json';data=json.loads(font.read_text())
    data['providers']=[{'type':'bitmap','file':'mandomc:font/credit.png','ascent':8,'height':8,'chars':['$']}]+[p for p in data['providers']if p.get('file')!='mandomc:font/credit.png']
    font.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n');print('Built Galactic credit glyph at U+0024, five visible pixels + one pixel advance; other characters unchanged.')
if __name__=='__main__':build()
