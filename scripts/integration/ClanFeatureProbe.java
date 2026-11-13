package net.mandomc.verification;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.crates.*;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.explosions.*;
import net.mandomc.gameplay.items.*;
import net.mandomc.gameplay.territory.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs against disposable Paper terrain only; never install on the shared development server. */
public final class ClanFeatureProbe extends JavaPlugin {
    private ClanService clans;
    private TerritoryService territory;
    private TemporaryExplosions explosions;
    private CrateService crates;
    private World world;
    private int checks;
    private Fixture a,b,mate,outsider;
    private ClanState.Clan alpha,bravo;
    private void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);checks++;}
    private void field(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private YamlConfiguration resource(String name){return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getResource(name),java.nio.charset.StandardCharsets.UTF_8));}
    private static Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0f;if(type==double.class)return 0d;return null;}
    private final class Fixture {
        final UUID id=UUID.randomUUID();final Player player;final PersistentDataContainer pdc;
        Location location;GameMode mode=GameMode.SURVIVAL;int saves;
        Fixture(String name,Location l){location=l;var stand=world.spawn(new Location(world,100,150,100),ArmorStand.class);pdc=stand.getPersistentDataContainer();
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,args)->{
                return switch(m.getName()){
                    case "getUniqueId"->id;case "getName"->name;case "getWorld"->world;case "getLocation"->location.clone();
                    case "getGameMode"->mode;case "isOnline","hasPermission","isPermissionSet"->true;
                    case "getMetadata"->List.of();case "getPersistentDataContainer"->pdc;
                    case "saveData"->{saves++;yield null;}case "hashCode"->id.hashCode();case "equals"->p==args[0];
                    case "toString"->name;default->zero(m.getReturnType());
                };
            });
        }
    }
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->guard(this::begin),20);}
    private interface Step{void run()throws Exception;}
    private void guard(Step step){try{step.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CLAN FEATURE FAILED",e);Bukkit.shutdown();}}
    private void begin()throws Exception{
        world=Bukkit.getWorlds().getFirst();world.setPVP(true);
        world.addPluginChunkTicket(0,0,this);world.addPluginChunkTicket(2,0,this);world.addPluginChunkTicket(4,0,this);
        territory=new TerritoryService(this,resource("territory/maps.yml"),resource("language/territory.yml"));
        var config=resource("clans/settings.yml");config.set("restore-blocks-per-step",2);
        clans=new ClanService(this,territory,config,resource("language/clans.yml"));
        explosions=new TemporaryExplosions(this,new BlastSettings(3,30,60,1000),territory::edits);
        var catalog=resource("items/catalog.yml");catalog.set("external-items",null);
        Files.createDirectories(getDataFolder().toPath().resolve("items/definitions"));catalog.save(new java.io.File(getDataFolder(),"items/catalog.yml"));
        for(String name:List.of("legacy","koth"))saveResource("items/definitions/"+name+".yml",true);
        var items=new ItemCatalog(getDataFolder().toPath(),(kind,id)->null);items.reload();
        crates=new CrateService(this,items,resource("crates/crates.yml"),p->false,p->false);
        Bukkit.getServicesManager().register(CrateService.class,crates,this,org.bukkit.plugin.ServicePriority.Normal);
        a=new Fixture("AlphaLeader",new Location(world,2.5,70,2.5));b=new Fixture("BravoLeader",new Location(world,34.5,70,2.5));
        mate=new Fixture("AlphaMate",a.location);outsider=new Fixture("Outsider",a.location);
        alpha=clans.state().create("Alpha",a.id,System.currentTimeMillis());bravo=clans.state().create("Bravo",b.id,System.currentTimeMillis());
        clans.state().join(alpha,mate.id,5,System.currentTimeMillis());
        for(int x:List.of(2,3,4,34,35,36)){world.getBlockAt(x,69,2).setType(Material.STONE,false);world.getBlockAt(x,70,2).setType(Material.AIR,false);}
        check(!clans.land().prepare(world,0,0),"baseline initially gated");
        check(!clans.land().prepare(world,2,0),"second baseline initially gated");
        var first=world.getBlockAt(66,70,2);first.setType(Material.DIRT,false);var replaced=first.getState();
        first.setType(Material.GOLD_BLOCK,false);
        var placement=new BlockPlaceEvent(first,replaced,first.getRelative(BlockFace.DOWN),new ItemStack(Material.GOLD_BLOCK),
                a.player,true,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placement);
        check(placement.isCancelled(),"first placement waits for pristine snapshot");replaced.update(true,false);
        waitReady(0);
    }
    private void waitReady(int count){
        if(count>100){guard(()->{throw new IllegalStateException("baseline timeout");});return;}
        if(!clans.land().ready(LandRestoration.chunk(a.location))||!clans.land().ready(LandRestoration.chunk(b.location))||!clans.land().ready(new ClanState.Chunk(world.getKey().toString(),4,0))){Bukkit.getScheduler().runTaskLater(this,()->waitReady(count+1),2);return;}
        guard(this::actions);
    }
    private EntityExplodeEvent blast(Player source,Block block){
        var event=new EntityExplodeEvent(source,block.getLocation().add(.5,.5,.5),new ArrayList<>(List.of(block)),0,ExplosionResult.DESTROY);
        Bukkit.getPluginManager().callEvent(event);return event;
    }
    private void actions()throws Exception{
        clans.state().claim(alpha,LandRestoration.chunk(a.location),4,clans::wilderness);
        clans.state().claim(bravo,LandRestoration.chunk(b.location),4,clans::wilderness);
        var wall=world.getBlockAt(34,70,2);wall.setType(Material.STONE,false);
        var breaking=new BlockBreakEvent(wall,a.player);Bukkit.getPluginManager().callEvent(breaking);
        check(breaking.isCancelled(),"rival hand breaking denied");
        breaking=new BlockBreakEvent(wall,b.player);Bukkit.getPluginManager().callEvent(breaking);check(!breaking.isCancelled(),"owner edits allowed");
        check(blast(a.player,wall).blockList().isEmpty(),"subthreshold explosion denied");
        for(int i=0;i<10;i++)clans.state().kill(alpha.id,bravo.id,System.currentTimeMillis());
        check(clans.canContainer(a.player,wall.getLocation()),"eligible rival chest access");
        check(!clans.canContainer(outsider.player,wall.getLocation()),"unrelated chest access denied");
        var event=blast(a.player,wall);check(event.blockList().contains(wall),"qualifying explosion breaches");
        check(clans.state().score(alpha.id,bravo.id)==0,"breach resets score");
        var rivalry=clans.state().rivalries.values().iterator().next();long deadline=rivalry.activeUntil();
        check(deadline>System.currentTimeMillis()+3590000,"one hour raid");
        blast(a.player,wall);check(clans.state().rivalries.values().iterator().next().activeUntil()==deadline,"blast cannot extend raid");
        check(explosions.pendingCount()==0,"raid holes do not temporarily regenerate");
        check(blast(outsider.player,wall).blockList().isEmpty(),"unaffiliated explosion denied");
        DamageSource source=(DamageSource)Proxy.newProxyInstance(DamageSource.class.getClassLoader(),new Class[]{DamageSource.class},
                (p,m,args)->m.getName().equals("getCausingEntity")?a.player:zero(m.getReturnType()));
        var damage=new EntityDamageByEntityEvent(a.player,mate.player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,source,5);
        Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"friendly fire denied");
        var chest=world.getBlockAt(35,70,2);chest.setType(Material.CHEST,false);
        // Container permission is independently checked because a synthetic unopened inventory has no viewer.
        check(!clans.canContainer(outsider.player,chest.getLocation()),"foreign container denied");
        var left=world.getBlockAt(31,70,5);var right=world.getBlockAt(32,70,5);
        left.setBlockData(Bukkit.createBlockData("minecraft:chest[facing=north,type=left,waterlogged=false]"),false);
        right.setBlockData(Bukkit.createBlockData("minecraft:chest[facing=north,type=right,waterlogged=false]"),false);
        check(((Chest)left.getState()).getInventory().getHolder() instanceof DoubleChest,"cross-border double chest fixture");
        check(!clans.canContainer(outsider.player,left.getLocation()),"wilderness half cannot open claimed half");
        check(clans.splitContainer(left.getLocation()),"split ownership detected on both chest halves");
        var cart=world.spawn(new Location(world,35,72,4),org.bukkit.entity.minecart.HopperMinecart.class);
        var transfer=new InventoryMoveItemEvent(((Container)chest.getState()).getInventory(),new ItemStack(Material.DIAMOND),cart.getInventory(),true);
        Bukkit.getPluginManager().callEvent(transfer);check(transfer.isCancelled(),"mobile hopper cannot siphon inside claim");cart.remove();
        bravo.lastActive=System.currentTimeMillis()-clans.settings().inactiveMillis()-1;
        var tick=ClanService.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(clans);
        check(bravo.abandonedAt!=0,"seven days triggers public loot");
        check(clans.canContainer(outsider.player,chest.getLocation()),"abandoned loot public");
        check(!clans.canEdit(outsider.player,wall.getLocation()),"public looting is not hand breaking");
        check(!blast(outsider.player,wall).blockList().isEmpty(),"public loot allows explosive access");
        long abandoned=bravo.abandonedAt;
        clans.join(new org.bukkit.event.player.PlayerJoinEvent(b.player,net.kyori.adventure.text.Component.empty()));
        check(bravo.abandonedAt==abandoned,"returning member cannot save abandoned clan");
        koth();
        // Full land reset restores mined terrain AND removes placed blocks, in bounded visible steps.
        var floor=world.getBlockAt(2,69,2);floor.setType(Material.AIR,false);
        for(int x=2;x<=4;x++)world.getBlockAt(x,70,2).setType(Material.GOLD_BLOCK,false);
        clans.state().claims.remove(LandRestoration.chunk(a.location));clans.land().release(LandRestoration.chunk(a.location),true);
        check(!clans.canEdit(outsider.player,a.location),"abandoned cleanup cannot be delayed by fresh edits");
        var first=world.getBlockAt(66,70,2);var replaced=first.getState();first.setType(Material.GOLD_BLOCK,false);
        var placement=new BlockPlaceEvent(first,replaced,first.getRelative(BlockFace.DOWN),new ItemStack(Material.GOLD_BLOCK),a.player,true,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placement);check(!placement.isCancelled(),"prepared placement permitted");
        var chunk=LandRestoration.chunk(first);var lands=(Map<?,?>)field(clans.land(),"lands");
        long originalDue=(long)field(lands.get(chunk),"due");
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
            clans.land().touch(chunk);long latestDue=(long)field(lands.get(chunk),"due");
            check(latestDue>originalDue,"latest edit extends wilderness deadline");
            check(latestDue-System.currentTimeMillis()>86399000,"24-hour elapsed deadline");
            clans.land().release(chunk,true);waitRestored(0);
        }),10);
    }
    @SuppressWarnings("unchecked") private void koth()throws Exception{
        var config=resource("events/koth.yml");config.set("settings.world",world.getKey().toString());
        config.set("settings.x",2.5);config.set("settings.y",70);config.set("settings.z",2.5);config.set("settings.capture-seconds",1);
        var koth=new KothEvent(this,config);koth.start();
        var viewers=new ArrayList<Player>();
        var bar=(org.bukkit.boss.BossBar)Proxy.newProxyInstance(org.bukkit.boss.BossBar.class.getClassLoader(),
                new Class[]{org.bukkit.boss.BossBar.class},(proxy,method,args)->switch(method.getName()) {
                    case "getPlayers"->List.copyOf(viewers);case "addPlayer"->{viewers.add((Player)args[0]);yield null;}
                    case "removePlayer"->{viewers.remove(args[0]);yield null;}case "removeAll"->{viewers.clear();yield null;}
                    default->zero(method.getReturnType());
                });
        field(koth,"bar",bar);
        koth.sample(List.of(a.player,mate.player,outsider.player));check(koth.capture().holder()==null,"enemy contests initial capture");
        koth.sample(List.of(a.player,mate.player));check(alpha.id.toString().equals(koth.capture().holder()),"clanmates share one holder");
        koth.sample(List.of(mate.player,outsider.player));check(koth.capture().ticks()==10,"remaining clanmate keeps timer despite enemy");
        koth.sample(List.of(mate.player,outsider.player));
        check(alpha.id.equals(koth.winner()),"KOTH victory belongs to clan ID");
        var definition=crates.definitions().get("koth");
        int keys=crates.keys(a.player,definition)+crates.keys(mate.player,definition);
        check(keys==1,"one key to one winning member at hill");check(crates.keys(a.player,definition)==0,"absent clanmate gets no reward");check(crates.keys(outsider.player,definition)==0,"no key to outsiders");
        check(a.saves+mate.saves==1,"reward recipient saved once");check(!koth.running(),"KOTH stopped after one payout");
        var view=new ClanMapCommand(this);var command=getCommand("c");
        check(view.onCommand(a.player,command,"c",new String[]{"map"}),"claim map renders in unmapped world");
    }
    private void waitRestored(int count){
        guard(()->{
            if(count>80)throw new IllegalStateException("restoration timeout");
            if(world.getBlockAt(2,69,2).getType()!=Material.STONE||world.getBlockAt(4,70,2).getType()!=Material.AIR||world.getBlockAt(66,70,2).getType()!=Material.DIRT){
                Bukkit.getScheduler().runTaskLater(this,()->waitRestored(count+1),5);return;
            }
            check(world.getBlockAt(2,70,2).getType()==Material.AIR,"placed blocks removed");
            check(world.getBlockAt(66,70,2).getType()==Material.DIRT,"first placement restores replaced terrain, not placed block");
            check(world.getBlockAt(2,69,2).getType()==Material.STONE,"mined terrain restored");
            // Persist, close and reopen all native lifecycle state against real disk.
            explosions.close();explosions=null;crates.close();crates=null;clans.close();
            org.bukkit.event.HandlerList.unregisterAll(this);
            clans=new ClanService(this,territory,resource("clans/settings.yml"),resource("language/clans.yml"));
            check(clans.state().member(a.id)!=null,"membership survives restart");
            check(clans.state().owner(LandRestoration.chunk(b.location)).id.equals(bravo.id),"claims survive restart");
            check(clans.state().clans.get(bravo.id).abandonedAt!=0,"public loot window survives restart");
            check(clans.land().hasBaseline(LandRestoration.chunk(a.location)),"original terrain survives restart");
            clans.state().clans.get(bravo.id).abandonedAt=System.currentTimeMillis()-clans.settings().lootMillis()-1;
            var tick=ClanService.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(clans);
            check(clans.state().owner(LandRestoration.chunk(b.location))==null,"24-hour public loot expiry releases land");
            check(clans.state().member(b.id)==null,"expired clan disbands");
            getLogger().info("CLAN FEATURE PASS: "+checks+" native Paper assertions");Bukkit.shutdown();
        });
    }
    @Override public void onDisable(){if(explosions!=null)explosions.close();if(crates!=null)crates.close();if(clans!=null)clans.close();if(territory!=null)territory.close();}
}
