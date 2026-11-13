from pathlib import Path
import argparse,itertools,json,math,zipfile
import numpy as np
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();PACK=a.baseline
def rotation(v):
 x,y,z=np.radians(v);return np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])@np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])@np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
def vertices(m):
 points=[]
 for e in m['elements']:
  r=e.get('rotation',{});a=[r.get(k,0)for k in 'xyz']
  if 'axis'in r:a['xyz'.index(r['axis'])]=r['angle']
  o=np.array(r.get('origin',[8]*3));points.extend([rotation(a)@(np.array(p)-o)+o-8 for p in itertools.product(*zip(e['from'],e['to']))])
 return np.array(points)
with zipfile.ZipFile(PACK) as z:
 models={n:json.loads(z.read(f'assets/mandomc/models/item/armor/{n}_helmet.json')) for n in ['beskar','durasteel','plastoid']}
vs={n:vertices(m)for n,m in models.items()}
def areas(v):return [float(np.prod(np.ptp((rotation([pitch,yaw,0])@v.T).T,axis=0)[:2]))for pitch in (20,35,50)for yaw in np.arange(0,360,22.5)]
target=np.array(areas(vs['durasteel']));report={}
for n,v in vs.items():
 s=float(np.exp(np.mean(np.log(target/np.array(areas(v))))/2));s=round(s,4)
 before=models[n].get('display',{}).get('ground',{}).get('scale',[1]*3)[0]
 report[n]=dict(rawBounds=[(v.min(0)+8).tolist(),(v.max(0)+8).tolist()],geometryDimensions=np.ptp(v,axis=0).tolist(),beforeScale=before,recommendedScale=s,projectedAreaRatioAfterGeometricMean=float(np.exp(np.mean(np.log(np.array(areas(v))*s*s/target)))),projectionSamples=48)
print(json.dumps(report,indent=2));a.output.write_text(json.dumps(report,indent=2)+'\n')
