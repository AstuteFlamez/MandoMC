package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.mobs.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real scheduled encounters in a disposable prepared desert; never invokes an admin/manual spawn path. */
public final class CreativeSpawnProbe extends JavaPlugin implements Listener {
    private MandoPlugin mando;
    private World world;
    private Player player;
    private Object handle;
    private List<Object> worldPlayers;
    private Map<UUID,Object> online;
    private Location point;
    private Boolean previousRule;
    private MythicMobAccess mobs;
    private final List<Chunk> chunks=new ArrayList<>();
    private int checks,naturalCandidates,creativeCount;
    private final NamespacedKey encounter=new NamespacedKey("mandomc","sandy_encounter_v1");
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this,this);
        later(80,this::begin);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void nativeCandidate(CreatureSpawnEvent event) {
        if(event.getSpawnReason()==CreatureSpawnEvent.SpawnReason.NATURAL)naturalCandidates++;
    }
    @SuppressWarnings("unchecked") private void begin() throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");mobs=new MythicMobAccess();world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        point=new Location(world,4000.5,281,4000.5);
        previousRule=world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        for(int x=3920;x<=4080;x+=16)for(int z=3920;z<=4080;z+=16) {var chunk=world.getChunkAt(x>>4,z>>4);chunk.addPluginChunkTicket(this);chunks.add(chunk);}
        for(int x=3920;x<=4080;x++)for(int z=3920;z<=4080;z++) {
            world.getBlockAt(x,279,z).setType(Material.SANDSTONE,false);world.getBlockAt(x,280,z).setType(Material.SAND,false);
            if(x%4==0 && z%4==0)world.setBiome(x,281,z,org.bukkit.block.Biome.DESERT);
        }
        player=nativeTestPlayer(world,point);player.setGameMode(GameMode.SPECTATOR);player.setInvulnerable(true);
        handle=player.getClass().getMethod("getHandle").invoke(player);
        var level=world.getClass().getMethod("getHandle").invoke(world);worldPlayers=(List<Object>)level.getClass().getMethod("players").invoke(level);worldPlayers.add(handle);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=list.getClass().getSuperclass().getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);online.put(player.getUniqueId(),handle);
        check(world.getPlayers().size()==1 && world.getPlayers().contains(player),"fixture is the only player in the actual world player list");
        check(mando.mobs().biome(point),"fixture stands in accepted survival desert");
        check(mando.mobs().settings().chance()==1 && mando.mobs().settings().interval()==10 && mando.mobs().settings().attempts()==32,"live override (10 seconds, chance 1, 32 attempts) unchanged for eligibility fix");
        seed();later(140,this::spectator);
    }
    private void spectator() throws Exception {
        check(nearby().isEmpty(),"actual periodic tick creates no encounters for Spectator");
        player.setGameMode(GameMode.CREATIVE);seed();later(mando.mobs().settings().interval()*20L+20,this::creative);
    }
    private void creative() throws Exception {
        var population=nearby();creativeCount=population.size();
        check(creativeCount>=2 && creativeCount<=6,"Creative-only presence generates a real bounded patrol through periodic scheduling");
        check(population.stream().anyMatch(e->mobs.type(e).equals("TuskenRaiderSniper")) && population.stream().anyMatch(e->mobs.type(e).equals("TuskenRaiderMelee")),"real encounter includes both Tusken variants");
        check(naturalCandidates==0,"custom encounters do not depend on native NATURAL candidate events");
        check(!world.getGameRuleValue(GameRule.DO_MOB_SPAWNING),"custom spawning works with vanilla mob spawning disabled");
        check(player.getGameMode()==GameMode.CREATIVE,"test never changes Creative targeting/immunity rules");
        player.setGameMode(GameMode.SURVIVAL);seed();later(mando.mobs().settings().interval()*20L+20,this::survival);
    }
    private void survival() {
        check(nearby().size()>creativeCount && nearby().size()<=mando.mobs().settings().localCap(),"Survival still generates real encounters and respects nearby cap");
        check(naturalCandidates==0,"neither mode required native candidates");
        getLogger().info("CREATIVE SPAWN PROBE PASSED "+checks+" assertions across real scheduled ticks");cleanup();Bukkit.shutdown();
    }
    private List<LivingEntity> nearby() {return world.getLivingEntities().stream().filter(e->e.getLocation().distanceSquared(point)<96*96 && e.getPersistentDataContainer().has(encounter,PersistentDataType.STRING)).toList();}
    private void seed() throws Exception {
        var field=SandySpawns.class.getDeclaredField("random");field.setAccessible(true);var random=(Random)field.get(mando.mobs());
        for(long seed=0;seed<100000;seed++) {var candidate=new Random(seed);if(candidate.nextDouble()<mando.mobs().settings().chance() && candidate.nextInt(EncounterPlan.Kind.values().length)==EncounterPlan.Kind.PATROL.ordinal()){random.setSeed(seed);return;}}
        throw new IllegalStateException("No deterministic patrol roll");
    }
    private interface Checked {void run() throws Exception;}
    private void later(long ticks,Checked step) {Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CREATIVE SPAWN PROBE FAILED",e);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean value,String label){if(!value)throw new IllegalStateException(label);checks++;getLogger().info("PASS "+label);}
    private void cleanup(){if(world!=null && point!=null && mobs!=null)nearby().forEach(mobs::remove);if(worldPlayers!=null)worldPlayers.remove(handle);if(online!=null && player!=null)online.remove(player.getUniqueId());if(previousRule!=null)world.setGameRule(GameRule.DO_MOB_SPAWNING,previousRule);chunks.forEach(c->c.removePluginChunkTicket(this));}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "CombatTest");
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
