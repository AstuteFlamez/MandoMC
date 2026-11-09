package net.mandomc.verification;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;

/** Temporary live per-entity cadence, native navigation and planted-shot fixture. */
public final class JabbaAiProbe extends JavaPlugin {
    private final List<Entity> entities=new ArrayList<>();
    private final List<BlockState> blocks=new ArrayList<>();
    private final List<Chunk> chunks=new ArrayList<>();
    private final Listener observer=new Listener(){};
    private final Map<UUID,List<Integer>> bursts=new LinkedHashMap<>();
    private final Map<UUID,Integer> last=new HashMap<>();
    private final List<Mob> guards=new ArrayList<>();
    private final Map<Mob,LivingEntity> targets=new LinkedHashMap<>();
    private World world; private int checks;
    @Override public void onEnable(){later(120,this::begin);}
    private void begin() throws Exception {
        world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for(int x=43;x<=47;x++)for(int z=43;z<=47;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
        for(int x=700;x<=750;x++)for(int z=700;z<=735;z++){
            var b=world.getBlockAt(x,300,z);if(!b.getType().isAir())throw new IllegalStateException("Fixture not empty sky");
            blocks.add(b.getState());b.setType(Material.SANDSTONE,false);
        }
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{
            var shot=(WeaponShootEvent)e;if(!entities.contains(shot.getShooter()))return;
            shot.getProjectile().remove();UUID id=shot.getShooter().getUniqueId();int tick=Bukkit.getCurrentTick();
            if(tick-last.getOrDefault(id,-10000)>25)bursts.computeIfAbsent(id,k->new ArrayList<>()).add(tick);
            last.put(id,tick);
        },this,true);
        for(String type:List.of("QuarrenGuard","TwilekGuard"))for(int i=0;i<4;i++){
            var mob=spawn(type,new Location(world,705+10*i,301,type.equals("QuarrenGuard")?705:720));
            guards.add(mob);mob.setAI(false);mob.setGravity(false);
            var target=target(mob.getLocation().add(0,0,8));select(mob,target);
        }
        Bukkit.getScheduler().runTaskTimer(this,()->targets.forEach((m,t)->{if(m.isValid()&&t.isValid())select(m,t);}),1,1);
        later(50,()->{for(var g:guards){var a=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(g);getLogger().info("DIAG "+a.getType().getInternalName()+" combat="+a.getThreatTable().inCombat()+" target="+a.hasTarget()+" ai="+g.hasAI()+" shots="+bursts.get(g.getUniqueId()));}});
        later(420,()->{
            Set<Integer> first=new HashSet<>(),intervals=new HashSet<>();
            for(var guard:guards){var b=bursts.getOrDefault(guard.getUniqueId(),List.of());
                check(b.size()>=3,"guard continues firing multiple bursts: "+b.size());first.add(b.getFirst());
                for(int i=1;i<b.size();i++){int d=b.get(i)-b.get(i-1);check(d>=49&&d<=105,"bounded independently rolled burst interval: "+d);intervals.add(d);}
            }
            check(first.size()>=3,"same-tick spawns have independently staggered first bursts");
            check(intervals.size()>=4,"successive attacks vary instead of sharing a fixed timer");
            guards.forEach(Entity::remove);movement("QuarrenGuard",()->movement("TwilekGuard",()->{
                getLogger().info("JABBA AI PROBE PASSED: "+checks+" assertions");cleanup();
            }));
        });
    }
    private void movement(String type,Check next) throws Exception {
        var mob=spawn(type,new Location(world,715.5,301,710.5));var enemy=target(mob.getLocation().add(0,0,12));select(mob,enemy);
        cooldown(mob,600000);var start=mob.getLocation();
        later(40,()->{
            check(start.distance(mob.getLocation())>0.5,type+" navigates between bursts");
            check(mob.hasAI(),type+" retains native AI while repositioning");
            enemy.teleport(mob.getLocation().add(0,0,10));select(mob,enemy);
            MythicBukkit.inst().getAPIHelper().getMythicMobInstance(mob).clearSkillCooldowns();
            String skill=type.equals("QuarrenGuard")?"quarren_attack":"twilek_attack";
            check(MythicBukkit.inst().getAPIHelper().castSkill(mob,skill,mob.getLocation(),List.of(enemy),List.of(),1),type+" starts firing pose");
            check(!mob.hasAI(),type+" suspends AI for planted firing pose");cooldown(mob,600000);
            var planted=mob.getLocation();
            later(35,()->{
                check(planted.distance(mob.getLocation())<0.2,type+" movement timer cannot slide shooter during burst");
            });
            later(50,()->{
                check(mob.hasAI(),type+" automatically restores AI after animation");
                enemy.teleport(mob.getLocation().add(0,0,10));
                var resumed=mob.getLocation();
                later(35,()->{
                    check(resumed.distance(mob.getLocation())>0.5,type+" resumes repositioning after firing");
                    targets.remove(mob);var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(mob);active.getThreatTable().dropCombat();active.setTarget(null);mob.setTarget(null);enemy.remove();
                    check(!active.getThreatTable().inCombat(),type+" target loss exits combat");mob.remove();next.run();
                });
            });
        });
    }
    private void cooldown(Mob mob,long duration){var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(mob);
        for(String name:List.of("quarren_attack","twilek_attack"))active.setSkillCooldownMillis((io.lumine.mythic.core.skills.AbstractSkill)MythicBukkit.inst().getSkillManager().getSkill(name).orElseThrow(),duration);}
    private Mob spawn(String type,Location at) throws Exception{var e=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob(type,at,1);entities.add(e);e.setRemoveWhenFarAway(false);MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e).setDespawnMode(io.lumine.mythic.core.mobs.DespawnMode.NEVER);return e;}
    private Cow target(Location at){var e=world.spawn(at,Cow.class,c->{c.setAI(false);c.setGravity(false);});entities.add(e);return e;}
    private void select(Mob e,LivingEntity t){targets.put(e,t);e.setTarget(t);var api=MythicBukkit.inst().getAPIHelper();api.addThreat(e,t,100);api.getMythicMobInstance(e).setTarget(BukkitAdapter.adapt(t));}
    private interface Check{void run() throws Exception;}
    private void later(long d,Check r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"JABBA AI PROBE FAILED",e);Bukkit.getScheduler().cancelTasks(this);cleanup();}},d);}
    private void check(boolean ok,String m){if(!ok)throw new AssertionError(m);checks++;getLogger().info("PASS: "+m);}
    private void cleanup(){HandlerList.unregisterAll(observer);entities.forEach(Entity::remove);entities.clear();blocks.forEach(b->b.update(true,false));blocks.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
    @Override public void onDisable(){cleanup();}
}
