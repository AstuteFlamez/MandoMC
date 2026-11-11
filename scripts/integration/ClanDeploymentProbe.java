package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.crates.*;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.territory.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Production mode is read-only. Actors and temporary terrain require the isolated full-suite marker. */
public final class ClanDeploymentProbe extends JavaPlugin {
    private MandoPlugin mando;
    private ClanService clans;
    private CrateService crates;
    private World world;
    private int checks;
    private final List<Player> actors=new ArrayList<>();
    private final Map<UUID,Object> handles=new HashMap<>();
    private Map<UUID,Object> online;
    private ClanState.Clan alpha,bravo;
    private Player a,b,mate;
    private KothEvent koth;
    private int originalPending;
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private interface Step{void run()throws Exception;}
    private void later(long ticks,Step action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    @Override public void onEnable(){later(100,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando!=null&&mando.isEnabled(),"MandoMC enabled");
        clans=Bukkit.getServicesManager().load(ClanService.class);crates=Bukkit.getServicesManager().load(CrateService.class);
        check(clans!=null&&clans.available(),"Native clans started");check(crates!=null,"Shared crates started");
        check(Bukkit.getServicesManager().getRegistrations(CrateService.class).size()==1,"Exactly one crate owner");
        check(crates.definitions().containsKey("koth"),"KOTH definition");check(crates.definitions().get("koth").virtual(),"Saved virtual KOTH keys");
        check(mando.items().create("koth_crate_key")!=null,"KOTH key catalog item");
        check(mando.getCommand("c").getExecutor() instanceof ClanCommands,"Clan command binding");
        check(Bukkit.getCommandMap().getCommand("c")==mando.getCommand("c"),"Bare clan label retained");
        check(clans.settings().members()==5&&clans.settings().chunks()==4,"Membership and claim limits");
        check(clans.settings().raidThreshold()==10&&clans.settings().raidMillis()==3600000,"Raid parameters");
        check(clans.settings().inactiveMillis()==604800000&&clans.settings().lootMillis()==86400000,"Abandonment parameters");
        check(clans.settings().resetMillis()==86400000&&!clans.settings().friendlyFire(),"Reset and friendly fire defaults");
        var territory=Bukkit.getServicesManager().load(TerritoryService.class);
        var tatooine=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));check(tatooine!=null,"Tatooine world retained");
        var atlas=territory.policy().map(tatooine.getKey().toString(),0,0);check(atlas!=null,"Tatooine full atlas");
        long safe=0,war=0,wild=0;
        for(int z=atlas.minZ();z<=atlas.maxZ();z++)for(int x=atlas.minX();x<=atlas.maxX();x++) {
            char zone=atlas.zone(x,z);if(zone=='S')safe++;else if(zone=='W')war++;else wild++;
            check(territory.policy().wildernessChunk(tatooine.getKey().toString(),x,z)==(zone=='.'),"Atlas/claim authority parity");
        }
        check(safe==79&&war==169&&wild==776,"Reviewed Tatooine zone counts");
        var mobs=new net.mandomc.integrations.regions.MobProtection();
        check(!mobs.encounter(new Location(tatooine,-125,70,-118)),"No encounters in town");
        check(!mobs.encounter(new Location(tatooine,166,121,127)),"No ambient encounters in palace");
        check(mobs.allowed(new Location(tatooine,166,121,127)),"Palace event spawning remains allowed");
        check(mobs.encounter(new Location(tatooine,-144,72,105)),"Wilderness encounter sites allowed");
        check(tatooine.getPVP()&&tatooine.getDifficulty()==Difficulty.NORMAL,"Tatooine combat/world policy applied");
        var settings=field(mando.mobs(),"settings");
        check(settings.getClass().getMethod("world").invoke(settings).equals("minecraft:tatooine_review"),"Live encounter world redirected");
        var ilum=Bukkit.getWorld(NamespacedKey.minecraft("ilum"));
        if(ilum!=null) {
            check(crates.definitions().containsKey("kyber"),"Kyber definition preserved");
            check(territory.policy().mechanisms(ilum.getKey().toString()),"Ilum mechanisms retained");
            check(!territory.combat(ilum.getSpawnLocation()),"Ilum safezone retained");
        }
        if(!new java.io.File(getDataFolder(),"full-suite").isFile()){success();return;}
        check(getServer().getPort()==25589,"Full suite restricted to disposable port");
        world=Bukkit.createWorld(new WorldCreator("clan_deployment_verify_v2").type(WorldType.FLAT)
                .generatorSettings("{\"biome\":\"minecraft:plains\",\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}]}").generateStructures(false));
        world.setPVP(true);world.setGameRule(GameRules.SPAWN_MOBS,false);
        world.addPluginChunkTicket(0,0,this);world.addPluginChunkTicket(2,0,this);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        var list=server.getClass().getMethod("getPlayerList").invoke(server);
        var f=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");f.setAccessible(true);online=(Map<UUID,Object>)f.get(list);
        for(int x:new int[]{2,3,4,34,35}){world.getBlockAt(x,69,2).setType(Material.STONE,false);world.getBlockAt(x,70,2).setType(Material.AIR,false);}
        a=actor(new Location(world,2.5,70,2.5));b=actor(new Location(world,34.5,70,2.5));mate=actor(new Location(world,3.5,70,2.5));
        command(a,"create","ProbeAlpha");command(b,"create","ProbeBravo");
        alpha=clans.member(a);bravo=clans.member(b);check(alpha!=null&&bravo!=null,"Create commands execute");
        clans.state().join(alpha,mate.getUniqueId(),5,System.currentTimeMillis());
        command(a,"claim");command(b,"claim");waitClaims(0);
    }
    private void command(Player player,String... args){check(mando.getCommand("c").getExecutor().onCommand(player,mando.getCommand("c"),"c",args),"Clan command dispatch");}
    private void waitClaims(int attempt)throws Exception {
        if(attempt>100)throw new IllegalStateException("Claim baseline timeout");
        if(!clans.land().ready(LandRestoration.chunk(a.getLocation()))||!clans.land().ready(LandRestoration.chunk(b.getLocation()))) {
            later(2,()->waitClaims(attempt+1));return;
        }
        command(a,"claim");command(b,"claim");
        check(clans.owner(a.getLocation())==alpha&&clans.owner(b.getLocation())==bravo,"Claim commands execute");
        var wall=world.getBlockAt(34,70,2);wall.setType(Material.STONE,false);
        var breaking=new BlockBreakEvent(wall,a);Bukkit.getPluginManager().callEvent(breaking);check(breaking.isCancelled(),"Rival edits denied with all deployed listeners");
        check(!clans.canContainer(mate,wall.getLocation()),"Non-raider container denial");
        var source=DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(a).withDirectEntity(a).build();
        var damage=new EntityDamageByEntityEvent(a,mate,EntityDamageEvent.DamageCause.ENTITY_ATTACK,source,5);
        Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"Friendly fire denied with all deployed listeners");
        originalPending=mando.explosions().pendingCount();
        var denied=blast(wall);check(denied.blockList().isEmpty(),"Locked raid explosion");
        for(int i=0;i<10;i++)clans.state().kill(alpha.id,bravo.id,System.currentTimeMillis());
        var breach=blast(wall);check(!breach.blockList().isEmpty(),"Attributed qualifying breach");
        check(clans.state().score(alpha.id,bravo.id)==0,"Raid consumes pair score");
        clans.state().kill(bravo.id,alpha.id,System.currentTimeMillis());check(clans.state().score(alpha.id,bravo.id)==0,"Raid freezes pair scoring");
        check(mando.explosions().pendingCount()==originalPending,"Claim breach excluded from temporary repair");
        check(clans.canContainer(a,wall.getLocation()),"Active raid container access");
        command(a,"map");command(a,"info");command(a,"warpoints","ProbeBravo");
        var config=YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"events/koth.yml"));
        config.set("settings.world",world.getKey().toString());config.set("settings.x",2.5);config.set("settings.y",70);config.set("settings.z",2.5);config.set("settings.capture-seconds",1);
        koth=new KothEvent(mando,config);koth.start();
        koth.sample(List.of(a,mate));check(alpha.id.toString().equals(koth.capture().holder()),"Clan KOTH holder");
        koth.sample(List.of(mate));check(koth.capture().ticks()==10,"Remaining clanmate retains capture");
        koth.sample(List.of(mate));
        check(alpha.id.equals(koth.winner())&&!koth.running(),"Clan victory and event cleanup");
        check(crates.keys(mate,crates.definitions().get("koth"))==1,"One shared-engine key to present winner");
        check(crates.keys(a,crates.definitions().get("koth"))==0,"Absent clanmate excluded");
        var result=clans.save();result.whenComplete((ignored,error)->later(1,()->{
            if(error!=null)throw new IllegalStateException(error);
            var saved=YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"data/clans.yml"));
            check(saved.getMapList("clans").size()>=2&&saved.getMapList("claims").size()>=2,"Native state committed");
            success();
        }));
    }
    private EntityExplodeEvent blast(org.bukkit.block.Block block){
        var e=new EntityExplodeEvent(a,block.getLocation().add(.5,.5,.5),new ArrayList<>(List.of(block)),0,ExplosionResult.DESTROY);
        Bukkit.getPluginManager().callEvent(e);return e;
    }
    private void success()throws Exception {
        var result="CLAN DEPLOYMENT PASSED: "+checks+" native assertions\n";
        Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result);
        getLogger().info(result);cleanup();
        if(getServer().getPort()==25589)later(40,()->{
            var checker=Bukkit.getPluginManager().getPlugin("TatooineAccessCheck");
            if(checker!=null&&!new java.io.File(checker.getDataFolder(),"PASSED.txt").isFile())
                throw new IllegalStateException("Tatooine checker did not pass");
            Bukkit.shutdown();
        });
    }
    private void fail(Throwable error){
        getLogger().log(java.util.logging.Level.SEVERE,"CLAN DEPLOYMENT FAILED",error);
        try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}
        cleanup();if(getServer().getPort()==25589)Bukkit.shutdown();
    }
    @SuppressWarnings("unchecked") private Player actor(Location at)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),
                playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level=at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"ClanCheck"+actors.size());
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flow=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionClass.getConstructor(flow).newInstance(flow.getField("SERVERBOUND").get(null));
        Class<?> cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Class<?> listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,at.getX(),at.getY(),at.getZ());
        Player player=(Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
        actors.add(player);handles.put(player.getUniqueId(),handle);online.put(player.getUniqueId(),handle);
        level.getClass().getMethod("addNewPlayer",playerClass).invoke(level,handle);
        player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);return player;
    }
    @SuppressWarnings("unchecked") private void cleanup(){
        Bukkit.getScheduler().cancelTasks(this);if(koth!=null)koth.stop(false);
        for(var actor:List.copyOf(actors))try {
            actor.closeInventory();if(online!=null)online.remove(actor.getUniqueId());Object handle=handles.get(actor.getUniqueId());
            for(var w:Bukkit.getWorlds()){Object level=w.getClass().getMethod("getHandle").invoke(w);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(handle);}
            handle.getClass().getMethod("discard").invoke(handle);
        }catch(Exception error){getLogger().warning("Fixture cleanup: "+error);}
        actors.clear();handles.clear();
    }
    @Override public void onDisable(){cleanup();}
}
