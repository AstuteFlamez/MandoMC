package net.mandomc.integrations.weapons;

import net.mandomc.gameplay.explosions.TemporaryExplosions;
import net.mandomc.gameplay.vehicles.VehicleService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** WeaponMechanics owns bolt collision/entity damage; MandoMC owns every temporary block hole. */
public final class VehicleWeapons implements Listener {
    private final VehicleService vehicles;
    private final TemporaryExplosions explosions;
    private final JavaPlugin plugin;
    private final java.util.Map<Object, Entity> launched = new java.util.WeakHashMap<>();

    @SuppressWarnings("unchecked")
    public VehicleWeapons(
            JavaPlugin plugin, VehicleService vehicles, TemporaryExplosions explosions)
            throws Exception {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.explosions = explosions;
        var vendor = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if (vendor == null || !vendor.isEnabled())
            throw new IllegalStateException("Vehicles require WeaponMechanics");
        var loader = vendor.getClass().getClassLoader();
        var shoot =
                (Class<? extends Event>)
                        Class.forName(
                                "me.deecaad.weaponmechanics.weapon.weaponevents.WeaponPreShootEvent",
                                true,
                                loader);
        Bukkit.getPluginManager()
                .registerEvent(
                        shoot,
                        this,
                        EventPriority.HIGHEST,
                        (listener, event) -> {
                            try {
                                if (call(event, "getShooter")
                                                instanceof org.bukkit.entity.Player player
                                        && !vehicles.mayShoot(player))
                                    ((Cancellable) event).setCancelled(true);
                            } catch (ReflectiveOperationException error) {
                                fail(event, error);
                            }
                        },
                        plugin,
                        true);
        var emission = (Class<? extends Event>) Class.forName(
                "me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent", true, loader);
        Bukkit.getPluginManager().registerEvent(emission, this, EventPriority.MONITOR, (listener, event) -> {
            try {
                Object projectile = call(event, "getProjectile");
                Entity shooter = (Entity) call(projectile, "getShooter");
                Entity vehicle = vehicles.combatVehicle(shooter);
                if (vehicle != null) launched.put(projectile, vehicle);
            } catch (ReflectiveOperationException error) {
                throw new EventException(error);
            }
        }, plugin, true);
        var damage =
                (Class<? extends Event>)
                        Class.forName(
                                "me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent",
                                true,
                                loader);
        Bukkit.getPluginManager()
                .registerEvent(
                        damage,
                        this,
                        EventPriority.HIGHEST,
                        (listener, e) -> damage(e),
                        plugin,
                        true);
        var blast =
                (Class<? extends Event>)
                        Class.forName(
                                "me.deecaad.weaponmechanics.weapon.weaponevents.ProjectileExplodeEvent",
                                true,
                                loader);
        Bukkit.getPluginManager()
                .registerEvent(
                        blast,
                        this,
                        EventPriority.HIGHEST,
                        (listener, e) -> blast(e),
                        plugin,
                        true);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    public void damage(Event event) {
        try {
            Entity victim = (Entity) call(event, "getVictim");
            Object source = call(event, "getSource");
            Entity shooter = (Entity) call(source, "getShooter");
            Entity vehicle = vehicles.combatVehicle(shooter);
            if (source.getClass().getSimpleName().equals("ProjectileDamageSource"))
                vehicle = launched.getOrDefault(call(source, "getProjectile"), vehicle);
            if (!vehicles.mayUse(vehicle, shooter, victim.getLocation())
                    || !vehicles.mayDamage(victim, shooter)) ((Cancellable) event).setCancelled(true);
        } catch (ReflectiveOperationException error) {
            fail(event, error);
        }
    }

    public void blast(Event event) {
        try {
            Object projectile = call(event, "getProjectile");
            Location center = ((Location) call(projectile, "getBukkitLocation")).clone();
            Entity shooter = (Entity) call(projectile, "getShooter");
            Entity vehicle = launched.getOrDefault(projectile, vehicles.combatVehicle(shooter));
            if (!vehicles.mayUse(vehicle, shooter, center)) {
                ((Cancellable) event).setCancelled(true);
                return;
            }
            if (vehicle != null) {
                // Vendor splash damage has no projectile reference in its damage source.
                // Filter its actual victim map while the launch platform is still available.
                var victims = (java.util.Map<?, ?>) call(event, "getEntities");
                victims.keySet().removeIf(target -> target instanceof Entity entity
                        && !vehicles.mayUse(vehicle, shooter, entity.getLocation()));
            }
            // Disable vendor block damage/regeneration. Keep its original entity damage and visual
            // effects.
            event.getClass()
                    .getMethod("setBlocks", List.class)
                    .invoke(event, new ArrayList<Block>());
            // Wait until every vendor event listener has made its cancellation decision.
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (!((Cancellable) event).isCancelled()
                                        && vehicles.mayUse(vehicle, shooter, center))
                                    explosions.vendorBlast(center, shooter);
                            });
        } catch (ReflectiveOperationException error) {
            fail(event, error);
        }
    }

    private void fail(Event event, Exception error) {
        ((Cancellable) event).setCancelled(true);
        plugin.getLogger()
                .log(java.util.logging.Level.SEVERE, "Vehicle weapon integration failed", error);
    }
}
