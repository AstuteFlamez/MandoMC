package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.decor.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Full-cycle timing and four visible limbs while the native body continuously walks. */
public final class MassiffTimelineProbe extends JavaPlugin {
 private AmbientMassiffs dogs;private NativeProbePlayers actors;private Player viewer;private org.bukkit.scheduler.BukkitTask task;
 private int tick,moving,streak,maxStreak,stall,maxStall,replacements,wraps,windows,badWindows;private double lastTime=-10;private IAnimationProperty property;
 private Location previous;private boolean baseline;private final List<String> rows=new ArrayList<>();private final Map<String,List<Double>> angles=new LinkedHashMap<>();
 private static final String[] LIMBS={"left_front_leg","left_rear_leg","right_front_leg","right_rear_leg"};
 private interface Step{void run()throws Exception;}
 private void later(int n,Step s){Bukkit.getScheduler().runTaskLater(this,()->{try{s.run();}catch(Throwable e){fail(e);}},n);}
 public void onEnable(){later(120,this::begin);}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 private void save(String name,String text){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),text);Files.write(getDataFolder().toPath().resolve(name+"-frames.csv"),rows);}catch(Exception e){throw new RuntimeException(e);}}
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"TIMELINE FAILED",e);save("FAILED",e.toString()+"\n"+summary());finish();}
 private void finish(){if(task!=null)task.cancel();if(actors!=null){actors.close();actors=null;}Bukkit.shutdown();}
 private void begin()throws Exception{
  var world=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));
  double[][] jawaPoints={{-5.403046981270816,34.40807852444705},{-4.416865618655401,32.00444662250574},{-2.8283500115352265,34.0603206862401}};
  var ground=new ArrayList<String>();ground.add("x,z,floor,feet,head,floorMaxY");
  for(var point:jawaPoints){int x=(int)java.lang.Math.floor(point[0]),z=(int)java.lang.Math.floor(point[1]);ground.add(point[0]+","+point[1]+","+world.getBlockAt(x,92,z).getType()+","+world.getBlockAt(x,93,z).getType()+","+world.getBlockAt(x,94,z).getType()+","+world.getBlockAt(x,92,z).getBoundingBox().getMaxY());}
  Files.createDirectories(getDataFolder().toPath());Files.write(getDataFolder().toPath().resolve("jawa-ground.csv"),ground);
  baseline=new java.io.File(getDataFolder(),"baseline").exists();var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
  dogs=Bukkit.getServicesManager().load(AmbientMassiffs.class);dogs.close();Bukkit.getServicesManager().unregister(AmbientMassiffs.class,dogs);
  var c=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"decor/travel-guides.yml")).getConfigurationSection("massiffs");
  c.set("starts",List.of(Map.of("x",3.5,"z",-23.5),Map.of("x",3.5,"z",-7.5),Map.of("x",17.5,"z",-23.5)));
  dogs=new AmbientMassiffs(plugin,c);Bukkit.getServicesManager().register(AmbientMassiffs.class,dogs,plugin,ServicePriority.Normal);
  var body=dogs.bodies().get("spawn_massiff_1");dogs.bodies().get("spawn_massiff_2").setAI(false);dogs.bodies().get("spawn_massiff_3").setAI(false);
  actors=new NativeProbePlayers("CycleProbe");viewer=actors.create(new Location(body.getWorld(),10,93,-15));
  // Control only navigation for a long uninterrupted measured path; the real
  // production animation sampler and model renderer remain active.
  var navigationTask=AmbientMassiffs.class.getDeclaredField("task");navigationTask.setAccessible(true);
  ((org.bukkit.scheduler.BukkitTask)navigationTask.get(dogs)).cancel();
  for(var b:dogs.bodies().values())b.getPathfinder().stopPathfinding();
  for(String limb:LIMBS)angles.put(limb,new ArrayList<>());
  rows.add("tick,moved,propertyIdentity,phase,time,lerpInTime,leftFront,leftRear,rightFront,rightRear");
  later(20,()->{
   check(body.getPathfinder().moveTo(new Location(body.getWorld(),17.5,93,-7.5),.8),"Measured native path accepted after body initialization");
   task=Bukkit.getScheduler().runTaskTimer(this,()->{try{sample();}catch(Throwable e){fail(e);}},1,1);
  });
 }
 private void sample(){
  tick++;var body=dogs.bodies().get("spawn_massiff_1");var at=body.getLocation();boolean moved=previous!=null&&at.distanceSquared(previous)>1e-7;previous=at;
  var active=dogs.model("spawn_massiff_1").active();ModelEngineAPI.getModeledEntity(body).getBase().getData().getMutableTracking().put(viewer.getUniqueId(),com.ticxo.modelengine.api.entity.CullType.NO_CULL);
  var clip=active.getAnimationHandler().getAnimation("walk");double time=clip==null?-2:clip.getTime();
  if(moved&&clip!=null){
   moving++;streak++;maxStreak=Math.max(maxStreak,streak);
   if(property!=null&&clip!=property)replacements++;
   if(clip==property&&Math.abs(time-lastTime)<1e-7){stall++;maxStall=Math.max(maxStall,stall);}else stall=0;
   if(clip==property&&time<lastTime-.5)wraps++;
   property=clip;lastTime=time;
  }else{streak=0;stall=0;property=null;lastTime=-10;for(var list:angles.values())list.clear();}
  var renderer=(DisplayRenderer)active.getModelRenderer();var torso=renderer.getRendered().get("body");StringBuilder row=new StringBuilder(tick+","+moved+","+(clip==null?0:System.identityHashCode(clip))+","+(clip==null?"none":clip.getPhase())+","+time+","+(clip==null?0:clip.getLerpInTime()));
  for(String limb:LIMBS){
   var foot=renderer.getRendered().get(limb);double pitch=Double.NaN;
   if(torso!=null&&foot!=null){var q=new Quaternionf(torso.getLeftRotation().get()).mul(torso.getRightRotation().get()).invert().mul(new Quaternionf(foot.getLeftRotation().get()).mul(foot.getRightRotation().get())).normalize();pitch=java.lang.Math.toDegrees(q.getEulerAnglesXYZ(new Vector3f()).x);}
   row.append(',').append(pitch);if(moved&&!Double.isNaN(pitch))angles.get(limb).add(pitch);
  }
  rows.add(row.toString());
  if(angles.values().stream().allMatch(a->a.size()>=25)){
   windows++;boolean bad=false;for(var a:angles.values()){double span=Collections.max(a)-Collections.min(a);if(span<28)bad=true;a.clear();}if(bad)badWindows++;
  }
  if(tick<270)return;
  String summary=summary();
  if(!baseline){check(maxStreak>=120,"Long continuous native walking captured");check(replacements==0,"Healthy looping property is never replaced at cycle boundary");check(maxStall<=3,"Moving timeline never stalls beyond three samples");check(wraps>=5,"At least five full walk cycles advance naturally");check(windows>=6&&badWindows==0,"Every full-cycle window swings all four visible legs through the gait");}
  save(baseline?"BASELINE":"PASSED",summary);getLogger().info(summary);finish();
 }
 private String summary(){return "movingSamples="+moving+" longestContinuous="+maxStreak+" propertyReplacements="+replacements+" longestTimelineStall="+maxStall+" normalLoopWraps="+wraps+" fullCycleWindows="+windows+" windowsMissingFullFourLegSwing="+badWindows;}
 public void onDisable(){if(task!=null)task.cancel();if(actors!=null){actors.close();actors=null;}}
}
