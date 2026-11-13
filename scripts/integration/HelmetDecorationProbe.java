package net.mandomc.verification;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.trophies.*;
import net.mandomc.gameplay.clans.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import static net.mandomc.gameplay.trophies.TrophyJournal.*;

/** Disposable helmet custody regression probe; requires isolated port 25594. */
public final class HelmetDecorationProbe extends JavaPlugin implements Listener {
    private MandoPlugin mando;private RancorTrophies trophies;private World world;private Player owner;
    private Block site;private ItemStack item;private int checks,index;private boolean denyPlace,denyBreak;
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();private final List<Entity> entities=new ArrayList<>();
    private final Map<World,List<Object>> worldPlayers=new HashMap<>();private Map<UUID,Object> online;private final List<Chunk> chunks=new ArrayList<>();
    private static final String[] IDS={"beskar_helmet","durasteel_helmet","plastoid_helmet","robes_helmet","rancor_skull"};
    private static final BlockFace[] FACES={BlockFace.UP,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.DOWN};
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(120,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception{
        check(getServer().getPort()==25594,"isolated port enforced");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");trophies=Bukkit.getServicesManager().load(RancorTrophies.class);check(trophies!=null,"existing custody owner registered");
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        world=Bukkit.getWorld(NamespacedKey.minecraft(System.getProperty("mandomc.decoration-test-world","tatooine_review")));check(world!=null,"isolated review world loaded");
        for(int x=-14;x<=-12;x++)for(int z=-14;z<=-12;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
        for(var type:EntityType.values())if(type.isAlive()){check(mando.rules().disabledNaturalSpawn(type,CreatureSpawnEvent.SpawnReason.NATURAL),"natural spawn disabled "+type);for(var reason:List.of(CreatureSpawnEvent.SpawnReason.CUSTOM,CreatureSpawnEvent.SpawnReason.COMMAND,CreatureSpawnEvent.SpawnReason.BREEDING,CreatureSpawnEvent.SpawnReason.SPAWNER))check(!mando.rules().disabledNaturalSpawn(type,reason),"deliberate spawn retained "+type+" "+reason);}
        var saved=new java.io.File(getDataFolder(),"restart.yml");
        if(saved.exists()){
            var data=YamlConfiguration.loadConfiguration(saved);owner=actor(new Location(world,-201.5,200,-199.5),UUID.fromString(data.getString("owner")),"HelmetTester",true);item=ItemStack.deserializeBytes(Base64.getDecoder().decode(data.getString("item")));var entry=trophies.journal().entries().get(UUID.fromString(data.getString("entry")));check(entry!=null&&entry.phase()==Phase.PLACED,"helmet journal survives actual restart");site=world.getBlockAt(entry.cell().x(),entry.cell().y(),entry.cell().z());moveActor(site.getLocation().add(-1.5,0,.5));later(25,()->{check(visuals()==1,"restart retains exactly one visual");inspect(entry);
            var clans=Bukkit.getServicesManager().load(ClanService.class);var clan=clans.state().create("HelmetTest",owner.getUniqueId(),System.currentTimeMillis());clans.state().claim(clan,new ClanState.Chunk(world.getKey().toString(),site.getX()>>4,site.getZ()>>4),4,c->true);
            var outsider=actor(site.getLocation().add(-1.5,0,.5),UUID.randomUUID(),"HelmetOutsider",false);outsider.getInventory().clear();outsider.getInventory().setItemInMainHand(item.clone());var blocked=site.getRelative(BlockFace.SOUTH);blocked.setType(Material.AIR,false);trophies.place(outsider,blocked,EquipmentSlot.HAND);check(outsider.getInventory().getItemInMainHand().isSimilar(item)&&blocked.getType().isAir(),"foreign claim rejects helmet placement without loss");trophies.breakTrophy(outsider,entry);check(site.getType()==Material.BARRIER&&outsider.getInventory().getItemInMainHand().isSimilar(item),"foreign claim rejects helmet recovery");
            nativeBreak(site);await(()->trophies.journal().entries().isEmpty()&&count()==1,()->{check(owner.getInventory().getItemInMainHand().isSimilar(item),"restart return preserves complete helmet metadata");trophies.recover(owner);later(25,()->{check(count()==1,"repeated recovery after restart returns once");getLogger().info("HELMET RESTART PASSED "+checks);Bukkit.shutdown();});});});return;
        }
        owner=actor(new Location(world,-201.5,200,-199.5),UUID.randomUUID(),"HelmetTester",false);owner.getInventory().clear();
        check(!trophies.helmet(new ItemStack(Material.DIAMOND_HELMET)),"vanilla helmet excluded");check(!trophies.helmet(mando.items().create("beskar_chestplate")),"other armor excluded");
        cycle();
    }
    private void give(String id){owner.getInventory().clear();item=mando.items().create(id);check(item!=null&&trophies.decoration(item),"catalog decoration identified "+id);var meta=item.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Saved "+id));meta.getPersistentDataContainer().set(new NamespacedKey("helmetprobe","memory"),PersistentDataType.STRING,"full metadata");if(meta instanceof org.bukkit.inventory.meta.Damageable damage)damage.setDamage(7);if(meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leather)leather.setColor(Color.AQUA);meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,3,true);item.setItemMeta(meta);owner.getInventory().setItemInMainHand(item.clone());}
    private void prepare(BlockFace face)throws Exception{site=world.getBlockAt(-200,200,-200);site.setType(Material.AIR,false);for(var f:FACES)site.getRelative(f).setType(Material.AIR,false);site.getRelative(face.getOppositeFace()).setType(Material.STONE,false);moveActor(site.getLocation().add(-1.5,0,.5));}
    private long count(){return Arrays.stream(owner.getInventory().getStorageContents()).filter(Objects::nonNull).filter(trophies::decoration).mapToInt(ItemStack::getAmount).sum();}
    private long visuals(){return world.getNearbyEntities(site.getLocation().add(.5,.5,.5),1,1,1).stream().filter(ItemDisplay.class::isInstance).count();}
    private Entry entry(){return trophies.journal().at(new Cell(world.getKey().toString(),site.getX(),site.getY(),site.getZ()));}
    private void inspect(Entry e){check(decode(e).isSimilar(item),"journal retains original item metadata");var display=(ItemDisplay)world.getNearbyEntities(site.getLocation().add(.5,.5,.5),1,1,1).stream().filter(ItemDisplay.class::isInstance).findFirst().orElseThrow();check(display.getItemStack().isSimilar(item),"world visual retains dye/name/enchant metadata");check(display.getItemDisplayTransform()==ItemDisplay.ItemDisplayTransform.NONE,"world uses independent NONE pose");check(owner.getInventory().getHelmet()==null||owner.getInventory().getHelmet().isEmpty(),"placement does not alter equipped slot");}
    @EventHandler(priority=EventPriority.LOWEST) public void traceEarly(PlayerInteractEvent e){if(e.getPlayer()==owner)getLogger().info("INPUT early "+e.getAction()+" sneak="+owner.isSneaking()+" item="+mando.items().identify(e.getItem())+" block="+e.useInteractedBlock()+" itemUse="+e.useItemInHand());}
    @EventHandler(priority=EventPriority.MONITOR) public void traceLate(PlayerInteractEvent e){if(e.getPlayer()==owner)getLogger().info("INPUT late "+e.getAction()+" block="+e.useInteractedBlock()+" itemUse="+e.useItemInHand());}
    private void cycle()throws Exception{
        if(index==30){protection();return;}var face=FACES[index%6];give(IDS[index/6]);prepare(face);owner.setSneaking(false);
        if(trophies.helmet(item)){var normal=new PlayerInteractEvent(owner,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,item.clone(),site.getRelative(face.getOppositeFace()),face,EquipmentSlot.HAND);trophies.interact(normal);check(!normal.isCancelled()&&count()==1&&entry()==null,"normal equip interaction bypasses placement");}
        owner.setSneaking(true);check(owner.isSneaking(),"sneak flag set");nativeUse(site.getRelative(face.getOppositeFace()),face);await(()->entry()!=null&&entry().phase()==Phase.PLACED&&visuals()==1,()->{check(count()==0,"exactly one helmet consumed "+index);check(entry().face()==Face.valueOf(face.name()),"actual native block face retained "+face);inspect(entry());nativeBreak(site);await(()->entry()==null&&trophies.journal().entries().isEmpty()&&count()==1,()->{check(site.getType().isAir()&&visuals()==0,"native break clears anchor and visual");check(owner.getInventory().getItemInMainHand().isSimilar(item),"native break returns same helmet");index++;cycle();});});
    }
    @EventHandler(priority=EventPriority.HIGH) public void place(BlockPlaceEvent e){if(denyPlace)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH) public void breaking(BlockBreakEvent e){if(denyBreak)e.setCancelled(true);}
    private void protection()throws Exception{
        give(IDS[0]);prepare(BlockFace.UP);denyPlace=true;trophies.place(owner,site,EquipmentSlot.HAND);later(40,()->{check(trophies.journal().entries().isEmpty()&&count()==1&&site.getType().isAir(),"cancelled protection event refunds exact one helmet");denyPlace=false;trophies.place(owner,site,EquipmentSlot.HAND);await(()->entry()!=null&&entry().phase()==Phase.PLACED&&site.getType()==Material.BARRIER,()->{denyBreak=true;trophies.breakTrophy(owner,entry());check(site.getType()==Material.BARRIER&&count()==0,"cancelled break retains custody");denyBreak=false;
            var blocks=new ArrayList<>(List.of(site));trophies.entityExplosion(new EntityExplodeEvent(owner,site.getLocation(),blocks,1,ExplosionResult.DESTROY));check(blocks.isEmpty(),"explosion preserves helmet anchor");var piston=new BlockPistonExtendEvent(site.getRelative(BlockFace.EAST),List.of(site),BlockFace.WEST);trophies.piston(piston);check(piston.isCancelled(),"piston preserves helmet anchor");
            for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Material.STONE,64));trophies.breakTrophy(owner,entry());await(()->site.getType().isAir()&&trophies.journal().entries().values().stream().anyMatch(e->e.phase()==Phase.RETURNING),()->{check(count()==0,"full inventory return remains saved");owner.getInventory().setItem(0,null);trophies.recover(owner);await(()->count()==1&&trophies.journal().entries().isEmpty(),this::persist);});});});
    }
    private void persist()throws Exception{if(world.getKey().equals(NamespacedKey.minecraft("devworld"))){check(!trophies.journal().entries().values().stream().anyMatch(e->e.owner().equals(owner.getUniqueId())),"DevWorld placement and recovery need no frozen baseline");getLogger().info("DEVWORLD DECORATIONS PASSED "+checks);Bukkit.shutdown();return;}give(IDS[3]);prepare(BlockFace.EAST);trophies.place(owner,site,EquipmentSlot.HAND,Face.EAST,site.getRelative(BlockFace.WEST));await(()->entry()!=null&&entry().phase()==Phase.PLACED&&visuals()==1,()->{var d=new YamlConfiguration();d.set("owner",owner.getUniqueId().toString());d.set("entry",entry().id().toString());d.set("item",Base64.getEncoder().encodeToString(item.serializeAsBytes()));d.save(new java.io.File(getDataFolder(),"restart.yml"));owner.saveData();getLogger().info("HELMET LIFECYCLE PASSED "+checks);Bukkit.shutdown();});}
    private void moveActor(Location location)throws Exception{
        // Unregistered fixture players have no client connection to acknowledge Bukkit teleports.
        var handle=handles.get(owner.getUniqueId());handle.getClass().getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());handle.getClass().getMethod("setYRot",float.class).invoke(handle,location.getYaw());handle.getClass().getMethod("setXRot",float.class).invoke(handle,location.getPitch());check(owner.getLocation().distanceSquared(location)<1e-6,"fixture actor is actually within native reach");
    }
    private void nativeUse(Block clicked,BlockFace face)throws Exception{
        var handle=handles.get(owner.getUniqueId());var mode=handle.getClass().getField("gameMode").get(handle);var posType=Class.forName("net.minecraft.core.BlockPos");var dirType=Class.forName("net.minecraft.core.Direction");var handType=Class.forName("net.minecraft.world.InteractionHand");var vecType=Class.forName("net.minecraft.world.phys.Vec3");var hitType=Class.forName("net.minecraft.world.phys.BlockHitResult");var pos=posType.getConstructor(int.class,int.class,int.class).newInstance(clicked.getX(),clicked.getY(),clicked.getZ());var at=clicked.getLocation().add(.5,.5,.5).add(face.getDirection().multiply(.5));if(clicked.getType()==Material.STONE_SLAB&&face==BlockFace.UP)at.setY(clicked.getY()+.5);var vec=vecType.getConstructor(double.class,double.class,double.class).newInstance(at.getX(),at.getY(),at.getZ());var direction=dirType.getField(face.name()).get(null);var hand=handType.getField("MAIN_HAND").get(null);var hit=hitType.getConstructor(vecType,dirType,posType,boolean.class).newInstance(vec,direction,pos,false);var item=handle.getClass().getMethod("getItemInHand",handType).invoke(handle,hand);var level=world.getClass().getMethod("getHandle").invoke(world);Arrays.stream(mode.getClass().getMethods()).filter(m->m.getName().equals("useItemOn")&&m.getParameterCount()==5).findFirst().orElseThrow().invoke(mode,handle,level,item,hand,hit);
    }
    private void nativeBreak(Block block)throws Exception{
        var handle=handles.get(owner.getUniqueId());var mode=handle.getClass().getField("gameMode").get(handle);var posType=Class.forName("net.minecraft.core.BlockPos");var dirType=Class.forName("net.minecraft.core.Direction");var actionType=Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");var pos=posType.getConstructor(int.class,int.class,int.class).newInstance(block.getX(),block.getY(),block.getZ());mode.getClass().getMethod("handleBlockBreakAction",posType,actionType,dirType,int.class,int.class).invoke(mode,pos,actionType.getField("START_DESTROY_BLOCK").get(null),dirType.getField("UP").get(null),world.getMaxHeight(),1);
    }
    private ItemStack decode(Entry e){return ItemStack.deserializeBytes(Base64.getDecoder().decode(e.item()));}
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable failure){fail(failure);}},ticks);}
    private void await(BooleanSupplier predicate,Check action){await(predicate,action,0);}
    private void await(BooleanSupplier predicate,Check action,int tries){later(2,()->{if(predicate.getAsBoolean())action.run();else if(tries>150)throw new AssertionError("Timed out waiting for native custody transition");else await(predicate,action,tries+1);});}
    private void check(boolean condition,String why){if(!condition)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
    private void fail(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"HELMET PROBE FAILED",failure);Bukkit.shutdown();}
    @SuppressWarnings("unchecked") private Player actor(Location where,UUID id,String name,boolean load)throws Exception{
        var p=nativeTestPlayer(where.getWorld(),where,id,name);actors.add(p);if(load)p.loadData();p.setGameMode(GameMode.SURVIVAL);var h=p.getClass().getMethod("getHandle").invoke(p);handles.put(id,h);var level=p.getWorld().getClass().getMethod("getHandle").invoke(p.getWorld());var list=worldPlayers.computeIfAbsent(p.getWorld(),w->{try{return(List<Object>)level.getClass().getMethod("players").invoke(level);}catch(Exception error){throw new RuntimeException(error);}});list.add(h);online.put(id,h);return p;
    }
    @Override public void onDisable(){HandlerList.unregisterAll((Listener)this);entities.forEach(e->{if(e.isValid())e.remove();});for(var p:actors){online.remove(p.getUniqueId());var list=worldPlayers.get(p.getWorld());if(list!=null)list.remove(handles.get(p.getUniqueId()));}chunks.forEach(c->c.removePluginChunkTicket(this));}
    private Player nativeTestPlayer(World world, Location location, UUID id, String name) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(id, name);
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
