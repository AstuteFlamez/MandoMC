package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.decor.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only live audit: no actors, world mutations, inventory edits or gameplay command requests. */
public final class SpawnNpcSmoke extends JavaPlugin {
 private int checks,samples;private AmbientMassiffs massiffs;private org.bukkit.scheduler.BukkitTask task;
 private final Map<String,Location> previous=new HashMap<>();private final Map<String,Double> walked=new HashMap<>();
 private final List<String> evidence=new ArrayList<>();
 private void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{begin();}catch(Throwable e){failed(e);}},120);}
 private void failed(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SPAWN NPC SMOKE FAILED",e);save("FAILED",e.toString());if(task!=null)task.cancel();if(!new java.io.File(getDataFolder(),"live-only").exists())Bukkit.shutdown();}
 private void save(String name,String text){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),text+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
 private void begin(){
  var guides=Bukkit.getServicesManager().load(TravelGuides.class);massiffs=Bukkit.getServicesManager().load(AmbientMassiffs.class);
  check(guides!=null&&massiffs!=null,"Native NPC services loaded");check(guides.bodies().size()==2&&guides.titles().size()==2,"Exactly two guides");
  check(Bukkit.getServicesManager().load(DecorService.class).bases().size()==9,"Nine scenery anchors retained");
  var world=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));
  check(world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(TravelGuides.MARKER,PersistentDataType.STRING)).count()==4,"Exactly four guide-owned body/title entities");
  check(world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(AmbientMassiffs.MARKER,PersistentDataType.STRING)).count()==3,"Exactly three Massiff IDs");
  check(guides.bodies().get("rebel_pilot").getLocation().distanceSquared(new Location(world,12.481200700988325,93,2.0938902873735747))<1e-12,"Immutable pilot anchor");
  check(guides.bodies().get("cassian_andor").getLocation().distanceSquared(new Location(world,-4.382061352772387,93,-14.50740707472801))<1e-12,"Immutable Cassian anchor");
  for(var row:guides.bodies().entrySet()){var b=row.getValue();check(b.isImmovable()&&b.isInvulnerable()&&!b.hasGravity()&&!b.isCollidable()&&!b.isPersistent(),"Stationary protected guide");evidence.add(row.getKey()+" uuid="+b.getUniqueId()+" location="+b.getLocation());}
  for(var t:guides.titles().values())check(t.getBackgroundColor().getAlpha()==0&&!t.isDefaultBackground(),"Transparent guide title");
  check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(guides.titles().get("rebel_pilot").text()).equals("Click to Travel"),"Exact pilot title");
  task=Bukkit.getScheduler().runTaskTimer(this,()->{try{sample();}catch(Throwable e){failed(e);}},1,5);
 }
 private void sample(){
  for(var row:massiffs.bodies().entrySet()){
   Wolf body=row.getValue();Location at=body.getLocation();check(body.isValid()&&massiffs.inside(at),"Massiff navigates inside fixed footprint");
   check(body.isInvulnerable()&&!body.isCollidable()&&body.getTarget()==null&&!body.isTamed(),"Peaceful nonblocking Massiff");
   check(massiffs.model(row.getKey()).valid(),"Massiff original renderer attached");
   var before=previous.put(row.getKey(),at);if(before!=null){double step=before.distance(at);check(step<1.2,"Native navigation has no teleport jitter");walked.merge(row.getKey(),step,Double::sum);}
  }
  if(++samples<60)return;
  for(String id:massiffs.bodies().keySet()){check(walked.getOrDefault(id,0.0)>2,"Every Massiff physically walks");evidence.add(id+" walked="+walked.get(id)+" location="+massiffs.bodies().get(id).getLocation());}
  task.cancel();Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mandomc jawa models");save("PASSED","SPAWN NPC SMOKE PASSED: "+checks+" assertions");getLogger().info("SPAWN NPC SMOKE PASSED: "+checks);
  if(!new java.io.File(getDataFolder(),"live-only").exists())Bukkit.shutdown();
 }
 public void onDisable(){if(task!=null)task.cancel();}
}
