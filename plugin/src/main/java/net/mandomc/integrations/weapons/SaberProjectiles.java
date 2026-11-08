package net.mandomc.integrations.weapons;

import java.lang.reflect.*;
import net.mandomc.gameplay.saber.SaberService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Pinned WeaponMechanics 4.3.1 public API boundary; no vendor class leaks into gameplay. */
public final class SaberProjectiles implements Listener {
    private final JavaPlugin plugin;
    private final SaberService sabers;
    private final Constructor<?> constructor;
    private final Object spawner;
    private final Method spawn;
    private static final String REFLECTIONS="mandomc_saber_reflections";

    @SuppressWarnings("unchecked")
    public SaberProjectiles(JavaPlugin plugin,SaberService sabers) throws ReflectiveOperationException {
        this.plugin=plugin;this.sabers=sabers;
        var vendor=Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if(vendor==null||!vendor.isEnabled()) throw new IllegalStateException("Sabers require WeaponMechanics");
        var loader=vendor.getClass().getClassLoader();
        var projectile=Class.forName("me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile",true,loader);
        constructor=java.util.Arrays.stream(projectile.getConstructors()).filter(c->c.getParameterCount()==10).findFirst().orElseThrow();
        spawner=vendor.getClass().getMethod("getProjectileSpawner").invoke(vendor);
        spawn=Class.forName("me.deecaad.weaponmechanics.weapon.projectile.ProjectileSpawner",true,loader)
                .getMethod("spawn",Class.forName("me.deecaad.weaponmechanics.weapon.projectile.AProjectile",true,loader));
        var event=(Class<? extends Event>)Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents.ProjectileHitEntityEvent",true,loader);
        Bukkit.getPluginManager().registerEvent(event,this,EventPriority.HIGHEST,(listener,e)->hit(e),plugin,true);
    }
    private static Object call(Object o,String method) throws ReflectiveOperationException {return o.getClass().getMethod(method).invoke(o);}
    /** Creates, but does not schedule, a new bolt with the defender as its immutable shooter. */
    public Object createReflection(Object bolt,Player player) throws ReflectiveOperationException {
        Vector direction=player.getEyeLocation().getDirection();
        Vector incoming=(Vector)call(bolt,"getMotion");
        Location origin=player.getEyeLocation().add(direction.clone().multiply(0.8));
        // MythicMobs fires through WeaponMechanics' NPC API, which supplies no inventory stack.
        ItemStack weapon = (ItemStack) call(bolt,"getWeaponStack");
        if (weapon == null) weapon = new WeaponMechanicsItems().generate("weapon", (String) call(bolt,"getWeaponTitle"));
        Object reflected=constructor.newInstance(call(bolt,"getProjectileSettings"),player,origin,
                direction.clone().multiply(Math.max(0.1,incoming.length())), weapon.clone(),
                call(bolt,"getWeaponTitle"),EquipmentSlot.HAND,call(bolt,"getSticky"),call(bolt,"getThrough"),call(bolt,"getBouncy"));
        int count=(int)bolt.getClass().getMethod("getIntTag",String.class).invoke(bolt,REFLECTIONS);
        reflected.getClass().getMethod("setIntTag",String.class,int.class).invoke(reflected,REFLECTIONS,count+1);
        return reflected;
    }
    public void hit(Event event) {
        try {
            Object bolt=call(event,"getProjectile");
            var victim=(LivingEntity)call(event,"getEntity");
            var shooter=(LivingEntity)call(bolt,"getShooter");
            int count=(int)bolt.getClass().getMethod("getIntTag",String.class).invoke(bolt,REFLECTIONS);
            if(count>0 && (!sabers.allowed(victim.getLocation()) || shooter==null || !sabers.allowed(shooter.getLocation()))) {
                ((Cancellable)event).setCancelled(true);call(bolt,"remove");return;
            }
            Vector incoming=(Vector)call(bolt,"getMotion");
            if(!(victim instanceof Player player)||!sabers.canReflect(player,incoming)) return;
            if(shooter!=null && !sabers.allowed(shooter.getLocation())) {((Cancellable)event).setCancelled(true);call(bolt,"remove");return;}
            ((Cancellable)event).setCancelled(true);
            Object reflected = count < sabers.settings().reflectionLimit() ? createReflection(bolt,player) : null;
            call(bolt,"remove");sabers.reflected(player);
            if(reflected==null)return;
            Location location=player.getEyeLocation();
            Bukkit.getScheduler().runTask(plugin,()-> {
                if(!player.isOnline()||player.isDead()||!player.getWorld().equals(location.getWorld())||!sabers.allowed(player.getLocation())||!sabers.allowed(location))return;
                try {
                    // The shooter field is immutable: create a new bolt owned by the defender.
                    spawn.invoke(spawner,reflected);
                } catch(ReflectiveOperationException error) {plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot spawn reflected bolt",error);}
            });
        } catch(ReflectiveOperationException | RuntimeException error) {
            // A broken compatibility hook must not convert an attempted reflection into an unprotected hit.
            ((Cancellable)event).setCancelled(true);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Saber projectile adapter failed",error);
        }
    }
}
