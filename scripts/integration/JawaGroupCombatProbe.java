package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.jawa.JawaCategoryGroups;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import me.deecaad.core.compatibility.HitBox;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.weaponevents.*;

/** Temporary native fixtures. Restart once after PHASE ONE; remove probe after PHASE TWO. */
public final class JawaGroupCombatProbe extends JavaPlugin {
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();
    private final List<LivingEntity> entities=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();
    private List<Object> worldPlayers;private Map<UUID,Object> online;private MandoPlugin mando;private World world;
    private Player buyer,victim;private List<LivingEntity> group,legacy,admin;private Cow target;private BukkitTask aim;
    private int checks,combatStart;private boolean keepFixtures,cancelVendor,cancelBukkit;
    private final Map<UUID,List<Integer>> shots=new HashMap<>();private final Listener observer=new Listener(){};
    private final NamespacedKey groupKey=new NamespacedKey("mandomc","jawa_group_v1"),categoryKey=new NamespacedKey("mandomc","jawa_category_v1");
    private final NamespacedKey fixture=new NamespacedKey("mandomc","jawa_group_probe_v1");
    private java.io.File state(){return new java.io.File(getDataFolder(),"restart.yml");}
    @Override public void onEnable(){later(120,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando.jawas()!=null,"Jawa service enabled");
        world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        for(int x=3;x<=6;x++)for(int z=3;z<=5;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
        if(state().exists()){restart();return;}
        var level=world.getClass().getMethod("getHandle").invoke(world);worldPlayers=(List<Object>)level.getClass().getMethod("players").invoke(level);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        buyer=actor(new Location(world,67.5,200,67.5));victim=actor(new Location(world,67.5,200,73.5));
        group=mando.jawas().spawnGroup(List.of(new Location(world,68.5,200,68.5),new Location(world,70.5,200,68.5),new Location(world,72.5,200,68.5),new Location(world,74.5,200,68.5)));track(group);
        var second=mando.jawas().spawnGroup(List.of(new Location(world,80.5,200,68.5),new Location(world,82.5,200,68.5)));track(second);
        legacy=new ArrayList<>();String oldGroup=UUID.randomUUID().toString();
        for(int i=0;i<3;i++){var e=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob("JawaBrown",new Location(world,80.5+i*2,200,72.5),1);e.getPersistentDataContainer().set(groupKey,PersistentDataType.STRING,oldGroup);e.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,"saplings");legacy.add(e);}track(legacy);
        mando.jawas().loaded(new org.bukkit.event.world.EntitiesLoadEvent(legacy.getFirst().getLocation().getChunk(),new ArrayList<Entity>(legacy)));
        var before=new HashSet<UUID>();world.getLivingEntities().forEach(e->before.add(e.getUniqueId()));
        var permission=buyer.addAttachment(this,"mandomc.admin.status",true);check(Bukkit.dispatchCommand(buyer,"mmc jawa brown"),"native administrator spawn command dispatched");buyer.removeAttachment(permission);
        admin=world.getLivingEntities().stream().filter(e->!before.contains(e.getUniqueId())&&mando.jawas().brown(e)).toList();track(admin);check(admin.size()==2,"admin command creates two grouped traders");
        try{mando.jawas().spawnGroup(Collections.nCopies(mando.jawas().settings().categories().size()+1,new Location(world,70,200,70)));throw new AssertionError("oversized group accepted");}catch(IllegalArgumentException expected){check(true,"oversized group rejected before spawning");}
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{var event=(WeaponShootEvent)e;if(group.contains(event.getShooter())){shots.computeIfAbsent(event.getShooter().getUniqueId(),k->new ArrayList<>()).add(Bukkit.getCurrentTick());event.getProjectile().remove();}},this,true);
        Bukkit.getPluginManager().registerEvent(WeaponDamageEntityEvent.class,observer,EventPriority.HIGH,(l,e)->{if(cancelVendor)((WeaponDamageEntityEvent)e).setCancelled(true);},this,false);
        Bukkit.getPluginManager().registerEvent(EntityDamageByEntityEvent.class,observer,EventPriority.HIGH,(l,e)->{if(cancelBukkit && ((EntityDamageByEntityEvent)e).getEntity()==victim)((EntityDamageByEntityEvent)e).setCancelled(true);},this,false);
        later(30,()->{
            verifyCategories(group);verifyCategories(second);verifyCategories(legacy);verifyCategories(admin);
            check(legacy.stream().filter(e->"saplings".equals(mando.jawas().category(e).id())).count()==1,"legacy duplicate preference retained once, remaining members repaired");
            var ledger=new JawaCategoryGroups(mando.getDataFolder().toPath().resolve("data/jawa-category-groups.yml"));
            for(var entity:entities)check(mando.jawas().category(entity).id().equals(ledger.category(entity.getPersistentDataContainer().get(groupKey,PersistentDataType.STRING),entity.getUniqueId())),"native group has durable reservation");
            menu();fire();
        });
    }
    private void track(List<LivingEntity> values)throws Exception{for(var e:values){entities.add(e);new net.mandomc.integrations.mobs.MythicMobAccess().persist(e);getLogger().info("FIXTURE GROUP "+e.getPersistentDataContainer().get(groupKey,PersistentDataType.STRING));e.setGravity(false);e.setAI(false);e.setPersistent(true);e.setRemoveWhenFarAway(false);e.getPersistentDataContainer().set(fixture,PersistentDataType.BYTE,(byte)1);}}
    private void verifyCategories(List<LivingEntity> values){var ids=new HashSet<String>();for(var e:values){var category=mando.jawas().category(e);check(category!=null,"group category ready");check(ids.add(category.id()),"group member category is unique");check(category.id().equals(e.getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING)),"entity category matches reservation");}}
    private void menu()throws Exception{
        var first=group.getFirst();position(buyer,first.getLocation().clone().add(0,0,-2));mando.jawas().open(buyer,first);
        var holder=buyer.getOpenInventory().getTopInventory().getHolder();check(holder!=null && holder.getClass().getName().contains("JawaTraders$Menu"),"native category shop opens");
        var field=holder.getClass().getDeclaredField("offers");field.setAccessible(true);var offers=(Map<?,?>)field.get(holder);
        check(new HashSet<>(offers.values()).equals(new HashSet<>(mando.jawas().category(first).offers())),"shop contains exactly its complete assigned category");buyer.closeInventory();
    }
    private void fire(){
        target=world.spawn(new Location(world,71,200,79),Cow.class,c->{c.setAI(false);c.setGravity(false);c.setInvulnerable(true);c.setPersistent(false);});entities.add(target);combatStart=Bukkit.getCurrentTick();
        aim=Bukkit.getScheduler().runTaskTimer(this,()->{for(var e:group){((Mob)e).setTarget(target);var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e);active.setTarget(BukkitAdapter.adapt(target));active.getThreatTable().threatGain(BukkitAdapter.adapt(target),100);}},0,1);
        later(80,()->{for(var e:group){var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e);getLogger().info("ATTACK STATE "+e.getUniqueId()+" target="+((Mob)e).getTarget()+" activeTarget="+active.hasTarget()+" combat="+active.getThreatTable().inCombat()+" los="+e.hasLineOfSight(target)+" allowed="+mando.territories().combat(e.getLocation()));}});
        later(315,()->{
            aim.cancel();var firsts=new HashSet<Integer>();var cadences=new HashSet<List<Integer>>();
            for(var e:group){var times=shots.getOrDefault(e.getUniqueId(),List.of());getLogger().info("BURSTS "+e.getUniqueId()+" "+times.stream().map(t->t-combatStart).toList());check(times.size()>=6,"each actual Jawa fires at least three paired bursts");var intervals=new ArrayList<Integer>();firsts.add(times.getFirst());
                for(int i=0;i+1<times.size();i+=2){check(times.get(i+1)-times.get(i)==1,"paired blaster shots stay one tick apart");if(i>=2){int gap=times.get(i)-times.get(i-2);check(gap>=60&&gap<=80,"actual inter-burst cadence stays within jitter bounds");intervals.add(gap);}}cadences.add(intervals);((Mob)e).setTarget(null);MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e).getThreatTable().dropCombat();}
            check(firsts.size()>1,"group first volleys are staggered");check(cadences.size()>1,"subsequent burst schedules vary independently");ion();
        });
    }
    private void ion()throws Exception{
        position(buyer,new Location(world,66.5,200,73.5));position(victim,new Location(world,69.5,200,73.5));
        shot(buyer,"Jawa_Ionization_Blaster");later(2,()->{
            var effect=mando.jawas().ionization();check(effect.held(victim),"real player ion projectile applies restraint after health loss");check(effect.dust().getColor().equals(Color.fromRGB(45,120,255)),"impact uses blue dust");
            var from=victim.getLocation();var move=new PlayerMoveEvent(victim,from,from.clone().add(1,-1,0));Bukkit.getPluginManager().callEvent(move);check(!move.isCancelled(),"airborne hit allows horizontal movement and falling");
            var velocity=new PlayerVelocityEvent(victim,new Vector(.4,-.5,.2));Bukkit.getPluginManager().callEvent(velocity);check(velocity.getVelocity().equals(new Vector(.4,-.5,.2)),"airborne velocity untouched");
            int y=world.getHighestBlockYAt(69,73)+1;position(victim,new Location(world,69.5,y,73.5));check(!victim.getLocation().getBlock().getType().isSolid(),"root fixture uses existing walkable surface");
            var landing=victim.getLocation();var walking=new PlayerMoveEvent(victim,landing,landing.clone().add(1,0,0));walking.getTo().setYaw(83);walking.getTo().setPitch(19);Bukkit.getPluginManager().callEvent(walking);check(walking.isCancelled(),"landing roots for remaining hit duration");check(walking.getFrom().getYaw()==83&&walking.getFrom().getPitch()==19,"ground restraint preserves camera look");
            var look=new PlayerMoveEvent(victim,landing,landing.clone());look.getTo().setYaw(30);Bukkit.getPluginManager().callEvent(look);check(!look.isCancelled(),"rotation-only movement remains free");
            var groundVelocity=new PlayerVelocityEvent(victim,new Vector(.4,.3,.2));Bukkit.getPluginManager().callEvent(groundVelocity);check(groundVelocity.getVelocity().equals(new Vector()),"ground root blocks horizontal movement and jumping");
            later(42,()->{check(!effect.held(victim),"ion restraint expires after two seconds");position(victim,new Location(world,69.5,200,73.5));position(buyer,new Location(world,66.5,200,73.5));shot(group.getFirst(),"Jawa_Ionization_Blaster");later(2,this::refresh);});
        });
    }
    private void refresh()throws Exception{
        var effect=mando.jawas().ionization();check(effect.held(victim),"actual Jawa-owned ion projectile applies restraint");
        later(18,()->{shot(buyer,"Jawa_Ionization_Blaster");later(2,()->{check(effect.remaining(victim)>=37&&effect.remaining(victim)<=40,"second real hit refreshes rather than accumulates duration");
            mando.combat().clear(victim);
            var tele=new PlayerTeleportEvent(victim,victim.getLocation(),victim.getLocation().add(3,0,0),PlayerTeleportEvent.TeleportCause.PLUGIN);Bukkit.getPluginManager().callEvent(tele);check(!tele.isCancelled()&&!effect.held(victim),"successful teleport clears restraint");
            cancelVendor=true;shot(buyer,"Jawa_Ionization_Blaster");cancelVendor=false;later(2,()->{check(!effect.held(victim),"cancelled WeaponMechanics damage cannot root");cancelBukkit=true;shot(buyer,"Jawa_Ionization_Blaster");cancelBukkit=false;later(2,()->{check(!effect.held(victim),"cancelled native damage cannot root");shot(buyer,"E11");later(2,()->{check(!effect.held(victim),"other blasters do not ionize");lifecycle();});});});
        });});
    }
    private void lifecycle()throws Exception{
        mando.combat().clear(victim);
        var effect=mando.jawas().ionization();
        victim.setInvulnerable(true);effect.hit(victim,Bukkit.getCurrentTick());check(!effect.held(victim),"immune player does not root");victim.setInvulnerable(false);
        victim.setGameMode(GameMode.CREATIVE);effect.hit(victim,Bukkit.getCurrentTick());check(!effect.held(victim),"creative player does not root");victim.setGameMode(GameMode.SURVIVAL);
        effect.hit(victim,Bukkit.getCurrentTick());
        var mount=new EntityMountEvent(victim,target);Bukkit.getPluginManager().callEvent(mount);check(mount.isCancelled(),"root cannot be bypassed by mounting");
        var cancelled=new PlayerTeleportEvent(victim,victim.getLocation(),victim.getLocation().add(1,0,0),PlayerTeleportEvent.TeleportCause.PLUGIN);cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);check(effect.held(victim),"cancelled teleport retains remaining root");
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(victim,world));check(!effect.held(victim),"world change clears root");
        effect.hit(victim,Bukkit.getCurrentTick());check(effect.held(victim),"live fixture is rooted before logout cleanup");Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(victim,net.kyori.adventure.text.Component.empty()));check(!effect.held(victim),"logout clears root");
        effect.hit(victim,Bukkit.getCurrentTick());check(effect.held(victim),"live fixture is rooted before game mode cleanup");victim.setGameMode(GameMode.CREATIVE);check(!effect.held(victim),"game mode change clears root");victim.setGameMode(GameMode.SURVIVAL);
        // A teleport between real damage and deferred confirmation must invalidate that pending hit.
        shot(buyer,"Jawa_Ionization_Blaster");mando.combat().clear(victim);Bukkit.getPluginManager().callEvent(new PlayerTeleportEvent(victim,victim.getLocation(),victim.getLocation().add(1,0,0),PlayerTeleportEvent.TeleportCause.PLUGIN));
        later(2,()->{check(!effect.held(victim),"pending damage cannot reapply root after a teleport");finish();});
    }
    private void shot(LivingEntity shooter,String title){
        victim.setNoDamageTicks(0);victim.setHealth(20);victim.setAbsorptionAmount(0);victim.setInvulnerable(false);
        var weapon=WeaponMechanicsAPI.generateWeapon(title);var definition=WeaponMechanics.getInstance().getWeaponConfigurations().getObject(title+".Projectile",Projectile.class);
        var origin=shooter.getLocation().clone().add(0,.5,0);var direction=victim.getLocation().clone().add(0,.5,0).toVector().subtract(origin.toVector()).normalize();
        var p=definition.create(shooter,origin,direction,weapon,title,EquipmentSlot.HAND);try{var hit=HitBox.getHitbox(victim).rayTrace(origin.toVector(),direction);check(hit!=null,"real projectile intersects player hitbox");WeaponMechanics.getInstance().getWeaponHandler().getHitHandler().handleHit(hit,p);}finally{p.remove();}
    }
    private void finish()throws Exception{
        var saved=new YamlConfiguration();saved.set("checks",checks);var ids=new ArrayList<String>();
        for(var e:entities)if(e.getPersistentDataContainer().has(fixture)){String id=e.getUniqueId().toString();ids.add(id);saved.set("members."+id+".group",e.getPersistentDataContainer().get(groupKey,PersistentDataType.STRING));saved.set("members."+id+".category",mando.jawas().category(e).id());}
        saved.set("ids",ids);getDataFolder().mkdirs();saved.save(state());keepFixtures=true;cleanup();getLogger().info("JAWA GROUP COMBAT PHASE ONE PASSED: "+checks+" assertions; restart required for persistence check");
    }
    private void restart()throws Exception{
        var saved=YamlConfiguration.loadConfiguration(state());checks=saved.getInt("checks");
        for(String id:saved.getStringList("ids")){var e=Bukkit.getEntity(UUID.fromString(id));check(e instanceof LivingEntity && e.getPersistentDataContainer().has(fixture),"actual saved group member survives server restart");var living=(LivingEntity)e;entities.add(living);check(saved.getString("members."+id+".group").equals(e.getPersistentDataContainer().get(groupKey,PersistentDataType.STRING)),"group identity survives restart");check(saved.getString("members."+id+".category").equals(mando.jawas().category(living).id()),"assigned category survives actual restart");}
        var config=YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"mobs/jawas.yml"));
        var anchors=new HashMap<String,Location>();
        for(String id:config.getConfigurationSection("npcs").getKeys(false))if(id.startsWith("batuu_market_")){
            String path="npcs."+id;var homeWorld=Bukkit.getWorld(NamespacedKey.fromString(config.getString(path+".world")));
            var center=new Location(homeWorld,config.getDouble(path+".x"),config.getDouble(path+".y"),config.getDouble(path+".z"));anchors.put(id,center);
            var c=center.getChunk();c.addPluginChunkTicket(this);chunks.add(c);
        }
        later(12,()->{for(var entry:anchors.entrySet()){
            var matching=entry.getValue().getWorld().getNearbyEntities(entry.getValue(),2,2,2).stream().filter(e->entry.getKey().equals(e.getPersistentDataContainer().get(new NamespacedKey("mandomc","jawa_npc_v1"),PersistentDataType.STRING))).toList();
            check(matching.size()==1,"exactly one captured gray buyer exists: "+entry.getKey());var buyer=(LivingEntity)matching.getFirst();check(mando.jawas().gray(buyer)&&buyer.isInvulnerable(),"saved buyer uses invincible gray variant: "+entry.getKey());
        }cleanup();getLogger().info("JAWA GROUP COMBAT PROBE PASSED: "+checks+" assertions");});
    }
    private Player actor(Location point)throws Exception{var p=nativeTestPlayer(world,point);actors.add(p);p.setGameMode(GameMode.SURVIVAL);var h=p.getClass().getMethod("getHandle").invoke(p);handles.put(p.getUniqueId(),h);worldPlayers.add(h);online.put(p.getUniqueId(),h);getLogger().info("FIXTURE PLAYER "+p.getUniqueId());return p;}
    private void position(Player player,Location location)throws Exception{var h=handles.get(player.getUniqueId());h.getClass().getMethod("setPos",double.class,double.class,double.class).invoke(h,location.getX(),location.getY(),location.getZ());}
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;getLogger().info("PASS "+message);}
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"JAWA GROUP COMBAT PROBE FAILED",error);cleanup();}
    private void cleanup(){Bukkit.getScheduler().cancelTasks(this);HandlerList.unregisterAll(observer);for(var actor:actors){actor.closeInventory();actor.getInventory().clear();}if(worldPlayers!=null)handles.values().forEach(worldPlayers::remove);if(online!=null)handles.keySet().forEach(online::remove);for(var e:entities)if(e.isValid() && (!keepFixtures||!e.getPersistentDataContainer().has(fixture)))e.remove();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
    @Override public void onDisable(){cleanup();}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "Trade" + actors.size());
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
