package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.MandoMC;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Spawns tagged Bukkit projectile entities with configurable velocity,
 * gravity, and type. Each projectile is PDC-tagged for damage attribution.
 */
public final class ProjectileSpawner {

    public static final NamespacedKey VEHICLE_PROJECTILE_KEY =
            new NamespacedKey(MandoMC.getInstance(), "vehicle_projectile");
    public static final NamespacedKey VEHICLE_DAMAGE_KEY =
            new NamespacedKey(MandoMC.getInstance(), "vehicle_projectile_damage");

    private ProjectileSpawner() {}

    /**
     * Spawns a projectile of the configured type from the given origin.
     *
     * @param shooter   the player firing
     * @param origin    spawn location (typically eye location)
     * @param direction normalized direction vector
     * @param config    weapon configuration
     * @return the spawned projectile entity
     */
    public static Projectile spawn(Player shooter, Location origin,
                                   Vector direction, WeaponConfig config) {

        Projectile projectile = switch (config.getProjectileType()) {
            case FIREBALL -> spawnFireball(shooter, origin, direction, config);
            case SNOWBALL -> spawnSnowball(shooter, origin, direction, config);
            case CUSTOM   -> spawnCustom(shooter, origin, direction, config);
            default       -> spawnArrow(shooter, origin, direction, config);
        };

        tag(projectile, config.getDamage());
        return projectile;
    }

    private static Arrow spawnArrow(Player shooter, Location origin,
                                    Vector direction, WeaponConfig config) {
        Arrow arrow = shooter.getWorld().spawnArrow(
                origin, direction, (float) config.getProjectileSpeed(), 0f);
        arrow.setShooter(shooter);
        arrow.setGravity(config.hasGravity());
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        arrow.setDamage(config.getDamage());
        return arrow;
    }

    private static SmallFireball spawnFireball(Player shooter, Location origin,
                                              Vector direction, WeaponConfig config) {
        SmallFireball fb = shooter.getWorld().spawn(origin, SmallFireball.class);
        fb.setShooter(shooter);
        fb.setDirection(direction.clone().multiply(config.getProjectileSpeed()));
        fb.setGravity(config.hasGravity());
        fb.setIsIncendiary(false);
        fb.setYield(0f);
        return fb;
    }

    private static Snowball spawnSnowball(Player shooter, Location origin,
                                          Vector direction, WeaponConfig config) {
        Snowball sb = shooter.getWorld().spawn(origin, Snowball.class);
        sb.setShooter(shooter);
        sb.setVelocity(direction.clone().multiply(config.getProjectileSpeed()));
        sb.setGravity(config.hasGravity());
        return sb;
    }

    private static Snowball spawnCustom(Player shooter, Location origin,
                                        Vector direction, WeaponConfig config) {
        return spawnSnowball(shooter, origin, direction, config);
    }

    private static void tag(Projectile projectile, double damage) {
        projectile.getPersistentDataContainer().set(
                VEHICLE_PROJECTILE_KEY, PersistentDataType.BOOLEAN, true);
        projectile.getPersistentDataContainer().set(
                VEHICLE_DAMAGE_KEY, PersistentDataType.DOUBLE, damage);
    }
}
