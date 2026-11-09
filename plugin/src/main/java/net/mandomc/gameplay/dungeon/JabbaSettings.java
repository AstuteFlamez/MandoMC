package net.mandomc.gameplay.dungeon;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
/** Reviewed coordinates/tuning; private gate artwork remains in ignored runtime map data. */
public record JabbaSettings(String profile,String world,String blueprints,int animation,Pos min,Pos max,List<Room> rooms,Map<String,Double> loot) {
 public record Pos(int x,int y,int z){public String key(){return x+","+y+","+z;}public Location location(World w){return new Location(w,x,y,z);}public static Pos parse(String s){var a=s.split(",");if(a.length!=3)throw new IllegalArgumentException("Invalid position");return new Pos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));}}
 public record Chest(Pos pos,BlockFace face){}
 public record Room(int gate,Pos movement,List<Pos> spawns,String mob,int count,List<Chest> chests){}
 public static JabbaSettings read(ConfigurationSection c){
  if(c.getInt("schema-version")!=1)throw new IllegalArgumentException("jabba: schema 1 required");
  String profile=c.getString("profile",""),world=c.getString("world",""),blueprints=c.getString("blueprints","");int animation=c.getInt("animation-ticks");
  if(!profile.matches("[a-z0-9_]+")||NamespacedKey.fromString(world)==null||!blueprints.matches("jabba/[a-z0-9_-]+\\.yml")||animation<4||animation>100)throw new IllegalArgumentException("jabba: invalid profile/world/blueprint/animation");
  var min=pos(c.getList("bounds.min"));var max=pos(c.getList("bounds.max"));if(min.x>max.x||min.y>max.y||min.z>max.z||max.x-min.x>512||max.z-min.z>512||(long)(max.x-min.x+1)*(max.z-min.z+1)>100000)throw new IllegalArgumentException("jabba: invalid bounds");
  var section=c.getConfigurationSection("rooms");if(section==null||section.getKeys(false).isEmpty()||section.getKeys(false).size()>32)throw new IllegalArgumentException("jabba: missing rooms");
  var rooms=new ArrayList<Room>();var gates=new HashSet<Integer>();var caches=new HashSet<String>();
  for(int n=1;n<=section.getKeys(false).size();n++){
   var r=section.getConfigurationSection(""+n);if(r==null)throw new IllegalArgumentException("jabba: rooms must be consecutive");int gate=r.getInt("gate"),count=r.getInt("count-per-spawn");var move=pos(r.getList("movement"));String mob=r.getString("mob","");
   if(!gates.add(gate)||count<1||count>8||!Set.of("guards","Bossk","BobaFett","Rancor").contains(mob)||Math.abs(move.x)+Math.abs(move.y)+Math.abs(move.z)<1||Math.abs(move.x)+Math.abs(move.y)+Math.abs(move.z)>32)throw new IllegalArgumentException("jabba: invalid gate/mobs/movement");
   var spawns=new ArrayList<Pos>();for(var s:r.getList("spawns",List.of()))spawns.add(pos((List<?>)s));var chests=new ArrayList<Chest>();
   for(var s:r.getList("chests",List.of())){var list=(List<?>)s;if(list.size()!=4)throw new IllegalArgumentException("jabba: chest requires x/y/z/facing");var p=pos(list.subList(0,3));var face=BlockFace.valueOf(list.get(3).toString());if(!Set.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST).contains(face)||!caches.add(p.key()))throw new IllegalArgumentException("jabba: duplicate/invalid chest");chests.add(new Chest(p,face));}
   if(spawns.isEmpty()||spawns.size()>16||spawns.size()*count>64||chests.size()>32)throw new IllegalArgumentException("jabba: invalid room mob/cache budget");
   for(var p:spawns)inside(p,min,max);for(var p:chests)inside(p.pos,min,max);rooms.add(new Room(gate,move,List.copyOf(spawns),mob,count,List.copyOf(chests)));
  }
  var loot=new LinkedHashMap<String,Double>();var pool=c.getConfigurationSection("loot");if(pool==null)throw new IllegalArgumentException("jabba: missing loot");for(String id:pool.getKeys(false)){double weight=pool.getDouble(id);if(!id.matches("[a-z0-9_]+")||!Double.isFinite(weight)||weight<=0||weight>1000)throw new IllegalArgumentException("jabba: invalid loot weight");loot.put(id,weight);}if(loot.isEmpty()||loot.size()>64)throw new IllegalArgumentException("jabba: invalid loot pool");
  return new JabbaSettings(profile,world,blueprints,animation,min,max,List.copyOf(rooms),Collections.unmodifiableMap(loot));
 }
 private static Pos pos(List<?> a){if(a==null||a.size()!=3||a.stream().anyMatch(v->!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue()))throw new IllegalArgumentException("jabba: integer xyz required");return new Pos(((Number)a.get(0)).intValue(),((Number)a.get(1)).intValue(),((Number)a.get(2)).intValue());}
 private static void inside(Pos p,Pos min,Pos max){if(p.x<min.x||p.x>max.x||p.y<min.y||p.y>max.y||p.z<min.z||p.z>max.z)throw new IllegalArgumentException("jabba: room position outside bounds");}
 public boolean contains(Location l){return l.getWorld().getKey().toString().equals(world)&&l.getX()>=min.x&&l.getX()<=max.x+1&&l.getY()>=min.y&&l.getY()<=max.y+1&&l.getZ()>=min.z&&l.getZ()<=max.z+1;}
}
