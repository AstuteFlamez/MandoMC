package net.mandomc.verification;
import java.util.*;
import me.deecaad.weaponmechanics.*;
import me.deecaad.weaponmechanics.utils.CustomTag;
import me.deecaad.weaponmechanics.weapon.weaponevents.*;
import me.deecaad.weaponmechanics.weapon.trigger.TriggerType;
import net.mandomc.integrations.weapons.ReloadIndicator;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.kyori.adventure.text.Component;

/** Actual WeaponMechanics lifecycle plus the installed native outbound handler, never on the shared server. */
public final class NativeReloadProbe extends JavaPlugin implements Listener {
 private Player actor;private ReloadIndicator indicator;private int checks,index,total;private boolean deny;
 private final List<String> weapons=new ArrayList<>();
 private static io.netty.channel.embedded.EmbeddedChannel wire;
 private static final Map<Integer,net.minecraft.world.item.ItemStack> icons=new HashMap<>();
 private static final List<ClientboundCooldownPacket> cooldowns=new ArrayList<>();
 private static Object lastPacket;
 public static final class Capture extends net.minecraft.server.network.ServerGamePacketListenerImpl {
  public Capture(net.minecraft.server.MinecraftServer server,net.minecraft.network.Connection connection,
    net.minecraft.server.level.ServerPlayer player,net.minecraft.server.network.CommonListenerCookie cookie){super(server,connection,player,cookie);}
  @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
   wire.writeAndFlush(packet);Object out;
   while((out=wire.readOutbound())!=null){lastPacket=out;
    if(out instanceof ClientboundSetPlayerInventoryPacket p)icons.put(p.slot(),p.contents());
    if(out instanceof ClientboundCooldownPacket p)cooldowns.add(p);
    if(out instanceof ClientboundContainerSetContentPacket p&&p.containerId()==0){
     for(int i=0;i<9;i++)icons.put(i,p.items().get(36+i));icons.put(40,p.items().get(45));
    }
   }
  }
 }
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(160,this::begin);}
 @EventHandler(priority=EventPriority.HIGHEST)public void deny(WeaponReloadEvent event){if(deny)event.setCancelled(true);total=event.getReloadCompleteTime();}
 private void begin()throws Exception{
  indicator=Bukkit.getServicesManager().load(ReloadIndicator.class);check(indicator!=null,"exact latest candidate enabled native observer");
  var world=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));Location location=null;
  var protection=new net.mandomc.integrations.regions.SaberProtection();
  for(int x=-224;x<=224&&location==null;x+=16)for(int z=-224;z<=224;z+=16){
   var point=new Location(world,x+.5,180,z+.5);if(protection.allowed(point)){location=point;break;}
  }
  check(location!=null,"native weapon fixture is outside protection");location.getChunk().addPluginChunkTicket(this);
  actor=nativePlayer(location);actor.setGameMode(GameMode.SURVIVAL);
  actor.setGravity(false);actor.setInvulnerable(true);actor.addAttachment(this,"weaponmechanics.use.*",true);
  var config=WeaponMechanics.getInstance().getWeaponConfigurations();
  for(String name:List.of("E11","A280","TwilekA280","SE14C","QuarrenSE14C","RelbyV10","CyclerRifle","EE3","Jawa_Ionization_Blaster"))
   if(config.getInt(name+".Reload.Magazine_Size")>0)weapons.add(name);
  check(weapons.size()==9,"all nine current real reloadable weapon variants found");if(System.getenv("MANDOMC_NATIVE_LIFECYCLE_ONLY")!=null)existing();else next();
 }
 private void load(String name,boolean off,int ammo){
  icons.clear();cooldowns.clear();actor.getInventory().setHeldItemSlot(0);actor.getInventory().clear();
  var weapon=WeaponMechanicsAPI.generateWeapon(name);CustomTag.AMMO_LEFT.setInteger(weapon,ammo);
  if(off)actor.getInventory().setItemInOffHand(weapon);else actor.getInventory().setItemInMainHand(weapon);
  actor.getInventory().setItem(8,weapon.clone());
  int slot=1;for(String id:List.of("Standard_Plasma_Cells","Ionized_Energy_Cells")){
   var cells=WeaponMechanicsAPI.generateAmmo(id,false);cells.setAmount(64);actor.getInventory().setItem(slot++,cells);
  }
  actor.setCooldown(Material.SHIELD,200);
 }
 private static Identifier group(net.minecraft.world.item.ItemStack item){return new ItemCooldowns().getCooldownGroup(item);}
 private Identifier ownGroup(int slot){return group(Objects.requireNonNull(icons.get(slot),"native client icon missing"));}
 private void fullInventory(){var nativeActor=((CraftPlayer)actor).getHandle();nativeActor.connection.send(new ClientboundContainerSetContentPacket(0,0,new ArrayList<>(nativeActor.inventoryMenu.getItems()),net.minecraft.world.item.ItemStack.EMPTY));}
 private void next()throws Exception{
  if(index==weapons.size()){existing();return;}
  String name=weapons.get(index++);load(name,false,0);
  check(WeaponMechanicsAPI.tryReload(actor),name+" starts actual vendor reload");
  later(4,()->{
   check(indicator.activeReloads()==1,name+" actual reload tracked");
   Identifier own=ownGroup(0);check(own.toString().startsWith("mandomc:reload/"),name+" actual outbound inventory image carries unique native group");
   check(cooldowns.stream().anyMatch(p->p.cooldownGroup().equals(own)&&p.duration()>0&&p.duration()<=total),name+" native cooldown packet matches icon group and actual duration");
   check(!actor.hasCooldown(actor.getInventory().getItemInMainHand()),name+" actual server weapon has no added cooldown");
   check(!actor.getInventory().getItemInMainHand().hasData(io.papermc.paper.datacomponent.DataComponentTypes.USE_COOLDOWN),name+" actual server item metadata is untouched");
   fullInventory();
   check(ownGroup(0).equals(own),name+" native full-inventory resync retains icon sweep");
   check(!ownGroup(8).equals(own),name+" identical same-material twin in another slot is unaffected");
   var client=new ItemCooldowns();client.addCooldown(own,40);
   check(client.getCooldownPercent(icons.get(0),0)>0&&client.getCooldownPercent(icons.get(8),0)==0,name+" native client cooldown renderer selects only reloading instance");
   for(int i=0;i<20;i++)client.tick();check(Math.abs(client.getCooldownPercent(icons.get(0),0)-.5f)<.001,name+" native client sweep advances to half height");
   check(actor.getCooldown(Material.SHIELD)>0,name+" unrelated saber/shield cooldown retained");
   if(name.equals("E11"))packetVariants(own);
   later(WeaponMechanics.getInstance().getWeaponConfigurations().getInt(name+".Reload.Reload_Duration")+30,()->{
    check(!WeaponMechanicsAPI.isReloading(actor)&&indicator.activeReloads()==0,name+" actual scheduled reload completes");
    check(CustomTag.AMMO_LEFT.getInteger(actor.getInventory().getItemInMainHand())>0,name+" native vendor ammo replenished normally");
    check(!ownGroup(0).equals(own),name+" completion restores authoritative original icon data");
    check(cooldowns.stream().anyMatch(p->p.cooldownGroup().equals(own)&&p.duration()==0),name+" completion clears only its own native visual group");next();
   });
  });
 }
 private void packetVariants(Identifier own){
  var real=CraftItemStack.asNMSCopy(actor.getInventory().getItemInMainHand());var before=real.copy();
  var handle=((CraftPlayer)actor).getHandle();handle.connection.send(new ClientboundContainerSetSlotPacket(0,1,36,real));
  var slot=(ClientboundContainerSetSlotPacket)lastPacket;
  check(group(slot.getItem()).equals(own),"real native single-slot updates preserve reload group");
  check(net.minecraft.world.item.ItemStack.isSameItemSameComponents(real,before),"outgoing transform never mutates the original packet item");
  var chest=new ArrayList<net.minecraft.world.item.ItemStack>(Collections.nCopies(63,net.minecraft.world.item.ItemStack.EMPTY));chest.set(54,real);chest.set(62,real.copy());
  handle.connection.send(new ClientboundContainerSetContentPacket(7,2,chest,net.minecraft.world.item.ItemStack.EMPTY));
  var contents=(ClientboundContainerSetContentPacket)lastPacket;
  check(group(contents.items().get(54)).equals(own)&&!group(contents.items().get(62)).equals(own),"real native chest-window resync shades only correct player hotbar slot");
  handle.connection.send(new ClientboundContainerSetSlotPacket(7,3,54,real));
  check(group(((ClientboundContainerSetSlotPacket)lastPacket).getItem()).equals(own),"subsequent external-window single-slot update retains group");
  handle.connection.send(new ClientboundContainerSetSlotPacket(0,4,36,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)));
  check(!group(((ClientboundContainerSetSlotPacket)lastPacket).getItem()).equals(own),"queued unrelated item in the old slot is never shaded");
  // Existing arbitrary components survive the outgoing projection, and were never changed on the server.
  var projected=contents.items().get(54).copy();projected.remove(DataComponents.USE_COOLDOWN);
  check(net.minecraft.world.item.ItemStack.isSameItemSameComponents(projected,real),"all item components except client-only USE_COOLDOWN remain identical");
  var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),handle.level().registryAccess());
  try{
   var outgoing=new ClientboundSetPlayerInventoryPacket(0,contents.items().get(54));
   ClientboundSetPlayerInventoryPacket.STREAM_CODEC.encode(buffer,outgoing);
   var decoded=ClientboundSetPlayerInventoryPacket.STREAM_CODEC.decode(buffer);
   check(group(decoded.contents()).equals(own),"actual vanilla network codec preserves projected item and cooldown group");
   decoded.contents().remove(DataComponents.USE_COOLDOWN);
   check(net.minecraft.world.item.ItemStack.isSameItemSameComponents(decoded.contents(),real),"native packet encode/decode preserves every other original component");
  }finally{buffer.release();}
 }
 private void existing()throws Exception{
  load("E11",false,0);var item=actor.getInventory().getItemInMainHand();var meta=item.getItemMeta();var use=meta.getUseCooldown();
  use.setCooldownSeconds(7.5f);use.setCooldownGroup(new NamespacedKey("probe","prior"));meta.setUseCooldown(use);item.setItemMeta(meta);actor.getInventory().setItemInMainHand(item);
  var original=item.getData(io.papermc.paper.datacomponent.DataComponentTypes.USE_COOLDOWN);var prior=Identifier.parse("probe:prior");
  actor.setCooldown(new NamespacedKey("probe","prior"),200);check(WeaponMechanicsAPI.tryReload(actor),"existing custom cooldown-group weapon reload accepted by vendor");
  later(4,()->{
   var own=ownGroup(0);check(!own.equals(prior)&&own.toString().startsWith("mandomc:reload/"),"existing group projected into unique client-only reload group");
   check(original.equals(actor.getInventory().getItemInMainHand().getData(io.papermc.paper.datacomponent.DataComponentTypes.USE_COOLDOWN)),"existing server USE_COOLDOWN component remains identical during reload");
   cancel();check(ownGroup(0).equals(prior),"cancellation restores original arbitrary cooldown-group component");
   check(actor.getCooldown(new NamespacedKey("probe","prior"))>0,"pre-existing real gameplay cooldown remains active");
   check(cooldowns.stream().noneMatch(p->p.cooldownGroup().equals(prior)&&p.duration()==0),"existing client cooldown is never cleared by reload cleanup");
   actor.setCooldown(new NamespacedKey("probe","prior"),0);lag();
  });
 }
 private void lag()throws Exception{
  load("E11",false,0);check(WeaponMechanicsAPI.tryReload(actor),"lag fixture starts actual vendor reload");
  later(3,()->{
   var own=ownGroup(0);long before=cooldowns.stream().filter(p->p.cooldownGroup().equals(own)&&p.duration()>0).count();
   Thread.sleep(3000); // Force native client timer expiration while the real tick-scheduled reload is paused.
   later(2,()->{
    check(WeaponMechanicsAPI.isReloading(actor),"server-stall fixture preserves actual scheduled reload");
    check(cooldowns.stream().filter(p->p.cooldownGroup().equals(own)&&p.duration()>0).count()>before,"expired client sweep is re-armed for actual remaining reload after lag");
    check(cooldowns.stream().filter(p->p.cooldownGroup().equals(own)&&p.duration()>0).reduce((a,b)->b).orElseThrow().duration()<total,"lag recovery uses remaining duration rather than restarting full reload");
    cancel();lifecycle();
   });
  });
 }
 private void cancel(){WeaponMechanics.getInstance().getEntityWrapper(actor).getMainHandData().cancelTasks();WeaponMechanics.getInstance().getEntityWrapper(actor).getOffHandData().cancelTasks();}
 private void lifecycle()throws Exception{
  load("E11",false,10);deny=true;check(!WeaponMechanicsAPI.tryReload(actor),"actual vendor veto retained");deny=false;
  later(3,()->{
   check(indicator.activeReloads()==0,"vetoed reload has no native sweep");check(WeaponMechanicsAPI.tryReload(actor),"partial-magazine actual reload accepted");
   later(3,()->{
    Identifier old=ownGroup(0);check(old.toString().startsWith("mandomc:reload/"),"partial reload gets native sweep");
    var wrapper=WeaponMechanics.getInstance().getEntityWrapper(actor);
    WeaponMechanics.getInstance().getWeaponHandler().getShootHandler().shootWithoutTrigger(wrapper,"E11",actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND,TriggerType.RIGHT_CLICK,false);
    check(!wrapper.getMainHandData().isReloading()&&indicator.activeReloads()==0,"actual right-click/shoot path still cancels partial reload while native sweep is visible");
    check(!ownGroup(0).equals(old),"shoot cancellation restores icon immediately");cancel();
    load("E11",true,0);check(WeaponMechanicsAPI.tryReload(actor),"actual offhand reload starts");
    later(4,()->{
     Identifier off=ownGroup(40);check(off.toString().startsWith("mandomc:reload/"),"offhand native inventory slot gets its own sweep");
     cancel();check(!ownGroup(40).equals(off)&&indicator.activeReloads()==0,"offhand cancellation clears native visual");
     load("E11",false,0);check(WeaponMechanicsAPI.tryReload(actor),"restart fixture starts");
     later(4,()->{
      Identifier first=ownGroup(0);cancel();check(WeaponMechanicsAPI.tryReload(actor),"cooldown restart accepted by vendor");
      later(4,()->{
       Identifier second=ownGroup(0);check(!first.equals(second),"restart uses a fresh isolated group");
       actor.getInventory().setHeldItemSlot(8);Bukkit.getPluginManager().callEvent(new PlayerItemHeldEvent(actor,0,8));
       check(indicator.activeReloads()==0,"actual weapon-switch event clears observer");
       check(cooldowns.stream().anyMatch(p->p.cooldownGroup().equals(second)&&p.duration()==0),"weapon switch clears only latest native group");cancel();cleanupCases();
      });
     });
    });
   });
  });
 }
 private void cleanupCases()throws Exception{
  load("E11",false,30);check(!WeaponMechanicsAPI.tryReload(actor),"full magazine rejected");
  later(3,()->{
   check(indicator.activeReloads()==0,"full magazine has no sweep");load("E11",false,0);
   for(int slot=1;slot<36;slot++)actor.getInventory().setItem(slot,null);
   check(!WeaponMechanicsAPI.tryReload(actor),"no-ammo reload rejected");
   later(3,()->{
    check(indicator.activeReloads()==0,"no ammo has no sweep");load("E11",false,0);check(WeaponMechanicsAPI.tryReload(actor),"world cleanup actual reload starts");
    later(4,()->{
     var old=ownGroup(0);indicator.world(new PlayerChangedWorldEvent(actor,actor.getWorld()));
     check(indicator.activeReloads()==0&&!ownGroup(0).equals(old),"world-change callback restores client inventory projection");cancel();
     load("E11",false,0);check(WeaponMechanicsAPI.tryReload(actor),"quit cleanup actual reload starts");
     later(4,()->{
      var quit=ownGroup(0);indicator.quit(new PlayerQuitEvent(actor,Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));
      check(indicator.activeReloads()==0&&!ownGroup(0).equals(quit),"logout callback removes sweep without modifying inventory");cancel();
      load("E11",false,0);check(WeaponMechanicsAPI.tryReload(actor),"native death reload starts");
      later(4,()->{
       var death=ownGroup(0);actor.setHealth(0);check(indicator.activeReloads()==0,"actual native death clears observer");
       check(cooldowns.stream().anyMatch(p->p.cooldownGroup().equals(death)&&p.duration()==0),"actual native death clears visual group");
       indicator.close();check(wire.pipeline().names().stream().noneMatch(n->n.startsWith("mandomc_reload_")),"all real Netty overlay handlers removed after cleanup/disable");
       getLogger().info("NATIVE RELOAD PROBE PASSED: "+checks+" assertions; real vendor tasks/native packets/Netty handler/client cooldown algorithm");cleanup();Bukkit.shutdown();
      });
     });
    });
   });
  });
 }
 private interface Work{void run()throws Exception;}
 private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"NATIVE RELOAD PROBE FAILED",error);cleanup();Bukkit.shutdown();}},ticks);}
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
 private void cleanup(){if(indicator!=null)indicator.close();if(actor!=null)WeaponMechanics.getInstance().removeEntityWrapper(actor);if(wire!=null)wire.finishAndReleaseAll();}
 @Override public void onDisable(){cleanup();}
    private Player nativePlayer(Location location)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());Object level=location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BobaStartupTest");
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection"),cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));
        wire=new io.netty.channel.embedded.EmbeddedChannel();wire.pipeline().addLast("packet_handler",new io.netty.channel.ChannelDuplexHandler());
        connectionClass.getField("channel").set(connection,wire);Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=Capture.class.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        for(Class<?> c=list.getClass();c!=null;c=c.getSuperclass())try{var map=c.getDeclaredField("playersByUUID");map.setAccessible(true);((Map<UUID,Object>)map.get(list)).put((UUID)profileClass.getMethod("id").invoke(profile),handle);break;}catch(NoSuchFieldException ignored){}
        levelClass.getMethod("addNewPlayer",playerClass).invoke(level,handle);
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
