package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import net.mandomc.gameplay.decor.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Quaternionf;

/** Observes native feet, animation properties and the actual visible leg display transforms. */
public final class MassiffWalkProbe extends JavaPlugin {
 private AmbientMassiffs massiffs;private NativeProbePlayers actors;private Player viewer;
 private org.bukkit.scheduler.BukkitTask sampler;private int checks,ticks,moving,noWalk,phase;
 private final Map<String,Location> positions=new HashMap<>();private final Map<String,Integer> stationary=new HashMap<>();
 private final Map<String,Quaternionf> legPrevious=new HashMap<>();private final Map<String,Integer> legChanges=new HashMap<>();
 private final Map<String,Double> times=new HashMap<>();private final Map<String,Integer> advances=new HashMap<>();private final List<String> evidence=new ArrayList<>();private boolean baseline;
 private interface Step {void run()throws Exception;}
 private void later(int n,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable e){fail(e);}},n);}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 private void save(String name,String text){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),text+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MASSIFF WALK FAILED",e);save("FAILED",e.toString());finish();}
 private void finish(){if(sampler!=null)sampler.cancel();if(actors!=null){actors.close();actors=null;}Bukkit.shutdown();}
 public void onEnable(){later(120,this::begin);}
 private void begin()throws Exception{
  baseline=new java.io.File(getDataFolder(),"baseline").exists();
  massiffs=Bukkit.getServicesManager().load(AmbientMassiffs.class);check(massiffs!=null&&massiffs.bodies().size()==3,"Three native Massiff bodies");
  check(Bukkit.getServicesManager().load(DecorService.class).bases().size()==9&&Bukkit.getServicesManager().load(TravelGuides.class).bodies().size()==2,"Other scenery and guide owners retained");
  actors=new NativeProbePlayers("GaitProbe");viewer=actors.create(new Location(massiffs.bodies().values().iterator().next().getWorld(),9,93,-15));
  sampler=Bukkit.getScheduler().runTaskTimer(this,()->{try{sample();}catch(Throwable e){fail(e);}},1,1);
  later(140,this::lostClip);
 }
 private Quaternionf leg(String id){
  var body=massiffs.bodies().get(id);var modeled=ModelEngineAPI.getModeledEntity(body);
  modeled.getBase().getData().getMutableTracking().put(viewer.getUniqueId(),com.ticxo.modelengine.api.entity.CullType.NO_CULL);
  var active=massiffs.model(id).active();var renderer=(DisplayRenderer)active.getModelRenderer();
  var foot=renderer.getRendered().get("left_front_leg");var torso=renderer.getRendered().get("body");
  if(foot==null||torso==null)return null;
  Quaternionf parent=new Quaternionf(torso.getLeftRotation().get()).mul(torso.getRightRotation().get()).invert();
  return parent.mul(new Quaternionf(foot.getLeftRotation().get()).mul(foot.getRightRotation().get())).normalize();
 }
 private void sample(){
  ticks++;
  for(var row:massiffs.bodies().entrySet()){
   String id=row.getKey();var body=row.getValue();if(!body.isValid()||!massiffs.model(id).valid())continue;
   Location at=body.getLocation(),before=positions.put(id,at);if(before==null)continue;
   double dx=at.getX()-before.getX(),dz=at.getZ()-before.getZ();boolean moved=dx*dx+dz*dz>1e-7;
   stationary.put(id,moved?0:stationary.getOrDefault(id,0)+1);
   var handler=massiffs.model(id).active().getAnimationHandler();var walk=handler.getAnimation("walk");
   boolean walking=walk!=null&&!walk.isEnded()&&!walk.isFinished();
   if(moved){moving++;if(!walking){noWalk++;if(!baseline&&ticks>5)throw new AssertionError("Moving native feet without advancing walk property: "+id+" clips="+handler.getAnimations().keySet());}}
   if(!baseline&&stationary.get(id)>10)check(!walking&&handler.isPlayingAnimation("idle"),"Stationary/collision feet settle into idle");
   var rotation=leg(id);var last=rotation==null?null:legPrevious.put(id,rotation);
   if(moved&&last!=null&&Math.abs(last.dot(rotation))<.999995)legChanges.merge(id,1,Integer::sum);
   if(moved&&walking){double now=walk.getTime();Double old=times.put(id,now);if(old!=null&&Math.abs(now-old)>1e-8)advances.merge(id,1,Integer::sum);}
   if(!baseline)check(handler.getAnimations().size()<=1,"Single ambient animation authority has no competing idle/walk");
  }
 }
 private void lostClip()throws Exception{
  evidence.add("natural moving="+moving+" withoutWalk="+noWalk+" visibleLegChanges="+legChanges);
  for(var row:massiffs.bodies().entrySet()){
   row.getValue().getPathfinder().stopPathfinding();row.getValue().getPathfinder().moveTo(destination(row.getValue()),.8);
   massiffs.model(row.getKey()).active().getAnimationHandler().forceStopAnimation("walk");
  }
  later(35,()->{
   evidence.add("lost-property moving="+moving+" withoutWalk="+noWalk+" visibleLegChanges="+legChanges);
   if(baseline){save("BASELINE","baseline actual feet samples="+moving+" missing walk samples="+noWalk);finish();return;}
   for(String id:massiffs.bodies().keySet())check(legChanges.getOrDefault(id,0)>10,"Actual renderer leg swings during native movement: "+id);
   collision();
  });
 }
 private Location destination(Wolf body){
  var at=body.getLocation();return new Location(body.getWorld(),at.getX()<10?16.5:4.5,93,at.getZ()<-15?-8.5:-21.5);
 }
 private void collision(){
  phase=1;for(var b:massiffs.bodies().values())b.setAI(false);
  later(25,()->{
   for(var row:massiffs.bodies().entrySet()){check(stationary.getOrDefault(row.getKey(),0)>10,"Stopped/blocked native body actually stationary");check(massiffs.model(row.getKey()).active().getAnimationHandler().isPlayingAnimation("idle"),"Idle selected from stopped feet despite retained navigation intent");row.getValue().setAI(true);row.getValue().getPathfinder().moveTo(destination(row.getValue()),.8);}
   later(65,this::recovery);
  });
 }
 private void recovery(){
  evidence.add("before recovery moving="+moving+" missing="+noWalk+" visibleLegChanges="+legChanges);
  for(String id:massiffs.bodies().keySet())massiffs.model(id).active().destroy();
  later(15,()->{
   for(String id:massiffs.bodies().keySet())check(massiffs.model(id).valid(),"Missing rig recovers with animation ownership");
   later(65,()->{
    check(moving>150&&noWalk==0,"Sustained actual movement always retains walk");
    for(String id:massiffs.bodies().keySet()){check(legChanges.getOrDefault(id,0)>25,"Visible leg transformations keep advancing across turns and rig recovery");}
    evidence.add("final moving="+moving+" missing="+noWalk+" visibleLegChanges="+legChanges);
    save("PASSED","MASSIFF WALK PASSED: "+checks+" assertions");getLogger().info("MASSIFF WALK PASSED: "+checks);finish();
   });
  });
 }
 public void onDisable(){if(sampler!=null)sampler.cancel();if(actors!=null){actors.close();actors=null;}}
}
