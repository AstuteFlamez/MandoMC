package net.mandomc.verification;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import com.ticxo.modelengine.api.ModelEngineAPI;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.mobs.BosskDioxis;

/** Temporary isolated Bossk integration checks; no real player inventories are accessed. */
public final class BosskProbe extends JavaPlugin {
    private final List<Entity> entities=new ArrayList<>();
    private final List<Player> players=new ArrayList<>();
    private final List<BlockState> blocks=new ArrayList<>();
    private final List<Chunk> chunks=new ArrayList<>();
    private final List<Integer> shots=new ArrayList<>();
    private final Listener observer=new Listener(){};
    private net.mandomc.integrations.models.BosskMuzzle muzzle;
    private MandoPlugin plugin; private BosskDioxis gas; private World world; private Mob boss; private Cow enemy;
    private boolean playerThrow; private boolean accuracy; private final List<Double> aimErrors=new ArrayList<>();
    private int checks,start;private boolean combo;private Location moveStart;private int cloudImpact=-1;
    @Override public void onEnable(){later(120,this::begin);}
    private void begin() throws Exception {
        plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");gas=plugin.bosskDioxis();muzzle=new net.mandomc.integrations.models.BosskMuzzle();check(gas!=null,"Dioxis service initialized");
        world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for(int x=49;x<=52;x++)for(int z=49;z<=52;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
        for(int x=795;x<=825;x++)for(int z=795;z<=825;z++){var b=world.getBlockAt(x,300,z);if(!b.getType().isAir())throw new IllegalStateException("Fixture not empty sky");blocks.add(b.getState());b.setType(Material.SANDSTONE,false);}
        boss=spawn(new Location(world,800.5,301,800.5));enemy=world.spawn(new Location(world,808.5,301,800.5),Cow.class,c->{c.setAI(false);c.setGravity(false);c.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(100);c.setHealth(100);});entities.add(enemy);
        select();cooldown();
        Bukkit.getScheduler().runTaskTimer(this,()->{if(boss!=null&&boss.isValid()&&enemy!=null&&enemy.isValid())select();},1,1);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntitySpawnEvent.class,observer,EventPriority.MONITOR,(l,e)->{
            var spawned=((org.bukkit.event.entity.EntitySpawnEvent)e).getEntity();
            if(!playerThrow&&spawned instanceof ItemDisplay display&&boss!=null&&boss.isValid()&&spawned.getLocation().distance(boss.getLocation())<4) {
                var muzzlePosition=muzzle.position(boss);
                getLogger().info("MUZZLE actual="+display.getLocation()+" actor="+boss.getLocation());
                check(muzzlePosition.getY()>boss.getY()+0.6,"animated barrel launch point stays above the ground");
                check(display.getLocation().distance(muzzlePosition)<0.05,"grenade originates at animated Relby barrel tip");
                check(ModelEngineAPI.getModeledEntity(boss).getModels().get("bossk").getAnimationHandler().isPlayingAnimation("attack3"),"grenade launches during attack3");
            }
        },this,true);
        Bukkit.getScheduler().runTaskTimer(this,()->{if(cloudImpact<0&&gas.activeClouds()>0)cloudImpact=Bukkit.getCurrentTick();},1,1);
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{
            var shot=(WeaponShootEvent)e;if(shot.getShooter()!=boss)return;shot.getProjectile().remove();
            check(shot.getProjectile().getIntTag(net.mandomc.integrations.weapons.BosskShotDamage.STYLE)>0,"Bossk projectile snapshots its attack damage profile");
            if(accuracy) {
                var motion=shot.getProjectile().getMotion();var origin=shot.getProjectile().getLocation();
                var center=enemy.getLocation().add(0,enemy.getHeight()*0.6,0);
                double travel=origin.distance(center.toVector())/motion.length();var predicted=center.clone().add(0.15*travel,0,0);
                var future=org.bukkit.util.BoundingBox.of(predicted,0.3,0.5,0.3);
                check(future.rayTrace(origin,motion.clone().normalize(),40)!=null,"accurate single-shot trajectory intersects moving target prediction");
                float expected=boss.getLocation().setDirection(predicted.toVector().subtract(boss.getEyeLocation().toVector())).getYaw();
                var model=ModelEngineAPI.getModeledEntity(boss).getModels().get("bossk");
                check(Math.abs(Math.IEEEremainder(model.getYBodyRot()-expected,360))<10,"visible Bossk body faces moving target at shot emission");
            }
            check(shot.getWeaponTitle().equals("RelbyV10"),"Bossk fires actual Relby V-10 projectile");shots.add(Bukkit.getCurrentTick()-start);
        },this,true);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDeathEvent.class,observer,EventPriority.HIGHEST,(l,e)->{
            var death=(org.bukkit.event.entity.EntityDeathEvent)e;if(entities.contains(death.getEntity())){death.getDrops().clear();death.setDroppedExp(0);}
        },this,true);
        later(40,()->{
            var model=ModelEngineAPI.getModeledEntity(boss);check(model!=null&&model.getModels().containsKey("bossk"),"original Bossk model attached");check(!model.isBaseEntityVisible(),"native zombie hidden");
            check(boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()==800,"Bossk health increased to 800");
            for(var a:ModelEngineAPI.getBlueprint("bossk").getAnimations().values())if(a.getName().startsWith("attack"))check(a.getLoopMode().name().equals("ONCE"),a.getName()+" finishes cleanly");
            var rifle=plugin.items().create("relby_v10");check("RelbyV10".equals(WeaponMechanicsAPI.getWeaponTitle(rifle)),"catalog generates real player rifle");check(rifle.getItemMeta().getItemModel().toString().equals("mandomc:blasters/relbyv10"),"rifle uses current item-model");
            check(me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations().getInt("RelbyV10.Shoot.Delay_Between_Shots")>0,"player rifle has valid supported shot delay");
            drops();
            netheriteBurst();
            boss.teleport(new Location(world,810.5,301,810.5));enemy.teleport(boss.getLocation().add(0,0,8));
            select();start=Bukkit.getCurrentTick();cast("BosskGrenade");cooldown();
            later(28,()->check(gas.activeFlights()==1,"muzzle grenade visibly travels before impact"));
            later(47,()->{
                check(gas.activeFlights()==0&&gas.activeClouds()==1,"grenade lands and creates one poison cloud");

                check(boss.getHealth()==boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),"own grenade does not damage Bossk");
                check(!boss.hasPotionEffect(PotionEffectType.POISON),"own cloud cannot poison Bossk");
                statusCallbacks();
                later(55,()->{
                    check(shots.size()==1,"attack4 grenade follow-up fires exactly once: "+shots);
                    check(shots.getFirst()>=76&&shots.getFirst()<=82,"grenade has time to act before single shot");
                    later(75,this::burst);
                });
            });
        });
    }
    private void statusCallbacks() throws Exception {
        var p=nativeTestPlayer(world,enemy.getLocation());players.add(p);p.setGameMode(GameMode.SURVIVAL);
        var center=enemy.getLocation().clone().add(0,0.15,0);
        var eligible=BosskDioxis.class.getDeclaredMethod("eligible",Player.class,Location.class);eligible.setAccessible(true);
        check((boolean)eligible.invoke(gas,p,center),"exposed survival player in gas is eligible");p.setGameMode(GameMode.CREATIVE);check(!(boolean)eligible.invoke(gas,p,center),"creative player unaffected");p.setGameMode(GameMode.SURVIVAL);
        var safe=nativeTestPlayer(world,world.getSpawnLocation());players.add(safe);check(!(boolean)eligible.invoke(gas,safe,safe.getLocation()),"protected landing cannot receive gas");
        var cloudClass=Class.forName("net.mandomc.gameplay.mobs.BosskDioxis$Cloud");var constructor=cloudClass.getDeclaredConstructor(UUID.class,Location.class,int.class);constructor.setAccessible(true);
        var cloud=constructor.newInstance(boss.getUniqueId(),center,Bukkit.getCurrentTick()+120);
        var expose=BosskDioxis.class.getDeclaredMethod("expose",cloudClass,Player.class,int.class);expose.setAccessible(true);
        double hp=p.getHealth();expose.invoke(gas,cloud,p,Bukkit.getCurrentTick());
        check(gas.stunned(p),"gas applies bounded movement stun");check(p.hasPotionEffect(PotionEffectType.POISON),"gas applies actual lingering poison");check(plugin.combat().tagged(p),"gas tags exposed player for combat");
        check(p.getHealth()==hp,"applying stun/poison does not emit fake real damage");
        var from=p.getLocation();var to=from.clone().add(2,1,0);to.setYaw(80);to.setPitch(30);var move=new PlayerMoveEvent(p,from,to);gas.move(move);
        check(move.isCancelled()&&move.getFrom().toVector().equals(from.toVector()),"stunned movement and jumping remain at anchor");check(move.getFrom().getYaw()==80&&move.getFrom().getPitch()==30,"stun allows looking around");
        p.playHurtAnimation(0);p.playHurtAnimation(0);check(p.getHealth()==hp,"hurt animation packets do not remove health");
        var tp=new PlayerTeleportEvent(p,from,from.clone().add(0,0,10),PlayerTeleportEvent.TeleportCause.PLUGIN);gas.teleport(tp);check(!gas.stunned(p),"external teleport releases stun");
        meleeKnockback();
        var grenade=plugin.items().create("dioxis_grenade");grenade.setAmount(2);p.getInventory().setItemInMainHand(grenade);
        var use=new PlayerInteractEvent(p,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,grenade,null,org.bukkit.block.BlockFace.SELF,org.bukkit.inventory.EquipmentSlot.HAND);
        int flights=gas.activeFlights();playerThrow=true;gas.use(use);
        check(p.getInventory().getItemInMainHand().getAmount()==1&&gas.activeFlights()==flights+1,"right click consumes exactly one grenade and starts flight");
        gas.use(use);check(p.getInventory().getItemInMainHand().getAmount()==1&&gas.activeFlights()==flights+1,"cooldown does not consume or double-launch grenades");playerThrow=false;
        plugin.combat().clear(p);
    }
    private void combo() throws Exception {
        check(gas.activeClouds()==0,"cloud expires after bounded lifetime");
        boss.teleport(new Location(world,810.5,301,810.5));
        enemy.teleport(boss.getLocation().add(0,0,5.5));enemy.setNoDamageTicks(0);double before=enemy.getHealth();var lungeStart=boss.getLocation();select();shots.clear();start=Bukkit.getCurrentTick();cast("BosskCloseCombo");cooldown();
        later(2,()->enemy.teleport(lungeStart.clone().add(5.5,0,0)));
        later(22,()->{
            check(boss.getX()-lungeStart.getX()>1.5,"lunge follows a target that changes sides during windup");
            check(lungeStart.distance(boss.getLocation())>1.5,"swipe physically lunges toward target from 5.5 blocks: "+lungeStart.distance(boss.getLocation()));
            check(Math.abs(before-enemy.getHealth()-9)<0.01,"lunging swipe reaches and hits target from former miss range");
        });
        later(68,()->check(gas.activeFlights()==1,"combo follows swipe with grenade release"));
        later(75,()->moveStart=boss.getLocation());
        later(95,()->check(moveStart.distance(boss.getLocation())>0.5,"Bossk quickly relocates after grenade launch before aiming"));
        later(150,()->{
            check(shots.size()==1,"combo finishes with one attack4 shot: "+shots);check(shots.getFirst()>=117&&shots.getFirst()<=124,"gas acts before relocated single shot");
            later(15,()->{
                check(boss.hasAI(),"combo automatically restores native AI");
                boss.teleport(new Location(world,810.5,301,810.5));
                enemy.teleport(boss.getLocation().add(0,0,12));select();cooldown();var p=boss.getLocation();
                later(35,()->{
                    check(boss.getLocation().distance(p)>0.5,"Bossk repositions between attacks");
                    boss.remove();later(2,()->{
                        check(gas.activeFlights()==0&&gas.activeClouds()==0,"Bossk removal clears owned active hazards");
                        getLogger().info("BOSSK PROBE PASSED: "+checks+" assertions");cleanup();
                    });
                });
            });
        });
    }
    private void netheriteBurst() throws Exception {
        var victim=nativeTestPlayer(world,boss.getLocation().add(0,0,5));players.add(victim);victim.setGameMode(GameMode.SURVIVAL);
        victim.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[]{
            new org.bukkit.inventory.ItemStack(Material.NETHERITE_BOOTS),new org.bukkit.inventory.ItemStack(Material.NETHERITE_LEGGINGS),
            new org.bukkit.inventory.ItemStack(Material.NETHERITE_CHESTPLATE),new org.bukkit.inventory.ItemStack(Material.NETHERITE_HELMET)});
        // Unregistered fixture players do not run equipment ticks: install the same vanilla defensive totals.
        if(victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue()==0)victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(20);
        if(victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).getValue()==0)victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).setBaseValue(12);
        check(victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue()==20,"fixture has full netherite armor protection");
        var wm=me.deecaad.weaponmechanics.WeaponMechanics.getInstance();
        var def=wm.getWeaponConfigurations().getObject("RelbyV10.Projectile",me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile.class);
        var weapon=plugin.items().create("relby_v10");
        for(int i=0;i<6;i++) {
            victim.setNoDamageTicks(0);
            var start=victim.getLocation().add(0,1.75,-5);var direction=new org.bukkit.util.Vector(0,0,1);
            var bolt=def.create(boss,start,direction,weapon,"RelbyV10",org.bukkit.inventory.EquipmentSlot.HAND);
            try {var box=me.deecaad.core.compatibility.HitBox.getHitbox(victim.getBoundingBox());box.setLivingEntity(victim);
                var hit=box.rayTrace(start.toVector(),direction);check(hit!=null,"netherite fixture receives head-height shot");
                double before=victim.getHealth();wm.getWeaponHandler().getHitHandler().handleHit(hit,bolt);
                check(victim.getHealth()<before,"actual nerfed Relby shot damages netherite without immunity masking it");
            }finally{bolt.remove();}
        }
        check(victim.getHealth()>10,"six close head-height hits leave over half netherite health: "+victim.getHealth());
        for(int style:List.of(1,2)) {
            victim.setHealth(20);victim.setNoDamageTicks(0);double before=victim.getHealth();
            for(int i=0;i<(style==1?1:3);i++) {
                var start=victim.getLocation().add(0,1.1,-5);var direction=new org.bukkit.util.Vector(0,0,1);
                var bolt=def.create(boss,start,direction,weapon,"RelbyV10",org.bukkit.inventory.EquipmentSlot.HAND);
                bolt.setIntTag(net.mandomc.integrations.weapons.BosskShotDamage.STYLE,style);
                try{var box=me.deecaad.core.compatibility.HitBox.getHitbox(victim.getBoundingBox());box.setLivingEntity(victim);
                    var hit=box.rayTrace(start.toVector(),direction);check(hit!=null,"Bossk profile shot intersects armored player");
                    wm.getWeaponHandler().getHitHandler().handleHit(hit,bolt);
                }finally{bolt.remove();}
            }
            double damage=before-victim.getHealth();
            check(Math.abs(damage-(style==1?6:8))<0.01,"netherite health loss matches "+(style==1?"three-heart single":"four-heart full burst")+": "+damage);
        }
        plugin.combat().clear(victim);
    }
    private void meleeKnockback() throws Exception {
        var actor=nativeTestPlayer(world,boss.getLocation().add(0,0,2));players.add(actor);
        Player proxy=(Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(p,m,args)->{
            if(m.getName().equals("isValid")||m.getName().equals("isOnline"))return true;
            return m.invoke(actor,args);
        });
        enemy.teleport(boss.getLocation().add(0,0,2));enemy.setNoDamageTicks(0);select();cast("BosskSwipeHit");cooldown();
        var hit=new org.bukkit.event.entity.EntityDamageByEntityEvent(boss,proxy,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,3);
        for(var listener:org.bukkit.event.entity.EntityDamageEvent.getHandlerList().getRegisteredListeners())
            if(listener.getListener() instanceof net.mandomc.integrations.mobs.BosskCombat)listener.callEvent(hit);
        later(2,()->{
            check(actor.getVelocity().length()>1&&actor.getVelocity().getY()>0.3,"connected swipe launches player back and upward");
            actor.setVelocity(new org.bukkit.util.Vector());
            var denied=new org.bukkit.event.entity.EntityDamageByEntityEvent(boss,proxy,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,3);denied.setCancelled(true);
            for(var listener:org.bukkit.event.entity.EntityDamageEvent.getHandlerList().getRegisteredListeners())
                if(listener.getListener() instanceof net.mandomc.integrations.mobs.BosskCombat)listener.callEvent(denied);
            later(2,()->check(actor.getVelocity().lengthSquared()==0,"cancelled swipe cannot launch the player"));
        });
    }
    private void accuracy() throws Exception {
        enemy.teleport(boss.getLocation().add(0,0,16));select();
        boss.setRotation(180,0);ModelEngineAPI.getModeledEntity(boss).setYBodyRotImmediately(180);
        accuracy=true;shots.clear();cast("BosskAim");cooldown();
        later(3,()->{
            var model=ModelEngineAPI.getModeledEntity(boss).getModels().get("bossk");
            check(!model.getAnimationHandler().isPlayingAnimation("attack4"),"Bossk turns before starting the attack4 animation");
            check(Math.abs(Math.IEEEremainder(model.getYBodyRot()-180,360))<150,"pre-attack turn does not instantly snap 180 degrees");
        });
        var movement=Bukkit.getScheduler().runTaskTimer(this,()->{if(enemy.isValid())enemy.teleport(enemy.getLocation().add(0.15,0,0));},1,1);
        later(55,()->{movement.cancel();accuracy=false;check(shots.size()==1,"moving-target attack4 emits exactly one accurate shot");combo();});
    }
    private void drops() {
        var mob=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss);Set<Integer> amounts=new HashSet<>();
        for(int i=0;i<1000;i++){
            var event=new io.lumine.mythic.bukkit.events.MythicMobDeathEvent(mob,null,new ArrayList<>());Bukkit.getPluginManager().callEvent(event);
            if(event.getDrops().size()!=1)throw new AssertionError("Unexpected Bossk adapter drop count");
            var item=event.getDrops().getFirst();if(!"dioxis_grenade".equals(plugin.items().identify(item))||item.getAmount()<1||item.getAmount()>3)throw new AssertionError("Wrong grenade death loot");amounts.add(item.getAmount());
        }
        check(amounts.equals(Set.of(1,2,3)),"Bossk consistently drops exactly 1–3 usable catalog grenades");
    }
    private void burst() throws Exception {
        enemy.teleport(boss.getLocation().add(0,0,10));select();shots.clear();start=Bukkit.getCurrentTick();cast("BosskBurst");cooldown();
        later(60,()->{check(shots.size()==3,"attack2 fires exactly three shots: "+shots);accuracy();});
    }
    private Mob spawn(Location loc) throws Exception {var mob=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob("Bossk",loc,1);entities.add(mob);mob.setRemoveWhenFarAway(false);MythicBukkit.inst().getAPIHelper().getMythicMobInstance(mob).setDespawnMode(io.lumine.mythic.core.mobs.DespawnMode.NEVER);return mob;}
    private void select(){boss.setTarget(enemy);var a=MythicBukkit.inst().getAPIHelper();a.addThreat(boss,enemy,100);a.getMythicMobInstance(boss).setTarget(BukkitAdapter.adapt(enemy));}
    private void cast(String name){MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss).clearSkillCooldowns();check(MythicBukkit.inst().getAPIHelper().castSkill(boss,name,boss.getLocation(),List.of(enemy),List.of(),1),"cast "+name);}
    private void cooldown(){var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss);for(String s:List.of("BosskCloseCombo","BosskRangedBrain"))active.setSkillCooldownMillis((io.lumine.mythic.core.skills.AbstractSkill)MythicBukkit.inst().getSkillManager().getSkill(s).orElseThrow(),600000);}
    private interface Check{void run() throws Exception;}
    private void later(long d,Check c){Bukkit.getScheduler().runTaskLater(this,()->{try{c.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BOSSK PROBE FAILED",e);Bukkit.getScheduler().cancelTasks(this);cleanup();}},d);}
    private void check(boolean ok,String m){if(!ok)throw new AssertionError(m);checks++;getLogger().info("PASS: "+m);}
    private void cleanup(){HandlerList.unregisterAll(observer);entities.forEach(Entity::remove);entities.clear();if(plugin!=null)players.forEach(plugin.combat()::clear);players.clear();blocks.forEach(b->b.update(true,false));blocks.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
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
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "MobTest");
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
