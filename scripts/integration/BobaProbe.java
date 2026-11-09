package net.mandomc.verification;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import com.ticxo.modelengine.api.ModelEngineAPI;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;
import net.mandomc.bootstrap.MandoPlugin;
/** Temporary legacy Boba/precise-warp verification; restores the isolated sky platform. */
public final class BobaProbe extends JavaPlugin {
 private MandoPlugin plugin;private World world;private Mob boss;private Cow enemy;private int checks,shots;private double maxY;
 private final List<BlockState> blocks=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();private final Listener observer=new Listener(){};
 @Override public void onEnable(){later(120,this::begin);}
 private void begin() throws Exception {
  plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
  var tw=Bukkit.getWorld(NamespacedKey.minecraft("tatooine"));
  for(int x=1711;x<=1712;x++)for(int z=2201;z<=2202;z++)for(int y=98;y<=101;y++)getLogger().info("Warp block "+x+","+y+","+z+" = "+tw.getBlockAt(x,y,z).getType());
  var warp=plugin.warps().destination("tatooine");
  check(warp.getWorld().getKey().toString().equals("minecraft:tatooine"),"warp uses Tatooine");
  check(Math.abs(warp.getX()-1711.845046707056)<0.000001 && warp.getY()==100 && Math.abs(warp.getZ()-2201.7401780545574)<0.000001,"warp retains exact player-selected coordinates and safe standing space");
  check(Math.abs(warp.getYaw()-81.4468)<0.001 && Math.abs(warp.getPitch()+11.09995)<0.001,"warp retains player yaw and pitch");
  check(new net.mandomc.integrations.regions.SaberProtection().allowed(warp),"palace arrival permits boss combat");
  var gun=plugin.items().create("ee3");check("EE3".equals(WeaponMechanicsAPI.getWeaponTitle(gun)),"EE-3 catalog creates real weapon");
  check(gun.getItemMeta().getItemModel().toString().equals("mandomc:blasters/ee3"),"EE-3 uses recovered item model");check(!plugin.rules().banned(gun),"EE-3 allowed by survival rules");
  world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
  for(int x=56;x<=58;x++)for(int z=56;z<=58;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
  for(int x=900;x<=930;x++)for(int z=900;z<=930;z++){var b=world.getBlockAt(x,300,z);if(!b.getType().isAir())throw new IllegalStateException("Sky fixture occupied");blocks.add(b.getState());b.setType(Material.SANDSTONE,false);}
  boss=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob("BobaFett",new Location(world,910.5,301,910.5),1);boss.setAI(true);Bukkit.getMobGoals().removeAllGoals(boss);boss.setRemoveWhenFarAway(false);
  enemy=world.spawn(boss.getLocation().add(0,0,8),Cow.class,c->{c.setAI(false);});
  select();suppress();
  Bukkit.getScheduler().runTaskTimer(this,()->{if(boss.isValid()&&enemy.isValid()){select();maxY=Math.max(maxY,boss.getY());}},1,1);
  Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{var shot=(WeaponShootEvent)e;if(shot.getShooter()!=boss)return;shot.getProjectile().remove();check(shot.getWeaponTitle().equals("EE3"),"Boba fires actual EE-3 projectile");shots++;},this,true);
  later(10,()->{
  check(boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==500,"legacy 500 health retained");
  check(boss.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue()==8,"legacy armor retained");

   var model=ModelEngineAPI.getModeledEntity(boss);check(model!=null&&model.getModels().containsKey("boba_fett"),"recovered Boba model attached");check(!model.isBaseEntityVisible(),"vanilla zombie hidden");
   check(boss.getBoundingBox().getHeight()>1.9,"native collision retained");
   for(String a:List.of("idle","walk","dodge_left","dodge_right","attack1","attack2","attack3","death"))check(ModelEngineAPI.getBlueprint("boba_fett").getAnimations().containsKey(a),"legacy animation exists: "+a);
   drops();dodge();
  });
 }
 private void dodge(){boss.setRotation(0,0);var before=boss.getLocation();cast("Boba_dodge");later(6,()->{getLogger().info("Dodge displacement="+boss.getLocation().distance(before)+" velocity="+boss.getVelocity()+" valid="+boss.isValid());check(boss.getLocation().distanceSquared(before)>0.1,"legacy dodge moves Boba");boss.teleport(new Location(world,910.5,301,910.5));boss.setVelocity(new org.bukkit.util.Vector());burst();});}
 private void burst(){cast("Boba_burst_shot");later(55,()->{check(shots==10,"legacy five paired burst releases emit ten projectiles: "+shots);shots=0;maxY=boss.getY();cast("Boba_jump_shot");later(55,()->{check(shots==2,"legacy jump shot emits its paired projectiles");check(maxY>301.3,"legacy jetpack jump lifts Boba: "+maxY);flame();});});}
 private void flame(){boss.teleport(new Location(world,910.5,301,910.5,0,0));boss.setVelocity(new org.bukkit.util.Vector());enemy.teleport(new Location(world,910.5,301,913.5));enemy.setHealth(10);enemy.setFireTicks(0);cast("Boba_flamethrower");later(40,()->{check(enemy.getFireTicks()>0 || enemy.getHealth()<10,"legacy flame projectile hits and ignites nearby target");later(45,()->{getLogger().info("BOBA PROBE PASSED: "+checks+" assertions");cleanup();});});}
 private void drops(){var mob=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss);int weapons=0,cells=0;
  for(int i=0;i<1000;i++){var e=new io.lumine.mythic.bukkit.events.MythicMobDeathEvent(mob,null,new ArrayList<>());Bukkit.getPluginManager().callEvent(e);int w=0,a=0;for(var item:e.getDrops()){var id=WeaponMechanicsAPI.getWeaponTitle(item);if("EE3".equals(id))w+=item.getAmount();else if(item.isSimilar(plugin.items().create("standard_plasma_cells")))a+=item.getAmount();else throw new AssertionError("Unexpected Boba loot "+id);}if(w>1||a>3)throw new AssertionError("Bad legacy Boba loot bounds");weapons+=w;cells+=a;}
  check(weapons>400&&weapons<600,"legacy half-chance EE-3 death drop");check(cells>1300&&cells<1700,"legacy three independent half-chance plasma-cell rolls");}
 private void select(){var api=MythicBukkit.inst().getAPIHelper();boss.setTarget(enemy);api.addThreat(boss,enemy,100);api.getMythicMobInstance(boss).setTarget(BukkitAdapter.adapt(enemy));}
 private void suppress(){var a=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss);a.setSkillCooldownMillis((io.lumine.mythic.core.skills.AbstractSkill)MythicBukkit.inst().getSkillManager().getSkill("Boba_bobafett_attack").orElseThrow(),600000);}
 private void cast(String s){check(MythicBukkit.inst().getAPIHelper().castSkill(boss,s,boss.getLocation(),List.of(enemy),List.of(),1),"cast "+s);}
 private interface Check{void run() throws Exception;}
 private void later(long d,Check c){Bukkit.getScheduler().runTaskLater(this,()->{try{c.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BOBA PROBE FAILED",e);cleanup();}},d);}
 private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;getLogger().info("PASS: "+msg);}
 private void cleanup(){Bukkit.getScheduler().cancelTasks(this);HandlerList.unregisterAll(observer);if(boss!=null)boss.remove();if(enemy!=null)enemy.remove();blocks.forEach(b->b.update(true,false));blocks.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
 @Override public void onDisable(){cleanup();}
}
