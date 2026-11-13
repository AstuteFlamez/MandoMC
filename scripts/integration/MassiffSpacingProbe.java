package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.decor.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

/** Forced native approaching/crossing/standing paths, then sustained three-dog wandering. */
public final class MassiffSpacingProbe extends JavaPlugin {
 private AmbientMassiffs dogs;private MandoPlugin plugin;private org.bukkit.scheduler.BukkitTask sampler;private int checks,phase,tick;
 private final Map<String,Location> previous=new HashMap<>();private final Map<String,Double> distances=new HashMap<>();
 private double minimum=Double.MAX_VALUE;private final List<String> evidence=new ArrayList<>();private NativeProbePlayers actors;private Player viewer;
 private interface Step{void run()throws Exception;}
 private void later(int ticks,Step s){Bukkit.getScheduler().runTaskLater(this,()->{try{s.run();}catch(Throwable e){fail(e);}},ticks);}
 private void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
 private void save(String name,String text){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),text+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MASSIFF SPACING FAILED",e);save("FAILED",e.toString());finish();}
 private void finish(){if(sampler!=null)sampler.cancel();if(actors!=null){actors.close();actors=null;}Bukkit.shutdown();}
 public void onEnable(){later(120,this::begin);}
 private void begin()throws Exception{
  plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");dogs=Bukkit.getServicesManager().load(AmbientMassiffs.class);
  actors=new NativeProbePlayers("SpaceProbe");viewer=actors.create(new Location(dogs.bodies().values().iterator().next().getWorld(),9.5,93,-15.5));
  scenario();sampler=Bukkit.getScheduler().runTaskTimer(this,()->{try{sample();}catch(Throwable e){fail(e);}},1,1);
 }
 private Location point(double x,double z){return new Location(viewer.getWorld(),x,93,z);}
 private void scenario(){
  if(sampler!=null)sampler.cancel();
  dogs.close();Bukkit.getServicesManager().unregister(AmbientMassiffs.class,dogs);
  var c=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"decor/travel-guides.yml")).getConfigurationSection("massiffs");
  double[][] starts=switch(phase){case 0->new double[][]{{4.5,-15.5},{15.5,-15.5},{9.5,-22.5}};case 1->new double[][]{{4.5,-20.5},{15.5,-20.5},{9.5,-8.5}};case 2->new double[][]{{4.5,-15.5},{10.5,-15.5},{15.5,-21.5}};default->new double[][]{{4.5,-20.5},{11.5,-12.5},{15.5,-21.5}};};
  c.set("starts",Arrays.stream(starts).map(v->Map.of("x",v[0],"z",v[1])).toList());dogs=new AmbientMassiffs(plugin,c);Bukkit.getServicesManager().register(AmbientMassiffs.class,dogs,plugin,ServicePriority.Normal);
  previous.clear();distances.clear();minimum=Double.MAX_VALUE;tick=0;
  var a=dogs.bodies().get("spawn_massiff_1");var b=dogs.bodies().get("spawn_massiff_2");var third=dogs.bodies().get("spawn_massiff_3");
  if(phase==0){a.getPathfinder().moveTo(point(15.5,-15.5),.8);b.getPathfinder().moveTo(point(4.5,-15.5),.8);}
  if(phase==1){a.getPathfinder().moveTo(point(15.5,-9.5),.8);b.getPathfinder().moveTo(point(4.5,-9.5),.8);}
  if(phase==2){b.setAI(false);a.getPathfinder().moveTo(point(15.5,-15.5),.8);third.getPathfinder().moveTo(point(4.5,-21.5),.8);}
  if(phase>0)sampler=Bukkit.getScheduler().runTaskTimer(this,()->{try{sample();}catch(Throwable e){fail(e);}},1,1);
  later(phase==3?400:180,this::next);
 }
 private void sample(){
  tick++;var entries=new ArrayList<>(dogs.bodies().entrySet());
  for(var row:entries){
   var body=row.getValue();var at=body.getLocation();check(dogs.inside(at),"Native feet stay inside unchanged roam bounds");
   check(!body.isCollidable()&&!body.getCollidableExemptions().contains(viewer.getUniqueId()),"Player collision remains disabled");
   var last=previous.put(row.getKey(),at);if(last!=null){double moved=last.distance(at);check(moved<.3,"Feet translate only through normal native path movement");distances.merge(row.getKey(),moved,Double::sum);}
   for(var other:entries)if(!other.getKey().equals(row.getKey()))check(body.getCollidableExemptions().contains(other.getValue().getUniqueId()),"Native dog-only collision exemption is reciprocal");
  }
  for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++){
   double d=entries.get(i).getValue().getLocation().distance(entries.get(j).getValue().getLocation());minimum=Math.min(minimum,d);
   check(d>=2.5,"Rendered Massiff envelopes never overlap: phase="+phase+" distance="+d);
  }
  check(viewer.getVelocity().lengthSquared()<1e-8,"Roamers never push the nearby player");
 }
 private void next(){
  evidence.add("phase="+phase+" ticks="+tick+" minimumCenterDistance="+minimum+" walked="+distances);
  for(String id:dogs.bodies().keySet())if(!(phase==2&&id.equals("spawn_massiff_2")))check(distances.getOrDefault(id,0.0)>(phase==3?6:2),"Yield/repath still makes progress: "+id);
  if(++phase<4){scenario();return;}
  check(Bukkit.getServicesManager().load(TravelGuides.class).bodies().size()==2&&Bukkit.getServicesManager().load(DecorService.class).bases().size()==9,"Pilot/Cassian and nine scenery anchors retained");
  save("PASSED","MASSIFF SPACING PASSED: "+checks+" assertions");getLogger().info("MASSIFF SPACING PASSED: "+checks);finish();
 }
 public void onDisable(){if(sampler!=null)sampler.cancel();if(actors!=null){actors.close();actors=null;}}
}
