package net.mandomc.verification;
import java.util.*;
import java.nio.file.*;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.mandomc.integrations.mobs.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
public final class MobBaseStatsProbe extends JavaPlugin {
 private final List<String> checks = new ArrayList<>();
 private final List<LivingEntity> owned = new ArrayList<>();
 private MythicMobAccess mobs;
 private void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks.add(name);}
 private LivingEntity raw(String type,int level,Location p) throws Exception {var e=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob(type,p,level);e.setAI(false);owned.add(e);return e;}
 @Override public void onEnable(){
  if(Bukkit.getPort()!=25578||!Bukkit.getWorlds().getFirst().getName().equals("levels_test"))throw new IllegalStateException("Disposable runtime only");
  Bukkit.getScheduler().runTaskLater(this,()->{try{start();}catch(Throwable e){finish(e);}},100);
 }
 private void start() throws Exception {
  var w=Bukkit.getWorlds().getFirst();var p=new Location(w,0.5,100,0.5);w.getChunkAt(p).addPluginChunkTicket(this);
  var state = getDataFolder().toPath().resolve("saved.ids");
  if (!Files.exists(state)) {
   var old=raw("TuskenRaiderMelee",5,p);old.setHealth(7);
   MythicBukkit.inst().getAPIHelper().getMythicMobInstance(old).setDisplayName("§6Tusken Raider §7[Lv. 5]");
   var herd=raw("Bantha",5,p.clone().add(5,0,0));((Ageable)herd).setBaby();((Ageable)herd).setAgeLock(true);
   if(herd instanceof Tameable t){t.setOwner(Bukkit.getOfflinePlayer(UUID.fromString("00000000-0000-0000-0000-000000000123")));t.setTamed(true);}
   var boss=raw("Bossk",1,p.clone().add(10,0,0));boss.setHealth(111);
   for(var e:owned){e.setGravity(false);e.setPersistent(true);MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e).setDespawnMode(io.lumine.mythic.core.mobs.DespawnMode.NEVER);}
   Files.createDirectories(getDataFolder().toPath());Files.writeString(state,old.getUniqueId()+"\n"+herd.getUniqueId()+"\n"+boss.getUniqueId());
   owned.clear();getLogger().info("Saved old level5 fixtures for genuine restart migration");Bukkit.shutdown();return;
  }
  var ids=Files.readAllLines(state);
  var old=(LivingEntity)Bukkit.getEntity(UUID.fromString(ids.get(0)));
  var herd=(LivingEntity)Bukkit.getEntity(UUID.fromString(ids.get(1)));
  var boss=(LivingEntity)Bukkit.getEntity(UUID.fromString(ids.get(2)));
  check(old!=null&&herd!=null&&boss!=null,"existing UUIDs loaded after actual save/restart");
  owned.addAll(List.of(old,herd,boss));
  var activeOld=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(old);
  check(activeOld.getLevel()==5,"persisted level5 fixture restored before migration");
  old.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.7);
  old.getAttribute(Attribute.SCALE).setBaseValue(1.25);
  ((Mob)old).setTarget(herd);
  double bossMax=boss.getAttribute(Attribute.MAX_HEALTH).getValue();
  mobs=new MythicMobAccess();
  Bukkit.getScheduler().runTaskLater(this,()->{try{
   check(mobs.level(old)==1,"saved level normalized");
   check(!old.hasAI(),"existing AI state preserved");
   check(old.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue()==0.7,"current movement preserved");
   check(old.getAttribute(Attribute.SCALE).getBaseValue()==1.25,"current model scale preserved");
   check(((Mob)old).getTarget()==herd,"current combat target preserved");check(old.getHealth()==7,"injured current HP preserved");
   check(old.getAttribute(Attribute.MAX_HEALTH).getValue()==20,"existing Tusken max HP20");
   check(old.getAttribute(Attribute.ATTACK_DAMAGE).getValue()==4,"existing Tusken attack4");
   check(!old.getCustomName().contains("Lv."),"existing level suffix removed");
   check(!((Ageable)herd).isAdult()&&((Ageable)herd).getAgeLock(),"baby age and age lock preserved");
   if(herd instanceof Tameable t)check(t.isTamed()&&t.getOwner().getUniqueId().toString().endsWith("0123"),"taming owner preserved");
   check(herd.getHealth()<=50&&herd.getAttribute(Attribute.MAX_HEALTH).getValue()==50,"old full animal clamped to base max");
   check(boss.getHealth()==111&&boss.getAttribute(Attribute.MAX_HEALTH).getValue()==bossMax,"Bossk HP and maximum untouched");
   var expected=Map.of("TuskenRaiderSniper",20.0,"TuskenRaiderMelee",20.0,"Bantha",50.0,"Dewback",40.0,"Massiff",50.0,"GamorreanGuard",30.0,"QuarrenGuard",25.0,"TwilekGuard",25.0);
   for(var entry:expected.entrySet()){
    var e=mobs.spawn(entry.getKey(),p,5);e.setAI(false);owned.add(e);
    check(mobs.level(e)==1,entry.getKey()+" adapter base level");
    check(e.getAttribute(Attribute.MAX_HEALTH).getValue()==entry.getValue(),entry.getKey()+" configured HP");
    check(e.getCustomName()!=null&&!e.getCustomName().contains("Lv"),entry.getKey()+" plain name");
   }
   var external=raw("QuarrenGuard",5,p);Bukkit.getScheduler().runTaskLater(this,()->{try{
    check(mobs.level(external)==1,"native vendor spawn5 coerced to base level");
    check(external.getAttribute(Attribute.MAX_HEALTH).getValue()==25,"native vendor spawn base HP25");
    finish(null);
   }catch(Throwable e){finish(e);}},3);
  }catch(Throwable e){finish(e);}},3);
 }
 private void finish(Throwable error){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),String.join("\n",checks)+(error==null?"":"\n"+error));}catch(Exception e){e.printStackTrace();}if(error!=null)error.printStackTrace();owned.forEach(Entity::remove);getLogger().info("Mob base stats checks: "+checks.size()+" "+(error==null?"PASS":"FAIL"));Bukkit.shutdown();}
}
