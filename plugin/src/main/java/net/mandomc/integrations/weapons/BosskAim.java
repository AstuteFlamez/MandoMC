package net.mandomc.integrations.weapons;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import net.mandomc.commands.RootCommand;
import net.mandomc.integrations.models.BosskMuzzle;

/** Fresh model facing and velocity-led, zero-spread attack4 through the supported NPC gun API. */
public final class BosskAim implements AutoCloseable {
    private record Track(UUID target,Location location,int tick,Vector velocity) { }
    private final Map<UUID,Track> tracks=new HashMap<>();
    private final org.bukkit.scheduler.BukkitTask cleanup;
    public BosskAim(JavaPlugin plugin,RootCommand root,Function<Entity,String> mobType,Predicate<Location> allowed,
            BosskMuzzle pose) throws ReflectiveOperationException {
        var vendor=Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        var shoot=Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI",true,vendor.getClass().getClassLoader())
                .getMethod("shoot",LivingEntity.class,String.class,Location.class);
        root.register("bossktarget","mandomc.admin.status",(sender,args)->{
            if(!(sender instanceof ConsoleCommandSender)||args.length!=3||!Set.of("face","shot").contains(args[0]))return;
            try {
                var source=Bukkit.getEntity(UUID.fromString(args[1]));var victim=Bukkit.getEntity(UUID.fromString(args[2]));
                if(!(source instanceof LivingEntity boss)||!(victim instanceof LivingEntity target)||boss.isDead()||target.isDead()
                        ||!boss.isValid()||!target.isValid()||!"Bossk".equals(mobType.apply(boss))||boss.getWorld()!=target.getWorld()
                        ||boss.getLocation().distanceSquared(target.getLocation())>1024)return;
                int now=Bukkit.getCurrentTick();var previous=tracks.get(boss.getUniqueId());var velocity=target.getVelocity();
                if(previous!=null&&previous.target.equals(target.getUniqueId())) {
                    int dt=now-previous.tick;
                    if(dt>0&&dt<=3)velocity=target.getLocation().toVector().subtract(previous.location.toVector()).multiply(1.0/dt);
                    else if(dt==0)velocity=previous.velocity.clone();
                }
                if(velocity.lengthSquared()>1)velocity=new Vector();
                tracks.put(boss.getUniqueId(),new Track(target.getUniqueId(),target.getLocation(),now,velocity));
                var point=target.getLocation().add(0,target.getHeight()*0.6,0);
                if(args[0].equals("shot")) {
                    if(!allowed.test(boss.getLocation())||!allowed.test(target.getLocation())||!boss.hasLineOfSight(target))return;
                    point=lead(boss.getEyeLocation(),point,velocity,2.75,10);
                }
                pose.face(boss,point);
                if(args[0].equals("shot"))shoot.invoke(null,boss,"RelbyV10",point);
            }catch(IllegalArgumentException ignored){ /* Reject stale UUID placeholders. */ }
            catch(ReflectiveOperationException|IllegalStateException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bossk targeting failed",e);}
        },(sender,args)->List.of());
        cleanup=Bukkit.getScheduler().runTaskTimer(plugin,()->tracks.entrySet().removeIf(e->Bukkit.getEntity(e.getKey())==null||Bukkit.getCurrentTick()-e.getValue().tick>100),100,100);
    }
    public static Location lead(Location origin,Location target,Vector velocity,double speed,double maxTicks) {
        var r=target.toVector().subtract(origin.toVector());double a=velocity.lengthSquared()-speed*speed,b=2*r.dot(velocity),c=r.lengthSquared();
        double t=r.length()/speed;
        if(Math.abs(a)<1e-9){if(Math.abs(b)>1e-9)t=-c/b;}
        else {double discriminant=b*b-4*a*c;if(discriminant>=0){double u=(-b-Math.sqrt(discriminant))/(2*a),v=(-b+Math.sqrt(discriminant))/(2*a);t=u>0&&v>0?Math.min(u,v):Math.max(u,v);}}
        t=Math.max(0,Math.min(maxTicks,t));return target.clone().add(velocity.clone().multiply(t));
    }
    @Override public void close(){cleanup.cancel();tracks.clear();}
}
