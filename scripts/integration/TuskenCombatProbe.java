package net.mandomc.verification;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.block.BlockState;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.mobs.DespawnMode;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;

/** Temporary live checks for actual shot timing, model facing, and automatic walking after attacks. */
public final class TuskenCombatProbe extends JavaPlugin {
    private final List<LivingEntity> entities = new ArrayList<>();
    private final List<BlockState> blocks = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private final Listener observer = new Listener() {};
    private final Map<UUID, Integer> shots = new HashMap<>();
    private World world;
    private Mob sniper;
    private Cow target;
    private int checks, started;
    private boolean testingShot;

    @Override public void onEnable() {
        later(120, this::begin);
    }
    private void begin() throws Exception {
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for (int x=36; x<=39; x++) for (int z=36; z<=39; z++) {
            var c=world.getChunkAt(x,z); c.addPluginChunkTicket(this); chunks.add(c);
        }
        for(int x=592;x<=628;x++) for(int z=592;z<=622;z++) {
            var b=world.getBlockAt(x,300,z); checkAir(b.getState()); blocks.add(b.getState()); b.setType(Material.SANDSTONE,false);
        }
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDeathEvent.class, observer,
                EventPriority.HIGHEST, (l,e)-> {
                    var death=(org.bukkit.event.entity.EntityDeathEvent)e;
                    if(entities.contains(death.getEntity())) {death.getDrops().clear();death.setDroppedExp(0);}
                }, this, true);
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class, observer, EventPriority.MONITOR, (l,e)-> {
            var shot=(WeaponShootEvent)e;
            if (!entities.contains(shot.getShooter())) return;
            shots.merge(shot.getShooter().getUniqueId(),1,Integer::sum);
            shot.getProjectile().remove(); // No test projectile or damage may escape the sky fixture.
            if (!testingShot || shot.getShooter()!=sniper) return;
            try {
                int elapsed=Bukkit.getCurrentTick()-started;
                check(elapsed>=18 && elapsed<=20,"actual cycler emits at tick 18 (observed "+elapsed+")");
                check(shot.getWeaponTitle().equals("CyclerRifle"),"shot retains WeaponMechanics CyclerRifle identity");
                var base=ModelEngineAPI.getModeledEntity(sniper).getBase();
                float yaw=sniper.getLocation().setDirection(target.getLocation().toVector().subtract(sniper.getLocation().toVector())).getYaw();
                getLogger().info("AIM expected="+yaw+" entity="+sniper.getYaw()+" head="+base.getYHeadRot()+" body="+base.getYBodyRot()+" model="+active(sniper,"ranged_raider").getYBodyRot());
                check(angle(sniper.getYaw(),yaw)<3,"native facing tracks moved target before shot");
                check(angle(base.getYBodyRot(),yaw)<10,"native body faces moved target at shot");
                check(angle(active(sniper,"ranged_raider").getYBodyRot(),yaw)<10,"visible model faces moved target at shot");
                check(angle(base.getYHeadRot(),yaw)<10,"head faces moved target at shot");
            } catch(Throwable error) { fail(error); }
        }, this, true);
        sniper=spawn("TuskenRaiderSniper",new Location(world,600.5,301,600.5,180,0));
        target=world.spawn(new Location(world,608.5,301,608.5),Cow.class,c->{c.setAI(false);c.setGravity(false);c.setRemoveWhenFarAway(false);}); entities.add(target);
        later(5,()-> {
            var model=active(sniper,"ranged_raider");
            check(model.getBlueprint().getAnimations().get("attack").isOverride(),"attack retains Override");
            check(model.getBlueprint().getAnimations().get("attack").getLoopMode().name().equals("ONCE"),"attack ends instead of holding");
            check(model.getBlueprint().getAnimations().get("walk").getLoopMode().name().equals("LOOP"),"walk remains looping");
            sniper.getPathfinder().moveTo(new Location(world,620.5,301,600.5),1.0);
            later(15,()-> {
                check(model.getAnimationHandler().isWalking(model),"native locomotion detected before attack");
                check(model.getAnimationHandler().isPlayingAnimation("walk"),"walk animation automatically plays before attack");
                aim();
            });
        });
    }
    private void aim() throws Exception {
        started=Bukkit.getCurrentTick(); testingShot=true;
        select(sniper,target);
        cast(sniper,"MandoTuskenCycler",target);
        check(shots.getOrDefault(sniper.getUniqueId(),0)==0,"no immediate shot or damage at animation start");
        later(8,()-> {target.teleport(target.getLocation().add(-8,0,8));});
        later(16,()->check(shots.getOrDefault(sniper.getUniqueId(),0)==0,"no projectile before recoil onset"));
        later(24,()-> {
            check(shots.getOrDefault(sniper.getUniqueId(),0)==1,"exactly one delayed shot");
            testingShot=false; select(sniper,null);
        });
        later(53,()-> {
            var model=active(sniper,"ranged_raider");
            check(!model.getAnimationHandler().isPlayingAnimation("attack"),"attack releases its final pose");
            check(sniper.hasAI(),"aiming restores AI after attack");
            sniper.getPathfinder().moveTo(new Location(world,600.5,301,618.5),1.0);
            later(15,()-> {
                check(model.getAnimationHandler().isWalking(model),"native locomotion resumes after attack");
                check(model.getAnimationHandler().isPlayingAnimation("walk"),"walk automatically resumes after attack");
                sniper.remove(); target.remove();
                invalidTargetCases();
            });
        });
    }
    private void invalidTargetCases() throws Exception {
        int index=0;
        for (String mode:List.of("range","wall","deadTarget","deadCaster")) {
            double testX=598.5+6*index++;
            var s=spawn("TuskenRaiderSniper",new Location(world,testX,301,598.5));s.setAI(false);
            var t=world.spawn(new Location(world,testX,301,606.5),Cow.class,c->{c.setAI(false);c.setGravity(false);});entities.add(t);
            select(s,t);cast(s,"MandoTuskenCycler",t);
            later(8,()-> {
                switch(mode) {
                    case "range" -> t.teleport(t.getLocation().add(0,0,40));
                    case "deadTarget" -> t.setHealth(0);
                    case "deadCaster" -> s.setHealth(0);
                    case "wall" -> {for(int x=(int)testX-1;x<=(int)testX+1;x++) for(int y=301;y<=304;y++) {var b=world.getBlockAt(x,y,603);blocks.add(b.getState());b.setType(Material.STONE,false);}}
                }
            });
            later(25,()-> {check(shots.getOrDefault(s.getUniqueId(),0)==0,"wind-up cancels shot after "+mode);s.remove();t.remove();});
        }
        later(30,this::meleeWalk);
    }
    private void meleeWalk() throws Exception {
        var melee=spawn("TuskenRaiderMelee",new Location(world,608.5,301,612.5));
        later(5,()-> {
            var model=active(melee,"melee_raider");
            for(String name:List.of("attack1","attack2","attack3")) check(model.getBlueprint().getAnimations().get(name).getLoopMode().name().equals("ONCE"),"melee "+name+" releases after playing");
            model.getAnimationHandler().playAnimation("attack1",0,0.1,1,true);
            later(27,()-> {
                check(!model.getAnimationHandler().isPlayingAnimation("attack1"),"melee attack finishes");
                melee.getPathfinder().moveTo(new Location(world,620.5,301,612.5),1);
                later(15,()-> {
                    check(model.getAnimationHandler().isWalking(model),"melee native locomotion detected");
                    check(model.getAnimationHandler().isPlayingAnimation("walk"),"melee walk automatically resumes");
                    melee.remove(); combatMovement();
                });
            });
        });
    }
    private void combatMovement() throws Exception {
        var calm=spawn("TuskenRaiderSniper",new Location(world,600.5,301,600.5),false);
        later(5,()-> {
            check(walkSpeed(calm)==1,"calm Tusken keeps normal walk playback");
            var calmStart=calm.getLocation();
            calm.getPathfinder().moveTo(new Location(world,624.5,301,600.5),1);
            later(12,()-> {
                double calmDistance=calmStart.distance(calm.getLocation());
                check(calmDistance>1,"baseline normal-speed navigation advances");
                calm.remove();
                var fighter=spawn("TuskenRaiderSniper",new Location(world,600.5,301,600.5),false);
                var enemy=world.spawn(new Location(world,627.5,301,600.5),Cow.class,c->{
                    c.setAI(false);c.setGravity(false);c.setRemoveWhenFarAway(false);
                });
                entities.add(enemy);
                var mythic=MythicBukkit.inst();
                var activeMob=mythic.getAPIHelper().getMythicMobInstance(fighter);
                var fire=(io.lumine.mythic.core.skills.AbstractSkill)mythic.getSkillManager()
                        .getSkill("MandoTuskenCycler").orElseThrow();
                // Isolate real native combat movement before allowing the next shot.
                activeMob.setSkillCooldownMillis(fire,60000);
                later(5,()-> {
                    var combatStart=fighter.getLocation();
                    select(fighter,enemy);
                    later(12,()-> {
                        double combatDistance=combatStart.distance(fighter.getLocation());
                        getLogger().info("MOVEMENT normal="+calmDistance+" combat="+combatDistance);
                        check(combatDistance>calmDistance*1.25,"real combat chase is faster than calm walking");
                        check(walkSpeed(fighter)==1.5,"combat automatically increases walk playback");
                        enemy.teleport(fighter.getLocation().add(0,0,12));
                        fighter.getPathfinder().stopPathfinding();
                        var repositionStart=fighter.getLocation();
                        later(25,()-> {
                            check(repositionStart.distance(fighter.getLocation())>0.8,
                                    "natural combat timer repositions inside firing range");
                            check(fighter.hasAI(),"movement between shots keeps AI active");
                            var model=active(fighter,"ranged_raider");
                            check(model.getAnimationHandler().isWalking(model),
                                    "restless repositioning drives the automatic walk animation");
                            enemy.teleport(fighter.getLocation().add(0,0,12));
                            var aimStart=fighter.getLocation();
                            activeMob.clearSkillCooldowns();
                            cast(fighter,"MandoTuskenCycler",enemy);
                            later(10,()-> {
                                check(!fighter.hasAI(),"aiming suspends restless AI movement");
                                check(aimStart.distance(fighter.getLocation())<0.15,
                                        "combat reposition timer cannot move the planted shooter");
                            });
                            later(40,()->check(aimStart.distance(fighter.getLocation())<0.15,
                                    "shooter stays planted through recoil recovery"));
                            later(65,()-> {
                                check(fighter.hasAI(),"combat movement resumes after firing pose");
                                check(aimStart.distance(fighter.getLocation())>0.5,
                                        "Tusken automatically repositions again after shooting");
                                activeMob.getThreatTable().dropCombat();
                                activeMob.setTarget(null);fighter.setTarget(null);enemy.remove();
                                later(5,()-> {
                                    check(!activeMob.getThreatTable().inCombat(),"target loss clears Mythic combat state");
                                    check(walkSpeed(fighter)==1,"dropping combat restores calm walk playback");
                                    getLogger().info("TUSKEN COMBAT PROBE PASSED: "+checks+" assertions");
                                    cleanup();
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private double walkSpeed(Mob mob) {
        return active(mob,"ranged_raider").getAnimationHandler()
                .getDefaultProperty(com.ticxo.modelengine.api.animation.ModelState.WALK).getSpeed();
    }
    private Mob spawn(String type,Location p) throws Exception {
        return spawn(type,p,true);
    }
    private Mob spawn(String type,Location p,boolean removeGoals) throws Exception {
        var e=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob(type,p,1);entities.add(e);
        MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e).setDespawnMode(DespawnMode.NEVER);
        if(removeGoals) Bukkit.getMobGoals().removeAllGoals(e);
        e.setRemoveWhenFarAway(false);return e;
    }
    private void select(Mob e,LivingEntity t) {
        e.setTarget(t);var api=MythicBukkit.inst().getAPIHelper();
        var mob=api.getMythicMobInstance(e);
        if(t!=null){api.addThreat(e,t,100);mob.setTarget(BukkitAdapter.adapt(t));}
        else{mob.getThreatTable().clearTarget();mob.setTarget(null);}
    }
    private void cast(Mob e,String skill,LivingEntity t) {check(MythicBukkit.inst().getAPIHelper().castSkill(e,skill,e.getLocation(),List.of(t),List.of(),1),"skill starts: "+skill);}
    private ActiveModel active(Mob e,String id) {return ModelEngineAPI.getModeledEntity(e).getModels().get(id);}
    private double angle(float a,float b) {return Math.abs(Math.IEEEremainder(a-b,360));}
    private void checkAir(BlockState b) {if(!b.getType().isAir())throw new IllegalStateException("Fixture is not empty sky");}
    private void check(boolean ok,String m) {if(!ok)throw new AssertionError(m);checks++;getLogger().info("PASS: "+m);}
    private interface Check {void run() throws Exception;}
    private void later(long delay,Check run) {Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable e){fail(e);}},delay);}
    private void fail(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"TUSKEN COMBAT PROBE FAILED",e);Bukkit.getScheduler().cancelTasks(this);cleanup();}
    private void cleanup() {HandlerList.unregisterAll(observer);entities.forEach(Entity::remove);entities.clear();Collections.reverse(blocks);blocks.forEach(b->b.update(true,false));blocks.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
    @Override public void onDisable(){cleanup();}
}
