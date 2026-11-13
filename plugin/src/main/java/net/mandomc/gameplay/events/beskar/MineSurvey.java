package net.mandomc.gameplay.events.beskar;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Bounded actual collision survey, with ordinary one-block jumps and a 0.6 by 1.8 player body. */
public final class MineSurvey {
    public record Pos(int x,int y,int z) {
        public Block block(World world){return world.getBlockAt(x,y,z);}
        public List<Integer> list(){return List.of(x,y,z);}
    }
    private record Walk(Pos floor,double feet) {}
    private final World world;
    private final List<Pos> floors;
    private final Pos entrance;
    private final Map<Pos,Walk> walk=new HashMap<>();
    private final Map<Long,List<Walk>> columns=new HashMap<>();
    private final Map<Pos,List<BoundingBox>> shapes=new HashMap<>();
    private final Map<Pos,Pos> parents=new HashMap<>();
    private final Set<Pos> reached=new HashSet<>();
    private final ArrayDeque<Walk> queue=new ArrayDeque<>();
    private int scanned;
    private boolean seeded,done;
    public MineSurvey(World world,List<Pos> floors,Pos entrance){this.world=world;this.floors=floors;this.entrance=entrance;}
    private long column(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private List<BoundingBox> shape(Pos p){return shapes.computeIfAbsent(p,k->List.copyOf(k.block(world).getCollisionShape().getBoundingBoxes()));}
    public boolean clear(double x,double feet,double z) {
        var body=new BoundingBox(x-.3,feet+.001,z-.3,x+.3,feet+1.8,z+.3);
        for(int bx=(int)Math.floor(body.getMinX());bx<=Math.floor(body.getMaxX()-.001);bx++)
            for(int by=(int)Math.floor(body.getMinY());by<=Math.floor(body.getMaxY()-.001);by++)
                for(int bz=(int)Math.floor(body.getMinZ());bz<=Math.floor(body.getMaxZ()-.001);bz++) {
                    var p=new Pos(bx,by,bz);var material=p.block(world).getType();
                    if(material==Material.WATER||material==Material.LAVA)return false;
                    for(var box:shape(p))if(body.overlaps(box.clone().shift(bx,by,bz)))return false;
                }
        return true;
    }
    public boolean step(int budget) {
        int used=0;
        while(scanned<floors.size()&&used++<budget){
            Pos p=floors.get(scanned++);double top=-1;
            for(var box:shape(p))if(box.getMinX()<=.5&&box.getMaxX()>=.5&&box.getMinZ()<=.5&&box.getMaxZ()>=.5)top=Math.max(top,box.getMaxY());
            if(top<.5||top>1||!clear(p.x+.5,p.y+top,p.z+.5))continue;
            var node=new Walk(p,p.y+top);walk.put(p,node);columns.computeIfAbsent(column(p.x,p.z),k->new ArrayList<>()).add(node);
        }
        if(scanned<floors.size())return false;
        if(!seeded){seeded=true;var seed=walk.get(entrance);if(seed==null)throw new IllegalStateException("Mine entrance is no longer walkable: "+entrance);reached.add(entrance);parents.put(entrance,null);queue.add(seed);}
        while(!queue.isEmpty()&&used++<budget){
            var a=queue.removeFirst();
            for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(var b:columns.getOrDefault(column(a.floor.x+d[0],a.floor.z+d[1]),List.of())){
                if(reached.contains(b.floor)||Math.abs(a.feet-b.feet)>1.001)continue;
                double top=Math.max(a.feet,b.feet);
                // Check the whole crossing, including protruding walls/models at chunk/tile edges.
                boolean clear=true;
                for(double t:new double[]{.25,.5,.75})if(!clear(a.floor.x+.5+d[0]*t,top,a.floor.z+.5+d[1]*t)){clear=false;break;}
                if(clear){reached.add(b.floor);parents.put(b.floor,a.floor);queue.add(b);}
            }
        }
        done=queue.isEmpty();return done;
    }
    public List<Pos> route(Pos floor){
        if(!reached(floor))return List.of();var path=new ArrayList<Pos>();
        for(Pos at=floor;at!=null;at=parents.get(at))path.add(at);return List.copyOf(path);
    }
    public boolean reached(Pos floor){return done&&reached.contains(floor);}
    public double feet(Pos floor){return walk.get(floor).feet;}
    public int reachedCount(){return reached.size();}
}
