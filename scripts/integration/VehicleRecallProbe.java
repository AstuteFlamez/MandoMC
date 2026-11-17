package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.vehicles.VehicleService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Actual Hoth spawn cancellation, ownership/token custody and JVM recovery; disposable runtime only. */
public final class VehicleRecallProbe extends JavaPlugin {
    private MandoPlugin mando;private NativeProbePlayers actors;private Player owner,other;private Location at;
    private int checks;private UUID id;private boolean cancelBase;private final Listener canceller=new Listener(){};
    private interface Work{void run()throws Exception;}
    @Override public void onEnable(){later(160,this::start);}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    private void check(boolean okay,String why){if(!okay)throw new IllegalStateException(why);checks++;}
    @SuppressWarnings("unchecked") private Map<UUID,Object> records()throws Exception{var f=VehicleService.class.getDeclaredField("records");f.setAccessible(true);return (Map<UUID,Object>)f.get(mando.vehicles());}
    private void set(Object target,String key,Object value)throws Exception{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
    private double health(ItemStack item)throws Exception{UUID key=UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(VehicleService.ID,PersistentDataType.STRING));var r=records().get(key);var f=r.getClass().getDeclaredField("health");f.setAccessible(true);return f.getDouble(r);}
    private int packedCount(Player p){return (int)Arrays.stream(p.getInventory().getContents()).filter(Objects::nonNull).filter(s->s.getItemMeta()!=null && s.getItemMeta().getPersistentDataContainer().has(VehicleService.ID,PersistentDataType.STRING)).count();}
    private ItemStack packed(Player p){return Arrays.stream(p.getInventory().getContents()).filter(Objects::nonNull).filter(s->s.getItemMeta()!=null && s.getItemMeta().getPersistentDataContainer().has(VehicleService.ID,PersistentDataType.STRING)).findFirst().orElseThrow();}
    private void command(Player p,String name,String...args){check(Bukkit.getPluginCommand(name).execute(p,name,args),"native command executes "+name);}
    private void start()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null && mando.isEnabled(),"gameplay enabled");
        var world=mando.worlds().resolve("Hoth");
        for(int x=500;x<1500 && at==null;x+=20){var point=new Location(world,x+.5,201,800.5);if(mando.territories().edits(point) && mando.territories().combat(point))at=point;}
        check(at!=null,"Hoth wilderness found");
        at.getChunk().load();for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){at.getBlock().getRelative(x,-1,z).setType(Material.STONE,false);for(int y=0;y<12;y++)at.getBlock().getRelative(x,y,z).setType(Material.AIR,false);}
        actors=new NativeProbePlayers("Recall");var phase=getDataFolder().toPath().resolve("restart.properties");
        if(Files.exists(phase)){
            var state=new Properties();try(var in=Files.newInputStream(phase)){state.load(in);}checks=Integer.parseInt(state.getProperty("checks"));
            owner=actors.create(at.clone().add(0,0,5),UUID.fromString(state.getProperty("owner")));owner.setGameMode(GameMode.SURVIVAL);mando.ranks().apply(owner);owner.getInventory().clear();id=UUID.fromString(state.getProperty("id"));
            check(mando.vehicles().entity(id)!=null && mando.vehicles().entity(id).isValid(),"deployed Hoth identity restored after JVM restart");
            command(owner,"vehicle","recall");check(mando.vehicles().entity(id)==null && packedCount(owner)==1,"persisted identity recalled exactly once");
            check(health(packed(owner))==31,"persisted damage retained");command(owner,"vrecall");check(packedCount(owner)==1,"repeat alias cannot duplicate");Files.delete(phase);finish(null);return;
        }
        owner=actors.create(at.clone().add(0,0,5));other=actors.create(at.clone().add(0,0,6));for(var p:List.of(owner,other)){p.setGameMode(GameMode.SURVIVAL);mando.ranks().apply(p);p.getInventory().clear();}
        check(!world.spawn(at.clone().add(6,0,0),Pig.class).isValid(),"ordinary Hoth pig remains blocked");
        Bukkit.getPluginManager().registerEvent(CreatureSpawnEvent.class,canceller,EventPriority.MONITOR,(l,e)->{var spawn=(CreatureSpawnEvent)e;if(cancelBase && spawn.getEntity().getPersistentDataContainer().has(VehicleService.ID,PersistentDataType.STRING))spawn.setCancelled(true);},this);
        cancelBase=true;var failed=mando.items().create("speederbike");int before=records().size();
        check(mando.vehicles().deploy(owner,failed,at)==null,"externally cancelled base rejected");check(failed.getAmount()==1,"cancelled spawn keeps original item");check(records().size()==before,"failed placement leaves no phantom ownership");cancelBase=false;
        for(String type:List.of("speederbike","xwing","tiefighter","uwing")){
            var item=mando.items().create(type);UUID deployed=mando.vehicles().deploy(owner,item,at);
            check(deployed!=null && mando.vehicles().entity(deployed).isValid(),"native Hoth base/model valid: "+type);
            check(item.isEmpty(),"successful placement consumes exactly one "+type);
            mando.vehicles().entity(deployed).remove();check(mando.vehicles().recall(owner),"missing base recovered "+type);check(packedCount(owner)==1,"one item returned "+type);owner.getInventory().clear();
        }
        var bike=mando.items().create("speederbike");id=mando.vehicles().deploy(owner,bike,at);check(id!=null,"bike placed for recall boundaries");
        later(20,this::boundaries);
    }
    private void boundaries()throws Exception{
        var entity=mando.vehicles().entity(id);entity.setHealth(31);entity.setVelocity(new Vector());
        check(!mando.vehicles().recall(other),"another player cannot recall owner bike");check(mando.vehicles().entity(id)==entity && packedCount(other)==0,"foreign ownership unchanged");
        mando.combat().tag(owner);check(!mando.vehicles().recall(owner),"player combat blocks recall");mando.combat().clear(owner);
        var record=records().get(id);set(record,"hitUntil",System.currentTimeMillis()+10000);check(!mando.vehicles().recall(owner),"vehicle combat blocks recall");set(record,"hitUntil",0L);
        for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Material.STONE,64));check(!mando.vehicles().recall(owner),"full inventory blocks recall");owner.getInventory().clear();
        entity.setVelocity(new Vector(.2,0,0));check(!mando.vehicles().recall(owner),"moving vehicle blocks recall");entity.setVelocity(new Vector());
        entity.teleport(at.clone().add(0,5,0));check(!mando.vehicles().recall(owner),"airborne vehicle blocks recall");entity.teleport(at);entity.setVelocity(new Vector());
        check(mando.vehicles().mount(owner,id,true),"owner can board bike for occupied test");check(!mando.vehicles().recall(owner),"occupied vehicle blocks recall");
        var field=VehicleService.class.getDeclaredField("active");field.setAccessible(true);var live=((Map<?,?>)field.get(mando.vehicles())).get(id);var accessor=live.getClass().getDeclaredMethod("model");accessor.setAccessible(true);((net.mandomc.gameplay.vehicles.VehicleModels.Handle)accessor.invoke(live)).eject(owner);
        later(3,this::remote);
    }
    private void remote()throws Exception{
        owner.teleport(mando.worlds().spawn("Batuu"));command(owner,"recallvehicle");check(packedCount(owner)==1 && mando.vehicles().entity(id)==null,"cross-world recall returns original identity");
        ItemStack first=packed(owner).clone();check(health(first)==31,"recall preserves actual health");command(owner,"vehicle","recall");check(packedCount(owner)==1,"repeat recall gives no second item");
        owner.getInventory().clear();owner.teleport(at.clone().add(0,0,5));id=mando.vehicles().deploy(owner,first,at);check(id!=null,"recalled token redeploys");
        mando.vehicles().entity(id).remove();check(mando.vehicles().recall(owner),"removed base remains recoverable");
        check(mando.vehicles().deploy(owner,first.clone(),at)==null,"spent stale token rejected");check(packedCount(owner)==1,"stale token does not consume current packed item");
        var current=packed(owner);id=mando.vehicles().deploy(owner,current,at);check(id!=null,"current token deploys once");
        later(25,this::restart);
    }
    private void restart()throws Exception{
        var state=new Properties();state.setProperty("checks",""+checks);state.setProperty("owner",owner.getUniqueId().toString());state.setProperty("id",id.toString());Files.createDirectories(getDataFolder().toPath());try(var out=Files.newOutputStream(getDataFolder().toPath().resolve("restart.properties"))){state.store(out,"Disposable recall restart phase");}
        actors.close();actors=null;getLogger().info("VEHICLE RECALL RESTART READY: "+checks);Bukkit.shutdown();
    }
    private void finish(Throwable error){try{HandlerList.unregisterAll(canceller);if(actors!=null)actors.close();Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),error==null?checks+" native vehicle recall assertions passed\n":error.toString());if(error==null)getLogger().info("VEHICLE RECALL PASSED: "+checks);else getLogger().log(java.util.logging.Level.SEVERE,"VEHICLE RECALL FAILED",error);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Probe cleanup failed",e);}Bukkit.shutdown();}
}
