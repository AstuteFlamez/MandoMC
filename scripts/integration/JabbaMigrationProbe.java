package net.mandomc.verification;
import java.util.*;import java.nio.file.*;import org.bukkit.*;import org.bukkit.block.*;import org.bukkit.configuration.file.YamlConfiguration;import org.bukkit.entity.*;import org.bukkit.event.block.Action;import org.bukkit.event.player.PlayerInteractEvent;import org.bukkit.inventory.*;import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;import net.mandomc.gameplay.dungeon.*;
public final class JabbaMigrationProbe extends JavaPlugin {
 MandoPlugin p;JabbaEvent event;World world;int checks,room;Player actor;Map<String,String> original=new LinkedHashMap<>();
 public void onEnable(){later(120,this::waitHoth);}
 void waitHoth() throws Exception {if(Bukkit.getPluginManager().getPlugin("HothCavesProbe")==null || Files.exists(getDataFolder().toPath().getParent().resolve("HothCavesProbe/PASSED.txt")))begin();else later(20,this::waitHoth);}
 interface Work{void run()throws Exception;}
 void later(long ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"MIGRATION FAILED",t);write("FAILED.txt",t.toString());Bukkit.shutdown();}},ticks);}
 void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
 void write(String name,String value){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name),value);}catch(Exception e){throw new RuntimeException(e);}}
 void begin()throws Exception{
 p=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");p.events().stop(false);event=(JabbaEvent)p.events().event("jabba");world=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));
 check(event.settings().world().equals("minecraft:tatooine_review"),"event targets new planet");check(event.settings().rooms().size()==9,"nine rooms");
 var cfg=YamlConfiguration.loadConfiguration(p.getDataFolder().toPath().resolve("jabba/doors.yml").toFile());var gs=cfg.getConfigurationSection("gates");
 for(String gate:gs.getKeys(false))for(String line:gs.getStringList(gate+".blocks")){var a=line.split("\\|",2);var pos=JabbaSettings.Pos.parse(a[0]);var actual=pos.location(world).getBlock().getBlockData().getAsString();check(actual.equals(Bukkit.createBlockData(a[1]).getAsString()),"gate original matches "+a[0]);original.put(a[0],actual);}
 for(var r:event.settings().rooms()){
 for(var pos:r.spawns()){var b=pos.location(world).getBlock();check(b.isPassable()&&b.getRelative(0,1,0).isPassable(),"clear spawn "+pos.key());check(!b.getRelative(0,-1,0).getCollisionShape().getBoundingBoxes().isEmpty(),"supported spawn "+pos.key());}
 for(var c:r.chests()){var b=c.pos().location(world).getBlock();check(b.getType().isAir(),"empty cache cell "+c.pos().key());original.put(c.pos().key(),b.getBlockData().getAsString());}}
 actor=nativeTestPlayer(world,new Location(world,166.5,121,127.5));actor.setGameMode(GameMode.SURVIVAL);
 check(p.events().start("jabba"),"scheduler starts migrated event");check(event.cachesRemaining()==22,"all22 caches at start");int startRoom=event.progress().room();
 for(var r:event.settings().rooms())for(var c:r.chests()){var b=c.pos().location(world).getBlock();check(b.getType()==Material.CHEST,"cache created "+c.pos().key());event.click(new PlayerInteractEvent(actor,Action.RIGHT_CLICK_BLOCK,null,b,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND));check(b.getType().isAir(),"one click removes cache");}
 check(event.cachesRemaining()==0,"all22 caches looted");check(event.progress().room()==startRoom,"looting never advances gate");room=1;later(40,this::fight);
 }
 void fight(){
 check(event.running(),"operation still running");check(event.progress().room()==room,"expected room "+room);check(event.progress().phase()==DungeonProgress.Phase.COMBAT,"combat room "+room);
 var ids=new ArrayList<>(event.progress().mobs());var spec=event.settings().rooms().get(room-1);check(ids.size()==spec.spawns().size()*spec.count(),"all configured enemies spawned room "+room);
 for(int i=0;i<ids.size();i++){
 var e=Bukkit.getEntity(ids.get(i));check(e instanceof LivingEntity&&e.getWorld()==world,"native mob in new planet");check(event.settings().contains(e.getLocation()),"mob in migrated bounds");((LivingEntity)e).setHealth(0);
 if(i<ids.size()-1)check(event.progress().room()==room&&event.progress().phase()==DungeonProgress.Phase.COMBAT,"only last enemy advances");}
 if(room<9){room++;later(40,this::fight);}else{check(event.progress().phase()==DungeonProgress.Phase.COMPLETE,"room9 completed");later(3,this::finish);}
 }
 void finish(){p.events().stop(false);check(!event.running(),"normal stop");for(var a:original.entrySet()){var b=JabbaSettings.Pos.parse(a.getKey()).location(world).getBlock();check(b.getBlockData().getAsString().equals(a.getValue()),"exact restoration "+a.getKey());}
 check(world.getEntities().stream().noneMatch(e->e.getScoreboardTags().contains("mandomc_jabba_palace")||e.getScoreboardTags().contains("mandomc_jabba_gate_palace")),"all newworld mobs and panels removed");
 check(p.events().start("jabba"),"second run starts cleanly");check(event.cachesRemaining()==22,"all caches replenish only for new run");p.events().stop(false);write("PASSED.txt",checks+" native assertions; all9 rooms and22 caches; clean stop and second start");getLogger().info("MIGRATION PASSED "+checks);Bukkit.shutdown();}
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
