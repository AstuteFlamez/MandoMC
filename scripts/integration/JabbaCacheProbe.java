package net.mandomc.verification;
import java.util.*;import java.nio.file.*;import org.bukkit.*;import org.bukkit.block.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.inventory.*;import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;import net.mandomc.gameplay.dungeon.*;
/** Disposable current-map native interaction regression; never install on a shared player server. */
public final class JabbaCacheProbe extends JavaPlugin implements Listener {
 MandoPlugin p;JabbaEvent event;World world;Player actor;int checks;boolean fixed;
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{begin();}catch(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"CACHE FAILED",t);finish("FAILED.txt",t.toString());}},120);}
 void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;getLogger().info("PASS "+reason);}
 void finish(String file,String result){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(file),result);}catch(Exception e){throw new RuntimeException(e);}Bukkit.shutdown();}
 @EventHandler(priority=EventPriority.LOWEST) public void before(PlayerInteractEvent e){if(e.getPlayer()==actor)getLogger().info("NATIVE BEFORE "+e.getAction()+" "+e.useInteractedBlock()+" "+e.useItemInHand());}
 @EventHandler(priority=EventPriority.MONITOR) public void after(PlayerInteractEvent e){if(e.getPlayer()==actor)getLogger().info("NATIVE AFTER "+e.getAction()+" "+e.useInteractedBlock()+" "+e.useItemInHand());}
 void begin()throws Exception {
 p=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");event=(JabbaEvent)p.events().event("jabba");world=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));
 fixed=!Boolean.getBoolean("mandomc.cache.reproduce");Bukkit.getPluginManager().registerEvents(this,this);
 actor=nativeTestPlayer(world,new Location(world,170.5,122,134.5));actor.setGameMode(GameMode.SURVIVAL);check(!actor.isOp(),"native player is non-op survival");
 check(p.events().start("jabba"),"current event starts");check(event.cachesRemaining()==22,"22 registered caches spawn");
 var specs=event.settings().rooms().stream().flatMap(r->r.chests().stream()).toList();var owned=new HashSet<String>();specs.forEach(c->owned.add(c.pos().key()));
 Block decorativeBlock=null;int decorative=0;for(int x=95;x<=205;x++)for(int z=115;z<=197;z++)for(int y=84;y<=145;y++){var b=world.getBlockAt(x,y,z);if((b.getType()==Material.CHEST||b.getType()==Material.TRAPPED_CHEST)&&!owned.contains(x+","+y+","+z)){decorative++;decorativeBlock=b;getLogger().info("DECORATIVE "+x+","+y+","+z);}}
 getLogger().info("DECORATIVE COUNT "+decorative);
 if(fixed&&decorativeBlock!=null){var state=decorativeBlock.getBlockData().getAsString();int amount=count();nativeClick(decorativeBlock,EquipmentSlot.HAND);check(decorativeBlock.getBlockData().getAsString().equals(state)&&count()==amount&&event.cachesRemaining()==22,"decorative map chest grants no event reward and is preserved");}
 Block first=specs.getFirst().pos().location(world).getBlock();
 if(fixed){var denial=new Listener(){};Bukkit.getPluginManager().registerEvent(PlayerInteractEvent.class,denial,EventPriority.LOW,(listener,e)->{var click=(PlayerInteractEvent)e;if(click.getPlayer()==actor)click.setCancelled(true);},this);nativeClick(first,EquipmentSlot.HAND);check(first.getType()==Material.CHEST&&event.cachesRemaining()==22&&count()==0,"independent protection cancellation remains authoritative");HandlerList.unregisterAll(denial);}
 int before=count();var rolled=reward(first);nativeClick(first,EquipmentSlot.HAND);
 if(!fixed){check(first.getType()==Material.CHEST&&event.cachesRemaining()==22&&count()==before,"REPRODUCED native click denied; no loot/chest consumption");finish("REPRODUCED.txt",checks+" checks");return;}
 check(first.getType().isAir()&&event.cachesRemaining()==21&&count()>before,"native click grants loot and consumes registered cache");check(sameReward(rolled),"exact catalog metadata and rolled amounts delivered");int granted=count();nativeClick(first,EquipmentSlot.HAND);check(count()==granted&&event.cachesRemaining()==21,"duplicate native click no reward");
 first.setType(Material.CHEST,false);nativeClick(first,EquipmentSlot.HAND);check(first.getType()==Material.CHEST&&count()==granted&&event.cachesRemaining()==21,"unregistered chest at former cache cell stays protected and gives no reward");first.setType(Material.AIR,false);
 for(int i=0;i<36;i++)actor.getInventory().setItem(i,new ItemStack(Material.STONE,64));var second=specs.get(1).pos().location(world).getBlock();var storage=actor.getInventory().getStorageContents();var retained=reward(second);nativeClick(second,EquipmentSlot.HAND);check(second.getType()==Material.CHEST&&event.cachesRemaining()==21,"full inventory retains cache and reward");check(Arrays.equals(storage,actor.getInventory().getStorageContents())&&retained.equals(reward(second)),"failed capacity leaves inventory and exact cached roll unchanged");actor.getInventory().clear();nativeClick(second,EquipmentSlot.OFF_HAND);check(second.getType()==Material.CHEST&&event.cachesRemaining()==21,"offhand cannot consume");nativeClick(second,EquipmentSlot.HAND);check(second.getType().isAir()&&count()>0,"retry after freeing space succeeds once");
 var failureBlock=specs.get(2).pos().location(world).getBlock();actor.getInventory().clear();var failureRoll=reward(failureBlock);var attempts=new int[]{0};
 var badInventory=(PlayerInventory)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class[]{PlayerInventory.class},(proxy,method,args)->{if(method.getName().equals("setStorageContents")&&attempts[0]++==0)throw new IllegalStateException("INJECTED delivery failure");try{return method.invoke(actor.getInventory(),args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
 var failingPlayer=(Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(proxy,method,args)->{if(method.getName().equals("getInventory"))return badInventory;try{return method.invoke(actor,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
 event.click(new PlayerInteractEvent(failingPlayer,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,failureBlock,BlockFace.UP,EquipmentSlot.HAND));check(failureBlock.getType()==Material.CHEST&&count()==0&&failureRoll.equals(reward(failureBlock)),"failed inventory API delivery preserves chest, exact roll and empty inventory");
 int room=event.progress().room();for(var spec:specs.subList(2,22)){actor.getInventory().clear();nativeClick(spec.pos().location(world).getBlock(),EquipmentSlot.HAND);}check(event.cachesRemaining()==0&&event.progress().room()==room,"all22 native cache clicks succeed without gating room progression");
 p.events().stop(false);check(!event.running(),"event stop");check(p.events().start("jabba")&&event.cachesRemaining()==22,"new run replenishes22 caches");p.events().stop(false);event.close();check(Bukkit.getServicesManager().load(net.mandomc.gameplay.events.EventCaches.class)==null,"closed cache owner unregisters authorization");
 var cfg=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("events/jabba.yml").toFile());var recovered=new JabbaEvent(p,p.items(),cfg);check(Bukkit.getServicesManager().load(net.mandomc.gameplay.events.EventCaches.class)==recovered,"recovered event owns authorization");
 var journal=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("jabba/palace-run.yml").toFile());check(journal.getString("world").equals("minecraft:tatooine_review"),"journal retains migrated world");for(String row:journal.getStringList("cleanup")){var a=row.split("\\|",2);check(JabbaSettings.Pos.parse(a[0]).location(world).getBlock().getBlockData().getAsString().equals(Bukkit.createBlockData(a[1]).getAsString()),"exact recovery cell "+a[0]);}
 recovered.start();check(recovered.cachesRemaining()==22,"recovered new run has22 caches");recovered.stop(false);recovered.close();finish("PASSED.txt",checks+" native assertions");
 }
 @SuppressWarnings("unchecked") List<ItemStack> reward(Block b)throws Exception{var f=JabbaEvent.class.getDeclaredField("caches");f.setAccessible(true);Object cache=((Map<String,Object>)f.get(event)).get(b.getX()+","+b.getY()+","+b.getZ());var m=cache.getClass().getDeclaredMethod("loot");m.setAccessible(true);return ((List<ItemStack>)m.invoke(cache)).stream().map(ItemStack::clone).toList();}
 boolean sameReward(List<ItemStack> expected){var staged=Bukkit.createInventory(null,36);for(var item:expected)staged.addItem(item.clone());return Arrays.equals(staged.getContents(),actor.getInventory().getStorageContents());}
 int count(){return Arrays.stream(actor.getInventory().getStorageContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();}
 void nativeClick(Block b,EquipmentSlot hand)throws Exception {
 Object player=actor.getClass().getMethod("getHandle").invoke(actor);Class<?> sp=player.getClass();Object gm=sp.getField("gameMode").get(player);Object level=world.getClass().getMethod("getHandle").invoke(world);
 var bp=Class.forName("net.minecraft.core.BlockPos").getConstructor(int.class,int.class,int.class).newInstance(b.getX(),b.getY(),b.getZ());
 var vec=Class.forName("net.minecraft.world.phys.Vec3").getConstructor(double.class,double.class,double.class).newInstance(b.getX()+.5,b.getY()+1.,b.getZ()+.5);
 var direction=Class.forName("net.minecraft.core.Direction");var hitType=Class.forName("net.minecraft.world.phys.BlockHitResult");var hit=hitType.getConstructor(vec.getClass(),direction,bp.getClass(),boolean.class).newInstance(vec,direction.getField("UP").get(null),bp,false);
 var h=Class.forName("net.minecraft.world.InteractionHand");Object hnd=h.getField(hand==EquipmentSlot.HAND?"MAIN_HAND":"OFF_HAND").get(null);Object item=sp.getMethod("getItemInHand",h).invoke(player,hnd);
 gm.getClass().getMethod("useItemOn",sp,Class.forName("net.minecraft.world.level.Level"),Class.forName("net.minecraft.world.item.ItemStack"),h,hitType).invoke(gm,player,level,item,hnd,hit);
 }
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "MobTest");
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

}
