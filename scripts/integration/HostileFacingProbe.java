package net.mandomc.verification;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import com.ticxo.modelengine.api.ModelEngineAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;
/** Checks actual rendered/native rotation before attack clips across every shipped hostile model. */
public final class HostileFacingProbe extends JavaPlugin {
 private record Case(String type,String model,String skill,String animation,double range,boolean nativeMelee){}
 private record Fixture(Case spec,Mob mob,Cow enemy){}
 private final List<Fixture> fixtures=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();private final Listener observer=new Listener(){};private int checks;
 @Override public void onEnable(){later(120,this::begin);}
 private void begin() throws Exception {
  var world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
  for(int x=61;x<=66;x++)for(int z=61;z<=66;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
  var cases=List.of(new Case("BobaFett","boba_fett","Boba_burst_shot","attack1",8,false),new Case("BobaFett","boba_fett","Boba_jump_shot","attack2",8,false),new Case("BobaFett","boba_fett","Boba_flamethrower","attack3",8,false),new Case("GamorreanGuard","gamorrean_guard","Light_Slash","attack1",2,false),new Case("GamorreanGuard","gamorrean_guard","Heavy_Slash","attack2",2,false),new Case("GamorreanGuard","gamorrean_guard","Spin_Attack","attack3",2,false),new Case("QuarrenGuard","quarren_guard","quarren_attack","attack",8,false),new Case("TwilekGuard","twilek_guard","twilek_attack1","attack1",8,false),new Case("TwilekGuard","twilek_guard","twilek_attack2","attack2",8,false),new Case("TuskenRaiderSniper","ranged_raider","MandoTuskenCycler","attack",8,false),new Case("Massiff","massiff","Massiff_Attack","attack",2,false),new Case("Bossk","bossk","BosskAim","attack4",8,false),new Case("TuskenRaiderMelee","melee_raider","MandoTuskenMeleeAttack","attack1",2,true));
  int index=0;for(var c:cases){var loc=new Location(world,1000.5+(index%4)*15,301,1000.5+(index/4)*15,180,0);index++;
   var mob=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob(c.type,loc,1);mob.setAI(false);mob.setGravity(false);mob.setRemoveWhenFarAway(false);
   var enemy=world.spawn(loc.clone().add(0,0,c.range),Cow.class,e->{e.setAI(false);e.setGravity(false);e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(100);e.setHealth(100);});
   var f=new Fixture(c,mob,enemy);fixtures.add(f);select(f);suppress(f);
  }
  Bukkit.getScheduler().runTaskTimer(this,()->fixtures.forEach(this::select),1,1);
  Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{var s=(WeaponShootEvent)e;if(fixtures.stream().anyMatch(f->f.mob==s.getShooter()))s.getProjectile().remove();},this,true);
  later(40,()->{for(var f:fixtures){var api=MythicBukkit.inst().getAPIHelper();var active=api.getMythicMobInstance(f.mob);active.clearSkillCooldowns();ModelEngineAPI.getModeledEntity(f.mob).setYBodyRotImmediately(180);f.mob.setRotation(180,0);check(api.castSkill(f.mob,f.spec.skill,f.mob.getLocation(),List.of(f.enemy),List.of(),1),"cast "+f.spec.skill);suppress(f);}
   later(1,()->{for(var f:fixtures)if(!f.spec.nativeMelee)check(angle(body(f),180)<150,f.spec.skill+" avoids instant half-turn");});
   later(5,()->{for(var f:fixtures)if(!f.spec.nativeMelee)check(!playing(f),f.spec.skill+" turns before attack animation starts");});
   later(8,()->{for(var f:fixtures){check(playing(f),f.spec.skill+" attack animation starts after facing");check(angle(body(f),0)<10,f.spec.skill+" rendered body faces target at animation start");check(angle(ModelEngineAPI.getModeledEntity(f.mob).getBase().getYBodyRot(),0)<10,f.spec.skill+" native body agrees at animation start");f.enemy.teleport(f.mob.getLocation().add(f.spec.range,0,0));}
    later(5,()->{for(var f:fixtures)if(!f.spec.nativeMelee)check(angle(body(f),-90)<10,f.spec.skill+" tracks target changing sides during wind-up");later(45,()->{for(var f:fixtures)if(Set.of("QuarrenGuard","TwilekGuard","TuskenRaiderSniper").contains(f.spec.type))check(!MythicBukkit.inst().getAPIHelper().getMythicMobInstance(f.mob).getAuraRegistry().hasAura(f.spec.type.equals("TuskenRaiderSniper")?"MandoTuskenAiming":"JabbaAiming"),f.spec.skill+" releases planting after clip");getLogger().info("HOSTILE FACING PROBE PASSED: "+checks+" assertions");cleanup();});});
   });
  });
 }
 private void suppress(Fixture f){var api=MythicBukkit.inst();var active=api.getAPIHelper().getMythicMobInstance(f.mob);for(String s:List.of("Boba_bobafett_attack","Gamorrean_Brain","quarren_attack","twilek_attack","MandoTuskenCycler","Massif_Combat","BosskCloseCombo","BosskRangedBrain"))api.getSkillManager().getSkill(s).ifPresent(skill->active.setSkillCooldownMillis((io.lumine.mythic.core.skills.AbstractSkill)skill,600000));}
 private void select(Fixture f){if(!f.mob.isValid()||!f.enemy.isValid())return;var api=MythicBukkit.inst().getAPIHelper();f.mob.setTarget(f.enemy);api.addThreat(f.mob,f.enemy,100);api.getMythicMobInstance(f.mob).setTarget(BukkitAdapter.adapt(f.enemy));}
 private float body(Fixture f){return ModelEngineAPI.getModeledEntity(f.mob).getYBodyRot();}
 private boolean playing(Fixture f){return ModelEngineAPI.getModeledEntity(f.mob).getModels().get(f.spec.model).getAnimationHandler().isPlayingAnimation(f.spec.animation);}
 private double angle(double a,double b){return Math.abs(Math.IEEEremainder(a-b,360));}
 private interface Check{void run() throws Exception;}
 private void later(long ticks,Check c){Bukkit.getScheduler().runTaskLater(this,()->{try{c.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"HOSTILE FACING PROBE FAILED",e);cleanup();}},ticks);}
 private void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;getLogger().info("PASS: "+s);}
 private void cleanup(){Bukkit.getScheduler().cancelTasks(this);HandlerList.unregisterAll(observer);for(var f:fixtures){f.mob.remove();f.enemy.remove();}fixtures.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
 @Override public void onDisable(){cleanup();}
}
