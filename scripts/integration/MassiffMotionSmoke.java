package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.decor.*;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Quaternionf;

/** Read-only live movement/spacing/animation audit; never creates actors or edits tracking. */
public final class MassiffMotionSmoke extends JavaPlugin {
 private AmbientMassiffs dogs;private org.bukkit.scheduler.BukkitTask task;private int checks,samples;
 private final Map<String,Location> previous=new HashMap<>();private final Map<String,Double> moved=new HashMap<>();
 private final Map<String,Integer> still=new HashMap<>(),legChanges=new HashMap<>();private final Map<String,Quaternionf> legs=new HashMap<>();
 private double minimum=Double.MAX_VALUE;private final List<String> evidence=new ArrayList<>();
 private void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
 private void save(String name,String text){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),text+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{begin();}catch(Throwable e){fail(e);}},120);}
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MASSIFF MOTION SMOKE FAILED",e);save("FAILED",e.toString());if(task!=null)task.cancel();Bukkit.getPluginManager().disablePlugin(this);if(!new java.io.File(getDataFolder(),"live-only").exists())Bukkit.shutdown();}
 private void begin(){
  dogs=Bukkit.getServicesManager().load(AmbientMassiffs.class);check(dogs!=null&&dogs.bodies().size()==3,"Exactly three Massiffs");
  check(Bukkit.getServicesManager().load(TravelGuides.class).bodies().size()==2&&Bukkit.getServicesManager().load(DecorService.class).bases().size()==9,"Guide/scenery owners preserved");
  task=Bukkit.getScheduler().runTaskTimer(this,()->{try{sample();}catch(Throwable e){fail(e);}},1,1);
 }
 private void sample(){
  samples++;var entries=new ArrayList<>(dogs.bodies().entrySet());
  for(var row:entries){
   var id=row.getKey();var body=row.getValue();var at=body.getLocation();check(dogs.inside(at)&&body.isValid(),"Native bounded movement");
   check(dogs.model(id).valid()&&body.getTarget()==null&&body.isInvulnerable()&&!body.isCollidable(),"Protected nonblocking model retained");
   var old=previous.put(id,at);if(old==null)continue;double step=at.distance(old);check(step<.3,"No custom feet displacement");moved.merge(id,step,Double::sum);
   boolean walking=Math.pow(at.getX()-old.getX(),2)+Math.pow(at.getZ()-old.getZ(),2)>1e-7;
   still.put(id,walking?0:still.getOrDefault(id,0)+1);
   var handler=dogs.model(id).active().getAnimationHandler();var walk=handler.getAnimation("walk");
   if(walking&&samples>5)check(walk!=null&&!walk.isEnded()&&!walk.isFinished(),"Every moving sample retains walk");
   if(still.get(id)>10)check(handler.isPlayingAnimation("idle")&&!handler.isPlayingAnimation("walk"),"Stationary feet use idle");
   check(handler.getAnimations().size()<=1,"No competing animation loops");
   var renderer=(DisplayRenderer)dogs.model(id).active().getModelRenderer();var foot=renderer.getRendered().get("left_front_leg");var torso=renderer.getRendered().get("body");
   if(foot!=null&&torso!=null){
    var q=new Quaternionf(torso.getLeftRotation().get()).mul(torso.getRightRotation().get()).invert().mul(new Quaternionf(foot.getLeftRotation().get()).mul(foot.getRightRotation().get())).normalize();
    var before=legs.put(id,q);if(walking&&before!=null&&Math.abs(before.dot(q))<.999995)legChanges.merge(id,1,Integer::sum);
   }
  }
  for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++){
   double gap=entries.get(i).getValue().getLocation().distance(entries.get(j).getValue().getLocation());minimum=Math.min(minimum,gap);check(gap>=2.5,"Rendered body envelopes stay separated");
  }
  if(samples<400)return;
  for(String id:dogs.bodies().keySet()){check(moved.getOrDefault(id,0.0)>2,"Every dog makes native walking progress");evidence.add(id+" walked="+moved.get(id)+" visibleLegChanges="+legChanges.getOrDefault(id,0));}
  evidence.add("minimumCenterDistance="+minimum);task.cancel();Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mandomc jawa models");save("PASSED","MASSIFF MOTION SMOKE PASSED: "+checks+" assertions");getLogger().info("MASSIFF MOTION SMOKE PASSED: "+checks);
  boolean live=new java.io.File(getDataFolder(),"live-only").exists();Bukkit.getPluginManager().disablePlugin(this);if(!live)Bukkit.shutdown();
 }
 public void onDisable(){if(task!=null)task.cancel();long pending=Bukkit.getScheduler().getPendingTasks().stream().filter(t->t.getOwner()==this&&!t.isCancelled()).count();save("CLEANUP","enabled="+isEnabled()+" listeners="+org.bukkit.event.HandlerList.getRegisteredListeners(this).size()+" pendingNonCancelled="+pending);}
}
