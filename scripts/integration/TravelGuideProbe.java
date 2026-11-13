package net.mandomc.gameplay.decor;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.verification.NativeProbePlayers;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;

/** Real native mannequin click packets, current command owners and daily delivery regression tests. */
public final class TravelGuideProbe extends JavaPlugin {
 private NativeProbePlayers actors; private TravelGuides guides; private Player p,q; private int checks;
 private final List<String> evidence=new ArrayList<>();
 private AmbientMassiffs massiffs;private org.bukkit.scheduler.BukkitTask monitor;
 private final Map<String,Location> lastPositions=new HashMap<>();private final Map<String,Double> distance=new HashMap<>();private final Set<String> walking=new HashSet<>();private int samples;
 private void monitorMassiffs(){
  massiffs=Bukkit.getServicesManager().load(AmbientMassiffs.class);check(massiffs!=null&&massiffs.bodies().size()==3,"Exactly three ambient Massiffs through existing lifecycle");
  monitor=Bukkit.getScheduler().runTaskTimer(this,()->{try{
   for(var row:massiffs.bodies().entrySet()){
    var body=row.getValue();var at=body.getLocation();check(massiffs.inside(at),"Massiff stays in surveyed spawn footprint");
    check(body.getTarget()==null&&body.isInvulnerable()&&!body.isCollidable()&&!body.isTamed(),"Massiff remains peaceful protected and nonblocking");
    check(massiffs.model(row.getKey()).active().getModelRenderer()!=null,"Original Massiff renderer attached");
    var old=lastPositions.put(row.getKey(),at);if(old!=null){double moved=old.distance(at);check(moved<1.2,"Ground navigation has no teleport jumps");distance.merge(row.getKey(),moved,Double::sum);if(moved>.01)walking.add(row.getKey());}
   }samples++;
  }catch(Throwable e){monitor.cancel();save("FAILED",e.toString());getLogger().log(java.util.logging.Level.SEVERE,"MASSIFF FAILED",e);Bukkit.shutdown();}},1,5);
 }
 private final class MutableClock extends Clock { Instant now=Instant.parse("2026-11-13T05:59:59Z"); public ZoneId getZone(){return ZoneId.of("America/Chicago");} public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;} }
 private final MutableClock clock=new MutableClock();
 private interface Step{void run()throws Exception;}
 private void later(long t,Step s){Bukkit.getScheduler().runTaskLater(this,()->{try{s.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"GUIDES FAILED",e);save("FAILED",e.toString());if(actors!=null)actors.close();Bukkit.shutdown();}},t);}
 private void check(boolean v,String s){if(!v)throw new AssertionError(s);checks++;evidence.add(s);}
 private void save(String name,String s){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),s+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
 public void onEnable(){later(100,this::begin);}
 private void begin()throws Exception{
  surveyGround();
  var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");guides=Bukkit.getServicesManager().load(TravelGuides.class);
  check(guides!=null,"Guides started through deployed decor lifecycle");monitorMassiffs();
  check(Bukkit.getServicesManager().load(DecorService.class).bases().size()==9,"Nine scenery anchors preserved");
  check(guides.bodies().size()==2&&guides.titles().size()==2,"Exactly two guide bodies and titles");
  check(Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getEntities().stream().filter(e->e.getPersistentDataContainer().has(TravelGuides.MARKER,PersistentDataType.STRING)).count()==4,"No stale guide duplicates");
  var pilot=guides.bodies().get("rebel_pilot");var cassian=guides.bodies().get("cassian_andor");
  check(pilot.getLocation().distanceSquared(new Location(pilot.getWorld(),12.481200700988325,93,2.0938902873735747))<1e-12,"Pilot uses immutable capture");
  check(cassian.getLocation().distanceSquared(new Location(pilot.getWorld(),-4.382061352772387,93,-14.50740707472801))<1e-12,"Cassian uses immutable capture");
  for(var e:guides.bodies().values())check(e.isInvulnerable()&&e.isImmovable()&&!e.hasGravity()&&!e.isPersistent()&&!e.isCollidable(),"Native stationary protected mannequin "+e.getEntityId());
  for(var e:guides.titles().values())check(e.getBackgroundColor().getAlpha()==0&&!e.isDefaultBackground(),"Transparent title background");
  check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(guides.titles().get("rebel_pilot").text()).equals("Click to Travel"),"Exact travel title");
  guides.close();Bukkit.getServicesManager().unregister(TravelGuides.class,guides);
  var ctor=TravelGuides.class.getDeclaredConstructor(MandoPlugin.class,YamlConfiguration.class,Clock.class);ctor.setAccessible(true);guides=ctor.newInstance(plugin,YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"decor/travel-guides.yml")),clock);
  Bukkit.getServicesManager().register(TravelGuides.class,guides,plugin,org.bukkit.plugin.ServicePriority.Normal);
  actors=new NativeProbePlayers("GuideProbe");p=actors.create(guides.bodies().get("rebel_pilot").getLocation().add(1,0,0));q=actors.create(guides.bodies().get("cassian_andor").getLocation().add(1,0,0));
  p.addAttachment(plugin,"mandomc.travel.warp",true);q.addAttachment(plugin,"mandomc.travel.warp",true);
  later(10,this::pilot);
 }
 private String menu(Player p){var i=p.getOpenInventory().getTopInventory();var rows=new ArrayList<String>();for(int n=0;n<i.getSize();n++){var s=i.getItem(n);rows.add(n+":"+(s==null?"air":s.serialize().toString()));}return rows.toString();}
 private void packet(Player p,Entity target,String hand)throws Exception{
  Object h=p.getClass().getMethod("getHandle").invoke(p),listener=h.getClass().getField("connection").get(h);
  var hands=Class.forName("net.minecraft.world.InteractionHand");var vec=Class.forName("net.minecraft.world.phys.Vec3");var type=Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
  var packet=type.getConstructor(int.class,hands,vec,boolean.class).newInstance(target.getEntityId(),hands.getField(hand).get(null),vec.getConstructor(double.class,double.class,double.class).newInstance(0,1,0),false);
  listener.getClass().getMethod("handleInteract",type).invoke(listener,packet);
 }
 private void pilot()throws Exception{
  Bukkit.dispatchCommand(p,"mandomc:warp");String expected=menu(p);p.closeInventory();actors.drain(p);actors.otherPackets(p);
  packet(p,guides.bodies().get("rebel_pilot"),"MAIN_HAND");
  later(2,()->{
   check(menu(p).equals(expected),"Actual native visible mannequin packet opens byte-equivalent current warp menu slots");
   var inventory=p.getOpenInventory().getTopInventory();actors.drain(p);actors.otherPackets(p);
   packet(p,guides.bodies().get("rebel_pilot"),"OFF_HAND");packet(p,guides.bodies().get("rebel_pilot"),"MAIN_HAND");
   later(2,()->{check(p.getOpenInventory().getTopInventory()==inventory,"Main/offhand and duplicate native interact-at packets open once");
    var plugin=Bukkit.getPluginManager().getPlugin("MandoMC");var deny=p.addAttachment(plugin,"mandomc.travel.warp",false);
    var view=p.getOpenInventory();int slot=-1;for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(item!=null&&item.getType()!=Material.GRAY_STAINED_GLASS_PANE){slot=i;break;}}
    var before=p.getLocation();menuPacket(p,slot);
    check(p.getLocation().distanceSquared(before)<1e-12,"Warp selection retains authoritative permission denial");p.removeAttachment(deny);
    later(12,this::journeys);
   });
  });
 }
 private void menuPacket(Player p,int slot)throws Exception{
  var handle=p.getClass().getMethod("getHandle").invoke(p);var menu=handle.getClass().getField("containerMenu").get(handle);var listener=handle.getClass().getField("connection").get(handle);
  var input=Class.forName("net.minecraft.world.inventory.ContainerInput");var mapType=Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap");var hash=Class.forName("net.minecraft.network.HashedStack");
  var type=Class.forName("net.minecraft.network.protocol.game.ServerboundContainerClickPacket");var map=Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").getConstructor().newInstance();
  var packet=type.getConstructor(int.class,int.class,short.class,byte.class,input,mapType,hash).newInstance(menu.getClass().getField("containerId").getInt(menu),(int)menu.getClass().getMethod("getStateId").invoke(menu),(short)slot,(byte)0,input.getField("PICKUP").get(null),map,hash.getField("EMPTY").get(null));
  listener.getClass().getMethod("handleContainerClick",type).invoke(listener,packet);
 }
 private void journeys()throws Exception{
  var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
  var config=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"travel/warps.yml"));int slot=config.getInt("warps.spawn.slot");
  packet(p,guides.bodies().get("rebel_pilot"),"MAIN_HAND");plugin.combat().tag(p);actors.drain(p);menuPacket(p,slot);
  later(2,()->{var denial=actors.drain(p);check(!denial.isEmpty(),"Actual native menu packet retains combat travel denial");plugin.combat().clear(p);
   later(12,()->{packet(p,guides.bodies().get("rebel_pilot"),"MAIN_HAND");var before=p.getLocation();menuPacket(p,slot);
    check(p.getLocation().distanceSquared(before)<1e-12,"Survival/adventure native menu selection retains travel warmup");
    later(75,()->{check(p.getLocation().distanceSquared(plugin.warps().destination("spawn"))<.01,"Authoritative warmup completes at current Batuu destination");
     p.teleport(guides.bodies().get("cassian_andor").getLocation().add(1,0,0));p.getInventory().clear();q.getInventory().clear();later(62,this::book);
    });
   });
  });
 }
 private long books(Player p){return Arrays.stream(p.getInventory().getContents()).filter(i->i!=null&&i.getType()==Material.WRITTEN_BOOK).count();}
 private void book()throws Exception{
  packet(p,guides.bodies().get("cassian_andor"),"MAIN_HAND");later(2,()->{
   check(books(p)==1,"First actual Cassian packet delivers one current handbook");
   var b=Arrays.stream(p.getInventory().getContents()).filter(i->i!=null&&i.getType()==Material.WRITTEN_BOOK).findFirst().orElseThrow();
   check(((org.bukkit.inventory.meta.BookMeta)b.getItemMeta()).getPageCount()==11,"Current authoritative eleven-page handbook");
   check("2026-11-12".equals(p.getPersistentDataContainer().get(TravelGuides.DAILY,PersistentDataType.STRING)),"Only successful delivery records local calendar day");
   p.loadData();check("2026-11-12".equals(p.getPersistentDataContainer().get(TravelGuides.DAILY,PersistentDataType.STRING)),"Daily entitlement survives native disk save/load");
   p.teleport(guides.bodies().get("cassian_andor").getLocation().add(1,0,0));actors.drain(p);actors.otherPackets(p);
   packet(p,guides.bodies().get("cassian_andor"),"OFF_HAND");later(2,()->{check(actors.drain(p).isEmpty(),"Cassian offhand has no duplicate book or dialogue");later(62,this::hint);});
  });
 }
 private String previous;
 private void hint()throws Exception{
  actors.drain(p);packet(p,guides.bodies().get("cassian_andor"),"MAIN_HAND");later(2,()->{
   var chat=actors.drain(p);check(chat.size()==1,"Further daily click gives one private hint");previous=chat.getFirst().toString();check(books(p)==1,"Hints never duplicate handbook");
   later(62,()->{packet(p,guides.bodies().get("cassian_andor"),"MAIN_HAND");later(2,()->{var next=actors.drain(p);check(next.size()==1&&!next.getFirst().toString().equals(previous),"Random hints avoid immediate repeats");full();});});
  });
 }
 private void full()throws Exception{
  for(int i=0;i<36;i++)q.getInventory().setItem(i,new ItemStack(Material.STONE,64));
  packet(q,guides.bodies().get("cassian_andor"),"MAIN_HAND");later(2,()->{
   check(!q.getPersistentDataContainer().has(TravelGuides.DAILY,PersistentDataType.STRING)&&books(q)==0,"Independent full-inventory player retains daily entitlement");
   check(Arrays.stream(q.getInventory().getStorageContents()).allMatch(i->i!=null&&i.getAmount()==64&&i.getType()==Material.STONE),"Full inventory never overwrites existing items");
   q.getInventory().setItem(0,null);later(62,()->{packet(q,guides.bodies().get("cassian_andor"),"MAIN_HAND");later(2,()->{check(books(q)==1&&q.getPersistentDataContainer().has(TravelGuides.DAILY,PersistentDataType.STRING),"Full inventory retry succeeds for independent player");midnight();});});
  });
 }
 private void midnight()throws Exception{
  clock.now=Instant.parse("2026-11-13T06:00:00Z");check(TravelGuides.day(clock).equals("2026-11-13"),"Chicago calendar midnight changes day");
  check(TravelGuides.day(Clock.fixed(Instant.parse("2026-11-01T06:30:00Z"),ZoneId.of("America/Chicago"))).equals(TravelGuides.day(Clock.fixed(Instant.parse("2026-11-01T07:30:00Z"),ZoneId.of("America/Chicago")))),"DST repeated hour retains same calendar day");
  later(62,()->{packet(p,guides.bodies().get("cassian_andor"),"MAIN_HAND");later(2,()->{check(books(p)==1&&"2026-11-13".equals(p.getPersistentDataContainer().get(TravelGuides.DAILY,PersistentDataType.STRING)),"Next local day refreshes existing book without duplication");survey();});});
 }
 private void surveyGround()throws Exception{
  var world=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));StringBuilder s=new StringBuilder("x,z,y,floor\n");
  for(int x=-18;x<=30;x++)for(int z=-34;z<=10;z++)for(int y=91;y<=98;y++){
   var floor=world.getBlockAt(x,y-1,z);if(floor.getType().isSolid()&&world.getBlockAt(x,y,z).isPassable()&&world.getBlockAt(x,y+1,z).isPassable())s.append(x+","+z+","+y+","+floor.getType()+"\n");
  }
  Files.writeString(getDataFolder().toPath().resolve("spawn-ground.csv"),s.toString());
 }
 private void survey()throws Exception{
  check(Bukkit.getServicesManager().load(DecorService.class).bases().size()==9,"Scenery stays at nine after all interactions");
  monitor.cancel();check(samples>=40,"Massiffs observed over real server ticks");
  for(var row:massiffs.bodies().entrySet()){
   check(walking.contains(row.getKey())&&distance.getOrDefault(row.getKey(),0.0)>3,"Massiff actually walked multiple blocks: "+row.getKey()+" distance="+distance.get(row.getKey()));
   var body=row.getValue();double health=body.getHealth();body.damage(10);check(body.getHealth()==health,"Actual native damage cannot injure ambient Massiff");
   double playerHealth=p.getHealth();p.damage(5,body);check(p.getHealth()==playerHealth,"Ambient Massiff cannot deal native damage");
   check(!body.addPassenger(p),"Ambient Massiff mount denied");
  }
  var ids=new HashMap<String,UUID>();massiffs.bodies().forEach((id,b)->ids.put(id,b.getUniqueId()));
  for(String id:ids.keySet())massiffs.model(id).active().destroy();
  later(15,()->{
   for(var row:massiffs.bodies().entrySet())check(row.getValue().getUniqueId().equals(ids.get(row.getKey()))&&massiffs.model(row.getKey()).valid(),"Massiff model loss reattaches to same native identity: "+row.getKey()+" uuid="+row.getValue().getUniqueId()+" expected="+ids.get(row.getKey())+" valid="+massiffs.model(row.getKey()).valid()+" destroyed="+massiffs.model(row.getKey()).active().isDestroyed()+" removed="+massiffs.model(row.getKey()).active().isRemoved());
   massiffs.bodies().get("spawn_massiff_1").remove();later(15,()->{
    check(massiffs.bodies().size()==3&&massiffs.bodies().values().stream().allMatch(Entity::isValid),"Missing body reconstructs exactly three Massiffs");
    check(!massiffs.bodies().get("spawn_massiff_1").getUniqueId().equals(ids.get("spawn_massiff_1")),"Removed Massiff reconstructs with same stable config ID");
    var territory=Bukkit.getServicesManager().load(net.mandomc.gameplay.territory.TerritoryService.class);
    var at=guides.bodies().get("rebel_pilot").getLocation();
    var staleGuide=territory.spawnNpc(EntityType.MANNEQUIN,at,()->at.getWorld().spawn(at,Mannequin.class,e->{e.setGravity(false);e.getPersistentDataContainer().set(TravelGuides.MARKER,PersistentDataType.STRING,"rebel_pilot");}));
    var staleMassiff=territory.spawnNpc(EntityType.WOLF,at,()->at.getWorld().spawn(at,Wolf.class,e->{e.setGravity(false);e.getPersistentDataContainer().set(AmbientMassiffs.MARKER,PersistentDataType.STRING,"spawn_massiff_1");}));
    Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(at.getChunk(),false));
    check(!staleGuide.isValid()&&!staleMassiff.isValid(),"Chunk-load reconciliation removes stale guide/Massiff duplicates");
    check(guides.bodies().values().stream().allMatch(Entity::isValid)&&massiffs.bodies().values().stream().allMatch(Entity::isValid),"Chunk reconciliation preserves current owned identities");
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mandomc jawa models");
    save("PASSED","GUIDES PASSED: "+checks+" assertions");getLogger().info("GUIDES PASSED: "+checks);actors.close();actors=null;Bukkit.shutdown();
   });
  });
 }
 public void onDisable(){if(monitor!=null)monitor.cancel();if(actors!=null)actors.close();}
}
