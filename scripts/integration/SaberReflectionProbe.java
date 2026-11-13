package net.mandomc.verification;

import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.*;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent;
import me.deecaad.weaponmechanics.weapon.damage.ProjectileDamageSource;
import net.mandomc.bootstrap.MandoPlugin;

/** Disposable native Paper collision + vendor render/disguise lifecycle regression. */
public final class SaberReflectionProbe extends JavaPlugin implements Listener {
    private MandoPlugin mm;
    private int checks, scenario;
    private final float[][] aims={{0,0},{65,-32},{-130,45},{20,-90},{-40,90}};
    private Player defender;
    private LivingEntity shooter,target,behind;
    private WeaponProjectile original,reflected;
    private Vector aim,renderStart,rayStart;
    private double targetHealth,impactSpeed;
    private int reflectedCount,targetHits,defenderHits,behindProjectileHits,protectionDenied;
    private boolean failed,protectionTest;
    private final List<WeaponProjectile> bolts=new ArrayList<>();
    private Map<UUID,Object> playerMap;
    private Object nativeHandle;
    @Override public void onEnable(){
        if(getServer().getPort()!=25641)throw new IllegalStateException("SaberReflectionProbe requires a disposable server on port 25641");
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
        mm=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        Bukkit.getPluginManager().registerEvents(this,this);
        new ProjectileScriptManager(this){@Override public void attach(AProjectile p){
            if(p instanceof WeaponProjectile debug){p.addProjectileScript(new ProjectileScript<AProjectile>(SaberReflectionProbe.this,p){
                @Override public void onCollide(me.deecaad.core.utils.ray.RayTraceResult r){getLogger().info("COLLISION "+debug.getIntTag("mandomc_saber_reflections")+" "+r);}
            });}
            if(p instanceof WeaponProjectile b && b.getIntTag("mandomc_saber_reflections")>0){
                reflected=b;bolts.add(b);reflectedCount++;
                check(b.getDisguise()!=null,"vendor creates visible laser disguise");
                check(b.getShooter()==defender,"defender owns scheduled reflection");
                check(b.getWeaponTitle().equals("E11"),"weapon damage identity retained");
                check(snapshot(b.getProjectileSettings()).equals(snapshot(original.getProjectileSettings())),"incoming projectile settings values retained");
                check(snapshot(b.getThrough()).equals(snapshot(original.getThrough())) && snapshot(b.getBouncy()).equals(snapshot(original.getBouncy())) && snapshot(b.getSticky()).equals(snapshot(original.getSticky())),"projectile behavior values retained");
                check(Math.abs(b.getMotion().length()-impactSpeed)<1e-8,"impact speed retained");
                renderStart=new Vector(b.getDisguise().getX(),b.getDisguise().getY(),b.getDisguise().getZ());
                rayStart=b.getLocation();
            }
        }}.register();
        next();
    }),60);}
    @EventHandler(priority=EventPriority.MONITOR) public void hit(WeaponDamageEntityEvent e){
        if(protectionTest && e.isCancelled())protectionDenied++;
        if(!(e.getSource() instanceof ProjectileDamageSource s))return;
        if(e.getVictim()==behind && !e.isCancelled())behindProjectileHits++;
        if(e.getVictim()==defender){impactSpeed=s.getProjectile().getMotion().length();defenderHits++;check(e.isCancelled(),"original defender collision cancelled");}
        if(e.getVictim()==target && !protectionTest){targetHits++;check(!e.isCancelled(),"reflected target hit permitted");check(s.getProjectile().getShooter()==defender,"actual damaging collision attributed to defender");}
    }
    private void next() throws Exception {
        cleanupCase();
        if(scenario==aims.length){
            protectedDamage();
            var safe=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation();
            check(!mm.sabers().allowed(safe),"existing safezone blocks saber combat");
            getLogger().info("REFLECTION PROBE PASSED: "+checks+" assertions; 5 native moving/collision/render cases");
            Bukkit.shutdown();return;
        }
        reflected=null;reflectedCount=targetHits=defenderHits=behindProjectileHits=0;renderStart=rayStart=null;
        var world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        var location=new Location(world,80,200,80,aims[scenario][0],aims[scenario][1]);
        world.getChunkAt(location).addPluginChunkTicket(this);
        defender=nativeTestPlayer(world,location);
        defender.setGravity(false);defender.setGameMode(GameMode.SURVIVAL);defender.setInvulnerable(false);
        defender.getInventory().setItemInMainHand(mm.items().create("vader"));
        var listener=nativeHandle.getClass().getField("connection").get(nativeHandle);
        var loaded=listener.getClass().getDeclaredMethod("markClientLoaded");loaded.setAccessible(true);loaded.invoke(listener);
        defender.startUsingItem(EquipmentSlot.HAND);
        defender.setActiveItemRemainingTime(defender.getActiveItemRemainingTime()-10);
        check(defender.isOnline() && defender.isBlocking(),"native online defender actively blocks");
        defender.setRotation(aims[scenario][0],aims[scenario][1]);
        aim=defender.getEyeLocation().getDirection();
        Vector toShooter=aim.clone();
        // Horizontal/oblique shots differ from aim to disprove automatic return-to-shooter behavior.
        if(Math.abs(aim.getY())<.99)toShooter.rotateAroundY(.3).normalize();
        Location eye=defender.getEyeLocation();
        shooter=mob(eye.clone().add(toShooter.clone().multiply(9)).subtract(0,.8,0));
        target=mob(eye.clone().add(aim.clone().multiply(6)).subtract(0,.8,0));
        behind=mob(eye.clone().subtract(toShooter.clone().multiply(scenario==0?2:6)).subtract(0,.8,0));
        check(target.isValid() && !target.isInvulnerable(),"native damage target is spawned and hittable");if(scenario==0)target.setHealth(.1);targetHealth=target.getHealth();
        check(mm.sabers().canReflect(defender,toShooter.clone().multiply(-2)),"existing cone permits incoming test shot");
        check(!mm.sabers().canReflect(defender,aim.clone().multiply(2)),"existing rear rejection retained");
        var def=WeaponMechanics.getInstance().getWeaponConfigurations().getObject("E11.Projectile",Projectile.class);
        original=def.create(shooter,eye.clone().add(toShooter.clone().multiply(3)),toShooter.clone().multiply(scenario==0?-10:-2),scenario==2?null:WeaponMechanicsAPI.generateWeapon("E11"),"E11",EquipmentSlot.HAND);
        bolts.add(original);
        def.shoot(original,original.getBukkitLocation());
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
            check(reflectedCount==1,"one reflection scheduled from actual incoming collision");
            check(original.isDead(),"original projectile removed");
            check(defenderHits==1 && defender.getHealth()==20,"one original collision and no defender damage");
            check(reflected!=null && renderStart!=null,"scheduled reflection has a rendered entity");
            // This runs while the bolt is moving toward a target six blocks away.
            Vector ray=reflected.getLocation().subtract(rayStart);
            Vector render=new Vector(reflected.getDisguise().getX(),reflected.getDisguise().getY(),reflected.getDisguise().getZ()).subtract(renderStart);
            check(ray.length()>1 && ray.clone().normalize().dot(aim)>.99999,"real moving damage ray follows current yaw/pitch "+Arrays.toString(aims[scenario]));
            check(render.length()>1 && render.clone().normalize().dot(aim)>.99999,"visible laser moves along same current yaw/pitch");
        }),5);
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
            check(targetHits==1 && target.getHealth()<targetHealth,"one real reflected target damage application");
            if(scenario==0)check(target.isDead() && target.getKiller()==defender,"native kill credit belongs to defender");
            check(behindProjectileHits==0,"removed original cannot apply projectile damage behind defender");
            check(reflectedCount==1,"no duplicate reflected projectile");
            scenario++;next();
        }),18);
    }
    private void protectedDamage() throws Exception {
        protectionTest=true;
        var safe=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation().add(0,80,0);
        safe.getChunk().addPluginChunkTicket(this);defender=nativeTestPlayer(safe.getWorld(),safe);
        defender.setGameMode(GameMode.SURVIVAL);defender.setGravity(false);defender.getInventory().setItemInMainHand(mm.items().create("vader"));
        defender.startUsingItem(EquipmentSlot.HAND);defender.setActiveItemRemainingTime(defender.getActiveItemRemainingTime()-10);
        check(!mm.sabers().canReflect(defender,new Vector(0,0,-2)),"protected defender cannot initiate a reflection");
        var point=new Location(Bukkit.getWorld(NamespacedKey.minecraft("devworld")),80,200,80);
        shooter=mob(point.clone().add(0,0,5));target=mob(point);
        var def=WeaponMechanics.getInstance().getWeaponConfigurations().getObject("E11.Projectile",Projectile.class);
        var handler=WeaponMechanics.getInstance().getWeaponHandler().getDamageHandler();
        for(boolean protectedVictim:new boolean[]{true,false}){
            var source=protectedVictim?shooter:defender;
            var victim=protectedVictim?defender:target;
            var b=def.create(source,source.getEyeLocation(),new Vector(0,0,-2),WeaponMechanicsAPI.generateWeapon("E11"),"E11",EquipmentSlot.HAND);
            bolts.add(b);b.setIntTag("mandomc_saber_reflections",1);double hp=victim.getHealth();int denied=protectionDenied;
            handler.tryUse(new ProjectileDamageSource(b,me.deecaad.weaponmechanics.weapon.damage.DamagePoint.BODY),victim,4,EquipmentSlot.HAND);
            check(protectionDenied==denied+1 && victim.getHealth()==hp,(protectedVictim?"protected victim":"protected shooter")+" cancels reflected damage");
        }
        cleanupCase();
    }
    private LivingEntity mob(Location p){p.getChunk().addPluginChunkTicket(this);return p.getWorld().spawn(p,Cow.class,e->{e.setAI(false);e.setGravity(false);e.setSilent(true);e.setPersistent(false);e.setRemoveWhenFarAway(false);e.setInvulnerable(false);});}
    private Map<String,Object> snapshot(Object o){
        var values=new TreeMap<String,Object>();if(o==null)return values;
        for(var m:o.getClass().getMethods())if(m.getParameterCount()==0 && (m.getName().startsWith("get")||m.getName().startsWith("is")) && (m.getReturnType().isPrimitive()||m.getReturnType().isEnum()||m.getReturnType()==String.class||m.getReturnType()==ItemStack.class)){
            try{values.put(m.getName(),m.invoke(o));}catch(Exception ex){throw new RuntimeException(ex);}
        }return values;
    }
    private void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);checks++;getLogger().info("PASS "+text);}
    private interface Checked{void run() throws Exception;}
    private void guard(Checked work){if(failed)return;try{work.run();}catch(Throwable t){failed=true;getLogger().log(java.util.logging.Level.SEVERE,"REFLECTION PROBE FAILED",t);cleanupCase();Bukkit.shutdown();}}
    private void cleanupCase(){for(var b:bolts)b.remove();bolts.clear();for(var e:new LivingEntity[]{shooter,target,behind})if(e!=null)e.remove();if(defender!=null){if(playerMap!=null)playerMap.remove(defender.getUniqueId());try {
            Object level=defender.getWorld().getClass().getMethod("getHandle").invoke(defender.getWorld());
            Class<?> reason=Class.forName("net.minecraft.world.entity.Entity$RemovalReason");
            level.getClass().getMethod("removePlayerImmediately",nativeHandle.getClass(),reason).invoke(level,nativeHandle,reason.getField("DISCARDED").get(null));
        } catch(Exception ex){throw new RuntimeException(ex);}defender=null;}}
    @Override public void onDisable(){cleanupCase();}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "SaberContactTest");
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
        connectionClass.getField("channel").set(connection,Class.forName("io.netty.channel.embedded.EmbeddedChannel").getConstructor().newInstance());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        var mapField=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");mapField.setAccessible(true);
        playerMap=(Map<UUID,Object>)mapField.get(list);
        nativeHandle=handle;
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        playerClass.getField("invulnerableTime").setInt(handle,0);
        levelClass.getMethod("addNewPlayer",playerClass).invoke(level,handle);
        playerMap.put((UUID)playerClass.getMethod("getUUID").invoke(handle),handle);
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

}
