package net.mandomc.verification;
import java.util.*;
import net.mandomc.integrations.weapons.ReloadIndicator;
import net.mandomc.gameplay.hud.ActionBars;
import me.deecaad.weaponmechanics.*;
import me.deecaad.weaponmechanics.utils.CustomTag;
import me.deecaad.weaponmechanics.weapon.weaponevents.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

/** Actual vendor lifecycle and native items, on a disposable runtime only. */
public final class ReloadProbe extends JavaPlugin implements Listener {
 private Player actor; private ReloadIndicator indicator; private int checks,index; private boolean deny;
 private final List<String> weapons=new ArrayList<>();
 public static final List<String> bars=new ArrayList<>();
 public static final class Capture extends net.minecraft.server.network.ServerGamePacketListenerImpl {
  public Capture(net.minecraft.server.MinecraftServer server,net.minecraft.network.Connection connection,
    net.minecraft.server.level.ServerPlayer player,net.minecraft.server.network.CommonListenerCookie cookie){super(server,connection,player,cookie);}
  @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
   if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket bar)bars.add(bar.text().getString());
  }
 }
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(160,this::begin);}
 @EventHandler(priority=EventPriority.HIGHEST) public void deny(WeaponReloadEvent event){if(deny)event.setCancelled(true);}
 private void begin()throws Exception{
  indicator=Bukkit.getServicesManager().load(ReloadIndicator.class);check(indicator!=null,"full exact candidate registered reload observer");
  var world=Bukkit.getWorlds().getFirst();actor=nativePlayer(new Location(world,0,220,0));actor.setGameMode(GameMode.SURVIVAL);actor.setGravity(false);actor.setInvulnerable(true);actor.addAttachment(this,"weaponmechanics.use.*",true);
  var config=WeaponMechanics.getInstance().getWeaponConfigurations();
  for(String name:List.of("E11","A280","TwilekA280","SE14C","QuarrenSE14C","RelbyV10","CyclerRifle","EE3","Jawa_Ionization_Blaster"))
   if(config.getInt(name+".Reload.Magazine_Size")>0)weapons.add(name);
  check(weapons.size()>=7,"current actual reloadable catalog found: "+weapons);if(System.getenv("MANDOMC_RELOAD_LIFECYCLE_ONLY")!=null)owner();else next();
 }
 private void load(String name,boolean off){
  bars.clear();actor.getInventory().clear();var weapon=WeaponMechanicsAPI.generateWeapon(name);CustomTag.AMMO_LEFT.setInteger(weapon,0);
  if(off)actor.getInventory().setItemInOffHand(weapon);else actor.getInventory().setItemInMainHand(weapon);
  int ammoSlot=1;for(String ammo:List.of("Standard_Plasma_Cells","Ionized_Energy_Cells")){
   var item=WeaponMechanicsAPI.generateAmmo(ammo,false);if(item!=null){item.setAmount(64);actor.getInventory().setItem(ammoSlot++,item);}
  }
 }
 private void next()throws Exception{
  if(index>=weapons.size()){owner();return;}
  String name=weapons.get(index++);load(name,false);
  check(WeaponMechanicsAPI.tryReload(actor),name+" accepted by actual vendor reload API");
  later(3,()->{
   check(indicator.activeReloads()==1,name+" actual scheduled reload observed");
   check(bars.stream().anyMatch(text->text.matches("Reloading \\[\\|{10}\\] [0-9]+\\.[0-9]s")),name+" real outgoing native actionbar contains progress bar and decimal seconds");
   check(!actor.hasCooldown(actor.getInventory().getItemInMainHand()),name+" observer introduced no native gameplay cooldown");
   check(!actor.getInventory().getItemInMainHand().hasData(io.papermc.paper.datacomponent.DataComponentTypes.USE_COOLDOWN),name+" observer introduced no item component");
   int duration=WeaponMechanics.getInstance().getWeaponConfigurations().getInt(name+".Reload.Reload_Duration")+30;
   later(duration,()->{
    check(!WeaponMechanicsAPI.isReloading(actor)&&indicator.activeReloads()==0,name+" actual completion clears observer (vendor="+WeaponMechanicsAPI.isReloading(actor)+", observer="+indicator.activeReloads()+")");
    check(CustomTag.AMMO_LEFT.getInteger(actor.getInventory().getItemInMainHand())>0,name+" actual vendor replenished ammo");
    check(bars.stream().filter(text->text.startsWith("Reloading")).distinct().count()>1,name+" real outgoing countdown advances during vendor reload");next();
   });
  });
 }
 private void owner()throws Exception {
  load("E11",false);check(WeaponMechanicsAPI.tryReload(actor),"fuel-owner fixture actual reload starts");
  later(4,()->{
   var feature=Bukkit.getServicesManager().load(net.mandomc.gameplay.boba.BobaEquipmentFeature.class);
   var main=(net.mandomc.bootstrap.MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
   actor.getInventory().setItemInOffHand(main.items().create("boba_jetpack"));
   check(!feature.equipment().toggle(actor),"actual no-fuel warning runs without altering reload");
   check(feature.equipment().ownsActionBar(actor),"existing Boba owner reserves critical fuel notice");bars.clear();
   later(33,()->{
    check(feature.equipment().ownsActionBar(actor),"critical fuel owner outlives ordinary actionbar lease");
    check(bars.stream().noneMatch(text->text.startsWith("Reloading")),"reload yields for full existing critical fuel reservation");
    WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().cancelTasks();
    feature.equipment().detach(actor);ActionBars.forget(actor);
    later(30,this::lag);
   });
  });
 }
 private double lastSeconds(){
  String value=bars.stream().filter(text->text.startsWith("Reloading")).reduce((left,right)->right).orElseThrow();
  return Double.parseDouble(value.substring(value.lastIndexOf(' ')+1,value.length()-1));
 }
 private void lag()throws Exception{
  load("E11",false);check(WeaponMechanicsAPI.tryReload(actor),"real lag fixture reload starts");
  later(4,()->{
   double before=lastSeconds();Thread.sleep(300);
   later(2,()->{
    check(before-lastSeconds()<=.2,"native countdown stays with tick-scheduled reload through 300ms server stall");
    WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().cancelTasks();lifecycle();
   });
  });
 }
 private void lifecycle()throws Exception{
  load("E11",false);deny=true;check(!WeaponMechanicsAPI.tryReload(actor),"vendor event veto remains authoritative");deny=false;
  later(3,()->{
   check(indicator.activeReloads()==0,"cancelled start has no countdown");
   check(WeaponMechanicsAPI.tryReload(actor),"actual restart accepted after veto");
   later(4,()->{
    check(indicator.activeReloads()==1,"restart observed");
    var boba=Bukkit.getServicesManager().load(net.mandomc.gameplay.boba.BobaEquipmentFeature.class);
    check(boba!=null&&!boba.equipment().toggle(actor),"actual existing Boba notice path runs through current candidate");
    check(!ActionBars.reload(actor,Component.text("Reloading")),"critical notice owns actionbar during reload");
    WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().stopReloadingTasks();
    check(indicator.activeReloads()==0,"actual vendor cancel immediately clears countdown");
    check(!ActionBars.reload(actor,Component.text("Reloading")),"reload cleanup preserved critical notice lease");
    load("E11",true);
    getLogger().info("OFFHAND fixture main="+WeaponMechanicsAPI.getWeaponTitle(actor.getInventory().getItemInMainHand())+" off="+WeaponMechanicsAPI.getWeaponTitle(actor.getInventory().getItemInOffHand())+" dead="+actor.isDead()+" mainReload="+WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().isReloading()+" offReload="+WeaponMechanics.getInstance().getEntityWrapper(actor).getOffHandData().isReloading()+" permission="+actor.hasPermission("weaponmechanics.use.*"));
    check(WeaponMechanicsAPI.tryReload(actor),"offhand actual reload accepted");
    later(4,()->{
     check(indicator.activeReloads()==1,"offhand actual task observed");
     WeaponMechanics.getInstance().getEntityWrapper(actor).getOffHandData().cancelTasks();
     check(indicator.activeReloads()==0,"offhand vendor task cancellation clears progress");
     load("E11",false);check(WeaponMechanicsAPI.tryReload(actor),"slot-switch fixture starts");
     later(4,()->{
      actor.getInventory().setHeldItemSlot(8);Bukkit.getPluginManager().callEvent(new PlayerItemHeldEvent(actor,0,8));
      later(3,()->{
       check(indicator.activeReloads()==0,"actual switch event/vendor cancellation leaves no progress");
       actor.getInventory().setHeldItemSlot(0);load("E11",false);
       CustomTag.AMMO_LEFT.setInteger(actor.getInventory().getItemInMainHand(),30);
       check(!WeaponMechanicsAPI.tryReload(actor),"full magazine is not reloaded");
       later(3,()->{check(indicator.activeReloads()==0,"full magazine has no progress");cleanupCases();});
      });
     });
    });
   });
  });
 }
 private void cleanupCases()throws Exception {
  load("E11",false);for(int i=1;i<36;i++)actor.getInventory().setItem(i,null);
  check(!WeaponMechanicsAPI.tryReload(actor),"empty weapon without cells rejected by actual vendor");
  later(3,()->{
   check(indicator.activeReloads()==0,"no-ammo rejection has no progress");
   load("E11",false);check(WeaponMechanicsAPI.tryReload(actor),"cleanup fixture reload starts");
   later(4,()->{
    Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(actor,actor.getWorld()));
    check(indicator.activeReloads()==0,"world-change cleanup removes progress");
    WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().cancelTasks();
    load("E11",false);check(WeaponMechanicsAPI.tryReload(actor),"logout fixture reload starts");
    later(4,()->{
     // The native fixture has no vendor join session; only exercise our logout callback.
     indicator.quit(new PlayerQuitEvent(actor,Component.empty(),org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
     check(indicator.activeReloads()==0,"logout cleanup removes progress");
     WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().cancelTasks();
     load("E11",false);check(WeaponMechanicsAPI.tryReload(actor),"death fixture actual reload starts");
     later(4,()->{
      actor.setHealth(0);check(indicator.activeReloads()==0,"native player death clears active reload progress");
      indicator.close();check(indicator.activeReloads()==0,"disable cleanup is idempotent");finish();
     });
    });
   });
  });
 }
 private void finish(){getLogger().info("RELOAD PROBE PASSED: "+checks+" actual vendor/native/actionbar assertions");cleanup();Bukkit.shutdown();}
 private interface Work{void run()throws Exception;}
 private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"RELOAD PROBE FAILED",e);cleanup();Bukkit.shutdown();}},ticks);}
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
 private void cleanup(){if(actor!=null)WeaponMechanics.getInstance().removeEntityWrapper(actor);}
 @Override public void onDisable(){cleanup();}
    private Player nativePlayer(Location location)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());Object level=location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BobaStartupTest");
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection"),cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=Capture.class.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        for(Class<?> c=list.getClass();c!=null;c=c.getSuperclass())try{var map=c.getDeclaredField("playersByUUID");map.setAccessible(true);((Map<UUID,Object>)map.get(list)).put((UUID)profileClass.getMethod("id").invoke(profile),handle);break;}catch(NoSuchFieldException ignored){}
        levelClass.getMethod("addNewPlayer",playerClass).invoke(level,handle);
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
