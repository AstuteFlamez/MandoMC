package net.mandomc.verification;
import java.util.*;import java.nio.file.*;
import org.bukkit.*;import org.bukkit.block.*;import org.bukkit.block.data.Directional;import org.bukkit.configuration.file.YamlConfiguration;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.block.Action;import org.bukkit.event.player.PlayerInteractEvent;import org.bukkit.inventory.*;import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;import net.mandomc.gameplay.dungeon.*;
/** Isolated two-room operation: no real player inventory or palace terrain is changed. */
public final class JabbaDungeonProbe extends JavaPlugin {
 private MandoPlugin plugin;private World world;private JabbaEvent event,recovered;private Player actor;private int checks;private final List<BlockState> blocks=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();private Path blueprint,journal;
 @Override public void onEnable(){later(140,this::begin);}
 private void begin() throws Exception {
  plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
  check(plugin.events().event("jabba")!=null,"Jabba registered with existing event scheduler");check(Bukkit.getPluginManager().getPlugin("BigDoors")==null,"BigDoors absent from runtime");
  var real=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("events/jabba.yml").toFile());var settings=JabbaSettings.read(real);check(settings.rooms().size()==9&&settings.rooms().stream().mapToInt(r->r.chests().size()).sum()==22,"all nine rooms and 22 legacy caches configured");
  check(real.getString("messages.opened").contains("Room <room> unlocked » blast doors opening"),"requested room message and » symbol retained");
  try(var gates=new NativeGates(plugin,Bukkit.getWorld(NamespacedKey.minecraft("tatooine")),settings,plugin.getDataFolder().toPath().resolve(settings.blueprints()).toFile())){check(gates.resetPlan().size()>200,"all nine private gate panels load without BigDoors");}
  for(String id:settings.loot().keySet())check(id.equals(plugin.items().identify(plugin.items().create(id))),"legacy stash item registered: "+id);
  for(var chest:settings.rooms().stream().flatMap(r->r.chests().stream()).toList()){var b=chest.pos().location(Bukkit.getWorld(NamespacedKey.minecraft("tatooine"))).getBlock();check(b.getType().isAir()||b.getType()==Material.CHEST,"real stash position valid: "+chest.pos().key());if(b.getState() instanceof Container c)check(c.getInventory().isEmpty(),"legacy leftover cache contains no player items");}
  for(int x=81;x<=83;x++)for(int z=81;z<=83;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
  for(int x=1298;x<=1335;x++)for(int z=1298;z<=1335;z++){var b=world.getBlockAt(x,300,z);if(!b.getType().isAir())throw new IllegalStateException("Sky fixture occupied");blocks.add(b.getState());b.setType(Material.SANDSTONE,false);}
  for(int x=1298;x<=1335;x++)for(int z=1298;z<=1335;z++)for(int y=301;y<=304;y++)if(!world.getBlockAt(x,y,z).getType().isAir())throw new IllegalStateException("Sky fixture above floor occupied");
  blueprint=plugin.getDataFolder().toPath().resolve("jabba/probe-native-doors.yml");journal=plugin.getDataFolder().toPath().resolve("jabba/probe_native-run.yml");Files.deleteIfExists(journal);var panels=new YamlConfiguration();panels.set("schema-version",1);panels.set("world",world.getKey().toString());
  for(int id:List.of(15,16)){var cells=new ArrayList<String>();int x=id==15?1305:1315,z=id==15?1303:1313;for(int y=301;y<=303;y++)for(int a=z;a<=z+2;a++)cells.add(x+","+y+","+a+"|minecraft:iron_block");panels.set("gates."+id+".blocks",cells);}panels.save(blueprint.toFile());
  actor=nativeTestPlayer(world,new Location(world,1307.5,301,1304.5));event=new JabbaEvent(plugin,plugin.items(),config());event.start();check(event.progress().phase()==DungeonProgress.Phase.OPENING,"start opens entry before spawning room one");
  check(displayCount()>0,"native block-display door animation exists");check(world.getBlockAt(1305,301,1303).getType()==Material.BARRIER,"gate stays physically blocked during animation");
  later(12,this::roomOne);
 }
 private YamlConfiguration config(){var c=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("events/jabba.yml").toFile());c.set("profile","probe_native");c.set("broadcast-start",false);c.set("world",world.getKey().toString());c.set("blueprints","jabba/probe-native-doors.yml");c.set("animation-ticks",6);c.set("bounds.min",List.of(1298,300,1298));c.set("bounds.max",List.of(1335,310,1335));c.set("rooms",null);
  for(int n:List.of(1,2)){String p="rooms."+n+".";c.set(p+"gate",n+14);c.set(p+"movement",List.of(0,4,0));c.set(p+"mob",n==1?"guards":"Bossk");c.set(p+"count-per-spawn",n==1?2:1);c.set(p+"spawns",List.of(n==1?List.of(1302,301,1304):List.of(1319,301,1314)));c.set(p+"chests",n==1?List.of(List.of(1307,301,1304,"EAST"),List.of(1308,301,1306,"NORTH")):List.of(List.of(1321,301,1315,"SOUTH")));}return c;
 }
 private void freeze(){for(var id:event.progress().mobs()){var e=(LivingEntity)Bukkit.getEntity(id);if(e instanceof Mob m)m.setAI(false);e.setGravity(false);}}
 private void roomOne() throws Exception {freeze();check(event.progress().phase()==DungeonProgress.Phase.COMBAT&&event.progress().mobs().size()==2,"configured first wave spawned");check(world.getBlockAt(1305,301,1303).getType()==Material.AIR&&displayCount()==0&&world.getBlockAt(1305,305,1303).getType()==Material.IRON_BLOCK,"opening clears passage and parks actual door blocks");check(world.getBlockAt(1315,301,1313).getType()==Material.IRON_BLOCK,"next gate remains closed");
  check(event.cachesRemaining()==3&&world.getBlockAt(1307,301,1304).getType()==Material.CHEST&&world.getBlockAt(1308,301,1306).getType()==Material.CHEST&&world.getBlockAt(1321,301,1315).getType()==Material.CHEST,"all rooms' caches spawn at event start");
  var first=world.getBlockAt(1307,301,1304);check(((Directional)first.getBlockData()).getFacing()==BlockFace.EAST,"cache keeps configured facing");int before=inventoryCount();click(first,EquipmentSlot.OFF_HAND);check(inventoryCount()==before&&event.cachesRemaining()==3,"offhand does not claim cache");click(first,EquipmentSlot.HAND);int granted=inventoryCount()-before;check(granted>=1&&granted<=3,"one click grants legacy one-to-three loot rolls");click(first,EquipmentSlot.HAND);check(inventoryCount()-before==granted&&event.cachesRemaining()==2,"duplicate click cannot duplicate loot");check(event.progress().phase()==DungeonProgress.Phase.COMBAT&&event.progress().room()==1,"looting cannot advance room progression");
  var foreign=world.spawn(new Location(world,1301,301,1301),Cow.class);foreign.setHealth(0);check(event.progress().mobs().size()==2,"unrelated death does not advance room");var ids=new ArrayList<>(event.progress().mobs());((LivingEntity)Bukkit.getEntity(ids.get(0))).setHealth(0);check(event.progress().room()==1&&world.getBlockAt(1315,301,1313).getType()==Material.IRON_BLOCK,"door waits for final owned enemy");((LivingEntity)Bukkit.getEntity(ids.get(1))).setHealth(0);
  check(event.progress().room()==2&&event.progress().phase()==DungeonProgress.Phase.OPENING,"last owned enemy immediately unlocks next room");check(world.getBlockAt(1308,301,1306).getType()==Material.CHEST&&event.cachesRemaining()==2,"unlooted earlier caches do not block the next room");later(12,this::roomTwo);
 }
 private void roomTwo() throws Exception {freeze();check(event.progress().mobs().size()==1,"configured boss room spawns exactly one boss");var id=event.progress().mobs().iterator().next();check("Bossk".equals(new net.mandomc.integrations.mobs.MythicMobAccess().type(Bukkit.getEntity(id))),"correct boss type used");((LivingEntity)Bukkit.getEntity(id)).setHealth(0);check(event.progress().phase()==DungeonProgress.Phase.COMPLETE&&event.running(),"final kill completes palace while event stays open");check(event.cachesRemaining()==2&&world.getBlockAt(1321,301,1315).getType()==Material.CHEST,"unopened caches remain available after final boss");click(world.getBlockAt(1321,301,1315),EquipmentSlot.HAND);check(event.progress().phase()==DungeonProgress.Phase.COMPLETE&&event.running(),"late cache looting does not end the event");event.tick();check(event.running()&&world.getBlockAt(1315,301,1313).getType()==Material.AIR,"completion keeps doors open until scheduled stop");event.stop(false);check(!event.running()&&world.getBlockAt(1305,301,1303).getType()==Material.IRON_BLOCK&&world.getBlockAt(1315,301,1313).getType()==Material.IRON_BLOCK,"event stop restores closed gates");check(world.getBlockAt(1308,301,1306).getType()==Material.AIR&&world.getBlockAt(1321,301,1315).getType()==Material.AIR,"event end removes remaining and looted caches");event.start();later(12,this::interrupt);
 }
 private void interrupt() throws Exception {freeze();var oldIds=event.progress().mobs();world.getBlockAt(1307,301,1304).setType(Material.CHEST,false);var field=JabbaEvent.class.getDeclaredField("gates");field.setAccessible(true);((NativeGates)field.get(event)).close();HandlerList.unregisterAll(event);
  recovered=new JabbaEvent(plugin,plugin.items(),config());check(oldIds.stream().allMatch(id->Bukkit.getEntity(id)==null||!Bukkit.getEntity(id).isValid()),"restart recovery removes prior run mobs");check(world.getBlockAt(1307,301,1304).getType()==Material.AIR&&world.getBlockAt(1305,301,1303).getType()==Material.IRON_BLOCK,"recovery clears caches and restores exact gate panels");event.close();recovered.start();recovered.stop(false);later(12,()->{check(recovered.progress().phase()==DungeonProgress.Phase.IDLE&&world.getEntities().stream().noneMatch(e->e.getScoreboardTags().contains("mandomc_jabba_probe_native")),"stopping during gate animation cannot spawn a late wave");check(displayCount()==0,"no gate visuals left after reset");getLogger().info("JABBA DUNGEON PROBE PASSED: "+checks+" assertions");cleanup();});
 }
 private int inventoryCount(){return Arrays.stream(actor.getInventory().getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();}
 private void click(Block block,EquipmentSlot hand){event.click(new PlayerInteractEvent(actor,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,hand));}
 private long displayCount(){return world.getEntitiesByClass(BlockDisplay.class).stream().filter(d->d.getScoreboardTags().contains("mandomc_jabba_gate")&&d.getX()>1290&&d.getX()<1340).count();}
 private interface Check{void run() throws Exception;}
 private void later(long n,Check c){Bukkit.getScheduler().runTaskLater(this,()->{try{c.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"JABBA DUNGEON PROBE FAILED",e);cleanup();}},n);}
 private void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;getLogger().info("PASS: "+s);}
 private void cleanup(){Bukkit.getScheduler().cancelTasks(this);if(event!=null)event.close();if(recovered!=null)recovered.close();if(world!=null){for(int id:List.of(15,16)){int x=id==15?1305:1315,z=id==15?1303:1313;for(int y=301;y<=307;y++)for(int a=z;a<=z+2;a++)world.getBlockAt(x,y,a).setType(Material.AIR,false);}for(var b:List.of(world.getBlockAt(1307,301,1304),world.getBlockAt(1308,301,1306),world.getBlockAt(1321,301,1315)))b.setType(Material.AIR,false);world.getEntitiesByClass(Item.class).stream().filter(e->e.getX()>1290&&e.getX()<1340&&e.getY()>290&&e.getY()<320&&e.getZ()>1290&&e.getZ()<1340).toList().forEach(Entity::remove);}blocks.forEach(b->b.update(true,false));blocks.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();try{if(journal!=null)Files.deleteIfExists(journal);if(blueprint!=null)Files.deleteIfExists(blueprint);}catch(Exception ignored){}}
 @Override public void onDisable(){cleanup();}
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
