"""Deterministic Ilum review terrain and gameplay manifest; generated worlds stay outside Git."""
from pathlib import Path
import argparse
import io
import json
import math
import struct
import time
import zlib

import nbtlib
import numpy as np
import yaml
from nbtlib import tag as T

CHAPTERS = [
    ("Snowbound Approach", ["The Landing", "Windcut Steps", "Signal Shelter", "Broken Ice", "Temple Approach"]),
    ("The Jedi Temple", ["Hall of Three", "Pilgrim Steps", "Sun Chamber", "Fallen Gallery", "The Listening Hall"]),
    ("The Frozen Lake", ["Lake Observatory", "Ice Floes", "Echo Station", "Frozen Falls", "Floodgate"]),
    ("Crystal Depths", ["Prism Grotto", "Crystal Ledges", "The Singing Vault", "Blue Chasm", "Resonance Hall"]),
    ("Imperial Excavation", ["Power Relay", "Maintenance Gantries", "Drill Control", "Broken Conveyor", "Security Relay"]),
    ("The Gathering", ["Memory Vault", "Quiet Ascent", "Hall of Reflection", "Last Crossing", "Kyber Sanctuary"]),
]
PALETTE = ["air", "stone", "andesite", "deepslate", "snow_block", "packed_ice", "blue_ice",
           "calcite", "smooth_quartz", "stone_bricks", "chiseled_stone_bricks", "polished_deepslate",
           "deepslate_tiles", "cyan_stained_glass", "light_blue_stained_glass", "sea_lantern",
           "emerald_block", "iron_block", "yellow_concrete", "black_concrete", "polished_basalt",
           "prismarine_bricks", "dark_prismarine", "white_concrete", "light_gray_concrete",
           "stone_button[face=wall,facing=west,powered=false]",
           "stone_button[face=wall,facing=east,powered=false]",
           "birch_sign[rotation=12,waterlogged=false]", "birch_sign[rotation=4,waterlogged=false]",
           "cyan_concrete", "blue_concrete", "purple_concrete", "gold_block", "bedrock"]
IDS = {n: i for i, n in enumerate(PALETTE)}
W, H, L, OX, OZ = 592, 192, 512, -16, -16


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("--worldgen-reference", type=Path, required=True)
    args = parser.parse_args()
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    blocks = np.zeros((H, L, W), dtype=np.uint8)
    rng = np.random.default_rng(713024)

    def put(x, y, z, name):
        if 0 <= y < H and OX <= x < OX+W and OZ <= z < OZ+L:
            blocks[y, z-OZ, x-OX] = IDS[name]

    def box(x1, y1, z1, x2, y2, z2, name):
        blocks[max(0,y1):min(H,y2+1), max(0,z1-OZ):min(L,z2-OZ+1),
               max(0,x1-OX):min(W,x2-OX+1)] = IDS[name]

    # A continuous snow mountain, not disconnected floating room boxes.
    zz, xx = np.mgrid[OZ:OZ+L, OX:OX+W]
    edge = np.minimum.reduce([xx+16, 575-xx, zz+16, 495-zz]).clip(0, 36)/36
    tops = (112 + 19*np.sin(xx/41) + 13*np.cos(zz/33) + 8*np.sin((xx+zz)/17)
            + 10*np.sin(xx/13)*np.cos(zz/27)) * edge
    tops = np.maximum(3, tops).astype(int)
    for y in range(H):
        mask = tops >= y
        blocks[y][mask] = IDS["deepslate" if y < 48 else "stone"]
        blocks[y][mask & (tops-y < 5)] = IDS["snow_block"]
    blocks[0] = IDS["bedrock"]

    rooms, signs, proof, controls = [], [], [], []

    def sign_at(x, y, z, lines, east=True):
        put(x,y,z,"birch_sign[rotation=12,waterlogged=false]" if east else "birch_sign[rotation=4,waterlogged=false]")
        signs.append(dict(x=x,y=y,z=z,lines=lines,east=east))

    for index in range(30):
        row, slot = divmod(index,5)
        col = slot if row%2 == 0 else 4-slot
        east = row%2 == 0
        x0, z0, floor = col*112, row*80, 80-row*3
        chapter, titles = CHAPTERS[row]
        kind = "puzzle" if slot%2 == 0 else "parkour"
        phase = index*.83

        def X(t): return x0+t if east else x0+111-t
        def Z(t): return z0+40+int(round(5*math.sin(t/15+phase)*math.sin(math.pi*t/111)))
        def local_box(t1,y1,dz1,t2,y2,dz2,name):
            a,b=sorted((X(t1),X(t2)))
            box(a,y1,z0+40+dz1,b,y2,z0+40+dz2,name)

        # Organic cave profile varies along the route. First ravine/landing are open to the sky.
        ceiling_heights=[]
        for t in range(112):
            x,z=X(t),Z(t)
            radius=14+int(6*(.5+.5*math.sin(t/17+phase)))
            ceiling=18+int(9*(.5+.5*math.sin(t/22+phase)))
            ceiling_heights.append(ceiling)
            for dz in range(-radius,radius+1):
                top=floor+4+int((ceiling-4)*math.sqrt(max(0,1-(dz/(radius+1))**2)))
                if row==0 and slot<2: top=H-1
                # Ice is the cavern surface itself, rather than sparse blue trim on stone walls.
                skin="snow_block" if row==0 else "packed_ice" if row in [2,3] else "stone_bricks" if row in [1,5] else "deepslate_tiles"
                if row in [2,3] and (t//7+dz//4)%4==0:skin="blue_ice"
                strip=blocks[max(1,floor-2):min(H,top+4),z+dz-OZ,x-OX]
                strip[strip!=0]=IDS[skin]
                box(x,floor+1,z+dz,x,top,z+dz,"air")
                put(x,floor,z+dz,"snow_block" if row==0 else "packed_ice" if row in [2,3] else "stone_bricks" if row in [1,5] else "polished_deepslate")
                if row in [2,3]:
                    put(x,floor-1,z+dz,"blue_ice")
            # Alternating wall ribs, low guide lights and inset ice ribs.
            if t%9==0:
                for s in [-1,1]:
                    for dy in range(1,11):
                        put(x,floor+dy,z+s*(radius-1),"blue_ice" if row!=4 else "iron_block")
                    put(x,floor+2,z+s*(radius-2),"sea_lantern")
            # Guaranteed five-wide walking spine except explicitly verified parkour below.
            for dz in range(-2,3):
                put(x,floor,z+dz,"stone_bricks" if row in [1,5] else "snow_block" if row==0 else "polished_deepslate" if row==4 else "packed_ice")
                box(x,floor+1,z+dz,x,floor+4,z+dz,"air")
            if t%10==0:
                put(x,floor,Z(t)+3,"sea_lantern")
            if (t<22 or t>89 or kind=="puzzle") and t<99:
                proof.append([x,floor+1,z])

        # Detailed architecture distinguishes each chapter.
        for t in [35,60,90]:
            x,z=X(t),Z(t)
            if row in [1,5]:
                for side in [-1,1]:
                    sx=x+side*0
                    box(sx-1,floor+1,z+side*11-1,sx+1,floor+13,z+side*11+1,"smooth_quartz")
                    put(sx,floor+6,z+side*10,"sea_lantern")
                    box(sx-2,floor+13,z+side*11-2,sx+2,floor+14,z+side*11+2,"chiseled_stone_bricks")
                box(x-1,floor+15,z-12,x+1,floor+15,z+12,"smooth_quartz")
                for dz in [-8,8]: box(x-1,floor+1,z+dz-1,x+1,floor+3,z+dz+1,"prismarine_bricks")
            elif row in [2,3]:
                for side in ([-1,1] if kind=="parkour" else [1]):
                    for q in range(3):
                        cz=z+side*(10+q*2)
                        height=5+int(rng.integers(2,10))
                        box(x-1+q,floor+1,cz-1,x+1+q,floor+height,cz+1,"light_blue_stained_glass" if q%2 else "blue_ice")
                        put(x+q,floor+height+1,cz,"sea_lantern")
            elif row==4:
                for side in [-1,1]:
                    box(x-1,floor+1,z+side*10-1,x+1,floor+13,z+side*10+1,"polished_basalt")
                    for dy in [3,8,13]:box(x-2,floor+dy,z+side*10-2,x+2,floor+dy,z+side*10+2,"iron_block")
                box(x-1,floor+14,z-10,x+1,floor+14,z+10,"iron_block")
                for dz in range(-8,9): put(x,floor+14,z+dz,"yellow_concrete" if dz%2 else "black_concrete")

        # Checkpoint alcove: every course/puzzle starts at its own safe meditation pad.
        cp=[X(8)+.5,floor+1,Z(8)+.5,-90.0 if east else 90.0]
        box(X(8)-2,floor,Z(8)-2,X(8)+2,floor,Z(8)+2,"prismarine_bricks")
        put(X(8),floor,Z(8),"emerald_block")
        for dx,dz in [(-2,-2),(-2,2),(2,-2),(2,2)]:put(X(8)+dx,floor,Z(8)+dz,"sea_lantern")
        sign_at(X(11),floor+1,Z(11)-3,[f"CHECKPOINT {index+1:02}",titles[slot][:20],"/ilumreview retry","Progress is personal"],east)
        room=dict(id=index,chapter=chapter,title=titles[slot],kind=kind,row=row,col=col,east=east,
                  x0=x0,z0=z0,floor=floor,checkpoint=cp,stations=[],platforms=[])

        if kind=="parkour":
            # Broad catch floor; plugin resets before impact. All mandatory gaps <=2 blocks.
            for t in range(24,87):
                x,z=X(t),Z(t)
                box(x,floor-5,z-24,x,floor+3,z+24,"air")
                box(x,floor-6,z-24,x,floor-6,z+24,"blue_ice" if row!=4 else "deepslate_tiles")
            for n,t in enumerate(range(24,85,5)):
                x,z=X(t),Z(t)
                y=floor
                # At every fifth landing, a wide rest platform makes multiplayer passing easy.
                half=4 if n in [4,9] else 2
                box(x-1,y-2,z-half,x+1,y,z+half,"snow_block" if row==0 else "stone_bricks" if row in [1,5] else "iron_block" if row==4 else "packed_ice")
                put(x,y-1,z,"sea_lantern")
                room["platforms"].append([x,y,z,3,half*2+1])
                proof.append([x,y+1,z])
            sign_at(X(19),floor+1,Z(19)-3,["EASY PARKOUR","1-2 block gaps","Wide rest platforms","Falling = safe retry"],east)
        else:
            # Three local panels. Nothing changes globally when another player interacts.
            for station,t in enumerate([26,52,78]):
                style=(index+station)%6
                choices=["ONE","TWO","THREE"]
                correct=(index*2+station)%3
                if style==0:
                    clue=["Read the temple rune", "I = 1  II = 2", "III = 3", "Rune: "+["I","II","III"][correct]]
                    question="Which number matches?"
                elif style==1:
                    count=correct+1
                    clue=["Crystal survey", "Count bright blocks", "on this plinth", "Then choose 1 / 2 / 3"]
                    question="How many bright blocks?"
                    for j in range(count): put(X(t-11),floor+2,Z(t-11)+7+j*2,"sea_lantern")
                elif style==2:
                    choices=["BLUE","CYAN","PURPLE"]
                    clue=["Follow the signal", "The archive records:",choices[correct],"Choose that signal"]
                    question="Select the recorded color"
                    box(X(t-11)-1,floor+1,Z(t-11)+7,X(t-11)+1,floor+4,Z(t-11)+9,["blue_concrete","cyan_concrete","purple_concrete"][correct])
                elif style==3:
                    clue=["Power calculation",["2 - 1","1 + 1","1 + 2"][correct],"Choose the result","No timer"]
                    question="Set the power level"
                elif style==4:
                    choices=["SUN","ICE","STAR"]
                    clue=["A pilgrim's memory","The next seal is",choices[correct],"Remember the word"]
                    question="Which seal was named?"
                else:
                    clue=["Sequence fragment",["3, 2, ?","4, 3, ?","1, 2, ?"][correct],"Continue by one","Choose 1 / 2 / 3"]
                    question="Complete the sequence"
                # Side panels are reached by a clear branch only a few blocks off the spine.
                for a in range(t-14,t+5):
                    x,z=X(a),Z(a)
                    box(x,floor,z-15,x,floor,z+10,"snow_block" if row==0 else "packed_ice" if row in [2,3] else "stone_bricks" if row==1 else "smooth_quartz" if row==5 else "polished_deepslate")
                    box(x,floor+1,z-15,x,floor+5,z+10,"air")
                sign_at(X(t-10),floor+1,Z(t-10)+6,clue,east)
                # Restore visual count/color artifacts after clearing the station alcove.
                if style==1:
                    box(X(t-11)-1,floor+1,Z(t-11)+6,X(t-11)+1,floor+1,Z(t-11)+13,"smooth_quartz")
                    for j in range(correct+1):put(X(t-11),floor+2,Z(t-11)+7+j*2,"sea_lantern")
                if style==2:
                    box(X(t-11)-1,floor+1,Z(t-11)+7,X(t-11)+1,floor+4,Z(t-11)+9,["blue_concrete","cyan_concrete","purple_concrete"][correct])
                sign_at(X(t-5),floor+1,Z(t-5)-4,[f"SEAL {station+1}/3",question[:22],"Right-click a button","Clue is behind you"],east)
                panel=[]
                for choice in range(3):
                    x,z=X(t),Z(t)-5-choice*3
                    put(x,floor+1,z,"smooth_quartz" if row!=4 else "iron_block")
                    # Button is on the approach face of a two-high freestanding terminal.
                    put(x,floor+2,z,"smooth_quartz" if row!=4 else "iron_block")
                    bx=X(t-1)
                    put(bx,floor+2,z,"stone_button[face=wall,facing=west,powered=false]" if east else "stone_button[face=wall,facing=east,powered=false]")
                    sign_at(X(t-2),floor+1,z,[choices[choice],"Right-click button","Personal progress","No reset for others"],east)
                    button=[bx,floor+2,z]
                    panel.append(button)
                    controls.append(dict(room=index,station=station,choice=choice,at=button))
                room["stations"].append(dict(index=station,correct=correct,buttons=panel,choices=choices))
            sign_at(X(89),floor+1,Z(89)-4,["THREE PERSONAL SEALS","Solve each panel","Door checks only you","Others cannot reset"],east)

        # Private progression uses a permanent bulkhead plus personal threshold transfer.
        x,z=X(104),Z(104)
        box(x,floor+1,z-24,x,floor+32,z+24,"stone_bricks" if row!=4 else "iron_block")
        box(x,floor+1,z-3,x,floor+6,z+3,"cyan_stained_glass")
        for side in [-1,1]:box(x-1,floor+1,z+side*4-1,x+1,floor+9,z+side*4+1,"chiseled_stone_bricks")
        box(x-1,floor+8,z-5,x+1,floor+10,z+5,"smooth_quartz")
        sign_at(X(97),floor+1,Z(97)-4,["NEXT CHECKPOINT" if index<29 else "KYBER EXTRACTION", "Walk toward the seal", "Personal progress", "No shared door reset"],east)

        if index==0:
            # Grounded shuttle silhouette and cargo at the snowy arrival shelf.
            sx,sz=X(14),Z(14)+10
            box(sx-7,floor+1,sz-3,sx+7,floor+3,sz+3,"white_concrete")
            box(sx-4,floor+4,sz-2,sx+3,floor+5,sz+2,"light_gray_concrete")
            box(sx+4,floor+2,sz-2,sx+10,floor+3,sz+2,"white_concrete")
            box(sx+7,floor+3,sz-1,sx+9,floor+3,sz+1,"cyan_stained_glass")
            for wing in [-1,1]:box(sx-6,floor+2,sz+wing*7-2,sx-2,floor+2,sz+wing*7+2,"white_concrete")
            sign_at(X(3),floor+1,Z(3)-4,["ILUM REVIEW","Safe multiplayer zone","~1 hour first journey","/ilumreview help"],east)
        if index==29:
            # A distinct final stepped quartz dais and tall luminous kyber cluster.
            sx,sz=X(90),Z(90)+9
            box(sx-5,floor+1,sz-5,sx+5,floor+1,sz+5,"smooth_quartz")
            box(sx-3,floor+2,sz-3,sx+3,floor+2,sz+3,"prismarine_bricks")
            box(sx-1,floor+3,sz-1,sx+1,floor+10,sz+1,"light_blue_stained_glass")
            put(sx,floor+7,sz,"sea_lantern")
            crystal=[sx,floor+3,sz]
            sign_at(X(93),floor+1,Z(93)+5,["YOUR KYBER CRYSTAL","Solve all 3 seals","Right-click crystal","Then /ilumreview leave"],east)
        if index==22:
            # Full-size excavator barrel and stepped cutting head, visible beside the controls.
            for t in range(36,83):
                x,z=X(t),z0+67
                box(x,floor+1,z0+60,x,floor+20,z0+78,"air")
                put(x,floor,z0+67,"polished_deepslate")
                radius=7 if t<68 else max(1,(83-t)//2)
                for dy in range(-radius,radius+1):
                    for dz in range(-radius,radius+1):
                        r=dy*dy+dz*dz
                        if r<=radius*radius and (t>=68 or r>=(radius-2)**2):
                            put(x,floor+9+dy,z+dz,"iron_block" if t%5==0 else "deepslate_tiles")
                for side in [-1,1]:box(x,floor+1,z+side*9,x,floor+2,z+side*9,"yellow_concrete" if t%2 else "black_concrete")
        rooms.append(room)

    # Signs are the final furnishing pass, so plinths and set pieces cannot erase a clue.
    for sign in signs:
        put(sign['x'],sign['y'],sign['z'],"birch_sign[rotation=12,waterlogged=false]" if sign['east'] else "birch_sign[rotation=4,waterlogged=false]")
    assert len({(s['x'],s['y'],s['z']) for s in signs})==len(signs), 'Overlapping clue labels'
    for button in controls:
        x,y,z=button['at']
        assert blocks[y,z-OZ,x-OX] in [IDS[PALETTE[25]],IDS[PALETTE[26]]],button

    manifest=dict(schema_version=1,world="minecraft:ilumreview",target_minutes=[50,70],
                  spawn=rooms[0]["checkpoint"],crystal=crystal,rooms=rooms,signs=signs,controls=controls)
    (out/"map.yml").write_text(yaml.safe_dump(manifest,sort_keys=False))
    (out/"palette.json").write_text(json.dumps(PALETTE))
    (out/"walk-proof.json").write_text(json.dumps(proof))
    # Verify feet/head/support and every landing against the actual final voxel array.
    for x,y,z in proof:
        assert blocks[y,z-OZ,x-OX]==0 and blocks[y+1,z-OZ,x-OX]==0,(x,y,z,"clearance")
        assert blocks[y-1,z-OZ,x-OX]!=0,(x,y,z,"support")
    for room in rooms:
        for a,b in zip(room["platforms"],room["platforms"][1:]):
            assert abs(a[0]-b[0])-3<=2 and abs(a[1]-b[1])<=1
            assert abs(a[2]-b[2])<=2
    # Save source voxels for independent readback/preview, outside source control.
    np.save(out/"blocks.npy",blocks)
    write_world(blocks,out/"dimension",args.worldgen_reference,manifest["spawn"])
    (out/"generation.json").write_text(json.dumps(dict(rooms=30,chapters=6,checkpoints=30,
        puzzle_rooms=18,personal_panels=54,parkour_courses=12,landings=156,
        max_air_gap=2,route_support_checks=len(proof),voxel_size=[W,H,L],origin=[OX,0,OZ],
        spawn=manifest['spawn'],target_minutes=[50,70],timing_playtested=False),indent=2))
    print((out/"generation.json").read_text(),flush=True)


def packed(values,bits):
    per=64//bits
    a=np.zeros(math.ceil(len(values)/per)*per,dtype=np.uint64)
    a[:len(values)]=values
    a=a.reshape(-1,per)
    longs=np.zeros(len(a),dtype=np.uint64)
    for j in range(per):longs|=a[:,j]<<np.uint64(j*bits)
    return T.LongArray(longs.view(np.int64))


def write_world(blocks,out,reference,spawn):
    (out/"region").mkdir(parents=True,exist_ok=True)
    palette=[]
    for name in PALETTE:
        p={"Name":T.String("minecraft:"+name.split("[")[0])}
        if "[" in name:p["Properties"]=T.Compound({k:T.String(v) for k,v in (s.split("=") for s in name.split("[")[1].rstrip("]").split(","))})
        palette.append(T.Compound(p))
    regions={}
    for cz in range(-1,31):
        for cx in range(-1,36):
            sections=[]
            chunk=blocks[:,cz*16-OZ:cz*16-OZ+16,cx*16-OX:cx*16-OX+16]
            for sy in range(-4,20):
                arr=chunk[sy*16:sy*16+16].ravel() if 0<=sy<12 else np.zeros(4096,dtype=np.uint8)
                unique,inverse=np.unique(arr,return_inverse=True)
                state={"palette":T.List[T.Compound]([palette[int(i)] for i in unique])}
                if len(unique)>1:state["data"]=packed(inverse,max(4,int(len(unique)-1).bit_length()))
                sections.append(T.Compound({"Y":T.Byte(sy),"block_states":T.Compound(state),"biomes":T.Compound({"palette":T.List[T.String]([T.String("minecraft:snowy_slopes")])})}))
            heights=(H-np.argmax((chunk!=0)[::-1],axis=0)+64).astype(np.uint16)
            hm=packed(heights.ravel(),9)
            nbt=nbtlib.File({"DataVersion":T.Int(4790),"xPos":T.Int(cx),"zPos":T.Int(cz),"yPos":T.Int(-4),"Status":T.String("minecraft:features"),"LastUpdate":T.Long(0),"InhabitedTime":T.Long(0),"isLightOn":T.Byte(0),"sections":T.List[T.Compound](sections),"block_entities":T.List[T.Compound]([]),"block_ticks":T.List[T.Compound]([]),"fluid_ticks":T.List[T.Compound]([]),"PostProcessing":T.List[T.List]([T.List[T.Short]([]) for _ in range(24)]),"structures":T.Compound({"starts":T.Compound({}),"References":T.Compound({})}),"Heightmaps":T.Compound({k:hm for k in ["WORLD_SURFACE","MOTION_BLOCKING","MOTION_BLOCKING_NO_LEAVES","OCEAN_FLOOR"]})})
            buf=io.BytesIO();nbt.write(buf)
            regions.setdefault((cx//32,cz//32),{})[(cx%32)+(cz%32)*32]=zlib.compress(buf.getvalue(),6)
        if cz%8==0:print(f"Wrote chunk row {cz}/30",flush=True)
    for (rx,rz),chunks in regions.items():
        header=bytearray(8192);body=bytearray();sector=2
        for index,payload in sorted(chunks.items()):
            record=struct.pack(">I",len(payload)+1)+b"\x02"+payload
            count=math.ceil(len(record)/4096)
            header[index*4:index*4+4]=(sector<<8|count).to_bytes(4,"big")
            header[4096+index*4:4100+index*4]=int(time.time()).to_bytes(4,"big")
            body+=record+b"\0"*(count*4096-len(record));sector+=count
        (out/"region"/f"r.{rx}.{rz}.mca").write_bytes(header+body)
    (out/"data/paper").mkdir(parents=True,exist_ok=True)
    nbtlib.File({"DataVersion":T.Int(4790),"data":T.Compound({"difficulty_settings":T.Compound({"locked":T.Byte(1),"difficulty":T.String("peaceful"),"hardcore":T.Byte(0)}),"spawn":T.Compound({"pos":T.IntArray([int(spawn[0]),int(spawn[1]),int(spawn[2])]),"pitch":T.Float(0),"yaw":T.Float(spawn[3])}),"initialized":T.Byte(1),"game_time":T.Long(6000),"game_type":T.Int(2)})}).save(out/"data/paper/level_overrides.dat",gzipped=True)
    worldgen=nbtlib.load(reference)
    worldgen["data"]["generate_structures"]=T.Byte(0)
    worldgen["data"]["dimensions"]["minecraft:overworld"]["generator"]=T.Compound({"type":T.String("minecraft:flat"),"settings":T.Compound({"biome":T.String("minecraft:the_void"),"features":T.Byte(0),"lakes":T.Byte(0),"layers":T.List[T.Compound]([T.Compound({"block":T.String("minecraft:air"),"height":T.Int(1)})]),"structure_overrides":T.List[T.String]([])})})
    (out/"data/minecraft").mkdir(exist_ok=True)
    worldgen.save(out/"data/minecraft/world_gen_settings.dat",gzipped=True)


if __name__=="__main__":main()
