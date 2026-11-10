package net.mandomc.verification;

import java.util.*;
import java.lang.reflect.Method;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.mobs.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable real scheduler/multiplayer checks followed by equal-seed accelerated native encounter trials. */
public final class PatrolProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private MandoPlugin mando;
    private World world;
    private MythicMobAccess mobs;
    private PatrolState state;
    private List<Object> worldPlayers;
    private Map<UUID,Object> online;
    private final List<Player> actors=new ArrayList<>();
    private final Map<UUID,Object> handles=new HashMap<>();
    private final List<Chunk> chunks=new ArrayList<>();
    private final NamespacedKey encounter=new NamespacedKey("mandomc","sandy_encounter_v1");
    private final NamespacedKey patrol=new NamespacedKey("mandomc","sandy_patrol_v1");
    private Location a,b;
    private Boolean previousRule;
    private String first,second;
    private int checks,round,trial,oldPatrol,newPatrol,oldWildlife,newWildlife;
    private long[] trialSeeds;
    private Random random;
    private Method tick;
    private java.lang.reflect.Field settingsField;
    private SandySettings originalSettings;
    private static final int TRIALS=120;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(80,this::begin);}
    @SuppressWarnings("unchecked") private void begin() throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");mobs=new MythicMobAccess();world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        a=new Location(world,4000.5,281,4000.5);b=new Location(world,4300.5,281,4000.5);
        previousRule=world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        floor(a);floor(b);
        Object level=world.getClass().getMethod("getHandle").invoke(world);worldPlayers=(List<Object>)level.getClass().getMethod("players").invoke(level);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var lookup=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");lookup.setAccessible(true);online=(Map<UUID,Object>)lookup.get(list);
        clearFixture();addPlayer(a);addPlayer(b);
        var stateField=SandySpawns.class.getDeclaredField("patrols");stateField.setAccessible(true);state=(PatrolState)stateField.get(mando.mobs());
        var randomField=SandySpawns.class.getDeclaredField("random");randomField.setAccessible(true);random=(Random)randomField.get(mando.mobs());random.setSeed(1);
        originalSettings=mando.mobs().settings();check(originalSettings.patrolChance()==.75 && originalSettings.wildlifeChance()==.75,"live overrides retain 10-second interval with 3x patrol and unchanged wildlife opportunities");
        later(140,this::initial);
    }
    private void floor(Location point){int cx=point.getBlockX(),cz=point.getBlockZ();for(int x=cx-80;x<=cx+80;x+=16)for(int z=cz-80;z<=cz+80;z+=16){var c=world.getChunkAt(x>>4,z>>4);c.addPluginChunkTicket(this);chunks.add(c);}for(int x=cx-80;x<=cx+80;x++)for(int z=cz-80;z<=cz+80;z++){world.getBlockAt(x,279,z).setType(Material.SANDSTONE,false);world.getBlockAt(x,280,z).setType(Material.SAND,false);if(x%4==0&&z%4==0)world.setBiome(x,281,z,org.bukkit.block.Biome.DESERT);}}
    private Player addPlayer(Location point) throws Exception {var p=nativeTestPlayer(world,point);p.setGameMode(GameMode.CREATIVE);p.setInvulnerable(true);var handle=p.getClass().getMethod("getHandle").invoke(p);actors.add(p);handles.put(p.getUniqueId(),handle);worldPlayers.add(handle);online.put(p.getUniqueId(),handle);return p;}
    private void initial() throws Exception {
        freeze();first=state.playerGroup(actors.get(0).getUniqueId());second=state.playerGroup(actors.get(1).getUniqueId());
        if(first==null||second==null){
            for(var p:actors)getLogger().info("DIAG "+p.getUniqueId()+" mode="+p.getGameMode()+" dead="+p.isDead()+" biome="+mando.mobs().biome(p.getLocation())+" nearby="+near(p.getLocation()).size()+" group="+state.playerGroup(p.getUniqueId()));
            getLogger().info("DIAG tags="+fixture().size()+" managed="+world.getLivingEntities().stream().filter(mobs::managed).count()+" players="+world.getPlayers().size()+" floor="+world.getHighestBlockYAt(4040,4000));
            if(++round>4)throw new IllegalStateException("No real independent scheduled patrols after bounded wait");random.setSeed(100L+round);getLogger().info("Waiting for next genuine scheduled opportunity; groups="+state.groups().size());later(originalSettings.interval()*20L+20,this::initial);return;}
        check(!first.equals(second),"independent Creative players receive independent real scheduled patrols");
        for(String id:List.of(first,second)){check(state.members(id).size()>=2&&state.members(id).size()<=6,"scheduled patrol preserves whole group budget");for(UUID member:state.members(id))check(id.equals(state.memberGroup(member)),"every patrol member shares group authority");}
        addPlayer(a.clone().add(1,0,0));random.setSeed(341);later(originalSettings.interval()*40L+20,this::blocked);
    }
    private void blocked() throws Exception {
        freeze();check(first.equals(state.playerGroup(actors.get(0).getUniqueId()))&&second.equals(state.playerGroup(actors.get(1).getUniqueId())),"ongoing patrol blocks additional patrols across multiple real scheduler ticks");
        check(first.equals(state.playerGroup(actors.get(2).getUniqueId())),"nearby third player shares existing patrol instead of getting an overlapping group");
        check(state.groups().size()==2,"world permits independent patrols without overlapping-player pileup");
        for(Location point:List.of(a,b))check(near(point).size()<=originalSettings.localCap(),"actual nearby population respects cap");
        var firstMob=(Mob)state.hostiles(first).stream().map(Bukkit::getEntity).filter(e -> e instanceof LivingEntity living && mobs.type(living).startsWith("TuskenRaider")).findFirst().orElseThrow();var secondMob=(Mob)state.hostiles(second).stream().map(Bukkit::getEntity).filter(e -> e instanceof LivingEntity living && mobs.type(living).startsWith("TuskenRaider")).findFirst().orElseThrow();
        actors.forEach(p->{p.setGameMode(GameMode.SURVIVAL);p.setInvulnerable(false);});
        var firstPrevious = firstMob.getTarget();
        boolean firstAccepted=target(firstMob,actors.get(1));
        getLogger().info("TARGET DIAG FIRST type="+mobs.type(firstMob)+" group="+state.memberGroup(firstMob.getUniqueId())+" playerGroup="+state.playerGroup(actors.get(1).getUniqueId())+" accepted="+firstAccepted+" old="+firstPrevious+" new="+firstMob.getTarget());
        check(!actors.get(1).equals(firstMob.getTarget()),"first patrol cannot switch onto player assigned second patrol");
        var secondPrevious = secondMob.getTarget();
        boolean secondAccepted=target(secondMob,actors.get(0));
        getLogger().info("TARGET DIAG type="+mobs.type(secondMob)+" group="+state.memberGroup(secondMob.getUniqueId())+" playerGroup="+state.playerGroup(actors.get(0).getUniqueId())+" accepted="+secondAccepted+" old="+secondPrevious+" new="+secondMob.getTarget());
        check(!actors.get(0).equals(secondMob.getTarget()),"second patrol cannot pile onto first player's encounter");
        boolean allowed=target(firstMob,actors.get(2));
        getLogger().info("ALLOWED DIAG result="+allowed+" target="+firstMob.getTarget()+" player="+actors.get(2)+" mode="+actors.get(2).getGameMode()+" dead="+actors.get(2).isDead()+" playerGroup="+state.playerGroup(actors.get(2).getUniqueId()));
        var sameGroupEvent=new org.bukkit.event.entity.EntityTargetLivingEntityEvent(firstMob,actors.get(2),org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        mando.mobs().patrolTarget(sameGroupEvent);
        check(!sameGroupEvent.isCancelled(),"ownership guard permits another player assigned the same patrol");
        check(firstMob.getTarget()==null || first.equals(state.playerGroup(firstMob.getTarget().getUniqueId())),"existing Mythic targeting remains inside the assigned patrol");firstMob.setTarget(null);
        actors.forEach(p->{p.setGameMode(GameMode.CREATIVE);p.setInvulnerable(true);});
        for(UUID hostile:state.hostiles(first)){var entity=Bukkit.getEntity(hostile);if(entity!=null)mobs.remove(entity);}
        check(state.playerGroup(actors.get(0).getUniqueId())==null&&state.playerGroup(actors.get(2).getUniqueId())==null,"last hostile removal releases all participants even if passive companions survive");
        check(second.equals(state.playerGroup(actors.get(1).getUniqueId()))&&state.hostiles(second).stream().allMatch(id->Bukkit.getEntity(id)!=null),"defeat never despawns or releases another player's patrol");
        mando.mobs().patrolQuit(new org.bukkit.event.player.PlayerQuitEvent(actors.get(1),net.kyori.adventure.text.Component.empty()));check(state.playerGroup(actors.get(1).getUniqueId())==null&&state.groups().contains(second),"logout releases player reservation without deleting group");
        beginTrials();
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void targetDiagnostic(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if(event.getReason()==org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER)
            getLogger().info("EVENT DIAG mob="+event.getEntity().getUniqueId()+" target="+event.getTarget()+" cancelled="+event.isCancelled());
    }
    private boolean target(Mob mob,Player player)throws Exception {
        var handle=mob.getClass().getMethod("getHandle").invoke(mob);var victim=player.getClass().getMethod("getHandle").invoke(player);
        return (boolean)handle.getClass().getMethod("setTarget",Class.forName("net.minecraft.world.entity.LivingEntity"),org.bukkit.event.entity.EntityTargetEvent.TargetReason.class)
                .invoke(handle,victim,org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
    }
    private void beginTrials() throws Exception {
        var taskField=SandySpawns.class.getDeclaredField("task");taskField.setAccessible(true);((org.bukkit.scheduler.BukkitTask)taskField.get(mando.mobs())).cancel();
        for(int i=1;i<actors.size();i++){var p=actors.get(i);worldPlayers.remove(handles.get(p.getUniqueId()));online.remove(p.getUniqueId());}
        clearFixture();
        settingsField=SandySpawns.class.getDeclaredField("settings");settingsField.setAccessible(true);
        tick=SandySpawns.class.getDeclaredMethod("tick");tick.setAccessible(true);
        trialSeeds=new Random(934713L).longs(TRIALS).toArray();setMultiplier(1);later(1,this::trial);
    }
    private void setMultiplier(double multiplier)throws Exception{var c=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"mobs/spawning.yml"));c.set("spawn.patrol-frequency-multiplier",multiplier);settingsField.set(mando.mobs(),SandySettings.read(c));check(mando.mobs().settings().patrolFrequencyMultiplier()==multiplier,"isolated comparative multiplier="+multiplier);}
    private void trial() throws Exception {
        clearFixture();boolean tuned=trial>=TRIALS;random.setSeed(trialSeeds[trial%TRIALS]);tick.invoke(mando.mobs());
        var healthy=SandySpawns.class.getDeclaredField("healthy");healthy.setAccessible(true);if(!healthy.getBoolean(mando.mobs()))throw new IllegalStateException("Spawner failed during rate trial");
        var population=near(a);var patrolGroups=new HashSet<String>();var wildlifeGroups=new HashSet<String>();
        for(var entity:population){String id=entity.getPersistentDataContainer().get(encounter,PersistentDataType.STRING);if(entity.getPersistentDataContainer().has(patrol))patrolGroups.add(id);else wildlifeGroups.add(id);}
        if(patrolGroups.size()>1 || population.size()>originalSettings.localCap())throw new IllegalStateException("Rate trial bypassed patrol/population cap");
        if(tuned){newPatrol+=patrolGroups.size();newWildlife+=wildlifeGroups.size();}else{oldPatrol+=patrolGroups.size();oldWildlife+=wildlifeGroups.size();}
        if(++trial==TRIALS)setMultiplier(3);
        if(trial<TRIALS*2){later(1,this::trial);return;}
        check(newPatrol>=oldPatrol*2 && newPatrol>oldPatrol+20,"actual encounter trials show substantial patrol increase under identical free-player conditions");
        check(Math.abs(newWildlife-oldWildlife)<=3,"actual wildlife encounter rate retained in paired trials");
        getLogger().info("PAIRED NATIVE SPAWN TRIALS: "+TRIALS+" equal seeds each; patrols "+oldPatrol+" -> "+newPatrol+", wildlife "+oldWildlife+" -> "+newWildlife);
        getLogger().info("PATROL PROBE PASSED "+checks+" assertions; real periodic multiplayer plus accelerated native encounter trials");cleanup();Bukkit.shutdown();
    }
    private List<LivingEntity> near(Location point){return world.getLivingEntities().stream().filter(e->Math.pow(e.getX()-point.getX(),2)+Math.pow(e.getZ()-point.getZ(),2)<96*96&&e.getPersistentDataContainer().has(encounter,PersistentDataType.STRING)).toList();}
    private void freeze(){for(var e:fixture()){e.setAI(false);e.setInvulnerable(true);}}
    private List<LivingEntity> fixture(){var result=new HashSet<LivingEntity>();if(a!=null)result.addAll(near(a));if(b!=null)result.addAll(near(b));return List.copyOf(result);}
    private void clearFixture(){for(var e:fixture()){mobs.remove(e);if(e.isValid())e.remove();}}
    private interface Checked{void run()throws Exception;}
    private void later(long ticks,Checked action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"PATROL PROBE FAILED",e);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean value,String label){if(!value)throw new IllegalStateException(label);checks++;getLogger().info("PASS "+label);}
    private void cleanup(){if(world!=null&&mobs!=null)clearFixture();if(worldPlayers!=null)handles.values().forEach(worldPlayers::remove);if(online!=null)handles.keySet().forEach(online::remove);if(previousRule!=null)world.setGameRule(GameRule.DO_MOB_SPAWNING,previousRule);if(settingsField!=null&&originalSettings!=null)try{settingsField.set(mando.mobs(),originalSettings);}catch(IllegalAccessException e){throw new IllegalStateException(e);}chunks.forEach(c->c.removePluginChunkTicket(this));}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "Patrol" + actors.size());
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
